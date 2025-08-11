package com.kamesuta.advrank.command;

import com.kamesuta.advrank.importer.AdvancementImporter;
import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;

import static com.kamesuta.advrank.AdvRankingPlugin.app;

/**
 * /adv_admin コマンドハンドラー
 * 管理者専用コマンドを処理する
 */
public class AdvAdminCommandHandler extends BaseCommandHandler {
    private static final String PERMISSION = "advrank.admin";
    private static final String IMPORT_COMMAND = "import_json_to_db";

    @Override
    public boolean handleCommand(CommandSender sender, String[] args) {
        // 権限チェック
        if (!sender.hasPermission(PERMISSION)) {
            sendErrorMessage(sender, "権限がありません。");
            return true;
        }

        // サブコマンドの存在チェック
        if (args.length == 0) {
            sendErrorMessage(sender, "使用法: /adv_admin " + IMPORT_COMMAND);
            return true;
        }

        // サブコマンドを実行
        return switch (args[0]) {
            case IMPORT_COMMAND -> executeImportCommand(sender);
            default -> {
                sendErrorMessage(sender, "不明なサブコマンド: " + args[0]);
                sendErrorMessage(sender, "使用法: /adv_admin " + IMPORT_COMMAND);
                yield true;
            }
        };
    }

    /**
     * JSONインポートコマンドを実行する
     */
    private boolean executeImportCommand(CommandSender sender) {
        try {
            // インポーターを作成して実行
            var importer = new AdvancementImporter(app.rankingManager);
            importer.importFromJson(sender);
        } catch (Exception e) {
            // エラーハンドリング
            logError("JSON インポート中にエラーが発生しました", e);
            sendErrorMessage(sender, "インポート中にエラーが発生しました。ログを確認してください。");
        }
        return true;
    }

    @Override
    public List<String> handleTabComplete(CommandSender sender, String[] args) {
        // 権限チェック
        if (!sender.hasPermission(PERMISSION)) {
            return Collections.emptyList();
        }
        
        // 1番目の引数：利用可能なサブコマンドを表示
        if (args.length == 1) {
            return List.of(IMPORT_COMMAND);
        }
        
        return Collections.emptyList();
    }
}