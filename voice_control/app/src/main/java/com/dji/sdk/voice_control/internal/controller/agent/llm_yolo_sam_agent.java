package com.dji.sdk.voice_control.internal.controller.agent;

import static com.google.android.gms.internal.zzahn.runOnUiThread;

import android.graphics.Bitmap;
import android.util.Log;
import android.view.TextureView;

import com.dji.sdk.voice_control.internal.controller.NetworkClient;
import com.dji.sdk.voice_control.internal.controller.UICallback;
import com.dji.sdk.voice_control.internal.controller.ControlActivity;
import com.dji.sdk.voice_control.internal.controller.chatgpt.Constant;
import com.dji.sdk.voice_control.internal.controller.flightcontrol.CommandInterpreter;
import com.dji.sdk.voice_control.internal.controller.flightcontrol.MyVirtualStickExecutor;
import com.dji.sdk.voice_control.internal.controller.track.yoloSamTrack;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import dji.sdk.flightcontroller.FlightController;

public class llm_yolo_sam_agent {

    private FlightController mFlightController;
    private CommandInterpreter mCI;
    private MyVirtualStickExecutor mSingletonVirtualStickExecutor;
    private TextureView mfpvTexture;
    private UICallback callback;
    private NetworkClient networkClient;


    //region agent 数据结构
    // 常量定义
    private static final int MAX_SEARCH_ATTEMPTS = 7;
    private static final int INITIAL_ANGLE = 5;
    private static final int ANGLE_INCREMENT_FACTOR = 2;
    private static final int SLEEP_BETWEEN_SEARCH_MS = 6000;
    private static final int COMMAND_UP_ANGLE = 5;
    private static final String IMAGE_FILE_NAME = "frame.jpg";
    // 共享变量
    private ExecutorService executorService = Executors.newSingleThreadExecutor();

    //提示词
    private String direction_prompt = "请分析图像，回答以下问题。首先，详细描述您的推理过程。然后，将您的答案以JSON格式输出。\n" +
            "\n" +
            "推理过程：\n" +
            "- 描述您如何判断图中是否有白色轿车,置信度水平如何。\n" +
            "- 解释您对白色轿车位置（左、中、右）的判断依据。\n" +
            "- 描述您如何估算白色轿车占据图像的比例。\n" +
            "\n" +
            "请在推理过程之后，输出JSON格式的答案：\n" +
            "\n" +
            "{\n" +
            "  \"has_white_car\": 布尔值（true或false），\n" +
            "  \"confidence_percentage\": 整数，范围0-100，表示您认为图中有白色轿车的把握，\n" +
            "  \"location_description\": \"字符串，'left'、'center'或'right'，描述白色轿车在图像中的位置\",\n" +
            "  \"estimated_proportion_percentage\": 整数，范围0-100，估计白色轿车占据图像的比例，\n" +
            "}\n" +
            "\n" +
            "**注意：**\n" +
            "- 请先输出推理过程，然后在下一行输出JSON对象。\n" +
            "- 不要在JSON对象之外添加额外的文本或注释。\n" +
            "- 请避免使用诸如“抱歉，我无法查看或分析图片内容”的句子，尽可能基于图像提供回答。\n" +
            "- 只需要判断目标在图像的左、右或者中间，不要回复类似左中(center-left)的回答。\n" +
            "- 请注意轿车通常具有完整白色轿车轮廓。";
    private String Gpt_result;
    //endregion


    //构造函数
    public llm_yolo_sam_agent(
            CommandInterpreter commandInterpreter,
            FlightController flightController,
            TextureView textureView,
            NetworkClient networkClient,
            UICallback callback
    ){
        this.mCI = commandInterpreter;
        this.mFlightController = flightController;
        this.mfpvTexture = textureView;
        this.networkClient = networkClient;
        this.callback = callback;
    }


