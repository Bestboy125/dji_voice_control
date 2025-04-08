package com.dji.sdk.voice_control.internal.controller.flightcontrol.agent;

import static com.google.android.gms.internal.zzahn.runOnUiThread;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.view.TextureView;

import com.dji.sdk.voice_control.internal.controller.NetworkClient;
import com.dji.sdk.voice_control.internal.controller.interfaces.ControlActivityCallback;
import com.dji.sdk.voice_control.internal.controller.ControlActivity;
import com.dji.sdk.voice_control.internal.controller.chatgpt.Constant;
import com.dji.sdk.voice_control.internal.controller.flightcontrol.CommandInterpreter;
import com.dji.sdk.voice_control.internal.controller.flightcontrol.MyVirtualStickExecutor;
import com.dji.sdk.voice_control.internal.controller.flightcontrol.track.yoloSamTrack;
import com.dji.sdk.voice_control.internal.controller.utils.JsonUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dji.sdk.flightcontroller.FlightController;

public class llm_yolo_sam_agent {

    private FlightController mFlightController;
    private CommandInterpreter mCI;
    private MyVirtualStickExecutor mSingletonVirtualStickExecutor;
    private TextureView mfpvTexture;
    private ControlActivityCallback callback;
    private NetworkClient networkClient;


    //region agent 数据结构
    // 常量定义
    private static final int MAX_SEARCH_ATTEMPTS = 7;
    private static final int INITIAL_ANGLE = 5;
    private static final int ANGLE_INCREMENT_FACTOR = 2;
    private static final int SLEEP_BETWEEN_SEARCH_MS = 3000;
    private static final int COMMAND_UP_ANGLE = 5;
    private static final String IMAGE_FILE_NAME = "frame.jpg";
    // 共享变量
    private ExecutorService executorService = Executors.newSingleThreadExecutor();

    //提示词
    private String direction_prompt = "请分析图像，回答以下问题。首先，详细描述您的推理过程。然后，将您的答案以JSON格式输出。\n" +
            "\n" +
            "推理过程：\n" +
            "- 描述您如何判断图中是否有黑色轿车,置信度水平如何。\n" +
            "- 解释您对黑色轿车位置（左、中、右）的判断依据。\n" +
            "- 描述您如何估算黑色轿车占据图像的比例。\n" +
            "\n" +
            "请在推理过程之后，输出JSON格式的答案：\n" +
            "\n" +
            "{\n" +
            "  \"has_white_car\": 布尔值（true或false），\n" +
            "  \"confidence_percentage\": 整数，范围0-100，表示您认为图中有黑色轿车的把握，\n" +
            "  \"location_description\": \"字符串，'left'、'center'或'right'，描述黑色轿车在图像中的位置\",\n" +
            "  \"estimated_proportion_percentage\": 整数，范围0-100，估计黑色轿车占据图像的比例，\n" +
            "}\n" +
            "\n" +
            "**注意：**\n" +
            "- 请先输出推理过程，然后在下一行输出JSON对象。\n" +
            "- 不要在JSON对象之外添加额外的文本或注释。\n" +
            "- 请避免使用诸如“抱歉，我无法查看或分析图片内容”的句子，尽可能基于图像提供回答。\n" +
            "- 只需要判断目标在图像的左、右或者中间，不要回复类似左中(center-left)的回答。\n" +
            "- 请注意轿车通常具有完整黑色轿车轮廓。";

    /**
     * 初始化跟踪器
     */
    private String detection_prompt = "请分析图像，回答以下问题。首先，详细描述您的推理过程。然后，将您的答案以JSON格式输出。\n" +
            "\n" +
            "推理过程：\n" +
            "- 描述您如何判断图中是否有黑色汽车,置信度水平如何。\n" +
            "- 解释您对ID号的判断依据。\n" +
            "\n" +
            "请在推理过程之后，输出JSON格式的答案：\n" +
            "\n" +
            "{\n" +
            "  \"has_car\": 布尔值（true或false），\n" +
            "  \"confidence_percentage\": 整数，范围0-100，表示您认为图中有黑色汽车的把握，\n" +
            "  \"ID\": 整数，描述黑色汽车对应ID号，图像中已给出\n" +
            "}\n" +
            "\n" +
            "**注意：**\n" +
            "- 请先输出推理过程，然后在下一行输出JSON对象。\n" +
            "- 不要在JSON对象之外添加额外的文本或注释。\n" +
            "- 请避免使用诸如抱歉，我无法查看或分析图片内容的句子，尽可能基于图像提供回答。\n" +
            "- 您作为具备影像分析能力的无人机，当前坐标参数为：以图像中心点为原点，右手系规则：X轴正东/Y轴正北/Z轴向上，初始朝向：正北（Y+方向）。";
    private String Gpt_result;



