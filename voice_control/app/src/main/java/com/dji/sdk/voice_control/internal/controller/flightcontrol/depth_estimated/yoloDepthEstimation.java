package com.dji.sdk.voice_control.internal.controller.flightcontrol.depth_estimated;

import static com.google.android.gms.internal.zzahn.runOnUiThread;

import com.dji.sdk.voice_control.internal.controller.ControlActivity;
import com.dji.sdk.voice_control.internal.controller.djitool.gimbal.gimbalControl;
import android.annotation.SuppressLint;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.dji.sdk.voice_control.R;
import com.dji.sdk.voice_control.internal.controller.adapter.DetectedObjectsAdapter;
import com.dji.sdk.voice_control.internal.controller.interfaces.ControlActivityCallback;
import com.dji.sdk.voice_control.internal.controller.NetworkClient;
import com.dji.sdk.voice_control.internal.controller.chatgpt.Constant;
import com.dji.sdk.voice_control.internal.controller.flightcontrol.CommandInterpreter;
import com.dji.sdk.voice_control.internal.controller.flightcontrol.MyVirtualStickExecutor;
import com.dji.sdk.voice_control.internal.controller.utils.image_util;
import com.dji.sdk.voice_control.internal.controller.flightcontrol.track.DetectedObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import dji.sdk.flightcontroller.FlightController;

//TODO:1. 
public class yoloDepthEstimation {

    //region 深度估计相关数据结构
    private static class CameraPose {
        public float x0;
        public float yo;
        public float f;
        public double yaw;
        public double pitch;
        public double roll;
        public double altitude;
        public double latitude;
        public double longitude;
        public File imageFile;
        
        public CameraPose(float x0, float yo, float f, double yaw, double pitch, double roll, float altitude, double latitude, double longitude, File imageFile) {
            this.x0 = x0;
            this.yo = yo;
            this.f = f;
            this.yaw = yaw;
            this.pitch = pitch;
            this.roll = roll;
            this.altitude = altitude;
            this.latitude = latitude;
            this.longitude = longitude;
            this.imageFile = imageFile;
        }
    }
    
    private List<CameraPose> cameraPoses = new ArrayList<>();
    //endregion

    //region 深度估计相关字段
    private List<DetectedObject> detectedObjectsList = new ArrayList<>();
    private DetectedObjectsAdapter adapter;
    private Bitmap annotatedBitmap;
    private String selectedObjectLabel = null;
    //endregion

    //region 深度估计


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

    private gimbalControl gimbalControl;

