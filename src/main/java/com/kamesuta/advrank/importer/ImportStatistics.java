package com.kamesuta.advrank.importer;

/**
 * インポート統計情報
 * インポート処理の進捗と結果を記録する
 */
public class ImportStatistics {
    /** 処理したプレイヤー数 */
    public int processedPlayers = 0;
    
    /** 取り込んだ実績数 */
    public int importedAdvancements = 0;
    
    /** スキップしたファイル数 */
    public int skippedFiles = 0;
    
    /** 処理エラー数 */
    public int errors = 0;
    
    /**
     * インポート結果レポートを生成する
     * 
     * @return フォーマットされた結果レポート
     */
    public String generateReport() {
        return """
            §b=== インポート結果 ===
            §a処理したプレイヤー数: §f%d人
            §a取り込んだ実績数: §f%d件
            §eスキップしたファイル数: §f%d件
            §c処理エラー数: §f%d件
            """.formatted(processedPlayers, importedAdvancements, skippedFiles, errors);
    }
}