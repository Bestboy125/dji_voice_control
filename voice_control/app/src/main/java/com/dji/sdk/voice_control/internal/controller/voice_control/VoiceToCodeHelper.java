package com.dji.sdk.voice_control.internal.controller.voice_control;

/**
 * VoiceToCodeHelper
 *
 */

public class VoiceToCodeHelper {

    private int cmd[] = {103,201,301,202,10};


    VoiceToCodeHelper(String path, String fileName){

    }

    public int[] getCmdCode(){
        return cmd;
    }

    public void setCmdCode(String input){
        String temp[] = input.split(" ");
        cmd = new int[temp.length];
        for(int i=0;i<temp.length;++i){
            cmd[i] = Integer.valueOf(temp[i]);
        }
    }
}
