package com.dji.sdk.voice_control.internal.controller.voice_control;

import android.speech.tts.TextToSpeech;
import android.text.TextUtils;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.Toast;
import java.util.HashMap;
import java.util.Map;

//import com.dji.sdk.voice_control.internal.controller.gptchat.ChatApiClient;
import com.hankcs.hanlp.HanLP;
import com.hankcs.hanlp.seg.Segment;
import com.ibm.watson.developer_cloud.natural_language_classifier.v1.NaturalLanguageClassifier;

import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

//import cn.hutool.json.JSONException;
//import cn.hutool.json.JSONObject;

/**
 * Classifier and encoder
 */

public class CommandClassifier {
    private final String command_classfier_id = "90e7b7x198-nlc-43271";
    private final String direction_classfier_id = "90e7b4x199-nlc-18482";
    private final String object_detect_classfier_id = "90e7acx197-nlc-37646";
    private final List<String> object_list = Arrays.asList("aeroplane", "bicycle", "bird", "boat",
            "bottle", "bus", "car", "cat", "chair",
            "cow", "diningtable", "dog", "horse",
            "motorbike", "person", "pottedplant",
            "sheep", "sofa", "train", "tvmonitor");
    private NaturalLanguageClassifier nlpService;
    private String command_direction;

    private ArrayList<Integer> encoded_string;
    public String google_map_search_string = null;

