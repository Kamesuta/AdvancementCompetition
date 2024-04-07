package com.kamesuta.advancementcompetition.display;

import com.kamesuta.advancementcompetition.AdvancementUtil;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;

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
}
