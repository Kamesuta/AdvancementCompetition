package com.kamesuta.advrank.command;

import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * コマンドハンドラーの抽象基底クラス
 * 共通処理とエラーハンドリング機能を提供する
 */
public abstract class BaseCommandHandler {
    protected static final Logger logger = Logger.getLogger(BaseCommandHandler.class.getName());
    
    /**
     * コマンドを処理する
     * 
     * @param sender コマンドを実行したプレイヤーまたはコンソール
     * @param args コマンド引数
     * @return 処理が成功した場合true
     */
    public abstract boolean handleCommand(CommandSender sender, String[] args);
    
    /**
     * タブ補完候補を取得する
     * 
     * @param sender コマンドを実行しているプレイヤー
     * @param args 現在の引数
     * @return 補完候補のリスト
     */
    public abstract List<String> handleTabComplete(CommandSender sender, String[] args);
    
    protected void sendErrorMessage(CommandSender sender, String message) {
        sender.sendMessage("§c" + message);
    }

    protected void logError(String message, Exception e) {
        logger.log(Level.SEVERE, message, e);
    }
    
    /**
     * 引数の長さを検証する
     */
    protected boolean validateArgsLength(CommandSender sender, String[] args, int expectedLength) {
        if (args.length < expectedLength) {
            sendErrorMessage(sender, "引数が不足しています。");
            return false;
        }
        return true;
    }
    
    /**
     * 文字列が整数かどうかをチェックする
     */
    protected boolean isInteger(String str) {
        try {
            Integer.parseInt(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}