    public CommandClassifier(){
        nlpService = new NaturalLanguageClassifier();
        String username = "892a7e25-f38a-4d04-a725-028871966429";
        String password = "1rFfpEEdA2k3";
        nlpService.setUsernameAndPassword(username, password);
        nlpService.setEndPoint("https://gateway.watsonplatform.net/natural-language-classifier/api");
        google_map_search_string = "";

//        // 初始化GPT客户端
//        chatApiClient = new ChatApiClient(this,
//                GlobalDataHolder.getGptApiHost(),
//                GlobalDataHolder.getGptApiKey(),
//                GlobalDataHolder.getGptModel(),
//                new ChatApiClient.OnReceiveListener() {
//                    private long lastRenderTime = 0;
//
//                    @Override
//                    public void onMsgReceive(String message) { // 收到GPT回复（增量）
//                        chatApiBuffer += message;
//                        handler.post(() -> {
//                            if(System.currentTimeMillis() - lastRenderTime > 100) { // 限制最高渲染频率10Hz
//                                boolean isBottom = svChatArea.getChildAt(0).getBottom()
//                                        <= svChatArea.getHeight() + svChatArea.getScrollY(); // 判断消息布局是否在底部
//
//                                markdownRenderer.render(tvGptReply, chatApiBuffer); // 渲染Markdown
//
//                                if(isBottom){
//                                    scrollChatAreaToBottom(); // 渲染前在底部则渲染后滚动到底部
//                                }
//                                lastRenderTime = System.currentTimeMillis();
//                            }
//
//                            if(currentTemplateParams.getBool("speak", ttsEnabled)) { // 处理TTS
//                                String wholeText = tvGptReply.getText().toString(); // 获取可朗读的文本
//                                if(ttsSentenceEndIndex < wholeText.length()) {
//                                    int nextSentenceEndIndex = wholeText.length();
//                                    boolean found = false;
//                                    for(String separator : ttsSentenceSeparator) { // 查找最后一个断句分隔符
//                                        int index = wholeText.indexOf(separator, ttsSentenceEndIndex);
//                                        if(index != -1 && index < nextSentenceEndIndex) {
//                                            nextSentenceEndIndex = index + separator.length();
//                                            found = true;
//                                        }
//                                    }
//                                    if(found) { // 找到断句分隔符则添加到朗读队列
//                                        String sentence = wholeText.substring(ttsSentenceEndIndex, nextSentenceEndIndex);
//                                        ttsSentenceEndIndex = nextSentenceEndIndex;
//                                        String id = UUID.randomUUID().toString();
//                                        tts.speak(sentence, TextToSpeech.QUEUE_ADD, null, id);
//                                        ttsLastId = id;
//                                    }
//                                }
//                            }
//                        });
//                    }
//
//                    @Override
//                    public void onFinished(boolean completed) { // GPT回复完成
//                        handler.post(() -> {
//                            String referenceStr = "\n\n" + getString(R.string.text_ref_web_prefix);
//                            int referenceCount = 0;
//                            if(completed) { // 如果是完整回复则添加参考网页
//                                int questionIndex = multiChatList.size() - 1;
//                                while(questionIndex >= 0 && multiChatList.get(questionIndex).role != ChatRole.USER) { // 找到上一个提问消息
//                                    questionIndex--;
//                                }
//                                for(int i = questionIndex + 1; i < multiChatList.size(); i++) { // 依次检查函数调用，并获取网页URL
//                                    if(multiChatList.get(i).role == ChatRole.FUNCTION
//                                            && multiChatList.get(i-1).role == ChatRole.ASSISTANT
//                                            && multiChatList.get(i-1).functionName != null) {
//                                        String funcName = multiChatList.get(i-1).functionName;
//                                        String funcArgs = multiChatList.get(i-1).contentText;
//                                        if(funcName.equals("get_html_text")) {
//                                            String url = new JSONObject(funcArgs).getStr("url");
//                                            referenceStr += String.format("[[%s]](%s) ", ++referenceCount, url);
//                                        }
//                                    }
//                                }
//                            }
//                            try {
//                                markdownRenderer.render(tvGptReply, chatApiBuffer); // 渲染Markdown
//                                String ttsText = tvGptReply.getText().toString();
//                                if(currentTemplateParams.getBool("speak", ttsEnabled) && ttsText.length() > ttsSentenceEndIndex) { // 如果TTS开启则朗读剩余文本
//                                    String id = UUID.randomUUID().toString();
//                                    tts.speak(ttsText.substring(ttsSentenceEndIndex), TextToSpeech.QUEUE_ADD, null, id);
//                                    ttsLastId = id;
//                                }
//                                if(referenceCount > 0)
//                                    chatApiBuffer += referenceStr; // 添加参考网页
//                                multiChatList.add(new ChatMessage(ChatRole.ASSISTANT).setText(chatApiBuffer)); // 保存回复内容到聊天数据列表
//                                ((LinearLayout) tvGptReply.getParent()).setTag(multiChatList.get(multiChatList.size() - 1)); // 绑定该聊天数据到布局
//                                markdownRenderer.render(tvGptReply, chatApiBuffer); // 再次渲染Markdown添加参考网页
//                                btSend.setImageResource(R.drawable.send_btn);
//                            } catch (Exception e) {
//                                e.printStackTrace();
//                            }
//                        });
//                    }
//
//                    @Override
//                    public void onError(String message) {
//                        handler.post(() -> {
//                            String errText = String.format(getString(R.string.text_gpt_error_prefix) + "%s", message);
//                            if(tvGptReply != null){
//                                tvGptReply.setText(errText);
//                            }else{
//                                Toast.makeText(MainActivity.this, errText, Toast.LENGTH_LONG).show();
//                            }
//                            btSend.setImageResource(R.drawable.send_btn);
//                        });
//                    }
//
//                    @Override
//                    public void onFunctionCall(String name, String arg) { // 收到函数调用请求
//                        Log.d("FunctionCall", String.format("%s: %s", name, arg));
//                        multiChatList.add(new ChatMessage(ChatRole.ASSISTANT).setFunction(name).setText(arg)); // 保存请求到聊天数据列表
//                        if (name.equals("get_html_text")) { // 调用联网函数
//                            try {
//                                JSONObject argJson = new JSONObject(arg);
//                                String url = argJson.getStr("url"); // 获取URL
//                                runOnUiThread(() -> {
//                                    markdownRenderer.render(tvGptReply, String.format(getString(R.string.text_visiting_web_prefix) + "[%s](%s)", URLDecoder.decode(url), url));
//                                    webScraper.load(url, new WebScraper.Callback() { // 抓取网页内容
//                                        @Override
//                                        public void onLoadResult(String result) {
//                                            postSendFunctionReply(name, result); // 返回网页内容给GPT
////                                            Log.d("FunctionCall", String.format("Response: %s", result));
//                                        }
//
//                                        @Override
//                                        public void onLoadFail(String message) {
//                                            postSendFunctionReply(name, "Failed to get response of this url.");
//                                        }
//                                    });
//                                    Log.d("FunctionCall", String.format("Loading url: %s", url));
//                                });
//                            } catch (JSONException e) {
//                                e.printStackTrace();
//                                postSendFunctionReply(name, "Error when getting response.");
//                            }
//                        } else if(name.equals("exit_voice_chat")){
//                            if(multiVoice)
//                                runOnUiThread(() -> findViewById(R.id.cv_voice_chat).performClick());
//                        } else {
//                            postSendFunctionReply(name, "Function not found.");
//                            Log.d("FunctionCall", String.format("Function not found: %s", name));
//                        }
//                    }
//                });
    }

