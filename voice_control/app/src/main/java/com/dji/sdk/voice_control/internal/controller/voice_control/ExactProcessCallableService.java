package com.dji.sdk.voice_control.internal.controller.voice_control;

/**
 * Supporting class of Voice recognition module
 */
import java.util.ArrayList;
import java.util.concurrent.Callable;

//查找第一个数字字符串。如果找到数字字符串，则返回该字符串作为结果。
public class ExactProcessCallableService implements Callable<String>{
    ArrayList<String> tokened_command;

    ExactProcessCallableService(ArrayList<String> tokened_command) {
        this.tokened_command = tokened_command;
    }

    public String call() throws Exception {
        String result=null;
        for (int i = 0; i < tokened_command.size(); i++) {
            if (isNumeric(tokened_command.get(i))) {
                result = tokened_command.get(i);
                break;
            }
        }
        return result;
    }

    private static boolean isNumeric(String s) {
        return s.matches("[-+]?\\d*\\.?\\d+");
    }

    private static int changeUnit(){
        return 0; //TODO
    }
}
