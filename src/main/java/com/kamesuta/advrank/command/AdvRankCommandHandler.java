package com.kamesuta.advrank.command;

import com.kamesuta.advrank.display.ChatUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static com.kamesuta.advrank.AdvRankingPlugin.app;

/**
 * /adv_rank コマンドハンドラー
 * 実績のランキング表示を処理する
 */
public class AdvRankCommandHandler extends BaseCommandHandler {
    private static final int PAGE_SIZE = 10;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");

    @Override
    public boolean handleCommand(CommandSender sender, String[] args) {
        // 引数チェック（ID必須、ページ番号任意）
        if (args.length < 1 || args.length > 2) {
            sendErrorMessage(sender, "使用法: /adv_rank <ID> [ページ番号]");
            return true;
        }

        // 実績IDを解析
        var advancementId = parseAdvancementId(sender, args[0]);
        if (advancementId == -1) return true;

        // ページ番号を解析（デフォルトは1）
        var page = parsePage(sender, args);
        if (page == -1) return true;

        // 実績の存在確認
        if (!app.rankingManager.isAdvancementIdExists(advancementId)) {
            sendErrorMessage(sender, "指定されたID " + advancementId + " の実績は存在しません");
            return true;
        }

        // ランキングを表示
        displayRanking(sender, advancementId, page);
        return true;
    }

    /**
     * 実績IDを解析する
     */
    private int parseAdvancementId(CommandSender sender, String idStr) {
        try {
            return Integer.parseInt(idStr);
        } catch (NumberFormatException e) {
            sendErrorMessage(sender, "IDは数値で指定してください");
            return -1;
        }
    }

    /**
     * ページ番号を解析する
     */
    private int parsePage(CommandSender sender, String[] args) {
        if (args.length == 1) return 1;
        
        try {
            var page = Integer.parseInt(args[1]);
            if (page < 1) {
                sendErrorMessage(sender, "ページ番号は1以上で指定してください");
                return -1;
            }
            return page;
        } catch (NumberFormatException e) {
            sendErrorMessage(sender, "ページ番号は数値で指定してください");
            return -1;
        }
    }

    /**
     * ランキングを表示する
     */
    private void displayRanking(CommandSender sender, int advancementId, int page) {
        // ランキングデータを取得
        var result = app.rankingManager.getRankingByAdvancementIdWithPagination(advancementId, page, PAGE_SIZE);

        // データが存在しない場合
        if (result.totalCount() == 0) {
            sender.sendMessage("§7この実績を達成したプレイヤーはいません");
            return;
        }

        // ページ範囲チェック
        if (page > result.getTotalPages()) {
            sendErrorMessage(sender, "ページ " + page + " は存在しません（最大ページ: " + result.getTotalPages() + "）");
            return;
        }

        // 実績名表示（多言語対応）
        String advancementKey = result.advancementKey();
        var displayName = ChatUtils.getAdvancementDisplayName(advancementKey);
        
        // ヘッダー表示
        ChatUtils.displayRankingHeader(sender, displayName);
        
        // プレイヤーのUUIDを取得（自分のランキング表示用）
        UUID playerUuid = null;
        if (sender instanceof Player) {
            playerUuid = ((Player) sender).getUniqueId();
        }
        
        // ランキング一覧を表示
        for (var entry : result.entries()) {
            var formattedTime = DATE_FORMAT.format(entry.timestamp());
            var isCurrentPlayer = playerUuid != null && entry.playerName().equals(sender.getName());
            ChatUtils.displayRankingEntry(sender, entry.rank(), entry.playerName(), formattedTime, isCurrentPlayer);
        }

        // 1ページ目で自分がページ内にいない場合、自分のランキングを表示
        if (page == 1 && playerUuid != null) {
            var playerRanking = app.rankingManager.getPlayerRankingByAdvancementId(advancementId, playerUuid);
            if (playerRanking != null && playerRanking.rank() > PAGE_SIZE) {
                var formattedTime = DATE_FORMAT.format(playerRanking.timestamp());
                ChatUtils.displayRankingEntry(sender, playerRanking.rank(), playerRanking.playerName(), formattedTime, true);
            }
        }

        // ページネーション UI
        if (result.getTotalPages() > 1) {
            ChatUtils.displayPaginationUI(sender, advancementId, result.currentPage(), 
                    result.getTotalPages(), result.hasPreviousPage(), result.hasNextPage());
        }
    }

    @Override
    public List<String> handleTabComplete(CommandSender sender, String[] args) {
        return switch (args.length) {
            case 1 -> List.of("<実績ID>");
            case 2 -> List.of("<ページ番号>");
            default -> Collections.emptyList();
        };
    }
}