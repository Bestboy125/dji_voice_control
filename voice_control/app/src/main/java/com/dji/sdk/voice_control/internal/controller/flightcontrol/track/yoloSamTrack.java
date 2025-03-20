package com.dji.sdk.voice_control.internal.controller.flightcontrol.track;

import static com.dji.sdk.voice_control.internal.controller.utils.ImageUtil.imageToBase64;
import static com.dji.sdk.voice_control.internal.djidemo.utils.ToastUtils.showToast;
import static com.google.android.gms.internal.zzahn.runOnUiThread;

import android.annotation.SuppressLint;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.dji.sdk.voice_control.R;
import com.dji.sdk.voice_control.internal.controller.adapter.DetectedObjectsAdapter;
import com.dji.sdk.voice_control.internal.controller.interfaces.ControlActivityCallback;
import com.dji.sdk.voice_control.internal.controller.utils.ImageUtil;
import com.dji.sdk.voice_control.internal.controller.NetworkClient;
import com.dji.sdk.voice_control.internal.controller.chatgpt.Constant;
import com.dji.sdk.voice_control.internal.controller.flightcontrol.CommandInterpreter;
import com.dji.sdk.voice_control.internal.controller.flightcontrol.MyVirtualStickExecutor;
import com.dji.sdk.voice_control.internal.controller.utils.image_util;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import dji.sdk.flightcontroller.FlightController;

public class yoloSamTrack {


    //region YOLO SAM数据结构
    private List<DetectedObject> detectedObjectsList = new ArrayList<>();
    private List<DetectedObject> trackingFrameList = new ArrayList<>();
    private String chosenId = null;
    private DetectedObjectsAdapter adapter;
    private Bitmap annotatedBitmap;
    //endregion

    //region YOLO+SAM 目标跟踪
    // 公共变量
    private String command = ""; // 当前命令
    private String param = ""; // 当前参数
    private float mSpeed = 0;
    private float mYaw = 0;
    private float mPitch = 0;


    private boolean isend = true;


    private NetworkClient networkClient;
    private FlightController mFlightController;
    private CommandInterpreter mCI;
    private MyVirtualStickExecutor mSingletonVirtualStickExecutor;
    private ControlActivityCallback callback;

    private image_util imageUtil;


    public yoloSamTrack(
        NetworkClient networkClient,
        FlightController flightController,
        CommandInterpreter commandInterpreter,
        ControlActivityCallback callback
    ){
        this.networkClient = networkClient;
        this.mFlightController = flightController;
        this.mCI = commandInterpreter;
        this.callback = callback;
        imageUtil = new image_util();
    }


