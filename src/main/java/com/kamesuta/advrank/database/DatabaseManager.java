package com.kamesuta.advrank.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.kamesuta.advrank.AdvRankingPlugin.app;

/**
 * データベース接続とテーブル管理を行うクラス
 * MySQL接続の初期化とテーブル作成を担当する
 */
public class DatabaseManager implements AutoCloseable {
    private static final Logger logger = Logger.getLogger(DatabaseManager.class.getName());

    private final Connection conn;

    /**
     * コンストラクタ
     * データベース接続を確立し、必要なテーブルを作成する
     */
    public DatabaseManager() throws SQLException {
        this.conn = createConnection();
        initializeTables();
    }

    /**
     * データベース接続を作成する
     */
    private Connection createConnection() throws SQLException {
        var config = app.getConfig();
        // 接続URLを構築（Java21のテキストブロック使用）
        var url = """
                jdbc:mysql://%s:%s/%s
                """.formatted(
                config.getString("mysql.host"),
                config.getString("mysql.port"),
                config.getString("mysql.databaseName")
        ).strip();

        var username = config.getString("mysql.username");
        var password = config.getString("mysql.password");

        // データベースに接続
        return DriverManager.getConnection(url, username, password);
    }

    /**
     * 必要なテーブルを作成する
     * player, advancement, player_advancementテーブルを初期化
     */
    private void initializeTables() throws SQLException {
        try (var stmt = conn.createStatement()) {
            // playerテーブル：プレイヤー情報を格納
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS player (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        uuid BINARY(16) UNIQUE NOT NULL,
                        name VARCHAR(16) NOT NULL,
                        INDEX idx_uuid (uuid)
                    );
                    """);

            // advancementテーブル：実績情報を格納
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS advancement (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        advancement_key VARCHAR(255) UNIQUE NOT NULL
                    );
                    """);

            // player_advancementテーブル：プレイヤーの実績達成記録を格納
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS player_advancement (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        player_id INT NOT NULL,
                        advancement_id INT NOT NULL,
                        timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        UNIQUE KEY unique_player_advancement (player_id, advancement_id),
                        FOREIGN KEY (player_id) REFERENCES player(id),
                        FOREIGN KEY (advancement_id) REFERENCES advancement(id),
                        INDEX idx_advancement_id (advancement_id),
                        INDEX idx_timestamp (timestamp)
                    );
                    """);
        }
    }

    /**
     * データベース接続を取得する
     */
    public Connection getConnection() {
        return conn;
    }

    /**
     * データベースにpingを送信して接続を維持する
     */
    public void pingDatabase() {
        try (var stmt = conn.createStatement()) {
            stmt.executeQuery("SELECT 1;");
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "KeepAliveパケット(ping)の送信に失敗しました", e);
        }
    }

    @Override
    public void close() throws SQLException {
        if (conn != null) {
            conn.close();
        }
    }
}