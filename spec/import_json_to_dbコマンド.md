# /adv_admin import_json_to_db コマンド仕様書

## 概要
MinecraftサーバーのadvancementsディレクトリにあるJSON形式の実績データを解析し、全プレイヤーの実績解除情報をMySQLデータベースにインポートするコマンド。

## コマンド形式
```
/adv_admin import_json_to_db
```

## 動作仕様

### 1. JSONファイルの読み込み
- **対象ディレクトリ**: `world/advancements/`
- **対象ファイル**: `*.json` (プレイヤーUUIDをファイル名とするJSONファイル)
- **ファイル名形式**: `{UUID}.json` (例: `4f2a2943-2d95-4959-b53e-60cd86edd245.json`)

### 2. JSONデータ構造
```json
{
  "minecraft:adventure/bullseye": {
    "criteria": {
      "bullseye": "2024-03-25 14:41:51 +0900"
    },
    "done": true
  },
  "minecraft:husbandry/balanced_diet": {
    "criteria": {
      "apple": "2025-08-09 18:27:28 +0900",
      "bread": "2025-08-09 18:27:26 +0900"
    },
    "done": false
  },
  "minecraft:nether/explore_nether": {
    "criteria": {
      "minecraft:crimson_forest": "2024-04-11 03:21:29 +0900",
      "minecraft:nether_wastes": "2025-08-09 18:56:42 +0900",
      "minecraft:soul_sand_valley": "2025-08-09 18:56:43 +0900",
      "minecraft:basalt_deltas": "2025-08-09 18:56:54 +0900",
      "minecraft:warped_forest": "2025-08-09 18:53:16 +0900"
    },
    "done": true
  },
  "DataVersion": 3700
}
```

**フィールド説明**:
- **キー**: 実績ID (例: `minecraft:adventure/bullseye`)
- **criteria**: 実績解除条件と達成日時のマップ
- **done**: 実績が完全に解除されているかのフラグ (`true`/`false`)
- **DataVersion**: Minecraftのデータバージョン（処理対象外）

### 3. インポート処理フロー

1. **権限チェック**
   - コマンド実行者がOP権限を持っているか確認
   - 権限がない場合はエラーメッセージを表示して終了

2. **ディレクトリ確認**
   - `world/advancements/` ディレクトリの存在確認
   - ディレクトリが存在しない場合はエラーメッセージを表示

3. **JSONファイル処理**
   - ディレクトリ内の全JSONファイルを取得
   - 各ファイルに対して:
     a. ファイル名からプレイヤーUUIDを抽出
     b. JSONファイルをパース
     c. `done: true` の実績のみを抽出
     d. 各criteriaの最も遅い達成日時を実績の達成日時とする（複数criteriaがある場合は最後に達成されたcriteriaが実績完了時刻）

4. **データベース登録**
   - 各プレイヤーについて:
     - プレイヤー名をBukkit APIから取得
     - 各実績に対して修正された`recordAdvancementProgressData`関数を呼び出し
     - JSONから取得した達成日時（Timestamp）を第4引数として指定して記録

5. **結果レポート**
   - 処理したプレイヤー数
   - インポートした実績の総数
   - エラーがあった場合はその詳細

### 4. エラーハンドリング

- **ファイル読み込みエラー**: 該当ファイルをスキップして続行、ログに記録
- **JSON解析エラー**: 該当ファイルをスキップして続行、ログに記録
- **データベース接続エラー**: 処理を中断、エラーメッセージを表示
- **データ挿入エラー**: エラーログを出力して続行

### 5. 実装上の注意点

- **UUID変換**: ファイル名のUUID（ハイフン付き文字列）をBINARY(16)形式に変換（`AdvancementUtil.uuidToBytes`使用）
- **日時フォーマット**: JSONの日時文字列（例: `2024-03-25 14:41:51 +0900`）をTimestamp型に変換
  ```java
  SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z");
  Timestamp timestamp = new Timestamp(sdf.parse(dateString).getTime());
  ```
- **重複処理**: `recordAdvancementProgressData`内の`INSERT IGNORE`により重複は自動的に回避される
- **プレイヤー名取得**: `Bukkit.getOfflinePlayer(UUID).getName()`を使用してプレイヤー名を取得
- **レシピ実績の除外**: `minecraft:recipes/`で始まる実績は処理対象外（既存コードと同様）

### 6. コマンド実装場所

既存の`AdvancementRanking.java`の`onCommand`メソッドに追加実装:
- コマンド名: `adv_admin`
- 引数チェック: `args[0].equals("import_json_to_db")`

### 7. 必要な追加実装

1. **新規クラス**: `AdvancementImporter.java`
   - JSONファイルの読み込み・解析処理
   - 日時文字列のTimestamp変換処理
   - エラーハンドリング

2. **RankingManagerの既存関数修正**
   - `recordAdvancementProgressData`関数の置き換え
     - 修正前: `recordAdvancementProgressData(UUID uuid, String name, String key)`
     - 修正後: `recordAdvancementProgressData(UUID uuid, String name, String key, Timestamp timestamp)`
   - `timestamp`がnullの場合は`NOW()`を使用（既存動作を維持）
   - `timestamp`が指定されている場合は指定されたタイムスタンプを使用
   - 既存の呼び出し箇所（`onAdvancementDone`メソッド）もnullを渡すように修正

3. **plugin.ymlの更新**
   - `adv_admin`コマンドの追加
   - 権限設定（`advrank.admin`）

### 8. セキュリティ考慮事項

- SQLインジェクション対策: PreparedStatementを使用
- ファイルパストラバーサル対策: advancementsディレクトリ外のファイルアクセスを禁止
- 権限チェック: OP権限を持つユーザーのみ実行可能