package com.dji.sdk.voice_control.internal.controller;

import static com.dji.sdk.voice_control.internal.utils.ToastUtils.showToast;
import static com.google.android.gms.internal.zzahn.runOnUiThread;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.Environment;
import android.os.Handler;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;

import dji.common.camera.SettingsDefinitions;
import dji.common.error.DJIError;
import dji.common.product.Model;
import dji.sdk.base.BaseProduct;
import dji.sdk.camera.Camera;
import dji.sdk.media.DownloadListener;
import dji.sdk.media.MediaFile;
import dji.sdk.sdkmanager.DJISDKManager;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import android.view.View;
import dji.sdk.media.DownloadListener;
import dji.sdk.media.FetchMediaTask;
import dji.sdk.media.FetchMediaTaskContent;
import dji.sdk.media.FetchMediaTaskScheduler;
import dji.sdk.media.MediaFile;
import dji.sdk.media.MediaManager;
import dji.common.camera.SettingsDefinitions;
import dji.common.camera.StorageState;
import dji.common.error.DJICameraError;
import dji.common.error.DJIError;
import dji.common.product.Model;
import dji.common.util.CommonCallbacks;
import dji.log.DJILog;
import dji.sdk.base.BaseProduct;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class HttpUtil {
    private Context mContext;
    private Handler handler;
    private int currentProgress = -1;
    private ProgressDialog mDownloadDialog;

    public HttpUtil(Context mcontext){
        this.mContext = mcontext;
    }

    //拍照
    public void captureAction(){
        final Camera camera = DJISampleApplication.getCameraInstance();
        if (camera != null) {
            if (isMavicAir2() || isM300()) {
                camera.setFlatMode(SettingsDefinitions.FlatCameraMode.PHOTO_SINGLE, djiError -> {
                    if (null == djiError) {
                        takePhoto();
                    }
                });
            }else {
                camera.setShootPhotoMode(SettingsDefinitions.ShootPhotoMode.SINGLE, djiError -> {
                    if (null == djiError) {
                        takePhoto();
                    }
                });
            }
        }
    }

    public void takePhoto(){
        final Camera camera = DJISampleApplication.getCameraInstance();
        if (camera == null){
            return;
        }
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                camera.startShootPhoto(djiError -> {
                    if (djiError == null) {
                        showToast("take photo: success");
                    } else {
                        showToast(djiError.getDescription());
                    }
                });
            }
        }, 2000);
    }

    //下载图片
    public void fetchLatestPhoto() {
        MediaManager mediaManager = DJISDKManager.getInstance().getProduct().getCamera().getMediaManager();
        mediaManager.refreshFileList(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError djiError) {
                if (djiError == null) {
                    List<MediaFile> mediaFiles = mediaManager.getSDCardFileListSnapshot();
                    if (mediaFiles != null && !mediaFiles.isEmpty()) {
                        MediaFile latestFile = mediaFiles.get(0);
                        downloadFile(latestFile);
                    }
                } else {
                    showToast("Failed to refresh media list: " + djiError.getDescription());
                }
            }
        });
    }

    public void downloadFile(MediaFile mediaFile){
        File destDir = new File(Environment.getExternalStorageDirectory().getPath() + "/MediaManagerDemo/");
        if ((mediaFile.getMediaType() == MediaFile.MediaType.PANORAMA)
                || (mediaFile.getMediaType() == MediaFile.MediaType.SHALLOW_FOCUS)) {
            return;
        }

        mediaFile.fetchFileData(destDir, null, new DownloadListener<String>() {
            @Override
            public void onFailure(DJIError error) {
                HideDownloadProgressDialog();
                setResultToToast("Download File Failed" + error.getDescription());
                currentProgress = -1;
            }

            @Override
            public void onProgress(long total, long current) {
            }

            @Override
            public void onRateUpdate(long total, long current, long persize) {
                int tmpProgress = (int) (1.0 * current / total * 100);
                if (tmpProgress != currentProgress) {
                    mDownloadDialog.setProgress(tmpProgress);
                    currentProgress = tmpProgress;
                }
            }

            @Override
            public void onRealtimeDataUpdate(byte[] bytes, long l, boolean b) {

            }

            @Override
            public void onStart() {
                currentProgress = -1;
                ShowDownloadProgressDialog();
            }

            @Override
            public void onSuccess(String filePath) {
                HideDownloadProgressDialog();
                uploadPhotoToServer(destDir);
                setResultToToast("Download File Success" + ":" + filePath);
                currentProgress = -1;
            }
        });
    }

    //上传服务器
    private void uploadPhotoToServer(File photoFile) {
        OkHttpClient client = new OkHttpClient();

        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("photo", photoFile.getName(),
                        RequestBody.create(MediaType.parse("image/jpeg"), photoFile))
                .build();

        Request request = new Request.Builder()
                .url("http://<server-ip>:<port>/upload") // 替换为你的服务器地址
                .post(requestBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> showToast("Upload failed: " + e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String responseData = response.body().string();
                    runOnUiThread(() -> showToast("Upload successful, result: " + responseData));
                } else {
                    runOnUiThread(() -> showToast("Upload failed: " + response.message()));
                }
            }
        });
    }


    //辅助函数=
    private boolean isMavicAir2(){
        BaseProduct baseProduct = DJISampleApplication.getProductInstance();
        if (baseProduct != null) {
            return baseProduct.getModel() == Model.MAVIC_AIR_2;
        }
        return false;
    }

    private boolean isM300(){
        BaseProduct baseProduct = DJISampleApplication.getProductInstance();
        if (baseProduct != null) {
            return baseProduct.getModel() == Model.MATRICE_300_RTK;
        }
        return false;
    }

    //UI窗口展示
    private void setResultToToast(final String result) {
        runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(mContext, result, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void ShowDownloadProgressDialog() {
        if (mDownloadDialog != null) {
            runOnUiThread(new Runnable() {
                public void run() {
                    mDownloadDialog.incrementProgressBy(-mDownloadDialog.getProgress());
                    mDownloadDialog.show();
                }
            });
        }
    }

    private void HideDownloadProgressDialog() {
        if (null != mDownloadDialog && mDownloadDialog.isShowing()) {
            runOnUiThread(new Runnable() {
                public void run() {
                    mDownloadDialog.dismiss();
                }
            });
        }
    }
}
