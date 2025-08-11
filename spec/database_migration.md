# データベース移行仕様書

## 概要
既存のprogress テーブル構造から、正規化された新しいテーブル構造（player、advancement、player_advancement）への移行を行う。

## 移行前のテーブル構造

### 1. player テーブル（既存）
プレイヤー情報を記録するテーブル

| カラム名 | データ型 | 制約 | 説明 |
|---------|----------|------|------|
| uuid | BINARY(16) | PRIMARY KEY | プレイヤーのUUID |
| name | VARCHAR(20) | NOT NULL | プレイヤー名 |

### 2. progress テーブル（既存）
実績解除情報を記録するテーブル

| カラム名 | データ型 | 制約 | 説明 |
|---------|----------|------|------|
| id | INT | PRIMARY KEY, AUTO_INCREMENT | レコードID |
| player_uuid | BINARY(16) | NOT NULL | プレイヤーのUUID |
| advancement_key | VARCHAR(100) | NOT NULL | 実績のキー |
| timestamp | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 実績解除日時 |

**制約**:
- UNIQUE (player_uuid, advancement_key)
- FOREIGN KEY (player_uuid) REFERENCES player(uuid)

### 3. ranking テーブル（既存）
ランキング情報を記録するテーブル

| カラム名 | データ型 | 制約 | 説明 |
|---------|----------|------|------|
| id | INT | PRIMARY KEY, AUTO_INCREMENT | レコードID |
| player_uuid | BINARY(16) | NOT NULL | プレイヤーのUUID |
| advancement_key | VARCHAR(100) | NOT NULL | 実績のキー |
| score | INT | NOT NULL | スコア |

**問題点**:
1. advancement_key の重複保存（実績ごとに文字列を保存）
2. player テーブルの主キーがUUIDで、ID参照ができない
3. 複数テーブル間でのJOIN処理が非効率

## 移行後のテーブル構造

### 1. player テーブル（新規作成）
プレイヤーのマスタデータ

| カラム名 | データ型 | 制約 | 説明 |
|---------|----------|------|------|
| id | INT | PRIMARY KEY, AUTO_INCREMENT | プレイヤーID |
| uuid | BINARY(16) | UNIQUE, NOT NULL | プレイヤーのUUID |
| name | VARCHAR(16) | NOT NULL | プレイヤー名 |

**インデックス**:
- PRIMARY KEY: `id`
- UNIQUE KEY: `uuid`
- INDEX: `idx_uuid`

### 2. advancement テーブル（新規作成）
実績のマスタデータ（キーのキャッシュ）

| カラム名 | データ型 | 制約 | 説明 |
|---------|----------|------|------|
| id | INT | PRIMARY KEY, AUTO_INCREMENT | 実績ID |
| advancement_key | VARCHAR(255) | UNIQUE, NOT NULL | 実績のキー |

**インデックス**:
- PRIMARY KEY: `id`
- UNIQUE KEY: `advancement_key`

### 3. player_advancement テーブル（新規作成）
プレイヤーの実績解除状況

| カラム名 | データ型 | 制約 | 説明 |
|---------|----------|------|------|
| id | INT | PRIMARY KEY, AUTO_INCREMENT | レコードID |
| player_id | INT | NOT NULL | playerテーブルへの外部キー |
| advancement_id | INT | NOT NULL | advancementテーブルへの外部キー |
| timestamp | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 実績解除日時 |

**制約**:
- PRIMARY KEY: `id`
- UNIQUE KEY: `(player_id, advancement_id)`
- FOREIGN KEY: `player_id` REFERENCES `player(id)`
- FOREIGN KEY: `advancement_id` REFERENCES `advancement(id)`

**インデックス**:
- PRIMARY KEY: `id`
- UNIQUE KEY: `(player_id, advancement_id)`
- INDEX: `idx_advancement_id`
- INDEX: `idx_timestamp`

## 移行手順

### 1. 新しいテーブル作成
```sql
-- playerテーブル作成
CREATE TABLE player (
    id INT AUTO_INCREMENT PRIMARY KEY,
    uuid BINARY(16) UNIQUE NOT NULL,
    name VARCHAR(16) NOT NULL,
    INDEX idx_uuid (uuid)
);

-- advancementテーブル作成
CREATE TABLE advancement (
    id INT AUTO_INCREMENT PRIMARY KEY,
    advancement_key VARCHAR(255) UNIQUE NOT NULL
);

-- player_advancementテーブル作成
CREATE TABLE player_advancement (
    id INT AUTO_INCREMENT PRIMARY KEY,
    player_id INT NOT NULL,
    advancement_id INT NOT NULL,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY unique_player_advancement (player_id, advancement_id),
    FOREIGN KEY (player_id) REFERENCES player(id),
    FOREIGN KEY (advancement_id) REFERENCES advancement(id),
    INDEX idx_advancement_id (advancement_id),
    INDEX idx_timestamp (timestamp)
);
```

