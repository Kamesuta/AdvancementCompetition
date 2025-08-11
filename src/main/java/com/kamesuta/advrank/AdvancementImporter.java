package com.kamesuta.advrank;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;

import static com.kamesuta.advrank.AdvancementRankingPlugin.logger;

/**
 * JSONファイルから実績データをインポートするクラス
 */
public class AdvancementImporter {
    private final RankingManager rankingManager;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z");
    private final Gson gson = new Gson();
    
    // インポート結果の統計情報
    private int processedPlayers = 0;
    private int importedAdvancements = 0;
    private int skippedFiles = 0;
    private int errors = 0;
    
    public AdvancementImporter(RankingManager rankingManager) {
        this.rankingManager = rankingManager;
    }
    
    /**
     * advancementsディレクトリからJSONファイルをインポートする
     * 
     * @param sender コマンド送信者
     * @return 成功した場合true
     */
    public boolean importFromJson(CommandSender sender) {
        // 権限チェック
        if (!sender.hasPermission("advrank.admin")) {
            sender.sendMessage("§c権限がありません。");
            return false;
        }
        
        // ディレクトリの確認
        File advDir = new File("world/advancements");
        if (!advDir.exists() || !advDir.isDirectory()) {
            sender.sendMessage("§cエラー: world/advancements ディレクトリが見つかりません。");
            return false;
        }
        
        // 統計情報をリセット
        processedPlayers = 0;
        importedAdvancements = 0;
        skippedFiles = 0;
        errors = 0;
        
        sender.sendMessage("§aJSONファイルからのインポートを開始します...");
        
        // JSONファイルを処理
        File[] jsonFiles = advDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (jsonFiles == null || jsonFiles.length == 0) {
            sender.sendMessage("§e警告: JSONファイルが見つかりませんでした。");
            return false;
        }
        
        for (File jsonFile : jsonFiles) {
            processJsonFile(jsonFile, sender);
        }
        
        // 結果レポート
        sender.sendMessage("§a=== インポート完了 ===");
        sender.sendMessage("§a処理したプレイヤー数: §f" + processedPlayers);
        sender.sendMessage("§aインポートした実績数: §f" + importedAdvancements);
        if (skippedFiles > 0) {
            sender.sendMessage("§eスキップしたファイル数: §f" + skippedFiles);
        }
        if (errors > 0) {
            sender.sendMessage("§cエラー数: §f" + errors);
        }
        
        return true;
    }
    
    /**
     * 単一のJSONファイルを処理する
     * 
     * @param jsonFile JSONファイル
     * @param sender コマンド送信者
     */
    private void processJsonFile(File jsonFile, CommandSender sender) {
        // ファイル名からUUIDを抽出
        String fileName = jsonFile.getName();
        String uuidString = fileName.substring(0, fileName.length() - 5); // .jsonを除去
        
        UUID playerUuid;
        try {
            playerUuid = UUID.fromString(uuidString);
        } catch (IllegalArgumentException e) {
            logger.log(Level.WARNING, "無効なUUID形式のファイル名: " + fileName);
            skippedFiles++;
            return;
        }
        
        // プレイヤー名を取得
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerUuid);
        String playerName = offlinePlayer.getName();
        if (playerName == null) {
            playerName = "Unknown";
        }
        
        // JSONファイルを読み込み
        try (FileReader reader = new FileReader(jsonFile)) {
            AdvancementData advancementData = gson.fromJson(reader, AdvancementData.class);
            
            if (advancementData == null || advancementData.isEmpty()) {
                logger.log(Level.WARNING, "JSONファイルが無効または空です: " + jsonFile.getName());
                skippedFiles++;
                return;
            }
            
            int playerAdvancementCount = 0;
            
            // 完了した実績のみを処理
            Map<String, AdvancementData.AdvancementProgress> completedAdvancements = 
                advancementData.getCompletedAdvancements(gson);
            
            for (Map.Entry<String, AdvancementData.AdvancementProgress> entry : completedAdvancements.entrySet()) {
                String advancementKey = entry.getKey();
                AdvancementData.AdvancementProgress progress = entry.getValue();
                
                // 最新の達成日時を取得
                String latestTimeString = progress.getLatestCriteriaTime();
                if (latestTimeString == null) {
                    logger.log(Level.WARNING, "実績の達成日時が見つかりません: " + advancementKey + " (プレイヤー: " + playerName + ")");
                    continue;
                }
                
                // 日時文字列をTimestampに変換
                Timestamp latestTimestamp;
                try {
                    Date date = dateFormat.parse(latestTimeString);
                    latestTimestamp = new Timestamp(date.getTime());
                } catch (ParseException e) {
                    logger.log(Level.WARNING, "日時の解析に失敗: " + latestTimeString + " (実績: " + advancementKey + ")", e);
                    continue;
                }
                
                // データベースに記録
                try {
                    rankingManager.recordAdvancementProgressData(playerUuid, playerName, advancementKey, latestTimestamp);
                    
                    playerAdvancementCount++;
                    importedAdvancements++;
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "実績の記録に失敗: " + advancementKey + " (プレイヤー: " + playerName + ")", e);
                    errors++;
                }
            }
            
            if (playerAdvancementCount > 0) {
                processedPlayers++;
                sender.sendMessage("§7プレイヤー §f" + playerName + " §7の実績を §f" + playerAdvancementCount + " §7件インポートしました。");
            }
            
        } catch (IOException e) {
            logger.log(Level.SEVERE, "JSONファイルの読み込みに失敗: " + jsonFile.getName(), e);
            errors++;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "JSONファイルの処理中にエラー: " + jsonFile.getName(), e);
            errors++;
        }
    }
}