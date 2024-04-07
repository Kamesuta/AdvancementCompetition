package com.kamesuta.advancementcompetition;

import org.bukkit.block.BlockFace;

import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

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
}
