package com.dji.sdk.voice_control.internal.controller.voice_control;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;



import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.dji.sdk.voice_control.internal.controller.DJISampleApplication;
import com.dji.sdk.voice_control.internal.controller.Utils;
import com.dji.sdk.voice_control.internal.controller.flightcontrol.CommandInterpreter;
import com.dji.sdk.voice_control.internal.controller.flightcontrol.MyVirtualStickExecutor;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
//import com.google.firebase.database.DatabaseReference;
//import com.google.firebase.database.FirebaseDatabase;

//Ibm语音识别
import com.ibm.watson.developer_cloud.android.library.audio.MicrophoneInputStream;
import com.ibm.watson.developer_cloud.android.library.audio.utils.ContentType;
import com.ibm.watson.developer_cloud.speech_to_text.v1.SpeechToText;
import com.ibm.watson.developer_cloud.speech_to_text.v1.model.RecognizeOptions;
import com.ibm.watson.developer_cloud.speech_to_text.v1.model.SpeechResults;
import com.ibm.watson.developer_cloud.speech_to_text.v1.websocket.RecognizeCallback;

//讯飞语音识别
import com.iflytek.cloud.RecognizerResult;
import com.iflytek.cloud.SpeechUtility;
import com.iflytek.cloud.ErrorCode;
import com.iflytek.cloud.InitListener;
import com.iflytek.cloud.LexiconListener;
import com.iflytek.cloud.RecognizerListener;
import com.iflytek.cloud.RecognizerResult;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechRecognizer;
import com.iflytek.cloud.ui.RecognizerDialog;
import com.iflytek.cloud.ui.RecognizerDialogListener;
import com.dji.sdk.voice_control.internal.setting.IatSettings;
import com.dji.sdk.voice_control.internal.utils.FucUtil;
import com.dji.sdk.voice_control.internal.utils.JsonParser;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import dji.common.battery.BatteryState;
import dji.common.flightcontroller.FlightControllerState;
import dji.sdk.base.BaseProduct;
import dji.sdk.products.Aircraft;

import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import com.dji.sdk.voice_control.R;

/**
 * FPV main control UI
 *
 */
public class FPVFullscreenActivity extends FragmentActivity implements CommandConfirmationDialogFragment.Communicator {
    //静态字符串 TAG 标签， 这个活动的标签
    public static final String TAG = FPVFullscreenActivity.class.getName();

    //该活动的实例
    private Context mContext;

    //命令行交互
    private CommandInterpreter mCI;

    //数据库
//    private FirebaseDatabase mDatabase;
//    private DatabaseReference mDBRecog;

    // Map
    private View mMapView;
    private LatLng mDroneLocation = new LatLng(0, 0);
    private float mDroneHeading = 0;
    private Marker mDroneMarker = null;
    //    private LocationManager mLocationManager;
    //    private LocationListener mLocationListener;
    private LatLng mUserLocation = new LatLng(0, 0);
    //定位
    private Button mBtnLoacte;
    private boolean mMapLocate_flag = true;
    //追踪
    private Button mBtnTracking;
    private boolean mMapTracking_flag = true;

    private PlaceListFragment mPlaceListFragment;


    // IBM watson varaibles
    private WatsonCommandClassifier cc1;
    private SpeechToText speechService;
    private MicrophoneInputStream capture;
    private String mStrIntention;

    // 科大讯飞
    // 语音听写对象
    private SpeechRecognizer mIat;
    // 语音听写UI
    private RecognizerDialog mIatDialog;
    // 用HashMap存储听写结果
    private HashMap<String, String> mIatResults = new LinkedHashMap<>();
    // 引擎类型
    private String mEngineType = SpeechConstant.TYPE_CLOUD;
    private String[] languageEntries;
    private String[] languageValues;
    private String language = "zh_cn";
    private int selectedNum = 0;
    private String resultType = "json";
    private StringBuffer buffer = new StringBuffer();