    /**
     * 入口函数
     */
    public void agentFindCar() {
        if(mCI.mFlightController == null){
            callback.addChatMessage(Constant.OWNER_BOT, "飞控未初始化");
        }
        else{
            // 初始化虚拟摇杆执行器
            mSingletonVirtualStickExecutor = MyVirtualStickExecutor.getUniqueInstance();

//            // 创建起飞和上升命令
//            MyVirtualStickExecutor.DroneCommand takeoff = mSingletonVirtualStickExecutor.new TakeoffCommand();
//            MyVirtualStickExecutor.DroneCommand up = mSingletonVirtualStickExecutor.new UpCommand(COMMAND_UP_ANGLE);
//
//            // 将命令添加到队列
//            mSingletonVirtualStickExecutor.enqueueCommand(takeoff);
//            mSingletonVirtualStickExecutor.enqueueCommand(up);
//
//            // 执行命令队列并设置回调
//            mSingletonVirtualStickExecutor.executeCommandQueue(new MyVirtualStickExecutor.CommandCompletionCallback() {
//                @Override
//                public void onComplete(DJIError error) {
//                    if (error != null) {
//                        // 处理命令执行失败的情况
//                        callback.addChatMessage(Constant.OWNER_BOT, "起飞或上升失败: " + error.getDescription());
//                        Log.e("agentFindCar", "起飞或上升失败: " + error.getDescription());
//                        return;
//                    }
//
//                    // 执行第一次搜索
//                    performSearch(1, MAX_SEARCH_ATTEMPTS, COMMAND_UP_ANGLE);
//                }
//            });
            mCI.mTakeoff();
            // 睡 6 秒再搜下一次
            try {
                Thread.sleep(SLEEP_BETWEEN_SEARCH_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                callback.addChatMessage(Constant.OWNER_BOT, "线程被中断");
            }
            mSingletonVirtualStickExecutor.mUp(8);
            try {
                Thread.sleep(SLEEP_BETWEEN_SEARCH_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                callback.addChatMessage(Constant.OWNER_BOT, "线程被中断");
            }
            // 执行第一次搜索
            performSearch(1, MAX_SEARCH_ATTEMPTS, COMMAND_UP_ANGLE);

        }
    }

    /**
     * 在场景中自动搜索车辆
     * @return
     */
    public boolean doSearch() {
        final boolean[] isFind = {false};
        int angle = INITIAL_ANGLE;

        for (int i = 0; i < MAX_SEARCH_ATTEMPTS; i++) {
            if (isFind[0]) break;

            Bitmap bitmap = mfpvTexture.getBitmap();
            if (bitmap == null) {
                callback.addChatMessage(Constant.OWNER_BOT, "未能捕获视频帧，TextureView 未准备好");
                return false;
            }

            File imageFile = callback.saveBitmapAsFile(bitmap, IMAGE_FILE_NAME);
            if (imageFile == null) {
                callback.addChatMessage(Constant.OWNER_BOT, "图片保存失败");
                return false;
            }

            callback.addChatMessage(Constant.OWNER_BOT, "图像捕获成功，正在分析...");
            callback.addChatMessage(Constant.OWNER_HUMAN, bitmap);
            callback.addChatMessage(Constant.OWNER_BOT, "思考中...");

            int finalAngle = angle;
            String Result = callback.sendQuestionToGPTS(direction_prompt, imageFile,true);

            JsonUtils.ParseResult parseResult = JsonUtils.robustJsonParser(Result);
            String response = parseResult.getInferenceProcess();

            if (parseResult.getJsonData() == null) {
                callback.addChatMessage(Constant.OWNER_BOT, "模型返回为空，尝试下一帧...");
            } else {
                boolean hasWhiteCar = parseResult.getJsonData().optBoolean("has_white_car", false);
                int confidence = parseResult.getJsonData().optInt("confidence_percentage", 0);

                if (hasWhiteCar && confidence >= 80) {
                    response += "\n车辆已锁定!";
                    callback.addChatMessage(Constant.OWNER_BOT, response);

                    //TODO 在这里增加要跟踪车辆的标号是什么

                    detectID();

                    isFind[0] = true;
                } else {
                    response += "\n未能识别到目标车辆，继续搜索...";
                    callback.addChatMessage(Constant.OWNER_BOT, response);

                    // 转动视角
                    MyVirtualStickExecutor executor = MyVirtualStickExecutor.getUniqueInstance();
                    executor.mTurn(303, finalAngle);
                }
            }
            if (isFind[0]) {
                break;
            }

            angle *= ANGLE_INCREMENT_FACTOR;

            // 睡 6 秒再搜下一次
            try {
                Thread.sleep(SLEEP_BETWEEN_SEARCH_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                callback.addChatMessage(Constant.OWNER_BOT, "线程被中断");
                return false;
            }

        }

        return isFind[0];
    }

    /**
     * 场景描述，并推理出要跟踪车辆的标号是什么
     */
    public void detectID(){
        String id = "0";

        //TODO 大模型判断要跟踪的车辆的标号是什么

        yoloSamTrack yoloSamTrack = new yoloSamTrack(networkClient,mCI.mFlightController,mCI,callback);
        yoloSamTrack.initializeTrackerWithId(id);

        //TODO 结束标志 开始识别车牌

        recognizeCarBrand();
    }

    /**
     * 拍照并识别车标品牌。
     * 如果使用 GPT，会调用 sendQuestionToGPT()；否则调用 sendQuestionToAPI()。
     */
    public void recognizeCarBrand() {
        // 1. 拍照
        Bitmap bitmap = mfpvTexture.getBitmap();
        if (bitmap == null) {
            callback.addChatMessage(Constant.OWNER_BOT, "未能捕获视频帧，TextureView 可能未准备好");
            return;
        }

        File brandImgFile = callback.saveBitmapAsFile(bitmap, "frame.jpg");
        if (brandImgFile == null) {
            callback.addChatMessage(Constant.OWNER_BOT, "拍照失败，无法识别车标...");
            return;
        }

        // 2. 构造识别请求
        String brandPrompt = "请识别图片中白色轿车的车标品牌。请给出 JSON 输出，如 {\"brand_name\":\"Toyota\"}";
        callback.addChatMessage(Constant.OWNER_BOT, "正在识别车标，请稍候...");

        // 3. 调用 GPT 或 API
        callback.sendQuestion(true, brandPrompt, brandImgFile, new ControlActivity.OnGptResultListener() {
            @Override
            public void onSuccess(String gptResult) {
                runOnUiThread(() -> {
                    // 4. 解析响应结果
                    JsonUtils.ParseResult brandParse = JsonUtils.robustJsonParser(gptResult);
                    if (brandParse.getJsonData() == null) {
                        callback.addChatMessage(Constant.OWNER_BOT, "未能识别车标，JSON 数据为空。");
                        return;
                    }

                    String brandProcess = brandParse.getInferenceProcess();
                    String brandName = brandParse.getJsonData().optString("brand_name", "未知品牌");

                    callback.addChatMessage(Constant.OWNER_BOT, "车标识别推理过程: " + brandProcess);
                    callback.addChatMessage(Constant.OWNER_BOT, "识别到的品牌: " + brandName);
                });
            }

            @Override
            public void onFailure(Exception e) {
                runOnUiThread(() -> {
                    callback.addChatMessage(Constant.OWNER_BOT, "调用模型出错: " + e.getMessage());
                    Log.e("recognizeCarBrand", "调用模型出错：" + e.getMessage());
                });
            }
        });
    }

    /**
     * 执行搜索并根据结果决定是否上升和继续搜索
     *
     * @param currentAttempt 当前尝试次数
     * @param maxAttempts    最大尝试次数
     * @param ascendHeight   每次上升的高度
     */
    public void performSearch(int currentAttempt, int maxAttempts, int ascendHeight) {
        executorService.execute(() -> {
            boolean isFind = doSearch();
            if (isFind) {
                runOnUiThread(() -> callback.addChatMessage(Constant.OWNER_BOT, "车辆已锁定！"));
                mSingletonVirtualStickExecutor.mStop();
                return;
            }

            if (currentAttempt < maxAttempts) {
                runOnUiThread(() -> callback.addChatMessage(Constant.OWNER_BOT, "第 " + currentAttempt + " 次搜索未找到，开始上升 " + ascendHeight + " 米..."));
                mSingletonVirtualStickExecutor.mUp(5);
                // 睡 6 秒再搜下一次
                try {
                    Thread.sleep(SLEEP_BETWEEN_SEARCH_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    callback.addChatMessage(Constant.OWNER_BOT, "线程被中断");
                }
                performSearch(currentAttempt+1,maxAttempts,ascendHeight);
            } else {
                runOnUiThread(() -> callback.addChatMessage(Constant.OWNER_BOT, "多次搜索仍未找到车辆。请检查坐标或场景是否正确。"));
                mSingletonVirtualStickExecutor.mStop();
            }
        });
    }

}
