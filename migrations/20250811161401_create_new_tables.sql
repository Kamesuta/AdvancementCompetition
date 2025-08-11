-- ================================================================================
-- Migration: 新しいテーブル構造への移行
-- Date: 2025-08-11
-- Description: player, advancement, player_advancement テーブルへの正規化
-- 既存テーブル: player, progress, ranking
-- ================================================================================

-- 1. 新しいテーブルの作成
-- --------------------------------------------------------------------------------

-- 新しいplayerテーブル（ID付き）
CREATE TABLE IF NOT EXISTS player_new (
    id INT AUTO_INCREMENT PRIMARY KEY,
    uuid BINARY(16) UNIQUE NOT NULL,
    name VARCHAR(16) NOT NULL,
    INDEX idx_uuid (uuid)
);

-- advancementテーブル（新規作成）
CREATE TABLE IF NOT EXISTS advancement (
    id INT AUTO_INCREMENT PRIMARY KEY,
    advancement_key VARCHAR(255) UNIQUE NOT NULL
);

-- player_advancementテーブル（新規作成）
CREATE TABLE IF NOT EXISTS player_advancement (
    id INT AUTO_INCREMENT PRIMARY KEY,
    player_id INT NOT NULL,
    advancement_id INT NOT NULL,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY unique_player_advancement (player_id, advancement_id),
    INDEX idx_advancement_id (advancement_id),
    INDEX idx_timestamp (timestamp)
);

-- 2. 既存データからプレイヤー情報を移行
-- --------------------------------------------------------------------------------

-- 既存のplayerテーブルからデータを移行
INSERT IGNORE INTO player_new (uuid, name)
SELECT uuid, name FROM player;

-- 3. 既存データから実績情報を移行
-- --------------------------------------------------------------------------------

-- progressテーブルから実績キーを抽出
INSERT IGNORE INTO advancement (advancement_key)
SELECT DISTINCT advancement_key 
FROM progress;

-- 4. player_advancementテーブルへデータを移行
-- --------------------------------------------------------------------------------

INSERT IGNORE INTO player_advancement (player_id, advancement_id, timestamp)
SELECT 
    p.id as player_id,
    a.id as advancement_id,
    pr.timestamp
FROM progress pr
JOIN player_new p ON pr.player_uuid = p.uuid
JOIN advancement a ON pr.advancement_key = a.advancement_key;

-- 5. 外部キー制約を追加
-- --------------------------------------------------------------------------------

ALTER TABLE player_advancement
ADD CONSTRAINT fk_player_advancement_player
FOREIGN KEY (player_id) REFERENCES player_new(id);

ALTER TABLE player_advancement
ADD CONSTRAINT fk_player_advancement_advancement
FOREIGN KEY (advancement_id) REFERENCES advancement(id);

-- 6. テーブルの入れ替え
-- --------------------------------------------------------------------------------

-- 既存テーブルをold_プレフィックス付きでバックアップとしてリネーム
RENAME TABLE player TO old_player;
RENAME TABLE progress TO old_progress;
RENAME TABLE ranking TO old_ranking;

-- 新しいテーブルを正式な名前にリネーム
RENAME TABLE player_new TO player;

-- 7. 確認用のクエリ（コメントアウト済み）
-- --------------------------------------------------------------------------------
-- SELECT COUNT(*) as player_count FROM player;
-- SELECT COUNT(*) as advancement_count FROM advancement;
-- SELECT COUNT(*) as player_advancement_count FROM player_advancement;

-- 8. 注意事項
-- --------------------------------------------------------------------------------
-- * 既存のテーブルは以下のようにリネームされる:
--   - player → old_player
--   - progress → old_progress  
--   - ranking → old_ranking
-- * 新しいテーブルが作成される:
--   - player (ID付きの新構造)
--   - advancement (実績マスタ)
--   - player_advancement (実績解除記録)
-- * 外部キー制約により、データの整合性が保たれる

-- 9. 移行後のテーブル構造
-- --------------------------------------------------------------------------------
-- 新しいテーブル:
-- * player: id(INT), uuid(BINARY(16)), name(VARCHAR(16))
-- * advancement: id(INT), advancement_key(VARCHAR(255))
-- * player_advancement: id(INT), player_id(INT), advancement_id(INT), timestamp(TIMESTAMP)
--
-- バックアップテーブル:
-- * old_player: uuid(BINARY(16)), name(VARCHAR(20))
-- * old_progress: id(INT), player_uuid(BINARY(16)), advancement_key(VARCHAR(100)), timestamp(TIMESTAMP)
-- * old_ranking: id(INT), player_uuid(BINARY(16)), advancement_key(VARCHAR(100)), score(INT)

-- 10. バックアップテーブル削除手順（移行が確実に成功した後で実行）
-- --------------------------------------------------------------------------------
-- 注意: 以下のSQLは移行が完全に成功し、問題がないことを確認してから実行してください
-- 
-- DROP TABLE old_player;
-- DROP TABLE old_progress;
-- DROP TABLE old_ranking;

-- 11. ロールバック手順（問題が発生した場合にのみ実行）
-- --------------------------------------------------------------------------------
-- DROP TABLE player_advancement;
-- DROP TABLE advancement; 
-- RENAME TABLE player TO player_new;
-- RENAME TABLE old_player TO player;
-- RENAME TABLE old_progress TO progress;
-- RENAME TABLE old_ranking TO ranking;
-- DROP TABLE player_new;