    // APP的UI和视图
    private EditText mResultText;
    private EditText showContacts;
    private TextView languageText;
    private Toast mToast;
    private SharedPreferences mSharedPreferences;
    private TextureView fpvTexture; //视频播放
    private Button mBtnInput;
    private boolean mBtnInput_flag = true;
    private EditText mTxtCmmand;
    private Button mBtnStop;
    private Button mBtnDummy;
    private Button mBtnDummyMap;
    private boolean mBtnDummyMap_flag = true;
    private Button mBtnShow;
    private Button mBtnHide;

    //Aircraft State 文字显示飞机的状态
    private TextView mAltitude;
    private TextView mVerSpeed;
    private TextView mHorSpeed;
    private TextView mDistance;
    //private TextView mDistance; 存储飞机数据
    private double mAltitudeData;
    private double mvs;
    private double mhs;
    private double mdistToHome;
    //Battery 电池状态
    BatteryView mBatteryView;
    private TextView mBatteryData;
    private int mBatteryPercent;

    // Retrieve and Rank fragment
    private RARFragment rarFragment;
    private boolean rarFlag;
    private Button mRandR;
    private View rootView;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        // When the compile and target version is higher than 22, please request the
        // following permissions at runtime to ensure the
        // SDK work well.
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

        // set up fpv
        mContext = this;
        fpvTexture = new TextureView(mContext);
        fpvTexture.setSurfaceTextureListener(new BaseFpvView(mContext));
        setContentView(fpvTexture);

        // set up UI
        LayoutInflater layoutInflater = getLayoutInflater();
        View content = layoutInflater.inflate(R.layout.activity_fpvfullscreen, null, false);
        RelativeLayout.LayoutParams rlParam = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        addContentView(content, rlParam);
        // set up 科大讯飞
        SpeechUtility.createUtility(this, SpeechConstant.APPID +"=12cecf5e");
        mIat = SpeechRecognizer.createRecognizer(this, mInitListener);

        //初始化 百度语音
        initUI();

        // set up Watson
        speechService = initSpeechToTextService();
        cc1 = new WatsonCommandClassifier();

        // 初始化听写Dialog，如果只使用有UI听写功能，无需创建SpeechRecognizer
        // 使用UI听写功能，请根据sdk文件目录下的notice.txt,放置布局文件和图片资源
        mIatDialog = new RecognizerDialog(this, mInitListener);

        // set up controller
        mCI = CommandInterpreter.getUniqueInstance(mContext);
        initDrone();

        // Register the broadcast receiver for receiving the device connection's changes.
        IntentFilter filter = new IntentFilter();
        filter.addAction(DJISampleApplication.FLAG_CONNECTION_CHANGE);
        registerReceiver(mReceiver, filter);


        // Set up firebase
//        mDatabase = FirebaseDatabase.getInstance("Users");
//        mDBRecog = mDatabase.getReference("recog");


