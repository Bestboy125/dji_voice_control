package com.dji.sdk.voice_control.internal.controller;

import static com.dji.sdk.voice_control.internal.utils.ToastUtils.showToast;

import android.app.AlertDialog;
import android.app.Service;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;

import com.dji.sdk.voice_control.R;
import com.dji.sdk.voice_control.internal.utils.Helper;
import com.dji.sdk.voice_control.internal.utils.PopupUtils;
import com.dji.sdk.voice_control.internal.utils.ToastUtils;
import com.dji.sdk.voice_control.internal.utils.VideoFeedView;
import com.dji.sdk.voice_control.internal.view.PresentableView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import dji.sdk.base.BaseProduct;
import dji.sdk.camera.VideoFeeder;
import dji.sdk.sdkmanager.DJISDKManager;
import dji.sdk.sdkmanager.LiveStreamManager;
import dji.sdk.sdkmanager.LiveVideoBitRateMode;
import dji.sdk.sdkmanager.LiveVideoResolution;

/**
 * Class for live stream demo.
 *
 * @author Hoker
 * @date 2019/1/28
 * <p>
 * Copyright (c) 2019, DJI All Rights Reserved.
 */
public class LiveStream extends RelativeLayout implements PresentableView, View.OnClickListener {

    public String liveShowUrl = null;

    private VideoFeedView primaryVideoFeedView;
    private VideoFeedView fpvVideoFeedView;
    public int lastBitRate = 2048;

    private LiveStreamManager.OnLiveChangeListener listener;
    private LiveStreamManager.LiveStreamVideoSource currentVideoSource = LiveStreamManager.LiveStreamVideoSource.Primary;
    private static final String URL_KEY = "sp_stream_url";

    public LiveStream(Context context) {
        super(context);
        liveShowUrl = context.getSharedPreferences(context.getPackageName(), Context.MODE_PRIVATE).getString(URL_KEY, liveShowUrl);
        initUI(context);
        initListener();
    }

    private void initUI(Context context) {
        LayoutInflater layoutInflater = (LayoutInflater) context.getSystemService(Service.LAYOUT_INFLATER_SERVICE);
        layoutInflater.inflate(R.layout.view_live_stream, this, true);

        try {
            // 初始化 primary 视频源
            primaryVideoFeedView = (VideoFeedView) findViewById(R.id.video_view_primary_video_feed);
            if (primaryVideoFeedView != null) {
                primaryVideoFeedView.registerLiveVideo(VideoFeeder.getInstance().getPrimaryVideoFeed(), true);
            } else {
                throw new NullPointerException("Primary VideoFeedView is null");
            }

            // 初始化 fpv 视频源
            fpvVideoFeedView = (VideoFeedView) findViewById(R.id.video_view_fpv_video_feed);
            if (fpvVideoFeedView != null) {
                fpvVideoFeedView.registerLiveVideo(VideoFeeder.getInstance().getSecondaryVideoFeed(), false);

                // 如果支持多流平台，显示 fpv 视频源
                if (Helper.isMultiStreamPlatform()) {
                    fpvVideoFeedView.setVisibility(VISIBLE);
                }
            } else {
                throw new NullPointerException("FPV VideoFeedView is null");
            }
        } catch (NullPointerException e) {
            // 处理空指针异常
            ToastUtils.setResultToToast("初始化视频视图失败: " + e.getMessage());
        } catch (Exception e) {
            // 捕获其他异常
            ToastUtils.setResultToToast("初始化视频流时发生错误: " + e.getMessage());
            e.printStackTrace(); // 打印堆栈信息，方便调试
        }

    }

