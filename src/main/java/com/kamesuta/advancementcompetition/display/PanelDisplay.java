package com.kamesuta.advancementcompetition.display;

import com.kamesuta.advancementcompetition.AdvancementUtil;
import com.kamesuta.advancementcompetition.RankingProgressData;
import com.mojang.math.Transformation;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.advancement.Advancement;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.craftbukkit.v1_20_R3.CraftWorld;
import org.bukkit.craftbukkit.v1_20_R3.advancement.CraftAdvancement;
import org.bukkit.craftbukkit.v1_20_R3.block.CraftBlock;
import org.bukkit.craftbukkit.v1_20_R3.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_20_R3.inventory.CraftItemType;
import org.bukkit.entity.Player;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.kamesuta.advancementcompetition.AdvancementCompetition.app;

/**
 * 進捗表示レンダラー
 */
public class PanelDisplay {
    /**
     * エンティティUUID開始番号
     */
    public static final int ENTITY_STARTING_ID = Integer.MAX_VALUE / 5;
    /**
     * エンティティ個数
     */
    public static final int ENTITY_COUNT = AdvancementDisplay.mapLength /* 額縁 */ + 4 /* =テキスト */ + 2 /* =アイテム */;

    /**
     * 表示されているプレイヤー
     */
    private final Set<UUID> shown = new HashSet<>();

    /**
     * ID
     */
    public final int id;
    /**
     * ブロック
     */
    public final Block baseBlock;
    /**
     * 向き
     */
    public final BlockFace direction;
    /**
     * 実績
     */
    public final Advancement advancement;

    /**
     * エンティティID
     */
    private final int[] entityIds;

    /**
     * 進捗表示レンダラーを作成します。
     *
     * @param id          ID
     * @param baseBlock   ベースブロック
     * @param direction   向き
     * @param advancement 実績
     */
    public PanelDisplay(int id, Block baseBlock, BlockFace direction, Advancement advancement) {
        this.id = id;
        this.baseBlock = baseBlock;
        this.direction = direction;
        this.advancement = advancement;

        // エンティティIDを生成
        this.entityIds = IntStream.range(0, ENTITY_COUNT)
                .map(i -> ENTITY_STARTING_ID + id * ENTITY_COUNT + i)
                .toArray();
    }

    /**
     * マップをプレイヤーに表示します。
     *
     * @param player プレイヤー
     * @param force  強制表示
     */
    public void show(Player player, boolean force) {
        // 既に表示されている場合は表示しない
        if (!this.shown.add(player.getUniqueId()) && !force) return;

        // エンティティIDのカウンターのカウンター
        int k = 0;

        // ランキングを取得
        RankingProgressData ranking = app.rankingManager.getAdvancementProgressData(player, advancement, 10, 5);

        // 実績ディスプレイを取得
        CraftAdvancement craftAdvancement = (CraftAdvancement) advancement;
        Optional<DisplayInfo> displayOptional = craftAdvancement.getHandle().value().display();

        // ランキングやディスプレイ情報が取得できなかったら表示しない
        if (ranking == null || !displayOptional.isPresent()) return;
        DisplayInfo display = displayOptional.get();

        // アイテムを表示
        showFlatItem(player, entityIds[k++], display.getIcon());

        // ステータス
        MutableComponent status = Component.empty();
        // ステータス 「進捗説明\n\nクリア率:%d/%d人(%d位)」
        ranking.appendProgressDescription(status);

        // ランキング
        MutableComponent leaderboard = Component.empty();
        // 上位10人の進捗を追加
        if (!ranking.top.isEmpty()) {
            leaderboard.append(Component.literal("トップ10").withStyle(ChatFormatting.YELLOW));
            ranking.appendRanking(leaderboard, ranking.top);
            ranking.appendBlankLines(leaderboard, 10, ranking.top.size());
        } else {
            leaderboard.append(Component.literal("まだ誰も達成していません。").withStyle(ChatFormatting.BLUE))
                    .append(Component.literal("\n\n"))
                    .append(Component.literal("実績に挑戦して\n1番目の達成者になりましょう！").withStyle(ChatFormatting.YELLOW));
        }
        // 直近5人の進捗を追加 (15人以上の場合)
        if (!ranking.bottom.isEmpty() && ranking.done >= 15) {
            leaderboard.append("\n\n")
                    .append(Component.literal("直近達成5位").withStyle(ChatFormatting.BLUE));
            ranking.appendRanking(leaderboard, ranking.bottom);
            ranking.appendBlankLines(leaderboard, 5, ranking.bottom.size());
        } else {
            ranking.appendBlankLines(leaderboard, 7, 0);
        }

        // テキストを表示
        showText(player, entityIds[k++], display.getTitle(), 0.78f, 0.1f, 123, 0.5f, true);
        showText(player, entityIds[k++], display.getDescription(), 0.6f, -0.2f, 220, 0.35f, false);
        showText(player, entityIds[k++], status, 0.6f, -0.477f, 220, 0.35f, true);
        showText(player, entityIds[k++], leaderboard, 2.075f, -0.45f, 180, 0.18f, false);

        // 進捗を表示
        if (ranking.total > 0) {
            showProgressBar(player, entityIds[k++], ranking.done / (float) ranking.total);
        }

        // マップを取得
        List<MapDisplay> panel = (ranking.progress != null && ranking.progress.rank >= 0)
                ? app.display.panelDone // 達成済み
                : app.display.panel; // 未達成

        // マップを表示
        BlockFace rightFace = AdvancementUtil.getRight(direction.getOppositeFace());
        for (int i = 0; i < AdvancementDisplay.mapLength; i++) {
            // マップを取得
            MapDisplay mapDisplay = panel.get(i);

            // ブロックを取得
            Location location = baseBlock.getRelative(rightFace, i).getRelative(direction).getLocation();

            // プレイヤーにマップを表示
            showMap(player, entityIds[k++], mapDisplay, location);
        }
    }

