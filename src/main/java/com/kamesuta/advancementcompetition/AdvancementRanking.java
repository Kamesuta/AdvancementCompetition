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
import net.minecraft.network.chat.MutableComponent;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Optional;

import static com.kamesuta.advancementcompetition.AdvancementCompetition.app;

/**
 * ランキング表示
 */
public class AdvancementRanking {
    /**
     * ランキング表示パケットアダプターを登録する
     */
    public void register() {
        app.protocolManager.addPacketListener(new PacketAdapter(app, PacketType.Play.Server.ADVANCEMENTS) {
            @Override
            public void onPacketSending(PacketEvent event) {
                Player viewer = event.getPlayer();

                // プレイヤーデータを取得
                PlayerData playerData = app.playerDataManager.getPlayerData(viewer);

                // タイトルに書くプレイヤー名を取得
                String titleSuffix = AdvancementUtil.formatLength(playerData.targetQueue != null ? " (" + playerData.targetQueue.getName() + "の進捗)" : "", 35);

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
                        // ランキングを取得
                        RankingProgressData ranking = app.rankingManager.getAdvancementProgressData(viewer, holder.toBukkit(), 3, 3);
                        if (ranking == null) return display; // ランキングが取得できなかったらそのまま返す

                        // タイトル 「進捗名 (プレイヤー名の進捗)」
                        Component title = Component.empty().append(display.getTitle()).append(Component.literal(titleSuffix).withStyle(ChatFormatting.GRAY));

                        // 説明
                        MutableComponent description = Component.empty()
                                .append(display.getDescription())
                                .append("\n\n");
                        // 説明 「進捗説明\n\nクリア率:%d/%d人(%d位)」
                        ranking.appendProgressDescription(description);

                        // 上位3人の進捗を追加
                        if (!ranking.top.isEmpty()) {
                            description.append("\n\n")
                                    .append(Component.literal("トップ3").withStyle(ChatFormatting.GOLD));
                            ranking.appendRanking(description, ranking.top);
                        }
                        // 直近3人の進捗を追加 (6人以上の場合)
                        if (!ranking.bottom.isEmpty() && ranking.total >= 6) {
                            description.append("\n\n")
                                    .append(Component.literal("直近達成3位").withStyle(ChatFormatting.BLUE));
                            ranking.appendRanking(description, ranking.bottom);
                        }

                        // 新しいDisplayInfoを作成
                        DisplayInfo copyDisplay = new DisplayInfo(display.getIcon(), title, description, display.getBackground(), display.getType(), display.shouldShowToast(), display.shouldAnnounceChat(), display.isHidden());
                        // 位置をコピー
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
