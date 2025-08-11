package com.kamesuta.advrank.command;

import com.kamesuta.advrank.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.kamesuta.advrank.AdvRankingPlugin.app;

/**
 * /adv コマンドハンドラー
 * 他のプレイヤーの進捗を表示する機能を処理する
 */
public class AdvCommandHandler extends BaseCommandHandler {

    @Override
    public boolean handleCommand(CommandSender sender, String[] args) {
        // プレイヤー限定コマンドかをチェック
        if (!(sender instanceof Player player)) {
            sendErrorMessage(sender, "このコマンドはプレイヤーのみ実行可能です");
            return true;
        }

        // 引数チェック（プレイヤー名必須）
        if (!validateArgsLength(sender, args, 1)) {
            sendErrorMessage(sender, "プレイヤー名を指定してください");
            return true;
        }

        // ターゲットプレイヤーを検索
        var target = findTargetPlayer(sender, args[0]);
        if (target == null) {
            sendErrorMessage(sender, "プレイヤーが見つかりません");
            return true;
        }

        // プレイヤーの進捗表示を開始
        displayPlayerAdvancements((CraftPlayer) player, target);
        return true;
    }

    /**
     * 指定された名前でプレイヤーを検索する
     */
    private CraftPlayer findTargetPlayer(CommandSender sender, String targetName) {
        return Bukkit.selectEntities(sender, targetName).stream()
                .filter(entity -> entity instanceof CraftPlayer)
                .map(entity -> (CraftPlayer) entity)
                .findFirst()
                .orElse(null);
    }

    /**
     * ターゲットプレイヤーの進捗表示を設定する
     */
    private void displayPlayerAdvancements(CraftPlayer player, CraftPlayer target) {
        // プレイヤーにタイトルメッセージを表示
        player.sendTitle("「L」キーで進捗画面を開く", target.getName() + " の進捗を表示中...", 10, 100000, 10);
        
        // プレイヤーデータを更新
        var playerData = app.playerDataManager.getPlayerData(player);
        playerData.targetQueue = target; // 表示対象を設定
        playerData.needUpdate = true;    // 更新フラグを立てる
        
        // 進捗ビューワーを開く
        app.viewer.seePlayerAdvancements(player, target);
    }

    @Override
    public List<String> handleTabComplete(CommandSender sender, String[] args) {
        // 1番目の引数：オンラインプレイヤー名の補完
        if (args.length == 1) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}