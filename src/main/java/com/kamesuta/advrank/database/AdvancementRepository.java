package com.kamesuta.advrank.database;

import java.sql.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 実績関連のデータベース操作を担当するリポジトリクラス
 * 実績情報とプレイヤーの実績達成記録のCRUD操作を提供する
 */
public class AdvancementRepository {
    private static final Logger logger = Logger.getLogger(AdvancementRepository.class.getName());
    
    private final DatabaseManager databaseManager;
    
    public AdvancementRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }
    
    /**
     * 実績IDを取得または新規作成する
     * 
     * @param advancementKey 実績キー
     * @return 実績ID
     */
    public int getOrCreateAdvancementId(String advancementKey) throws SQLException {
        // まず既存の実績を検索
        var selectSql = "SELECT id FROM advancement WHERE advancement_key = ?";
        try (var selectStmt = databaseManager.getConnection().prepareStatement(selectSql)) {
            selectStmt.setString(1, advancementKey);
            try (var rs = selectStmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
        }
        
        // 新規実績を作成
        return createAdvancement(advancementKey);
    }
    
    /**
     * 新しい実績レコードを作成する
     */
    private int createAdvancement(String advancementKey) throws SQLException {
        var insertSql = "INSERT INTO advancement (advancement_key) VALUES (?)";
        try (var insertStmt = databaseManager.getConnection().prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
            insertStmt.setString(1, advancementKey);
            insertStmt.executeUpdate();
            
            // 生成されたIDを取得
            try (var rs = insertStmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        
        throw new SQLException("Failed to create advancement record");
    }
    
    /**
     * プレイヤーの実績達成を記録する
     * 
     * @param playerId プレイヤーID
     * @param advancementId 実績ID
     * @param timestamp 達成日時（nullの場合は現在時刻）
     */
    public void recordPlayerAdvancement(int playerId, int advancementId, Timestamp timestamp) throws SQLException {
        // タイムスタンプに応じてSQL文を選択
        var sql = timestamp == null 
            ? "INSERT IGNORE INTO player_advancement (player_id, advancement_id, timestamp) VALUES (?, ?, NOW());"
            : "INSERT IGNORE INTO player_advancement (player_id, advancement_id, timestamp) VALUES (?, ?, ?);";
            
        try (var pstmt = databaseManager.getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, playerId);
            pstmt.setInt(2, advancementId);
            if (timestamp != null) {
                pstmt.setTimestamp(3, timestamp);
            }
            pstmt.executeUpdate();
        }
    }
    
    /**
     * 実績キーから実績IDを取得する
     * 
     * @param advancementKey 実績キー
     * @return 実績ID（見つからない場合は-1）
     */
    public int getAdvancementIdByKey(String advancementKey) {
        try (var pstmt = databaseManager.getConnection().prepareStatement(
                "SELECT id FROM advancement WHERE advancement_key = ?;"
        )) {
            pstmt.setString(1, advancementKey);
            var rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("id");
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "実績IDの取得に失敗しました", e);
        }
        return -1;
    }
    
    /**
     * 指定された実績IDが存在するかを確認する
     * 
     * @param advancementId 実績ID
     * @return 存在する場合true
     */
    public boolean isAdvancementIdExists(int advancementId) {
        try (var pstmt = databaseManager.getConnection().prepareStatement(
                "SELECT COUNT(*) FROM advancement WHERE id = ?;"
        )) {
            pstmt.setInt(1, advancementId);
            var rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "実績IDの存在確認に失敗しました", e);
        }
        return false;
    }
}