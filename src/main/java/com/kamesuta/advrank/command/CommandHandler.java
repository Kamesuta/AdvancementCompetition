package com.kamesuta.advrank.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;

/**
 * コマンドハンドラーの統合管理クラス
 * 各コマンドを適切なハンドラーにルーティングする
 */
public class CommandHandler {
    // 各コマンドハンドラーのインスタンス
    private final AdvRankCommandHandler advRankHandler;
    private final AdvCommandHandler advHandler;
    private final AdvIdCommandHandler advIdHandler;
    private final AdvAdminCommandHandler advAdminHandler;

    public CommandHandler() {
        this.advRankHandler = new AdvRankCommandHandler();
        this.advHandler = new AdvCommandHandler();
        this.advIdHandler = new AdvIdCommandHandler();
        this.advAdminHandler = new AdvAdminCommandHandler();
    }

    public boolean handleAdvRankCommand(CommandSender sender, String[] args) {
        return advRankHandler.handleCommand(sender, args);
    }

    public boolean handleAdvCommand(CommandSender sender, String[] args) {
        return advHandler.handleCommand(sender, args);
    }

    public boolean handleAdvIdCommand(CommandSender sender, String[] args) {
        return advIdHandler.handleCommand(sender, args);
    }

    public boolean handleAdvAdminCommand(CommandSender sender, String[] args) {
        return advAdminHandler.handleCommand(sender, args);
    }

    /**
     * タブ補完を処理する
     * コマンド名に基づいて適切なハンドラーにルーティングする
     */
    public List<String> handleTabComplete(CommandSender sender, Command command, String[] args) {
        return switch (command.getName().toLowerCase()) {
            case "adv_rank" -> advRankHandler.handleTabComplete(sender, args);
            case "adv" -> advHandler.handleTabComplete(sender, args);
            case "adv_id" -> advIdHandler.handleTabComplete(sender, args);
            case "adv_admin" -> advAdminHandler.handleTabComplete(sender, args);
            default -> Collections.emptyList();
        };
    }
}