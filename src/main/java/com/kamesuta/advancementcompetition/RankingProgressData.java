package com.kamesuta.advancementcompetition;

import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;

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
