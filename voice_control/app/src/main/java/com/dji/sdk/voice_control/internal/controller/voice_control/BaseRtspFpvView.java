package com.dji.sdk.voice_control.internal.controller.voice_control;

import android.app.Service;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import com.dji.sdk.voice_control.R;

import java.io.IOException;
import java.nio.ByteBuffer;

import dji.midware.usb.P3.UsbAccessoryService;
import dji.sdk.camera.VideoFeeder;
import dji.sdk.codec.DJICodecManager;
import kr.co.makeitall.rtspserver.RtspServer;

import com.pedro.encoder.Frame;
import com.pedro.encoder.utils.CodecUtil;
import com.pedro.encoder.video.FormatVideoEncoder;
import com.pedro.encoder.video.GetVideoData;
import com.pedro.encoder.video.VideoEncoder;

import com.pedro.rtsp.rtsp.VideoCodec;

/**
 * This class is designed for showing the fpv video feed from the camera or Lightbridge 2.
 * @maintainer Eddie Wang
 */
public class BaseRtspFpvView extends RelativeLayout implements TextureView.SurfaceTextureListener, GetVideoData {

    private TextureView mVideoSurface = null;
    private DJICodecManager mCodecManager = null;
    private VideoFeeder.VideoDataListener videoDataListener = null;
    private RtspServer rtspServer;
    private VideoEncoder videoEncoder;

    public BaseRtspFpvView(Context context, RtspServer rtspServer) {
        super(context);
        this.rtspServer = rtspServer;
        initUI();

        // 初始化视频编码器
        videoEncoder = new VideoEncoder(this);
        videoEncoder.prepareVideoEncoder(
                1280,            // width: 1280 pixels
                720,             // height: 720 pixels
                30,              // fps: 30 frames per second
                2500 * 1024,     // bitRate: 2500 kbps
                0,               // rotation: 0 degrees
                1,               // iFrameInterval: 1 second
                FormatVideoEncoder.YUV420Dynamical // formatVideoEncoder: YUV420
        );
    }

    private void initUI() {
        LayoutInflater layoutInflater = (LayoutInflater) getContext().getSystemService(Service.LAYOUT_INFLATER_SERVICE);

        View content = layoutInflater.inflate(R.layout.layout_fpvscreen, null, false);
        LayoutParams rlParam = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);

        addView(content, rlParam);

        mVideoSurface = (TextureView) findViewById(R.id.texture_video_previewer_surface);

        if (null != mVideoSurface) {
            mVideoSurface.setSurfaceTextureListener(this);

            // 设置视频数据监听器
            videoDataListener = new VideoFeeder.VideoDataListener() {
                @Override
                public void onReceive(byte[] bytes, int size) {
                    if (mCodecManager != null) {
                        mCodecManager.sendDataToDecoder(bytes,
                                size,
                                UsbAccessoryService.VideoStreamSource.Fpv.getIndex());
                    }

                    // 将一帧数据封装并编码，实时发送到 RTSP 服务器
                    try {
                        int pts = (int) (System.nanoTime() / 1000);
                        Frame frame = new Frame(bytes, pts, size);
                        videoEncoder.inputYUVData(frame);

                        // 确保编码器处于运行状态
                        if (!videoEncoder.isRunning()) {
                            videoEncoder.start();
                        }
                    } catch (Exception e) {
                        Log.e("BaseRtspFpvView", "Error encoding video frame: " + e.getMessage());
                    }
                }
            };
        }

        initSDKCallback();
    }

    public void onSpsPpsVpsRtp(ByteBuffer sps, ByteBuffer pps, ByteBuffer vps) {
        // 确保 Sps 和 Pps 正确传递给 RTSP 服务器
        ByteBuffer newSps = sps != null ? sps.duplicate() : null;
        ByteBuffer newPps = pps != null ? pps.duplicate() : null;
        ByteBuffer newVps = vps != null ? vps.duplicate() : null;

        rtspServer.setVideoInfo(newSps, newPps, newVps);
    }

    @Override
    public synchronized void onSpsPpsVps(ByteBuffer sps, ByteBuffer pps, ByteBuffer vps) {
        if (sps == null || pps == null) {
            Log.e("BaseRtspFpvView", "SPS or PPS is null. Retrying encoder initialization...");
            videoEncoder.stop();
            videoEncoder.prepareVideoEncoder(
                    1280,            // width: 1280 pixels
                    720,             // height: 720 pixels
                    30,              // fps: 30 frames per second
                    2500 * 1024,     // bitRate: 2500 kbps
                    0,               // rotation: 0 degrees
                    1,               // iFrameInterval: 1 second
                    FormatVideoEncoder.YUV420Dynamical // formatVideoEncoder: YUV420
            );
            videoEncoder.start();
        }
        onSpsPpsVpsRtp(sps, pps, vps);
    }

    @Override
    public void getVideoData(ByteBuffer h264Buffer, MediaCodec.BufferInfo info) {
        try {
            rtspServer.sendVideo(h264Buffer, info);
        } catch (Exception e) {
            Log.e("BaseRtspFpvView", "Error sending video data: " + e.getMessage());
        }
    }

    @Override
    public void onVideoFormat(MediaFormat mediaFormat) {
        // 视频格式回调，必要时实现处理
    }

    private void initSDKCallback() {
        try {
            VideoFeeder.getInstance().getSecondaryVideoFeed().addVideoDataListener(videoDataListener);
        } catch (Exception e) {
            Log.e("BaseRtspFpvView", "Error initializing SDK callback: " + e.getMessage());
        }
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        if (mCodecManager == null) {
            mCodecManager = new DJICodecManager(getContext(), surface, width, height);
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        if (mCodecManager != null) {
            mCodecManager.cleanSurface();
            mCodecManager = null;
        }
        mCodecManager = new DJICodecManager(getContext(), surface, width, height);
        initUI();
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        if (mCodecManager != null) {
            mCodecManager.cleanSurface();
            mCodecManager = null;
        }
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        // 必要时处理纹理更新
    }
}
