package com.dji.sdk.voice_control.internal.controller.agent;

import static com.google.android.gms.internal.zzahn.runOnUiThread;

import android.graphics.Bitmap;
import android.util.Log;
import android.view.TextureView;

import com.dji.sdk.voice_control.internal.controller.UICallback;
import com.dji.sdk.voice_control.internal.controller.ControlActivity;
import com.dji.sdk.voice_control.internal.controller.chatgpt.Constant;
import com.dji.sdk.voice_control.internal.controller.flightcontrol.CommandInterpreter;
import com.dji.sdk.voice_control.internal.controller.flightcontrol.MyVirtualStickExecutor;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import dji.sdk.flightcontroller.FlightController;

public class llm_agent {

    private FlightController mFlightController;
    private CommandInterpreter mCI;
    private MyVirtualStickExecutor mSingletonVirtualStickExecutor;
    private TextureView mfpvTexture;
    private UICallback callback;


    //region agent 数据结构
    private static final String AGENT_URL = "http://122.207.106.69:25130/chat";
    private static final String TEMPLATE="Please answer the following question: {question}";
    // 常量定义
    private static final int MAX_SEARCH_ATTEMPTS = 7;
    private static final int INITIAL_ANGLE = 5;
    private static final int ANGLE_INCREMENT_FACTOR = 2;
    private static final int SEARCH_TIMEOUT_SECONDS = 30;
    private static final int SLEEP_BETWEEN_SEARCH_MS = 6000;
    private static final int SLEEP_AFTER_CLOSE_MS = 6000;
    private static final int CLOSE_POSITION_PROPORTION_THRESHOLD = 60;
    private static final int COMMAND_UP_ANGLE = 5;
    private static final String IMAGE_FILE_NAME = "frame.jpg";
    // 共享变量
    private volatile boolean isCenterAndClose = false; // 是否满足在中心且占比>=70
    private ExecutorService executorService = Executors.newSingleThreadExecutor();
    // 移动距离配置
    private static final double MIN_MOVE_DISTANCE = 0.5; // 最小移动距离（米）
    private static final double MAX_MOVE_DISTANCE = 3.0; // 最大移动距离（米）
    private static final int PROPORTION_THRESHOLD = 60; // 占比阈值

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
    public llm_agent(
            CommandInterpreter commandInterpreter,
            FlightController flightController,
            TextureView textureView,
            UICallback callback
    ){
        this.mCI = commandInterpreter;
        this.mFlightController = flightController;
        this.mfpvTexture = textureView;
        this.callback = callback;
    }


    //region Agent控制

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
//            sendQuestion(isGPT, direction_prompt, imageFile, new OnGptResultListener() {
//                @Override
//                public void onSuccess(String gptResult) {
//                    try {
//                        JsonUtils.ParseResult parseResult = JsonUtils.robustJsonParser(gptResult);
//                        String response = parseResult.getInferenceProcess();
//
//                        if (parseResult.getJsonData() == null) {
//                            callback.addChatMessage(Constant.OWNER_BOT, "模型返回为空，尝试下一帧...");
//                        } else {
//                            boolean hasWhiteCar = parseResult.getJsonData().optBoolean("has_white_car", false);
//                            int confidence = parseResult.getJsonData().optInt("confidence_percentage", 0);
//
//                            if (hasWhiteCar && confidence >= 80) {
//                                response += "\n车辆已锁定!";
//                                callback.addChatMessage(Constant.OWNER_BOT, response);
//                                Close_to();
//                                isFind[0] = true;
//                            } else {
//                                response += "\n未能识别到目标车辆，继续搜索...";
//                                callback.addChatMessage(Constant.OWNER_BOT, response);
//
//                                // 转动视角
//                                MyVirtualStickExecutor executor = MyVirtualStickExecutor.getUniqueInstance();
//                                executor.mTurn(303, finalAngle);
//                            }
//                        }
//
//                    } catch (Exception e) {
//                        callback.addChatMessage(Constant.OWNER_BOT, "解析结果时出错: " + e.getMessage());
//                    } finally {
//                    }
//                }
//
//                @Override
//                public void onFailure(Exception e) {
//                    callback.addChatMessage(Constant.OWNER_BOT, "调用模型出错: " + e.getMessage());
//                }
//            });

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
                    Close_to();
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
     * 根据识别到的车辆信息，进行“靠近”操作。
     */
    public void Close_to() {
        performCloseToSearch(1, MAX_SEARCH_ATTEMPTS);
    }

