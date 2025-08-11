package com.kamesuta.advrank.database;

import com.kamesuta.advrank.data.RankingProgressData;
import com.kamesuta.advrank.ranking.RankingService;

import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.jetbrains.annotations.Nullable;

import java.sql.*;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.Level;

import static com.kamesuta.advrank.AdvRankingPlugin.app;
import static com.kamesuta.advrank.util.AdvancementUtil.bytesToUuid;
import static com.kamesuta.advrank.util.AdvancementUtil.uuidToBytes;

/**
 * ランキング機能の統合管理クラス
 * 旧来のRankingManagerの互換性を保つためのラッパークラス
 * 実際の処理は各リポジトリとサービスクラスに委譲する
 */
public class RankingManager implements AutoCloseable, Listener {
    private static final Logger logger = Logger.getLogger(RankingManager.class.getName());
    
    // 各コンポーネントのインスタンス
    private final DatabaseManager databaseManager;
    private final PlayerRepository playerRepository;
    private final AdvancementRepository advancementRepository;
    private final RankingService rankingService;

    /**
     * コンストラクタ
     * 各コンポーネントを初期化する
     */
    public RankingManager() throws SQLException {
        this.databaseManager = new DatabaseManager();
        this.playerRepository = new PlayerRepository(databaseManager);
        this.advancementRepository = new AdvancementRepository(databaseManager);
        this.rankingService = new RankingService(databaseManager);
    }

    @Override
    public void close() throws SQLException {
        if (databaseManager != null) {
            databaseManager.close();
        }
    }

    /**
     * データベースにpingを送信して接続を維持する
     */
    public void pingDatabase() {
        databaseManager.pingDatabase();
    }

    /**
     * プレイヤーが実績を達成した時のイベントハンドラ
     */
    @EventHandler
    public void onAdvancementDone(PlayerAdvancementDoneEvent event) {
        // レシピ実績は無視
        if (event.getAdvancement().getKey().asString().startsWith("minecraft:recipes/")) {
            return;
        }

        // 実績達成を記録
        var key = event.getAdvancement().getKey().asString();
        var player = event.getPlayer();
        recordAdvancementProgressData(player.getUniqueId(), player.getName(), key, null);
    }

    /**
     * プレイヤーの実績達成を記録する
     * 
     * @param uuid プレイヤーのUUID
     * @param name プレイヤー名
     * @param key 実績キー
     * @param timestamp 達成日時（nullの場合は現在時刻）
     */
    public void recordAdvancementProgressData(UUID uuid, String name, String key, Timestamp timestamp) {
        try {
            // リポジトリを使用してデータを記録
            var playerId = playerRepository.getOrCreatePlayerId(uuid, name);
            var advancementId = advancementRepository.getOrCreateAdvancementId(key);
            advancementRepository.recordPlayerAdvancement(playerId, advancementId, timestamp);
        } catch (SQLException e) {
            logger.log(Level.WARNING, "進捗の保存に失敗しました", e);
        }
    }

    /**
     * 実績の進捗データを取得する
     */
    public @Nullable RankingProgressData getAdvancementProgressData(Player player, Advancement advancement, int limitTop, int limitBottom) {
        return rankingService.getAdvancementProgressData(player, advancement, limitTop, limitBottom);
    }
    
    /**
     * ページネーション付きランキングを取得する
     */
    public RankingService.RankingResult getRankingByAdvancementIdWithPagination(int advancementId, int page, int pageSize) {
        return rankingService.getRankingByAdvancementIdWithPagination(advancementId, page, pageSize);
    }
    
    /**
     * プレイヤーの特定実績のランキングを取得する
     */
    public RankingService.RankingEntry getPlayerRankingByAdvancementId(int advancementId, UUID playerUuid) {
        return rankingService.getPlayerRankingByAdvancementId(advancementId, playerUuid);
    }

    /**
     * 実績キーから実績IDを取得する
     */
    public int getAdvancementIdByKey(String advancementKey) {
        return advancementRepository.getAdvancementIdByKey(advancementKey);
    }

    /**
     * 実績IDの存在確認
     */
    public boolean isAdvancementIdExists(int advancementId) {
        return advancementRepository.isAdvancementIdExists(advancementId);
    }
}