        Log.e(TAG, "onCreate");

    }

    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_fpvfullscreen, container, false);
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onResume() {
        Log.e(TAG, "onResume");
        super.onResume();
        updateConnection();
        initDrone();
    }

    @Override
    public void onPause() {
        Log.e(TAG, "onPause");
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
        super.onDestroy();
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

    /**
     * 初始化监听器。
     */
    private InitListener mInitListener = new InitListener() {

        @Override
        public void onInit(int code) {
            Log.d(TAG, "SpeechRecognizer init() code = " + code);
            if (code != ErrorCode.SUCCESS) {
                Toast.makeText(mContext, "初始化失败，错误码：" + code + ",请点击网址https://www.xfyun.cn/document/error-code查询解决方案", Toast.LENGTH_LONG).show();
            }
        }
    };

    /**
     * 在线听写支持多种小语种设置。支持语言类型如下：
     * <item>zh_cn</item> 中文
     * <item>en_us</item> 英文
     * <item>ja_jp</item> 日语
     * <item>ru-ru</item> 俄语
     * <item>es_es</item> 西班牙语
     * <item>fr_fr</item> 法语
     * <item>ko_kr</item> 韩语
     *
     * @param v
     */
    private void setLanguage(View v) {
        new AlertDialog.Builder(v.getContext()).setTitle("语种语言种类")
                .setSingleChoiceItems(languageEntries, // 单选框有几项,各是什么名字
                        0, // 默认的选项
                        new DialogInterface.OnClickListener() { // 点击单选框后的处理
                            public void onClick(DialogInterface dialog,
                                                int which) { // 点击了哪一项
                                language = languageValues[which];
                                ((TextView) findViewById(R.id.languageText)).setText("你选择的是：" + languageEntries[which]);
                                selectedNum = which;
                                dialog.dismiss();
                            }
                        }).show();
        mIat.setParameter(SpeechConstant.LANGUAGE, language);
    }


    /**
     * 听写监听器。
     */
    private RecognizerListener mRecognizerListener = new RecognizerListener() {


        @Override
        public void onBeginOfSpeech() {
            // 此回调表示：sdk内部录音机已经准备好了，用户可以开始语音输入
            showTip("开始说话");
        }

        @Override
        public void onError(SpeechError error) {
            // Tips：
            // 错误码：10118(您没有说话)，可能是录音机权限被禁，需要提示用户打开应用的录音权限。
            Log.d(TAG, "onError " + error.getPlainDescription(true));
            showTip(error.getPlainDescription(true));

        }

        @Override
        public void onEndOfSpeech() {
            // 此回调表示：检测到了语音的尾端点，已经进入识别过程，不再接受语音输入
            showTip("结束说话");
        }

        @Override
        public void onResult(RecognizerResult results, boolean isLast) {
            Log.d(TAG, results.getResultString());
            if (isLast) {
                Log.d(TAG, "onResult 结束");
            }
            if (resultType.equals("json")) {
                printResult(results);
                return;
            }
            if (resultType.equals("plain")) {
                buffer.append(results.getResultString());
                mResultText.setText(buffer.toString());
                mResultText.setSelection(mResultText.length());
            }


            mStrIntention = buffer.toString();
            showMicText(mStrIntention);

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
            new ClassificationTask().execute(tokenedCommand);
            // 任务拆解
            //openai
            //xunfei
            // 让无人机向上飞50米

        }

        @Override
        public void onVolumeChanged(int volume, byte[] data) {
            showTip("当前正在说话，音量大小 = " + volume + " 返回音频数据 = " + data.length);
        }

        @Override
        public void onEvent(int eventType, int arg1, int arg2, Bundle obj) {
            // 以下代码用于获取与云端的会话id，当业务出错时将会话id提供给技术支持人员，可用于查询会话日志，定位出错原因
            // 若使用本地能力，会话id为null
            //	if (SpeechEvent.EVENT_SESSION_ID == eventType) {
            //		String sid = obj.getString(SpeechEvent.KEY_EVENT_SESSION_ID);
            //		Log.d(TAG, "session id =" + sid);
            //	}
        }
    };

    /**
     * 显示结果
     */
    private void printResult(RecognizerResult results) {
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
        mResultText.setText(resultBuffer.toString());
        mResultText.setSelection(mResultText.length());
    }

    /**
     * 听写UI监听器
     */
    private RecognizerDialogListener mRecognizerDialogListener = new RecognizerDialogListener() {
        // 返回结果
        public void onResult(RecognizerResult results, boolean isLast) {
            printResult(results);
            mStrIntention = buffer.toString();
            showMicText(mStrIntention);

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
            new ClassificationTask().execute(tokenedCommand);
        }

        // 识别回调错误
        public void onError(SpeechError error) {
            showTip(error.getPlainDescription(true));
        }

    };


    private void showTip(final String str) {
        if (mToast != null) {
            mToast.cancel();
        }
        mToast = Toast.makeText(getApplicationContext(), str, Toast.LENGTH_SHORT);
        mToast.show();
    }

    /**
     * 参数设置
     *
     * @return
     */
    public void setParam() {
        // 清空参数
        mIat.setParameter(SpeechConstant.PARAMS, null);
        // 设置听写引擎
        mIat.setParameter(SpeechConstant.ENGINE_TYPE, mEngineType);
        // 设置返回结果格式
        mIat.setParameter(SpeechConstant.RESULT_TYPE, resultType);

        if (language.equals("zh_cn")) {
            String lag = mSharedPreferences.getString("iat_language_preference",
                    "mandarin");
            // 设置语言
            Log.e(TAG, "language = " + language);
            mIat.setParameter(SpeechConstant.LANGUAGE, "zh_cn");
            // 设置语言区域
            mIat.setParameter(SpeechConstant.ACCENT, lag);
        } else {
            mIat.setParameter(SpeechConstant.LANGUAGE, language);
        }
        Log.e(TAG, "last language:" + mIat.getParameter(SpeechConstant.LANGUAGE));

        //此处用于设置dialog中不显示错误码信息
        //mIat.setParameter("view_tips_plain","false");

        // 设置语音前端点:静音超时时间，即用户多长时间不说话则当做超时处理
        mIat.setParameter(SpeechConstant.VAD_BOS, mSharedPreferences.getString("iat_vadbos_preference", "4000"));

        // 设置语音后端点:后端点静音检测时间，即用户停止说话多长时间内即认为不再输入， 自动停止录音
        mIat.setParameter(SpeechConstant.VAD_EOS, mSharedPreferences.getString("iat_vadeos_preference", "1000"));

        // 设置标点符号,设置为"0"返回结果无标点,设置为"1"返回结果有标点
        mIat.setParameter(SpeechConstant.ASR_PTT, mSharedPreferences.getString("iat_punc_preference", "1"));

        // 设置音频保存路径，保存音频格式支持pcm、wav.
        mIat.setParameter(SpeechConstant.AUDIO_FORMAT, "wav");
        mIat.setParameter(SpeechConstant.ASR_AUDIO_PATH,
                getExternalFilesDir("msc").getAbsolutePath() + "/iat.wav");
    }


    private void initUI(){

        mTxtCmmand = (EditText) findViewById(R.id.command_text);
        mBtnInput = (Button) findViewById(R.id.input_btn);
        mBtnStop = (Button) findViewById(R.id.stop_btn);
        mBtnDummy = (Button) findViewById(R.id.dummy_btn);
//        mBtnDummyMap = (Button) findViewById(R.id.dummy_map_btn);
        mBtnShow = (Button) findViewById(R.id.show_btn);
        mBtnHide = (Button) findViewById(R.id.hide_btn);
        mBtnShow.setVisibility(View.GONE);
        mBtnLoacte = (Button) findViewById(R.id.locate_button);
        mBtnTracking = (Button) findViewById(R.id.tracking_button);
        mBtnLoacte.setVisibility(View.GONE);
        mBtnTracking.setVisibility(View.GONE);
        mBatteryView = (BatteryView) findViewById(R.id.battery_view);
        mBatteryData = (TextView) findViewById(R.id.battery_data);
        mAltitude = (TextView) findViewById(R.id.Altitude);
        mVerSpeed = (TextView) findViewById(R.id.VerticalSpeed);
        mDistance = (TextView) findViewById(R.id.Distance);
        mHorSpeed = (TextView) findViewById(R.id.HorizonSpeed);
//        mDistance = (TextView) findViewById(R.id.Distance);
        mRandR = (Button) findViewById(R.id.RR_Button);
//        mTest = (Button) findViewById(R.id.testBtn);

        stopBtnListener();
        voiceInputListener();
        inputBtnListener();
//        mapBtnListener();
//        showHideBtnListener();
//        locateTrackBtnListener();
        RRInputListener();
        findViewById(R.id.iat_recognize).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 设置参数
                setParam();
                boolean isShowDialog = mSharedPreferences.getBoolean(
                        getString(R.string.pref_key_iat_show), true);
                if (isShowDialog) {
                    // 显示听写对话框
                    mIatDialog.setListener(mRecognizerDialogListener);
                    mIatDialog.show();
                    showTip(getString(R.string.text_begin));
                } else {
                    // 不显示听写对话框
                    ret = mIat.startListening(mRecognizerListener);
                    if (ret != ErrorCode.SUCCESS) {
                        showTip("听写失败,错误码：" + ret + ",请点击网址https://www.xfyun.cn/document/error-code查询解决方案");
                    } else {
                        showTip(getString(R.string.text_begin));
                    }
                }
            }
        });
        findViewById(R.id.iat_stop).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mIat.stopListening();
                showTip("停止听写");
            }
        });
        findViewById(R.id.iat_cancel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mIat.cancel();
                showTip("取消听写");
            }
        });
        findViewById(R.id.image_iat_set).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intents = new Intent(mContext, IatSettings.class);
                startActivity(intents);
            }
        });

        findViewById(R.id.languageText).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setLanguage(v);
            }
        });
    }


    private int dpToPix(int dps) {
        final float scale = mContext.getResources().getDisplayMetrics().density;
        return (int) (dps * scale + 0.5f);
    }

    private static void sendViewToBack(final View child) {
        final ViewGroup parent = (ViewGroup) child.getParent();
        if (null != parent) {
            parent.removeView(child);
            parent.addView(child, 0);
        }
    }

    int ret = 0; // 函数调用返回值

    private void iat_setListener(){

    }

    @SuppressLint("ClickableViewAccessibility")
    private void voiceInputListener() {
        mBtnDummy.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    // Reset string command_text input to null
                    mStrIntention = null;
                    // Change button back ground color
                    mTxtCmmand.setBackgroundResource(R.drawable.common_google_signin_btn_text_dark_focused);
                    // Init MicrophoneInputStream and start watson speec-to-text websocket
                    capture = new MicrophoneInputStream(true);
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                buffer.setLength(0);
                                mResultText.setText(null);// 清空显示内容
                                mIatResults.clear();
                                // 设置参数
                                setParam();
                                boolean isShowDialog = mSharedPreferences.getBoolean(
                                        getString(R.string.pref_key_iat_show), true);
                                if (isShowDialog) {
                                    // 显示听写对话框
                                    mIatDialog.setListener(mRecognizerDialogListener);
                                    mIatDialog.show();
                                    showTip(getString(R.string.text_begin));
                                } else {
                                    // 不显示听写对话框
                                    ret = mIat.startListening(mRecognizerListener);
                                    if (ret != ErrorCode.SUCCESS) {
                                        showTip("听写失败,错误码：" + ret + ",请点击网址https://www.xfyun.cn/document/error-code查询解决方案");
                                    } else {
                                        showTip(getString(R.string.text_begin));
                                    }
                                }
                            } catch (Exception e) {
                                showError(e);
                            }
                        }
                    }).start();
                    break;
                case MotionEvent.ACTION_UP:
                    // Change button back ground color
                    mTxtCmmand.setBackgroundResource(R.drawable.common_google_signin_btn_text_dark_normal);
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
//                    mTxtCmmand.addTextChangedListener(new TextWatcher() {
//                        @Override
//                        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
//
//                        }
//
//                        @Override
//                        public void onTextChanged(CharSequence s, int start, int before, int count) {
//
//                        }
//
//                        @Override
//                        public void afterTextChanged(Editable s) {
//
//                        }
//                    });
                }
                else {
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
                    new ClassificationTask().execute(tokenedCommand);
                    mTxtCmmand.setText("");
                    mTxtCmmand.setHint("Hold for Voice Input");
                    mTxtCmmand.setEnabled(false);
                    mBtnDummy.setVisibility(View.VISIBLE);
                    mBtnInput_flag = true;
                }
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

    //    private double initAltitude = 0;
//    private boolean altiFlag = true;

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

    protected BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            updateConnection();
            if (mCI.mFlightController == null) {
                initDrone();
            }
        }
    };

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


    public static boolean checkGpsCoordination(double latitude, double longitude) {
        return (latitude > -90 && latitude < 90 && longitude > -180 && longitude < 180) && (latitude != 0f && longitude != 0f);
    }

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
            new ClassificationTask().execute(tokenedCommand);
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
    private class ClassificationTask extends AsyncTask<ArrayList, Void, String> {
        @Override
        protected String doInBackground(ArrayList... params) {
            String result = null;
            if (params[0].size() != 0) {
                // call WatsonCommandClassifier to classify into 利用分类器进行命令的编码
                cc1.classify(params[0]);
                // show execution confirmation dialog fragment 确定窗口 并执行回调函数，如果确定，那么就进行任务执行
                showDialog(rootView.findViewById(android.R.id.content));

                result = "Did classify";
            } else {
                result = "Not classify";
            }
            return result;

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

    // exectue based on user feedback from command confirmation window
    // 基于用户的反馈执行不同命令
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

    /**
     * END of Confirmation box
     */
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
}
