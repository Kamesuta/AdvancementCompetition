package com.kamesuta.advrank.ranking;

import com.kamesuta.advrank.data.RankingProgressData;
import com.kamesuta.advrank.database.DatabaseManager;
import com.kamesuta.advrank.util.AdvancementUtil;
import org.bukkit.Bukkit;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.kamesuta.advrank.util.AdvancementUtil.bytesToUuid;
import static com.kamesuta.advrank.util.AdvancementUtil.uuidToBytes;

/**
 * ランキング機能のビジネスロジックを担当するサービスクラス
 * 実績のランキング取得・計算処理を提供する
 */
public class RankingService {
    private static final Logger logger = Logger.getLogger(RankingService.class.getName());
    
    private final DatabaseManager databaseManager;
    
    public RankingService(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }
    
    /**
     * 実績の進捗データを取得する
     * 
     * @param player 対象プレイヤー
     * @param advancement 対象実績
     * @param limitTop 上位プレイヤーの表示数
     * @param limitBottom 下位プレイヤーの表示数
     * @return ランキング進捗データ
     */
    public RankingProgressData getAdvancementProgressData(Player player, Advancement advancement, int limitTop, int limitBottom) {
        var key = advancement.getKey().asString();
        var total = -1;
        var done = -1;
        RankingProgressData.PlayerProgress progress;
        var top = new ArrayList<RankingProgressData.PlayerProgress>();
        var bottom = new ArrayList<RankingProgressData.PlayerProgress>();

        try {
            total = getTotalPlayerCount();
            done = getCompletedPlayerCount(key);
            progress = getPlayerProgress(player, key);
            
            if (limitTop > 0) {
                top = getTopPlayers(key, limitTop);
            }
            
            if (limitBottom > 0) {
                bottom = getBottomPlayers(key, limitBottom);
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "進捗データの取得に失敗しました", e);
            return null;
        }

        return new RankingProgressData(total, done, progress, top, bottom);
    }
    