    /**
     * YOLO+SAM目标跟踪
     */
    @SuppressLint("DefaultLocale")
    public void handleObjectTracking(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                /**
                 * 测试用代码图片
                 */
                Resources res = callback.mgetResources();
                Bitmap bitmap = BitmapFactory.decodeResource(res, R.drawable.car);
                File frame1File = callback.saveBitmapAsFile(bitmap,"frame1.jpg");
                String frame1B64  = imageToBase64(frame1File.getAbsolutePath());
//                //转换图像格式
//                //第一步：获取当前帧图像
//                File FisrtImage = CaptureImage();
//                Bitmap bitmap = BitmapFactory.decodeFile(FisrtImage.getAbsolutePath());
//                String frame1B64  = imageToBase64(FisrtImage.getAbsolutePath());

                try {
                    //第二歩：上传服务器，获取YOLO检测结果
                    JsonObject detectResp = networkClient.sendDetectRequest(frame1B64);
                    JsonArray bboxes = detectResp.getAsJsonArray("bboxes");
                    String annotatedImageB64 = detectResp.get("annotated_image").getAsString();
                    annotatedBitmap = ImageUtil.decodeBase64Image(annotatedImageB64);
                    callback.addChatMessage(Constant.OWNER_HUMAN,annotatedBitmap);

                    // 清空之前的列表
                    detectedObjectsList.clear();
                    for (int i = 0; i < bboxes.size(); i++) {
                        JsonObject bbox = bboxes.get(i).getAsJsonObject();
                        String id = bbox.get("id").getAsString();

                        // 将 'box' 解析为 JsonArray
                        JsonArray boxArray = bbox.getAsJsonArray("box");

                        // 提取 box 信息
                        int x1 = boxArray.get(0).getAsInt();
                        int y1 = boxArray.get(1).getAsInt();
                        int x2 = boxArray.get(2).getAsInt();
                        int y2 = boxArray.get(3).getAsInt();

                        double conf = bbox.get("conf").getAsDouble();
                        String className = bbox.get("class_name").getAsString();

                        // 格式化 box 信息
                        String boxStr = String.format("[x1=%d, y1=%d, x2=%d, y2=%d]", x1, y1, x2, y2);

                        callback.addChatMessage(Constant.OWNER_BOT,String.format("ID=%s, box=%s, conf=%.3f, class=%s\n",
                                id, boxStr, conf, className));
                        // 裁剪图像
                        Bitmap croppedBitmap = imageUtil.cropBitmap(bitmap, x1, y1, x2, y2);

                        // 创建 DetectedObject 对象并添加到列表
                        DetectedObject obj = new DetectedObject(id, x1, y1, x2, y2, conf, className, croppedBitmap);
                        detectedObjectsList.add(obj);

                        // 在数据处理完成后显示消息框（UI更新）
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                callback.showSelectObjectDialog();  // 弹出对话框
                            }
                        });
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }).start();

    }

    /**
     * YOLO目标检测
     */
    @SuppressLint("DefaultLocale")
    public File handleObjectDetect(){
        final File[] yoloimage = {null};
        // Create a CountDownLatch to wait for thread completion
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    /**
                     * 测试用代码图片
                     */
                    Resources res = callback.mgetResources();
                    Bitmap bitmap = BitmapFactory.decodeResource(res, R.drawable.car);
                    File frame1File = callback.saveBitmapAsFile(bitmap,"frame1.jpg");
                    String frame1B64  = imageToBase64(frame1File.getAbsolutePath());
    //                //转换图像格式
    //                //第一步：获取当前帧图像
    //                File FisrtImage = CaptureImage();
    //                Bitmap bitmap = BitmapFactory.decodeFile(FisrtImage.getAbsolutePath());
    //                String frame1B64  = imageToBase64(FisrtImage.getAbsolutePath());

                    try {
                        //第二歩：上传服务器，获取YOLO检测结果
                        JsonObject detectResp = networkClient.sendDetectRequest(frame1B64);
                        JsonArray bboxes = detectResp.getAsJsonArray("bboxes");
                        String annotatedImageB64 = detectResp.get("annotated_image").getAsString();
                        annotatedBitmap = ImageUtil.decodeBase64Image(annotatedImageB64);
                        callback.addChatMessage(Constant.OWNER_HUMAN,annotatedBitmap);

                        yoloimage[0] = callback.saveBitmapAsFile(annotatedBitmap,"yolo.jpg");
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                } finally {
                    // Count down the latch to signal completion regardless of success or failure
                    latch.countDown();
                }
            }
        }).start();

        try {
            // Wait for the thread to complete
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread interrupted while waiting for YOLO detection", e);
        }
        
        return yoloimage[0];
    }

    /**
     * 初始化跟踪器
     */
    public void initializeTrackerWithId(String id){

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    JsonObject initResp = networkClient.sendInitTrackerRequest(Integer.parseInt(id));

                    if (initResp == null || !initResp.get("status").getAsString().equalsIgnoreCase("success")) {
                        runOnUiThread(() -> showToast("Init tracker response error: " + initResp));

                    }

                    runOnUiThread(() -> showToast("[CLIENT] Tracker initialized.\nServer returned: " + initResp.toString() + "\n"));
                    TrackFrame();
                } catch (NumberFormatException e) {

                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }).start();
    }

    private volatile boolean keepTracking = true;
    private int count_lost = 0;

    public void TrackFrame(){

        new Thread(new Runnable() {
            @SuppressLint("DefaultLocale")
            @Override
            public void run() {
                while(keepTracking){
                    long startTime = 0;
                    long endTime = 0;

                    /**
                     * 测试用代码图片
                     */
                    Bitmap croppedBitmap = null;
                    Resources res = callback.mgetResources();
                    Bitmap bitmap = BitmapFactory.decodeResource(res, R.drawable.car);
                    File frame1File = callback.saveBitmapAsFile(bitmap,"frame1.jpg");
                    String frame1B64  = imageToBase64(frame1File.getAbsolutePath());

//                    // 图片转换
//                    Bitmap croppedBitmap = null;
//                    File FisrtImage = CaptureImage();
//                    Bitmap bitmap = BitmapFactory.decodeFile(FisrtImage.getAbsolutePath());
//                    String frame1B64  = imageToBase64(FisrtImage.getAbsolutePath());

                    try {
                        startTime = System.currentTimeMillis(); // Record start time
                        // 上传服务器，获取逐帧动作
                        JsonObject trackResp = networkClient.sendTrackFrameRequest(frame1B64);
                        endTime = System.currentTimeMillis(); // Record end time
                        JsonArray bboxes = trackResp.getAsJsonArray("bboxes");
                        String status = trackResp.get("status").getAsString();
                        if(status.equals("failed")){
                            if(count_lost == 3){
                                mSingletonVirtualStickExecutor = MyVirtualStickExecutor.getUniqueInstance();
                                mSingletonVirtualStickExecutor.mStop();
                                count_lost = 0;
                            }
                            count_lost++;
                            continue;
                        } else {
                            count_lost = 0;
                        }

                        JsonObject bbox = bboxes.get(0).getAsJsonObject();
                        JsonArray boxArray = bbox.getAsJsonArray("bbox");

                        // 提取 box 信息
                        int x1 = boxArray.get(0).getAsInt();
                        int y1 = boxArray.get(1).getAsInt();
                        int w = boxArray.get(2).getAsInt();
                        int h = boxArray.get(3).getAsInt();

                        // 格式化 box 信息
                        String boxStr = String.format("[x1=%d, y1=%d, w=%d, h=%d]", x1, y1, w, h);
                        callback.addChatMessage(Constant.OWNER_BOT, String.format("box=%s\n", boxStr));

                        // 裁剪图像
                        croppedBitmap = imageUtil.cropBitmapwh(bitmap, x1, y1, w, h);
                        callback.addChatMessage(Constant.OWNER_HUMAN, croppedBitmap);

                        mYaw = trackResp.get("yaw").getAsFloat();
                        mPitch = trackResp.get("vx").getAsFloat();

                        callback.addChatMessage(Constant.OWNER_BOT, String.format("mYaw=%f, mSpeed=%f\n", mYaw, mPitch));

//                        //更新无人机状态
//                        updateDroneState(mYaw, mPitch);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    long totalTime = endTime - startTime; // Calculate total time spent for this iteration
                    callback.addChatMessage(Constant.OWNER_BOT, String.format("Total time for this frame: %d ms\n", totalTime));
                }
            }
        }).start();
    }


    /**
     * 更新无人机的命令和单位状态
     */
    public void updateDroneState(String command, String param) {
        int unit =  Integer.parseInt(param);
        mSingletonVirtualStickExecutor = MyVirtualStickExecutor.getUniqueInstance();
        switch (command) {
            case "hover":
                mSingletonVirtualStickExecutor.mStop();
                runOnUiThread(() -> callback.addChatMessage(Constant.OWNER_BOT,"未找到目标"));
                break;
            case "rotate":
                if(unit>0){
                    mSingletonVirtualStickExecutor.mTurn(303,unit);
                    runOnUiThread(() -> callback.addChatMessage(Constant.OWNER_BOT,"向左转"));
                }
                else{
                    mSingletonVirtualStickExecutor.mTurn(304,-1*unit);
                    runOnUiThread(() -> callback.addChatMessage(Constant.OWNER_BOT,"向右转"));
                }
                break;
            case "move_updown":
                if(unit>0){
                    mSingletonVirtualStickExecutor.mUp(unit);
                    runOnUiThread(() -> callback.addChatMessage(Constant.OWNER_BOT,"向上飞"));
                }
                else{
                    mSingletonVirtualStickExecutor.mDown(-1*unit);
                    runOnUiThread(() -> callback.addChatMessage(Constant.OWNER_BOT,"向下飞"));
                }
                break;
            case "move_forward":
                if(unit>0){
                    mSingletonVirtualStickExecutor.mGo(301,unit);
                    runOnUiThread(() -> callback.addChatMessage(Constant.OWNER_BOT,"向前移动"));
                }
                break;
            default:
                break;
        }

    }

    /**
     * 更新无人机角速度和加速度状态
     */
    public void updateDroneState(float mYaw, float mPitch) {
        mSingletonVirtualStickExecutor = MyVirtualStickExecutor.getUniqueInstance();
        float currHeading = mCI.mFlightController.getCompass().getHeading();
        mYaw += currHeading;
        mSingletonVirtualStickExecutor.mMove(mYaw,mPitch);
        runOnUiThread(() -> callback.addChatMessage(Constant.OWNER_BOT,"执行完毕"));
    }

    /**
     * 停止跟踪
     */
    public void stopTracking() {
        keepTracking = false;
        mSingletonVirtualStickExecutor = MyVirtualStickExecutor.getUniqueInstance();
        mSingletonVirtualStickExecutor.mStop();
    }

    public boolean getIsEnd(){
        return isend;
    }
    //endregion
}