    public yoloDepthEstimation(
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
     * 检查服务器状态
     */
    public void checkServerStatus() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    JsonObject serviceInfo = networkClient.getServiceInfo();
                    JsonObject healthStatus = networkClient.getHealthStatus();
                    
                    runOnUiThread(() -> {
                        callback.addChatMessage(Constant.OWNER_BOT, "服务器信息: " + serviceInfo.toString());
                        callback.addChatMessage(Constant.OWNER_BOT, "健康状态: " + healthStatus.toString());
                    });
                } catch (IOException e) {
                    runOnUiThread(() -> {
                        callback.addChatMessage(Constant.OWNER_BOT, "服务器连接失败: " + e.getMessage());
                    });
                }
            }
        }).start();
    }

    /**
     * 使用新的目标检测接口进行目标检测并显示选择对话框
     */
    @SuppressLint("DefaultLocale")
    public void handleObjectDetectionWithNewAPI(String classType){
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    /**
                     * 测试用代码图片
                     */
                    Resources res = callback.mgetResources();
                    Bitmap bitmap = BitmapFactory.decodeResource(res, R.drawable.depth1);
                    File frame1File = callback.saveBitmapAsFile(bitmap,"frame1.jpg");

//                    // // 真实图片
//                    final File[] FisrtImage = {null};
//                    callback.CaptureDjiImage(new ControlActivity.CaptureImageCallback() {
//                        @Override
//                        public void onSuccess(File imageFile) {
//                            // TODO Auto-generated method stub
//                            FisrtImage[0] = imageFile;
//                        }
//
//                        @Override
//                        public void onFailure(String error) {
//                            // TODO Auto-generated method stub
//                            throw new UnsupportedOperationException("Unimplemented method 'onFailure'");
//                        }
//                    });
//                     Bitmap bitmap = BitmapFactory.decodeFile(FisrtImage[0].getAbsolutePath());
//                     File frame1File = callback.saveBitmapAsFile(bitmap,"frame1.jpg");
                    
                    // 使用新的目标检测接口
                    JsonObject detectResp = networkClient.detectTarget(frame1File, classType);
                    
                    // 解析响应
                    JsonArray bbox2d = detectResp.getAsJsonArray("bbox_2d");
                    String label = detectResp.has("label") && !detectResp.get("label").isJsonNull() ? 
                                  detectResp.get("label").getAsString() : "unknown";
                    String subLabel = detectResp.has("sub_label") && !detectResp.get("sub_label").isJsonNull() ? 
                                     detectResp.get("sub_label").getAsString() : null;
                    
                    if (bbox2d != null && bbox2d.size() >= 4) {
                        int x1 = bbox2d.get(0).getAsInt();
                        int y1 = bbox2d.get(1).getAsInt();
                        int x2 = bbox2d.get(2).getAsInt();
                        int y2 = bbox2d.get(3).getAsInt();
                        
                        // 清空之前的列表
                        detectedObjectsList.clear();
                        
                        // 格式化 box 信息
                        String boxStr = String.format("[x1=%d, y1=%d, x2=%d, y2=%d]", x1, y1, x2, y2);
                        String displayMessage = String.format("检测到目标: %s\n边界框: %s\n标签: %s", 
                                classType, boxStr, label);
                        if (subLabel != null) {
                            displayMessage += "\n子标签: " + subLabel;
                        }

                        String finalDisplayMessage = displayMessage;
                        runOnUiThread(() -> {
                            callback.addChatMessage(Constant.OWNER_BOT, finalDisplayMessage);
                        });
                        
                        // 裁剪图像
                        Bitmap croppedBitmap = imageUtil.cropBitmap(bitmap, x1, y1, x2, y2);
                        
                        // 创建标注图像 (简单的在原图上绘制边界框)
                        annotatedBitmap = createAnnotatedBitmap(bitmap, x1, y1, x2, y2, label);
                        
                        // 创建 DetectedObject 对象并添加到列表
                        // 使用label作为id，因为新API没有返回id字段
                        DetectedObject obj = new DetectedObject(label, x1, y1, x2-x1, y2-y1, 1.0, label, croppedBitmap);
                        detectedObjectsList.add(obj);
                        
                        // 显示检测到的目标图像
                        runOnUiThread(() -> {
                            callback.addChatMessage(Constant.OWNER_HUMAN, croppedBitmap);
                        });
                        
                        // 弹出选择对话框
                        runOnUiThread(() -> {
                            callback.showSelectObjectDialogForDepthEstimation();
                        });
                    } else {
                        runOnUiThread(() -> {
                            callback.addChatMessage(Constant.OWNER_BOT, "未检测到目标: " + classType);
                        });
                    }
                    
                } catch (IOException e) {
                    runOnUiThread(() -> {
                        callback.addChatMessage(Constant.OWNER_BOT, "目标检测失败: " + e.getMessage());
                    });
                }
            }
        }).start();
    }

    /**
     * 创建带有边界框标注的图像
     */
    private Bitmap createAnnotatedBitmap(Bitmap originalBitmap, int x1, int y1, int x2, int y2, String label) {
        // 创建一个可变的bitmap副本
        Bitmap annotated = originalBitmap.copy(Bitmap.Config.ARGB_8888, true);
        
        // 简单处理：这里可以使用Canvas绘制边界框，但为了简化，我们直接返回原图
        // 在实际应用中，应该使用Canvas在图像上绘制边界框和标签
        return annotated;
    }

    /**
     * 获取检测到的对象列表
     */
    public List<DetectedObject> getDetectedObjectsList() {
        return detectedObjectsList;
    }

    /**
     * 获取标注后的图像
     */
    public Bitmap getAnnotatedBitmap() {
        return annotatedBitmap;
    }

    /**
     * 设置选择的对象标签并开始深度估计
     */
    public void setSelectedObjectAndStartDepthEstimation(String objectLabel) {
        this.selectedObjectLabel = objectLabel;
        handleDepthEstimation(objectLabel);
    }

    /**
     * 深度估计 - 多姿态图片采集和深度估计
     */
    public void handleDepthEstimation(String classType) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    runOnUiThread(() -> {
                        callback.addChatMessage(Constant.OWNER_BOT, "开始深度估计流程，目标: " + classType);
                    });
                    
                    // 清空之前的姿态数据
                    cameraPoses.clear();