    /**
     * 総プレイヤー数を取得する
     */
    private int getTotalPlayerCount() throws SQLException {
        try (var pstmt = databaseManager.getConnection().prepareStatement("SELECT COUNT(*) FROM player;")) {
            var rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }
    
    /**
     * 指定された実績を達成したプレイヤー数を取得する
     */
    private int getCompletedPlayerCount(String advancementKey) throws SQLException {
        var sql = """
            SELECT COUNT(*) FROM player_advancement pa
             JOIN advancement a ON pa.advancement_id = a.id
             WHERE a.advancement_key = ?;
            """;
        try (var pstmt = databaseManager.getConnection().prepareStatement(sql)) {
            pstmt.setString(1, advancementKey);
            var rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }
    
    /**
     * プレイヤーの実績進捗情報を取得する
     */
    private RankingProgressData.PlayerProgress getPlayerProgress(Player player, String advancementKey) throws SQLException {
        var sql = """
            SELECT p.uuid, pa.timestamp, RANK() OVER(ORDER BY pa.timestamp DESC)
             FROM player_advancement pa
             JOIN player p ON pa.player_id = p.id
             JOIN advancement a ON pa.advancement_id = a.id
             WHERE p.uuid = ? AND a.advancement_key = ?
             LIMIT 1;
            """;
        try (var pstmt = databaseManager.getConnection().prepareStatement(sql)) {
            pstmt.setBytes(1, uuidToBytes(player.getUniqueId()));
            pstmt.setString(2, advancementKey);
            var rs = pstmt.executeQuery();
            
            if (rs.next()) {
                var offlinePlayer = Bukkit.getOfflinePlayer(bytesToUuid(rs.getBytes(1)));
                var timestamp = rs.getTimestamp(2);
                var rank = rs.getInt(3);
                return new RankingProgressData.PlayerProgress(offlinePlayer, timestamp.toInstant(), rank);
            }
        }
        return null;
    }
    
    /**
     * 上位プレイヤーのランキングを取得する
     */
    private ArrayList<RankingProgressData.PlayerProgress> getTopPlayers(String advancementKey, int limit) throws SQLException {
        var players = new ArrayList<RankingProgressData.PlayerProgress>();
        var sql = """
            SELECT p.uuid, pa.timestamp, RANK() OVER(ORDER BY pa.timestamp ASC)
             FROM player_advancement pa
             JOIN player p ON pa.player_id = p.id
             JOIN advancement a ON pa.advancement_id = a.id
             WHERE a.advancement_key = ?
             ORDER BY pa.timestamp ASC
             LIMIT ?;
            """;
        try (var pstmt = databaseManager.getConnection().prepareStatement(sql)) {
            pstmt.setString(1, advancementKey);
            pstmt.setInt(2, limit);
            var rs = pstmt.executeQuery();
            
            while (rs.next()) {
                var offlinePlayer = Bukkit.getOfflinePlayer(bytesToUuid(rs.getBytes(1)));
                var timestamp = rs.getTimestamp(2);
                var rank = rs.getInt(3);
                players.add(new RankingProgressData.PlayerProgress(offlinePlayer, timestamp.toInstant(), rank));
            }
        }
        return players;
    }
    
    /**
     * 下位プレイヤーのランキングを取得する
     */
    private ArrayList<RankingProgressData.PlayerProgress> getBottomPlayers(String advancementKey, int limit) throws SQLException {
        var players = new ArrayList<RankingProgressData.PlayerProgress>();
        var sql = """
            SELECT * FROM (
                SELECT p.uuid, pa.timestamp, RANK() OVER(ORDER BY pa.timestamp ASC)
                 FROM player_advancement pa
                 JOIN player p ON pa.player_id = p.id
                 JOIN advancement a ON pa.advancement_id = a.id
                 WHERE a.advancement_key = ?
                 ORDER BY pa.timestamp DESC
                 LIMIT ?
            ) AS A ORDER BY timestamp ASC;
            """;
        try (var pstmt = databaseManager.getConnection().prepareStatement(sql)) {
            pstmt.setString(1, advancementKey);
            pstmt.setInt(2, limit);
            var rs = pstmt.executeQuery();
            
            while (rs.next()) {
                var offlinePlayer = Bukkit.getOfflinePlayer(bytesToUuid(rs.getBytes(1)));
                var timestamp = rs.getTimestamp(2);
                var rank = rs.getInt(3);
                players.add(new RankingProgressData.PlayerProgress(offlinePlayer, timestamp.toInstant(), rank));
            }
        }
        return players;
    }
    
    /**
     * 実績IDによるページネーション付きランキングを取得する
     * 
     * @param advancementId 実績ID
     * @param page ページ番号（1から開始）
     * @param pageSize 1ページあたりの件数
     * @return ランキング結果
     */
    public RankingResult getRankingByAdvancementIdWithPagination(int advancementId, int page, int pageSize) {
        var ranking = new ArrayList<RankingEntry>();
        String advancementKey = null;
        var totalCount = 0;
        
        try {
            advancementKey = getAdvancementKey(advancementId);
            if (advancementKey == null) {
                return new RankingResult(ranking, null, 0, page, pageSize);
            }
            
            totalCount = getTotalCountByAdvancementId(advancementId);
            ranking = getRankingData(advancementId, page, pageSize, advancementKey);
            
        } catch (SQLException e) {
            logger.log(Level.WARNING, "ランキングデータの取得に失敗しました", e);
        }
        
        return new RankingResult(ranking, advancementKey, totalCount, page, pageSize);
    }
    
    /**
     * 実績IDから実績キーを取得する
     */
    private String getAdvancementKey(int advancementId) throws SQLException {
        try (var pstmt = databaseManager.getConnection().prepareStatement(
                "SELECT advancement_key FROM advancement WHERE id = ?;"
        )) {
            pstmt.setInt(1, advancementId);
            var rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getString("advancement_key");
            }
        }
        return null;
    }
    
    /**
     * 指定された実績IDの達成者総数を取得する
     */
    private int getTotalCountByAdvancementId(int advancementId) throws SQLException {
        try (var pstmt = databaseManager.getConnection().prepareStatement(
                "SELECT COUNT(*) FROM player_advancement pa WHERE pa.advancement_id = ?;"
        )) {
            pstmt.setInt(1, advancementId);
            var rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }
    
    /**
     * ページネーション付きランキングデータを取得する
     */
    private ArrayList<RankingEntry> getRankingData(int advancementId, int page, int pageSize, String advancementKey) throws SQLException {
        var ranking = new ArrayList<RankingEntry>();
        var offset = (page - 1) * pageSize;
        var sql = """
            SELECT p.name, pa.timestamp,
             RANK() OVER (ORDER BY pa.timestamp ASC) as rank
             FROM player_advancement pa
             JOIN player p ON pa.player_id = p.id
             WHERE pa.advancement_id = ?
             ORDER BY pa.timestamp ASC
             LIMIT ? OFFSET ?;
            """;
        try (var pstmt = databaseManager.getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, advancementId);
            pstmt.setInt(2, pageSize);
            pstmt.setInt(3, offset);
            var rs = pstmt.executeQuery();
            
            while (rs.next()) {
                var playerName = rs.getString("name");
                var timestamp = rs.getTimestamp("timestamp");
                var rank = rs.getInt("rank");
                ranking.add(new RankingEntry(playerName, timestamp, rank, advancementKey));
            }
        }
        return ranking;
    }
    
    /**
     * プレイヤーの特定実績のランキング情報を取得する
     * 
     * @param advancementId 実績ID
     * @param playerUuid プレイヤーのUUID
     * @return ランキング情報（未達成の場合はnull）
     */
    public RankingEntry getPlayerRankingByAdvancementId(int advancementId, UUID playerUuid) {
        try {
            var advancementKey = getAdvancementKey(advancementId);
            if (advancementKey == null) {
                return null;
            }
            
            var sql = """
                SELECT p.name, pa.timestamp,
                 (SELECT COUNT(*) + 1 FROM player_advancement pa2
                  JOIN player p2 ON pa2.player_id = p2.id
                  WHERE pa2.advancement_id = ? AND pa2.timestamp < pa.timestamp) as rank
                 FROM player_advancement pa
                 JOIN player p ON pa.player_id = p.id
                 WHERE pa.advancement_id = ? AND p.uuid = ?;
                """;
            try (var pstmt = databaseManager.getConnection().prepareStatement(sql)) {
                pstmt.setInt(1, advancementId);
                pstmt.setInt(2, advancementId);
                pstmt.setBytes(3, AdvancementUtil.uuidToBytes(playerUuid));
                var rs = pstmt.executeQuery();
                
                if (rs.next()) {
                    var playerName = rs.getString("name");
                    var timestamp = rs.getTimestamp("timestamp");
                    var rank = rs.getInt("rank");
                    return new RankingEntry(playerName, timestamp, rank, advancementKey);
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "プレイヤーランキングの取得に失敗しました", e);
        }
        
        return null;
    }

    /**
     * ランキング結果のデータクラス（ページネーション対応）
     * 
     * @param entries ランキングエントリのリスト
     * @param advancementKey 実績キー
     * @param totalCount 総件数
     * @param currentPage 現在のページ
     * @param pageSize ページサイズ
     */
    public record RankingResult(
            List<RankingEntry> entries,
            String advancementKey,
            int totalCount,
            int currentPage,
            int pageSize) {
        /**
         * 総ページ数を計算する
         */
        public int getTotalPages() {
            return (int) Math.ceil((double) totalCount / pageSize);
        }

        /**
         * 次のページが存在するかを確認する
         */
        public boolean hasNextPage() {
            return currentPage < getTotalPages();
        }

        /**
         * 前のページが存在するかを確認する
         */
        public boolean hasPreviousPage() {
            return currentPage > 1;
        }
    }

    /**
     * ランキングエントリのデータクラス
     * 
     * @param playerName プレイヤー名
     * @param timestamp 達成日時
     * @param rank ランキング順位
     * @param advancementKey 実績キー
     */
    public record RankingEntry(String playerName, Timestamp timestamp, int rank, String advancementKey) {
    }
}