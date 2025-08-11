package com.kamesuta.advrank;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.advancement.Advancement;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.jetbrains.annotations.Nullable;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

import static com.kamesuta.advrank.AdvancementRankingPlugin.app;
import static com.kamesuta.advrank.AdvancementRankingPlugin.logger;
import static com.kamesuta.advrank.AdvancementUtil.bytesToUuid;
import static com.kamesuta.advrank.AdvancementUtil.uuidToBytes;

/**
 * ランキング管理
 */
public class RankingManager implements AutoCloseable, Listener {
    /**
     * SQLデータベース接続
     */
    private final Connection conn;

    /**
     * SQLデータベースを初期化する
     */
    public RankingManager() throws SQLException {
        // データベースを初期化する
        conn = getConnection();

        // テーブルを作成する
        try (Statement stmt = conn.createStatement()) {
            // playerテーブル
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS player (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY," +
                            "uuid BINARY(16) UNIQUE NOT NULL," +
                            "name VARCHAR(16) NOT NULL," +
                            "INDEX idx_uuid (uuid)" +
                            ");"
            );

            // advancementテーブル
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS advancement (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY," +
                            "advancement_key VARCHAR(255) UNIQUE NOT NULL" +
                            ");"
            );

            // player_advancementテーブル
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS player_advancement (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY," +
                            "player_id INT NOT NULL," +
                            "advancement_id INT NOT NULL," +
                            "timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                            "UNIQUE KEY unique_player_advancement (player_id, advancement_id)," +
                            "FOREIGN KEY (player_id) REFERENCES player(id)," +
                            "FOREIGN KEY (advancement_id) REFERENCES advancement(id)," +
                            "INDEX idx_advancement_id (advancement_id)," +
                            "INDEX idx_timestamp (timestamp)" +
                            ");"
            );
        }
    }

    @Override
    public void close() throws SQLException {
        if (conn != null) {
            conn.close();
        }
    }

    /**
     * データベースに接続する
     *
     * @return 接続
     * @throws SQLException SQL例外
     */
    private Connection getConnection() throws SQLException {
        // データベースを初期化する
        FileConfiguration config = app.getConfig();
        String url = "jdbc:mysql://" + config.getString("mysql.host") + ":" + config.getString("mysql.port") + "/" + config.getString("mysql.databaseName");
        String username = config.getString("mysql.username");
        String password = config.getString("mysql.password");

        // SQLデータベースに接続する
        return DriverManager.getConnection(url, username, password);
    }

