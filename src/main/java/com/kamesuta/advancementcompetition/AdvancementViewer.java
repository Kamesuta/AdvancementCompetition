package com.kamesuta.advancementcompetition;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import net.minecraft.network.protocol.game.ServerboundSeenAdvancementsPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.advancements.AdvancementVisibilityEvaluator;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_20_R3.CraftServer;
import org.bukkit.craftbukkit.v1_20_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.*;

import static com.kamesuta.advancementcompetition.AdvancementCompetition.app;

/**
 * 他人の進捗を見る
 */
public class AdvancementViewer {
    /**
     * プレイヤーが他のプレイヤーの進捗を見る
     *
     * @param viewer 見るプレイヤー
     *               このプレイヤーに進捗を送信する
     * @param target 見られるプレイヤー
     */
    public void seePlayerAdvancements(CraftPlayer viewer, CraftPlayer target) {
        // 送信するデータ
        List<AdvancementHolder> toAdd = new ArrayList<>();
        Set<ResourceLocation> toRemove = new HashSet<>();
        Map<ResourceLocation, AdvancementProgress> toUpdate = new HashMap<>();

        // 進捗を取得する
        ServerAdvancementManager advancementManager = ((CraftServer) Bukkit.getServer()).getServer().getAdvancements();
        PlayerAdvancements playerAdvancements = target.getHandle().getAdvancements();
        for (AdvancementHolder advancementHolder : advancementManager.getAllAdvancements()) {
            AdvancementProgress progress = playerAdvancements.getOrStartProgress(advancementHolder);

            // 進捗を追加する
            toUpdate.put(advancementHolder.id(), progress);
        }

        // 表示されている進捗を追加する
        for (AdvancementNode root : advancementManager.tree().roots()) {
            AdvancementVisibilityEvaluator.evaluateVisibility(root,
                    (node) -> playerAdvancements.getOrStartProgress(node.holder()).isDone(),
                    (node, flag) -> {
                        if (flag) {
                            // 進捗を追加する
                            toAdd.add(node.holder());
                        }
                    }
            );
        }

        // 進捗を送信する
        viewer.getHandle().connection.send(new ClientboundUpdateAdvancementsPacket(true, toAdd, toRemove, toUpdate));
    }

    /**
     * タブ閉じるパケットアダプターを登録する
     */
    public void register() {
        app.protocolManager.addPacketListener(new PacketAdapter(app, PacketType.Play.Client.ADVANCEMENTS) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                PacketContainer packetContainer = event.getPacket();
                ServerboundSeenAdvancementsPacket packet = (ServerboundSeenAdvancementsPacket) packetContainer.getHandle();

                // 進捗タブが開かれた
                if (packet.getAction() == ServerboundSeenAdvancementsPacket.Action.OPENED_TAB) {
                    onAdvancementTabOpen(event.getPlayer());
                }

                // 進捗タブが閉じられた
                if (packet.getAction() == ServerboundSeenAdvancementsPacket.Action.CLOSED_SCREEN) {
                    onAdvancementTabClose(event.getPlayer());
                }
            }
        });
    }

    /**
     * 進捗タブが開かれた
     *
     * @param viewer 見るプレイヤー
     */
    private void onAdvancementTabOpen(Player viewer) {
        // プレイヤーデータを取得
        PlayerData playerData = app.playerDataManager.getPlayerData(viewer);
        if (playerData.needUpdate) {
            // タイトルを消す
            viewer.sendTitle("", "", 0, 0, 0);

            // 進捗を更新する
            playerData.needUpdate = false;
        }
    }

    /**
     * 進捗タブが閉じられた
     *
     * @param viewer 見るプレイヤー
     */
    private void onAdvancementTabClose(Player viewer) {
        // プレイヤーデータを取得
        PlayerData playerData = app.playerDataManager.getPlayerData(viewer);
        // ターゲットをリセット
        playerData.targetQueue = null;
        // ID表示をリセット
        playerData.showId = false;
        // 元に戻す
        seePlayerAdvancements((CraftPlayer) viewer, (CraftPlayer) viewer);
    }
}
