package com.kamesuta.advrank.importer;

import com.google.gson.Gson;
import com.kamesuta.advrank.data.AdvancementData;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * JSONファイル処理クラス
 * プレイヤーの実績JSONファイルを解析し、構造化されたデータに変換する
 * データベース操作は行わず、純粋にファイル解析のみを担当
 */
public class JsonFileProcessor {
    private static final Logger logger = Logger.getLogger(JsonFileProcessor.class.getName());
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss zzz");
    private final Gson gson = new Gson();
    
    /**
     * JSONファイルを処理してプレイヤーの実績データを抽出する
     * 
     * @param jsonFile 処理するJSONファイル
     * @return 処理結果（成功時は実績データ、失敗時はエラー情報）
     */
    public ProcessResult processJsonFile(File jsonFile) {
        var playerUuid = extractUuidFromFilename(jsonFile.getName());
        if (playerUuid == null) {
            return ProcessResult.skipped("無効なUUID形式のファイル名: " + jsonFile.getName());
        }
        
        var playerName = getPlayerName(playerUuid);
        
        try (var reader = new FileReader(jsonFile)) {
            return processAdvancementData(reader, playerUuid, playerName);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "JSONファイルの読み取り中にエラーが発生: " + jsonFile.getName(), e);
            return ProcessResult.error("ファイル読み取りエラー: " + e.getMessage());
        }
    }
    
    /**
     * ファイル名からプレイヤーのUUIDを抽出する
     * MinecraftのadvancementsファイルはUUID.json形式で保存される
     * 
     * @param fileName JSONファイル名
     * @return 抽出されたUUID（無効な場合はnull）
     */
    private UUID extractUuidFromFilename(String fileName) {
        if (!fileName.endsWith(".json") || fileName.length() < 41) {
            return null;
        }
        
        var uuidString = fileName.substring(0, fileName.length() - 5);
        try {
            return UUID.fromString(uuidString);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
    
    /**
     * プレイヤーのUUIDから名前を取得する
     */
    private String getPlayerName(UUID playerUuid) {
        var offlinePlayer = Bukkit.getOfflinePlayer(playerUuid);
        var playerName = offlinePlayer.getName();
        return playerName != null ? playerName : "Unknown";
    }
    
    /**
     * JSONファイルから実績データを解析する
     */
    private ProcessResult processAdvancementData(FileReader reader, UUID playerUuid, String playerName) {
        try {
            var advancementData = gson.fromJson(reader, AdvancementData.class);
            
            if (advancementData == null || advancementData.isEmpty()) {
                return ProcessResult.skipped("JSONファイルが無効または空です");
            }
            
            return parsePlayerAdvancements(advancementData, playerUuid, playerName);
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "JSON解析エラー", e);
            return ProcessResult.error("JSON解析エラー: " + e.getMessage());
        }
    }
    
    /**
     * プレイヤーの実績データを解析してリストに変換する
     */
    private ProcessResult parsePlayerAdvancements(AdvancementData advancementData, UUID playerUuid, String playerName) {
        var completedAdvancements = advancementData.getCompletedAdvancements(gson);
        var advancementList = new ArrayList<AdvancementRecord>();
        
        for (var entry : completedAdvancements.entrySet()) {
            var advancementKey = entry.getKey();
            var progress = entry.getValue();
            
            var timestamp = parseLatestCompletionDate(progress);
            advancementList.add(new AdvancementRecord(advancementKey, timestamp));
        }
        
        return ProcessResult.success(playerUuid, playerName, advancementList);
    }
    
    /**
     * 実績の最新達成日時を解析してTimestampに変換する
     */
    private Timestamp parseLatestCompletionDate(AdvancementData.AdvancementProgress progress) {
        var latestDate = progress.getLatestCriteriaTime();
        if (latestDate == null) {
            return null;
        }
        
        try {
            var date = dateFormat.parse(latestDate);
            return new Timestamp(date.getTime());
        } catch (ParseException e) {
            logger.log(Level.WARNING, "日付の解析に失敗: " + latestDate, e);
            return null;
        }
    }
    
    /**
     * 実績記録データ
     * 
     * @param key 実績キー
     * @param timestamp 達成日時
     */
    public record AdvancementRecord(String key, Timestamp timestamp) {}
    
    /**
     * ファイル処理結果
     * 
     * @param status 処理ステータス
     * @param playerUuid プレイヤーUUID
     * @param playerName プレイヤー名
     * @param advancements 実績リスト
     * @param message エラー・スキップメッセージ
     */
    public record ProcessResult(
            ProcessStatus status,
            UUID playerUuid,
            String playerName,
            List<AdvancementRecord> advancements,
            String message
    ) {
        public static ProcessResult success(UUID playerUuid, String playerName, List<AdvancementRecord> advancements) {
            return new ProcessResult(ProcessStatus.SUCCESS, playerUuid, playerName, advancements, null);
        }
        
        public static ProcessResult skipped(String message) {
            return new ProcessResult(ProcessStatus.SKIPPED, null, null, null, message);
        }
        
        public static ProcessResult error(String message) {
            return new ProcessResult(ProcessStatus.ERROR, null, null, null, message);
        }
        
        public boolean isSuccess() {
            return status == ProcessStatus.SUCCESS;
        }
        
        public boolean isSkipped() {
            return status == ProcessStatus.SKIPPED;
        }
        
        public boolean isError() {
            return status == ProcessStatus.ERROR;
        }
    }
    
    /**
     * 処理ステータス
     */
    public enum ProcessStatus {
        /** 処理成功 */
        SUCCESS, 
        /** スキップ（無効ファイルなど） */
        SKIPPED, 
        /** エラー発生 */
        ERROR
    }
}