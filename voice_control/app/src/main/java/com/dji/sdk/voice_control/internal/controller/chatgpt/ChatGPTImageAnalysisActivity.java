package com.dji.sdk.voice_control.internal.controller.chatgpt;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.dji.sdk.voice_control.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 示例Activity，展示如何使用ChatGPTClient进行图像分析
 */
public class ChatGPTImageAnalysisActivity extends AppCompatActivity {

    private static final int REQUEST_CAMERA_PERMISSION = 100;
    private static final int REQUEST_IMAGE_CAPTURE = 101;
    private static final int REQUEST_PICK_IMAGE = 102;

    private ImageView imageView;
    private EditText questionEditText;
    private Button cameraButton;
    private Button galleryButton;
    private Button analyzeButton;
    private TextView resultTextView;
    private ProgressBar progressBar;

    private File photoFile;
    private Bitmap currentImageBitmap;
    private ChatGPTExample chatGPTExample;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chatgpt_image_analysis);

        // 初始化视图
        imageView = findViewById(R.id.imageView);
        questionEditText = findViewById(R.id.questionEditText);
        cameraButton = findViewById(R.id.cameraButton);
        galleryButton = findViewById(R.id.galleryButton);
        analyzeButton = findViewById(R.id.analyzeButton);
        resultTextView = findViewById(R.id.resultTextView);
        progressBar = findViewById(R.id.progressBar);

        // 初始化ChatGPTExample
        chatGPTExample = new ChatGPTExample(this);

        // 设置按钮点击事件
        cameraButton.setOnClickListener(v -> checkAndRequestCameraPermission());
        galleryButton.setOnClickListener(v -> openGallery());
        analyzeButton.setOnClickListener(v -> analyzeCurrentImage());
    }

    /**
     * 检查并请求相机权限
     */
    private void checkAndRequestCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        } else {
            openCamera();
        }
    }

    /**
     * 打开相机
     */
    private void openCamera() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                Toast.makeText(this, "创建图像文件时出错", Toast.LENGTH_SHORT).show();
                return;
            }

            if (photoFile != null) {
                Uri photoURI = FileProvider.getUriForFile(this,
                        "com.dji.sdk.voice_control.fileprovider",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
            }
        }
    }

    /**
     * 创建图像文件
     */
    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(null);
        return File.createTempFile(imageFileName, ".jpg", storageDir);
    }

    /**
     * 打开图库
     */
    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, REQUEST_PICK_IMAGE);
    }

    /**
     * 分析当前图像
     */
    private void analyzeCurrentImage() {
        String question = questionEditText.getText().toString().trim();
        if (currentImageBitmap == null) {
            Toast.makeText(this, "请先选择或拍摄一张图片", Toast.LENGTH_SHORT).show();
            return;
        }

        if (question.isEmpty()) {
            Toast.makeText(this, "请输入一个问题", Toast.LENGTH_SHORT).show();
            return;
        }

        // 显示进度条
        progressBar.setVisibility(View.VISIBLE);
        resultTextView.setText("");

        // 调用ChatGPTExample进行图像分析
        chatGPTExample.analyzeImage(currentImageBitmap, question, result -> {
            // 在UI线程显示结果
            runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE);
                resultTextView.setText(result);
            });
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                Toast.makeText(this, "需要相机权限才能拍照", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_IMAGE_CAPTURE) {
                try {
                    currentImageBitmap = BitmapFactory.decodeFile(photoFile.getAbsolutePath());
                    imageView.setImageBitmap(currentImageBitmap);
                } catch (Exception e) {
                    Toast.makeText(this, "加载拍摄的图片失败", Toast.LENGTH_SHORT).show();
                }
            } else if (requestCode == REQUEST_PICK_IMAGE && data != null) {
                try {
                    Uri selectedImage = data.getData();
                    currentImageBitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), selectedImage);
                    imageView.setImageBitmap(currentImageBitmap);
                } catch (IOException e) {
                    Toast.makeText(this, "加载图库图片失败", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }
} 