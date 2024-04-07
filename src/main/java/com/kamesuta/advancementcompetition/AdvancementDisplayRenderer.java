package com.kamesuta.advancementcompetition;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.craftbukkit.v1_20_R3.CraftWorld;
import org.bukkit.craftbukkit.v1_20_R3.block.CraftBlock;
import org.bukkit.craftbukkit.v1_20_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.map.MapPalette;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.kamesuta.advancementcompetition.AdvancementCompetition.app;

/**
 * 進捗表示レンダラー
 */
public class AdvancementDisplayRenderer {
    private Set<UUID> shown = new HashSet<>();

    private final Block baseBlock;
    private final BlockFace direction;
    private final List<FrameDisplay> frames;

    public AdvancementDisplayRenderer(Block baseBlock, BlockFace direction) {
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
     * 額縁
     */
    public static class FrameDisplay {
        /**
         * アイテムフレームUUID開始番号
         */
        public static final int ITEM_FRAME_STARTING_ID = Integer.MAX_VALUE / 5;
        private static final AtomicInteger ITEM_FRAME_ID_COUNTER = new AtomicInteger(ITEM_FRAME_STARTING_ID);

        /**
         * 額縁の向き
         */
        private final BlockFace direction;
        /**
         * 額縁の位置
         */
        private final Location location;
        /**
         * アイテムフレームID
         */
        private final int frameId;

        /**
         * マップレンダラーを作成します。
         *
         * @param location  位置
         * @param direction 向き
         */
        public FrameDisplay(Location location, BlockFace direction) {
            this.location = location;
            this.direction = direction;
            this.frameId = ITEM_FRAME_ID_COUNTER.getAndIncrement();
        }

        /**
         * マップをプレイヤーに表示します。
         *
         * @param player プレイヤー
         * @param map    マップ
         */
        private void createMap(Player player, MapDisplay map) {
            ItemStack item = new ItemStack(Items.FILLED_MAP);
            item.getOrCreateTag().putInt("map", map.mapId);

            ItemFrame frame = new ItemFrame(((CraftWorld) player.getWorld()).getHandle(),
                    BlockPos.containing(location.getX(), location.getY(), location.getZ()),
                    CraftBlock.blockFaceToNotch(direction));
            frame.setItem(item, false, false);
            frame.setInvisible(true);
            frame.setInvulnerable(true);
            frame.setSilent(true);
            frame.setId(frameId);
            frame.fixed = true;

            ServerGamePacketListenerImpl connection = ((CraftPlayer) player).getHandle().connection;
            connection.send(new ClientboundAddEntityPacket(frame, frame.getDirection().get3DDataValue(), frame.getPos()));
            connection.send(new ClientboundSetEntityDataPacket(frame.getId(), frame.getEntityData().packDirty()));
            connection.send(new ClientboundMapItemDataPacket(map.mapId, (byte) 3, false,
                    Collections.emptyList(), new MapItemSavedData.MapPatch(0, 0, 128, 128, map.pixels)));
        }

        /**
         * マップを削除します。
         *
         * @param player プレイヤー
         */
        public void destroyMap(Player player) {
            ((CraftPlayer) player).getHandle().connection.send(new ClientboundRemoveEntitiesPacket(frameId));
        }
    }

    /**
     * マップをクライアントに送信します。
     */
    public static class MapDisplay {
        /**
         * マップUUID開始番号
         */
        private static final int MAP_STARTING_ID = 1_000_000 - 1000; // Imagesプラグインが1_000_000から開始するため重複しない値を設定
        private static final AtomicInteger MAP_ID_COUNTER = new AtomicInteger(MAP_STARTING_ID);

        /**
         * ピクセルデータ
         */
        public final byte[] pixels;
        /**
         * マップID
         */
        public final int mapId;

        /**
         * マップレンダラーを作成します。
         *
         * @param image 画像
         */
        public MapDisplay(BufferedImage image) {
            this.pixels = createPixels(image);
            this.mapId = MAP_ID_COUNTER.getAndIncrement();
        }

        /**
         * 画像からピクセルデータを作成します。
         *
         * @param image 画像
         * @return ピクセルデータ
         */
        @SuppressWarnings("removal")
        private byte[] createPixels(BufferedImage image) {
            int pixelCount = image.getWidth() * image.getHeight();
            int[] pixels = new int[pixelCount];
            image.getRGB(0, 0, image.getWidth(), image.getHeight(), pixels, 0, image.getWidth());

            byte[] colors = new byte[pixelCount];
            for (int i = 0; i < pixelCount; i++) {
                colors[i] = MapPalette.matchColor(new Color(pixels[i], true));
            }

            return colors;
        }
    }
}
