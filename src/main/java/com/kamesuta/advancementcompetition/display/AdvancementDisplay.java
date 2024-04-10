package com.kamesuta.advancementcompetition.display;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.advancement.Advancement;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.kamesuta.advancementcompetition.AdvancementCompetition.app;

/**
 * 進捗表示ブロック
 */
public class AdvancementDisplay implements Listener {
    /**
     * マップビュー
     */
    private List<PanelDisplay> panelList = new CopyOnWriteArrayList<>();

    /**
     * マップの数
     */
    public static final int mapLength = 3;

    /**
     * 表示距離
     */
    public static final int viewDistance = 40;

    /**
     * マップ
     */
    public List<MapDisplay> panel;

    /**
     * マップ (達成済み)
     */
    public List<MapDisplay> panelDone;

    /**
     * マップとパネルを読み込む
     *
     * @throws IOException マップが読み込めない場合
     */
    public void load() throws IOException {
        // マップ画像を読み込む
        panel = loadMap("panel.png");
        panelDone = loadMap("panel_done.png");

        // パネルを読み込む
        panelList = app.rankingManager.loadPanelData();
    }

    /**
     * マップを読み込む
     *
     * @param name 画像名
     * @return マップ
     * @throws IOException マップが読み込めない場合
     */
    private List<MapDisplay> loadMap(String name) throws IOException {
        // マップ画像を読み込む
        InputStream mapResource = app.getResource(name);
        if (mapResource == null) {
            throw new IOException(name + "が見つかりません");
        }
        BufferedImage image = ImageIO.read(mapResource);

        // mapLength分割
        return IntStream.range(0, mapLength)
                .mapToObj((i) -> {
                    int width = image.getWidth() / mapLength;
                    return image.getSubimage(width * i, 0, width, image.getHeight());
                })
                // マップに変換
                .map(MapDisplay::new)
                .collect(Collectors.toList());
    }

    /**
     * パネルを設置
     *
     * @param block       基準ブロック
     * @param face        基準ブロックの向き
     * @param advancement 進捗
     */
    public void place(Block block, BlockFace face, Advancement advancement) throws IllegalArgumentException {
        if (!face.isCartesian() || face == BlockFace.UP || face == BlockFace.DOWN) {
            throw new IllegalArgumentException("額縁は横向きに設置してください");
        }

        // マップを取得
        PanelDisplay panel = app.rankingManager.recordPanelData(block, face, advancement);
        if (panel == null) {
            throw new IllegalArgumentException("パネルの設置に失敗しました");
        }
        panelList.add(panel);

        // 周りにいる人のパネルを更新
        Collection<Player> players = block.getLocation().getNearbyPlayers(viewDistance);
        Bukkit.getScheduler().runTaskAsynchronously(app, () ->
                players.forEach(player -> refreshPanel(player, player.getLocation(), false, panel)));
    }

    /**
     * パネルを削除
     *
     * @param block 基準ブロック
     * @param face  基準ブロックの向き
     */
    public void destroy(Block block, BlockFace face) throws IllegalArgumentException {
        // マップを検索
        PanelDisplay panel = panelList.stream()
                .filter(p -> p.baseBlock.equals(block) && p.direction == face)
                .findFirst().orElse(null);
        if (panel == null) {
            // パネルが見つからない
            throw new IllegalArgumentException("パネルが見つかりません");
        }

        // マップを削除
        app.rankingManager.removePanelData(panel.id);
        panelList.remove(panel);

        // 周りにいる人のパネルを更新
        Collection<Player> players = block.getLocation().getNearbyPlayers(viewDistance);
        Bukkit.getScheduler().runTaskAsynchronously(app, () -> players.forEach(panel::hide));
    }

    /**
     * 実績解放時にパネルを更新
     *
     * @param advancement 実績
     */
    public void refreshPanelOnAdvancement(Advancement advancement) {
        panelList.stream()
                .filter(panel -> panel.advancement.getKey().equals(advancement.getKey()))
                .forEach(panel -> {
                    // 周りにいる人のパネルを更新
                    Collection<Player> players = panel.baseBlock.getLocation().getNearbyPlayers(viewDistance);
                    Bukkit.getScheduler().runTaskAsynchronously(app, () ->
                            players.forEach(player -> refreshPanel(player, player.getLocation(), true, panel)));
                });
    }

    /**
     * すべて表示 (ロード時)
     */
    public void showAll() {
        panelList.forEach(panel -> {
            // 周りにいる人のパネルを更新
            Collection<Player> players = panel.baseBlock.getLocation().getNearbyPlayers(viewDistance);
            Bukkit.getScheduler().runTaskAsynchronously(app, () ->
                    players.forEach(player -> panel.show(player, true)));
        });
    }

    /**
     * すべて非表示 (アンロード時)
     */
    public void hideAll() {
        panelList.forEach(panel -> {
            // 周りにいる人のパネルを更新
            Collection<Player> players = panel.baseBlock.getLocation().getNearbyPlayers(viewDistance);
            players.forEach(panel::hide);
        });
    }

    /**
     * プレイヤーへのパネル表示を更新
     *
     * @param player   プレイヤー
     * @param location 位置
     * @param force    強制更新
     */
    public void refreshAllPanel(Player player, Location location, boolean force) {
        for (PanelDisplay panel : panelList) {
            refreshPanel(player, location, force, panel);
        }
    }

    /**
     * プレイヤーへのパネル表示を更新
     *
     * @param player   プレイヤー
     * @param location 位置
     * @param force    強制更新
     * @param panel    パネル
     */
    public void refreshPanel(Player player, Location location, boolean force, PanelDisplay panel) {
        // 表示範囲内か
        boolean canSee = location.getWorld().equals(panel.baseBlock.getWorld()) && // 同じワールド
                location.distanceSquared(panel.baseBlock.getLocation()) < viewDistance * viewDistance; // 表示範囲内

        // 表示/非表示
        if (canSee) {
            panel.show(player, force);
        } else {
            panel.hide(player);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Location location = player.getLocation();

        // プレイヤーの全パネルを更新
        Bukkit.getScheduler().runTaskLaterAsynchronously(app, () -> refreshAllPanel(player, location, true), 20);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Location location = event.getRespawnLocation();

        // プレイヤーの全パネルを更新
        Bukkit.getScheduler().runTaskLaterAsynchronously(app, () -> refreshAllPanel(player, location, false), 20);
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        Location location = player.getLocation();

        // プレイヤーの全パネルを更新
        Bukkit.getScheduler().runTaskLaterAsynchronously(app, () -> refreshAllPanel(player, location, false), 20);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location from = event.getFrom(), to = event.getTo();

        if (from.getBlockX() >> 4 != to.getBlockX() >> 4 ||
                from.getBlockZ() >> 4 != to.getBlockZ() >> 4) {
            // プレイヤーの全パネルを更新
            Bukkit.getScheduler().runTaskLaterAsynchronously(app, () -> refreshAllPanel(player, to, false), 20);
        }
    }
}