    /**
     * データベースにpingを送信して接続を維持する
     */
    public void pingDatabase() {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeQuery("SELECT 1;");
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "KeepAliveパケット(ping)の送信に失敗しました", e);
        }
    }

    /**
     * プレイヤーIDを取得または作成する
     * @param uuid プレイヤーのUUID
     * @param name プレイヤー名
     * @return プレイヤーID
     */
    private int getOrCreatePlayerId(UUID uuid, String name) throws SQLException {
        byte[] uuidBytes = AdvancementUtil.uuidToBytes(uuid);
        
        // まず既存のプレイヤーIDを取得
        String selectSql = "SELECT id FROM player WHERE uuid = ?";
        try (PreparedStatement selectStmt = conn.prepareStatement(selectSql)) {
            selectStmt.setBytes(1, uuidBytes);
            try (ResultSet rs = selectStmt.executeQuery()) {
                if (rs.next()) {
                    // 既存のプレイヤーが見つかった場合、名前を更新
                    String updateSql = "UPDATE player SET name = ? WHERE id = ?";
                    try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                        updateStmt.setString(1, name);
                        updateStmt.setInt(2, rs.getInt("id"));
                        updateStmt.executeUpdate();
                    }
                    return rs.getInt("id");
                }
            }
        }
        
        // 新規プレイヤーを作成
        String insertSql = "INSERT INTO player (uuid, name) VALUES (?, ?)";
        try (PreparedStatement insertStmt = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
            insertStmt.setBytes(1, uuidBytes);
            insertStmt.setString(2, name);
            insertStmt.executeUpdate();
            
            try (ResultSet rs = insertStmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        
        throw new SQLException("Failed to create player record");
    }

    /**
     * 実績IDを取得または作成する
     * @param advancementKey 実績のキー
     * @return 実績ID
     */
    private int getOrCreateAdvancementId(String advancementKey) throws SQLException {
        // まず既存の実績IDを取得
        String selectSql = "SELECT id FROM advancement WHERE advancement_key = ?";
        try (PreparedStatement selectStmt = conn.prepareStatement(selectSql)) {
            selectStmt.setString(1, advancementKey);
            try (ResultSet rs = selectStmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
        }
        
        // 新規実績を作成
        String insertSql = "INSERT INTO advancement (advancement_key) VALUES (?)";
        try (PreparedStatement insertStmt = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
            insertStmt.setString(1, advancementKey);
            insertStmt.executeUpdate();
            
            try (ResultSet rs = insertStmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        
        throw new SQLException("Failed to create advancement record");
    }

    /**
     * プレイヤーの進捗を更新する
     *
     * @param event 進捗イベント
     */
    @EventHandler
    public void onAdvancementDone(PlayerAdvancementDoneEvent event) {
        // レシピなどは無視
        if (event.getAdvancement().getKey().asString().startsWith("minecraft:recipes/")) return;

        // プレイヤーの進捗を更新する
        String key = event.getAdvancement().getKey().asString();
        // プレイヤーIDを取得
        Player player = event.getPlayer();

        // 進捗を記録
        recordAdvancementProgressData(player.getUniqueId(), player.getName(), key, null);
    }

    /**
     * プレイヤーの進捗を記録する
     *
     * @param name プレイヤー名
     * @param uuid プレイヤーのUUID
     * @param key  進捗のキー
     */
    public void recordAdvancementProgressData(UUID uuid, String name, String key, Timestamp timestamp) {
        // SQLに書き込み
        try {
            // プレイヤーIDを取得または作成
            int playerId = getOrCreatePlayerId(uuid, name);
            
            // 実績IDを取得または作成
            int advancementId = getOrCreateAdvancementId(key);
            
            // player_advancementテーブルに記録
            String sql = timestamp == null 
                ? "INSERT IGNORE INTO player_advancement (player_id, advancement_id, timestamp) VALUES (?, ?, NOW());"
                : "INSERT IGNORE INTO player_advancement (player_id, advancement_id, timestamp) VALUES (?, ?, ?);";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, playerId);
                pstmt.setInt(2, advancementId);
                if (timestamp != null) {
                    pstmt.setTimestamp(3, timestamp);
                }
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "進捗の保存に失敗しました", e);
        }
    }

    /**
     * 進捗データ取得
     *
     * @param player      プレイヤー
     * @param advancement 進捗
     * @param limitTop    上位の表示数
     * @param limitBottom 下位の表示数
     */
    public @Nullable RankingProgressData getAdvancementProgressData(Player player, Advancement advancement, int limitTop, int limitBottom) {
        // プレイヤーの進捗を更新する
        String key = advancement.getKey().asString();

        // 結果
        int total = -1;
        int done = -1;
        RankingProgressData.PlayerProgress progress = null;
        List<RankingProgressData.PlayerProgress> top = new ArrayList<>();
        List<RankingProgressData.PlayerProgress> bottom = new ArrayList<>();

        try {
            // 全プレイヤー数を取得
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "SELECT COUNT(*) FROM player;"
            )) {
                // SQLを実行
                ResultSet rs = pstmt.executeQuery();

                // 結果を取得
                if (rs.next()) {
                    total = rs.getInt(1);
                }
            }

            // 達成したプレイヤーの数を取得
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "SELECT COUNT(*) FROM player_advancement pa " +
                    "JOIN advancement a ON pa.advancement_id = a.id " +
                    "WHERE a.advancement_key = ?;"
            )) {
                // SQLを実行
                pstmt.setString(1, key);
                ResultSet rs = pstmt.executeQuery();

                // 結果を取得
                if (rs.next()) {
                    done = rs.getInt(1);
                }
            }

            // 現在のプレイヤーの進捗を取得
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "SELECT p.uuid, pa.timestamp, RANK() OVER(ORDER BY pa.timestamp DESC) FROM player_advancement pa " +
                            "JOIN player p ON pa.player_id = p.id " +
                            "JOIN advancement a ON pa.advancement_id = a.id " +
                            "WHERE p.uuid = ? AND a.advancement_key = ? " +
                            "LIMIT 1;"
            )) {
                // SQLを実行
                pstmt.setBytes(1, uuidToBytes(player.getUniqueId()));
                pstmt.setString(2, key);
                ResultSet rs = pstmt.executeQuery();

                // 結果を取得
                if (rs.next()) {
                    OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(bytesToUuid(rs.getBytes(1)));
                    Timestamp timestamp = rs.getTimestamp(2);
                    int rank = rs.getInt(3);
                    progress = new RankingProgressData.PlayerProgress(offlinePlayer, timestamp.toInstant(), rank);
                }
            }

            // 上位のプレイヤーの進捗を取得
            if (limitTop > 0) {
                try (PreparedStatement pstmt = conn.prepareStatement(
                        "SELECT p.uuid, pa.timestamp, RANK() OVER(ORDER BY pa.timestamp ASC) FROM player_advancement pa " +
                                "JOIN player p ON pa.player_id = p.id " +
                                "JOIN advancement a ON pa.advancement_id = a.id " +
                                "WHERE a.advancement_key = ? " +
                                "ORDER BY pa.timestamp ASC " +
                                "LIMIT ?;"
                )) {
                    // SQLを実行
                    pstmt.setString(1, key);
                    pstmt.setInt(2, limitTop);
                    ResultSet rs = pstmt.executeQuery();

                    // 結果を取得
                    while (rs.next()) {
                        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(bytesToUuid(rs.getBytes(1)));
                        Timestamp timestamp = rs.getTimestamp(2);
                        int rank = rs.getInt(3);
                        top.add(new RankingProgressData.PlayerProgress(offlinePlayer, timestamp.toInstant(), rank));
                    }
                }
            }

            // 下位のプレイヤーの進捗を取得
            if (limitBottom > 0) {
                try (PreparedStatement pstmt = conn.prepareStatement(
                        "SELECT * FROM (" +
                                "SELECT p.uuid, pa.timestamp, RANK() OVER(ORDER BY pa.timestamp ASC) FROM player_advancement pa " +
                                "JOIN player p ON pa.player_id = p.id " +
                                "JOIN advancement a ON pa.advancement_id = a.id " +
                                "WHERE a.advancement_key = ? " +
                                "ORDER BY pa.timestamp DESC " +
                                "LIMIT ?" +
                                ") AS A ORDER BY timestamp ASC;"
                )) {
                    // SQLを実行
                    pstmt.setString(1, key);
                    pstmt.setInt(2, limitBottom);
                    ResultSet rs = pstmt.executeQuery();

                    // 結果を取得
                    while (rs.next()) {
                        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(bytesToUuid(rs.getBytes(1)));
                        Timestamp timestamp = rs.getTimestamp(2);
                        int rank = rs.getInt(3);
                        bottom.add(new RankingProgressData.PlayerProgress(offlinePlayer, timestamp.toInstant(), rank));
                    }
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "進捗データの取得に失敗しました", e);
            return null;
        }

        return new RankingProgressData(total, done, progress, top, bottom);
    }
    
    /**
     * 実績IDによるランキングデータを取得（ページネーション対応）
     *
     * @param advancementId 実績ID
     * @param page ページ番号（1から開始）
     * @param pageSize 1ページあたりの件数
     * @return ランキング結果
     */
    public RankingResult getRankingByAdvancementIdWithPagination(int advancementId, int page, int pageSize) {
        List<RankingEntry> ranking = new ArrayList<>();
        String advancementKey = null;
        int totalCount = 0;
        
        try {
            // まず実績キーを取得
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "SELECT advancement_key FROM advancement WHERE id = ?;"
            )) {
                pstmt.setInt(1, advancementId);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    advancementKey = rs.getString("advancement_key");
                }
            }
            
            if (advancementKey == null) {
                return new RankingResult(ranking, null, 0, page, pageSize);
            }
            
            // 総件数を取得
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "SELECT COUNT(*) FROM player_advancement pa WHERE pa.advancement_id = ?;"
            )) {
                pstmt.setInt(1, advancementId);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    totalCount = rs.getInt(1);
                }
            }
            
            // ページネーション付きランキングデータを取得
            int offset = (page - 1) * pageSize;
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "SELECT p.name, pa.timestamp, " +
                    "RANK() OVER (ORDER BY pa.timestamp ASC) as rank " +
                    "FROM player_advancement pa " +
                    "JOIN player p ON pa.player_id = p.id " +
                    "WHERE pa.advancement_id = ? " +
                    "ORDER BY pa.timestamp ASC " +
                    "LIMIT ? OFFSET ?;"
            )) {
                pstmt.setInt(1, advancementId);
                pstmt.setInt(2, pageSize);
                pstmt.setInt(3, offset);
                ResultSet rs = pstmt.executeQuery();
                
                while (rs.next()) {
                    String playerName = rs.getString("name");
                    Timestamp timestamp = rs.getTimestamp("timestamp");
                    int rank = rs.getInt("rank");
                    ranking.add(new RankingEntry(playerName, timestamp, rank, advancementKey));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "ランキングデータの取得に失敗しました", e);
        }
        
        return new RankingResult(ranking, advancementKey, totalCount, page, pageSize);
    }
    
    /**
     * プレイヤーの実績ランキングを取得
     *
     * @param advancementId 実績ID
     * @param playerUuid プレイヤーのUUID
     * @return プレイヤーのランキング情報（未達成の場合はnull）
     */
    public RankingEntry getPlayerRankingByAdvancementId(int advancementId, UUID playerUuid) {
        try {
            // 実績キーを取得
            String advancementKey = null;
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "SELECT advancement_key FROM advancement WHERE id = ?;"
            )) {
                pstmt.setInt(1, advancementId);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    advancementKey = rs.getString("advancement_key");
                }
            }
            
            if (advancementKey == null) {
                return null;
            }
            
            // プレイヤーのランキングを取得
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "SELECT p.name, pa.timestamp, " +
                    "(SELECT COUNT(*) + 1 FROM player_advancement pa2 " +
                    " JOIN player p2 ON pa2.player_id = p2.id " +
                    " WHERE pa2.advancement_id = ? AND pa2.timestamp < pa.timestamp) as rank " +
                    "FROM player_advancement pa " +
                    "JOIN player p ON pa.player_id = p.id " +
                    "WHERE pa.advancement_id = ? AND p.uuid = ?;"
            )) {
                pstmt.setInt(1, advancementId);
                pstmt.setInt(2, advancementId);
                pstmt.setBytes(3, AdvancementUtil.uuidToBytes(playerUuid));
                ResultSet rs = pstmt.executeQuery();
                
                if (rs.next()) {
                    String playerName = rs.getString("name");
                    Timestamp timestamp = rs.getTimestamp("timestamp");
                    int rank = rs.getInt("rank");
                    return new RankingEntry(playerName, timestamp, rank, advancementKey);
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "プレイヤーランキングの取得に失敗しました", e);
        }
        
        return null;
    }

    /**
     * 実績キーから実績IDを取得
     *
     * @param advancementKey 実績キー
     * @return 実績ID（見つからない場合は-1）
     */
    public int getAdvancementIdByKey(String advancementKey) {
        try (PreparedStatement pstmt = conn.prepareStatement(
                "SELECT id FROM advancement WHERE advancement_key = ?;"
        )) {
            pstmt.setString(1, advancementKey);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("id");
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "実績IDの取得に失敗しました", e);
        }
        return -1;
    }

    /**
     * 実績IDの存在確認
     *
     * @param advancementId 実績ID
     * @return 存在する場合true
     */
    public boolean isAdvancementIdExists(int advancementId) {
        try (PreparedStatement pstmt = conn.prepareStatement(
                "SELECT COUNT(*) FROM advancement WHERE id = ?;"
        )) {
            pstmt.setInt(1, advancementId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "実績IDの存在確認に失敗しました", e);
        }
        return false;
    }

    /**
     * ランキング結果のデータクラス（ページネーション対応）
     */
    public record RankingResult(
            List<RankingEntry> entries,
            String advancementKey,
            int totalCount,
            int currentPage,
            int pageSize) {
        public int getTotalPages() {
            return (int) Math.ceil((double) totalCount / pageSize);
        }

        public boolean hasNextPage() {
            return currentPage < getTotalPages();
        }

        public boolean hasPreviousPage() {
            return currentPage > 1;
        }
    }

    /**
     * ランキングエントリーのデータクラス
     */
    public record RankingEntry(String playerName, Timestamp timestamp, int rank, String advancementKey) {
    }
}