    public ArrayList<Integer> classify(ArrayList<String> tokenedCommand,String language){
        String command = "stop";
        String direction = null;
        String unit = null;
        String map_search_string = null;
        String object_detect_class_string = null;
        ArrayList<Integer> result = null;

        String commandInText = TextUtils.join(" ", tokenedCommand).toLowerCase(); //连接字符串
//        if (tokenedCommand != null) {
//            ExecutorService executor = Executors.newCachedThreadPool();
////            Callable<String> task0 = new ExactProcessCallableService(tokenedCommand); //寻找数字字符串
////            Callable<String> task1 = new NLPCallableService(nlpService, command_classfier_id, commandInText); //一般命令分类，起飞，停止
////            Callable<String> task2 = new NLPCallableService(nlpService, direction_classfier_id, commandInText); //方向命令分类，向左，向右
////            Callable<String> task3 = new NLUCallableService(commandInText); //
////            Callable<String> task4 = new NLPCallableService(nlpService, object_detect_classfier_id, commandInText); //对象命令检测：汽车，树
//
//            // Tasks using ChatGPT API
//            Callable<String> task0 = () -> callChatGPTAPI("Extract numeric string", commandInText);
//            Callable<String> task1 = () -> callChatGPTAPI("Classify general command", commandInText);
//            Callable<String> task2 = () -> callChatGPTAPI("Classify direction", commandInText);
//            Callable<String> task3 = () -> callChatGPTAPI("Extract map search string", commandInText);
//            Callable<String> task4 = () -> callChatGPTAPI("Detect object", commandInText);
//
//            // Execute tasks
//            Future<String> future0 = executor.submit(task0);
//            Future<String> future1 = executor.submit(task1);
//            Future<String> future2 = executor.submit(task2);
//            Future<String> future3 = executor.submit(task3);
//            Future<String> future4 = executor.submit(task4);
//p
//            executor.shutdown();
//            //存储命令分类结果
//            try {
//                unit = future0.get();
//                command = future1.get();
//                direction = future2.get();
//                map_search_string = future3.get();
//                object_detect_class_string = future4.get();
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            } catch (ExecutionException e) {
//                e.printStackTrace();
//            }
//        }

        // parse into decimal encoded string 对命令进行编码展示
        // Split the input string based on commas

        if(language == "en_us"){
            // 使用关键词匹配提取英文命令
            if (commandInText.contains("takeoff")) {
                command = "takeoff";
            } else if (commandInText.contains("take off")) {
                command = "takeoff";
            } else if (commandInText.contains("landing")) {
                command = "landing";
            } else if (commandInText.contains("stop")) {
                command = "stop";
            } else if (commandInText.contains("flyto")) {
                command = "flyto";
            } else if (commandInText.contains("fly to")) {
                command = "flyto";
            } else if (commandInText.contains("move")) {
                command = "move";
                if (commandInText.contains("left")) {
                    direction = "left";
                } else if (commandInText.contains("right")) {
                    direction = "right";
                } else if (commandInText.contains("forward")) {
                    direction = "forward";
                } else if (commandInText.contains("backward")) {
                    direction = "backward";
                } else if (commandInText.contains("up")) {
                    direction = "up";
                } else if (commandInText.contains("down")) {
                    direction = "down";
                }
                // 提取单位
                unit = commandInText.replaceAll("[^\\d]", ""); // 提取数字作为单位
            } else if (commandInText.contains("turn")) {
                command = "turn";
                if (commandInText.contains("left")) {
                    direction = "left";
                } else if (commandInText.contains("right")) {
                    direction = "right";
                }
            } else if (commandInText.contains("photo")) {
                command = "photo";
                // 提取目标对象
                object_detect_class_string = commandInText.replaceFirst(".*photo", "").trim();
            } else if (commandInText.contains("location")) {
                command = "location";
            } else if (commandInText.contains("home")) {
                command = "home";
            } else if (commandInText.contains("height")) {
                command = "height";
            } else if (commandInText.contains("speed")) {
                command = "speed";
            }

            result = encode_string(command, direction, unit, object_detect_class_string,language);
            int switch_num = result.remove(result.size()-1);

            // set encoded_string 对命令进行编码
            switch (switch_num) {
                case 0:
                    this.command_direction = command;
                    break;
                case 1:
                case 2:
                    this.command_direction = command + ' ' + direction;
                    break;
                case 3:
                case 4:
                    this.command_direction = command + ' ' + direction + ' ' + unit;
                    break;
                case 5:
                    this.command_direction = "Advance Mission: " + this.google_map_search_string;
                    break;
                case 6:
                    this.command_direction = "Change Setting: " + command;
                    break;
                case 7:
                    this.command_direction = "Take photo: "+object_detect_class_string;
                    break;
                case 8:
                    this.command_direction = "fly to" + this.google_map_search_string;
                    break;
                default:
                    this.command_direction = "Unrecognize command in WatsonCommandClassifier";
                    break;
            }
            this.encoded_string = result;

            // show result TODO final comment out
            System.out.println(command + ' ' + direction);
            System.out.println(result.toString());
        }
        else {
            // 使用关键词匹配提取中文命令
            if (commandInText.contains("起飞")) {
                command = "起飞";
            } else if (commandInText.contains("降落")) {
                command = "降落";
            } else if (commandInText.contains("停止")) {
                command = "停止";
            } else if (commandInText.contains("飞到")) {
                command = "飞到";
                google_map_search_string = commandInText.replaceAll("飞到","");
            } else if (commandInText.contains("移动")) {
                command = "移动";
                if (commandInText.contains("左")) {
                    direction = "左";
                } else if (commandInText.contains("右")) {
                    direction = "右";
                } else if (commandInText.contains("前")) {
                    direction = "前";
                } else if (commandInText.contains("后")) {
                    direction = "后";
                } else if (commandInText.contains("上")) {
                    direction = "上";
                } else if (commandInText.contains("下")) {
                    direction = "下";
                }
                // 提取单位
                unit = commandInText.replaceAll("[^\\d一二三四五六七八九十零百千]", "");
                if (unit.matches(".*[一二三四五六七八九十零百千].*")) {
                    // 包含中文数字时进行转换
                    unit = convertChineseNumberToArabic(unit);
                }// 提取文本中的数字作为单位
            } else if (commandInText.contains("转")) {
                command = "转";
                if (commandInText.contains("左")) {
                    direction = "左";
                } else if (commandInText.contains("右")) {
                    direction = "右";
                }
                // 提取单位
                unit = commandInText.replaceAll("[^\\d一二三四五六七八九十零百千]", "");
                if (unit.matches(".*[一二三四五六七八九十零百千].*")) {
                    // 包含中文数字时进行转换
                    unit = convertChineseNumberToArabic(unit);
                }// 提取文本中的数字作为单位
            } else if (commandInText.contains("拍照")) {
                command = "拍照";
                // 提取目标对象
                object_detect_class_string = commandInText.replaceFirst(".*拍照", "").trim();
            }  else if (commandInText.contains("位置")) {
                command = "位置";
            } else if (commandInText.contains("返航")) {
                command = "返航";
            } else if (commandInText.contains("高度")) {
                command = "高度";
            } else if (commandInText.contains("速度")) {
                command = "速度";
            }

            result = encode_string(command, direction, unit, object_detect_class_string, language);
            int switch_num = result.remove(result.size() - 1);

            // set encoded_string 对命令进行编码
            switch (switch_num) {
                case 0:
                    this.command_direction = command;
                    break;
                case 1:
                case 2:
                    this.command_direction = command + ' ' + direction;
                    break;
                case 3:
                case 4:
                    this.command_direction = command + ' ' + direction + ' ' + unit;
                    break;
                case 5:
                    this.command_direction = "执行任务: " + this.google_map_search_string;
                    break;
                case 6:
                    this.command_direction = "设置更改: " + command;
                    break;
                case 7:
                    this.command_direction = "拍照: " + object_detect_class_string;
                    break;
                case 8:
                    this.command_direction = "飞到" + this.google_map_search_string;
                    break;
                default:
                    this.command_direction = "无法识别的命令";
                    break;
            }
            this.encoded_string = result;
            System.out.println(command + ' ' + direction);
            System.out.println(result.toString());
        }
        return result;
    }

