package com.kamesuta.advancementcompetition;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.Converters;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.network.chat.Component;
import org.bukkit.entity.Player;

import java.text.DateFormat;
import java.util.*;

import static com.kamesuta.advancementcompetition.AdvancementCompetition.plugin;

/**
 * ランキング表示
 */
public class AdvancementRanking {
    /**
     * ランキング表示パケットアダプターを登録する
     */
    public void register() {
        PlayerDataManager playerDataManager = plugin.playerDataManager;
        plugin.protocolManager.addPacketListener(new PacketAdapter(plugin, PacketType.Play.Server.ADVANCEMENTS) {
            @Override
            public void onPacketSending(PacketEvent event) {
                Player viewer = event.getPlayer();

                // プレイヤーデータを取得
                PlayerData playerData = playerDataManager.getPlayerData(viewer);
                Player target = playerData.targetQueue != null ? playerData.targetQueue : viewer;

                // パケットを取得
                PacketContainer packetContainer = event.getPacket();
                StructureModifier<List<AdvancementHolder>> added = packetContainer.getLists(Converters.passthrough(AdvancementHolder.class));

                // 追加される進捗を取得
                List<AdvancementHolder> addedList = new ArrayList<>(added.read(0));

                // 送信しようとしている進捗を弄る
                for (ListIterator<AdvancementHolder> it = addedList.listIterator(); it.hasNext(); ) {
                    AdvancementHolder holder = it.next();

                    // 進捗にランキングと誰の進捗かを追加する
                    Advancement advancement = holder.value();
                    Optional<DisplayInfo> displayInfo = advancement.display().map((display) -> {
                        Component title = Component.empty().append(display.getTitle()).append(Component.literal(" (" + target.getName() + "の進捗)").withStyle(ChatFormatting.GRAY));
                        String date = DateFormat.getDateTimeInstance().format(new Date());
                        Component description = Component.empty().append(display.getDescription()).append("\n\n").append(Component.literal("クリア率:2/40人(2位)").withStyle(ChatFormatting.YELLOW)).append("\n").append(Component.literal(date + "時点").withStyle(ChatFormatting.GRAY));
                        DisplayInfo copyDisplay = new DisplayInfo(display.getIcon(), title, description, display.getBackground(), display.getType(), display.shouldShowToast(), display.shouldAnnounceChat(), display.isHidden());
                        copyDisplay.setLocation(display.getX(), display.getY());
                        return copyDisplay;
                    });
                    Advancement copyAdvancement = new Advancement(advancement.parent(), displayInfo, advancement.rewards(), advancement.criteria(), advancement.requirements(), advancement.sendsTelemetryEvent(), advancement.name());

                    // 進捗を更新する
                    it.set(new AdvancementHolder(holder.id(), copyAdvancement));
                }

                // 追加される進捗を設定
                added.write(0, addedList);
            }
        });
    }
}