    //endregion


    //构造函数
    public llm_yolo_sam_agent(
            CommandInterpreter commandInterpreter,
            FlightController flightController,
            TextureView textureView,
            NetworkClient networkClient,
            ControlActivityCallback callback
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
            mCI.mTakeoff();
            // 睡 6 秒再搜下一次
            try {
                Thread.sleep(SLEEP_BETWEEN_SEARCH_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                callback.addChatMessage(Constant.OWNER_BOT, "线程被中断");
            }
            mSingletonVirtualStickExecutor.mUp(5);
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
     * 执行搜索并根据结果决定是否上升和继续搜索
     *
     * @param currentAttempt 当前尝试次数
     * @param maxAttempts    最大尝试次数
     * @param ascendHeight   每次上升的高度
     */
    public void performSearch(int currentAttempt, int maxAttempts, int ascendHeight) {
        executorService.execute(() -> {
            boolean isFind = false;
            try {
                isFind = doSearch();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
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

    /**
     * 在场景中自动搜索车辆
     * @return
     */
    public boolean doSearch() throws Exception {
        final boolean[] isFind = {false};
        int angle = INITIAL_ANGLE;

        for (int i = 0; i < MAX_SEARCH_ATTEMPTS; i++) {
            if (isFind[0]) break;

            File imageFile = CaptureImage();
            Bitmap bitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath());

            callback.addChatMessage(Constant.OWNER_BOT, "图像捕获成功，正在分析...");
            callback.addChatMessage(Constant.OWNER_HUMAN, bitmap);
            callback.addChatMessage(Constant.OWNER_BOT, "思考中...");

            int finalAngle = angle;
            String Result = callback.sendQuestionToGPTSync(direction_prompt, imageFile,true);

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
    public void detectID() throws Exception {
        String id = "0";

        yoloSamTrack yoloSamTrack = new yoloSamTrack(networkClient,mCI.mFlightController,mCI,callback);

        //场景描述
        File yoloimage = yoloSamTrack.handleObjectDetect();

        //利用大模型进行场景描述推理
        String Result = callback.sendQuestionToGPTSync(detection_prompt, yoloimage,true);
        JsonUtils.ParseResult parseResult = JsonUtils.robustJsonParser(Result);

        String ID = parseResult.getJsonData().optString("ID",null);
        yoloSamTrack.initializeTrackerWithId(ID);

        //TODO 结束标志 开始识别车牌
        while(yoloSamTrack.getIsEnd() == true){
            //TODO 转向车辆的前方
            llm_agent llmAgent = new llm_agent(mCI,mFlightController,mfpvTexture,callback);
            llmAgent.Close_to();
        }
    }

    /**
     * 保存为图像文件
     * @param bitmap
     * @param filename
     * @return
     */
    private File saveBitmapAsFile(Bitmap bitmap, String filename) {
        File file = new File(callback.mgetCacheDir(), filename); // 保存到应用的缓存目录
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out); // 压缩并保存为 JPEG
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        return file;
    }

    /**
     * 获取当前帧图像
     */
    public File CaptureImage(){

//        Resources res = callback.mgetResources();
//        Bitmap bitmap = BitmapFactory.decodeResource(res, R.drawable.car);
//        File imageFile = saveBitmapAsFile(bitmap,"frame1.jpg");

        Bitmap bitmap = mfpvTexture.getBitmap();
        if (bitmap == null) {
            callback.addChatMessage(Constant.OWNER_BOT, "未能捕获视频帧，TextureView 未准备好");
            return null;
        }

        File imageFile = saveBitmapAsFile(bitmap, IMAGE_FILE_NAME);
        if (imageFile == null) {
            callback.addChatMessage(Constant.OWNER_BOT, "图片保存失败");
            return null;
        }
        return imageFile;
    }

}
