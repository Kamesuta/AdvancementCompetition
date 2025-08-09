package com.kamesuta.advancementcompetition;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * プラグイン
 */
public final class AdvancementCompetition extends JavaPlugin implements Listener {
    /**
     * ロガー
     */
    public static Logger logger;
    /**
     * プラグイン
     */
    public static AdvancementCompetition app;

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
    /**
     * ランキングマネージャー
     */
    public RankingManager rankingManager;

    @Override
    public void onEnable() {
        // Plugin startup logic
        logger = getLogger();
        app = this;

        // コンフィグ初期化
        saveDefaultConfig();

        // イベントリスナー
        getServer().getPluginManager().registerEvents(this, this);
        // ProtocolLib
        protocolManager = ProtocolLibrary.getProtocolManager();

        // プレイヤーデータ
        playerDataManager = new PlayerDataManager();

        // ランキングマネージャー初期化
        try {
            rankingManager = new RankingManager();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "ランキングマネージャーの初期化に失敗しました", e);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        getServer().getPluginManager().registerEvents(rankingManager, this);

        // 定期的にSQLのPingを送信
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            rankingManager.pingDatabase();
        }, 0, 300 * 20);

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
        if (rankingManager != null) {
            try {
                rankingManager.close();
            } catch (Exception e) {
                logger.log(Level.SEVERE, "ランキングマネージャーのクローズに失敗しました", e);
            }
        }
    }

    // コマンドハンドラ
    public boolean onCommand(@NotNull CommandSender sender, Command command, @NotNull String label, String @NotNull [] args) {
        // 他人の進捗を見る
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

        // ID表示
        if (command.getName().equals("adv_id")) {
            // 自身を取得
            if (!(sender instanceof Player)) {
                sender.sendMessage("このコマンドはプレイヤーのみ実行可能です");
                return true;
            }
            CraftPlayer player = (CraftPlayer) sender;

            player.sendTitle("「L」キーで進捗画面を開く", "進捗のIDを表示中...", 10, 100000, 10);
            PlayerData playerData = playerDataManager.getPlayerData(player);
            playerData.showId = true;
            playerData.targetQueue = null;
            playerData.needUpdate = true;
            viewer.seePlayerAdvancements(player, player);
        }

        // 管理者コマンド
        if (command.getName().equals("adv_admin")) {
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
                AdvancementImporter importer = new AdvancementImporter(rankingManager);
                importer.importFromJson(sender);
                return true;
            }
            
            sender.sendMessage("§c不明なサブコマンド: " + args[0]);
            sender.sendMessage("§c使用法: /adv_admin import_json_to_db");
            return true;
        }

        return true;
    }

    // タブ補完
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, Command command, @NotNull String alias, String @NotNull [] args) {
        // 他人の進捗を見る
        if (command.getName().equals("adv")) {
            return Stream.concat(Stream.of("<player>の進捗を見る"), Bukkit.getOnlinePlayers().stream().map(Player::getName))
                    .filter(name -> name.startsWith(args[0]))
                    .collect(Collectors.toList());
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
