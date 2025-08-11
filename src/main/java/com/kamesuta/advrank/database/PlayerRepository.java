package com.kamesuta.advrank.database;

import com.kamesuta.advrank.util.AdvancementUtil;

import java.sql.*;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * プレイヤー関連のデータベース操作を担当するリポジトリクラス
 * プレイヤー情報のCRUD操作を提供する
 */
public class PlayerRepository {
    private static final Logger logger = Logger.getLogger(PlayerRepository.class.getName());
    
    private final DatabaseManager databaseManager;
    
    public PlayerRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }
    
    /**
     * プレイヤーIDを取得または新規作成する
     * 既存のプレイヤーが見つかった場合は名前を更新する
     * 
     * @param uuid プレイヤーのUUID
     * @param name プレイヤー名
     * @return プレイヤーID
     */
    public int getOrCreatePlayerId(UUID uuid, String name) throws SQLException {
        byte[] uuidBytes = AdvancementUtil.uuidToBytes(uuid);
        
        // まず既存のプレイヤーを検索
        var selectSql = "SELECT id FROM player WHERE uuid = ?";
        try (var selectStmt = databaseManager.getConnection().prepareStatement(selectSql)) {
            selectStmt.setBytes(1, uuidBytes);
            try (var rs = selectStmt.executeQuery()) {
                if (rs.next()) {
                    // 既存プレイヤーの名前を更新
                    var playerId = rs.getInt("id");
                    updatePlayerName(playerId, name);
                    return playerId;
                }
            }
        }
        
        // 新規プレイヤーを作成
        return createPlayer(uuidBytes, name);
    }
    
    /**
     * プレイヤー名を更新する
     */
    private void updatePlayerName(int playerId, String name) throws SQLException {
        var updateSql = "UPDATE player SET name = ? WHERE id = ?";
        try (var updateStmt = databaseManager.getConnection().prepareStatement(updateSql)) {
            updateStmt.setString(1, name);
            updateStmt.setInt(2, playerId);
            updateStmt.executeUpdate();
        }
    }
    
    /**
     * 新しいプレイヤーレコードを作成する
     */
    private int createPlayer(byte[] uuidBytes, String name) throws SQLException {
        var insertSql = "INSERT INTO player (uuid, name) VALUES (?, ?)";
        try (var insertStmt = databaseManager.getConnection().prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
            insertStmt.setBytes(1, uuidBytes);
            insertStmt.setString(2, name);
            insertStmt.executeUpdate();
            
            // 生成されたIDを取得
            try (var rs = insertStmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        
        throw new SQLException("Failed to create player record");
    }
}