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
public class BaseRtspFpvView extends RelativeLayout implements TextureView.SurfaceTextureListener,GetVideoData {

    private TextureView mVideoSurface = null;
    private DJICodecManager mCodecManager = null;
    private VideoFeeder.VideoDataListener videoDataListener = null;
    private RtspServer rtspServer;

    private VideoEncoder videoEncoder;

    public BaseRtspFpvView(Context context, RtspServer rtspServer) {
        super(context);
        this.rtspServer = rtspServer;
        initUI();
    }

    private void initUI() {
        LayoutInflater layoutInflater = (LayoutInflater) getContext().getSystemService(Service.LAYOUT_INFLATER_SERVICE);

        View content = layoutInflater.inflate(R.layout.layout_fpvscreen, null, false);
        LayoutParams rlParam = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);

        addView(content, rlParam);

        Log.v("TAG","Start to test");

        mVideoSurface = (TextureView) findViewById(R.id.texture_video_previewer_surface);

        if (null != mVideoSurface) {
            mVideoSurface.setSurfaceTextureListener(this);

            videoDataListener = new VideoFeeder.VideoDataListener() {
                @Override
                public void onReceive(byte[] bytes, int size) {
                    if (null != mCodecManager) {
                        mCodecManager.sendDataToDecoder(bytes,
                                size,
                                UsbAccessoryService.VideoStreamSource.Fpv.getIndex());

                    }

                    // 将一帧原始数据进行编码 getVideoData进行接受编码回调 并将视频发送给Rtspserver
                    int pts = (int) (System.nanoTime() / 1000);
                    Frame frame = new Frame(bytes, pts, bytes.length);
                    videoEncoder = new VideoEncoder(BaseRtspFpvView.this);
                    videoEncoder.prepareVideoEncoder();
                    videoEncoder.inputYUVData(frame);
                    videoEncoder.start();
                }
            };
        }

        initSDKCallback();

    }

    public void onSpsPpsVpsRtp(ByteBuffer sps, ByteBuffer pps, ByteBuffer vps) {
        ByteBuffer newSps = sps.duplicate();
        ByteBuffer newPps = pps.duplicate();
        ByteBuffer newVps = vps.duplicate();
        rtspServer.setVideoInfo(newSps, newPps, newVps);
    }

    @Override
    public void onSpsPpsVps(ByteBuffer sps, ByteBuffer pps, ByteBuffer vps) {
        onSpsPpsVpsRtp(sps.duplicate(), pps.duplicate(), vps != null ? vps.duplicate() : null);
    }

    @Override
    public void getVideoData(ByteBuffer h264Buffer, MediaCodec.BufferInfo info) {
        rtspServer.sendVideo(h264Buffer, info);
    }

    @Override
    public void onVideoFormat(MediaFormat mediaFormat) {

    }


    private void initSDKCallback() {
        try {
            VideoFeeder.getInstance().getSecondaryVideoFeed().addVideoDataListener(videoDataListener);
        } catch (Exception ignored) {
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

    }


}