//                    mSingletonVirtualStickExecutor = MyVirtualStickExecutor.getUniqueInstance();
                    
                    // 1. 获取当前姿态和图像
                    CameraPose currentPose = captureCurrentPose("current");
                    cameraPoses.add(currentPose);
                    
                    runOnUiThread(() -> {
                        callback.addChatMessage(Constant.OWNER_BOT, "已获取当前姿态图像");
                    });
                    
                    // 2. 向左旋转10°并获取图像
                    rotateAndCapture(-10.0f, "left_10");
                    
                    runOnUiThread(() -> {
                        callback.addChatMessage(Constant.OWNER_BOT, "已获取左转10°图像");
                    });
                    
                    // 3. 向右旋转20°并获取图像（相对于原始位置右转10°）
                    rotateAndCapture(20.0f, "right_20");
                    
                    runOnUiThread(() -> {
                        callback.addChatMessage(Constant.OWNER_BOT, "已获取右转20°图像");
                    });
                    
//                    // 4. 回复原状
//                    rotateToOriginalPosition();
                    
                    runOnUiThread(() -> {
                        callback.addChatMessage(Constant.OWNER_BOT, "已回复原始姿态");
                    });
                    
                    // 5. 开始深度估计
                    if (cameraPoses.size() == 3) {
                        performDepthEstimation(classType);
                    } else {
                        runOnUiThread(() -> {
                            callback.addChatMessage(Constant.OWNER_BOT, "图像采集不完整，无法进行深度估计");
                        });
                    }
                    
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        callback.addChatMessage(Constant.OWNER_BOT, "深度估计流程失败: " + e.getMessage());
                    });
                }
            }
        }).start();
    }

    /**
     * 获取当前姿态和图像
     */
    private CameraPose captureCurrentPose(String suffix) {
        // 获取当前姿态信息，如果 flightController 为 null，使用默认值
        float altitude = 0.0f;
        double latitude = 0.0;
        double longitude = 0.0;
        float x0 = 0.0f;
        float y0 = 0.0f;
        float f = 0.0f;
        float fi = 0.0f;
        float omg = 0.0f;
        float kappa = 0.0f;

        double droneYaw = 0.0;
        double dronePitch = 0.0;
        double droneRoll = 0.0;
        double gimbalYaw = 0.0;
        double gimbalPitch = 0.0;
        double gimbalRoll = 0.0;

//        gimbalControl = new gimbalControl();
        
        if (mFlightController != null) {
            try {
                if (mFlightController.getState() != null && mFlightController.getState().getAircraftLocation() != null) {
                    altitude = mFlightController.getState().getAircraftLocation().getAltitude();
                    latitude = mFlightController.getState().getAircraftLocation().getLatitude();
                    longitude = mFlightController.getState().getAircraftLocation().getLongitude();

                    droneYaw = mFlightController.getState().getAttitude().yaw;
                    dronePitch = mFlightController.getState().getAttitude().pitch;
                    droneRoll = mFlightController.getState().getAttitude().roll;

                    
                }
                if(gimbalControl!= null){
                    gimbalYaw = gimbalControl.getyaw();
                    gimbalPitch = gimbalControl.getPitch();
                    gimbalRoll = gimbalControl.getRoll();
                }
            } catch (Exception e) {
                // 如果获取姿态信息失败，使用默认值
                runOnUiThread(() -> {
                    callback.addChatMessage(Constant.OWNER_BOT, "获取姿态信息失败，使用默认值: " + e.getMessage());
                });
            }
        } else {
            runOnUiThread(() -> {
                callback.addChatMessage(Constant.OWNER_BOT, "FlightController 为 null，使用默认姿态值");
            });
        }

        //
        File imageFile = null;
        
        // 获取图像（这里使用测试图像，实际应用中需要从相机获取）
        if(suffix == "current"){
            Resources res = callback.mgetResources();
            Bitmap bitmap = BitmapFactory.decodeResource(res, R.drawable.depth1);
            imageFile = callback.saveBitmapAsFile(bitmap, "depth_" + suffix + ".jpg");
        } else if (suffix == "left_10") {
            Resources res = callback.mgetResources();
            Bitmap bitmap = BitmapFactory.decodeResource(res, R.drawable.depth2);
            imageFile = callback.saveBitmapAsFile(bitmap, "depth_" + suffix + ".jpg");
        } else {
            Resources res = callback.mgetResources();
            Bitmap bitmap = BitmapFactory.decodeResource(res, R.drawable.depth3);
            imageFile = callback.saveBitmapAsFile(bitmap, "depth_" + suffix + ".jpg");
        }

//        // // 真实图片
//        final File[] FisrtImage = {null};
//        callback.CaptureDjiImage(new ControlActivity.CaptureImageCallback() {
//            @Override
//            public void onSuccess(File imageFile) {
//                // TODO Auto-generated method stub
//                FisrtImage[0] = imageFile;
//            }
//
//            @Override
//            public void onFailure(String error) {
//                // TODO Auto-generated method stub
//                throw new UnsupportedOperationException("Unimplemented method 'onFailure'");
//            }
//        });
//        Bitmap bitmap = BitmapFactory.decodeFile(FisrtImage[0].getAbsolutePath());
//        imageFile = callback.saveBitmapAsFile(bitmap,"frame1.jpg");
        
        return new CameraPose(x0, y0, f, droneYaw, dronePitch, droneRoll, altitude, latitude, longitude, imageFile);
    }

    /**
     * 旋转并获取图像
     */
    private void rotateAndCapture(float degrees, String suffix) {
        try {
//            // 执行旋转
//            if (degrees > 0) {
//                mSingletonVirtualStickExecutor.mTurn(304, (int)degrees); // 右转
//            } else {
//                mSingletonVirtualStickExecutor.mTurn(303, (int)Math.abs(degrees)); // 左转
//            }
//
            // 等待旋转完成
            Thread.sleep(3000);
            
            // 获取当前姿态和图像
            CameraPose pose = captureCurrentPose(suffix);
            cameraPoses.add(pose);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 回复原始姿态
     */
    private void rotateToOriginalPosition() {
        try {
            // 回到原始位置（左转10°）
            mSingletonVirtualStickExecutor.mTurn(303, 10);
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 执行深度估计
     */
    private void performDepthEstimation(String classType) {
        try {
            // 构建相机参数
            String cameraParams1 = buildCameraParameters(cameraPoses.get(0));
            String cameraParams2 = buildCameraParameters(cameraPoses.get(1));
            String cameraParams3 = buildCameraParameters(cameraPoses.get(2));
            
            runOnUiThread(() -> {
                callback.addChatMessage(Constant.OWNER_BOT, "开始深度估计计算...");
            });
            
            // 调用深度估计接口
            JsonObject depthResult = networkClient.estimateDepth(
                cameraPoses.get(0).imageFile,
                cameraPoses.get(1).imageFile,
                cameraPoses.get(2).imageFile,
                cameraParams1,
                cameraParams2,
                cameraParams3,
                classType,
                0, // 使用第一张图像进行检测
                0.0f, // coarse_depth_min
                8000.0f, // coarse_depth_max
                100, // coarse_samples
                200, // fine_samples
                15.0f // tolerance
            );
            
            // 解析深度估计结果
            parseDepthEstimationResult(depthResult);
            
        } catch (IOException e) {
            runOnUiThread(() -> {
                callback.addChatMessage(Constant.OWNER_BOT, "深度估计失败: " + e.getMessage());
            });
        }
    }

    /**
     * 将大地坐标转换为空间直角坐标
     * @param lon 经度（度）
     * @param lat 纬度（度）
     * @param alt 高度（米）
     * @return double数组，包含x, y, z坐标
     */
    private double[] blhtoxyz(double lon, double lat, double alt) {
        // 将度转换为弧度
        double B = Math.toRadians(lat);
        double L = Math.toRadians(lon);
        double H = alt;
        
        // WGS84椭球参数
        double a = 6378137.0;          // 长半轴
        double b = 6356752.31424517;   // 短半轴
        double e2 = (a * a - b * b) / (a * a);  // e的平方
        
        // 计算卯酉圈半径
        double N = a / Math.sqrt(1 - e2 * Math.sin(B) * Math.sin(B));
        
        // 计算空间直角坐标
        double x = (N + H) * Math.cos(B) * Math.cos(L);
        double y = (N + H) * Math.cos(B) * Math.sin(L);
        double z = (N * (1 - e2) + H) * Math.sin(B);
        
        return new double[]{x, y, z};
    }

    /**
     * 四元数数据结构
     */
    private static class Quaternion {
        public double w;  // 标量部分
        public double x;  // 向量部分i
        public double y;  // 向量部分j
        public double z;  // 向量部分k
        
        public Quaternion(double w, double x, double y, double z) {
            this.w = w;
            this.x = x;
            this.y = y;
            this.z = z;
        }
        
        /**
         * 转换为数组形式 [w, x, y, z]
         */
        public double[] toArray() {
            return new double[]{w, x, y, z};
        }
        
        /**
         * 归一化四元数
         */
        public void normalize() {
            double norm = Math.sqrt(w * w + x * x + y * y + z * z);
            if (norm > 0) {
                w /= norm;
                x /= norm;
                y /= norm;
                z /= norm;
            }
        }
        
        @Override
        public String toString() {
            return String.format("Quaternion(w=%.6f, x=%.6f, y=%.6f, z=%.6f)", w, x, y, z);
        }
    }

    /**
     * 将欧拉角转换为四元数
     * @param yaw 偏航角（度）- 绕Z轴旋转，航向角
     * @param pitch 俯仰角（度）- 绕Y轴旋转
     * @param roll 横滚角（度）- 绕X轴旋转
     * @return 四元数数组 [w, x, y, z]
     */
    private double[] eulerToQuaternion(double yaw, double pitch, double roll) {
        // 将度转换为弧度
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        double rollRad = Math.toRadians(roll);
        
        // 计算半角的三角函数值
        double cy = Math.cos(yawRad * 0.5);
        double sy = Math.sin(yawRad * 0.5);
        double cp = Math.cos(pitchRad * 0.5);
        double sp = Math.sin(pitchRad * 0.5);
        double cr = Math.cos(rollRad * 0.5);
        double sr = Math.sin(rollRad * 0.5);
        
        // 计算四元数分量
        // 使用 ZYX 旋转顺序（先绕Z轴，再绕Y轴，最后绕X轴）
        double w = cr * cp * cy + sr * sp * sy;
        double x = sr * cp * cy - cr * sp * sy;
        double y = cr * sp * cy + sr * cp * sy;
        double z = cr * cp * sy - sr * sp * cy;
        
        // 创建四元数对象并归一化
        Quaternion quaternion = new Quaternion(w, x, y, z);
        quaternion.normalize();
        
        return quaternion.toArray();
    }

    /**
     * 将四元数转换为欧拉角
     * @param quaternion 四元数数组 [w, x, y, z]
     * @return 欧拉角数组 [yaw, pitch, roll]（度）
     */
    private double[] quaternionToEuler(double[] quaternion) {
        double w = quaternion[0];
        double x = quaternion[1];
        double y = quaternion[2];
        double z = quaternion[3];
        
        // 计算欧拉角
        double sinr_cosp = 2 * (w * x + y * z);
        double cosr_cosp = 1 - 2 * (x * x + y * y);
        double roll = Math.atan2(sinr_cosp, cosr_cosp);
        
        double sinp = 2 * (w * y - z * x);
        double pitch;
        if (Math.abs(sinp) >= 1) {
            pitch = Math.copySign(Math.PI / 2, sinp); // 使用90度，如果超出范围
        } else {
            pitch = Math.asin(sinp);
        }
        
        double siny_cosp = 2 * (w * z + x * y);
        double cosy_cosp = 1 - 2 * (y * y + z * z);
        double yaw = Math.atan2(siny_cosp, cosy_cosp);
        
        // 转换为度
        return new double[]{
            Math.toDegrees(yaw),
            Math.toDegrees(pitch),
            Math.toDegrees(roll)
        };
    }

    /**
     * 测试四元数转换功能
     */
    public void testQuaternionConversion() {
        // 测试用例：yaw=45°, pitch=30°, roll=60°
        double testYaw = 45.0;
        double testPitch = 30.0;
        double testRoll = 60.0;
        
        // 转换为四元数
        double[] quaternion = eulerToQuaternion(testYaw, testPitch, testRoll);
        
        // 再转换回欧拉角
        double[] euler = quaternionToEuler(quaternion);
        
        runOnUiThread(() -> {
            String testResult = String.format(
                "四元数转换测试:\n" +
                "输入欧拉角: yaw=%.2f°, pitch=%.2f°, roll=%.2f°\n" +
                "四元数: [w=%.6f, x=%.6f, y=%.6f, z=%.6f]\n" +
                "转换回欧拉角: yaw=%.2f°, pitch=%.2f°, roll=%.2f°\n" +
                "误差: yaw=%.6f°, pitch=%.6f°, roll=%.6f°",
                testYaw, testPitch, testRoll,
                quaternion[0], quaternion[1], quaternion[2], quaternion[3],
                euler[0], euler[1], euler[2],
                Math.abs(testYaw - euler[0]),
                Math.abs(testPitch - euler[1]),
                Math.abs(testRoll - euler[2])
            );
            callback.addChatMessage(Constant.OWNER_BOT, testResult);
        });
    }

    /**
     * 构建相机参数JSON字符串
     */
    private String buildCameraParameters(CameraPose pose) {
        // 将大地坐标转换为空间直角坐标
        double[] xyz = blhtoxyz(pose.longitude, pose.latitude, pose.altitude);

        // 将轴角yaw, pitch, roll转换为四元数
        double[] quaternion = eulerToQuaternion(pose.yaw, pose.pitch, pose.roll);
        
        JsonObject params = new JsonObject();
        params.addProperty("x0", pose.x0); // 图像中心x坐标
        params.addProperty("y0", pose.yo); // 图像中心y坐标
        params.addProperty("f", pose.f); // 焦距
        params.addProperty("xs", xyz[0]); // 空间直角坐标X
        params.addProperty("ys", xyz[1]); // 空间直角坐标Y
        params.addProperty("zs", xyz[2]); // 空间直角坐标Z
        params.addProperty("rotation_type", "quaternion");
        params.addProperty("qw", quaternion[0]); // 四元数
        params.addProperty("qx", quaternion[1]); // 四元数
        params.addProperty("qy", quaternion[2]); // 四元数
        params.addProperty("qz", quaternion[3]); // 四元数
        
        return params.toString();
    }

    /**
     * 解析深度估计结果
     */
    private void parseDepthEstimationResult(JsonObject result) {
        runOnUiThread(() -> {
            boolean success = result.get("success").getAsBoolean();
            String message = result.get("message").getAsString();
            
            if (success) {
                JsonArray coordinates = result.getAsJsonArray("estimated_coordinates");
                float depth = result.get("depth").getAsFloat();
                float reprojectionError = result.get("reprojection_error").getAsFloat();
                int numMatches = result.get("num_matches").getAsInt();
                
                String resultMessage = String.format(
                    "深度估计成功!\n" +
                    "估计坐标: [%.2f, %.2f, %.2f]\n" +
                    "深度: %.2f 米\n" +
                    "重投影误差: %.2f 像素\n" +
                    "匹配点数: %d\n" +
                    "消息: %s",
                    coordinates.get(0).getAsFloat(),
                    coordinates.get(1).getAsFloat(),
                    coordinates.get(2).getAsFloat(),
                    depth,
                    reprojectionError,
                    numMatches,
                    message
                );
                
                callback.addChatMessage(Constant.OWNER_BOT, resultMessage);
                
                // 如果有检测结果，显示检测信息
                if (result.has("detection_result")) {
                    JsonObject detection = result.getAsJsonObject("detection_result");
                    if (detection.has("bbox_2d")) {
                        JsonArray bbox = detection.getAsJsonArray("bbox_2d");
                        if (bbox.size() > 0) {
                            String label = detection.has("label") ? detection.get("label").getAsString() : "unknown";
                            String detectionMsg = String.format("检测到目标: %s, 边界框: %s", label, bbox.toString());
                            callback.addChatMessage(Constant.OWNER_BOT, detectionMsg);
                        }
                    }
                }
            } else {
                callback.addChatMessage(Constant.OWNER_BOT, "深度估计失败: " + message);
            }
        });
    }

    /**
     * 测试坐标转换功能
     */
    public void testCoordinateConversion() {
        // 测试用例：经度120.12，纬度30.34，高度20.67
        double[] result = blhtoxyz(120.12, 30.34, 20.67);
        
        runOnUiThread(() -> {
            String testResult = String.format(
                "坐标转换测试:\n" +
                "输入: 经度=120.12°, 纬度=30.34°, 高度=20.67m\n" +
                "输出: X=%.6f, Y=%.6f, Z=%.6f\n" +
                "参考: X=-2764652.942859, Y=4765441.941872, Z=3202969.265280",
                result[0], result[1], result[2]
            );
            callback.addChatMessage(Constant.OWNER_BOT, testResult);
        });
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
        float currHeading = 0.0f;
        
        // 安全地获取当前航向角
        if (mCI != null && mCI.mFlightController != null) {
            try {
                if (mCI.mFlightController.getCompass() != null) {
                    currHeading = mCI.mFlightController.getCompass().getHeading();
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    callback.addChatMessage(Constant.OWNER_BOT, "获取当前航向角失败，使用默认值: " + e.getMessage());
                });
            }
        } else {
            runOnUiThread(() -> {
                callback.addChatMessage(Constant.OWNER_BOT, "FlightController 不可用，使用默认航向角");
            });
        }
        
        mYaw += currHeading;
        mSingletonVirtualStickExecutor.mMovewith(mYaw,mPitch);
        runOnUiThread(() -> callback.addChatMessage(Constant.OWNER_BOT,"执行完毕"));
    }

    /**
     * 停止跟踪
     */
    public void stopTracking() {
        mSingletonVirtualStickExecutor = MyVirtualStickExecutor.getUniqueInstance();
        mSingletonVirtualStickExecutor.mStop();
    }

    public boolean getIsEnd(){
        return isend;
    }

    //endregion
}