    /**
     * 递归执行靠近搜索，直到满足条件或达到最大尝试次数
     */
    public void performCloseToSearch(int currentAttempt, int maxAttempts) {
        if( currentAttempt>maxAttempts ){
            recognizeCarBrand();
        }
        if (isCenterAndClose) {
            return;
        }

        Bitmap bitmap = mfpvTexture.getBitmap();
        if (bitmap == null) {
            callback.addChatMessage(Constant.OWNER_BOT, "未能捕获视频帧，TextureView 未准备好");
            return;
        }

        File imageFile = callback.saveBitmapAsFile(bitmap, IMAGE_FILE_NAME);
        if (imageFile == null) {
            callback.addChatMessage(Constant.OWNER_BOT, "图片保存失败");
            return;
        }

        callback.addChatMessage(Constant.OWNER_BOT, "图像捕获成功，正在分析...");
        callback.addChatMessage(Constant.OWNER_HUMAN, bitmap);
        callback.addChatMessage(Constant.OWNER_BOT, "思考中...");

        CountDownLatch latch = new CountDownLatch(1);

        callback.sendQuestion(true, direction_prompt, imageFile, new ControlActivity.OnGptResultListener() {
            @Override
            public void onSuccess(String gptResult) {
                try {
                    JsonUtils.ParseResult parseResult = JsonUtils.robustJsonParser(gptResult);
                    String response = parseResult.getInferenceProcess();

                    if (parseResult.getJsonData() == null) {
                        callback.addChatMessage(Constant.OWNER_BOT, "模型返回为空，尝试下一帧...");
                    } else {
                        String locationDesc = parseResult.getJsonData().optString("location_description", "center");
                        int confidence = parseResult.getJsonData().optInt("confidence_percentage", 0);
                        int proportion = parseResult.getJsonData().optInt("estimated_proportion_percentage", 0);

                        callback.addChatMessage(Constant.OWNER_BOT,
                                String.format("开始靠近车辆 —— 位置: %s, 置信度: %d%%, 占比: %d%%",
                                        locationDesc, confidence, proportion)
                        );

                        // 调整无人机位置
                        adjustDronePosition(locationDesc, proportion);

                        // 如果占比 >= 70，则尝试识别车标
                        if (proportion >= CLOSE_POSITION_PROPORTION_THRESHOLD) {
                            isCenterAndClose = true;
                            callback.addChatMessage(Constant.OWNER_BOT, "目标较大，可能已靠近车辆，准备识别车标...");
                            recognizeCarBrand();
                            callback.addChatMessage(Constant.OWNER_BOT, "Close_to 流程完成。");
                        }
                    }
                } catch (Exception e) {
                    callback.addChatMessage(Constant.OWNER_BOT, "解析结果时出错: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            }

            @Override
            public void onFailure(Exception e) {
                callback.addChatMessage(Constant.OWNER_BOT, "调用模型出错: " + e.getMessage());
                latch.countDown();
            }
        });

        // 等待回调完成，最多等待一定时间以防止无限阻塞
        try {
            boolean completed = latch.await(SEARCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!completed) {
                callback.addChatMessage(Constant.OWNER_BOT, "等待模型响应超时，尝试下一帧...");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            callback.addChatMessage(Constant.OWNER_BOT, "线程被中断");
        }

        if (!isCenterAndClose) {
            // 稍等一会儿，让无人机稳定
            try {
                Thread.sleep(SLEEP_AFTER_CLOSE_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                callback.addChatMessage(Constant.OWNER_BOT, "线程被中断");
            }

            // 递归调用，继续靠近搜索
            performCloseToSearch(currentAttempt+1,maxAttempts);
        }
    }

    /**
     * 调整无人机的位置或视角，使车辆更居中。
     * 当车辆已在中心 (center) 时，若占比 < 阈值，则向前移动一定距离。
     * @param locationDesc 车辆在画面中的位置描述 (left/right/center)
     * @param proportion   车辆在画面中的占比
     */
    public void adjustDronePosition(String locationDesc, int proportion) {
        mSingletonVirtualStickExecutor = MyVirtualStickExecutor.getUniqueInstance();

        switch (locationDesc) {
            case "left":
                callback.addChatMessage(Constant.OWNER_BOT, "车辆在图像左侧，向左移动...");
                double moveLeftDistance = calculateMoveDistance(proportion);
                mSingletonVirtualStickExecutor.mGo(302, moveLeftDistance);
                break;

            case "right":
                callback.addChatMessage(Constant.OWNER_BOT, "车辆在图像右侧，向右移动...");
                double moveRightDistance = calculateMoveDistance(proportion);
                mSingletonVirtualStickExecutor.mGo(303, moveRightDistance);
                break;

            case "center":
            default:
                // 如果车辆已经处于画面中央，但占比 < 阈值，说明还比较远，可以向前飞一定距离
                if (proportion < PROPORTION_THRESHOLD) {
                    double moveDistance = calculateMoveDistance(proportion);
                    callback.addChatMessage(Constant.OWNER_BOT,
                            String.format("车辆已大致位于中心，但占比为 %d%%，向前移动 %.2f 米靠近...", proportion, moveDistance));
                    mSingletonVirtualStickExecutor.mGo(301, moveDistance);
                } else {
                    isCenterAndClose=true;
                    callback.addChatMessage(Constant.OWNER_BOT, "车辆已居中且接近，不需要移动。");
                }
                break;
        }
    }

    /**
     * 根据车辆在图像中的占比计算移动距离。
     * @param proportion 车辆占比（0-100）
     * @return 需要移动的距离（米）
     */
    public double calculateMoveDistance(int proportion) {
        if (proportion < MAX_MOVE_DISTANCE) {
            double distanceRange = MAX_MOVE_DISTANCE - MIN_MOVE_DISTANCE;
            double proportionRatio = (double)(PROPORTION_THRESHOLD - proportion) / PROPORTION_THRESHOLD;
            double moveDistance = MIN_MOVE_DISTANCE + (distanceRange * proportionRatio);
            moveDistance = Math.max(MIN_MOVE_DISTANCE, Math.min(moveDistance, MAX_MOVE_DISTANCE));
            return moveDistance;
        } else {
            return 0.0;
        }
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

    //endregion


}
