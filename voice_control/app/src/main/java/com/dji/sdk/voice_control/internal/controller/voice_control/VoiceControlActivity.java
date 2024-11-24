package com.dji.sdk.voice_control.internal.controller.voice_control;

import androidx.appcompat.app.AppCompatActivity;

import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.text.SpannableStringBuilder;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.Toast;

import com.dji.sdk.voice_control.internal.setting.IatSettings;
import com.iflytek.cloud.ErrorCode;
import com.iflytek.cloud.InitListener;
import com.iflytek.cloud.RecognizerListener;
import com.iflytek.cloud.RecognizerResult;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechRecognizer;
import com.iflytek.cloud.SpeechUtility;
import com.iflytek.cloud.ui.RecognizerDialog;
import com.iflytek.cloud.ui.RecognizerDialogListener;
import com.dji.sdk.voice_control.internal.utils.FucUtil;
import com.dji.sdk.voice_control.internal.utils.JsonParser;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import com.dji.sdk.voice_control.R;

import android.os.Looper;
import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Address;
import android.location.Geocoder;
import android.os.Build;
import android.content.SharedPreferences;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.annotation.NonNull;

import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.TextureView;

import com.dji.sdk.voice_control.internal.controller.DJISampleApplication;
import com.dji.sdk.voice_control.internal.controller.Utils;
import com.dji.sdk.voice_control.internal.controller.flightcontrol.CommandInterpreter;
import com.dji.sdk.voice_control.internal.controller.flightcontrol.MyVirtualStickExecutor;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;

//Ibm语音识别
import com.ibm.watson.developer_cloud.android.library.audio.MicrophoneInputStream;
import com.ibm.watson.developer_cloud.android.library.audio.utils.ContentType;
import com.ibm.watson.developer_cloud.speech_to_text.v1.SpeechToText;
import com.ibm.watson.developer_cloud.speech_to_text.v1.model.RecognizeOptions;
import com.ibm.watson.developer_cloud.speech_to_text.v1.model.SpeechResults;
import com.ibm.watson.developer_cloud.speech_to_text.v1.websocket.RecognizeCallback;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.List;
import java.util.StringTokenizer;

import dji.common.battery.BatteryState;
import dji.common.flightcontroller.FlightControllerState;
import dji.sdk.base.BaseProduct;
import dji.sdk.products.Aircraft;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;
import java.util.concurrent.atomic.AtomicBoolean;

public class VoiceControlActivity extends AppCompatActivity implements CommandConfirmationDialogFragment.Communicator, View.OnClickListener {

    private static final String TAG = "VoiceControlActivity";

    //region 语音识别的数据结构
    // 语音听写对象
    private SpeechRecognizer mIat;
    // 语音听写UI
    private RecognizerDialog mIatDialog;
    //
    // 用HashMap存储听写结果
    private HashMap<String, String> mIatResults = new LinkedHashMap<>();
    private Button languageText, dialogButton;
    // 语言类型【默认中文】
    private String language = "en_us";
    // 格式类型【默认json】
    private String resultType = "json";
    private boolean cyclic = false;//音频流识别是否循环调用
    //拼接字符串
    private StringBuffer buffer = new StringBuffer();
    //Handler码
    private int handlerCode = 0x123;
    // 函数调用返回值
    private int resultCode = 0;
    // 切换中英文
    private boolean languageType;
    // 弹框是否显示
    private int dialogType;
    //endregion
    //region 飞行控制的数据结构
    //该活动的实例
    private Context mContext;

    //命令行交互
    private CommandInterpreter mCI;

    // Map
    private View mMapView;
    private LatLng mDroneLocation = new LatLng(0, 0);
    private float mDroneHeading = 0;
    private Marker mDroneMarker = null;
    private LatLng mUserLocation = new LatLng(0, 0);
    //定位
    private Button mBtnLoacte;
    private boolean mMapLocate_flag = true;
    //追踪
    private Button mBtnTracking;
    private boolean mMapTracking_flag = true;

    private PlaceListFragment mPlaceListFragment;

    //private TextView mDistance; 存储飞机数据
    private double mAltitudeData;
    private double mvs;
    private double mhs;
    private double mdistToHome;
    //endregion


    int ret = 0; // 函数调用返回值
    
    //region APP UI结构
    // 分类器 varaibles
    private CommandClassifier cc1;
    private SpeechToText speechService;
    private MicrophoneInputStream capture;
    private String mStrIntention;

    // APP的UI和视图
    private EditText showContacts;
    private Toast mToast;
    private SharedPreferences mSharedPreferences;
    private TextureView fpvTexture; //视频播放
    private Button mBtnInput;
    private boolean mBtnInput_flag = true;
    private EditText mTxtCmmand;
    private Button mBtnStop;
    private Button mBtnDummy;
    private Button mBtnShow;
    private Button mBtnHide;
    private Button mBtnLanguage;

    //Aircraft State 文字显示飞机的状态
    private TextView mAltitude;
    private TextView mVerSpeed;
    private TextView mHorSpeed;
    private TextView mDistance;
    //Battery 电池状态
    BatteryView mBatteryView;
    private TextView mBatteryData;
    private int mBatteryPercent;