### 2. データ移行

#### 2.1 プレイヤー情報の移行
```sql
-- 既存のplayerテーブルからデータを移行
INSERT INTO player_new (uuid, name)
SELECT uuid, name FROM player_old;
```

#### 2.2 実績情報の移行
```sql
-- progressテーブルから実績キーを抽出してadvancementテーブルに挿入
INSERT INTO advancement (advancement_key)
SELECT DISTINCT advancement_key
FROM progress;
```

#### 2.3 実績解除データの移行
```sql
-- progressテーブルからデータを移行
INSERT INTO player_advancement (player_id, advancement_id, timestamp)
SELECT 
    p.id as player_id,
    a.id as advancement_id,
    pr.timestamp
FROM progress pr
JOIN player p ON pr.player_uuid = p.uuid
JOIN advancement a ON pr.advancement_key = a.advancement_key;
```

### 3. 旧テーブルの処理
- **player テーブル**: `old_player` にリネーム（バックアップとして保持）
- **progress テーブル**: `old_progress` にリネーム（バックアップとして保持）
- **ranking テーブル**: `old_ranking` にリネーム（バックアップとして保持）

## 移行による改善点

1. **正規化**
   - プレイヤー情報と実績情報が適切に正規化される
   - データの重複が削減される

2. **パフォーマンス向上**
   - JOINクエリが効率的に実行される
   - インデックスによる高速検索

3. **データ整合性**
   - 外部キー制約による整合性保証
   - プレイヤー名の一元管理

4. **拡張性**
   - 新しいランキング機能の実装が容易
   - IDベースの参照による柔軟な設計

## 後方互換性

- 既存のJavaコードはRankingManagerクラス内で自動的に新しいテーブル構造を使用
- APIレベルでの変更はなし
- 既存のprogressテーブルは保持され、必要に応じて参照可能

## 移行後のテーブル構造

### 新しいテーブル
- **player**: id(INT), uuid(BINARY(16)), name(VARCHAR(16))
- **advancement**: id(INT), advancement_key(VARCHAR(255))
- **player_advancement**: id(INT), player_id(INT), advancement_id(INT), timestamp(TIMESTAMP)

### バックアップテーブル（old_プレフィックス付き）
- **old_player**: uuid(BINARY(16)), name(VARCHAR(20))
- **old_progress**: id(INT), player_uuid(BINARY(16)), advancement_key(VARCHAR(100)), timestamp(TIMESTAMP)
- **old_ranking**: id(INT), player_uuid(BINARY(16)), advancement_key(VARCHAR(100)), score(INT)

## ロールバック手順

問題が発生した場合の復旧手順:

```sql
-- 新しいテーブルの削除
DROP TABLE player_advancement;
DROP TABLE advancement;
RENAME TABLE player TO player_new;

-- 旧テーブルを元の名前に戻す
RENAME TABLE old_player TO player;
RENAME TABLE old_progress TO progress;
RENAME TABLE old_ranking TO ranking;

-- 作業用テーブルの削除
DROP TABLE player_new;
```

## バックアップテーブル削除

移行が完全に成功し、新しいテーブル構造での動作確認が完了した後、バックアップテーブルを削除できます：

```sql
-- 注意: 移行が完全に成功していることを確認してから実行
DROP TABLE old_player;
DROP TABLE old_progress;
DROP TABLE old_ranking;
```

**削除前の確認事項:**
1. 新しいテーブルでのアプリケーション動作確認
2. データ件数の整合性確認
3. 十分な運用期間（推奨：1週間以上）の経過

## 注意事項

1. **プレイヤー名の取得**
   - 移行時点でのプレイヤー名は取得できない
   - サーバー起動後に実績解除が発生した際に正しい名前で更新される

2. **外部キー制約**
   - データの整合性は向上するが、削除操作時に注意が必要
   - CASCADE設定は行わず、明示的な削除が必要

3. **インデックス**
   - 大量データでの移行時は時間がかかる可能性
   - インデックス作成は最後に実行することを推奨