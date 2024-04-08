package com.kamesuta.advancementcompetition.display;

import com.kamesuta.advancementcompetition.AdvancementUtil;
import com.mojang.math.Transformation;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.craftbukkit.v1_20_R3.CraftWorld;
import org.bukkit.craftbukkit.v1_20_R3.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_20_R3.inventory.CraftItemType;
import org.bukkit.entity.Player;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.kamesuta.advancementcompetition.AdvancementCompetition.app;

/**
 * 進捗表示レンダラー
 */
public class PanelDisplay {
    private Set<UUID> shown = new HashSet<>();

    private final Block baseBlock;
    private final BlockFace direction;
    private final List<FrameDisplay> frames;

    public PanelDisplay(Block baseBlock, BlockFace direction) {
        this.baseBlock = baseBlock;
        this.direction = direction;
        this.frames = IntStream.range(0, AdvancementDisplay.mapLength)
                .mapToObj(i -> {
                    // ブロックを取得
                    BlockFace rightFace = AdvancementUtil.getRight(direction.getOppositeFace());
                    Block target = baseBlock.getRelative(rightFace, i);

                    return new FrameDisplay(target.getRelative(direction).getLocation(), direction);
                })
                .collect(Collectors.toList());
    }

    /**
     * マップをプレイヤーに表示します。
     *
     * @param player プレイヤー
     */
    public void show(Player player) {
        if (this.shown.add(player.getUniqueId())) {
            // アイテムを表示
            showFlatItem(player, new ItemStack(Items.ACACIA_FENCE_GATE));

            // テキストを表示
            showText(player, "Hello, world!", 0.38f, 0.22f, 0.5f, true);
            showText(player, "説明", -0.33f, -0.03f, 0.35f, false);
            showText(player, "クリア率:1/2人(1位) 03/25 14:41取得", -0.33f, 0.1f, 0.35f, false);

            for (int i = 0; i < AdvancementDisplay.mapLength; i++) {
                // マップを取得
                MapDisplay mapDisplay = app.display.panel.get(i);

                // フレームを取得
                FrameDisplay frameDisplay = frames.get(i);

                // プレイヤーにマップを表示
                frameDisplay.createMap(player, mapDisplay);
            }
        }
    }

    /**
     * 平らなアイテムをパネルに表示します。
     *
     * @param player    プレイヤー
     * @param itemStack アイテム
     */
    private void showFlatItem(Player player, ItemStack itemStack) {
        // アイテムを取得
        boolean isBlock = AdvancementUtil.isBlock(CraftItemType.minecraftToBukkit(itemStack.getItem()));

        // アイテムディスプレイの幻覚
        Display.ItemDisplay itemDisplay = new Display.ItemDisplay(EntityType.ITEM_DISPLAY, ((CraftWorld) player.getWorld()).getHandle());
        itemDisplay.setItemStack(itemStack);
        Location location = baseBlock.getLocation().clone().add(0.5, 0.5, 0.5).add(direction.getDirection().multiply(0.52));
        itemDisplay.setPos(location.getX(), location.getY(), location.getZ());
        itemDisplay.setBrightnessOverride(Brightness.FULL_BRIGHT);

        // アイテムディスプレイの向き
        Matrix4f transform = new Matrix4f()
                .rotate((float) (-Math.PI / 2) - AdvancementUtil.getRotate(direction), new Vector3f(0, 1, 0)) // ブロックの向きに合わせる
                .translate(0.235f, 0.285f, 0f); // マップ上の位置に合わせる
        if (isBlock) {
            // ブロックの場合
            transform.scale(0.15f, 0.15f, 0.01f)
                    .rotate((float) Math.toRadians(-30), new Vector3f(1, 0, 0))
                    .rotate((float) Math.toRadians(180 + 45), new Vector3f(0, 1, 0));
        } else {
            // アイテムの場合
            transform.scale(0.25f, 0.25f, 0.25f);
        }
        itemDisplay.setTransformation(new Transformation(transform));

        ServerGamePacketListenerImpl connection = ((CraftPlayer) player).getHandle().connection;
        connection.send(new ClientboundAddEntityPacket(itemDisplay, itemDisplay.getDirection().get3DDataValue()));
        connection.send(new ClientboundSetEntityDataPacket(itemDisplay.getId(), itemDisplay.getEntityData().packDirty()));
    }

    /**
     * 平らなアイテムをパネルに表示します。
     *
     * @param player プレイヤー
     * @param text   テキスト
     * @param x      X座標
     * @param y      Y座標
     * @param scale  スケール
     * @param shadow 影
     */
    private void showText(Player player, String text, float x, float y, float scale, boolean shadow) {
        // テキストディスプレイの幻覚
        Display.TextDisplay textDisplay = new Display.TextDisplay(EntityType.TEXT_DISPLAY, ((CraftWorld) player.getWorld()).getHandle());
        textDisplay.setText(Component.literal(text));
        Location location = baseBlock.getLocation().clone().add(0.5, 0.5, 0.5).add(direction.getDirection().multiply(0.52));
        textDisplay.setPos(location.getX(), location.getY(), location.getZ());
        textDisplay.setBrightnessOverride(Brightness.FULL_BRIGHT);
        textDisplay.getEntityData().set(Display.TextDisplay.DATA_BACKGROUND_COLOR_ID, 0);
        textDisplay.setFlags((byte) (Display.TextDisplay.FLAG_ALIGN_LEFT | (shadow ? Display.TextDisplay.FLAG_SHADOW : 0)));

        // テキストディスプレイの向き
        Matrix4f transform = new Matrix4f()
                .rotate((float) (Math.PI / 2) - AdvancementUtil.getRotate(direction), new Vector3f(0, 1, 0)) // ブロックの向きに合わせる
                .translate(x, y, 0f) // マップ上の位置に合わせる
                .scale(scale, scale, 1f); // スケール
        textDisplay.setTransformation(new Transformation(transform));

        ServerGamePacketListenerImpl connection = ((CraftPlayer) player).getHandle().connection;
        connection.send(new ClientboundAddEntityPacket(textDisplay, textDisplay.getDirection().get3DDataValue()));
        connection.send(new ClientboundSetEntityDataPacket(textDisplay.getId(), textDisplay.getEntityData().packDirty()));
    }
}
