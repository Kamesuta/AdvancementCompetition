package com.kamesuta.advancementcompetition;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.logging.Level;

import static com.kamesuta.advancementcompetition.AdvancementCompetition.logger;

/**
 * チャット関連のユーティリティクラス
 */
public class ChatUtils {
    
    /**
     * ページネーションUIを表示
     * @param sender コマンド送信者
     * @param advancementId 実績ID
     * @param currentPage 現在のページ
     * @param totalPages 総ページ数
     * @param hasPrevious 前のページがあるか
     * @param hasNext 次のページがあるか
     */
    public static void displayPaginationUI(CommandSender sender, int advancementId, 
                                          int currentPage, int totalPages, 
                                          boolean hasPrevious, boolean hasNext) {
        // ===== ◀ (8/33ページ) ▶ (1 … 6 | 7 | 8̲ | 9 | 10 … 33) ===== の形式でコンポーネントを構築
        var ui = Component.text("===== ", NamedTextColor.YELLOW);
        
        // 前のページ矢印（クリック可能）
        if (hasPrevious) {
            String prevCommand = "/adv_rank " + advancementId + " " + (currentPage - 1);
            ui = ui.append(Component.text("◀", NamedTextColor.GREEN)
                    .clickEvent(ClickEvent.runCommand(prevCommand))
                    .hoverEvent(HoverEvent.showText(Component.text(prevCommand, NamedTextColor.GRAY))));
        } else {
            ui = ui.append(Component.text("◀", NamedTextColor.GRAY));
        }
        
        // ページ情報
        ui = ui.append(Component.text(" (" + currentPage + "/" + totalPages + "ページ) ", NamedTextColor.WHITE));
        
        // 次のページ矢印（クリック可能）
        if (hasNext) {
            String nextCommand = "/adv_rank " + advancementId + " " + (currentPage + 1);
            ui = ui.append(Component.text("▶", NamedTextColor.GREEN)
                    .clickEvent(ClickEvent.runCommand(nextCommand))
                    .hoverEvent(HoverEvent.showText(Component.text(nextCommand, NamedTextColor.GRAY))));
        } else {
            ui = ui.append(Component.text("▶", NamedTextColor.GRAY));
        }
        
        // ページ番号表示
        ui = ui.append(buildPageNumbers(advancementId, currentPage, totalPages));
        
        ui = ui.append(Component.text(" =====", NamedTextColor.YELLOW));
        
        // Adventureコンポーネントとしてそのまま送信（プレイヤー・コンソール両対応）
        sender.sendMessage(ui);
    }
    
    /**
     * ページ番号部分のコンポーネントを構築
     * CoreProtect方式のページ番号リンク表示: (1 … 6 | 7 | 8̲ | 9 | 10 … 33)
     */
    private static Component buildPageNumbers(int advancementId, int currentPage, int totalPages) {
        var result = Component.text(" (", NamedTextColor.WHITE);
        
        // CoreProtectロジック: 表示範囲を決定
        int displayStart, displayEnd;
        
        if (totalPages <= 7) {
            // 7ページ以下は全て表示
            displayStart = 1;
            displayEnd = totalPages;
        } else if (currentPage <= 3) {
            // 最初の方: 1〜6を表示
            displayStart = 1;
            displayEnd = 6;
        } else if (currentPage >= totalPages - 2) {
            // 最後の方: 最後の6ページを表示
            displayStart = totalPages - 5;
            displayEnd = totalPages;
        } else {
            // 中間: 現在ページの前後2ページを表示
            displayStart = currentPage - 2;
            displayEnd = currentPage + 2;
        }
        
        // 最初のページと省略記号
        if (displayStart > 1) {
            // ページ1を表示
            result = result.append(createPageLink(advancementId, 1, false));
            
            // 省略記号（ページ2が表示範囲外の場合のみ）
            if (displayStart > 2) {
                result = result.append(Component.text(" … ", NamedTextColor.GRAY));
            } else {
                result = result.append(Component.text(" | ", NamedTextColor.GRAY));
            }
        }
        
        // ページ番号の表示
        for (int i = displayStart; i <= displayEnd; i++) {
            if (i == currentPage) {
                // 現在のページ（下線付き、黄色）
                result = result.append(Component.text(String.valueOf(i), NamedTextColor.YELLOW)
                        .decoration(TextDecoration.UNDERLINED, true));
            } else {
                // クリック可能なページ番号
                result = result.append(createPageLink(advancementId, i, false));
            }
            
            // セパレータ
            if (i < displayEnd || displayEnd < totalPages) {
                result = result.append(Component.text(" | ", NamedTextColor.GRAY));
            }
        }
        
        // 最後のページと省略記号
        if (displayEnd < totalPages) {
            // 省略記号（最後から2番目のページが表示範囲外の場合のみ）
            if (displayEnd < totalPages - 1) {
                result = result.append(Component.text("… ", NamedTextColor.GRAY));
            }
            
            // 最後のページを表示
            result = result.append(createPageLink(advancementId, totalPages, false));
        }
        
        result = result.append(Component.text(")", NamedTextColor.WHITE));
        return result;
    }
    