    public String getCommand(){
        return this.command_direction;
    }

    // 中文数字转换为阿拉伯数字
    public static String convertChineseNumberToArabic(String chineseNumber) {
        HashMap<Character, Integer> numberMap = new HashMap<>();
        numberMap.put('零', 0);
        numberMap.put('一', 1);
        numberMap.put('二', 2);
        numberMap.put('三', 3);
        numberMap.put('四', 4);
        numberMap.put('五', 5);
        numberMap.put('六', 6);
        numberMap.put('七', 7);
        numberMap.put('八', 8);
        numberMap.put('九', 9);
        numberMap.put('十', 10);
        numberMap.put('百', 100);
        numberMap.put('千', 1000);

        int result = 0;
        int temp = 0;
        int base = 1; // 表示当前的位值

        for (int i = chineseNumber.length() - 1; i >= 0; i--) {
            char c = chineseNumber.charAt(i);

            if (numberMap.containsKey(c)) {
                int num = numberMap.get(c);
                if (num >= 10) { // 遇到“十、百、千”
                    if (temp == 0) temp = 1; // 特殊处理如"十"等情况
                    base = num;
                } else {
                    temp += num * base;
                    base = 1; // 重置base
                }
            }
        }
        result += temp; // 加上最后一个段的值
        return String.valueOf(result);
    }

//    private String callChatGPTAPI(String task, String input) {
//        // Simulate API call to ChatGPT (this should be replaced with real API implementation)
//        return "Simulated response for task: " + task + " with input: " + input;
//    }

