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
                            "id INT AUTO_INCREMENT PRIMARY KEY," +
                            "name VARCHAR(20) NOT NULL," +
                            "uuid VARCHAR(36) NOT NULL UNIQUE" +
                            ");"
            );

            // 進捗データベース
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS progress (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY," +
                            "player_id INT NOT NULL," +
                            "advancement_id VARCHAR(100) NOT NULL," +
                            "timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                            "FOREIGN KEY (player_id) REFERENCES player(id)," +
                            "UNIQUE (player_id, advancement_id)" +
                            ");"
            );

            // ランキングデータベース
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS ranking (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY," +
                            "player_id INT NOT NULL," +
                            "advancement_id VARCHAR(100) NOT NULL," +
                            "score INT NOT NULL," +
                            "FOREIGN KEY (player_id) REFERENCES player(id)," +
                            "UNIQUE (player_id, advancement_id)" +
                            ");"
            );
        }
    }

    @Override
    public void close() throws SQLException {
        conn.close();
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
        String uuid = player.getUniqueId().toString();

        // SQLに書き込み
        try {
            // プレイヤー情報の挿入または更新
            String sqlPlayer = "INSERT INTO player (name, uuid) VALUES (?, ?) ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id), name = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sqlPlayer)) {
                // SQLを実行
                pstmt.setString(1, player.getName());
                pstmt.setString(2, uuid);
                pstmt.setString(3, player.getName());
                pstmt.executeUpdate();
            }

            // 進捗の追加
            String sqlProgress = "INSERT INTO progress (player_id, advancement_id, timestamp) VALUES (LAST_INSERT_ID(), ?, NOW())";
            try (PreparedStatement stmt = conn.prepareStatement(sqlProgress)) {
                stmt.setString(1, key);
                stmt.executeUpdate();
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
                    "SELECT COUNT(*) FROM progress WHERE advancement_id = ?;"
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
                    "SELECT player.uuid, progress.timestamp, RANK() OVER(ORDER BY progress.timestamp DESC) FROM progress " +
                            "JOIN player ON progress.player_id = player.id " +
                            "WHERE advancement_id = ? AND player.uuid = ? " +
                            "LIMIT 1;"
            )) {
                // SQLを実行
                pstmt.setString(1, key);
                pstmt.setString(2, player.getUniqueId().toString());
                ResultSet rs = pstmt.executeQuery();

                // 結果を取得
                if (rs.next()) {
                    String uuid = rs.getString(1);
                    Timestamp timestamp = rs.getTimestamp(2);
                    int rank = rs.getInt(3);
                    OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(UUID.fromString(uuid));
                    progress = new RankingProgressData.PlayerProgress(offlinePlayer, timestamp.toInstant(), rank);
                }
            }

            // 上位のプレイヤーの進捗を取得
            if (limitTop > 0) {
                try (PreparedStatement pstmt = conn.prepareStatement(
                        "SELECT player.uuid, progress.timestamp, RANK() OVER(ORDER BY progress.timestamp DESC) FROM progress " +
                                "JOIN player ON progress.player_id = player.id " +
                                "WHERE advancement_id = ? " +
                                "ORDER BY progress.timestamp DESC " +
                                "LIMIT ?;"
                )) {
                    // SQLを実行
                    pstmt.setString(1, key);
                    pstmt.setInt(2, limitTop);
                    ResultSet rs = pstmt.executeQuery();

                    // 結果を取得
                    while (rs.next()) {
                        String uuid = rs.getString(1);
                        Timestamp timestamp = rs.getTimestamp(2);
                        int rank = rs.getInt(3);
                        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(UUID.fromString(uuid));
                        top.add(new RankingProgressData.PlayerProgress(offlinePlayer, timestamp.toInstant(), rank));
                    }
                }
            }

            // 下位のプレイヤーの進捗を取得
            if (limitBottom > 0) {
                try (PreparedStatement pstmt = conn.prepareStatement(
                        "SELECT player.uuid, progress.timestamp, RANK() OVER(ORDER BY progress.timestamp DESC) FROM progress " +
                                "JOIN player ON progress.player_id = player.id " +
                                "WHERE advancement_id = ? " +
                                "ORDER BY progress.timestamp ASC " +
                                "LIMIT ?;"
                )) {
                    // SQLを実行
                    pstmt.setString(1, key);
                    pstmt.setInt(2, limitBottom);
                    ResultSet rs = pstmt.executeQuery();

                    // 結果を取得
                    while (rs.next()) {
                        String uuid = rs.getString(1);
                        Timestamp timestamp = rs.getTimestamp(2);
                        int rank = rs.getInt(3);
                        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(UUID.fromString(uuid));
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
