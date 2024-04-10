package com.kamesuta.advancementcompetition;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * ユーティリティ
 */
public class AdvancementUtil {
    /**
     * ランキング表示時の日時フォーマット
     */
    public static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("MM/dd HH:mm").withZone(ZoneId.systemDefault());

    /**
     * 文字列を指定の長さにフォーマットします
     *
     * @param target フォーマット対象の文字列
     * @param length フォーマット後の長さ
     * @return フォーマットされた文字列
     */
    public static String formatLength(String target, int length) {
        int byteDiff = (getByteLength(target) - target.length()) / 2;
        return String.format("%-" + (length - byteDiff) + "s", target);
    }

    /**
     * 文字列のバイト長を取得します
     *
     * @param string 文字列
     * @return バイト長
     */
    private static int getByteLength(String string) {
        return string.getBytes(StandardCharsets.UTF_8).length;
    }

    /**
     * ブロックの右側を取得
     *
     * @param face ブロックの向き
     * @return 右側の向き
     */
    public static BlockFace getRight(BlockFace face) {
        switch (face) {
            case NORTH:
                return BlockFace.EAST;
            case EAST:
                return BlockFace.SOUTH;
            case SOUTH:
                return BlockFace.WEST;
            case WEST:
                return BlockFace.NORTH;
            default:
                return face;
        }
    }

    /**
     * ブロックの向きを取得
     *
     * @param direction ブロックの向き
     * @return 回転角度 (ラジアン)
     */
    public static float getRotate(BlockFace direction) {
        int modX = direction.getModX();
        int modZ = direction.getModZ();
        return (float) Math.atan2(modZ, modX);
    }

    /**
     * ブロックかどうか判定
     *
     * @param type ブロックの種類
     * @return ブロックかどうか
     */
    public static boolean isBlock(Material type) {
        return type.isSolid() && !type.toString().equals("AMETHYST_CLUSTER") && !type.toString().equals("POINTED_DRIPSTONE");
    }

    /**
     * UUIDをバイト配列に変換
     *
     * @param uuid UUID
     * @return バイト配列
     */
    public static byte[] uuidToBytes(UUID uuid) {
        return ByteBuffer.wrap(new byte[16])
                .order(java.nio.ByteOrder.BIG_ENDIAN)
                .putLong(uuid.getMostSignificantBits())
                .putLong(uuid.getLeastSignificantBits())
                .array();
    }

    /**
     * バイト配列をUUIDに変換
     *
     * @param bytes バイト配列
     * @return UUID
     */
    public static UUID bytesToUuid(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes)
                .order(java.nio.ByteOrder.BIG_ENDIAN);
        return new UUID(buffer.getLong(), buffer.getLong());
    }
}
