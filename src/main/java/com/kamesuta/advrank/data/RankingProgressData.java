package com.kamesuta.advrank.data;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.kamesuta.advrank.util.AdvancementUtil.TIME_FORMATTER;

/**
 * ランキングの進捗データ
 *
 * @param total    トータルのプレイヤー数
 * @param done     達成したプレイヤーの数
 * @param progress 現在のプレイヤーの進捗
 * @param top      上位のプレイヤーの進捗
 * @param bottom   下位のプレイヤーの進捗
 */
public record RankingProgressData(
        int total,
        int done,
        @Nullable PlayerProgress progress,
        List<PlayerProgress> top,
        List<PlayerProgress> bottom
) {

    /**
     * 進捗説明を追加します
     *
     * @param description 出力先
     */
    public void appendProgressDescription(MutableComponent description) {
        // 説明 「進捗説明\n\nクリア率:%d/%d人(%d位)」
        String rank = (progress != null && progress.rank >= 0) ? progress.rank + "位" : "未達成";
        description.append(Component.literal(String.format("クリア率:%d/%d人(%s)", done, total, rank)).withStyle(ChatFormatting.YELLOW));
        // 取得日時を追加
        if (progress != null) {
            // MM/dd HH:mm形式に変換
            String date = TIME_FORMATTER.format(progress.timestamp);
            description.append(" ")
                    .append(Component.literal(date + "取得").withStyle(ChatFormatting.GRAY));
        }
    }

    /**
     * ランキングを追加します
     *
     * @param description 出力先
     * @param ranking     ランキング
     */
    public void appendRanking(MutableComponent description, List<PlayerProgress> ranking) {
        for (PlayerProgress progress : ranking) {
            // プレイヤー名
            MutableComponent name = Component.literal(Optional.ofNullable(progress.player.getName()).orElse("不明"));
            if (this.progress != null && progress.player.equals(this.progress.player)) {
                // 自分の進捗
                name.withStyle(ChatFormatting.GREEN);
            }

            // ランク
            MutableComponent rank = Component.literal(String.format("%d位", progress.rank));
            switch (progress.rank) {
                case 1:
                    rank.withStyle(ChatFormatting.GOLD);
                    break;
                case 2:
                    rank.withStyle(ChatFormatting.AQUA);
                    break;
                case 3:
                    rank.withStyle(ChatFormatting.RED);
                    break;
            }

            // 進捗を取得
            String time = TIME_FORMATTER.format(progress.timestamp);
            // ランキングを追加
            description.append("\n")
                    .append(Component.empty().append(rank).append(":").append(name).append(String.format("(%s)", time)).withStyle(ChatFormatting.GRAY));
        }
    }

    /**
     * プレイヤーの進捗
     *
     * @param player    プレイヤー
     * @param timestamp 進捗のタイムスタンプ
     * @param rank      順位
     */
    public record PlayerProgress(OfflinePlayer player, Instant timestamp, int rank) {
    }
}