    // Retrieve and Rank fragment
    private RARFragment rarFragment;
    private boolean rarFlag;
    private Button mRandR;
    //endregion

    //region activity生命周期
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_voice);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.VIBRATE,
                            Manifest.permission.INTERNET, Manifest.permission.ACCESS_WIFI_STATE,
                            Manifest.permission.WAKE_LOCK, Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_NETWORK_STATE, Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.CHANGE_WIFI_STATE, Manifest.permission.MOUNT_UNMOUNT_FILESYSTEMS,
                            Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.SYSTEM_ALERT_WINDOW,
                            Manifest.permission.READ_PHONE_STATE, Manifest.permission.RECORD_AUDIO
                    }
                    , 1);
        }

        SpeechUtility.createUtility(this, SpeechConstant.APPID +"=12cecf5e");
        //初始化控件
        LayoutInflater layoutInflater = getLayoutInflater();
        View content = layoutInflater.inflate(R.layout.activity_voice, null);
        setContentView(content);
        initUI();

        //初始化科大讯飞语音识别
        // 初始化识别无UI识别对象
        // 使用SpeechRecognizer对象，可根据回调消息自定义界面；
        mIat = SpeechRecognizer.createRecognizer(this, mInitListener);
        // 初始化听写Dialog，如果只使用有UI听写功能，无需创建SpeechRecognizer
        // 使用UI听写功能，请根据sdk文件目录下的notice.txt,放置布局文件和图片资源
        mIatDialog = new RecognizerDialog(VoiceControlActivity.this, mInitListener);

        // 初始化分类器
        speechService = initSpeechToTextService();
        cc1 = new CommandClassifier();

        // 初始化控制器
        mCI = CommandInterpreter.getUniqueInstance(mContext);
        initDrone();

        //注册接收器
        IntentFilter filter = new IntentFilter();
        filter.addAction(DJISampleApplication.FLAG_CONNECTION_CHANGE);
        registerReceiver(mReceiver, filter);

        Log.e(TAG, "onCreate");

    }

    @Override
    public void onStart() {
        super.onStart();
//        doInit();
    }

    @Override
    protected void onResume() {
        // 开放统计 移动数据统计分析
      /*FlowerCollector.onResume(MainActivity.this);
      FlowerCollector.onPageStart(TAG);*/
        Log.e(TAG, "onResume");
        super.onResume();
        updateConnection();
        initDrone();
    }

    @Override
    protected void onPause() {
        Log.e(TAG, "onPause");
        // 开放统计 移动数据统计分析
        super.onPause();
    }

    @Override
    public void onStop() {
        Log.e(TAG, "onStop");
        super.onStop();
    }

    public void onReturn(View view) throws Throwable {
        Log.e(TAG, "onReturn");
        this.finalize();
    }

    @Override
    public void onDestroy() {
        Log.e(TAG, "onDestroy");
//        unregisterReceiver(mReceiver);
        MyVirtualStickExecutor.destroyInstance();
        Log.i(TAG, "onDestory");
        super.onDestroy();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_voice, container, false);
    }
    //endregion

    //region UI相关
    @Override
    public void onDialogMessage(boolean message) {
        if (message) {
            writeRecogRecord(true, mStrIntention, cc1.getEncodedString().toString(), cc1.getCommand());
//            showFpvToast("Start executing command");
            preCheck(cc1.getEncodedString(), cc1.getGoogleMapSearchString()); // Start execution 开始执行操作
        } else {
            writeRecogRecord(false, mStrIntention, cc1.getEncodedString().toString(), cc1.getCommand());
            showFpvToast("Command cancelled");
        }
    }

    @Override
    public void onClick(View view) {
        if (null == mIat) {
            // 创建单例失败，与 21001 错误为同样原因，
            // 参考 http://bbs.xfyun.cn/forum.php?mod=viewthread&tid=9688
            showToast("创建对象失败，请确认 libmsc.so 放置正确，且有调用 createUtility 进行初始化");
            return;
        }
        switch (view.getId()) {
            // 开始听写
            // 如何判断一次听写结束：OnResult isLast=true 或者 onError
            case R.id.iat_recognize:
                buffer.setLength(0);//长度清空
                mIatResults.clear();//清除存贮结果
                // 设置参数
                setParam();
                if (dialogType == 0) {
                    // 显示听写对话框
                    mIatDialog.setListener(mRecognizerDialogListener);
                    mIatDialog.show();
                    showToast("开始听写");
                } else if (dialogType == 1) {
                    // 不显示听写对话框
                    resultCode = mIat.startListening(mRecognizerListener);
                    if (resultCode != ErrorCode.SUCCESS) {
                        showToast("听写失败,错误码：" + ",请点击网址https://www.xfyun.cn/document/error-code查询解决方案");
                    } else {
                        showToast("开始听写");
                    }
                } else if (dialogType == 2) {
                    // 自定义听写对话框
                    showAlertDialog();
                    resultCode = mIat.startListening(mRecognizerListener);
                    if (resultCode != ErrorCode.SUCCESS) {
                        showToast("听写失败,错误码：" + ",请点击网址https://www.xfyun.cn/document/error-code查询解决方案");
                    } else {
                        showToast("开始听写");
                    }
                }
                break;
//            case R.id.languageText:
//                if (languageType) {
//                    languageType = false;
//                    language = "zh_cn";
//                    languageText.setText("点击切换语种：中文");
//                } else {
//                    languageType = true;
//                    language = "en_us";
//                    languageText.setText("点击切换语种：英文");
//                }
//                mIat.setParameter(SpeechConstant.LANGUAGE, language);
//                break;
            // 停止听写
            case R.id.iat_stop:
                mIat.stopListening();
                showToast("停止听写");
                break;
            // 取消听写
            case R.id.iat_cancel:
                mIat.cancel();
                showToast("取消听写");
                break;
//            //默认显示弹框
//            case R.id.dialogButton:
//                if (dialogType == 0) {
//                    dialogType = 1;
//                    dialogButton.setText("不显示讯飞弹框");
//                } else if (dialogType == 1) {
//                    dialogType = 2;
//                    dialogButton.setText("显示自定义弹框");
//                } else if (dialogType == 2) {
//                    dialogButton.setText("显示讯飞弹框");
//                    dialogType = 0;
//                }
//                break;
        }
    }

    private void initUI(){

        mTxtCmmand = (EditText) findViewById(R.id.command_text);
        mBtnInput = (Button) findViewById(R.id.input_btn);
        mBtnStop = (Button) findViewById(R.id.stop_btn);
        mBtnDummy = (Button) findViewById(R.id.dummy_btn);
        mBtnShow = (Button) findViewById(R.id.show_btn);
        mBtnHide = (Button) findViewById(R.id.hide_btn);
        mBtnShow.setVisibility(View.GONE);
        mBtnLoacte = (Button) findViewById(R.id.locate_button);
        mBtnTracking = (Button) findViewById(R.id.tracking_button);
        mBtnLanguage = (Button) findViewById(R.id.iat_lan);
        mBtnLoacte.setVisibility(View.GONE);
        mBtnTracking.setVisibility(View.GONE);
        mBatteryView = (BatteryView) findViewById(R.id.battery_view);
        mBatteryData = (TextView) findViewById(R.id.battery_data);
        mAltitude = (TextView) findViewById(R.id.Altitude);
        mVerSpeed = (TextView) findViewById(R.id.VerticalSpeed);
        mDistance = (TextView) findViewById(R.id.Distance);
        mHorSpeed = (TextView) findViewById(R.id.HorizonSpeed);
        mRandR = (Button) findViewById(R.id.RR_Button);

        findViewById(R.id.iat_recognize).setOnClickListener(this);
        findViewById(R.id.iat_stop).setOnClickListener(this);
        findViewById(R.id.iat_cancel).setOnClickListener(this);
        findViewById(R.id.iat_lan).setOnClickListener(this);

        stopBtnListener();
        lanBtnListener();
        voiceInputListener();
        inputBtnListener();
        RRInputListener();
    }

    private void stopBtnListener() {
        mBtnStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
//                mCI.mDestroy();
                mCI.mStop();
            }
        });
    }

    private void lanBtnListener() {
        mBtnLanguage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (languageType) {
                    languageType = false;
                    language = "zh_cn";
                    mBtnLanguage.setText("中文");
                } else {
                    languageType = true;
                    language = "en_us";
                    mBtnLanguage.setText("英文");
                }
                mIat.setParameter(SpeechConstant.LANGUAGE, language);
            }
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    private void voiceInputListener() {
        mBtnDummy.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        // Reset string command_text input to null
                        mStrIntention = null;
                        // Change button back ground color
//                        mTxtCmmand.setBackgroundResource(R.drawable.common_google_signin_btn_text_dark_focused);
                        // Init MicrophoneInputStream and start watson speec-to-text websocket
                        capture = new MicrophoneInputStream(true);
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    speechService.recognizeUsingWebSocket(capture, getRecognizeOptions(), new VoiceControlActivity.MicrophoneRecognizeDelegate());
                                } catch (Exception e) {
                                    showError(e);
                                }
                            }
                        }).start();
                        break;
                    case MotionEvent.ACTION_UP:
                        // Change button back ground color
