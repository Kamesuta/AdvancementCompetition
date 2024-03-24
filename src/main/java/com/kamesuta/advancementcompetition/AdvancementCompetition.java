package com.kamesuta.advancementcompetition;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.v1_20_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;

public final class AdvancementCompetition extends JavaPlugin implements Listener {
    /**
     * ロガー
     */
    public static Logger logger;
    /**
     * プラグイン
     */
    public static AdvancementCompetition plugin;

    /**
     * ProtocolLibのプロトコルマネージャ
     */
    public ProtocolManager protocolManager;
    /**
     * プレイヤーデータ管理
     */
    public PlayerDataManager playerDataManager = new PlayerDataManager();

    /**
     * 他人の進捗を見る
     */
    public AdvancementViewer viewer;

    /**
     * ランキング表示
     */
    public AdvancementRanking ranking;

    @Override
    public void onEnable() {
        // Plugin startup logic
        logger = getLogger();
        plugin = this;

        // イベントリスナー
        getServer().getPluginManager().registerEvents(this, this);
        // ProtocolLib
        protocolManager = ProtocolLibrary.getProtocolManager();

        // プレイヤーデータ
        playerDataManager = new PlayerDataManager();

        // Viewer初期化
        viewer = new AdvancementViewer();
        viewer.register();
        // Ranking初期化
        ranking = new AdvancementRanking();
        ranking.register();
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    // コマンドハンドラ
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equals("adv")) {
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
            PlayerData playerData = playerDataManager.getPlayerData(player);
            playerData.targetQueue = target;
            playerData.needUpdate = true;
            viewer.seePlayerAdvancements(player, target);
        }

        return true;
    }
}
