# リファクタリング計画書

## 現状分析

### 現在の問題点
1. **単一パッケージ集中**: `com.kamesuta.advrank` パッケージに11個のクラスが集中している
2. **大きすぎるクラス**: 
   - `RankingManager`: 563行（データベース操作、ランキング機能、内部クラスが混在）
   - `CommandHandler`: 235行（複数のコマンド処理が一つのクラスに集中）
   - `AdvancementImporter`: 180行（JSON処理、ファイル操作、データベース操作が混在）
3. **責務の混在**: 各クラスが複数の責務を持っている

## リファクタリング目標

### 1. パッケージ構造の再編成
現在の単一パッケージ構造を機能別に分割する。

#### 実装されたパッケージ構造:
```
com.kamesuta.advrank/
├── AdvRankingPlugin.java          # メインクラス（リネーム）
├── command/                       # コマンド処理
│   ├── BaseCommandHandler.java   # 抽象基底クラス（新規作成）
│   ├── CommandHandler.java       # ルーティング専用にリファクタリング
│   ├── AdvRankCommandHandler.java
│   ├── AdvCommandHandler.java
│   ├── AdvIdCommandHandler.java
│   └── AdvAdminCommandHandler.java
├── data/                          # データ管理
│   ├── PlayerDataManager.java
│   ├── PlayerData.java
│   ├── AdvancementData.java
│   └── RankingProgressData.java  # 既にrecordとして実装済み
├── database/                      # データベース操作
│   ├── DatabaseManager.java      # RankingManagerから分離
│   ├── PlayerRepository.java     # プレイヤー関連DB操作
│   ├── AdvancementRepository.java # 実績関連DB操作
│   └── RankingManager.java       # リファクタリング後（薄いラッパー）
├── ranking/                       # ランキング機能
│   └── RankingService.java       # ランキング計算・取得機能
├── display/                       # 表示・UI
│   ├── AdvancementRankingDisplay.java
│   ├── AdvancementViewer.java
│   └── ChatUtils.java
├── util/                          # ユーティリティ
│   └── AdvancementUtil.java
└── importer/                      # インポート機能（importは予約語のためimporterに変更）
    ├── AdvancementImporter.java   # 制御フロー専用にリファクタリング
    ├── JsonFileProcessor.java    # JSON解析・ファイル処理
    └── ImportStatistics.java     # recordとして実装
```

### 2. 大きなクラスの分割計画

#### A. RankingManager (563行) の分割
**問題**: データベース接続、CRUD操作、ランキング計算が混在

**分割案**:
1. **DatabaseManager** (30-80行)
   - データベース接続管理
   - `getConnection()`, `pingDatabase()`, `close()`

2. **PlayerRepository** (40-60行)
   - プレイヤー関連のDB操作
   - `getOrCreatePlayerId()`

3. **AdvancementRepository** (40-60行)
   - 進捗関連のDB操作
   - `getOrCreateAdvancementId()`, `recordAdvancementProgressData()`

4. **RankingService** (80-120行)
   - ランキング取得・計算ロジック
   - `getRankingByAdvancementIdWithPagination()`, `getPlayerRankingByAdvancementId()`

5. **RankingCalculator** (50-80行)
   - ランキング計算専用
   - `getAdvancementProgressData()`の複雑な計算部分

#### B. CommandHandler (235行) の分割
**問題**: 4つの異なるコマンド処理が一つのクラスに集中

**分割案**:
1. **BaseCommandHandler** (抽象クラス, 30-40行)
   - 共通処理、エラーハンドリング

2. **AdvRankCommandHandler** (60-80行)
   - `handleAdvRankCommand()` + タブ補完

3. **AdvCommandHandler** (40-60行)
   - `handleAdvCommand()` + タブ補完

4. **AdvIdCommandHandler** (30-40行)
   - `handleAdvIdCommand()` + タブ補完

5. **AdvAdminCommandHandler** (40-60行)
   - `handleAdvAdminCommand()` + タブ補完

6. **CommandHandler** (リファクタリング後, 40-60行)
   - コマンドルーティング、各ハンドラーへの委譲

#### C. AdvancementImporter (180行) の分割
**問題**: ファイル処理、JSON解析、データベース操作、統計管理が混在

**分割案**:
1. **JsonFileProcessor** (80-100行)
   - JSON解析、ファイル読み込み
   - `processJsonFile()`の大部分

2. **ImportStatistics** (30-40行)
   - インポート統計管理
   - カウンタ管理、レポート生成

3. **AdvancementImporter** (リファクタリング後, 60-80行)
   - 全体の制御、進行管理
   - `importFromJson()`の制御フロー

### 3. 実装手順

#### フェーズ1: パッケージ構造準備
1. 新しいパッケージディレクトリの作成
2. 既存クラスの新パッケージへの移動
3. import文の更新

#### フェーズ2: データベース層の分離
1. `DatabaseManager`の抽出
2. `PlayerRepository`, `AdvancementRepository`の作成
3. `RankingManager`のリファクタリング

#### フェーズ3: コマンド処理の分割
1. `BaseCommandHandler`抽象クラスの作成
2. 各コマンドハンドラーの分割
3. `CommandHandler`のリファクタリング

