-- ================================================================================
-- Migration: 新しいテーブル構造への移行
-- Date: 2025-08-11
-- Description: player, advancement, player_advancement テーブルへの正規化
-- ================================================================================

-- 1. 既存データのバックアップ（既存のprogressテーブルがある場合）
-- --------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS progress_backup AS
SELECT * FROM progress;

-- 2. 新しいテーブルの作成（既に存在する場合はスキップ）
-- --------------------------------------------------------------------------------

-- playerテーブル（新しい構造）
CREATE TABLE IF NOT EXISTS player_new (
    id INT AUTO_INCREMENT PRIMARY KEY,
    uuid BINARY(16) UNIQUE NOT NULL,
    name VARCHAR(16) NOT NULL,
    INDEX idx_uuid (uuid)
);

-- advancementテーブル
CREATE TABLE IF NOT EXISTS advancement (
    id INT AUTO_INCREMENT PRIMARY KEY,
    advancement_key VARCHAR(255) UNIQUE NOT NULL
);

-- player_advancementテーブル
CREATE TABLE IF NOT EXISTS player_advancement_new (
    player_id INT NOT NULL,
    advancement_id INT NOT NULL,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (player_id, advancement_id),
    INDEX idx_advancement_id (advancement_id),
    INDEX idx_timestamp (timestamp)
);

-- 3. 既存データからプレイヤー情報を移行
-- --------------------------------------------------------------------------------

-- 既存のplayerテーブルからデータを移行（存在する場合）
INSERT IGNORE INTO player_new (uuid, name)
SELECT uuid, name FROM player;

-- progressテーブルからもプレイヤー情報を抽出（存在する場合）
INSERT IGNORE INTO player_new (uuid, name)
SELECT DISTINCT p.player_uuid, pl.name 
FROM progress p
JOIN player pl ON p.player_uuid = pl.uuid
WHERE p.player_uuid NOT IN (SELECT uuid FROM player_new);

-- 4. 既存データから実績情報を移行
-- --------------------------------------------------------------------------------

-- progressテーブルから実績キーを抽出
INSERT IGNORE INTO advancement (advancement_key)
SELECT DISTINCT advancement_key FROM progress;

-- 5. player_advancementテーブルへデータを移行
-- --------------------------------------------------------------------------------

INSERT IGNORE INTO player_advancement_new (player_id, advancement_id, timestamp)
SELECT 
    pn.id as player_id,
    a.id as advancement_id,
    p.timestamp
FROM progress p
JOIN player_new pn ON p.player_uuid = pn.uuid
JOIN advancement a ON p.advancement_key = a.advancement_key;

-- 6. 外部キー制約を追加
-- --------------------------------------------------------------------------------

ALTER TABLE player_advancement_new
ADD CONSTRAINT fk_player_advancement_player
FOREIGN KEY (player_id) REFERENCES player_new(id);

ALTER TABLE player_advancement_new
ADD CONSTRAINT fk_player_advancement_advancement
FOREIGN KEY (advancement_id) REFERENCES advancement(id);

-- 7. テーブルの入れ替え
-- --------------------------------------------------------------------------------

-- 既存のテーブルをリネーム（バックアップとして保持）
RENAME TABLE player TO player_old;
RENAME TABLE player_new TO player;

-- player_advancementテーブルが既に存在する場合の対応
DROP TABLE IF EXISTS player_advancement_old;
CREATE TABLE IF NOT EXISTS player_advancement_old AS
SELECT * FROM player_advancement WHERE 1=0; -- 空のテーブルを作成

-- player_advancementテーブルの入れ替え
RENAME TABLE player_advancement TO player_advancement_old_temp;
RENAME TABLE player_advancement_new TO player_advancement;
DROP TABLE IF EXISTS player_advancement_old_temp;

-- 8. 確認用のクエリ（コメントアウト済み）
-- --------------------------------------------------------------------------------
-- SELECT COUNT(*) as player_count FROM player;
-- SELECT COUNT(*) as advancement_count FROM advancement;
-- SELECT COUNT(*) as player_advancement_count FROM player_advancement;

-- 9. ロールバック手順（必要な場合にのみ実行）
-- --------------------------------------------------------------------------------
-- RENAME TABLE player TO player_new;
-- RENAME TABLE player_old TO player;
-- RENAME TABLE player_advancement TO player_advancement_new;
-- RENAME TABLE player_advancement_old TO player_advancement;
-- DROP TABLE player_new;
-- DROP TABLE advancement;
-- DROP TABLE player_advancement_new;