    public ArrayList<Integer> getEncodedString(){
        return this.encoded_string;
    }

    public String getGoogleMapSearchString(){
        return this.google_map_search_string;
    }

    private ArrayList<Integer> encode_string (String command, String direction, String unit, String object_detect_class_string, String language) {
        ArrayList<Integer> encoded_string = new ArrayList<Integer>();
        int switch_num = 0; // 0 for null, 1 for move, 2 for turn, 3 for move unit, 4 for turn unit, 5 for advance mission, 6 for setting, 7 for image recong

        if (language.equals("en_us")) {
            // 英语部分保持不变
            switch (command) {
                case "takeoff":
                    encoded_string.add(100);
                    break;
                case "landing":
                    encoded_string.add(101);
                    break;
                case "stop":
                    encoded_string.add(102);
                    break;
                case "move":
                    switch_num = 1;
                    switch (direction) {
                        case "left":
                            encoded_string.add(103);
                            encoded_string.add(201);
                            encoded_string.add(303);
                            break;
                        case "right":
                            encoded_string.add(103);
                            encoded_string.add(201);
                            encoded_string.add(304);
                            break;
                        case "forward":
                            encoded_string.add(103);
                            encoded_string.add(201);
                            encoded_string.add(301);
                            break;
                        case "backward":
                            encoded_string.add(103);
                            encoded_string.add(201);
                            encoded_string.add(302);
                            break;
                        case "up":
                            encoded_string.add(105);
                            break;
                        case "down":
                            encoded_string.add(106);
                            break;
                        default:
                    }
                    break;
                case "turn":
                    encoded_string.add(104);
                    encoded_string.add(203);
                    switch_num = 2;
                    switch (direction) {
                        case "left":
                            encoded_string.add(303);
                            break;
                        case "right":
                            encoded_string.add(304);
                            break;
                        case "forward":
                            encoded_string.add(303);
                            encoded_string.add(204);
                            encoded_string.add(180);
                            break;
                        case "backward":
                            encoded_string.add(303);
                            encoded_string.add(204);
                            encoded_string.add(180);
                            break;
                        default:
                    }
                    break;
                case "location":
                    encoded_string.add(107);
                    encoded_string.add(205);
                    switch_num = 5;
                    break;
                case "home":
                    encoded_string.add(108);
                    encoded_string.add(206);
                    encoded_string.add(401);
                    encoded_string.add(207);
                    switch_num = 6;
                    break;
                case "height":
                    encoded_string.add(108);
                    encoded_string.add(206);
                    encoded_string.add(402);
                    encoded_string.add(207);
                    switch_num = 6;
                    break;
                case "speed":
                    encoded_string.add(108);
                    encoded_string.add(206);
                    encoded_string.add(403);
                    encoded_string.add(207);
                    switch_num = 6;
                    break;
                case "photo":
                    encoded_string.add(109);
                    int id = this.object_list.indexOf(object_detect_class_string) + 1;
                    encoded_string.add(id);
                    switch_num = 7;
                    break;
                case "flyto":
                    encoded_string.add(110);
                    switch_num = 8;
                    break;
                default:
                    // code to be executed if all cases are not matched;
            }
        } else if (language.equals("zh_cn")) {
            // 中文部分编码逻辑
            switch (command) {
                case "起飞":
                    encoded_string.add(100);
                    break;
                case "降落":
                    encoded_string.add(101);
                    break;
                case "停止":
                    encoded_string.add(102);
                    break;
                case "移动":
                    switch_num = 1;
                    switch (direction) {
                        case "左":
                            encoded_string.add(103);
                            encoded_string.add(201);
                            encoded_string.add(303);
                            break;
                        case "右":
                            encoded_string.add(103);
                            encoded_string.add(201);
                            encoded_string.add(304);
                            break;
                        case "前":
                            encoded_string.add(103);
                            encoded_string.add(201);
                            encoded_string.add(301);
                            break;
                        case "后":
                            encoded_string.add(103);
                            encoded_string.add(201);
                            encoded_string.add(302);
                            break;
                        case "上":
                            encoded_string.add(105);
                            break;
                        case "下":
                            encoded_string.add(106);
                            break;
                        default:
                    }
                    break;
                case "转":
                    encoded_string.add(104);
                    encoded_string.add(203);
                    switch_num = 2;
                    switch (direction) {
                        case "左":
                            encoded_string.add(303);
                            break;
                        case "右":
                            encoded_string.add(304);
                            break;
                        case "前":
                            encoded_string.add(303);
                            encoded_string.add(204);
                            encoded_string.add(180);
                            break;
                        case "后":
                            encoded_string.add(303);
                            encoded_string.add(204);
                            encoded_string.add(180);
                            break;
                        default:
                    }
                    break;
                case "位置":
                    encoded_string.add(107);
                    encoded_string.add(205);
                    switch_num = 5;
                    break;
                case "返航":
                    encoded_string.add(108);
                    encoded_string.add(206);
                    encoded_string.add(401);
                    encoded_string.add(207);
                    switch_num = 6;
                    break;
                case "高度":
                    encoded_string.add(108);
                    encoded_string.add(206);
                    encoded_string.add(402);
                    encoded_string.add(207);
                    switch_num = 6;
                    break;
                case "速度":
                    encoded_string.add(108);
                    encoded_string.add(206);
                    encoded_string.add(403);
                    encoded_string.add(207);
                    switch_num = 6;
                    break;
                case "拍照":
                    encoded_string.add(109);
                    int id = this.object_list.indexOf(object_detect_class_string) + 1;
                    encoded_string.add(id);
                    switch_num = 7;
                    break;
                case "飞到":
                    encoded_string.add(110);
                    switch_num = 8;
                    break;
                default:
                    // code to be executed if all cases are not matched;
            }
        }

        if (unit != null) {
            //move
            if (switch_num == 1) {
                encoded_string.add(202);
                switch_num = 3;
                encoded_string.add(Integer.parseInt(unit));
            }
            //turn
            else if (switch_num == 2) {
                encoded_string.add(204);
                switch_num = 4;
                encoded_string.add(Integer.parseInt(unit));
            }
            //settings
            else if (switch_num == 6) {
                encoded_string.add(Integer.parseInt(unit));
            }
        }

        encoded_string.add(switch_num);
        return encoded_string;
    }
}

