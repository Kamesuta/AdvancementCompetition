package com.kamesuta.advancementcompetition;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.kamesuta.advancementcompetition.AdvancementUtil.TIME_FORMATTER;

/**
 * ランキングの進捗データ
 */
public class RankingProgressData {
    /**
     * トータルのプレイヤー数
     */
    public final int total;
    /**
     * 達成したプレイヤーの数
     */
    public final int done;
    /**
     * 現在のプレイヤーの進捗
     */
    public final @Nullable PlayerProgress progress;
    /**
     * 上位のプレイヤーの進捗
     */
    public final List<PlayerProgress> top;
    /**
     * 下位のプレイヤーの進捗
     */
    public final List<PlayerProgress> bottom;

    public RankingProgressData(int total, int done, @Nullable PlayerProgress progress, List<PlayerProgress> top, List<PlayerProgress> bottom) {
        this.total = total;
        this.done = done;
        this.progress = progress;
        this.top = top;
        this.bottom = bottom;
    }

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
     * ランキングが足りない場合に空白行を追加します
     *
     * @param description   出力先
     * @param expectedLines 期待される行数
     * @param shownLines    表示された行数
     */
    public void appendBlankLines(MutableComponent description, int expectedLines, int shownLines) {
        description.append(IntStream.range(0, Math.max(0, expectedLines - shownLines)).mapToObj(i -> "\n").collect(Collectors.joining()));
    }

    /**
     * プレイヤーの進捗
     */
    public static class PlayerProgress {
        /**
         * プレイヤー
         */
        public final OfflinePlayer player;
        /**
         * 進捗のタイムスタンプ
         */
        public final Instant timestamp;
        /**
         * 順位
         */
        public final int rank;

        public PlayerProgress(OfflinePlayer player, Instant timestamp, int rank) {
            this.player = player;
            this.timestamp = timestamp;
            this.rank = rank;
        }
    }
}
