# ランキングシステム仕様書

## 概要
プレイヤーの実績解除状況を管理し、全プレイヤーのランキングを表示するシステム。
データベース構造を正規化し、実績情報を別テーブルで管理することで、効率的なデータ管理を実現する。

## データベース設計

### 1. player テーブル（新規）
プレイヤーのマスタデータを管理するテーブル

| カラム名 | データ型 | 制約 | 説明 |
|---------|----------|------|------|
| id | INT | PRIMARY KEY, AUTO_INCREMENT | プレイヤーの一意識別子 |
| uuid | BINARY(16) | UNIQUE, NOT NULL | プレイヤーのUUID |
| name | VARCHAR(16) | NOT NULL | プレイヤー名 |

**インデックス**:
- PRIMARY KEY: `id`
- UNIQUE KEY: `uuid`

### 2. advancement テーブル（新規）
実績のマスタデータを管理するテーブル（Minecraftのキーをキャッシュ）

| カラム名 | データ型 | 制約 | 説明 |
|---------|----------|------|------|
| id | INT | PRIMARY KEY, AUTO_INCREMENT | 実績の一意識別子 |
| advancement_key | VARCHAR(255) | UNIQUE, NOT NULL | 実績のキー（例: minecraft:story/mine_stone） |

**インデックス**:
- PRIMARY KEY: `id`
- UNIQUE KEY: `advancement_key`

### 3. player_advancement テーブル（既存テーブルの改修）
プレイヤーの実績解除状況を記録するテーブル

| カラム名 | データ型 | 制約 | 説明 |
|---------|----------|------|------|
| player_id | INT | NOT NULL, FOREIGN KEY | player.idへの外部キー |
| advancement_id | INT | NOT NULL, FOREIGN KEY | advancement.idへの外部キー |
| timestamp | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | 実績解除日時 |

**インデックス**:
- PRIMARY KEY: `(player_id, advancement_id)`
- INDEX: `advancement_id`
- INDEX: `timestamp`
- FOREIGN KEY: `player_id` REFERENCES `player(id)`
- FOREIGN KEY: `advancement_id` REFERENCES `advancement(id)`

## コマンド仕様

### /adv_rank <ID> コマンド

#### 概要
指定された実績IDに対する全プレイヤーのランキングを表示する。

#### コマンド形式
```
/adv_rank <ID>
```

#### パラメータ
- **ID**: advancement テーブルの数値ID（必須）
  - 数値のみ受け付ける
  - 存在しないIDの場合はエラーメッセージを表示

#### 動作仕様

1. **入力検証**
   - IDが数値であることを確認
   - IDがデータベースに存在することを確認

2. **ランキング取得**
   ```sql
   SELECT 
       p.name,
       pa.timestamp,
       RANK() OVER (ORDER BY pa.timestamp ASC) as rank
   FROM player_advancement pa
   JOIN player p ON pa.player_id = p.id
   WHERE pa.advancement_id = ?
   ORDER BY pa.timestamp ASC
   LIMIT 100;
   ```

3. **表示形式**
   ```
   === 実績ランキング: [実績名] ===
   1位: PlayerA - 2024/03/25 14:41:51
   2位: PlayerB - 2024/03/25 15:30:22
   3位: PlayerC - 2024/03/26 09:15:33
   ...
   ```

4. **エラーハンドリング**
   - 無効なID形式: 「エラー: IDは数値で指定してください」
   - 存在しないID: 「エラー: 指定されたID [ID] の実績は存在しません」
   - データベースエラー: 「エラー: ランキングの取得に失敗しました」

#### 権限
- デフォルト: 全プレイヤーが実行可能
- 設定により制限可能: `advancementcompetition.command.adv_rank`

## データ移行計画

### 1. 移行手順

1. **player テーブルの作成**
   ```sql
   CREATE TABLE IF NOT EXISTS player (
       id INT AUTO_INCREMENT PRIMARY KEY,
       uuid BINARY(16) UNIQUE NOT NULL,
       name VARCHAR(16) NOT NULL,
       INDEX idx_uuid (uuid)
   );
   ```

2. **advancement テーブルの作成**
   ```sql
   CREATE TABLE IF NOT EXISTS advancement (
       id INT AUTO_INCREMENT PRIMARY KEY,
       advancement_key VARCHAR(255) UNIQUE NOT NULL
   );
   ```

3. **既存データから player を抽出して登録**
   ```sql
   INSERT IGNORE INTO player (uuid, name)
   SELECT DISTINCT uuid, name 
   FROM player_advancement_old;
   ```

4. **既存データから advancement_key を抽出して登録**
   ```sql
   INSERT IGNORE INTO advancement (advancement_key)
   SELECT DISTINCT advancement_key 
   FROM player_advancement_old;
   ```

5. **新しい player_advancement テーブルの作成**
   ```sql
   CREATE TABLE IF NOT EXISTS player_advancement_new (
       player_id INT NOT NULL,
       advancement_id INT NOT NULL,
       timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
       PRIMARY KEY (player_id, advancement_id),
       FOREIGN KEY (player_id) REFERENCES player(id),
       FOREIGN KEY (advancement_id) REFERENCES advancement(id),
       INDEX idx_advancement_id (advancement_id),
       INDEX idx_timestamp (timestamp)
   );
   ```

6. **データ移行**
   ```sql
   INSERT INTO player_advancement_new (player_id, advancement_id, timestamp)
   SELECT 
       p.id,
       a.id,
       pa.timestamp
   FROM player_advancement_old pa
   JOIN player p ON pa.uuid = p.uuid
   JOIN advancement a ON pa.advancement_key = a.advancement_key;
   ```

7. **テーブル入れ替え**
   ```sql
   RENAME TABLE player_advancement TO player_advancement_backup;
   RENAME TABLE player_advancement_new TO player_advancement;
   ```

### 2. 移行時の考慮事項

- **ダウンタイム最小化**: RENAME TABLE操作は高速なため、ダウンタイムは最小限
- **ロールバック対応**: バックアップテーブルを保持し、問題があれば即座に戻せるようにする
- **データ整合性**: 外部キー制約により、不整合なデータの混入を防ぐ

## 実装計画

### Phase 1: データベース構造の変更
1. advancement テーブルの作成
2. 既存コードの影響範囲調査
3. RankingManager クラスの改修
4. migration SQLの作成

### Phase 2: コード改修
1. **RankingManager.java**
   - player テーブルへの登録処理追加
   - advancement テーブルへの登録処理追加
   - player_advancement テーブルへの登録処理を player_id と advancement_id を使用するように変更
   - recordAdvancementProgressData メソッドの改修
   - getOrCreatePlayerId メソッドの追加
   - getOrCreateAdvancementId メソッドの追加

### Phase 3: ランキングコマンドの実装
1. **AdvancementCompetition.java**
   - /adv_rank コマンドの追加
   - パラメータ検証処理

2. **RankingManager.java**
   - getRankingByAdvancementId メソッドの追加
   - 表示フォーマット処理

### Phase 4: データ移行
1. 移行スクリプトの作成
2. テスト環境での移行テスト

## パフォーマンス考慮事項

1. **インデックス最適化**
   - advancement_id による検索が頻繁に発生するため、適切なインデックスを設定
   - timestamp によるソートのためのインデックス

## セキュリティ考慮事項

1. **SQLインジェクション対策**
   - 全てのクエリで PreparedStatement を使用
   - 入力値の厳密な検証

## テスト計画

1. **単体テスト**
   - データベース操作の正確性
   - コマンドパラメータの検証
