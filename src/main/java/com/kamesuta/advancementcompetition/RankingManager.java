package com.kamesuta.advancementcompetition;

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

import static com.kamesuta.advancementcompetition.AdvancementCompetition.app;
import static com.kamesuta.advancementcompetition.AdvancementCompetition.logger;
import static com.kamesuta.advancementcompetition.AdvancementUtil.bytesToUuid;
import static com.kamesuta.advancementcompetition.AdvancementUtil.uuidToBytes;

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
        FileConfiguration config = app.getConfig();
        String url = "jdbc:mysql://" + config.getString("mysql.host") + ":" + config.getString("mysql.port") + "/" + config.getString("mysql.databaseName");
        String username = config.getString("mysql.username");
        String password = config.getString("mysql.password");

        // SQLデータベースに接続する
        conn = DriverManager.getConnection(url, username, password);

        // テーブルを作成する
        try (Statement stmt = conn.createStatement()) {
            // プレイヤーデータベース
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS player (" +
                            "uuid BINARY(16) NOT NULL PRIMARY KEY," +
                            "name VARCHAR(20) NOT NULL" +
                            ");"
            );

            // 進捗データベース
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS progress (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY," +
                            "player_uuid BINARY(16) NOT NULL," +
                            "advancement_key VARCHAR(100) NOT NULL," +
                            "timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                            "FOREIGN KEY (player_uuid) REFERENCES player(uuid)," +
                            "UNIQUE (player_uuid, advancement_key)" +
                            ");"
            );

            // ランキングデータベース
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS ranking (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY," +
                            "player_uuid BINARY(16) NOT NULL," +
                            "advancement_key VARCHAR(100) NOT NULL," +
                            "score INT NOT NULL," +
                            "FOREIGN KEY (player_uuid) REFERENCES player(uuid)," +
                            "UNIQUE (player_uuid, advancement_key)" +
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
        recordAdvancementProgressData(player.getUniqueId(), player.getName(), key);
    }

    /**
     * プレイヤーの進捗を記録する
     *
     * @param name プレイヤー名
     * @param uuid プレイヤーのUUID
     * @param key  進捗のキー
     */
    private void recordAdvancementProgressData(UUID uuid, String name, String key) {
        // SQLに書き込み
        try {
            // プレイヤー情報の挿入または更新
            String sqlPlayer = "INSERT IGNORE INTO player (uuid, name) VALUES (?, ?);";
            try (PreparedStatement pstmt = conn.prepareStatement(sqlPlayer)) {
                // SQLを実行
                pstmt.setBytes(1, uuidToBytes(uuid));
                pstmt.setString(2, name);
                pstmt.executeUpdate();
            }

            // 進捗の追加
            String sqlProgress = "INSERT IGNORE INTO progress (player_uuid, advancement_key, timestamp) VALUES (?, ?, NOW());";
            try (PreparedStatement pstmt = conn.prepareStatement(sqlProgress)) {
                // SQLを実行
                pstmt.setBytes(1, uuidToBytes(uuid));
                pstmt.setString(2, key);
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "進捗の保存に失敗しました", e);
        }
    }

    /**
     * 進捗データ取得
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
                    "SELECT COUNT(*) FROM progress WHERE advancement_key = ?;"
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
                    "SELECT player_uuid, timestamp, RANK() OVER(ORDER BY timestamp DESC) FROM progress " +
                            "WHERE player_uuid = ? AND advancement_key = ? " +
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
                        "SELECT player_uuid, timestamp, RANK() OVER(ORDER BY timestamp ASC) FROM progress " +
                                "WHERE advancement_key = ? " +
                                "ORDER BY timestamp ASC " +
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
                                "SELECT player_uuid, timestamp, RANK() OVER(ORDER BY timestamp ASC) FROM progress " +
                                "WHERE advancement_key = ? " +
                                "ORDER BY timestamp DESC " +
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
}