    /**
     * マップをプレイヤーから非表示にします。
     */
    public void hide(Player player) {
        // 既に非表示の場合は非表示しない
        if (!this.shown.remove(player.getUniqueId())) return;

        // エンティティを削除
        ServerGamePacketListenerImpl connection = ((CraftPlayer) player).getHandle().connection;
        connection.send(new ClientboundRemoveEntitiesPacket(entityIds));
    }

    /**
     * 平らなアイテムをパネルに表示します。
     *
     * @param player    プレイヤー
     * @param id        ID
     * @param itemStack アイテム
     */
    private void showFlatItem(Player player, int id, ItemStack itemStack) {
        // アイテムディスプレイの幻覚
        Display.ItemDisplay itemDisplay = new Display.ItemDisplay(EntityType.ITEM_DISPLAY, ((CraftWorld) player.getWorld()).getHandle());
        itemDisplay.setId(id);
        itemDisplay.setItemStack(itemStack);
        Location location = baseBlock.getLocation().clone().add(0.5, 0.5, 0.5).add(direction.getDirection().multiply(0.52));
        itemDisplay.setPos(location.getX(), location.getY(), location.getZ());
        itemDisplay.setBrightnessOverride(Brightness.FULL_BRIGHT);
        itemDisplay.setItemTransform(ItemDisplayContext.GUI);

        // アイテムディスプレイの向き
        Matrix4f transform = new Matrix4f()
                .rotate((float) (-Math.PI / 2) - AdvancementUtil.getRotate(direction), new Vector3f(0, 1, 0)) // ブロックの向きに合わせる
                .translate(0.235f, 0.27f, 0f) // マップ上の位置に合わせる
                .scale(0.25f, 0.25f, 0.01f);
        itemDisplay.setTransformation(new Transformation(transform));

        // エンティティを送信
        ServerGamePacketListenerImpl connection = ((CraftPlayer) player).getHandle().connection;
        connection.send(new ClientboundAddEntityPacket(itemDisplay, itemDisplay.getDirection().get3DDataValue()));
        connection.send(new ClientboundSetEntityDataPacket(itemDisplay.getId(), itemDisplay.getEntityData().packDirty()));
    }

    /**
     * プログレスバーを表示します。
     *
     * @param player   プレイヤー
     * @param id       ID
     * @param progress 進捗
     */
    private void showProgressBar(Player player, int id, float progress) {
        // プログレスバーディスプレイの幻覚
        Display.ItemDisplay itemDisplay = new Display.ItemDisplay(EntityType.ITEM_DISPLAY, ((CraftWorld) player.getWorld()).getHandle());
        itemDisplay.setId(id);
        itemDisplay.setItemStack(new ItemStack(CraftItemType.bukkitToMinecraft(Material.LIGHT_BLUE_CONCRETE)));
        Location location = baseBlock.getLocation().clone().add(0.5, 0.5, 0.5).add(direction.getDirection().multiply(0.51));
        itemDisplay.setPos(location.getX(), location.getY(), location.getZ());
        itemDisplay.setBrightnessOverride(Brightness.FULL_BRIGHT);

        // アイテムディスプレイの向き
        // /summon item_display 1.5 -58.5 59.0 {transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[-0.575f,-0.344f,0f],scale:[1.96f,0.095f,0.018f]},item:{id:"minecraft:light_blue_concrete",Count:1b}}
        float widthScale = 1.96f * progress;
        Matrix4f transform = new Matrix4f()
                .rotate((float) (-Math.PI / 2) - AdvancementUtil.getRotate(direction), new Vector3f(0, 1, 0)) // ブロックの向きに合わせる
                .translate(-0.575f + (1 - widthScale / 2), -0.344f, 0f) // マップ上の位置に合わせる
                .scale(widthScale, 0.095f, 0.018f); // スケール
        itemDisplay.setTransformation(new Transformation(transform));

        // エンティティを送信
        ServerGamePacketListenerImpl connection = ((CraftPlayer) player).getHandle().connection;
        connection.send(new ClientboundAddEntityPacket(itemDisplay, itemDisplay.getDirection().get3DDataValue()));
        connection.send(new ClientboundSetEntityDataPacket(itemDisplay.getId(), itemDisplay.getEntityData().packDirty()));
    }

