package com.kamesuta.advancementcompetition;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.advancements.AdvancementVisibilityEvaluator;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.v1_20_R3.CraftServer;
import org.bukkit.craftbukkit.v1_20_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.text.DateFormat;
import java.util.*;
import java.util.logging.Logger;

import static jdk.internal.org.jline.keymap.KeyMap.display;

public final class AdvancementCompetition extends JavaPlugin {
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

    @Override
    public void onEnable() {
        // Plugin startup logic
        logger = getLogger();
        plugin = this;

        // ProtocolLib
        protocolManager = ProtocolLibrary.getProtocolManager();
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    // コマンドハンドラ
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equals("advancementcompetition")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("このコマンドはプレイヤーのみ実行可能です");
                return true;
            }
            CraftPlayer player = (CraftPlayer) sender;

            if (args.length != 1) {
                player.sendMessage("プレイヤー名を指定してください");
                return true;
            }
            CraftPlayer otherPlayer = Bukkit.selectEntities(sender, args[0]).stream()
                    .filter(p -> p instanceof CraftPlayer)
                    .map(p -> (CraftPlayer) p)
                    .findFirst()
                    .orElse(null);
            if (otherPlayer == null) {
                player.sendMessage("プレイヤーが見つかりません");
                return true;
            }

            // 送信するデータ
            List<AdvancementHolder> toAdd = new ArrayList<>();
            Set<ResourceLocation> toRemove = new HashSet<>();
            Map<ResourceLocation, AdvancementProgress> toUpdate = new HashMap<>();

            // 進捗を取得する
            ServerAdvancementManager advancementManager = ((CraftServer) Bukkit.getServer()).getServer().getAdvancements();
            PlayerAdvancements playerAdvancements = otherPlayer.getHandle().getAdvancements();
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
                                // 進捗を弄る
                                Advancement advancement = node.advancement();
                                Optional<DisplayInfo> displayInfo = advancement.display().map((display) -> {
                                    String string = display.getTitle().getString();
                                    Component title = Component.empty().append(display.getTitle()).append(Component.literal(" (" + player.getName() + "の進捗)").withStyle(ChatFormatting.GRAY));
                                    String date = DateFormat.getDateTimeInstance().format(new Date());
                                    Component description = Component.empty().append(display.getDescription()).append("\n\n").append(Component.literal("クリア率:2/40人(2位)").withStyle(ChatFormatting.YELLOW)).append("\n").append(Component.literal(date + "時点").withStyle(ChatFormatting.GRAY));
                                    DisplayInfo copyDisplay = new DisplayInfo(display.getIcon(), title, description, display.getBackground(), display.getType(), display.shouldShowToast(), display.shouldAnnounceChat(), display.isHidden());
                                    copyDisplay.setLocation(display.getX(), display.getY());
                                    return copyDisplay;
                                });
                                Advancement copyAdvancement = new Advancement(advancement.parent(), displayInfo, advancement.rewards(), advancement.criteria(), advancement.requirements(), advancement.sendsTelemetryEvent(), advancement.name());

                                // 進捗を追加する
                                toAdd.add(new AdvancementHolder(node.holder().id(), copyAdvancement));
                            }
                        }
                );
            }

            // 進捗を送信する
            player.getHandle().connection.send(new ClientboundUpdateAdvancementsPacket(true, toAdd, toRemove, toUpdate));
        }

        return true;
    }
}
