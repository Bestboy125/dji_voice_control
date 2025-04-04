package com.dji.sdk.voice_control.internal.controller.yolo;

import android.content.Context;
import android.graphics.Bitmap;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.List;

public class SceneDescriptionGenerator implements Detector.DetectorListener {
    private Detector detector;
    private int frameNumber = 0;
    private JSONObject sceneDescription = null;
    private boolean detectionComplete = false;
    
    public SceneDescriptionGenerator(Context context, String modelPath, String labelPath) {
        detector = new Detector(context, modelPath, labelPath, this);
    }
    
    /**
     * 生成单张图片的场景描述
     * @param image 输入的图片
     * @param frameNum 帧序号
     * @return JSON格式的场景描述
     */
    public JSONObject generateDescription(Bitmap image, int frameNum) {
        this.frameNumber = frameNum;
        this.detectionComplete = false;
        this.sceneDescription = null;
        
        // 调用detector进行物体检测
        detector.detect(image);
        
        // 等待检测完成
        long timeout = System.currentTimeMillis() + 3000; // 3秒超时
        while (!detectionComplete && System.currentTimeMillis() < timeout) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        
        return sceneDescription;
    }
    
    /**
     * 关闭检测器资源
     */
    public void close() {
        if (detector != null) {
            detector.close();
        }
    }
    
    /**
     * 当没有检测到物体时的回调
     */
    @Override
    public void onEmptyDetect() {
        try {
            sceneDescription = new JSONObject();
            sceneDescription.put("帧序号", frameNumber);
            sceneDescription.put("检测模型", "YOLO");
            sceneDescription.put("检测对象", "");
            sceneDescription.put("bounding_box", JSONObject.NULL);
            sceneDescription.put("描述位置", "目标已离开画面，无法检测到");
            
            detectionComplete = true;
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 当检测到物体时的回调
     */
    @Override
    public void onDetect(List<BoundingBox> boundingBoxes, long inferenceTime) {
        try {
            // 取置信度最高的检测框
            if (!boundingBoxes.isEmpty()) {
                BoundingBox bestBox = boundingBoxes.get(0);
                
                sceneDescription = new JSONObject();
                sceneDescription.put("帧序号", frameNumber);
                sceneDescription.put("检测模型", "YOLO");
                sceneDescription.put("检测对象", bestBox.getClsName());
                
                // 创建bounding_box信息
                JSONObject bbox = new JSONObject();
                // 将相对坐标转换为像素坐标 (这里假设输入图像尺寸为1000x1000，可根据实际情况调整)
                int imageWidth = 1000;
                int imageHeight = 1000;
                int x = (int) (bestBox.getCx() * imageWidth);
                int y = (int) (bestBox.getCy() * imageHeight);
                int width = (int) (bestBox.getW() * imageWidth);
                int height = (int) (bestBox.getH() * imageHeight);
                
                bbox.put("x", x);
                bbox.put("y", y);
                bbox.put("width", width);
                bbox.put("height", height);
                sceneDescription.put("bounding_box", bbox);
                
                // 确定物体在图像中的位置描述
                String positionDescription = determinePosition(bestBox.getCx(), bestBox.getCy());
                sceneDescription.put("描述位置", positionDescription);
            }
            
            detectionComplete = true;
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 根据物体的中心点确定其在图像中的位置描述
     * @param cx 物体中心点x坐标（归一化到0-1范围）
     * @param cy 物体中心点y坐标（归一化到0-1范围）
     * @return 位置描述
     */
    private String determinePosition(float cx, float cy) {
        String horizontalPos;
        String verticalPos;
        
        // 水平位置
        if (cx < 0.33) {
            horizontalPos = "左";
        } else if (cx < 0.66) {
            horizontalPos = "中央";
        } else {
            horizontalPos = "右";
        }
        
        // 垂直位置
        if (cy < 0.33) {
            verticalPos = "偏上";
        } else if (cy < 0.66) {
            verticalPos = "位置";
        } else {
            verticalPos = "偏下";
        }
        
        return "图像" + horizontalPos + verticalPos;
    }
    
    /**
     * 示例用法：如何使用这个类生成场景描述并加入到prompt中
     */
    public static void useExample(Context context, Bitmap image) {
        SceneDescriptionGenerator generator = new SceneDescriptionGenerator(
                context, 
                "models/yolov5s.tflite", 
                "models/coco.txt");
        
        try {
            // 生成当前帧的场景描述
            JSONObject currentDescription = generator.generateDescription(image, 6);
            
            // 构建完整的prompt
            JSONObject fullPrompt = new JSONObject();
            String jsonString = fullPrompt.toString(); // 将完整的prompt转换为字符串
            
            // 使用完毕后释放资源
            generator.close();
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
} 