    /**
     * クリック可能なページ番号リンクを作成
     */
    private static Component createPageLink(int advancementId, int pageNum, boolean isCurrentPage) {
        String pageCommand = "/adv_rank " + advancementId + " " + pageNum;
        
        if (isCurrentPage) {
            return Component.text(String.valueOf(pageNum), NamedTextColor.YELLOW)
                    .decoration(TextDecoration.UNDERLINED, true);
        } else {
            return Component.text(String.valueOf(pageNum), NamedTextColor.AQUA)
                    .clickEvent(ClickEvent.runCommand(pageCommand))
                    .hoverEvent(HoverEvent.showText(Component.text(pageCommand, NamedTextColor.GRAY)));
        }
    }
    
    /**
     * ランキングヘッダーを表示
     */
    public static void displayRankingHeader(CommandSender sender, Component displayName) {
        Component header = Component.text("===== 実績ランキング: ", NamedTextColor.YELLOW)
                .append(displayName)
                .append(Component.text(" =====", NamedTextColor.YELLOW));
        sender.sendMessage(header);
    }
    
    /**
     * ランキングエントリーを表示
     */
    public static void displayRankingEntry(CommandSender sender, int rank, String playerName, 
                                          String formattedTime, boolean isCurrentPlayer) {
        if (isCurrentPlayer) {
            sender.sendMessage("§6" + rank + "位: " + playerName + " §7- " + formattedTime);
        } else {
            sender.sendMessage("§f" + rank + "位: §b" + playerName + " §7- " + formattedTime);
        }
    }
    
    /**
     * 実績キーから表示名を取得（多言語対応）
     * @param advancementKey 実績キー
     * @return 表示名のComponent
     */
    public static Component getAdvancementDisplayName(String advancementKey) {
        if (advancementKey == null) return Component.text("不明な実績");
        
        // NamespacedKeyを作成
        NamespacedKey key = NamespacedKey.fromString(advancementKey);
        if (key == null) {
            return Component.text(getFallbackDisplayName(advancementKey));
        }
        
        // 進捗を取得
        Advancement advancement = Bukkit.getAdvancement(key);
        if (advancement == null) {
            return Component.text(getFallbackDisplayName(advancementKey));
        }
        
        // 進捗の表示名を取得
        if (advancement.getDisplay() != null) {
            // advancement.getDisplay().title()でKyori Componentを取得
            return advancement.getDisplay().title();
        }
        
        return Component.text(getFallbackDisplayName(advancementKey));
    }
    
    /**
     * フォールバック用の表示名生成
     * @param advancementKey 実績キー
     * @return フォールバック表示名
     */
    private static String getFallbackDisplayName(String advancementKey) {
        // minecraft: プレフィックスを削除
        String displayName = advancementKey.replace("minecraft:", "");
        // スラッシュをスペースに置換
        displayName = displayName.replace("/", " > ");
        // アンダースコアをスペースに置換
        displayName = displayName.replace("_", " ");
        
        return displayName;
    }
}