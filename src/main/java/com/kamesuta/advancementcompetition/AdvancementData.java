package com.kamesuta.advancementcompetition;

import com.google.gson.annotations.SerializedName;
import java.util.Map;

/**
 * プレイヤーの実績データを表すクラス
 * JSONファイルから直接デシリアライズするために使用
 */
public class AdvancementData {
    
    /**
     * 単一の実績の進捗情報
     */
    public static class AdvancementProgress {
        /**
         * 実績が完了しているかどうか
         */
        @SerializedName("done")
        private boolean done;
        
        /**
         * 実績の各基準の達成日時
         * キー: 基準名, 値: 達成日時（"yyyy-MM-dd HH:mm:ss Z" 形式）
         */
        @SerializedName("criteria")
        private Map<String, String> criteria;
        
        public boolean isDone() {
            return done;
        }
        
        public void setDone(boolean done) {
            this.done = done;
        }
        
        public Map<String, String> getCriteria() {
            return criteria;
        }
        
        public void setCriteria(Map<String, String> criteria) {
            this.criteria = criteria;
        }
        
        /**
         * 最も遅い（最新の）達成日時を取得
         * @return 最新の達成日時文字列、存在しない場合はnull
         */
        public String getLatestCriteriaTime() {
            if (criteria == null || criteria.isEmpty()) {
                return null;
            }
            
            // 最新の日時を見つける（文字列比較でも日時形式なら正しく動作）
            return criteria.values().stream()
                    .max(String::compareTo)
                    .orElse(null);
        }
    }
    
    /**
     * データバージョン（通常は無視）
     */
    @SerializedName("DataVersion")
    private Integer dataVersion;
    
    /**
     * 各実績の進捗情報
     * キー: 実績キー（例: "minecraft:story/mine_stone"）
     * 値: 実績の進捗情報
     */
    private Map<String, AdvancementProgress> advancements;
    
    public Integer getDataVersion() {
        return dataVersion;
    }
    
    public void setDataVersion(Integer dataVersion) {
        this.dataVersion = dataVersion;
    }
    
    public Map<String, AdvancementProgress> getAdvancements() {
        return advancements;
    }
    
    public void setAdvancements(Map<String, AdvancementProgress> advancements) {
        this.advancements = advancements;
    }
    
    /**
     * 完了した実績のみを取得
     * @return 完了した実績のMap
     */
    public Map<String, AdvancementProgress> getCompletedAdvancements() {
        if (advancements == null) {
            return Map.of();
        }
        
        return advancements.entrySet().stream()
                .filter(entry -> {
                    // DataVersionやその他のメタデータは除外
                    if (entry.getKey().equals("DataVersion")) {
                        return false;
                    }
                    // レシピ実績は除外
                    if (entry.getKey().startsWith("minecraft:recipes/")) {
                        return false;
                    }
                    // 完了していない実績は除外
                    AdvancementProgress progress = entry.getValue();
                    return progress != null && progress.isDone();
                })
                .collect(java.util.stream.Collectors.toMap(
                    Map.Entry::getKey,
                    Map.Entry::getValue
                ));
    }
}