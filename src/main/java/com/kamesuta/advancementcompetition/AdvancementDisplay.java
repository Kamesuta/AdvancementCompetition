package com.kamesuta.advancementcompetition;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.Listener;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.kamesuta.advancementcompetition.AdvancementCompetition.app;

/**
 * 進捗表示ブロック
 */
public class AdvancementDisplay implements Listener {
    /**
     * マップビュー
     */
    private final List<AdvancementDisplayRenderer> displays = new ArrayList<>();

    /**
     * マップの数
     */
    public static final int mapLength = 3;

    /**
     * マップ
     */
    public List<AdvancementDisplayRenderer.MapDisplay> panel;

    /**
     * マップ (達成済み)
     */
    public List<AdvancementDisplayRenderer.MapDisplay> panelDone;

    /**
     * マップを読み込む
     *
     * @throws IOException マップが読み込めない場合
     */
    public void load() throws IOException {
        // マップ画像を読み込む
        panel = loadMap("panel.png");
        panelDone = loadMap("panel_done.png");
    }

    private List<AdvancementDisplayRenderer.MapDisplay> loadMap(String name) throws IOException {
        // マップ画像を読み込む
        InputStream mapResource = app.getResource(name);
        if (mapResource == null) {
            throw new IOException(name + "が見つかりません");
        }
        BufferedImage image = ImageIO.read(mapResource);

        // mapLength分割
        return IntStream.range(0, mapLength)
                .mapToObj((i) -> {
                    int width = image.getWidth() / mapLength;
                    return image.getSubimage(width * i, 0, width, image.getHeight());
                })
                // マップに変換
                .map(AdvancementDisplayRenderer.MapDisplay::new)
                .collect(Collectors.toList());
    }

    /**
     * パネルを設置
     * @param block 基準ブロック
     * @param face 基準ブロックの向き
     */
    public void place(Block block, BlockFace face) {
        if (!face.isCartesian() || face == BlockFace.UP || face == BlockFace.DOWN) {
            throw new IllegalArgumentException("額縁は横向きに設置してください");
        }

        // 指定したブロックから右にmapLength個のブロックを設置
        for (int i = 0; i < mapLength; i++) {
            // ブロックを取得
            BlockFace rightFace = AdvancementUtil.getRight(face.getOppositeFace());
            Block target = block.getRelative(rightFace, i);
            // ブロックを置く
            target.setType(Material.GLASS, false);
        }

        // マップを取得
        AdvancementDisplayRenderer display = new AdvancementDisplayRenderer(block, face);
        displays.add(display);
        block.getLocation().getNearbyPlayers(16).forEach((player) -> display.show(player));
    }
}