#### フェーズ4: インポート機能の分割
1. `JsonFileProcessor`の抽出
2. `ImportStatistics`の作成
3. `AdvancementImporter`のリファクタリング

#### フェーズ5: サービス層の整理
1. `RankingService`の作成
2. ビジネスロジックの集約
3. 依存関係の整理

### 4. 期待される効果

#### コード品質の向上
- **単一責任原則**: 各クラスが明確な責務を持つ
- **可読性向上**: クラスサイズが適切になり理解しやすい
- **保守性向上**: 変更の影響範囲が限定される

#### 開発効率の向上
- **テスト容易性**: 小さなクラスは単体テストが書きやすい
- **並行開発**: 機能別に分かれているため複数人での開発が容易
- **再利用性**: 機能が分離されているため他の部分での再利用が可能

#### パフォーマンス
- **メモリ効率**: 必要な機能のみロード
- **初期化時間**: 責務が分離されているため初期化処理が効率的

### 5. 注意事項とリスク

#### 潜在的リスク
1. **互換性**: 既存のAPIに影響する可能性
2. **複雑度**: クラス数が増えることによる管理複雑化
3. **パフォーマンス**: クラス間呼び出しのオーバーヘッド

#### 対策
1. **段階的実装**: フェーズ別に少しずつリファクタリング
2. **インターフェース活用**: 変更の影響を最小限に抑制
3. **十分なテスト**: リファクタリング前後での動作確認

### 6. Java 21モダン化方針

#### 積極的に採用する新機能
1. **Record Classes**: データクラスをrecordで実装
   - `PlayerData`, `AdvancementData`, `RankingProgressData`
   - `ImportStatistics` (統計情報)
   - `RankingEntry`, `RankingResult` (内部クラス)

2. **Pattern Matching for switch**: 複雑な条件分岐を簡潔に
   - コマンドハンドラーでのコマンド分岐処理
   - データベース結果の処理

3. **Text Blocks**: 長いSQL文やJSON文字列
   - データベースクエリの可読性向上
   - エラーメッセージの整理

4. **Sealed Classes/Interfaces**: 型安全性の向上
   - コマンド結果の表現
   - エラータイプの定義

5. **Local Variable Type Inference (var)**: 冗長性削減
   - 型が明確な場合の変数宣言

#### 適用例
```java
// Before: 従来のデータクラス
public class PlayerData {
    private final UUID uuid;
    private final String name;
    // getter, setter, equals, hashCode...
}

// After: Record
public record PlayerData(UUID uuid, String name) {}

// Before: 複雑なswitch
if (command.equals("rank")) {
    // ...
} else if (command.equals("view")) {
    // ...
}

// After: Pattern matching
switch (command) {
    case "rank" -> handleRankCommand(args);
    case "view" -> handleViewCommand(args);
    default -> showHelp(sender);
}

// Before: 長いSQL
String sql = "SELECT p.uuid, p.name, ap.completed_date FROM players p " +
             "JOIN advancement_progress ap ON p.id = ap.player_id " +
             "WHERE ap.advancement_id = ? ORDER BY ap.completed_date LIMIT ?";

// After: Text blocks
String sql = """
    SELECT p.uuid, p.name, ap.completed_date 
    FROM players p 
    JOIN advancement_progress ap ON p.id = ap.player_id 
    WHERE ap.advancement_id = ? 
    ORDER BY ap.completed_date 
    LIMIT ?
    """;
```

### 7. 成功指標

1. **クラスサイズ**: 各クラスが200行以下になる
2. **パッケージ構造**: 機能別に適切に分離される
3. **テストカバレッジ**: 分割後にテストが書きやすくなる
4. **コードの重複**: DRY原則に従った実装になる
5. **Java 21活用度**: recordクラスやpattern matchingを適切に使用

### 8. 計画からの変更点

#### 実装時の判断による変更
1. **configパッケージ**: 作成しなかった
   - 理由: 現時点では設定管理の複雑性が低く、分離の必要性がなかった

2. **RankingCalculator**: 作成しなかった
   - 理由: RankingService内で十分に機能を実装でき、過度な分割を避けた

3. **TimeUtil**: 作成しなかった
   - 理由: AdvancementUtil内の既存機能で十分対応できた

4. **BaseCommandHandler**: 計画外で追加
   - 理由: コマンドハンドラー間の共通処理を抽象化し、DRY原則を適用

5. **パッケージ名変更**: `import` → `importer`
   - 理由: `import`はJavaの予約語のため、コンパイルエラーを回避

6. **RankingManager**: 完全削除せず薄いラッパーとして残存
   - 理由: 既存コードとの互換性を保ちつつ、段階的な移行を可能にした

#### Java 21機能の追加活用
- **Pattern Matching for switch**: コマンドハンドラーでの条件分岐
- **var宣言**: 型推論の活用によるコード簡潔化
- **Text Blocks**: SQLクエリの可読性向上（DatabaseManagerで実装）

このリファクタリングにより、保守性、可読性、拡張性が大幅に向上し、長期的な開発効率の向上が実現されました。