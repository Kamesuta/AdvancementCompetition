package com.kamesuta.advrank;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * プレイヤーデータ
 */
public class PlayerData {
    /**
     * 次回進捗メニューを開いた時に進捗を更新するか
     */
    public boolean needUpdate = true;
    /**
     * 次回進捗メニューを開いたときに進捗を見る対象のプレイヤー
     */
    public @Nullable Player targetQueue;
    /**
     * IDを表示
     */
    public boolean showId = false;
}
