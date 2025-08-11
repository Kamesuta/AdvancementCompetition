package com.kamesuta.advrank.importer;

import com.kamesuta.advrank.database.RankingManager;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 実績データインポーター
 * JSONファイルからプレイヤーの実績データをデータベースに取り込む
 * インポート全体の制御フローを担当し、統計情報の管理も行う
 */
public class AdvancementImporter {
    private static final Logger logger = Logger.getLogger(AdvancementImporter.class.getName());
    
    private final RankingManager rankingManager;
    private final JsonFileProcessor jsonProcessor;

    public AdvancementImporter(RankingManager rankingManager) {
        this.rankingManager = rankingManager;
        this.jsonProcessor = new JsonFileProcessor();
    }

    /**
     * JSONファイルから実績データをインポートする
     * 
     * @param sender コマンド実行者（進捗メッセージの送信先）
     */
    public void importFromJson(CommandSender sender) {
        // advancementsディレクトリの確認
        var advancementsDir = getAdvancementsDirectory();
        if (!advancementsDir.exists()) {
            sender.sendMessage("§cadvancements ディレクトリが存在しません: " + advancementsDir.getAbsolutePath());
            return;
        }

        // JSONファイル一覧を取得
        var jsonFiles = getJsonFiles(advancementsDir);
        if (jsonFiles.length == 0) {
            sender.sendMessage("§eadvancements ディレクトリにJSONファイルが見つかりませんでした");
            return;
        }

        // インポート処理開始
        sender.sendMessage("§b=== 実績データインポート開始 ===");
        sender.sendMessage("§7見つかったファイル数: " + jsonFiles.length);

        // 各ファイルを処理して結果を表示
        var statistics = processFiles(jsonFiles, sender);
        sender.sendMessage(statistics.generateReport());
    }

    /**
     * 実績ファイルが格納されているディレクトリを取得する
     * 
     * @return advancementsディレクトリ
     */
    private File getAdvancementsDirectory() {
        var worldsDir = new File("world");
        return new File(worldsDir, "advancements");
    }

    /**
     * ディレクトリ内のJSONファイル一覧を取得する
     */
    private File[] getJsonFiles(File directory) {
        return directory.listFiles((dir, name) -> name.endsWith(".json"));
    }

    /**
     * 複数のJSONファイルを順次処理する
     * 進行状況を定期的に報告し、統計情報を集計する
     */
    private ImportStatistics processFiles(File[] jsonFiles, CommandSender sender) {
        var statistics = new ImportStatistics();
        var totalFiles = jsonFiles.length;

        for (var i = 0; i < totalFiles; i++) {
            var jsonFile = jsonFiles[i];
            
            // 10ファイルごとに進捗を報告
            if (i % 10 == 0) {
                sender.sendMessage("§7処理中... " + (i + 1) + "/" + totalFiles);
            }

            // JSONファイルを解析
            var parseResult = jsonProcessor.processJsonFile(jsonFile);
            processParseResult(parseResult, statistics);
        }

        return statistics;
    }

    /**
     * JSON解析結果を処理し、統計情報を更新する
     * 
     * @param parseResult JSON解析結果
     * @param statistics 統計情報
     */
    private void processParseResult(JsonFileProcessor.ProcessResult parseResult, ImportStatistics statistics) {
        switch (parseResult.status()) {
            case SUCCESS -> {
                statistics.processedPlayers++;
                // データベースに保存して、成功した件数を統計に加算
                var importedCount = importPlayerAdvancements(parseResult);
                statistics.importedAdvancements += importedCount;
            }
            case SKIPPED -> statistics.skippedFiles++;
            case ERROR -> statistics.errors++;
        }
    }

    /**
     * プレイヤーの実績データをデータベースに保存する
     * 
     * @param parseResult JSON解析結果
     * @return 正常に保存された実績の数
     */
    private int importPlayerAdvancements(JsonFileProcessor.ProcessResult parseResult) {
        var importedCount = 0;
        
        // 解析された各実績をデータベースに保存
        for (var advancement : parseResult.advancements()) {
            try {
                rankingManager.recordAdvancementProgressData(
                    parseResult.playerUuid(), 
                    parseResult.playerName(), 
                    advancement.key(), 
                    advancement.timestamp()
                );
                importedCount++;
            } catch (Exception e) {
                logger.log(Level.WARNING, "実績データの保存に失敗: " + advancement.key(), e);
            }
        }
        
        return importedCount;
    }
}