//                        mTxtCmmand.setBackgroundResource(R.drawable.common_google_signin_btn_text_dark_normal);
                        // Close MicrophoneInputStream
                        try {
                            capture.close();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        break;
                    default:
                        break;
                }
                return false;
            }
        });
    }

    private void inputBtnListener() {
        mBtnInput.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Reset string command_text input to null
                mStrIntention = null;

                if (mBtnInput_flag) {
                    mBtnInput.setBackgroundResource(R.drawable.keyboard);
                    mTxtCmmand.setHint("Enter Your Command");
                    mTxtCmmand.setEnabled(true);
                    mBtnDummy.setVisibility(View.GONE);
                    mBtnInput_flag = false;
                } else {
                    mBtnInput.setBackgroundResource(R.drawable.mic);
                    mStrIntention = mTxtCmmand.getText().toString();
                    // Tokenize command_in_text
                    StringTokenizer st = new StringTokenizer(mStrIntention);
                    ArrayList<String> tokenedCommand = new ArrayList<>();
                    while (st.hasMoreTokens()) {
                        tokenedCommand.add(st.nextToken());
                    }
                    // Replace mavic similar words
//                    tokenedCommand = findMavicSimilar(tokenedCommand);
                    // Change arraylist to string
                    mStrIntention = TextUtils.join(" ", tokenedCommand);
                    // Execute NLC
                    VoiceControlActivity.ClassificationTask cft = new VoiceControlActivity.ClassificationTask();
                    cft.execute(tokenedCommand);
                    mTxtCmmand.setText("");
                    mTxtCmmand.setHint("Hold for Voice Input");
                    mTxtCmmand.setEnabled(false);
                    mBtnDummy.setVisibility(View.VISIBLE);
                    mBtnInput_flag = true;
                }
            }
        });
    }

    /**
     * Specific input taker for interactive manual
     */
    private void RRInputListener() {
        mRandR.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                rarFlag = !rarFlag;
                if (rarFlag) {
                    mBtnDummy.setVisibility(View.GONE);
                    mBtnInput.setVisibility(View.GONE);
                    mTxtCmmand.setVisibility(View.GONE);
                    rarFragment = new RARFragment();
                    getSupportFragmentManager().beginTransaction().add(R.id.main_layout, rarFragment).commit();
                } else {
                    mBtnDummy.setVisibility(View.VISIBLE);
                    mBtnInput.setVisibility(View.VISIBLE);
                    mTxtCmmand.setVisibility(View.VISIBLE);
                    getSupportFragmentManager().beginTransaction().remove(rarFragment).commit();
                }
            }
        });
    }

    /**
     * show toast
     */
    public void showFpvToast(final String msg) {
        this.runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(mContext.getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showMicText(final String text) {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // input.setText(text);
                mTxtCmmand.setHint(text);
            }
        });
    }

    private void showError(final Exception e) {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(mContext, e.getMessage(), Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            }
        });
    }

    //endregion

    //region 科大讯飞监视器
    /**
     * 初始化监听器。
     */
    private InitListener mInitListener = new InitListener() {

        @Override
        public void onInit(int code) {
            Log.e(TAG, "SpeechRecognizer init() code = " + code);
            if (code != ErrorCode.SUCCESS) {
                showToast("初始化失败，错误码：" + code + ",请点击网址https://www.xfyun.cn/document/error-code查询解决方案");
            }
        }
    };

    /**
     * 听写监听器。
     */
    private RecognizerListener mRecognizerListener = new RecognizerListener() {

        @Override
        public void onBeginOfSpeech() {
            // 此回调表示：sdk内部录音机已经准备好了，用户可以开始语音输入
            showToast("开始说话");
        }

        @Override
        public void onError(SpeechError error) {
            // Tips：
            // 错误码：10118(您没有说话)，可能是录音机权限被禁，需要提示用户打开应用的录音权限。
            showToast(error.getPlainDescription(true));
            if (null != dialog) {
                dialog.dismiss();
            }
        }

        @Override
        public void onEndOfSpeech() {
            // 此回调表示：检测到了语音的尾端点，已经进入识别过程，不再接受语音输入
            showToast("结束说话");
            if (null != dialog) {
                dialog.dismiss();
            }
            // Tokenize command_in_text
            StringTokenizer st = new StringTokenizer(mStrIntention);
            ArrayList<String> tokenedCommand = new ArrayList<>();
            while (st.hasMoreTokens()) {
                tokenedCommand.add(st.nextToken());
            }
            // Replace mavic similar words
//            tokenedCommand = findMavicSimilar(tokenedCommand);
            // Change arraylist to string
            mStrIntention = TextUtils.join(" ", tokenedCommand);
            // Display command in string format
            showMicText(mStrIntention);
            // Execute NLC
            new VoiceControlActivity.ClassificationTask().execute(tokenedCommand);
        }

        @Override
        public void onResult(RecognizerResult results, boolean isLast) {
            Log.e(TAG, "onResult: " + results.getResultString());
            if (resultType.equals(resultType)) {
            } else if (resultType.equals("plain")) {
                buffer.append(results.getResultString());
                mStrIntention = buffer.toString();
            }
            if (isLast & cyclic) {
                // TODO 最后的结果
                Message message = Message.obtain();
                message.what = handlerCode;
                handler.sendMessageDelayed(message, 100);
            }
        }

        @Override
        public void onVolumeChanged(int volume, byte[] data) {
            //showToast("当前正在说话，音量大小：" + volume);
            Log.e(TAG, "onVolumeChanged: " + data.length);
        }

        @Override
        public void onEvent(int eventType, int arg1, int arg2, Bundle obj) {
            // 以下代码用于获取与云端的会话id，当业务出错时将会话id提供给技术支持人员，可用于查询会话日志，定位出错原因
            // 若使用本地能力，会话id为null
            // if (SpeechEvent.EVENT_SESSION_ID == eventType) {
            //    String sid = obj.getString(SpeechEvent.KEY_EVENT_SESSION_ID);
            //    Log.d(TAG, "session id =" + sid);
            // }
        }
    };

    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if (msg.what == handlerCode) {
            }
        }
    };

    /**
     * 听写UI监听器
     */
    private RecognizerDialogListener mRecognizerDialogListener = new RecognizerDialogListener() {
        /**
         * 识别回调成功
         */
        public void onResult(RecognizerResult results, boolean isLast) {
            String text = JsonParser.parseIatResult(results.getResultString());
            String sn = null;
            // 读取json结果中的sn字段
            try {
                JSONObject resultJson = new JSONObject(results.getResultString());
                sn = resultJson.optString("sn");
            } catch (JSONException e) {
                e.printStackTrace();
            }
            mIatResults.put(sn, text);
            StringBuffer resultBuffer = new StringBuffer();
            for (String key : mIatResults.keySet()) {
                resultBuffer.append(mIatResults.get(key));
            }
            // 创建消息框
            AlertDialog.Builder builder = new AlertDialog.Builder(VoiceControlActivity.this);
            builder.setTitle("确认识别结果");

            // 创建一个 EditText 以显示和编辑结果
            EditText editText = new EditText(VoiceControlActivity.this);
            editText.setText(resultBuffer.toString());
            editText.setSelection(resultBuffer.toString().length()); // 光标移到文本末尾
            builder.setView(editText);

            // 设置确认按钮
            builder.setPositiveButton("确定", (dialog, which) -> {
                String confirmedResult = editText.getText().toString();
                // TODO: 在这里处理用户确认后的识别结果，例如更新 UI 或发送数据
                editText.setText(confirmedResult); // 假设将结果显示在 TextView 上
                Toast.makeText(getApplicationContext(), "结果已确认：" + confirmedResult, Toast.LENGTH_SHORT).show();
                processConfirmedResult(confirmedResult);
            });

            // 设置取消按钮
            builder.setNegativeButton("取消", (dialog, which) -> {
                dialog.dismiss(); // 直接关闭对话框
            });

            // 显示消息框
            builder.show();
        }

        /**
         * 识别回调错误.
         */
        public void onError(SpeechError error) {
            showToast(error.getPlainDescription(true));
        }


    };
    // 处理确认后的结果并执行您的逻辑
    private void processConfirmedResult(String confirmedResult) {
        // 将确认结果转换为小写
        String mStrIntention = confirmedResult.toLowerCase();

        // 使用 StringTokenizer 对结果进行分词
        StringTokenizer st = new StringTokenizer(mStrIntention);
        ArrayList<String> tokenedCommand = new ArrayList<>();
        while (st.hasMoreTokens()) {
            tokenedCommand.add(st.nextToken());
        }

        // 如果需要，处理类似 mavic 的单词（这里注释掉了）
        // tokenedCommand = findMavicSimilar(tokenedCommand);

        // 将 ArrayList 转换为字符串
        mStrIntention = TextUtils.join(" ", tokenedCommand);

        // 在界面上显示分词后的字符串
        showMicText(mStrIntention);

        // 执行分类任务（假设是执行 NLC 的核心逻辑）
        new VoiceControlActivity.ClassificationTask().execute(tokenedCommand);
    }

    /**
     * 听写参数设置
     */
    public void setParam() {
        // 清空参数
        mIat.setParameter(SpeechConstant.PARAMS, null);
        // 设置听写引擎类型
        mIat.setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_CLOUD);
        // 设置返回结果格式【目前支持json,xml以及plain 三种格式，其中plain为纯听写文本内容】
        mIat.setParameter(SpeechConstant.RESULT_TYPE, resultType);
        //目前Android SDK支持zh_cn：中文、en_us：英文、ja_jp：日语、ko_kr：韩语、ru-ru：俄语、fr_fr：法语、es_es：西班牙语、
        // 注：小语种若未授权无法使用会报错11200，可到控制台-语音听写（流式版）-方言/语种处添加试用或购买。
        mIat.setParameter(SpeechConstant.LANGUAGE, language);
        // 设置语言区域、当前仅在LANGUAGE为简体中文时，支持方言选择，其他语言区域时，可把此参数值设为mandarin。
        // 默认值：mandarin，其他方言参数可在控制台方言一栏查看。
        mIat.setParameter(SpeechConstant.ACCENT, "mandarin");
        //获取当前语言（同理set对应get方法）
        Log.e(TAG, "last language:" + mIat.getParameter(SpeechConstant.LANGUAGE));
        //此处用于设置dialog中不显示错误码信息
        //mIat.setParameter("view_tips_plain","false");
        //开始录入音频后，音频后面部分最长静音时长，取值范围[0,10000ms]，默认值5000ms
        mIat.setParameter(SpeechConstant.VAD_BOS, "5000");
        // 设置语音后端点:后端点静音检测时间，即用户停止说话多长时间内即认为不再输入， 自动停止录音取值范围[0,10000ms]，默认值1800ms。
        mIat.setParameter(SpeechConstant.VAD_EOS, "1800");
        // 设置标点符号,设置为"0"返回结果无标点,设置为"1"返回结果有标点
        mIat.setParameter(SpeechConstant.ASR_PTT, "1");
        // 设置音频保存路径，保存音频格式支持pcm、wav，设置路径为sd卡请注意WRITE_EXTERNAL_STORAGE权限
        mIat.setParameter(SpeechConstant.AUDIO_FORMAT, "wav");
        mIat.setParameter(SpeechConstant.ASR_AUDIO_PATH, Environment.getExternalStorageDirectory() + "/msc/helloword.wav");
    }


    /**
     * 展示吐司
     */
    private void showToast(final String str) {
        Toast.makeText(this, str, Toast.LENGTH_SHORT).show();
    }

    private AlertDialog dialog;

    private void showAlertDialog() {
        dialog = new AlertDialog.Builder(this)
                .setTitle("自定弹框")//标题
                .setMessage("正在识别，请稍后...")//内容
                .setIcon(R.mipmap.ic_launcher)//图标
                .create();
        dialog.show();
    }

    private void showTip(final String str) {
        if (mToast != null) {
            mToast.cancel();
        }
        mToast = Toast.makeText(getApplicationContext(), str, Toast.LENGTH_SHORT);
        mToast.show();
    }
    //endregion

    //region 飞行控制器

    private void initDrone() {
        mCI.initFlightController();
        if (mCI.mFlightController != null) {
            mCI.setPhotoMode();
//            showFpvToast("Set up call bacsk");
            if (mCI.mFlightController.isVirtualStickControlModeAvailable()) {
                mBtnStop.setVisibility(View.VISIBLE);
            }

            mCI.mFlightController.setStateCallback(new FlightControllerState.Callback() {
                @Override
                public void onUpdate(@NonNull FlightControllerState flightControllerState) {
                    double mDroneLocationLat = flightControllerState.getAircraftLocation().getLatitude();
                    double mDroneLocationLng = flightControllerState.getAircraftLocation().getLongitude();
                    mDroneLocation = new LatLng(mDroneLocationLat, mDroneLocationLng);
                    mDroneHeading = mCI.mFlightController.getCompass().getHeading();
//                    updateDroneLocation();
                    // set flight data
                    mAltitudeData = (double) flightControllerState.getAircraftLocation().getAltitude(); // - initAltitude;
//                    if (mAltitudeData < 18) {
//                        mAltitudeData = (double) flightControllerState.getUltrasonicHeightInMeters();
//                    }
                    mhs = Math.sqrt(flightControllerState.getVelocityX() * flightControllerState.getVelocityX()
                            + flightControllerState.getVelocityY() * flightControllerState.getVelocityY());
                    mvs = -1 * flightControllerState.getVelocityZ();
                    mdistToHome = Utils.calcDistance(mUserLocation.latitude, mUserLocation.longitude, mDroneLocation.latitude, mDroneLocation.longitude);
                    updateFlightData();
                }
            });

//            mTest.setOnClickListener(new View.OnClickListener() {
//                @Override
//                public void onClick(View v) {
//                    mCI.shootPhoto();
//                }
//            });

            // set up battery
            mCI.aircraft.getBattery().setStateCallback(new BatteryState.Callback() {
                @Override
                public void onUpdate(BatteryState batteryState) {
                    mBatteryPercent = batteryState.getChargeRemainingInPercent();
                    mBatteryView.setProgress(mBatteryPercent);
                    updateBatteryStatus();
                }
            });
        }

    }

    private void updateConnection() {
//        boolean ret = false;
        BaseProduct product = DJISampleApplication.getProductInstance();
        if (product != null) {
            if (product.isConnected()) {
                //The product is connected
                showFpvToast(DJISampleApplication.getProductInstance().getModel() + " Connected");
//                ret = true;
            } else {
                if (product instanceof Aircraft) {
                    Aircraft aircraft = (Aircraft) product;
                    if (aircraft.getRemoteController() != null && aircraft.getRemoteController().isConnected()) {
                        // The product is not connected, but the remote controller is connected
                        showFpvToast("only RC Connected");
//                        ret = true;
                    }
                }
            }
        }

//        if (!ret) {
//            // The product or the remote controller are not connected.
//            showFpvToast("Disconnected");
//        }
    }

    protected BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            updateConnection();
            if (mCI.mFlightController == null) {
                initDrone();
            }
        }
    };

    /**
     * Update drone's distance, altitude, vertical speed, and horizontal speed
     * 更新无人机的距离，经纬度，竖直速度，水平速度
     */
    private void updateFlightData() {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mDistance.setText("D: " + new DecimalFormat("####").format(mdistToHome) + "m");
                mAltitude.setText("H: " + new DecimalFormat("###.#").format(mAltitudeData) + "m");
                mVerSpeed.setText("V.S: " + new DecimalFormat("##.#").format(mvs) + "m/s");
                mHorSpeed.setText("H.S: " + new DecimalFormat("##.#").format(mhs) + "m/s");
            }
        });
    }

    /**
     * Update battery level
     * 更新电池状态
     */
    private void updateBatteryStatus() {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mBatteryData.setText(Integer.toString(mBatteryPercent) + "%");
            }
        });
    }

    /**
     * 执行虚拟摇杆控制器
     */
    private void callExecution(ArrayList<Integer> encoded_string) {
        mEncodedStr = encoded_string;
        boolean success = false;
        if (mCI != null) {
            mCI.initFlightController();
        }
        if (mCI.mFlightController != null) {
            mCI.executeCmd(mEncodedStr);
            success = true;
        }
        if (success) {
            showFpvToast("Instruction Sent");
        } else {
            showFpvToast("Flight Control Error");
        }
    }

    /**
     * Search for place and sort the result by distance to the drone
     */
    private List<Address> addressList = null;
    private LatLng[] locList;
    private boolean addressList_flag = true;

    public void searchPlace(String locationName) {
        Geocoder mGeocoder = new Geocoder(mContext);
        int maxResults = 20;
        double lowerLeftLatitude = mDroneLocation.latitude - 0.05;
        double lowerLeftLongitude = mDroneLocation.longitude - 0.05;
        double upperRightLatitude = mDroneLocation.latitude + 0.05;
        double upperRightLongitude = mDroneLocation.longitude + 0.05;
//        double lowerLeftLatitude = mUserLocation.latitude - 0.05;
//        double lowerLeftLongitude = mUserLocation.longitude - 0.05;
//        double upperRightLatitude = mUserLocation.latitude + 0.05;
//        double upperRightLongitude = mUserLocation.longitude + 0.05;
        try {
            addressList = mGeocoder.getFromLocationName(locationName, maxResults, lowerLeftLatitude, lowerLeftLongitude, upperRightLatitude, upperRightLongitude);
        } catch (IOException e) {
            Log.e(TAG, e.toString());
            addressList_flag = false;
        }
        if (addressList_flag && addressList.size() != 0) {
            Bundle args = new Bundle();
            String[] places = new String[addressList.size()];
            double[] dist = new double[addressList.size()];
            LatLng[] cdArray = new LatLng[addressList.size()];
            for (int i = 0; i < addressList.size(); i++) {
                String sb = "";
                for (int k = 0; k < addressList.get(i).getMaxAddressLineIndex(); k++) {
                    sb += addressList.get(i).getAddressLine(k);
                    sb += "; ";
                }
                double lat = addressList.get(i).getLatitude();
                double lon = addressList.get(i).getLongitude();
                LatLng currentCd = new LatLng(lat, lon);
                double distance = Utils.calcDistance(mDroneLocation.latitude, mDroneLocation.longitude, lat, lon);
                sb += new DecimalFormat("####").format(distance) + "m";
                places[i] = sb;
                dist[i] = distance;
                cdArray[i] = currentCd;
                for (int j = i - 1; j >= 0; j--) {
                    if (dist[j + 1] < dist[j]) {
                        double t1 = dist[j];
                        String t2 = places[j];
                        LatLng t3 = cdArray[j];
                        dist[j] = dist[j + 1];
                        places[j] = places[j + 1];
                        cdArray[j] = cdArray[j + 1];
                        dist[j + 1] = t1;
                        places[j + 1] = t2;
                        cdArray[j + 1] = t3;
                    }
                }
            }
            locList = cdArray;
            args.putStringArray("places", places);
            mPlaceListFragment = new PlaceListFragment();
            mPlaceListFragment.setArguments(args);
            Log.e(TAG, mPlaceListFragment.getArguments().toString());
            getSupportFragmentManager().beginTransaction().add(R.id.main_layout, mPlaceListFragment).commit();
        } else {
            showFpvToast("No result available");
            Log.e(TAG, "No result available");
            addressList_flag = true;
        }
    }

    /**
     * Get a specific place's coordinates, encode it, and call (fly to) execution
     */
    public void getPlaceCoordinates(int index) {
        getSupportFragmentManager().beginTransaction().remove(mPlaceListFragment).commit();
        double lat = locList[index].latitude;
        double lon = locList[index].longitude;
        LatLng targetLatLng = new LatLng(lat, lon);
//        showFpvToast(targetLatLng.toString());
        addressList = null;
        locList = null;

        int latInt = (int) lat;
        int latDeci = (int) ((lat - latInt) * 100000);
        int lonInt = (int) lon;
        int lonDeci = (int) ((lon - lonInt) * 100000);

        ArrayList<Integer> temp = cc1.getEncodedString();
        temp.add(latInt);
        temp.add(latDeci);
        temp.add(lonInt);
        temp.add(lonDeci);

//        showFpvToast(temp.toString());

        callExecution(temp);
    }

    //endregion

    //region watson文字分类
    /**
     * Initialize Watson Service
     * 初始化watson service
     */
    private SpeechToText initSpeechToTextService() {
        SpeechToText service = new SpeechToText();
        String username = "23c90b4b-23ee-43cc-b0e9-97f36a0c0cfc";
        String password = "X5zb8Ub0WKsH";
        service.setUsernameAndPassword(username, password);
        service.setEndPoint("https://stream.watsonplatform.net/speech-to-text/api");
        return service;
    }

    /**
     * Audio service 语音录制结束时的回调函数
     */
    private class MicrophoneRecognizeDelegate implements RecognizeCallback {

        @Override
        public void onTranscription(SpeechResults speechResults) {
            System.out.println(speechResults);
            mStrIntention = speechResults.getResults().get(0).getAlternatives().get(0).getTranscript();
            showMicText(mStrIntention);
        }

        @Override
        public void onConnected() {

        }

        @Override
        public void onError(Exception e) {
            showError(e);
            // mTxtCmmand.setEnabled(true);
        }

        @Override
        public void onDisconnected() {
            // Tokenize command_in_text
            StringTokenizer st = new StringTokenizer(mStrIntention);
            ArrayList<String> tokenedCommand = new ArrayList<>();
            while (st.hasMoreTokens()) {
                tokenedCommand.add(st.nextToken());
            }
            // Replace mavic similar words
//            tokenedCommand = findMavicSimilar(tokenedCommand);
            // Change arraylist to string
            mStrIntention = TextUtils.join(" ", tokenedCommand);
            // Display command in string format
            showMicText(mStrIntention);
            // Execute NLC
            new VoiceControlActivity.ClassificationTask().execute(tokenedCommand);
        }
    }

    /**
     * Recognize Options 识别选项
     */
    private RecognizeOptions getRecognizeOptions() {
        return new RecognizeOptions.Builder()
                .continuous(true)
                .contentType(ContentType.OPUS.toString())
                .model("en-US_BroadbandModel")
                .interimResults(true)
                .customizationId("bf8c3a80-fba6-11e6-a1e7-a139b48a88e5")
                .inactivityTimeout(3000)
                .smartFormatting(true)
                .build();
    }

    /**
     * Classification Service 声音分类服务
     */
