package com.kamesuta.advancementcompetition.display;

import org.bukkit.map.MapPalette;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * マップをクライアントに送信します。
 */
public class MapDisplay {
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
