package com.kamesuta.advrank.data;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * プレイヤーデータ管理
 */
public class PlayerDataManager {
    /**
     * プレイヤーデータマップ
     */
    public Map<Player, PlayerData> playerDataMap = new ConcurrentHashMap<>();

    /**
     * プレイヤーデータを取得する
     *
     * @param player プレイヤー
     * @return プレイヤーデータ
     */
    public PlayerData getPlayerData(Player player) {
        return playerDataMap.computeIfAbsent(player, k -> new PlayerData());
    }
}
