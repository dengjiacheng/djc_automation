package com.automation.infrastructure.keyboard;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.automation.application.keyboard.KeyboardCommandExecutor;
import com.automation.domain.keyboard.WindowTokenProvider;
import com.automation.domain.keyboard.command.KeyboardCommand;

/**
 * 将广播意图交给命令执行器处理，并处理结果回传。
 */
public class KeyboardBroadcastReceiver extends BroadcastReceiver {

    private static final String TAG = "KeyboardReceiver";

    private final KeyboardIntentParser intentParser;
    private final KeyboardCommandExecutor commandExecutor;
    private final WindowTokenProvider windowTokenProvider;

    public KeyboardBroadcastReceiver(KeyboardIntentParser intentParser,
                                     KeyboardCommandExecutor commandExecutor,
                                     WindowTokenProvider windowTokenProvider) {
        this.intentParser = intentParser;
        this.commandExecutor = commandExecutor;
        this.windowTokenProvider = windowTokenProvider;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            KeyboardCommand command = intentParser.parse(intent);
            String result = commandExecutor.execute(context, command, windowTokenProvider.provide());
            setResultCode(Activity.RESULT_OK);
            setResultData(result);
        } catch (Exception exception) {
            Log.e(TAG, "Failed to handle keyboard action", exception);
            setResultCode(Activity.RESULT_CANCELED);
            setResultData("error:" + exception.getMessage());
        }
    }
}
