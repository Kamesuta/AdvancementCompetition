package com.kamesuta.advancementcompetition;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.kamesuta.advancementcompetition.display.AdvancementDisplay;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.v1_20_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.RayTraceResult;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

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

    /**
     * ディスプレイ
     */
    public AdvancementDisplay display;

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

        // Viewer初期化
        viewer = new AdvancementViewer();
        viewer.register();
        // Ranking初期化
        ranking = new AdvancementRanking();
        ranking.register();

        // Display初期化
        display = new AdvancementDisplay();
        try {
            display.load();
        } catch (IOException e) {
            logger.log(Level.SEVERE, "マップの初期化に失敗しました", e);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        getServer().getPluginManager().registerEvents(display, this);
        // パネルを表示
        display.showAll();
    }

    @Override
    public void onDisable() {
        // パネルを非表示
        if (display != null) {
            display.hideAll();
        }

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
    public boolean onCommand(@NotNull CommandSender sender, Command command, @NotNull String label, String[] args) {
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

        // パネルを設置
        if (command.getName().equals("adv_place")) {
            // 自身を取得
            if (!(sender instanceof Player)) {
                sender.sendMessage("このコマンドはプレイヤーのみ実行可能です");
                return true;
            }
            Player player = (Player) sender;

            // 引数を取得
            if (args.length != 1) {
                player.sendMessage("進捗IDを指定してください /adv_place story/root");
                return true;
            }
            // 進捗を取得
            Advancement advancement = Optional.ofNullable(NamespacedKey.fromString(args[0])).map(Bukkit::getAdvancement).orElse(null);
            if (advancement == null) {
                player.sendMessage("進捗が見つかりません");
                return true;
            }

            // 目線の先を取得
            RayTraceResult result = player.rayTraceBlocks(6, FluidCollisionMode.NEVER);
            if (result != null && result.getHitBlock() != null && result.getHitBlockFace() != null) {
                // 設置
                try {
                    display.place(result.getHitBlock(), result.getHitBlockFace(), advancement);
                } catch (IllegalArgumentException e) {
                    sender.sendMessage(e.getMessage());
                }
            }
        }

        // パネルを削除
        if (command.getName().equals("adv_destroy")) {
            // 自身を取得
            if (!(sender instanceof Player)) {
                sender.sendMessage("このコマンドはプレイヤーのみ実行可能です");
                return true;
            }
            Player player = (Player) sender;

            // 目線の先を取得
            RayTraceResult result = player.rayTraceBlocks(6, FluidCollisionMode.NEVER);
            if (result != null && result.getHitBlock() != null && result.getHitBlockFace() != null) {
                // 削除
                try {
                    display.destroy(result.getHitBlock(), result.getHitBlockFace());
                } catch (IllegalArgumentException e) {
                    sender.sendMessage(e.getMessage());
                }
            }
        }

        return true;
    }

    // タブ補完
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, Command command, @NotNull String alias, String[] args) {
        // 他人の進捗を見る
        if (command.getName().equals("adv")) {
            return Stream.concat(Stream.of("<player>の進捗を見る"), Bukkit.getOnlinePlayers().stream().map(Player::getName))
                    .filter(name -> name.startsWith(args[0]))
                    .collect(Collectors.toList());
        }

        // 進捗を設置
        if (command.getName().equals("adv_place")) {
            Stream<Advancement> advancements = StreamSupport.stream(
                    Spliterators.spliteratorUnknownSize(Bukkit.advancementIterator(), Spliterator.ORDERED),
                    false
            );
            return Stream.concat(Stream.of("<進捗ID>"), advancements.map(advancement -> advancement.getKey().value()))
                    .filter(name -> name.startsWith(args[0]))
                    .collect(Collectors.toList());
        }
        return null;
    }
}