//    private class ClassificationTask extends AsyncTask<ArrayList, Void, String> {
//        @Override
//        protected String doInBackground(ArrayList... params) {
//            String result = null;
//            if (params[0].size() != 0) {
//                // call WatsonCommandClassifier to classify into 利用分类器进行命令的编码
//                cc1.classify(params[0]);
//                // show execution confirmation dialog fragment 确定窗口 并执行回调函数，如果确定，那么就进行任务执行
//                showDialog(rootView.findViewById(android.R.id.content));
//
//                result = "Did classify";
//            } else {
//                result = "Not classify";
//            }
//            return result;
//
//        }
//    }
    public class ClassificationTask {

        private final ExecutorService executorService;
        private final Handler mainHandler;

        public ClassificationTask() {
            this.executorService = Executors.newSingleThreadExecutor();
            this.mainHandler = new Handler(Looper.getMainLooper());
        }

        public void execute(ArrayList params) {
            String result = doInBackground(params);
            // Post result back to main thread
        }

        private String doInBackground(ArrayList... params) {
            String result = null;
            if (params[0].size() != 0) {
                // call WatsonCommandClassifier to classify into 利用分类器进行命令的编码
                cc1.classify(params[0],language);
                // show execution confirmation dialog fragment 确定窗口 并执行回调函数，如果确定，那么就进行任务执行
                showDialog(findViewById(android.R.id.content));

                result = "Did classify";
            } else {
                result = "Not classify";
            }
            return result;
        }

        private void onPostExecute(String result) {
            // Handle the result on the main thread (if needed)
            // For example, update UI or log the result
            System.out.println(result);
        }

        // Clean up resources when no longer needed
        public void shutdown() {
            executorService.shutdown();
        }
    }

    /**
     * Prepare Encoded String
     * 预处理命令编码
     */
    private ArrayList<Integer> mEncodedStr;

    private void preCheck(ArrayList<Integer> encoded_string, String google_map_string) {
        // Get first and see if it is adnvacce mission
        if (encoded_string.get(0) == 107) {
            searchPlace(google_map_string);
        } else {
            callExecution(encoded_string);
        }
    }

    private void writeRecogRecord(boolean pos, String s2tStr, String encodedStr, String classifiedStr) {
        String group = "neg";
        if (pos) {
            group = "pos";
        }
//        String key = mDBRecog.child(group).push().getKey();
//        mDBRecog.child(group).child(key).child("s2tStr").setValue(s2tStr);
//        mDBRecog.child(group).child(key).child("classifiedStr").setValue(classifiedStr);
//        mDBRecog.child(group).child(key).child("encodedStr").setValue(encodedStr);
    }
    /**
     * END of Prepare Encoded String
     */

    /**
     * Confirmation box
     */
    // 显示命令确认窗口
    public void showDialog(View v) {
        // create FragmentManager and CommandConfirmationDialogFragment
        FragmentManager manager = getSupportFragmentManager();
        CommandConfirmationDialogFragment myDialogFragment = new CommandConfirmationDialogFragment();
        // send encoded_string and command into pop up window
        Bundle bundle = new Bundle();

        bundle.putString("encoded_string", cc1.getEncodedString().toString());
        bundle.putString("command", cc1.getCommand());
        myDialogFragment.setArguments(bundle);
        // show pop up window
        myDialogFragment.show(manager, "MyDialogFragment");
    }
    //endregion




}