    /**
     * 平らなアイテムをパネルに表示します。
     *
     * @param player プレイヤー
     * @param id     ID
     * @param text   テキスト
     * @param x      X座標
     * @param y      Y座標
     * @param scale  スケール
     * @param shadow 影
     */
    private void showText(Player player, int id, Component text, float x, float y, int width, float scale, boolean shadow) {
        // テキストディスプレイの幻覚
        Display.TextDisplay textDisplay = new Display.TextDisplay(EntityType.TEXT_DISPLAY, ((CraftWorld) player.getWorld()).getHandle());
        textDisplay.setId(id);
        Location location = baseBlock.getLocation().clone().add(0.5, 0.5, 0.5).add(direction.getDirection().multiply(0.52));
        textDisplay.setPos(location.getX(), location.getY(), location.getZ());
        textDisplay.setBrightnessOverride(Brightness.FULL_BRIGHT);
        textDisplay.getEntityData().set(Display.TextDisplay.DATA_BACKGROUND_COLOR_ID, 0);
        textDisplay.getEntityData().set(Display.TextDisplay.DATA_LINE_WIDTH_ID, width);
        textDisplay.setFlags((byte) (Display.TextDisplay.FLAG_ALIGN_LEFT | (shadow ? Display.TextDisplay.FLAG_SHADOW : 0)));

        // 1行がwidthピクセルになるようにスペースを追加
        int SPACE_WIDTH = 4;
        String space = IntStream.range(0, Math.max(0, width / SPACE_WIDTH)).mapToObj(i -> " ").collect(Collectors.joining());
        // テキストを設定
        textDisplay.setText(Component.empty().append(text).append("\n " + space));

        // テキストディスプレイの向き
        Matrix4f transform = new Matrix4f()
                .rotate((float) (Math.PI / 2) - AdvancementUtil.getRotate(direction), new Vector3f(0, 1, 0)) // ブロックの向きに合わせる
                .translate(x, y, 0f) // マップ上の位置に合わせる
                .scale(scale, scale, 1f); // スケール
        textDisplay.setTransformation(new Transformation(transform));

        // エンティティを送信
        ServerGamePacketListenerImpl connection = ((CraftPlayer) player).getHandle().connection;
        connection.send(new ClientboundAddEntityPacket(textDisplay, textDisplay.getDirection().get3DDataValue()));
        connection.send(new ClientboundSetEntityDataPacket(textDisplay.getId(), textDisplay.getEntityData().packDirty()));
    }

    /**
     * マップをプレイヤーに表示します。
     *
     * @param player   プレイヤー
     * @param id       ID
     * @param map      マップ
     * @param location 位置
     */
    private void showMap(Player player, int id, MapDisplay map, Location location) {
        // マップアイテムを作成
        ItemStack item = new ItemStack(Items.FILLED_MAP);
        item.getOrCreateTag().putInt("map", map.mapId);

        // アイテムフレームを作成
        ItemFrame frame = new ItemFrame(((CraftWorld) player.getWorld()).getHandle(),
                BlockPos.containing(location.getX(), location.getY(), location.getZ()),
                CraftBlock.blockFaceToNotch(direction));
        frame.setId(id);
        frame.setItem(item, false, false);
        frame.setInvisible(true);
        frame.setInvulnerable(true);
        frame.setSilent(true);
        frame.fixed = true;

        // エンティティ+マップを送信
        ServerGamePacketListenerImpl connection = ((CraftPlayer) player).getHandle().connection;
        connection.send(new ClientboundAddEntityPacket(frame, frame.getDirection().get3DDataValue(), frame.getPos()));
        connection.send(new ClientboundSetEntityDataPacket(frame.getId(), frame.getEntityData().packDirty()));
        connection.send(new ClientboundMapItemDataPacket(map.mapId, (byte) 3, false,
                Collections.emptyList(), new MapItemSavedData.MapPatch(0, 0, 128, 128, map.pixels)));
    }
}
