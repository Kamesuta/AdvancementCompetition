package com.kamesuta.advrank.command;

import com.kamesuta.advrank.data.PlayerData;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

import static com.kamesuta.advrank.AdvRankingPlugin.app;

/**
 * /adv_id コマンドハンドラー
 * 進捗のIDを表示するモードを切り替える
 */
public class AdvIdCommandHandler extends BaseCommandHandler {

    @Override
    public boolean handleCommand(CommandSender sender, String[] args) {
        // プレイヤー限定コマンドかをチェック
        if (!(sender instanceof Player player)) {
            sendErrorMessage(sender, "このコマンドはプレイヤーのみ実行可能です");
            return true;
        }

        // ID表示モードを有効にする
        displayAdvancementIds((CraftPlayer) player);
        return true;
    }

    /**
     * 進捗ID表示モードを有効にする
     */
    private void displayAdvancementIds(CraftPlayer player) {
        // プレイヤーにタイトルメッセージを表示
        player.sendTitle("「L」キーで進捗画面を開く", "進捗のIDを表示中...", 10, 100000, 10);
        
        // プレイヤーデータを更新してID表示モードを有効にする
        var playerData = app.playerDataManager.getPlayerData(player);
        playerData.showId = true;        // ID表示フラグをオン
        playerData.targetQueue = null;   // ターゲットをクリア（自分の進捗を表示）
        playerData.needUpdate = true;    // 更新フラグを立てる
        
        // 進捗ビューワーを開く
        app.viewer.seePlayerAdvancements(player, player);
    }

    @Override
    public List<String> handleTabComplete(CommandSender sender, String[] args) {
        // このコマンドには引数がないため補完候補なし
        return Collections.emptyList();
    }
}