package com.kamesuta.advrank.display;

import com.kamesuta.advrank.data.PlayerData;
import com.kamesuta.advrank.data.RankingProgressData;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.Converters;
import com.kamesuta.advrank.util.AdvancementUtil;
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

import static com.kamesuta.advrank.AdvRankingPlugin.app;

/**
 * ランキング表示クラス
 * プレイヤーが進捗画面を開いた際に、実績にランキング情報を動的に追加する
 * ProtocolLibを使用してパケットを書き換えることで実現している
 */
public class AdvancementRankingDisplay {

    /**
     * ランキング表示パケットリスナーを登録する
     * 進捗画面にランキング情報とIDを動的に追加する
     */
    public void register() {
        app.protocolManager.addPacketListener(new PacketAdapter(app, PacketType.Play.Server.ADVANCEMENTS) {
            @Override
            public void onPacketSending(PacketEvent event) {
                Player viewer = event.getPlayer();

                // プレイヤーデータを取得して表示設定を確認
                PlayerData playerData = app.playerDataManager.getPlayerData(viewer);

                // タイトルサフィックス（他プレイヤーの進捗を見ている場合の表示）を生成
                String titleSuffix = AdvancementUtil.formatLength(playerData.targetQueue != null ? " (" + playerData.targetQueue.getName() + "の進捗)" : "", 35);

                // 進捗パケットデータを取得・解析
                PacketContainer packetContainer = event.getPacket();
                StructureModifier<List<AdvancementHolder>> added = packetContainer.getLists(Converters.passthrough(AdvancementHolder.class));

                // 追加される進捗を取得
                List<AdvancementHolder> addedList = new ArrayList<>(added.read(0));

                // 各進捗にランキング情報を追加
                for (ListIterator<AdvancementHolder> it = addedList.listIterator(); it.hasNext(); ) {
                    AdvancementHolder holder = it.next();

                    // 進捗にランキングと誰の進捗かを追加する
                    Advancement advancement = holder.value();
                    Optional<DisplayInfo> displayInfo = advancement.display().map((display) -> {
                        // この実績のランキングデータを取得（上位3人、下位3人）
                        RankingProgressData ranking = app.rankingManager.getAdvancementProgressData(viewer, holder.toBukkit(), 3, 3);
                        if (ranking == null) return display; // ランキングが取得できなかったらそのまま返す

                        // 進捗タイトルを構築（他プレイヤーの進捗を見ている場合はその旨を表示）
                        Component title = Component.empty().append(display.getTitle()).append(Component.literal(titleSuffix).withStyle(ChatFormatting.GRAY));

                        // 進捗の説明文を構築（元の説明にランキング情報を追加）
                        MutableComponent description = Component.empty()
                                .append(display.getDescription())
                                .append("\n\n");
                        // ランキング進捗情報を追加（達成人数、順位など）
                        ranking.appendProgressDescription(description);

                        // 上位プレイヤーの情報を表示に追加
                        if (!ranking.top().isEmpty()) {
                            description.append("\n\n")
                                    .append(Component.literal("トップ3").withStyle(ChatFormatting.YELLOW));
                            ranking.appendRanking(description, ranking.top());
                            
                            // 全ランキング表示コマンドのリンクを生成
                            String advancementKey = holder.id().toString();
                            int advancementId = app.rankingManager.getAdvancementIdByKey(advancementKey);
                            if (advancementId != -1) {
                                description.append("\n")
                                        .append(Component.literal("/adv_rank " + advancementId).withStyle(ChatFormatting.BLUE))
                                        .append(Component.literal(" で全てのランキングを見る").withStyle(ChatFormatting.GRAY));
                            }
                        }
                        // 下位プレイヤーの情報も表示（達成者が6人以上の場合）
                        if (!ranking.bottom().isEmpty() && ranking.done() >= 6) {
                            description.append("\n\n")
                                    .append(Component.literal("直近達成3位").withStyle(ChatFormatting.BLUE));
                            ranking.appendRanking(description, ranking.bottom());
                        }

                        // ID表示モードの場合、実績IDを表示
                        if (playerData.showId) {
                            description.append("\n\n")
                                    .append(Component.literal("ID: ").withStyle(ChatFormatting.GRAY))
                                    .append(Component.literal(holder.id().toString()).withStyle(ChatFormatting.GOLD));
                        }

                        // ランキング情報を追加した新しいDisplayInfoオブジェクトを作成
                        DisplayInfo copyDisplay = new DisplayInfo(display.getIcon(), title, description, display.getBackground(), display.getType(), display.shouldShowToast(), display.shouldAnnounceChat(), display.isHidden());
                        // 元の進捗の位置情報を保持
                        copyDisplay.setLocation(display.getX(), display.getY());
                        return copyDisplay;
                    });
                    Advancement copyAdvancement = new Advancement(advancement.parent(), displayInfo, advancement.rewards(), advancement.criteria(), advancement.requirements(), advancement.sendsTelemetryEvent(), advancement.name());

                    // 修正された進捗情報でパケットを更新
                    it.set(new AdvancementHolder(holder.id(), copyAdvancement));
                }

                // 修正済みの進捗リストをパケットに書き戻し
                added.write(0, addedList);
            }
        });
    }
}
