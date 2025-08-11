package com.kamesuta.advrank;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import com.kamesuta.advrank.command.CommandHandler;
import com.kamesuta.advrank.data.PlayerDataManager;
import com.kamesuta.advrank.database.RankingManager;
import com.kamesuta.advrank.display.AdvancementRankingDisplay;
import com.kamesuta.advrank.display.AdvancementViewer;


import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * プラグイン
 */
public final class AdvRankingPlugin extends JavaPlugin implements Listener {
    /**
     * ロガー
     */
    public static Logger logger;
    /**
     * プラグイン
     */
    public static AdvRankingPlugin app;

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
    public AdvancementRankingDisplay ranking;
    /**
     * ランキングマネージャー
     */
    public RankingManager rankingManager;
    /**
     * コマンドハンドラ
     */
    public CommandHandler commandHandler;

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
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> rankingManager.pingDatabase(), 0, 300 * 20);

        // Viewer初期化
        viewer = new AdvancementViewer();
        viewer.register();
        // Ranking初期化
        ranking = new AdvancementRankingDisplay();
        ranking.register();
        
        // コマンドハンドラ初期化
        commandHandler = new CommandHandler();
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
        // ランキング表示
        return switch (command.getName()) {
            // ランキング表示
            case "adv_rank" -> commandHandler.handleAdvRankCommand(sender, args);

            // 他人の進捗を見る
            case "adv" -> commandHandler.handleAdvCommand(sender, args);

            // ID表示
            case "adv_id" -> commandHandler.handleAdvIdCommand(sender, args);

            // 管理者コマンド
            case "adv_admin" -> commandHandler.handleAdvAdminCommand(sender, args);

            // その他のコマンドは無視
            default -> true;
        };
    }

    // タブ補完
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String @NotNull [] args) {
        return commandHandler.handleTabComplete(sender, command, args);
    }
}