    private void initListener() {
        listener = new LiveStreamManager.OnLiveChangeListener() {
            @Override
            public void onStatusChanged(int i) {
                ToastUtils.setResultToToast("status changed : " + i);
            }
        };
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        BaseProduct product = DJISampleApplication.getProductInstance();
        if (product == null || !product.isConnected()) {
            ToastUtils.setResultToToast("Disconnect");
            return;
        }
        if (isLiveStreamManagerOn()){
            DJISDKManager.getInstance().getLiveStreamManager().registerListener(listener);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (isLiveStreamManagerOn()){
            DJISDKManager.getInstance().getLiveStreamManager().unregisterListener(listener);
        }
    }

    @Override
    public int getDescription() {
        return R.string.component_listview_live_stream;
    }

    @NonNull
    @Override
    public String getHint() {
        return this.getClass().getSimpleName() + ".java";
    }


    //region 控制直播的函数
    public Bitmap getBitmap() {
        if (primaryVideoFeedView != null) {
            // 创建一个 Bitmap 来存储当前帧
            Bitmap bitmap = Bitmap.createBitmap(
                    primaryVideoFeedView.getWidth(),
                    primaryVideoFeedView.getHeight(),
                    Bitmap.Config.ARGB_8888
            );
            // 创建 Canvas 并让它绘制 primaryVideoFeedView
            Canvas canvas = new Canvas(bitmap);
            primaryVideoFeedView.draw(canvas);
            return bitmap;
        }
        return null;
    }

    public void startLiveShow() {
        ToastUtils.setResultToToast("Start Live Show");
        if (!isLiveStreamManagerOn()) {
            return;
        }
        if (DJISDKManager.getInstance().getLiveStreamManager().isStreaming()) {
            ToastUtils.setResultToToast("already started!");
            return;
        }
        new Thread() {
            @Override
            public void run() {
                DJISDKManager.getInstance().getLiveStreamManager().setLiveUrl(liveShowUrl);
                int result = DJISDKManager.getInstance().getLiveStreamManager().startStream();
                DJISDKManager.getInstance().getLiveStreamManager().setStartTime();
                LiveStream.this.getContext().getSharedPreferences(LiveStream.this.getContext().getPackageName(),
                                                                      Context.MODE_PRIVATE).edit().putString(URL_KEY,liveShowUrl).commit();

                ToastUtils.setResultToToast("startLive:" + result +
                        "\n isVideoStreamSpeedConfigurable:" + DJISDKManager.getInstance().getLiveStreamManager().isVideoStreamSpeedConfigurable() +
                        "\n isLiveAudioEnabled:" + DJISDKManager.getInstance().getLiveStreamManager().isLiveAudioEnabled());
            }
        }.start();
    }

    public void enableReEncoder() {
        if (!isLiveStreamManagerOn()) {
            return;
        }
        DJISDKManager.getInstance().getLiveStreamManager().setVideoEncodingEnabled(true);
        ToastUtils.setResultToToast("Force Re-Encoder Enabled!");
    }

    public void disableReEncoder() {
        if (!isLiveStreamManagerOn()) {
            return;
        }
        DJISDKManager.getInstance().getLiveStreamManager().setVideoEncodingEnabled(false);
        ToastUtils.setResultToToast("Disable Force Re-Encoder!");
    }

    public void stopLiveShow() {
        if (!isLiveStreamManagerOn()) {
            return;
        }
        DJISDKManager.getInstance().getLiveStreamManager().stopStream();
        ToastUtils.setResultToToast("Stop Live Show");
    }

    public void soundOn() {
        if (!isLiveStreamManagerOn()) {
            return;
        }
        DJISDKManager.getInstance().getLiveStreamManager().setAudioMuted(false);
        ToastUtils.setResultToToast("Sound On");
    }

    public void soundOff() {
        if (!isLiveStreamManagerOn()) {
            return;
        }
        DJISDKManager.getInstance().getLiveStreamManager().setAudioMuted(true);
        ToastUtils.setResultToToast("Sound Off");
    }

    public void isLiveShowOn() {
        if (!isLiveStreamManagerOn()) {
            return;
        }
        ToastUtils.setResultToToast("Is Live Show On:" + DJISDKManager.getInstance().getLiveStreamManager().isStreaming());
    }

    public void showInfo() {
        if (!isLiveStreamManagerOn()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Video BitRate:").append(DJISDKManager.getInstance().getLiveStreamManager().getLiveVideoBitRate()).append(" kpbs\n");
        sb.append("Audio BitRate:").append(DJISDKManager.getInstance().getLiveStreamManager().getLiveAudioBitRate()).append(" kpbs\n");
        sb.append("Video FPS:").append(DJISDKManager.getInstance().getLiveStreamManager().getLiveVideoFps()).append("\n");
        sb.append("Video Cache size:").append(DJISDKManager.getInstance().getLiveStreamManager().getLiveVideoCacheSize()).append(" frame");
        sb.append("Video Resolution:").append(DJISDKManager.getInstance().getLiveStreamManager().getLiveVideoResolution());

        ToastUtils.setResultToToast(sb.toString());
    }

    public void showLiveStartTime() {
        if (!isLiveStreamManagerOn()) {
            return;
        }
        if (!DJISDKManager.getInstance().getLiveStreamManager().isStreaming()){
            ToastUtils.setResultToToast("Please Start Live First");
            return;
        }
        long startTime = DJISDKManager.getInstance().getLiveStreamManager().getStartTime();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.getDefault());
        String sd = sdf.format(new Date(Long.parseLong(String.valueOf(startTime))));
        ToastUtils.setResultToToast("Live Start Time: " + sd);
    }

    public void changeVideoSource() {
        if (!isLiveStreamManagerOn()) {
            return;
        }
        if (!isSupportSecondaryVideo()) {
            return;
        }
        if (DJISDKManager.getInstance().getLiveStreamManager().isStreaming()) {
            ToastUtils.setResultToToast("Before change live source, you should stop live stream!");
            return;
        }
        currentVideoSource = (currentVideoSource == LiveStreamManager.LiveStreamVideoSource.Primary) ?
                LiveStreamManager.LiveStreamVideoSource.Secoundary :
                LiveStreamManager.LiveStreamVideoSource.Primary;
        DJISDKManager.getInstance().getLiveStreamManager().setVideoSource(currentVideoSource);

        ToastUtils.setResultToToast("Change Success ! Video Source : " + currentVideoSource.name());
    }

    public void showCurrentVideoSource(){
        ToastUtils.setResultToToast("Video Source : " + currentVideoSource.name());
    }

    public boolean isLiveStreamManagerOn() {
        if (DJISDKManager.getInstance().getLiveStreamManager() == null) {
            ToastUtils.setResultToToast("No live stream manager!");
            return false;
        }
        return true;
    }

    public boolean isSupportSecondaryVideo(){
        if (!Helper.isMultiStreamPlatform()) {
            ToastUtils.setResultToToast("No secondary video!");
            return false;
        }
        return true;
    }

    public void setBitRate() {
        final EditText inputServer = new EditText(this.getContext());
        inputServer.setText("2048");
        inputServer.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                inputServer.setHint(null);
            }
        });
        AlertDialog.Builder builder = new AlertDialog.Builder(this.getContext());
        builder.setTitle("Set Live Video Bit Rate in Kpbs 默认为2048").setView(inputServer)
               .setNegativeButton("CANCEL", null);
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                try {
                    String input = inputServer.getText().toString();
                    int bitRate = Integer.parseInt(input);
                    lastBitRate = bitRate;
                    DJISDKManager.getInstance().getLiveStreamManager().setLiveVideoBitRate(bitRate);
                    showToast("Set Video Bit Rate Success!!!");
                } catch (Exception e) {
                    showToast("data error!" + e.getMessage());
                }
            }
        });
        builder.show();
    }

    public void setResolution() {
        LiveVideoResolution[] resolutions = {
            LiveVideoResolution.VIDEO_RESOLUTION_1920_1080,
            LiveVideoResolution.VIDEO_RESOLUTION_1440_1080,
            LiveVideoResolution.VIDEO_RESOLUTION_1280_960,
            LiveVideoResolution.VIDEO_RESOLUTION_1280_720,
            LiveVideoResolution.VIDEO_RESOLUTION_960_720,
            LiveVideoResolution.VIDEO_RESOLUTION_960_540,
            LiveVideoResolution.VIDEO_RESOLUTION_720_540,
            LiveVideoResolution.VIDEO_RESOLUTION_480_360
        };
        PopupUtils.INSTANCE.initPopupNumberPicker(Helper.makeList(resolutions), new Runnable() {
            @Override
            public void run() {
                DJISDKManager.getInstance().getLiveStreamManager().setLiveVideoResolution(resolutions[PopupUtils.INSTANCE.getIndex()[0]]);
                showToast("Set Video Resolution Success!!!");
                PopupUtils.INSTANCE.resetIndex();
            }
        },this);
    }

    //endregion

    @Override
    public void onClick(View v) {
        switch (v.getId()) {}
    }
}
