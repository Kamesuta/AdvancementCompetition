package com.kamesuta.advancementcompetition;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.kamesuta.advancementcompetition.AdvancementCompetition.app;

/**
 * コマンド処理クラス
 */
public class CommandHandler {
    
    /**
     * ランキング表示コマンド
     */
    public boolean handleAdvRankCommand(CommandSender sender, String[] args) {
        // 引数チェック
        if (args.length < 1 || args.length > 2) {
            sender.sendMessage("§c使用法: /adv_rank <ID> [ページ番号]");
            return true;
        }
        
        // ID形式チェック
        int advancementId;
        try {
            advancementId = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cエラー: IDは数値で指定してください");
            return true;
        }
        
        // ページ番号チェック（デフォルトは1）
        int page = 1;
        if (args.length == 2) {
            try {
                page = Integer.parseInt(args[1]);
                if (page < 1) {
                    sender.sendMessage("§cエラー: ページ番号は1以上で指定してください");
                    return true;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage("§cエラー: ページ番号は数値で指定してください");
                return true;
            }
        }
        
        // 実績ID存在確認
        if (!app.rankingManager.isAdvancementIdExists(advancementId)) {
            sender.sendMessage("§cエラー: 指定されたID " + advancementId + " の実績は存在しません");
            return true;
        }
        
        // ページネーション付きランキング取得
        int pageSize = 10;
        RankingManager.RankingResult result = app.rankingManager.getRankingByAdvancementIdWithPagination(advancementId, page, pageSize);
        
        if (result.totalCount() == 0) {
            sender.sendMessage("§7この実績を達成したプレイヤーはいません");
            return true;
        }
        
        // ページ範囲チェック
        if (page > result.getTotalPages()) {
            sender.sendMessage("§cエラー: ページ " + page + " は存在しません（最大ページ: " + result.getTotalPages() + "）");
            return true;
        }
        
        // 実績名表示（プレイヤーの言語設定を考慮）
        String advancementKey = result.advancementKey();
        Player player = sender instanceof Player ? (Player) sender : null;
        String displayName = ChatUtils.getAdvancementDisplayName(advancementKey, player);
        
        // ヘッダー表示
        ChatUtils.displayRankingHeader(sender, displayName);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        
        // ランキング表示
        UUID playerUuid = null;
        if (sender instanceof Player) {
            playerUuid = ((Player) sender).getUniqueId();
        }
        
        for (RankingManager.RankingEntry entry : result.entries()) {
            String formattedTime = sdf.format(entry.timestamp());
            boolean isCurrentPlayer = playerUuid != null && entry.playerName().equals(sender.getName());
            ChatUtils.displayRankingEntry(sender, entry.rank(), entry.playerName(), formattedTime, isCurrentPlayer);
        }
        
        // 1ページ目で自分がページ内にいない場合、自分のランキングを表示
        if (page == 1 && playerUuid != null) {
            RankingManager.RankingEntry playerRanking = app.rankingManager.getPlayerRankingByAdvancementId(advancementId, playerUuid);
            if (playerRanking != null && playerRanking.rank() > pageSize) {
                String formattedTime = sdf.format(playerRanking.timestamp());
                ChatUtils.displayRankingEntry(sender, playerRanking.rank(), playerRanking.playerName(), formattedTime, true);
            }
        }
        
        // ページネーション UI
        if (result.getTotalPages() > 1) {
            ChatUtils.displayPaginationUI(sender, advancementId, result.currentPage(), 
                    result.getTotalPages(), result.hasPreviousPage(), result.hasNextPage());
        }
        
        return true;
    }
    
    /**
     * 他人の進捗を見るコマンド
     */
    public boolean handleAdvCommand(CommandSender sender, String[] args) {
        // 自身を取得
        if (!(sender instanceof Player)) {
            sender.sendMessage("このコマンドはプレイヤーのみ実行可能です");
            return true;
        }
        CraftPlayer player = (CraftPlayer) sender;

        // ターゲットを取得
        if (args.length != 1) {
            player.sendMessage("プレイヤー名を指定してください");
            return true;
        }
        CraftPlayer target = Bukkit.selectEntities(sender, args[0]).stream()
                .filter(p -> p instanceof CraftPlayer)
                .map(p -> (CraftPlayer) p)
                .findFirst()
                .orElse(null);
        if (target == null) {
            player.sendMessage("プレイヤーが見つかりません");
            return true;
        }

        // 他のプレイヤーの進捗を見る
        player.sendTitle("「L」キーで進捗画面を開く", target.getName() + " の進捗を表示中...", 10, 100000, 10);
        PlayerData playerData = app.playerDataManager.getPlayerData(player);
        playerData.targetQueue = target;
        playerData.needUpdate = true;
        app.viewer.seePlayerAdvancements(player, target);
        
        return true;
    }
    
    /**
     * ID表示コマンド
     */
    public boolean handleAdvIdCommand(CommandSender sender, String[] args) {
        // 自身を取得
        if (!(sender instanceof Player)) {
            sender.sendMessage("このコマンドはプレイヤーのみ実行可能です");
            return true;
        }
        CraftPlayer player = (CraftPlayer) sender;

        player.sendTitle("「L」キーで進捗画面を開く", "進捗のIDを表示中...", 10, 100000, 10);
        PlayerData playerData = app.playerDataManager.getPlayerData(player);
        playerData.showId = true;
        playerData.targetQueue = null;
        playerData.needUpdate = true;
        app.viewer.seePlayerAdvancements(player, player);
        
        return true;
    }
    
    /**
     * 管理者コマンド
     */
    public boolean handleAdvAdminCommand(CommandSender sender, String[] args) {
        // 権限チェック
        if (!sender.hasPermission("advancementcompetition.admin")) {
            sender.sendMessage("§c権限がありません。");
            return true;
        }
        
        // サブコマンドのチェック
        if (args.length == 0) {
            sender.sendMessage("§c使用法: /adv_admin import_json_to_db");
            return true;
        }
        
        // import_json_to_db サブコマンド
        if (args[0].equals("import_json_to_db")) {
            AdvancementImporter importer = new AdvancementImporter(app.rankingManager);
            importer.importFromJson(sender);
            return true;
        }
        
        sender.sendMessage("§c不明なサブコマンド: " + args[0]);
        sender.sendMessage("§c使用法: /adv_admin import_json_to_db");
        return true;
    }
    
    /**
     * タブ補完
     */
    public List<String> handleTabComplete(CommandSender sender, Command command, String[] args) {
        // 他人の進捗を見る
        if (command.getName().equals("adv")) {
            return Stream.concat(Stream.of("<player>の進捗を見る"), Bukkit.getOnlinePlayers().stream().map(Player::getName))
                    .filter(name -> name.startsWith(args[0]))
                    .collect(Collectors.toList());
        }
        
        // ランキング表示
        if (command.getName().equals("adv_rank")) {
            if (args.length == 1) {
                return List.of("<実績ID>").stream()
                        .filter(cmd -> cmd.startsWith(args[0]))
                        .collect(Collectors.toList());
            } else if (args.length == 2) {
                return List.of("<ページ番号>").stream()
                        .filter(cmd -> cmd.startsWith(args[1]))
                        .collect(Collectors.toList());
            }
        }
        
        // 管理者コマンド
        if (command.getName().equals("adv_admin")) {
            if (args.length == 1) {
                return List.of("import_json_to_db").stream()
                        .filter(cmd -> cmd.startsWith(args[0]))
                        .collect(Collectors.toList());
            }
        }
        
        return null;
    }
}