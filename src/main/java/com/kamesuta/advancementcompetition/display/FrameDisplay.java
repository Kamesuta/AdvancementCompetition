package com.kamesuta.advancementcompetition.display;

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
import org.bukkit.block.BlockFace;
import org.bukkit.craftbukkit.v1_20_R3.CraftWorld;
import org.bukkit.craftbukkit.v1_20_R3.block.CraftBlock;
import org.bukkit.craftbukkit.v1_20_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 額縁
 */
public class FrameDisplay {
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
    public void createMap(Player player, MapDisplay map) {
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
