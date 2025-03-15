package com.dji.sdk.voice_control.internal.controller.voice_control;

import android.app.Service;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import com.dji.sdk.voice_control.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;

import dji.midware.usb.P3.UsbAccessoryService;
import dji.sdk.camera.VideoFeeder;
import dji.sdk.codec.DJICodecManager;
import kr.co.makeitall.rtspserver.RtspServer;

import com.pedro.encoder.Frame;
import com.pedro.encoder.input.decoder.VideoDecoder;
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
    private boolean isStreaming = false; // 添加isStreaming标志位

    //视频录制
    private boolean isRecording = false;
    private File recordingFile = null;
    private FileOutputStream recordingOutputStream = null;
    private ConcurrentLinkedQueue<Frame> recordingFrames = new ConcurrentLinkedQueue<>();
    private Thread recordingThread = null;
    private long recordingStartTime = 0;

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

                    if (isStreaming && rtspServer.isRunning() && rtspServer.getNumClients() > 0) {
                        // 将一帧数据封装并编码，实时发送到 RTSP 服务器
                        try {
                            int pts = (int) (System.nanoTime() / 1000);
                            Frame frame = new Frame(bytes, pts, size);
                            videoEncoder.inputYUVData(frame);

                        } catch (Exception e) {
                            Log.e("BaseRtspFpvView", "Error encoding video frame: " + e.getMessage());
                        }
                    }

                    // 添加录制功能 - 确保在这里添加数据到录制队列
                    if (isRecording && recordingOutputStream != null) {
                        try {
                            // 克隆数据以避免被修改
                            byte[] dataCopy = new byte[size];
                            System.arraycopy(bytes, 0, dataCopy, 0, size);
                            
                            int pts = (int) (System.nanoTime() / 1000);
                            Frame frame = new Frame(dataCopy, pts, size);
                            boolean added = recordingFrames.offer(frame);
                            
                            if (added) {
                                Log.d("BaseRtspFpvView", "Added frame to recording queue, size: " + size);
                            } else {
                                Log.w("BaseRtspFpvView", "Failed to add frame to recording queue");
                            }
                        } catch (Exception e) {
                            Log.e("BaseRtspFpvView", "Error adding frame to recording: " + e.getMessage());
                        }
                    }
                }
            };
        }

        initSDKCallback();
    }

    //region 视频录制
    

    // 开始录制视频
    public void startRecording() {
        if (isRecording) {
            Log.w("BaseRtspFpvView", "Recording is already in progress");
            return;
        }
        
        try {
            // 检查视频数据监听器是否已初始化
            if (videoDataListener == null) {
                Log.e("BaseRtspFpvView", "Video data listener is null");
                throw new IllegalStateException("Video data listener is not initialized");
            }
            
            // 修改为使用应用专属目录
            File recordingDir = new File(getContext().getExternalFilesDir(Environment.DIRECTORY_MOVIES), "DJIRecordings");
            if (!recordingDir.exists()) {
                boolean created = recordingDir.mkdirs();
                if (!created) {
                    Log.e("BaseRtspFpvView", "Failed to create recording directory");
                    throw new IOException("Failed to create recording directory");
                }
            }
            
            // 创建带时间戳的文件名
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            recordingFile = new File(recordingDir, "DJI_" + timestamp + ".h264");
            
            // 创建文件输出流
            recordingOutputStream = new FileOutputStream(recordingFile);
            recordingFrames.clear();
            isRecording = true;
            recordingStartTime = System.currentTimeMillis();
            
            // 创建录制线程
            recordingThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        int frameCount = 0;
                        while (isRecording) {
                            Frame frame = recordingFrames.poll();
                            if (frame != null && recordingOutputStream != null) {
                                recordingOutputStream.write(frame.getBuffer(), 0, frame.getSize());
                                frameCount++;
                                
                                // 每100帧记录一次日志
                                if (frameCount % 100 == 0) {
                                    Log.d("BaseRtspFpvView", "Recorded " + frameCount + " frames");
                                }
                            } else {
                                Thread.sleep(5); // 短暂休眠避免CPU占用过高
                            }
                        }
                        Log.i("BaseRtspFpvView", "Recording thread finished, total frames: " + frameCount);
                    } catch (Exception e) {
                        Log.e("BaseRtspFpvView", "Error in recording thread: " + e.getMessage());
                    }
                }
            });
            recordingThread.start();
            
            Log.i("BaseRtspFpvView", "Started recording to: " + recordingFile.getAbsolutePath());
        } catch (Exception e) {
            Log.e("BaseRtspFpvView", "Failed to start recording: " + e.getMessage(), e);
            isRecording = false;
        }
        
    }
    
    // 停止录制视频
    public File stopRecording() {
        if (!isRecording) {
            Log.w("BaseRtspFpvView", "No recording in progress");
            return null;
        }
        
        isRecording = false;
        
        try {
            // 等待录制线程结束
            if (recordingThread != null) {
                recordingThread.join(1000);
                recordingThread = null;
            }
            
            // 关闭输出流
            if (recordingOutputStream != null) {
                recordingOutputStream.flush();
                recordingOutputStream.close();
                recordingOutputStream = null;
            }
            
            Log.i("BaseRtspFpvView", "Stopped recording. Duration: " + 
                    ((System.currentTimeMillis() - recordingStartTime) / 1000) + " seconds");
            
            // 返回录制的文件
            File completedFile = recordingFile;
            recordingFile = null;
            return completedFile;
            
        } catch (Exception e) {
            Log.e("BaseRtspFpvView", "Error stopping recording: " + e.getMessage());
            return null;
        }
    }
    
    // 获取当前录制状态
    public boolean isRecording() {
        return isRecording;
    }
    
    // 获取当前录制时长（秒）
    public int getRecordingDuration() {
        if (!isRecording || recordingStartTime == 0) {
            return 0;
        }
        return (int)((System.currentTimeMillis() - recordingStartTime) / 1000);
    }

    //endregion

    public void onSpsPpsVpsRtp(ByteBuffer sps, ByteBuffer pps, ByteBuffer vps) {
        // 确保 Sps 和 Pps 正确传递给 RTSP 服务器
        ByteBuffer newSps = sps != null ? sps.duplicate() : null;
        ByteBuffer newPps = pps != null ? pps.duplicate() : null;
        ByteBuffer newVps = vps != null ? vps.duplicate() : null;

        rtspServer.setVideoInfo(newSps, newPps, newVps);
    }

    // 启动推流（启动编码器）
    public void startStreaming() {
        if (!isStreaming) {
            isStreaming = true; // 设置标志位为true
            Log.i("BaseRtspFpvView", "Streaming started. Starting encoder...");
            videoEncoder.start(); // 启动编码器
        } else {
            Log.w("BaseRtspFpvView", "Streaming is already running.");
        }
    }

    // 停止推流（停止编码器）
    public void stopStreaming() {
        if (isStreaming) {
            isStreaming = false; // 设置标志位为false
            Log.i("BaseRtspFpvView", "Streaming stopped. Stopping encoder...");
            videoEncoder.stop(); // 停止编码器
        } else {
            Log.w("BaseRtspFpvView", "Streaming is not running.");
        }
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

            // 添加录制功能 - 保存编码后的H264数据
            if (isRecording && recordingOutputStream != null) {
                byte[] data = new byte[info.size];
                h264Buffer.position(info.offset);
                h264Buffer.get(data, 0, info.size);
                
                // 将编码后的数据添加到录制队列
                Frame frame = new Frame(data, (int) info.presentationTimeUs, info.size);
                recordingFrames.offer(frame);
            }
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
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        if (isRecording) {
            stopRecording();
        }

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
