package com.kamesuta.advrank.util;

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
