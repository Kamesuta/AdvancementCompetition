package com.kamesuta.advrank.data;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import java.util.HashMap;
import java.util.Map;

/**
 * プレイヤーの実績データを表すクラス
 * JSONファイルから直接デシリアライズするために使用
 * 
 * JSON構造: 
 * {
 *   "minecraft:story/mine_stone": {
 *     "criteria": {
 *       "get_stone": "2025-08-08 17:37:05 +0900"
 *     },
 *     "done": true
 *   },
 *   "DataVersion": 123
 * }
 */
public class AdvancementData extends HashMap<String, Object> {
    
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
     * 完了した実績のみを取得
     * @param gson Gsonインスタンス（AdvancementProgressへの変換用）
     * @return 完了した実績のMap
     */
    public Map<String, AdvancementProgress> getCompletedAdvancements(Gson gson) {
        Map<String, AdvancementProgress> completedAdvancements = new HashMap<>();
        
        for (Map.Entry<String, Object> entry : this.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            // DataVersionやその他のメタデータは除外
            if (key.equals("DataVersion")) {
                continue;
            }
            
            // レシピ実績は除外
            if (key.startsWith("minecraft:recipes/")) {
                continue;
            }
            
            // ObjectをAdvancementProgressに変換
            try {
                // GsonでObjectからAdvancementProgressに変換
                String json = gson.toJson(value);
                AdvancementProgress progress = gson.fromJson(json, AdvancementProgress.class);
                
                // 完了していない実績は除外
                if (progress != null && progress.isDone()) {
                    completedAdvancements.put(key, progress);
                }
            } catch (Exception e) {
                // 変換に失敗した場合は無視
                continue;
            }
        }
        
        return completedAdvancements;
    }
}