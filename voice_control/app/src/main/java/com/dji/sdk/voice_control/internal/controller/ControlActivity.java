package com.dji.sdk.voice_control.internal.controller;


import android.app.Dialog;
import android.content.res.Resources;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;
import com.amap.api.location.AMapLocationListener;
import com.amap.api.maps2d.AMap;
import com.amap.api.maps2d.CameraUpdateFactory;
import com.amap.api.maps2d.model.LatLng;
import com.amap.api.maps2d.model.Marker;
import com.amap.api.services.core.AMapException;
import com.amap.api.services.core.LatLonPoint;
import com.amap.api.services.core.ServiceSettings;
import com.amap.api.services.geocoder.GeocodeAddress;
import com.amap.api.services.geocoder.GeocodeQuery;
import com.amap.api.services.geocoder.GeocodeResult;
import com.amap.api.services.geocoder.GeocodeSearch;
import com.amap.api.services.geocoder.RegeocodeAddress;
import com.amap.api.services.geocoder.RegeocodeResult;
import com.amap.api.maps2d.AMap.OnMapClickListener;
import com.amap.api.maps2d.CameraUpdate;
import com.amap.api.maps2d.MapView;
import com.amap.api.maps2d.model.BitmapDescriptorFactory;
import com.amap.api.maps2d.model.MarkerOptions;
import com.amap.apis.utils.core.api.AMapUtilCoreApi;
import com.dji.sdk.voice_control.R;
import com.dji.sdk.voice_control.demo.camera.PlaybackCommandsView;
import com.dji.sdk.voice_control.internal.controller.adapter.ChatListAdapter;
import com.dji.sdk.voice_control.internal.controller.agent.JsonUtils;
import com.dji.sdk.voice_control.internal.controller.chatgpt.ChatMessageData;
import com.dji.sdk.voice_control.internal.controller.chatgpt.Constant;
import com.dji.sdk.voice_control.internal.controller.chatgpt.GPTS;
import com.dji.sdk.voice_control.internal.controller.chatgpt.GPTSCallback;
import com.dji.sdk.voice_control.internal.controller.chatgpt.IChatMessageData;
import com.dji.sdk.voice_control.internal.controller.chatgpt.IJSONMessage;
import com.dji.sdk.voice_control.internal.controller.chatgpt.JSONMessage;
import com.dji.sdk.voice_control.internal.controller.flightcontrol.CommandInterpreter;
import com.dji.sdk.voice_control.internal.controller.flightcontrol.FlightData;
import com.dji.sdk.voice_control.internal.controller.flightcontrol.MyVirtualStickExecutor;
import com.dji.sdk.voice_control.internal.controller.voice_control.BaseRtspFpvView;
import com.dji.sdk.voice_control.internal.controller.voice_control.BatteryView;
import com.dji.sdk.voice_control.internal.controller.voice_control.CommandClassifier;
import com.dji.sdk.voice_control.internal.controller.voice_control.CommandConfirmationDialogFragment;
import com.dji.sdk.voice_control.internal.controller.waypoint.Waypoint2Activity;

import dji.common.util.DJIParamMinMaxCapability;
import dji.sdk.flightcontroller.FlightAssistant;
import dji.sdk.gimbal.Gimbal;
import dji.sdk.sdkmanager.LiveStreamManager;
import com.dji.sdk.voice_control.internal.utils.AMapUtil;
import com.dji.sdk.voice_control.internal.utils.CallbackHandlers;
import com.dji.sdk.voice_control.internal.utils.JsonParser;
import com.dji.sdk.voice_control.internal.utils.ToastUtil;
import com.dji.sdk.voice_control.internal.utils.ToastUtils;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.tabs.TabLayout;
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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.security.acl.Owner;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import dji.common.battery.BatteryState;
import dji.common.error.DJIError;
import dji.common.error.DJISDKError;
import dji.common.flightcontroller.FlightControllerState;
import dji.common.flightcontroller.simulator.InitializationData;
import dji.common.flightcontroller.simulator.SimulatorState;
import dji.common.flightcontroller.virtualstick.FlightControlData;
import dji.common.flightcontroller.virtualstick.FlightCoordinateSystem;
import dji.common.flightcontroller.virtualstick.RollPitchControlMode;
import dji.common.flightcontroller.virtualstick.VerticalControlMode;
import dji.common.flightcontroller.virtualstick.YawControlMode;
import dji.common.mission.waypoint.WaypointMissionHeadingMode;
import dji.common.mission.waypointv2.WaypointV2;
import dji.common.mission.waypointv2.WaypointV2MissionTypes;
import dji.common.model.LocationCoordinate2D;
import dji.common.useraccount.UserAccountState;
import dji.common.util.CommonCallbacks;
import dji.log.DJILog;
import dji.sdk.base.BaseComponent;
import dji.sdk.base.BaseProduct;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.mission.MissionControl;
import dji.sdk.mission.waypoint.WaypointV2MissionOperator;
import dji.sdk.products.Aircraft;
import 	dji.common.gimbal.*;
import dji.sdk.sdkmanager.DJISDKInitEvent;
import dji.sdk.sdkmanager.DJISDKManager;
import dji.sdk.useraccount.UserAccountManager;
import dji.sdk.base.DJIDiagnostics;

//RTSP推流
import kr.co.makeitall.rtspserver.RtspServer;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import com.pedro.rtsp.utils.ConnectCheckerRtsp;

public class ControlActivity extends AppCompatActivity implements OnMapClickListener, View.OnClickListener ,CommandConfirmationDialogFragment.Communicator {

    //region 标记
    private boolean iscommond = false;
    private boolean iswaypoint = false;
    //endregion

    //region UI数据结构
    //诊断信息
    private StringBuilder diagnosticsMessage = new StringBuilder();
    //日志
    private static final String TAG = MainActivity.class.getName();
    //权限列表
    private static final String[] REQUIRED_PERMISSION_LIST = new String[]{
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.VIBRATE,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.CAMERA,
    };
    //缺失权限
    private List<String> missingPermission = new ArrayList<>();
    //是被注册在过程中
    private AtomicBoolean isRegistrationInProgress = new AtomicBoolean(false);
    private static final int REQUEST_PERMISSION_CODE = 12345;
    //UI控件
    protected TextView mConnectStatusTextView;
    private DrawerLayout drawerLayout;

    private CommandClassifier cc1;
    private Button mBtnLanguage2;
    private boolean languageType = false;
    private String language="zh_cn";

    //飞机的状态
    private TextView mAltitude;
    private TextView mVerSpeed;
    private TextView mHorSpeed;
    private TextView mDistance;
    //Battery 电池状态
    BatteryView mBatteryView;
    private TextView mBatteryData;
    private int mBatteryPercent;

    private TextView mTextView;

    //消息控件
    private Toast toast;
    //该活动的实例
    private Context mContext;
    //FPV
    private TextureView fpvTexture;

    //开始推流按钮
    private Boolean isStreaming = false;

    //视图切换
    private TabLayout mTabLayout;

    //侧边栏按钮
    private Button mBtnEnableVirtualStick;
    private Button mBtnDisableVirtualStick;
    private Button mBtnPhoto;
    private Button mBtnDownload;
    private Button mBtnWaypoint;
    private NavigationView navView;
    private ToggleButton mBtnSimulator;

    //手动控制
    private Button mFlightControlTab;
    //endregion

    //region 语音识别的数据结构
    // 语音听写对象
    private SpeechRecognizer mIat;
    // 语音听写UI
    private RecognizerDialog mIatDialog;
    // 用HashMap存储听写结果
    private HashMap<String, String> mIatResults = new LinkedHashMap<>();
    // 格式类型【默认json】
    private String resultType = "json";
    private boolean cyclic = false;//音频流识别是否循环调用
    //拼接字符串
    private StringBuffer buffer = new StringBuffer();
    //Handler码
    private int handlerCode = 0x123;
    // 函数调用返回值
    private int resultCode = 0;
    // 弹框是否显示
    private int dialogType;
    //endregion

    //region 地图数据结构
    //手机定位
    private AMapLocationClient mLocationClient;
    private AMapLocationClientOption mLocationOption;
    // Mapui
    private MapView mMapView;
    //高德地图API
    private AMap aMap;
    private LatLng mDroneLocation = new LatLng(0, 0);
    private float mDroneHeading = 0;
    private Marker mDroneMarker = null;
    private LatLng mUserLocation = new LatLng(0, 0);
    private LatLonPoint mTargetLocation = new LatLonPoint(0, 0);
    //定位
    private Button mBtnLoacte;
    private boolean mMapLocate_flag = true;
    //追踪
    private Button mBtnTracking;
    private boolean mMapTracking_flag = true;
    //是否添加
    private boolean isAdd = false;
    //航点
    private final Map<Integer, Marker> mMarkers = new ConcurrentHashMap<Integer, Marker>();
    private Marker droneMarker = null;
    private Marker userMarker = null;
    private List<WaypointV2> waypointList = new ArrayList<>();
    private class Place{
        public String name;
        public Double lat;
        public Double lon;
    }
    private List<Place> Places = new ArrayList<>();
    //endregion

    //region 航点数据结构
    private Waypoint mWaypoint;
    private WaypointV2MissionOperator mMissionOperator;
    private String mTargetDes;
    //endregion

    //region 飞行控制数据结构
    //飞行控制
    private MyVirtualStickExecutor mSingletonVirtualStickExecutor;
    //飞行数据更新任务
    private DroneDataUpdater droneDataUpdater;
    //飞行数据
    private FlightData mFlightData = new FlightData();

    //虚拟摇杆
    private OnScreenJoystick mScreenJoystickRight;
    private OnScreenJoystick mScreenJoystickLeft;

    //高德地图查询器
    GeocodeSearch geocoderSearch;
    private String addressName;

    private Timer mSendVirtualStickDataTimer;
    private SendVirtualStickDataTask mSendVirtualStickDataTask;
    //飞行数据
    private float mPitch;
    private float mRoll;
    private float mYaw;
    private float mThrottle;
    private String mStrIntention;
    private float droneHeading;
    //消息控制器
    private Handler mHandler;
    private DJISDKManager.SDKManagerCallback mDJISDKManagerCallback;
    public static final String FLAG_CONNECTION_CHANGE = "com_dji_simulatorDemo_connection_change";

    //命令行交互
    private CommandInterpreter mCI;

    //private TextView mDistance; 存储飞机数据
    private double mAltitudeData;
    private double mvs;
    private double mhs;
    private double mdistToHome;

    //手动控制标志
    private boolean iscontrol;
    //endregion

    //region 视频流RTSP数据结构
    private RtspServer rtspServer;
    private static final int RTSP_PORT = 5000;
    private LiveStream mLiveStream;

    //TEST
//    private MediaProjectionManager projectionManager;
//    private RtspServerDisplay rtspServerDisplay;
//    private static final int REQUEST_CODE_SCREEN_CAPTURE = 100;
    //endregion

    //region chatgpt UI数据结构
    private RecyclerView mRvChatList;
    private EditText mEtQuestion;
    private ChatListAdapter mListAdapter;
    public OkHttpClient client;
    private Button mSendBtn;
    private BaseRtspFpvView mBaseRtspFpvView;

    /**
     * 聊天信息数据
     */
    private IChatMessageData mChatMessageData;
    /**
     * JSON类型的上下文聊天数据
     */
    private IJSONMessage mJSONMessage;
    private JSONObject GPThistory;

    //是否启用GPT
    private Boolean isGPT =true;

    // 用于保存当前待确认的任务信息
    private String pendingCommand;
    private String pendingEncodedString;
    private String pendingTarget;
    //endregion

    //region 生命周期
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //消息控制器
        mHandler = new Handler(Looper.getMainLooper());

        //初始化DJISDK
        mDJISDKManagerCallback = new DJISDKManager.SDKManagerCallback() {

            //Listens to the SDK registration result
            @Override
            public void onRegister(DJIError error) {

                if(error == DJISDKError.REGISTRATION_SUCCESS) {

                    Handler handler = new Handler(Looper.getMainLooper());
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(getApplicationContext(), "Register Success", Toast.LENGTH_LONG).show();
                        }
                    });

                    DJISDKManager.getInstance().startConnectionToProduct();

                } else {

                    Handler handler = new Handler(Looper.getMainLooper());
                    handler.post(new Runnable() {

                        @Override
                        public void run() {
                            Toast.makeText(getApplicationContext(), "Register sdk fails, check network is available", Toast.LENGTH_LONG).show();
                        }
                    });

                }
                Log.e("TAG", error.toString());
            }

            @Override
            public void onProductDisconnect() {
                Log.d("TAG", "onProductDisconnect");
                notifyStatusChange();
            }
            @Override
            public void onProductConnect(BaseProduct baseProduct) {
                Log.d("TAG", String.format("onProductConnect newProduct:%s", baseProduct));
                notifyStatusChange();

            }

            @Override
            public void onProductChanged(BaseProduct baseProduct) {

            }

            @Override
            public void onComponentChange(BaseProduct.ComponentKey componentKey, BaseComponent oldComponent,
                                          BaseComponent newComponent) {
                if (newComponent != null) {
                    newComponent.setComponentListener(new BaseComponent.ComponentListener() {

                        @Override
                        public void onConnectivityChange(boolean isConnected) {
                            Log.d("TAG", "onComponentConnectivityChanged: " + isConnected);
                            notifyStatusChange();
                        }
                    });
                }

                Log.d("TAG",
                        String.format("onComponentChange key:%s, oldComponent:%s, newComponent:%s",
                                componentKey,
                                oldComponent,
                                newComponent));

            }
            @Override
            public void onInitProcess(DJISDKInitEvent djisdkInitEvent, int i) {

            }

            @Override
            public void onDatabaseDownloadProgress(long l, long l1) {

            }


        };

        //检查手机权限
        checkAndRequestPermissions();

        //初始化DJI推流和直播
        mContext = this;
        mLiveStream = new LiveStream(mContext);

        //初始化RTSP推流
        rtspServer = new RtspServer(connectCheckerRtsp, RTSP_PORT);

        //初始化FPV推流
        fpvTexture = new TextureView(mContext);
//        fpvTexture.setSurfaceTextureListener(new BaseFpvView(mContext));
        mBaseRtspFpvView = new BaseRtspFpvView(mContext,rtspServer);
        fpvTexture.setSurfaceTextureListener(mBaseRtspFpvView);

        //语音识别初始化
        SpeechUtility.createUtility(this, SpeechConstant.APPID +"=12cecf5e");

        //加载XML文件
        setContentView(R.layout.activity_fpvwaypoint);
//        projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
//        rtspServerDisplay = new RtspServerDisplay(FPVActivity.this,true,connectCheckerRtsp,RTSP_PORT);
//        Intent serviceIntent = new Intent(this, MediaProjectionService.class);
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            startForegroundService(serviceIntent);
//        } else {
//            startService(serviceIntent);
//        }

        // 使用SpeechRecognizer对象，可根据回调消息自定义界面；

        //初始化UI听写dialog
        mIat = SpeechRecognizer.createRecognizer(this, mInitListener);

        // 初始化听写Dialog
        mIatDialog = new RecognizerDialog(ControlActivity.this, mInitListener);

        //初始化地图
        mMapView = findViewById(R.id.map);
        mMapView.onCreate(savedInstanceState);

        // 将 TextureView 添加到容器中
        FrameLayout fpvContainer = findViewById(R.id.fpv_container);
        fpvContainer.addView(mLiveStream);
        fpvContainer.addView(fpvTexture);

        // 设置 TabLayout 切换监听
        mTabLayout = (TabLayout) findViewById(R.id.tab_layout);
        mTabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                switch (tab.getPosition()) {
                    case 0: // 地图视图
                        mMapView.setVisibility(View.VISIBLE);
                        fpvTexture.setVisibility(View.GONE);
                        mLiveStream.setVisibility(View.GONE);
                        break;
                    case 1: // FPV 视图
                        mMapView.setVisibility(View.GONE);
                        fpvTexture.setVisibility(View.VISIBLE);
                        mLiveStream.setVisibility(View.VISIBLE);
                        break;
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                // 可选：添加取消选择时的逻辑
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                // 可选：添加重新选择时的逻辑
            }
        });

        // 添加 Tab 项
        mTabLayout.addTab(mTabLayout.newTab().setText("地图视图"));
        mTabLayout.addTab(mTabLayout.newTab().setText("FPV视图"));

        //初始地理查询器
        ServiceSettings.updatePrivacyShow(this,true,true);
        ServiceSettings.updatePrivacyAgree(this,true);
        try {
            geocoderSearch = new GeocodeSearch(this);
        } catch (AMapException e) {
            throw new RuntimeException(e);
        }

        //初始化航点操作类
        mWaypoint = new Waypoint(ControlActivity.this);
        mMissionOperator = getWaypointMissionOperator(mMissionOperator);

        //实例化命令分类器
        cc1 = new CommandClassifier();

        // 初始化命令交互控制器
        mCI = CommandInterpreter.getUniqueInstance(mContext);

        //初始化UI事件
        initUI();

        //初始化无人机飞控
        initFlightController();

        droneDataUpdater = new DroneDataUpdater();
        //更新无人机数据
        if(mCI.mFlightController!=null){
            droneDataUpdater.startUpdatingData();
        }

        //初始化用户定位
        initLocation();

        //初始化chatgpt
        mRvChatList = (RecyclerView) findViewById(R.id.rv_chatlist);
        mEtQuestion = (EditText) findViewById(R.id.dialog_Text);
        mChatMessageData = ChatMessageData.getInstance();
        mJSONMessage = JSONMessage.getInstance();
        initAdpater();

        //注册广播器
        IntentFilter filter = new IntentFilter();
        filter.addAction(DJISampleApplication.FLAG_CONNECTION_CHANGE);
        registerReceiver(mReceiver, filter);

        Log.e(TAG, "onCreate");
    }

    @Override
    public void onResume() {
        Log.e(TAG, "onResume");
        super.onResume();
        //更新状态栏
        updateConnection();
        //初始化飞控
        initFlightController();
        //登录DJI账户
        loginAccount();
        //开始定位
        startLocation();
    }

    @Override
    public void onPause() {
        Log.e(TAG, "onPause");
        super.onPause();
        //停止定位
        stopLocation();
    }

    @Override
    public void onStop() {
        Log.e(TAG, "onStop");
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Log.e(TAG, "onDestroy");

        //解除注册接收器
        if (mReceiver != null) {
            unregisterReceiver(mReceiver);
            mReceiver = null;
        }

        //停止发送虚拟摇杆控制数据的任务
        if (null != mSendVirtualStickDataTimer) {
            mSendVirtualStickDataTask.cancel();
            mSendVirtualStickDataTask = null;
            mSendVirtualStickDataTimer.cancel();
            mSendVirtualStickDataTimer.purge();
            mSendVirtualStickDataTimer = null;
        }
        super.onDestroy();
    }

    public void onReturn(View view){
        Log.e(TAG, "onReturn");
        this.finish();
    }
    //endregion

    //region UI点击事件
    @Override
    public void onClick(View v) {

        switch (v.getId()) {
            case R.id.locate:
                updateDroneLocation();
                cameraUpdate(new LatLng(mDroneLocation.latitude,mDroneLocation.longitude)); // Locate the drone's place
                break;
            case R.id.btn_control_recognize:
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
            case R.id.send_btn:
                sendQuestion();
                break;
            default:
                break;
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_fpvwaypoint, container, false);
    }

    @Override
    public void onDialogMessage(boolean message) {
        if(iscommond){
            if (message) {
                writeRecogRecord(true, mStrIntention, cc1.getEncodedString().toString(), cc1.getCommand());
                //预处理命令
//                preCheck(cc1.getEncodedString(), cc1.getGoogleMapSearchString());
                addChatMessage(Constant.OWNER_HUMAN,"确认执行");
                handleUserResponse("确认执行");
                iscommond = false;
            } else {
                writeRecogRecord(false, mStrIntention, cc1.getEncodedString().toString(), cc1.getCommand());
                addChatMessage(Constant.OWNER_HUMAN,"取消执行");
                handleUserResponse("取消执行");
                showToast("Command cancelled");
                iscommond = false;
            }
        }
        else if (iswaypoint){
            if (message) {
                addChatMessage(Constant.OWNER_HUMAN,"继续添加");
                showToast("继续添加");
                iswaypoint = false;
            } else {
                addChatMessage(Constant.OWNER_HUMAN,"添加完成");
                showToast("添加完成");
                iswaypoint = false;
            }
        }
    }

    /**
     * Checks if there is any missing permissions, and 检查缺失的权限并重新请求
     * requests runtime permission if needed.
     */
    private void checkAndRequestPermissions() {

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 100);
        }
        // Check for permissions
        for (String eachPermission : REQUIRED_PERMISSION_LIST) {
            if (ContextCompat.checkSelfPermission(this, eachPermission) != PackageManager.PERMISSION_GRANTED) {
                missingPermission.add(eachPermission);
            }
        }
        // Request for missing permissions
        if (!missingPermission.isEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(this,
                    missingPermission.toArray(new String[missingPermission.size()]),
                    REQUEST_PERMISSION_CODE);
        }
    }

    /**
     * Result of runtime permission request 请求权限的结果
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // Check for granted permission and remove from missing list
        if (requestCode == REQUEST_PERMISSION_CODE) {
            for (int i = grantResults.length - 1; i >= 0; i--) {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    missingPermission.remove(permissions[i]);
                }
            }
        }
        // If there is enough permission, we will start the registration
        if (missingPermission.isEmpty()) {
            startSDKRegistration();
        } else {
            showToast("Missing permissions!!!");
        }
    }
    private void notifyStatusChange() {
        mHandler.removeCallbacks(updateRunnable);
        mHandler.postDelayed(updateRunnable, 500);
    }
    private Runnable updateRunnable = new Runnable() {

        @Override
        public void run() {
            Intent intent = new Intent(FLAG_CONNECTION_CHANGE);
            getApplicationContext().sendBroadcast(intent);
        }
    };

    /**
     * SDK注册
     */
    private void startSDKRegistration() {
        if (isRegistrationInProgress.compareAndSet(false, true)) {
            AsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    runOnUiThread(() ->
                            showToast( "registering, pls wait...")
                    );
                    DJISDKManager.getInstance().registerApp(getApplicationContext(), new DJISDKManager.SDKManagerCallback() {
                        @Override
                        public void onRegister(DJIError djiError) {
                            if (djiError == DJISDKError.REGISTRATION_SUCCESS) {
                                DJILog.e("App registration", DJISDKError.REGISTRATION_SUCCESS.getDescription());
                                DJISDKManager.getInstance().startConnectionToProduct();
                                showToast("Register Success");
                            } else {
                                showToast( "Register sdk fails, check network is available");
                            }
                            Log.v(TAG, djiError.getDescription());
                        }

                        @Override
                        public void onProductDisconnect() {
                            Log.d(TAG, "onProductDisconnect");
                            showToast("Product Disconnected");

                        }
                        @Override
                        public void onProductConnect(BaseProduct baseProduct) {
                            Log.d(TAG, String.format("onProductConnect newProduct:%s", baseProduct));
                            showToast("Product Connected");

                        }

                        @Override
                        public void onProductChanged(BaseProduct baseProduct) {

                        }

                        @Override
                        public void onComponentChange(BaseProduct.ComponentKey componentKey, BaseComponent oldComponent,
                                                      BaseComponent newComponent) {

                            if (newComponent != null) {
                                newComponent.setComponentListener(new BaseComponent.ComponentListener() {

                                    @Override
                                    public void onConnectivityChange(boolean isConnected) {
                                        Log.d(TAG, "onComponentConnectivityChanged: " + isConnected);
                                    }
                                });
                            }
                            Log.d(TAG,
                                    String.format("onComponentChange key:%s, oldComponent:%s, newComponent:%s",
                                            componentKey,
                                            oldComponent,
                                            newComponent));

                        }
                        @Override
                        public void onInitProcess(DJISDKInitEvent djisdkInitEvent, int i) {

                        }

                        @Override
                        public void onDatabaseDownloadProgress(long l, long l1) {

                        }
                    });
                }
            });
        }
    }

    /**
     * 展示吐司
     */
    private void showToast(final String str) {
        if (!isFinishing() && !isDestroyed()) {
            runOnUiThread(new Runnable() {
                public void run() {
                    if (toast != null) {
                        toast.cancel();
                    }
                    toast = Toast.makeText(ControlActivity.this, str, Toast.LENGTH_SHORT);
                    toast.show();
                }
            });
        }
    }

    /**
     * 登录账户
     */
    private void loginAccount(){

        UserAccountManager.getInstance().logIntoDJIUserAccount(this,
                new CommonCallbacks.CompletionCallbackWith<UserAccountState>() {
                    @Override
                    public void onSuccess(final UserAccountState userAccountState) {
                        Log.e(TAG, "Login Success");
                    }
                    @Override
                    public void onFailure(DJIError error) {
                        showToast("Login Error:"
                                + error.getDescription());
                    }
                });
    }

    /**
     * 初始化UI
     */
    private void initUI() {

        mTextView = (TextView) findViewById(R.id.textview_simulator);
        mBtnLanguage2 = (Button) findViewById(R.id.btn_control_lan);
        mConnectStatusTextView = (TextView) findViewById(R.id.ConnectStatusTextView);
        mScreenJoystickRight = (OnScreenJoystick)findViewById(R.id.directionJoystickRight);
        mScreenJoystickLeft = (OnScreenJoystick)findViewById(R.id.directionJoystickLeft);
        mBatteryView = (BatteryView) findViewById(R.id.battery_view);
        mBatteryData = (TextView) findViewById(R.id.battery_data);
        mAltitude = (TextView) findViewById(R.id.Altitude);
        mVerSpeed = (TextView) findViewById(R.id.VerticalSpeed);
        mDistance = (TextView) findViewById(R.id.Distance);
        mHorSpeed = (TextView) findViewById(R.id.HorizonSpeed);
        mContext = ControlActivity.this;
        mSendBtn = (Button) findViewById(R.id.send_btn);
        navView = (NavigationView) findViewById(R.id.nav_view);
        mBtnEnableVirtualStick = (Button) navView.findViewById(R.id.btn_enable_virtual_stick);
        mBtnDisableVirtualStick = (Button) navView.findViewById(R.id.btn_disable_virtual_stick);
        mBtnPhoto = (Button) navView.findViewById(R.id.btn_photo);
        mBtnDownload = (Button) navView.findViewById(R.id.btn_to_download);
        mBtnWaypoint = (Button) navView.findViewById(R.id.btn_waypoint);
        drawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        mFlightControlTab = (Button) findViewById(R.id.Flight_control_tab);

        Button problemButton = findViewById(R.id.problem_buttion);
        RelativeLayout mRlSend = (RelativeLayout) findViewById(R.id.rl_send);
        // 获取帮助按钮
        Button helpButton = findViewById(R.id.help_Btn);
        Button dataButtion = findViewById(R.id.data_Btn);
        Button openDrawerButton = findViewById(R.id.btn_open_drawer);
        Button nogps_takeoff = findViewById(R.id.btn_nogps_takeoff);
        Button set_home_current = findViewById(R.id.set_home_current);
        Button test = findViewById(R.id.test);
        ToggleButton is_Gpt_Serve = findViewById(R.id.is_GPT_Serve);
        mBtnSimulator = (ToggleButton) findViewById(R.id.btn_start_simulator);



        // 设置点击事件
        openDrawerButton.setOnClickListener(v -> openDrawer());
        mBtnEnableVirtualStick.setOnClickListener(v -> {
            // 处理 Home 按钮点击逻辑
            if (mCI.mFlightController != null){

                mCI.mFlightController.setVirtualStickModeEnabled(true, new CommonCallbacks.CompletionCallback() {
                    @Override
                    public void onResult(DJIError djiError) {
                        if (djiError != null){
                            showToast(djiError.getDescription());
                        }else
                        {
                            showToast("Enable Virtual Stick Success");
                        }
                    }
                });

            }
            drawerLayout.closeDrawer(GravityCompat.START);
        });
        mBtnDisableVirtualStick.setOnClickListener(v -> {
            if (mCI.mFlightController != null){
                mCI.mFlightController.setVirtualStickModeEnabled(false, new CommonCallbacks.CompletionCallback() {
                    @Override
                    public void onResult(DJIError djiError) {
                        if (djiError != null) {
                            showToast(djiError.getDescription());
                        } else {
                            showToast("Disable Virtual Stick Success");
                        }
                    }
                });
            }
            drawerLayout.closeDrawer(GravityCompat.START);
        });
        mBtnPhoto.setOnClickListener(v -> {
            Intent intent2 = new Intent(v.getContext(), VideoActivity.class);
            v.getContext().startActivity(intent2);
            drawerLayout.closeDrawer(GravityCompat.START);
        });
        mBtnDownload.setOnClickListener(v -> {
            Intent intent3 = new Intent(v.getContext(), DownloadActivity.class);
            v.getContext().startActivity(intent3);
            drawerLayout.closeDrawer(GravityCompat.START);
        });
        mBtnWaypoint.setOnClickListener(v -> {
            Intent intent4 = new Intent(v.getContext(), Waypoint2Activity.class);
            v.getContext().startActivity(intent4);
            drawerLayout.closeDrawer(GravityCompat.START);
        });
        mFlightControlTab.setOnClickListener(v -> {
            if(iscontrol){
                mScreenJoystickRight = (OnScreenJoystick)findViewById(R.id.directionJoystickRight);
                mScreenJoystickLeft = (OnScreenJoystick)findViewById(R.id.directionJoystickLeft);
                mScreenJoystickRight.setJoystickListener(new OnScreenJoystickListener(){

                    @Override
                    public void onTouch(OnScreenJoystick joystick, float pX, float pY) {
                        if(Math.abs(pX) < 0.02 ){
                            pX = 0;
                        }

                        if(Math.abs(pY) < 0.02 ){
                            pY = 0;
                        }

                        float pitchJoyControlMaxSpeed = 10;
                        float rollJoyControlMaxSpeed = 10;

                        mPitch = (float)(pitchJoyControlMaxSpeed * pX);

                        mRoll = (float)(rollJoyControlMaxSpeed * pY);

                        if (null == mSendVirtualStickDataTimer) {
                            mSendVirtualStickDataTask = new SendVirtualStickDataTask();
                            mSendVirtualStickDataTimer = new Timer();
                            mSendVirtualStickDataTimer.schedule(mSendVirtualStickDataTask, 100, 200);
                        }

                    }

                });
                mScreenJoystickLeft.setJoystickListener(new OnScreenJoystickListener() {

                    @Override
                    public void onTouch(OnScreenJoystick joystick, float pX, float pY) {
                        if(Math.abs(pX) < 0.02 ){
                            pX = 0;
                        }

                        if(Math.abs(pY) < 0.02 ){
                            pY = 0;
                        }
                        float verticalJoyControlMaxSpeed = 2;
                        float yawJoyControlMaxSpeed = 30;

                        mYaw = (float)(yawJoyControlMaxSpeed * pX);
                        mThrottle = (float)(verticalJoyControlMaxSpeed * pY);

                        if (null == mSendVirtualStickDataTimer) {
                            mSendVirtualStickDataTask = new SendVirtualStickDataTask();
                            mSendVirtualStickDataTimer = new Timer();
                            mSendVirtualStickDataTimer.schedule(mSendVirtualStickDataTask, 0, 200);
                        }

                    }
                });
                mScreenJoystickRight.setVisibility(View.VISIBLE);
                mScreenJoystickLeft.setVisibility(View.VISIBLE);
                mRlSend.setVisibility(View.GONE);
                mFlightControlTab.setText("手动控制");
            }
            else{
                mScreenJoystickRight.setVisibility(View.GONE);
                mScreenJoystickLeft.setVisibility(View.GONE);
                mRlSend.setVisibility(View.VISIBLE);
                mFlightControlTab.setText("语音控制");
            }
            iscontrol = !iscontrol;
        });
        test.setOnClickListener(v -> {
            agentFindCar();
        });
        set_home_current.setOnClickListener(v ->{
           if(mCI.mFlightController!=null){
               mCI.mFlightController.setHomeLocationUsingAircraftCurrentLocation(new CommonCallbacks.CompletionCallback() {
                   @Override
                   public void onResult(DJIError djiError) {
                       if (djiError != null) {
                           showToast(djiError.getDescription());
                       }else
                       {
                           showToast("设置返航点成功");
                       }
                   }
               });
           }
        });
        nogps_takeoff.setOnClickListener(v ->{
            if(mCI.mFlightController!=null){
                boolean lock = false;
                mCI.mFlightController.lockTakeoffWithoutGPS(lock,new CommonCallbacks.CompletionCallback() {
                    @Override
                    public void onResult(DJIError djiError) {
                        if (djiError != null) {
                            showToast(djiError.getDescription());
                        }else
                        {
                            showToast("开启无GPS起飞成功");
                        }
                    }
                });
            }
        });
        helpButton.setOnClickListener(v -> showHelpDialog());
        dataButtion.setOnClickListener(v -> showFlightDataDialog());
        problemButton.setOnClickListener(v -> showDiagnosticsDialog(diagnosticsMessage.toString()));
        is_Gpt_Serve.setTextOn("GPT已开启");
        is_Gpt_Serve.setTextOff("GPT已关闭");
        is_Gpt_Serve.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    // 当 Switch 被打开，会显示 textOn，同时 isChecked = true
                    isGPT = true;
                } else {
                    // 当 Switch 被关闭，会显示 textOff，同时 isChecked = false
                    isGPT = false;
                }
            }
        });
        findViewById(R.id.btn_control_recognize).setOnClickListener(this);
        findViewById(R.id.btn_control_lan).setOnClickListener(this);
        mSendBtn.setOnClickListener(this);
        mBtnSimulator.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {

                    if (mCI.mFlightController != null) {

                        mCI.mFlightController.getSimulator()
                                .start(InitializationData.createInstance(new LocationCoordinate2D(23, 113), 10, 10),
                                        new CommonCallbacks.CompletionCallback() {
                                            @Override
                                            public void onResult(DJIError djiError) {
                                                if (djiError != null) {
                                                    showToast(djiError.getDescription());
                                                }else
                                                {
                                                    showToast("Start Simulator Success");
                                                }
                                            }
                                        });
                    }

                } else {


                    if (mCI.mFlightController != null) {
                        mCI.mFlightController.getSimulator()
                                .stop(new CommonCallbacks.CompletionCallback() {
                                          @Override
                                          public void onResult(DJIError djiError) {
                                              if (djiError != null) {
                                                  showToast(djiError.getDescription());
                                              }else
                                              {
                                                  showToast("Stop Simulator Success");
                                              }
                                          }
                                      }
                                );
                    }
                }
            }
        });
        // 虚拟摇杆设置事件
        mScreenJoystickRight.setJoystickListener(new OnScreenJoystickListener(){

            @Override
            public void onTouch(OnScreenJoystick joystick, float pX, float pY) {
                if(Math.abs(pX) < 0.02 ){
                    pX = 0;
                }

                if(Math.abs(pY) < 0.02 ){
                    pY = 0;
                }

                float pitchJoyControlMaxSpeed = 10;
                float rollJoyControlMaxSpeed = 10;

                mPitch = (float)(pitchJoyControlMaxSpeed * pX);

                mRoll = (float)(rollJoyControlMaxSpeed * pY);

                if (null == mSendVirtualStickDataTimer) {
                    mSendVirtualStickDataTask = new SendVirtualStickDataTask();
                    mSendVirtualStickDataTimer = new Timer();
                    mSendVirtualStickDataTimer.schedule(mSendVirtualStickDataTask, 100, 200);
                }

            }

        });
        mScreenJoystickLeft.setJoystickListener(new OnScreenJoystickListener() {

            @Override
            public void onTouch(OnScreenJoystick joystick, float pX, float pY) {
                if(Math.abs(pX) < 0.02 ){
                    pX = 0;
                }

                if(Math.abs(pY) < 0.02 ){
                    pY = 0;
                }
                float verticalJoyControlMaxSpeed = 2;
                float yawJoyControlMaxSpeed = 30;

                mYaw = (float)(yawJoyControlMaxSpeed * pX);
                mThrottle = (float)(verticalJoyControlMaxSpeed * pY);

                if (null == mSendVirtualStickDataTimer) {
                    mSendVirtualStickDataTask = new SendVirtualStickDataTask();
                    mSendVirtualStickDataTimer = new Timer();
                    mSendVirtualStickDataTimer.schedule(mSendVirtualStickDataTask, 0, 200);
                }

            }
        });
        lanBtnListener2();
        //高德地图初始化
        if (aMap == null) {
            aMap = mMapView.getMap();
            aMap.setOnMapClickListener(ControlActivity.this);// add the listener for click for amap object
        }
        aMap.moveCamera(CameraUpdateFactory.zoomTo(18));

        //设置可视化
        mScreenJoystickRight.setVisibility(View.GONE);
        mScreenJoystickLeft.setVisibility(View.GONE);
        mRlSend.setVisibility(View.VISIBLE);

        //注册诊断信息回调
        if(DJISDKManager.getInstance().getProduct()!=null){
            DJISDKManager.getInstance().getProduct().setDiagnosticsInformationCallback(diagnosticsList -> {
                try {
                    if (diagnosticsList == null || diagnosticsList.isEmpty()) {
                        return;
                    }

                    // 遍历诊断信息列表
                    diagnosticsMessage = new StringBuilder();
                    for (DJIDiagnostics diagnostics : diagnosticsList) {
                        diagnosticsMessage
                                .append("模块: ").append(diagnostics.getComponentIndex()).append("\n")
                                .append("类型：").append(diagnostics.getType()).append("\n")
                                .append("错误码: ").append(diagnostics.getCode()).append("\n")
                                .append("原因: ").append(diagnostics.getReason()).append("\n")
                                .append("解决方案: ").append(diagnostics.getSolution()).append("\n\n");
                    }

                    // 打印日志（用于调试）
                    System.out.println("诊断信息: " + diagnosticsMessage.toString());

                } catch (Exception e) {
                    showToast("处理诊断信息时发生错误：" + e.getMessage());
                    e.printStackTrace();
                }
            });
        }

    }

    // 打开侧边栏的方法
    public void openDrawer() {
        if (drawerLayout != null) {
            drawerLayout.openDrawer(GravityCompat.START);
        }
    }

    //endregion

    //region RTSP连接回调函数
    private final ConnectCheckerRtsp connectCheckerRtsp = new ConnectCheckerRtsp() {
        @Override
        public void onNewBitrateRtsp(long bitrate) {
            // 可以添加逻辑处理码率
        }

        @Override
        public void onConnectionSuccessRtsp() {
            runOnUiThread(() -> Toast.makeText(
                    ControlActivity.this,
                    "Connection success",
                    Toast.LENGTH_LONG
            ).show());
        }

        @Override
        public void onConnectionFailedRtsp(String reason) {
            runOnUiThread(() -> {
                Toast.makeText(
                        ControlActivity.this,
                        "Connection failed. " + reason,
                        Toast.LENGTH_LONG
                ).show();
                rtspServer.stopServer();
//                rtspServerDisplay.stopStream();
            });
        }

        @Override
        public void onConnectionStartedRtsp(String rtspUrl) {
            // 可以添加逻辑处理连接启动
        }

        @Override
        public void onDisconnectRtsp() {
            runOnUiThread(() -> Toast.makeText(
                    ControlActivity.this,
                    "Disconnected",
                    Toast.LENGTH_LONG
            ).show());
        }

        @Override
        public void onAuthErrorRtsp() {
            runOnUiThread(() -> {
                Toast.makeText(
                        ControlActivity.this,
                        "Auth error",
                        Toast.LENGTH_LONG
                ).show();
                rtspServer.stopServer();
//                rtspServerDisplay.stopStream();
            });
        }

        @Override
        public void onAuthSuccessRtsp() {
            runOnUiThread(() -> Toast.makeText(
                    ControlActivity.this,
                    "Auth success",
                    Toast.LENGTH_LONG
            ).show());
        }
    };
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
            // Execute NLC
            new ClassificationTask(ControlActivity.this).execute(tokenedCommand);
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

    private boolean isDialogActive = false;
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
            AlertDialog.Builder builder = new AlertDialog.Builder(ControlActivity.this);
            builder.setTitle("确认识别结果");

            // 创建一个 EditText 以显示和编辑结果
            if(isDialogActive) return;

            isDialogActive = true;
            EditText editText = new EditText(ControlActivity.this);
            editText.setText(resultBuffer.toString());
            editText.setSelection(resultBuffer.toString().length()); // 光标移到文本末尾
            builder.setView(editText);

            // 设置确认按钮
            builder.setPositiveButton("确定", (dialog, which) -> {
                isDialogActive = false;
                String confirmedResult = editText.getText().toString();
                mEtQuestion.setText(editText.getText().toString());
                // TODO: 在这里处理用户确认后的识别结果，例如更新 UI 或发送数据
                Toast.makeText(getApplicationContext(), "结果已确认：" + confirmedResult, Toast.LENGTH_SHORT).show();
            });

            // 设置取消按钮
            builder.setNegativeButton("取消", (dialog, which) -> {
                isDialogActive = false;
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

    private AlertDialog dialog;

    private void showAlertDialog() {
        dialog = new AlertDialog.Builder(this)
                .setTitle("自定弹框")//标题
                .setMessage("正在识别，请稍后...")//内容
                .setIcon(R.mipmap.ic_launcher)//图标
                .create();
        dialog.show();
    }

    /**
     * 语言切换按钮监听器
     */
    private void lanBtnListener2() {
        mBtnLanguage2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (languageType) {
                    languageType = false;
                    language = "zh_cn";
                    mBtnLanguage2.setBackgroundResource (R.drawable.zh_cn);
                } else {
                    languageType = true;
                    language = "en_us";
                    mBtnLanguage2.setBackgroundResource(R.drawable.en_us);
                }
                mIat.setParameter(SpeechConstant.LANGUAGE, language);
            }
        });
    }
    //endregion

    //region 文字分类
    /**
     * Classification Service 分类服务
     */
    public class ClassificationTask {

        private final ExecutorService executorService;
        private final WeakReference<ControlActivity> activityReference;

        public ClassificationTask(ControlActivity activity) {
            this.executorService = Executors.newSingleThreadExecutor();
            this.activityReference = new WeakReference<>(activity);
        }

        public void execute(ArrayList params) {
            String result = doInBackground(params);

            ControlActivity activity = activityReference.get();
            if (activity != null && !activity.isFinishing()) {
                activity.runOnUiThread(() -> activity.showToast(result));
            }
            // Post result back to main thread
        }

        private String doInBackground(ArrayList... params) {
            String result = null;
            if (params[0].size() != 0) {
                cc1.google_map_search_string = null;
                // call WatsonCommandClassifier to classify into 利用分类器进行命令的编码
                cc1.classify(params[0],language);
                // show execution confirmation dialog fragment 确定窗口 并执行回调函数，如果确定，那么就进行任务执行

                if(cc1.getGoogleMapSearchString()!=null){
                    GetPlace(cc1.getGoogleMapSearchString());
                }
                else{
                    iscommond = true;
                    pendingEncodedString = cc1.getEncodedString().toString();
                    pendingCommand = cc1.getCommand();
                    showBaseDialog(findViewById(android.R.id.content));
//                    sendCommandConfirmationToChatBot(cc1.getEncodedString().toString(), cc1.getCommand());
                }

                result = "Did classify";
            } else {
                result = "Not classify";
            }
            return result;
        }

        // Clean up resources when no longer needed
        public void shutdown() {
            executorService.shutdown();
        }
    }

    /**
     * Prepare Encoded String 预处理命令编码
     */
    private ArrayList<Integer> mEncodedStr;
    private void preCheck(ArrayList<Integer> encoded_string, String google_map_string) {
        // 如果有地图点跟踪任务
        if (encoded_string.get(0) == 110) {
            getPlaceCoordinates(mTargetLocation);
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
     * Confirmation box 确认命令窗口
     */
    public void showBaseDialog(View v) {
        // create FragmentManager and CommandConfirmationDialogFragment
        FragmentManager manager = getSupportFragmentManager();
        CommandConfirmationDialogFragment myDialogFragment1 = new CommandConfirmationDialogFragment();
        // send encoded_string and command into pop up window
        Bundle bundle = new Bundle();

        bundle.putString("encoded_string", cc1.getEncodedString().toString());
        bundle.putString("command", cc1.getCommand());
        addChatMessage(Constant.OWNER_BOT, "是否执行以下任务？\n任务内容：" + cc1.getCommand() + "\n如果确认，请回复“确认执行”；如果取消，请回复“取消执行”。");
        myDialogFragment1.setArguments(bundle);
        // show pop up window
        Log.d(TAG, "Showing dialog...");
        if (manager.findFragmentByTag("MyDialogFragment1") == null) {
            runOnUiThread(() -> myDialogFragment1.show(manager, "MyDialogFragment1"));
        }
    }

    /**
     * Confirmation box 确认地点窗口
     */
    public void showPlaceDialog(View v) {
        // create FragmentManager and CommandConfirmationDialogFragment
        FragmentManager manager = getSupportFragmentManager();
        CommandConfirmationDialogFragment myDialogFragment2 = new CommandConfirmationDialogFragment();
        // send encoded_string and command into pop up window
        Bundle bundle = new Bundle();

        Log.d(TAG, "mTargetDes: " + mTargetDes);
        Log.d(TAG, "Command: " + (cc1 != null ? cc1.getCommand() : "cc1 is null"));

        addChatMessage(Constant.OWNER_BOT, "是否添加以下目标点" + mTargetDes + "\n如果确认，请回复“确认执行”；如果取消，请回复“取消执行”。");

        bundle.putString("encoded_string", cc1.getEncodedString().toString());
        bundle.putString("command", mTargetDes);
        myDialogFragment2.setArguments(bundle);
        // show pop up window
        Log.d(TAG, "Showing dialog...");
        if (manager.findFragmentByTag("MyDialogFragment2") == null) {
            runOnUiThread(() -> myDialogFragment2.show(manager, "MyDialogFragment2"));
        }
    }

    /**
     * Confirmation box 确认任务窗口
     */
    public void showTaskDialog(View v) {
        // create FragmentManager and CommandConfirmationDialogFragment
        FragmentManager manager = getSupportFragmentManager();
        CommandConfirmationDialogFragment myDialogFragment3 = new CommandConfirmationDialogFragment();
        // send encoded_string and command into pop up window
        Bundle bundle = new Bundle();

        Log.d(TAG, "mTargetDes: " + mTargetDes);
        Log.d(TAG, "Command: " + (cc1 != null ? cc1.getCommand() : "cc1 is null"));
        if(mMissionOperator!=null){
            bundle.putString("encoded_string", "现在航点数量"+ mWaypoint.getWaypointCount(mMissionOperator));
            bundle.putString("command", "是否继续添加航点");
        }
        addChatMessage(Constant.OWNER_BOT, "是否继续添加目标点" + mTargetDes + "\n如果确认，请回复“继续添加”；如果取消，请回复“添加完成”。");
        bundle.putString("encoded_string", "现在航点数量");
        bundle.putString("command", "是否继续添加航点");
        myDialogFragment3.setArguments(bundle);
        // show pop up window
        Log.d(TAG, "Showing dialog...");
        if (manager.findFragmentByTag("MyDialogFragment3") == null) {
            runOnUiThread(() -> myDialogFragment3.show(manager, "MyDialogFragment3"));
        }
    }

    //endregion

    //region 云台相关
    private Gimbal gimbal = null;
    private int currentGimbalId = 0;
    //endregion

    //region agent 数据结构
    // 构造 GPTS 实例
    private GPTS gpts = new GPTS(
            "sk-AQoUM4UNCS4B9ozs3c7764DbC7Ec4a8487F8719a03DaB650", // 请填入实际的 API Key
            "gpt-4o",
            0.8f,
            0.9f,
            300
    );
    private static final String AGENT_URL = "http://122.207.106.69:25130/chat";
    private static final String TEMPLATE="Please answer the following question: {question}";
    // 常量定义
    private static final int MAX_SEARCH_ATTEMPTS = 7;
    private static final int INITIAL_ANGLE = 5;
    private static final int ANGLE_INCREMENT_FACTOR = 2;
    private static final int SEARCH_TIMEOUT_SECONDS = 30;
    private static final int SLEEP_BETWEEN_SEARCH_MS = 6000;
    private static final int SLEEP_AFTER_CLOSE_MS = 6000;
    private static final int CLOSE_POSITION_PROPORTION_THRESHOLD = 60;
    private static final int COMMAND_UP_ANGLE = 5;
    private static final String IMAGE_FILE_NAME = "frame.jpg";
    // 共享变量
    private volatile boolean isCenterAndClose = false; // 是否满足在中心且占比>=70
    private ExecutorService executorService = Executors.newSingleThreadExecutor();
    // 移动距离配置
    private static final double MIN_MOVE_DISTANCE = 0.5; // 最小移动距离（米）
    private static final double MAX_MOVE_DISTANCE = 3.0; // 最大移动距离（米）
    private static final int PROPORTION_THRESHOLD = 60; // 占比阈值

    //提示词
    private String direction_prompt = "请分析图像，回答以下问题。首先，详细描述您的推理过程。然后，将您的答案以JSON格式输出。\n" +
            "\n" +
            "推理过程：\n" +
            "- 描述您如何判断图中是否有白色轿车,置信度水平如何。\n" +
            "- 解释您对白色轿车位置（左、中、右）的判断依据。\n" +
            "- 描述您如何估算白色轿车占据图像的比例。\n" +
            "\n" +
            "请在推理过程之后，输出JSON格式的答案：\n" +
            "\n" +
            "{\n" +
            "  \"has_white_car\": 布尔值（true或false），\n" +
            "  \"confidence_percentage\": 整数，范围0-100，表示您认为图中有白色轿车的把握，\n" +
            "  \"location_description\": \"字符串，'left'、'center'或'right'，描述白色轿车在图像中的位置\",\n" +
            "  \"estimated_proportion_percentage\": 整数，范围0-100，估计白色轿车占据图像的比例，\n" +
            "}\n" +
            "\n" +
            "**注意：**\n" +
            "- 请先输出推理过程，然后在下一行输出JSON对象。\n" +
            "- 不要在JSON对象之外添加额外的文本或注释。\n" +
            "- 请避免使用诸如“抱歉，我无法查看或分析图片内容”的句子，尽可能基于图像提供回答。\n" +
            "- 只需要判断目标在图像的左、右或者中间，不要回复类似左中(center-left)的回答。\n" +
            "- 请注意轿车通常具有完整白色轿车轮廓。";
    private String Gpt_result;
    //endregion

    //region 飞行控制器
    /**
     * 初始飞行控制器
     */
    private void initFlightController() {
        //实例化飞控
        mCI.initFlightController();
        if (mCI.mFlightController != null) {
            mCI.setPhotoMode();
//            showFpvToast("Set up call bacsk");
        }
        if (mCI.aircraft == null || !mCI.aircraft.isConnected()) {
            showToast("Disconnected");
            mCI.mFlightController = null;
            return;
        } else {
            //初始化设置飞控模式
            mCI.mFlightController = mCI.aircraft.getFlightController();
            mCI.mFlightController.setRollPitchControlMode(RollPitchControlMode.VELOCITY);
            mCI.mFlightController.setYawControlMode(YawControlMode.ANGLE);
            mCI.mFlightController.setVerticalControlMode(VerticalControlMode.VELOCITY);
            //设置坐标系为地面坐标系
            mCI.mFlightController.setRollPitchCoordinateSystem(FlightCoordinateSystem.GROUND);
            mCI.mFlightController.getSimulator().setStateCallback(new SimulatorState.Callback() {
                //显示状态数据
                @Override
                public void onUpdate(final SimulatorState stateData) {
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {

                            String yaw = String.format("%.2f", stateData.getYaw());
                            String pitch = String.format("%.2f", stateData.getPitch());
                            String roll = String.format("%.2f", stateData.getRoll());
                            String positionX = String.format("%.2f", stateData.getPositionX());
                            String positionY = String.format("%.2f", stateData.getPositionY());
                            String positionZ = String.format("%.2f", stateData.getPositionZ());

                            try {
                                addChatMessage(Constant.OWNER_BOT, "Yaw : " + yaw + ", Pitch : " + pitch + ", Roll : " + roll + "\n" + ", X坐标 : " + positionX +
                                        ", Y坐标 : " + positionY +
                                        ", Z坐标 : " + positionZ);
                            }
                            catch (Exception e){
                                addChatMessage(Constant.OWNER_BOT, "现在未连接无人机");
                            }
                        }
                    });
                }
            });
            mCI.mFlightController.setStateCallback(new FlightControllerState.Callback() {
                @Override
                public void onUpdate(@NonNull FlightControllerState flightControllerState) {
                    double mDroneLocationLat = flightControllerState.getAircraftLocation().getLatitude();
                    double mDroneLocationLng = flightControllerState.getAircraftLocation().getLongitude();
                    mDroneLocation = new LatLng(mDroneLocationLat, mDroneLocationLng);
                    mDroneHeading = mCI.mFlightController.getCompass().getHeading();
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
                    updateDroneLocation();
                }
            });
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

    /**
     * 更新无人机状态
     */
    private void updateConnection() {
//        boolean ret = false;
        BaseProduct product = DJISampleApplication.getProductInstance();
        if (product != null) {
            if (product.isConnected()) {
                //The product is connected
                showToast(DJISampleApplication.getProductInstance().getModel() + " Connected");
//                ret = true;
            } else {
                if (product instanceof Aircraft) {
                    Aircraft aircraft = (Aircraft) product;
                    if (aircraft.getRemoteController() != null && aircraft.getRemoteController().isConnected()) {
                        // The product is not connected, but the remote controller is connected
                        runOnUiThread(() ->
                                showToast("only RC Connected")
                        );
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
                initFlightController();
            }
        }
    };

    /**
     * 更新标题栏中无人机的距离，经纬度，竖直速度，水平速度
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
            runOnUiThread(() ->
                    showToast("Instruction Sent")
            );
        } else {
            runOnUiThread(() ->
                    showToast("Flight Control Error")
            );
        }
    }

    /**
     * 发送虚拟摇杆数据任务类
     */
    class SendVirtualStickDataTask extends TimerTask {

        @Override
        public void run() {

            if (mCI.mFlightController != null) {
                mCI.mFlightController.sendVirtualStickFlightControlData(
                        new FlightControlData(
                                mPitch, mRoll, mYaw, mThrottle
                        ), djiError -> {

                        }
                );
            }
        }
    }

    /**
     * 获取航点控制权
     * @param instance
     * @return
     */
    public WaypointV2MissionOperator getWaypointMissionOperator(WaypointV2MissionOperator instance) {
        if (instance == null) {
            MissionControl missionControl = DJISDKManager.getInstance().getMissionControl();
            if (missionControl != null) {
                instance = missionControl.getWaypointMissionV2Operator();
            }
        }
        return instance;
    }

    /**
     * 定时更新无人机飞行数据
     */
    public class DroneDataUpdater {

        private ScheduledExecutorService scheduler;

        public DroneDataUpdater() {
            // 创建定时任务调度器
            scheduler = Executors.newScheduledThreadPool(1);
        }

        public void startUpdatingData() {
            // 每200毫秒执行一次数据更新
            scheduler.scheduleWithFixedDelay(new Runnable() {
                @Override
                public void run() {
                    // 定时更新飞行数据
                    updateDroneData();
                }
            }, 0, 200, TimeUnit.MILLISECONDS); // 初始延迟0，200ms间隔
        }

        public void stopUpdatingData() {
            // 停止定时任务
            if (scheduler != null && !scheduler.isShutdown()) {
                scheduler.shutdown();
            }
        }

        private void updateDroneData() {
            // 获取飞行控制器状态信息
            double mDroneLocationLat = mCI.mFlightController.getState().getAircraftLocation().getLatitude();
            double mDroneLocationLng = mCI.mFlightController.getState().getAircraftLocation().getLongitude();
            mDroneLocation = new LatLng(mDroneLocationLat, mDroneLocationLng);
            mDroneHeading = mCI.mFlightController.getCompass().getHeading();

            // 获取高度数据
            mAltitudeData = (double) mCI.mFlightController.getState().getAircraftLocation().getAltitude();

            // 计算飞行速度
            mhs = Math.sqrt(mCI.mFlightController.getState().getVelocityX() * mCI.mFlightController.getState().getVelocityX()
                    + mCI.mFlightController.getState().getVelocityY() * mCI.mFlightController.getState().getVelocityY());
            mvs = -1 * mCI.mFlightController.getState().getVelocityZ();

            // 计算距离家
            mdistToHome = Utils.calcDistance(mUserLocation.latitude, mUserLocation.longitude, mDroneLocation.latitude, mDroneLocation.longitude);

            // 更新数据
            updateFlightData();
            updateDroneLocation();
        }
    }

//    /**
//     * 云台控制
//     */
//    private void ControlGimbal(double Angle){
//        BaseProduct product = DJISampleApplication.getProductInstance();
//        if (product != null) {
//            if (product instanceof Aircraft) {
//                Aircraft aircraft = (Aircraft) product;
//                gimbals = aircraft.getGimbals();
//            }
//        }
//        for(int i =0;i<gimbals.size();i++){
//            Gimbal gb = gimbals.get(i);
//            gb.
//        }
//    }
    //endregion

    //region 高德地图定位，位置查询，用户位置，无人机位置交互相关

    /**
     * 初始化用户定位
     */
    private void initLocation() {
        boolean collectEnable = true;
        AMapUtilCoreApi.setCollectInfoEnable(collectEnable);
        // 初始化定位
        mLocationClient = new AMapLocationClient(getApplicationContext());
        mLocationOption = new AMapLocationClientOption();

        // 设置定位模式
        mLocationOption.setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy);
        // 设置定位监听
        mLocationClient.setLocationListener(new AMapLocationListener() {
            @Override
            public void onLocationChanged(AMapLocation aMapLocation) {
                if (aMapLocation != null && aMapLocation.getErrorCode() == 0) {
                    // 获取定位信息成功
                    double latitude = aMapLocation.getLatitude();
                    double longitude = aMapLocation.getLongitude();
                    LatLng userLocation = new LatLng(latitude, longitude);
                    updateUserLocation(userLocation, aMapLocation.getBearing());
                } else {
                    // 定位失败
                    Log.e("AMap", "Location failed, error code: " + aMapLocation.getErrorCode());
                }
            }
        });

        // 设置定位参数
        mLocationOption.setOnceLocation(false); // 设置为连续定位
        mLocationOption.setInterval(1000); // 定位更新间隔时间，单位：毫秒
        mLocationClient.setLocationOption(mLocationOption);
    }

    /**
     * 开始用户定位
     */
    private void startLocation() {
        // 启动定位
        mLocationClient.startLocation();
    }

    /**
     * 停止用户定位
     */
    private void stopLocation() {
        // 停止定位
        mLocationClient.stopLocation();
    }

    /**
     * 更新用户位置
     * @param userLocation
     * @param bearing
     */
    private void updateUserLocation(LatLng userLocation, float bearing) {
        // 创建 MarkerOptions 对象
        final MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.position(userLocation);
        markerOptions.icon(BitmapDescriptorFactory.fromResource(R.drawable.icon_girl)); // 设置用户位置图标
        markerOptions.anchor(0.5f, 0.5f); // 设置图标锚点
        mUserLocation = userLocation;

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // 更新地图上的用户位置
                if (userMarker != null) {
                    userMarker.remove(); // 移除旧的 Marker
                }

                // 在地图上添加 Marker
                userMarker = aMap.addMarker(markerOptions);

                // 设置用户位置方向（如果需要根据方位角更新）
                userMarker.setRotateAngle(bearing); // 根据实际需要设置旋转角度（例如朝向）
            }
        });
    }

    /**
     * 更新无人机在地图上的标记
     */
    private void updateDroneLocation() {

        LatLng pos = new LatLng(mDroneLocation.latitude, mDroneLocation.longitude);
        //Create MarkerOptions object
        final MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.position(pos);
        markerOptions.icon(BitmapDescriptorFactory.fromResource(R.drawable.aircraft));
        markerOptions.anchor(0.5f, 0.618f);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (droneMarker != null) {
                    droneMarker.remove();
                }

                if (checkGpsCoordination(mDroneLocation.latitude, mDroneLocation.longitude)) {
                    droneMarker = aMap.addMarker(markerOptions);
                    droneMarker.setRotateAngle(droneHeading * -1.0f);
                }
            }
        });
    }

    /**
     * 检查坐标是否合理
     * @param latitude
     * @param longitude
     * @return
     */
    public static boolean checkGpsCoordination(double latitude, double longitude) {
        return (latitude > -90 && latitude < 90 && longitude > -180 && longitude < 180) && (latitude != 0f && longitude != 0f);
    }

    /**
     * 更新地图上的无人机位置，并鹰眼放大
     */
    private void cameraUpdate(LatLng pos) {
        float zoomlevel = (float) 18.0;
        CameraUpdate cu = CameraUpdateFactory.newLatLngZoom(pos, zoomlevel);
        aMap.moveCamera(cu);

    }

    /**
     * 点击地图事件重写
     * @param point
     */
    @Override
    public void onMapClick(LatLng point) {
        if (isAdd == true) {
            markWaypoint(point);
        } else {
            showToast("Cannot Add Waypoint");
        }
    }

    /**
     * 根据点数据在地图上标记航点 并添加这个航点
     * @param point
     */
    private void markWaypoint(LatLng point) {
        mWaypoint.AddWaypoint(point.latitude,point.longitude);
        //Create MarkerOptions object
        MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.position(point);
        markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE));
        Marker marker = aMap.addMarker(markerOptions);
        mMarkers.put(mMarkers.size(), marker);
    }

    /**
     * 根据地名获取经纬度 核心函数
     * @param name
     */
    private void GetPlace(String name){
        geocoderSearch.setOnGeocodeSearchListener(new GeocodeSearch.OnGeocodeSearchListener() {
            @Override
            public void onRegeocodeSearched(RegeocodeResult regeocodeResult, int i) {
                // 处理逆地理编码结果
                RegeocodeAddress address = regeocodeResult.getRegeocodeAddress();
            }

            @Override
            public void onGeocodeSearched(GeocodeResult result, int rCode) {
                if (rCode == 1000) {
                    if (result != null && result.getGeocodeAddressList() != null
                            && result.getGeocodeAddressList().size() > 0) {
                        GeocodeAddress address = result.getGeocodeAddressList().get(0);
                        aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
                                AMapUtil.convertToLatLng(address.getLatLonPoint()), 15));
                        addressName = "经纬度值:" + address.getLatLonPoint() + "\n位置描述:"
                                + address.getFormatAddress();
                        mTargetDes = address.getFormatAddress();
                        mTargetLocation = address.getLatLonPoint();

                        iscommond = true;

                        Place place = new Place();
                        place.name = name;
                        place.lat = mTargetLocation.getLatitude();
                        place.lon = mTargetLocation.getLongitude();
                        Places.add(place);

                        pendingEncodedString = cc1.getEncodedString().toString();
                        pendingCommand = cc1.getCommand();
                        pendingTarget = cc1.getGoogleMapSearchString();
                        showPlaceDialog(findViewById(android.R.id.content));
//                        sendCommandConfirmationToChatBot(cc1.getEncodedString().toString(), mTargetDes);

                        ToastUtil.show(ControlActivity.this, addressName);
                    } else {
                        ToastUtil.show(ControlActivity.this, R.string.no_result);
                    }

                } else if (rCode == 27) {
                    ToastUtil.show(ControlActivity.this, R.string.error_network);
                } else if (rCode == 32) {
                    ToastUtil.show(ControlActivity.this, R.string.error_key);
                } else {
                    ToastUtil.show(ControlActivity.this,
                            getString(R.string.error_other) + rCode);
                }
            }
        });

        // name表示地址，第二个参数表示查询城市，中文或者中文全拼，citycode、adcode
        GeocodeQuery query = new GeocodeQuery(name, "长沙");

        geocoderSearch.getFromLocationNameAsyn(query);
    }

    /**
     * 获取目标点的经纬度，添加航点，并在地图上标记，并执行航点任务
     * @param mTargetLocation
     */
    public void getPlaceCoordinates(LatLonPoint mTargetLocation){
        double lat = mTargetLocation.getLatitude();
        double lon = mTargetLocation.getLongitude();
        LatLng point = new LatLng(mTargetLocation.getLatitude(),mTargetLocation.getLongitude());

        int latInt = (int) lat;
        int lonInt = (int) lon;

        markWaypoint(point);
        iswaypoint = true;
        cc1.google_map_search_string = null;
        showTaskDialog(findViewById(android.R.id.content));
    }

    //endregion

    //region 对话机器人Jarvis
    private void initAdpater() {
        mListAdapter = new ChatListAdapter();
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(mContext);
        // 从底部加入聊天消息
        linearLayoutManager.setStackFromEnd(true);
        mRvChatList.setLayoutManager(linearLayoutManager);
        mRvChatList.setAdapter(mListAdapter);
    }

    /**
     *
     * @param confirmedResult
     */
    private void processCommand(String confirmedResult) {
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

        // 执行分类任务（假设是执行 NLC 的核心逻辑）
        ClassificationTask cft = new ClassificationTask(ControlActivity.this);
        cft.execute(tokenedCommand);
    }

    /**
     * 发送分类后的确认任务消息给用户
     * @param encodedString
     * @param command
     */
    public void sendCommandConfirmationToChatBot(String encodedString, String command) {
        this.pendingEncodedString = encodedString;
        this.pendingCommand = command;

        // 发送确认消息
        addChatMessage(Constant.OWNER_BOT, "是否执行以下任务？\n任务内容：" + command + "\n如果确认，请回复“确认执行”；如果取消，请回复“取消执行”。");
    }

    /**
     * 处理用户的回复
     * @param userResponse
     */
    public void handleUserResponse(String userResponse) {
        if ("确认执行".equalsIgnoreCase(userResponse)) {
            if(pendingTarget!=null){
                preCheck(cc1.getEncodedString(), cc1.getGoogleMapSearchString());
                addChatMessage(Constant.OWNER_BOT, "已添加航点目的地：" + pendingTarget);
                pendingTarget = null;
            }
            else{
                // 用户确认执行任务
                if (pendingEncodedString != null) {
                    preCheck(cc1.getEncodedString(), cc1.getGoogleMapSearchString());
                    addChatMessage(Constant.OWNER_BOT, "任务已开始执行：" + pendingCommand);
                } else {
                    addChatMessage(Constant.OWNER_BOT, "任务信息丢失，无法执行。");
                }
                // 清除待确认任务信息
                pendingCommand = null;
                pendingEncodedString = null;
            }
        } else if ("取消执行".equalsIgnoreCase(userResponse)) {
            // 用户取消执行任务
            addChatMessage(Constant.OWNER_BOT, "任务已取消：" + pendingCommand);
            // 清除待确认任务信息
            pendingCommand = null;
            pendingEncodedString = null;
        } else {
            // 其他无效回复
            addChatMessage(Constant.OWNER_BOT, "无效的回复，请输入“确认执行”或“取消执行”。");
        }
    }

    /**
     * 发送指令
     */
    private void sendQuestion() {
        String question = mEtQuestion.getText().toString().trim();
        if (TextUtils.isEmpty(question)) {
            Toast.makeText(this, "请先输入你的问题", Toast.LENGTH_SHORT).show();
            return;
        }

        mEtQuestion.setText("");
        // 发送文字到List里
        addChatMessage(Constant.OWNER_HUMAN, question);

        // 检查是否为用户反馈
        if (isUserResponse(question)) {
            handleUserResponse(question);
        } else {
            // 如果不是反馈，则按常规指令处理
            addChatMessage(Constant.OWNER_BOT_THINK, "正在分析问题并自动执行任务中...");
            handleRobotCommand(question);
        }
    }

    /**
     * 发送问题到GPT或其他API，并处理回调
     *
     * @param isGPT 是否使用GPT模型
     * @param prompt 提示语
     * @param imageFile 图片文件
     * @param listener 回调监听器
     */
    private void sendQuestion(boolean isGPT, String prompt, File imageFile, OnGptResultListener listener) {
        if (isGPT) {
            sendQuestionToGPT(prompt, imageFile, true, listener);
        } else {
            sendQuestionToAPI(prompt, imageFile, listener);
        }
    }

    /**
     * 检查用户输入是否为反馈消息
     * @param input 用户输入
     * @return 是否为反馈
     */
    private boolean isUserResponse(String input) {
        return input.equalsIgnoreCase("确认执行") || input.equalsIgnoreCase("取消执行");
    }

    /**
     * 重要 存储消息，显示消息，播放消息内容，记录上下文
     *
     * @param owner
     * @param question
     */
    private void addChatMessage(String owner, String question) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mChatMessageData.addChatMessage(owner, question);
                mListAdapter.notifyDataSetChanged();
                mRvChatList.smoothScrollToPosition(mChatMessageData.getSize());
                if (mChatMessageData.isBot(owner)) {
                    mJSONMessage.addBotMessage(question); // 记录每次用户的上下文，这样AI就能实现多次对话
//                    Speech.getInstance().say(question, new TextToSpeechCallback() {
//                        @Override
//                        public void onStart() {
//
//                        }
//
//                        @Override
//                        public void onCompleted() {
//
//                        }
//
//                        @Override
//                        public void onError() {
//
//                        }
//                    });
                }
            }
        });
    }

    private void addChatMessage(String owner, Bitmap image) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (image != null) {
                    mChatMessageData.addChatMessage(owner, null, image);
                    mListAdapter.notifyDataSetChanged();
                    mRvChatList.smoothScrollToPosition(mChatMessageData.getSize());
                }
            }
        });
    }

    /**
     * 获取文件格式（扩展名）
     *
     * @param file File 对象
     * @return 文件格式（扩展名），如果没有扩展名则返回 null
     */
    public static String getFileFormat(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            return null; // 如果文件无效，返回 null
        }

        String fileName = file.getName();
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
            return fileName.substring(dotIndex + 1).toLowerCase(); // 返回扩展名
        } else {
            return null; // 如果没有扩展名，返回 null
        }
    }

    // 用于在异步获取 GPT 结果后通知主流程
    public interface OnGptResultListener {
        void onSuccess(String gptResult);
        void onFailure(Exception e);
    }

    /**
     * 向服务器诗句语言大模型发送问题
     * @param question
     */
    private void sendQuestionToAPI(String question, File file) {

        MultipartBody.Builder builder = new MultipartBody.Builder();
        if(getFileFormat(file).equals("jpg")){
            // 创建请求体
            builder = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("question", TEMPLATE.replace("{question}", question)) // 替换模板中的占位符
                    .addFormDataPart("format", "jpg") // 文件格式
                    .addFormDataPart("file", file.getName(), RequestBody.create(MediaType.parse("image/jpg"), file)); // 上传文件
        }
        else{
            // 创建请求体
            builder = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("question", TEMPLATE.replace("{question}", question)) // 替换模板中的占位符
                    .addFormDataPart("format", "mp4") // 文件格式
                    .addFormDataPart("file", file.getName(), RequestBody.create(MediaType.parse("video/mp4"), file)); // 上传文件
        }

        // 创建请求
        RequestBody requestBody = builder.build();
        Request request = new Request.Builder()
                .url(AGENT_URL)
                .post(requestBody)
                .build();

        // 设置 OkHttp 客户端
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(120, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                mChatMessageData.removeLastChatMessage(); // 删除"思考中"消息
                addChatMessage(Constant.OWNER_BOT, "出错了，错误信息是：" + e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                mChatMessageData.removeLastChatMessage(); // 删除"思考中"消息

                if (response.isSuccessful() && response.body() != null) {
                    try {
                        // 将响应体解析为 JSON 对象
                        String responseBody = response.body().string();
                        JSONObject jsonResponse = new JSONObject(responseBody);

                        // 提取 'response' 字段内容
                        String responseMessage = jsonResponse.optString("response", "未找到响应内容");

                        // 将提取的内容显示在对话框中
                        addChatMessage(Constant.OWNER_BOT, responseMessage.trim());
                        Gpt_result = responseMessage.trim();
                    } catch (JSONException e) {
                        // JSON 解析失败
                        addChatMessage(Constant.OWNER_BOT, "响应解析错误：" + e.getMessage());
                    }
                } else {
                    // 请求失败或无响应体
                    addChatMessage(Constant.OWNER_BOT, "请求失败，错误信息是：" + (response.body() != null ? response.body().string() : "无响应体"));
                }
            }
        });

    }

    private void sendQuestionToAPI(String question, File file,OnGptResultListener listener) {

        MultipartBody.Builder builder = new MultipartBody.Builder();
        if(getFileFormat(file).equals("jpg")){
            // 创建请求体
            builder = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("question", TEMPLATE.replace("{question}", question)) // 替换模板中的占位符
                    .addFormDataPart("format", "jpg") // 文件格式
                    .addFormDataPart("file", file.getName(), RequestBody.create(MediaType.parse("image/jpg"), file)); // 上传文件
        }
        else{
            // 创建请求体
            builder = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("question", TEMPLATE.replace("{question}", question)) // 替换模板中的占位符
                    .addFormDataPart("format", "mp4") // 文件格式
                    .addFormDataPart("file", file.getName(), RequestBody.create(MediaType.parse("video/mp4"), file)); // 上传文件
        }

        // 创建请求
        RequestBody requestBody = builder.build();
        Request request = new Request.Builder()
                .url(AGENT_URL)
                .post(requestBody)
                .build();

        // 设置 OkHttp 客户端
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(120, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                mChatMessageData.removeLastChatMessage(); // 删除"思考中"消息
                addChatMessage(Constant.OWNER_BOT, "出错了，错误信息是：" + e.getMessage());
                listener.onFailure(e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                mChatMessageData.removeLastChatMessage(); // 删除"思考中"消息

                if (response.isSuccessful() && response.body() != null) {
                    try {
                        // 将响应体解析为 JSON 对象
                        String responseBody = response.body().string();
                        JSONObject jsonResponse = new JSONObject(responseBody);

                        // 提取 'response' 字段内容
                        String responseMessage = jsonResponse.optString("response", "未找到响应内容");

//                        // 将提取的内容显示在对话框中
//                        addChatMessage(Constant.OWNER_BOT, responseMessage.trim());
                        Gpt_result = responseMessage.trim();
                        listener.onSuccess(Gpt_result);
                    } catch (JSONException e) {
                        // JSON 解析失败
                        addChatMessage(Constant.OWNER_BOT, "响应解析错误：" + e.getMessage());
                        listener.onFailure(e);
                    }
                } else {
                    // 请求失败或无响应体
                    String errorMsg = response.body() != null ? response.body().string() : "无响应体";
                    runOnUiThread(() -> {
                        addChatMessage(Constant.OWNER_BOT, "请求失败，错误信息是：" + errorMsg);
                        listener.onFailure(new Exception("请求失败，错误信息是：" + errorMsg));
                    });
                }
            }
        });

    }

    /**
     * 向gpt语言大模型发送问题
     * @param question
     */
    private void sendQuestionToGPT(String question, File file, boolean isHistory) {
        // 构造 GPTS 实例
        GPTS gpts = new GPTS(
                "sk-AQoUM4UNCS4B9ozs3c7764DbC7Ec4a8487F8719a03DaB650", // 请填入实际的 API Key
                "gpt-4o",
                0.8f,
                0.9f,
                300
        );

        // 异步调用
        gpts.chatAsync(
                question,
                file.getPath(),        // 如果需要传图片，可以传文件路径
                null,        // 自定义 system prompt
                isHistory ? GPThistory : null,  // 若多轮对话，需要把上一次的 history 传进来
                new GPTSCallback() {
                    @Override
                    public void onSuccess(GPTS.GPTSResult result) {
                        // 这里是子线程回调，如果需要更新UI，请切回主线程
                        runOnUiThread(() -> {
                            // 例如添加对话内容到列表
                            addChatMessage("OWNER_BOT", result.output);
                            Gpt_result = result.output;
                            // 保存新的上下文，以便下一次多轮对话
                            GPThistory = result.history;
                        });
                    }

                    @Override
                    public void onError(Exception e) {
                        runOnUiThread(() -> {
                            addChatMessage("OWNER_BOT", "出错了: " + e.getMessage());
                            e.printStackTrace();
                        });
                    }
                }
        );
    }

    /**
     * 向gpt语言大模型发送问题
     * @param question
     */
    private String sendQuestionToGPTS(String question, File file, boolean isHistory) {
        // 初始化 GPTS 实例
        GPTS gpts = new GPTS(
                "sk-AQoUM4UNCS4B9ozs3c7764DbC7Ec4a8487F8719a03DaB650",
                "gpt-4o",
                0.8f,
                0.9f,
                300
        );

        final String[] resultHolder = {null};  // 用于存储返回结果
        final CountDownLatch latch = new CountDownLatch(1);  // 控制异步转同步的工具

        // 异步请求
        gpts.chatAsync(
                question,
                file != null ? file.getPath() : null,
                null,
                isHistory ? GPThistory : null,
                new GPTSCallback() {
                    @Override
                    public void onSuccess(GPTS.GPTSResult result) {
                        resultHolder[0] = result.output;
                        GPThistory = result.history;
                        latch.countDown();  // 释放锁
                    }

                    @Override
                    public void onError(Exception e) {
                        resultHolder[0] = "Error: " + e.getMessage();
                        latch.countDown();  // 出错也释放锁，防止永远阻塞
                    }
                }
        );

        try {
            latch.await();  // 等待 GPT 处理完成
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return resultHolder[0];
    }

    /**
     * 向gpt语言大模型发送问题
     * @param question
     */
    private void sendQuestionToGPT(String question, File file, boolean isHistory,OnGptResultListener listener) {
        // 异步调用
        gpts.chatAsync(
                question,
                file.getPath(),        // 如果需要传图片，可以传文件路径
                null,        // 自定义 system prompt
                isHistory ? GPThistory : null,  // 若多轮对话，需要把上一次的 history 传进来
                new GPTSCallback() {
                    @Override
                    public void onSuccess(GPTS.GPTSResult result) {
                        // 这里是子线程回调，如果需要更新UI，请切回主线程
                        runOnUiThread(() -> {
    //                            // 例如添加对话内容到列表
    //                            addChatMessage("OWNER_BOT", result.output);
                            Gpt_result = result.output;
                            // 保存新的上下文，以便下一次多轮对话
                            GPThistory = result.history;
                            listener.onSuccess(Gpt_result);
                        });
                    }

                    @Override
                    public void onError(Exception e) {
                        runOnUiThread(() -> {
                            addChatMessage(Constant.OWNER_BOT, "调用GPT出错: " + e.getMessage());
                            listener.onFailure(e);
                            e.printStackTrace();
                        });
                    }
                }
        );
    }

    /**
     * 命令处理
     */
    public void handleRobotCommand(String command) {
        if (command.contains("开始推流")) {
            handleStartStreaming();
        } else if (command.contains("停止推流")) {
            handleStopStreaming();
        } else if (command.contains("定位")) {
            handleLocateDrone();
        } else if (command.contains("执行任务")) {
            handleExecuteMission();
        } else if (command.contains("识别")) {
            // 获取 "识别" 后面的部分
            int index = command.indexOf("识别");
            String question = command.substring(index + 2);
            handleObjectIdentify(question);
        } else if (command.contains("删除")){
            handleDeleteMission(command.replaceAll("删除",""));
        } else if (command.contains("配置航点")){
            handleConfigMission();
        } else if (command.contains("上传航点")){
            handleUploadMission();
        } else if (command.contains("停止任务")){
            handleStopMission();
        } else if (command.contains("清除航点")){
            handleDeleteWaypoint();
        } else if (command.contains("手动添加")){
            handleAddMission();
        } else if (command.contains("开启智能飞行助手")){
            handleFlightAssistant();
        } else if (command.contains("用户追踪")){
            handleUserLocation();
        } else if (command.contains("修改地址")){
            handleModiferurl();
        } else if (command.contains("自动搜索")){
            agentFindCar();
        } else if (command.contains("俯视图")){
            rotateGimbalDownwardView();
        } else if (command.contains("前视图")){
            rotateGimbalForwardView();
        }else {
            processCommand(command);
        }
    }
    //endregion

    //region Agent控制

    /**
     * 入口函数
     */
    public void agentFindCar() {
//        // 初始化虚拟摇杆执行器
//        mSingletonVirtualStickExecutor = MyVirtualStickExecutor.getUniqueInstance();
//        //起飞
//        mCI.mTakeoff();
//        SleepThread(SLEEP_BETWEEN_SEARCH_MS);
//        //向上飞8米
//        mSingletonVirtualStickExecutor.mUp(8);
//        SleepThread(SLEEP_BETWEEN_SEARCH_MS);
        // 执行第一次搜索
        new Thread(() -> {
            performSearch(1, MAX_SEARCH_ATTEMPTS, COMMAND_UP_ANGLE);
        }).start();
    }

    /**
     * 在场景中自动搜索车辆
     * @return
     */
    private boolean doSearch(int attemptIndex) {
        // 如果超出最大次数，就结束
        if (attemptIndex >= MAX_SEARCH_ATTEMPTS) {
            runOnUiThread(() -> {
                addChatMessage(Constant.OWNER_BOT, "多次搜索仍未找到车辆。");
            });
            return false;
        }
        final boolean[] result = { false }; // 存放是否找到车
        File imageFile = CaptureImage();
        Bitmap bitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath());
        runOnUiThread(() -> {addChatMessage(Constant.OWNER_BOT, "图像捕获成功，正在分析...");});
        runOnUiThread(() -> {addChatMessage(Constant.OWNER_HUMAN, bitmap);});
        runOnUiThread(() -> {addChatMessage(Constant.OWNER_BOT, "思考中...");});

        auavLock("sendquestion");
        sendQuestionToGPT(direction_prompt, imageFile,true, new OnGptResultListener() {
            @Override
            public void onSuccess(String gptResult) {
                runOnUiThread(() -> {showToast("成功");});
                JsonUtils.ParseResult parseResult = JsonUtils.robustJsonParser(gptResult);
                String response = parseResult.getInferenceProcess();

                if (parseResult.getJsonData() == null) {
                    runOnUiThread(() -> {addChatMessage(Constant.OWNER_BOT, "模型返回为空，尝试下一帧...");});
                } else {
                    boolean hasWhiteCar = parseResult.getJsonData().optBoolean("has_white_car", false);
                    int confidence = parseResult.getJsonData().optInt("confidence_percentage", 0);

                    if (hasWhiteCar && confidence >= 80) {
                        result[0] = true;
                        response += "\n车辆已锁定!";
                        String finalResponse = response;
                        runOnUiThread(() -> {addChatMessage(Constant.OWNER_BOT, finalResponse);});
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                Close_to();
                            }
                        }).start();
                    } else {
                        response += "\n未能识别到目标车辆，继续搜索...";
                        String finalResponse1 = response;
                        runOnUiThread(() -> {addChatMessage(Constant.OWNER_BOT, finalResponse1);});
//                            // 转动视角
//                            MyVirtualStickExecutor executor = MyVirtualStickExecutor.getUniqueInstance();
//                            executor.mTurn(303, finalAngle);

                        new Thread(() -> {
                            try {
                                Thread.sleep(SLEEP_BETWEEN_SEARCH_MS);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            // 回到主线程继续下一次搜索
                            runOnUiThread(() -> doSearch(attemptIndex + 1));
                        }).start();
                    }
                }
                auavLock("continue");
            }
            @Override
            public void onFailure(Exception e) {
                addChatMessage(Constant.OWNER_BOT, "调用模型出错: " + e.getMessage());
                doSearch(attemptIndex + 1);
                auavLock("continue");
            }
        });
        auavSpin();

        return result[0];
    }

    /**
     * 执行搜索并根据结果决定是否上升和继续搜索
     *
     * @param currentAttempt 当前尝试次数
     * @param maxAttempts    最大尝试次数
     * @param ascendHeight   每次上升的高度
     */
    private void performSearch(int currentAttempt, int maxAttempts, int ascendHeight) {
        boolean isFind = doSearch(0);
        if (isFind) {
            runOnUiThread(() -> addChatMessage(Constant.OWNER_BOT, "车辆已锁定！"));
//                mSingletonVirtualStickExecutor.mStop();
            return;
        }

        if (currentAttempt < maxAttempts) {
            runOnUiThread(() -> addChatMessage(Constant.OWNER_BOT, "第 " + currentAttempt + " 次搜索未找到，开始上升 " + ascendHeight + " 米..."));
//                mSingletonVirtualStickExecutor.mUp(5);
            // 睡 6 秒再搜下一次
            SleepThread(SLEEP_BETWEEN_SEARCH_MS);
            performSearch(currentAttempt+1,maxAttempts,ascendHeight);
        } else {
            runOnUiThread(() -> addChatMessage(Constant.OWNER_BOT, "多次搜索仍未找到车辆。请检查坐标或场景是否正确。"));
            mSingletonVirtualStickExecutor.mStop();
        }
    }

    /**
     * 根据识别到的车辆信息，进行“靠近”操作。
     */
    public void Close_to() {
        performCloseToSearch(1, MAX_SEARCH_ATTEMPTS);
    }

    /**
     * 递归执行靠近搜索，直到满足条件或达到最大尝试次数
     */
    private void performCloseToSearch(int currentAttempt, int maxAttempts) {
        if( currentAttempt>maxAttempts ){
//            recognizeCarBrand();
            return;
        }
        if (isCenterAndClose) {
            return;
        }

        File imageFile = CaptureImage();
        Bitmap bitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath());

        addChatMessage(Constant.OWNER_BOT, "图像捕获成功，正在分析...");
        addChatMessage(Constant.OWNER_HUMAN, bitmap);
        addChatMessage(Constant.OWNER_BOT, "思考中...");

        auavLock("sendquestion");
        sendQuestionToGPT(direction_prompt, imageFile, true, new OnGptResultListener() {
            @Override
            public void onSuccess(String gptResult) {
                try {
                    JsonUtils.ParseResult parseResult = JsonUtils.robustJsonParser(gptResult);
                    String response = parseResult.getInferenceProcess();

                    if (parseResult.getJsonData() == null) {
                        addChatMessage(Constant.OWNER_BOT, "模型返回为空，尝试下一帧...");
                    } else {
                        String locationDesc = parseResult.getJsonData().optString("location_description", "center");
                        int confidence = parseResult.getJsonData().optInt("confidence_percentage", 0);
                        int proportion = parseResult.getJsonData().optInt("estimated_proportion_percentage", 0);

                        addChatMessage(Constant.OWNER_BOT,
                                String.format("开始靠近车辆 —— 位置: %s, 置信度: %d%%, 占比: %d%%",
                                        locationDesc, confidence, proportion)
                        );

//                        // 调整无人机位置
//                        adjustDronePosition(locationDesc, proportion);

                        // 如果占比 >= 70，则尝试识别车标
                        if (proportion >= CLOSE_POSITION_PROPORTION_THRESHOLD) {
                            isCenterAndClose = true;
                            addChatMessage(Constant.OWNER_BOT, "目标较大，可能已靠近车辆，准备识别车标...");
//                            recognizeCarBrand();
                            addChatMessage(Constant.OWNER_BOT, "Close_to 流程完成。");
                        }
                    }
                } catch (Exception e) {
                    addChatMessage(Constant.OWNER_BOT, "解析结果时出错: " + e.getMessage());
                }
                auavLock("continue");
            }

            @Override
            public void onFailure(Exception e) {
                addChatMessage(Constant.OWNER_BOT, "调用模型出错: " + e.getMessage());
                auavLock("continue");
            }
        });
        auavSpin();

        if (!isCenterAndClose) {
            // 递归调用，继续靠近搜索
            performCloseToSearch(currentAttempt+1,maxAttempts);
        }
    }

    /**
     * 调整无人机的位置或视角，使车辆更居中。
     * 当车辆已在中心 (center) 时，若占比 < 阈值，则向前移动一定距离。
     * @param locationDesc 车辆在画面中的位置描述 (left/right/center)
     * @param proportion   车辆在画面中的占比
     */
    private void adjustDronePosition(String locationDesc, int proportion) {
        mSingletonVirtualStickExecutor = MyVirtualStickExecutor.getUniqueInstance();

        switch (locationDesc) {
            case "left":
                addChatMessage(Constant.OWNER_BOT, "车辆在图像左侧，向左移动...");
                double moveLeftDistance = calculateMoveDistance(proportion);
                mSingletonVirtualStickExecutor.mGo(302, moveLeftDistance);
                break;

            case "right":
                addChatMessage(Constant.OWNER_BOT, "车辆在图像右侧，向右移动...");
                double moveRightDistance = calculateMoveDistance(proportion);
                mSingletonVirtualStickExecutor.mGo(303, moveRightDistance);
                break;

            case "center":
            default:
                // 如果车辆已经处于画面中央，但占比 < 阈值，说明还比较远，可以向前飞一定距离
                if (proportion < PROPORTION_THRESHOLD) {
                    double moveDistance = calculateMoveDistance(proportion);
                    addChatMessage(Constant.OWNER_BOT,
                            String.format("车辆已大致位于中心，但占比为 %d%%，向前移动 %.2f 米靠近...", proportion, moveDistance));
                    mSingletonVirtualStickExecutor.mGo(301, moveDistance);
                } else {
                    isCenterAndClose=true;
                    addChatMessage(Constant.OWNER_BOT, "车辆已居中且接近，不需要移动。");
                }
                break;
        }
    }

    /**
     * 根据车辆在图像中的占比计算移动距离。
     * @param proportion 车辆占比（0-100）
     * @return 需要移动的距离（米）
     */
    private double calculateMoveDistance(int proportion) {
        if (proportion < MAX_MOVE_DISTANCE) {
            double distanceRange = MAX_MOVE_DISTANCE - MIN_MOVE_DISTANCE;
            double proportionRatio = (double)(PROPORTION_THRESHOLD - proportion) / PROPORTION_THRESHOLD;
            double moveDistance = MIN_MOVE_DISTANCE + (distanceRange * proportionRatio);
            moveDistance = Math.max(MIN_MOVE_DISTANCE, Math.min(moveDistance, MAX_MOVE_DISTANCE));
            return moveDistance;
        } else {
            return 0.0;
        }
    }

    /**
     * 拍照并识别车标品牌。
     * 如果使用 GPT，会调用 sendQuestionToGPT()；否则调用 sendQuestionToAPI()。
     */
    private void recognizeCarBrand() {
        // 1. 拍照
        Bitmap bitmap = fpvTexture.getBitmap();
        if (bitmap == null) {
            addChatMessage(Constant.OWNER_BOT, "未能捕获视频帧，TextureView 可能未准备好");
            return;
        }

        File brandImgFile = saveBitmapAsFile(bitmap, "frame.jpg");
        if (brandImgFile == null) {
            addChatMessage(Constant.OWNER_BOT, "拍照失败，无法识别车标...");
            return;
        }

        // 2. 构造识别请求
        String brandPrompt = "请识别图片中白色轿车的车标品牌。请给出 JSON 输出，如 {\"brand_name\":\"Toyota\"}";
        addChatMessage(Constant.OWNER_BOT, "正在识别车标，请稍候...");

        // 3. 调用 GPT 或 API
        sendQuestion(isGPT, brandPrompt, brandImgFile, new OnGptResultListener() {
            @Override
            public void onSuccess(String gptResult) {
                runOnUiThread(() -> {
                    // 4. 解析响应结果
                    JsonUtils.ParseResult brandParse = JsonUtils.robustJsonParser(gptResult);
                    if (brandParse.getJsonData() == null) {
                        addChatMessage(Constant.OWNER_BOT, "未能识别车标，JSON 数据为空。");
                        return;
                    }

                    String brandProcess = brandParse.getInferenceProcess();
                    String brandName = brandParse.getJsonData().optString("brand_name", "未知品牌");

                    addChatMessage(Constant.OWNER_BOT, "车标识别推理过程: " + brandProcess);
                    addChatMessage(Constant.OWNER_BOT, "识别到的品牌: " + brandName);
                });
            }

            @Override
            public void onFailure(Exception e) {
                runOnUiThread(() -> {
                    addChatMessage(Constant.OWNER_BOT, "调用模型出错: " + e.getMessage());
                    Log.e("recognizeCarBrand", "调用模型出错：" + e.getMessage());
                });
            }
        });
    }

    //endregion

    //region 自旋锁
    private String auavLock = null;
    synchronized void auavLock(String value) {
        auavLock = value;
    }

    public void auavSpin() {
        while (auavLock.equals("continue") == false) {
            try { Thread.sleep(1000); }
            catch (Exception e) {}
        }
    }
    //endregion

    //region 自动化执行逻辑

    /**
     * 自动化完成直播设置并开始推流
     */
    private void handleStartStreaming() {
        // 自动设置直播 URL
        if (mLiveStream.liveShowUrl == null || mLiveStream.liveShowUrl.isEmpty()) {
            showSetLiveUrlDialog(new OnDialogCompleteListener() {
                @Override
                public void onComplete() {
                    if (mLiveStream.liveShowUrl != null && !mLiveStream.liveShowUrl.isEmpty()) {
                        addChatMessage(Constant.OWNER_BOT, "直播地址已设置为：" + mLiveStream.liveShowUrl);

                        // 配置视频分辨率
                        mLiveStream.setResolution();
                        addChatMessage(Constant.OWNER_BOT, "直播视频分辨率设置成功");

                        // 配置视频比特率
                        mLiveStream.setBitRate();
                        addChatMessage(Constant.OWNER_BOT, "直播视频比特率设置为 " + mLiveStream.lastBitRate + " kbps。");

                        try {
                            //开启编码器
                            DJISDKManager.getInstance().getLiveStreamManager().setVideoEncodingEnabled(true);
                            // 开始推流
                            mLiveStream.startLiveShow();
                            isStreaming = true; // 记录推流状态
                            addChatMessage(Constant.OWNER_BOT, "直播推流已启动，地址：" + mLiveStream.liveShowUrl);
                        } catch (Exception e) {
                            addChatMessage(Constant.OWNER_BOT, "推流启动失败，错误信息：" + e.getMessage());
                        }
                    } else {
                        addChatMessage(Constant.OWNER_BOT, "未设置有效的直播地址，无法启动推流。");
                    }
                }
            });
            return; // 等待用户设置直播地址后再继续
        }

        try {
            //开启编码器
            DJISDKManager.getInstance().getLiveStreamManager().setVideoEncodingEnabled(true);
            // 开始推流
            mLiveStream.startLiveShow();
            isStreaming = true; // 记录推流状态
            addChatMessage(Constant.OWNER_BOT, "直播推流已启动，地址：" + mLiveStream.liveShowUrl);
        } catch (Exception e) {
            addChatMessage(Constant.OWNER_BOT, "推流启动失败，错误信息：" + e.getMessage());
        }
    }

    /**
     * 修改弹窗
     */
    private void handleModiferurl(){
        showModifyLiveUrlDialog();
    }

    /**
     * 自动化停止推流
     */
    private void handleStopStreaming(){
        mLiveStream.stopLiveShow();
    }

    /**
     * 自动化定位现在飞机位置
     */
    private void handleLocateDrone() {
        try {
            updateDroneLocation();
            cameraUpdate(new LatLng(mDroneLocation.latitude,mDroneLocation.longitude));
            addChatMessage(Constant.OWNER_BOT, "无人机位置已更新，并定位至地图视图。");
        } catch (Exception e) {
            addChatMessage(Constant.OWNER_BOT, "定位失败，错误信息：" + e.getMessage());
        }
    }

    /**
     * 自动化执行航点任务
     */
    private void handleExecuteMission() {
        if (mMissionOperator != null) {
            try {
                mWaypoint.startWaypointMission(mMissionOperator);
                addChatMessage(Constant.OWNER_BOT, "航点任务已启动。");
            } catch (Exception e) {
                addChatMessage(Constant.OWNER_BOT, "航点任务启动失败，错误信息：" + e.getMessage());
            }
        } else {
            addChatMessage(Constant.OWNER_BOT, "无法启动航点任务，任务操作对象未初始化。");
        }
    }

    /**
     * 自动执行目标识别任务
     * @param question
     */
    private void handleObjectIdentify(String question) {
        // 初始化图片文件对象
        File imageFile = new File("test.jpg");
        mTabLayout.getTabAt(1).select();
        try {
            // 从 mLiveStream 捕获视频流的一帧
            // 尝试捕获视频帧，最多重试3次，每次间隔500毫秒
            Bitmap bitmap = null;
            int retries = 3;
            int retryDelay = 500; // 毫秒

            for (int attempt = 0; attempt < retries; attempt++) {
                bitmap = fpvTexture.getBitmap();
                if (bitmap != null) {
                    break;
                }
                Thread.sleep(retryDelay);
            }

            if (bitmap == null) {
                throw new NullPointerException("未能捕获视频帧，TextureView 可能未准备好");
            }

            // 保存帧为图片文件
            imageFile = saveBitmapAsFile(bitmap, "frame.jpg");
            if (imageFile == null) {
                throw new IOException("图片保存失败");
            }

            // 反馈捕获成功
            addChatMessage(Constant.OWNER_BOT, "图像捕获成功，正在分析...");
            addChatMessage(Constant.OWNER_HUMAN,question.toString());
            addChatMessage(Constant.OWNER_HUMAN,bitmap);
            // 3. 思考中...
            addChatMessage(Constant.OWNER_BOT, "思考中...");
            //TODO
            //显示捕获的这一帧图像在对话框
            // 如果图片文件成功生成，则发送至大模型
            if(isGPT){
                sendQuestionToGPT(question, imageFile,true, new OnGptResultListener() {
                    @Override
                    public void onSuccess(String gptResult) {
                        showToast("成功");
                        addChatMessage(Constant.OWNER_BOT, gptResult);
                    }
                    @Override
                    public void onFailure(Exception e) {
                        addChatMessage(Constant.OWNER_BOT, "调用模型出错: " + e.getMessage());
                    }
                });
            }
            else {
                sendQuestionToAPI(question,imageFile);
            }
        } catch (NullPointerException e) {
            addChatMessage(Constant.OWNER_BOT, "摄像头未连接或未准备好，请检查设备连接状态");
            Log.e("ObjectIdentifyError", "摄像头错误：" + e.getMessage());
            return;
        } catch (IOException e) {
            addChatMessage(Constant.OWNER_BOT, "无法保存图片，请检查存储权限或存储空间");
            Log.e("ObjectIdentifyError", "保存图片失败：" + e.getMessage());
            return;
        } catch (Exception e) {
            addChatMessage(Constant.OWNER_BOT, "未知错误：" + e.getMessage());
            Log.e("ObjectIdentifyError", "未知错误：" + e.getMessage());
            return;
        }
    }

    /**
     * 自动执行删除航点任务
     * @param place
     */
    private void handleDeleteMission(String place) {
        Iterator<Place> iterator = Places.iterator();
        int index = 0;

        while (iterator.hasNext()) {
            Place currentPlace = iterator.next();
            if (currentPlace.name.equals(place)) {
                mWaypoint.RemoveWaypoint(currentPlace.lat, currentPlace.lon);
                mMarkers.get(index).remove();
                mMarkers.remove(index);
                iterator.remove(); // 如果需要从 Places 中移除该元素
                // 向前推进markers索引
                Map<Integer, Marker> updatedMarkers = new ConcurrentHashMap<>();
                for (Map.Entry<Integer, Marker> entry : mMarkers.entrySet()) {
                    int currentIndex = entry.getKey();
                    Marker marker = entry.getValue();
                    if (currentIndex > index) {
                        updatedMarkers.put(currentIndex - 1, marker);
                    } else {
                        updatedMarkers.put(currentIndex, marker);
                    }
                }
                mMarkers.clear();
                mMarkers.putAll(updatedMarkers);

                addChatMessage(Constant.OWNER_BOT, "已成功删除任务：" + place);
                break; // 假设 place 是唯一的，找到后即可退出循环
            }
            index++;
        }
    }

    /**
     * 自动执行配置任务
     */
    private void handleConfigMission() {
        try {
            showSettingDialog();
            addChatMessage(Constant.OWNER_BOT, "已配置完航点任务");
        } catch (Exception e) {
            addChatMessage(Constant.OWNER_BOT, "配置航点任务时发生错误: " + e.getMessage());
            e.printStackTrace();  // 打印详细的错误信息
        }
    }

    /**
     * 自动执行上传航点任务
     */
    private void handleUploadMission() {
        try {
            mWaypoint.uploadWayPointMission(mMissionOperator);
            addChatMessage(Constant.OWNER_BOT, "已上传所有航点任务");
        } catch (Exception e) {
            addChatMessage(Constant.OWNER_BOT, "上传航点任务时发生错误: " + e.getMessage());
            e.printStackTrace();  // 打印详细的错误信息
        }
    }

    /**
     * 自动执行转变手动添加模式
     */
    private void handleAddMission() {
        try {
            if (!isAdd) {
                isAdd = true;
                addChatMessage(Constant.OWNER_BOT, "现在是手动添加模式");
            } else {
                isAdd = false;
                addChatMessage(Constant.OWNER_BOT, "手动添加模式已关闭");
            }
        } catch (Exception e) {
            addChatMessage(Constant.OWNER_BOT, "切换手动添加模式时发生错误: " + e.getMessage());
            e.printStackTrace();  // 打印详细的错误信息
        }
    }

    /**
     * 自动执行停止航点任务
     */
    private void handleStopMission() {
        try {
            mWaypoint.stopWaypointMission(mMissionOperator);
            addChatMessage(Constant.OWNER_BOT, "已停止航点任务");
        } catch (Exception e) {
            addChatMessage(Constant.OWNER_BOT, "停止航点任务时发生错误: " + e.getMessage());
            e.printStackTrace();  // 打印详细的错误信息
        }
    }

    /**
     * 自动执行删除航点
     */
    private void handleDeleteWaypoint() {
        try {
            int count = mWaypoint.getWaypointCount(mMissionOperator);
            for (int i = 0; i < count; i++) {
                mWaypoint.RemoveWaypoint(i);
                mMarkers.get(i).remove();
                mMarkers.remove(i);
            }
            addChatMessage(Constant.OWNER_BOT, "已成功删除所有航点");
        } catch (Exception e) {
            addChatMessage(Constant.OWNER_BOT, "删除航点时发生错误: " + e.getMessage());
            e.printStackTrace();  // 打印详细的错误信息
        }
    }

    /**
     * 自动开启出现避障，视觉定位，返航避障等功能筛选的消息框，以打对钩的形式进行选择，点击确定完成设置
     */
    private void handleFlightAssistant() {
        if (mCI.mFlightController == null) {
            Toast.makeText(this, "无法获取FlightController，设备可能未连接或不支持", Toast.LENGTH_SHORT).show();
            return;
        }

        FlightAssistant flightAssistant = mCI.mFlightController.getFlightAssistant();
        if (flightAssistant == null) {
            Toast.makeText(this, "无法获取FlightAssistant，设备不支持智能飞行助手", Toast.LENGTH_SHORT).show();
            return;
        }

        // 2. 加载自定义布局
        LayoutInflater inflater = LayoutInflater.from(this);
        View dialogView = inflater.inflate(R.layout.flight_assistant_dialog, null);

        // 获取布局中的 CheckBox
        CheckBox cbCollisionAvoidance       = dialogView.findViewById(R.id.cb_collision_avoidance);
        CheckBox cbRthObstacleAvoidance     = dialogView.findViewById(R.id.cb_rth_avoidance);
        CheckBox cbActiveObstacleAvoidance  = dialogView.findViewById(R.id.cb_active_avoidance);
        CheckBox cbLandingProtection        = dialogView.findViewById(R.id.cb_landing_protection);
        CheckBox cbVisionPositioning        = dialogView.findViewById(R.id.cb_vision_positioning);

        // 在对话框显示前，将已有设置同步到 CheckBox，以便看到当前启用状态
        flightAssistant.getCollisionAvoidanceEnabled(new CommonCallbacks.CompletionCallbackWith<Boolean>() {
            @Override
            public void onSuccess(Boolean enabled) {
                // 注意，这里的 enabled 就是 true/false
                runOnUiThread(() -> {
                    // 在主线程更新 UI
                    cbCollisionAvoidance.setChecked(enabled);
                });
            }

            @Override
            public void onFailure(DJIError error) {
                // 获取失败，例如功能不支持，或者无人机没连接等
                Log.e("FlightAssistant", "getCollisionAvoidanceEnabled failed: " + error.getDescription());
            }
        });
        flightAssistant.getRTHObstacleAvoidanceEnabled(new CommonCallbacks.CompletionCallbackWith<Boolean>() {
            @Override
            public void onSuccess(Boolean enabled) {
                // 注意，这里的 enabled 就是 true/false
                runOnUiThread(() -> {
                    // 在主线程更新 UI
                    cbRthObstacleAvoidance.setChecked(enabled);
                });
            }

            @Override
            public void onFailure(DJIError error) {
                // 获取失败，例如功能不支持，或者无人机没连接等
                Log.e("FlightAssistant", "getCollisionAvoidanceEnabled failed: " + error.getDescription());
            }
        });
        flightAssistant.getActiveObstacleAvoidanceEnabled(new CommonCallbacks.CompletionCallbackWith<Boolean>() {
            @Override
            public void onSuccess(Boolean enabled) {
                // 注意，这里的 enabled 就是 true/false
                runOnUiThread(() -> {
                    // 在主线程更新 UI
                    cbActiveObstacleAvoidance.setChecked(enabled);
                });
            }

            @Override
            public void onFailure(DJIError error) {
                // 获取失败，例如功能不支持，或者无人机没连接等
                Log.e("FlightAssistant", "getCollisionAvoidanceEnabled failed: " + error.getDescription());
            }
        });
        flightAssistant.getVisionAssistedPositioningEnabled(new CommonCallbacks.CompletionCallbackWith<Boolean>() {
            @Override
            public void onSuccess(Boolean enabled) {
                // 注意，这里的 enabled 就是 true/false
                runOnUiThread(() -> {
                    // 在主线程更新 UI
                    cbLandingProtection.setChecked(enabled);
                });
            }

            @Override
            public void onFailure(DJIError error) {
                // 获取失败，例如功能不支持，或者无人机没连接等
                Log.e("FlightAssistant", "getCollisionAvoidanceEnabled failed: " + error.getDescription());
            }
        });
        flightAssistant.getLandingProtectionEnabled(new CommonCallbacks.CompletionCallbackWith<Boolean>() {
            @Override
            public void onSuccess(Boolean enabled) {
                // 注意，这里的 enabled 就是 true/false
                runOnUiThread(() -> {
                    // 在主线程更新 UI
                    cbVisionPositioning.setChecked(enabled);
                });
            }

            @Override
            public void onFailure(DJIError error) {
                // 获取失败，例如功能不支持，或者无人机没连接等
                Log.e("FlightAssistant", "getCollisionAvoidanceEnabled failed: " + error.getDescription());
            }
        });

        // 3. 构建对话框
        new AlertDialog.Builder(this)
                .setTitle("智能飞行助手设置")
                .setView(dialogView)
                .setPositiveButton("确定", (dialog, which) -> {
                    // 用户点击“确定”，开始设置选项
                    boolean collision = cbCollisionAvoidance.isChecked();
                    boolean rthAvoid  = cbRthObstacleAvoidance.isChecked();
                    boolean landing   = cbLandingProtection.isChecked();
                    boolean visionPos = cbVisionPositioning.isChecked();
                    boolean activeAvoid = cbActiveObstacleAvoidance.isChecked();

                    // 设置避障模式
                    flightAssistant.setCollisionAvoidanceEnabled(collision, djiError -> {
                        if (djiError == null) {
                            Log.d("FlightAssistant", "Collision Avoidance 设置成功: " + collision);
                        } else {
                            Log.e("FlightAssistant", "Collision Avoidance 设置失败: " + djiError.getDescription());
                        }
                    });

                    // 设置返航避障
                    flightAssistant.setRTHObstacleAvoidanceEnabled(rthAvoid, djiError -> {
                        if (djiError == null) {
                            Log.d("FlightAssistant", "RTH 避障 设置成功: " + rthAvoid);
                        } else {
                            Log.e("FlightAssistant", "RTH 避障 设置失败: " + djiError.getDescription());
                        }
                    });

                    //设置主动避障
                    flightAssistant.setActiveObstacleAvoidanceEnabled(rthAvoid, djiError -> {
                        if (djiError == null) {
                            Log.d("FlightAssistant", "RTH 避障 设置成功: " + activeAvoid);
                        } else {
                            Log.e("FlightAssistant", "RTH 避障 设置失败: " + djiError.getDescription());
                        }
                    });

                    // 设置着陆保护
                    flightAssistant.setLandingProtectionEnabled(landing, djiError -> {
                        if (djiError == null) {
                            Log.d("FlightAssistant", "Landing Protection 设置成功: " + landing);
                        } else {
                            Log.e("FlightAssistant", "Landing Protection 设置失败: " + djiError.getDescription());
                        }
                    });

                    // 设置视觉定位
                    flightAssistant.setVisionAssistedPositioningEnabled(visionPos, djiError -> {
                        if (djiError == null) {
                            Log.d("FlightAssistant", "Vision Positioning 设置成功: " + visionPos);
                        } else {
                            Log.e("FlightAssistant", "Vision Positioning 设置失败: " + djiError.getDescription());
                        }
                    });


                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 自动定位到手机和遥控器位置
     */
    private void handleUserLocation(){

        cameraUpdate(new LatLng(mUserLocation.latitude,mUserLocation.longitude));
    }
    //endregion

    //region 辅助函数

    /**
     * 阻塞主线程
     */
    private void SleepThread(int time){
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            addChatMessage(Constant.OWNER_BOT, "线程被中断");
        }
    }

    /**
     * 获取当前帧图像
     */
    public File CaptureImage(){

        Resources res = getResources();
        Bitmap bitmap = BitmapFactory.decodeResource(res, R.drawable.car);
        File imageFile = saveBitmapAsFile(bitmap,"frame1.jpg");

//        Bitmap bitmap = fpvTexture.getBitmap();
//        if (bitmap == null) {
//            addChatMessage(Constant.OWNER_BOT, "未能捕获视频帧，TextureView 未准备好");
//            return null;
//        }
//
//        File imageFile = saveBitmapAsFile(bitmap, IMAGE_FILE_NAME);
//        if (imageFile == null) {
//            addChatMessage(Constant.OWNER_BOT, "图片保存失败");
//            return null;
//        }
        return imageFile;
    }

    /**
     * 完成回调接口
     */
    public interface OnDialogCompleteListener {
        void onComplete();
    }

    /**
     * 弹窗修改直播 URL
     */
    private void showModifyLiveUrlDialog() {
        // 创建 EditText 让用户输入新的 URL
        final EditText input = new EditText(this);
        input.setHint("请输入新的直播地址");
        input.setText(mLiveStream.liveShowUrl); // 显示当前的直播地址作为默认值

        // 创建 AlertDialog
        new AlertDialog.Builder(this)
                .setTitle("修改直播地址")
                .setView(input)
                .setCancelable(false) // 防止用户直接取消弹窗
                .setPositiveButton("确定", (dialog, which) -> {
                    String newUrl = input.getText().toString().trim();
                    if (isValidLiveUrl(newUrl)) { // 验证 URL 格式
                        modifyLiveUrl(newUrl);
                    } else {
                        addChatMessage(Constant.OWNER_BOT, "无效的直播地址，请重新输入！");
                        showModifyLiveUrlDialog(); // 再次显示弹窗
                    }
                })
                .setNegativeButton("取消", (dialog, which) -> {
                    addChatMessage(Constant.OWNER_BOT, "直播地址修改已取消。");
                })
                .show();
    }

    /**
     * 修改直播地址的方法
     * @param newUrl 新的直播地址
     */
    private void modifyLiveUrl(String newUrl) {
        // 停止当前的直播推流
        if (isStreaming) {
            try {
                mLiveStream.stopLiveShow();
                isStreaming = false;
                addChatMessage(Constant.OWNER_BOT, "当前推流已停止，准备修改直播地址。");
            } catch (Exception e) {
                addChatMessage(Constant.OWNER_BOT, "停止推流失败，错误信息：" + e.getMessage());
                return;
            }
        }

        // 更新直播地址
        mLiveStream.liveShowUrl = newUrl;
        addChatMessage(Constant.OWNER_BOT, "直播地址已更新为：" + mLiveStream.liveShowUrl);

        // 重新配置视频参数
        mLiveStream.setResolution();
        addChatMessage(Constant.OWNER_BOT, "直播视频分辨率设置成功");

        mLiveStream.setBitRate();
        addChatMessage(Constant.OWNER_BOT, "直播视频比特率设置为 " + mLiveStream.lastBitRate + " kbps。");

        try {
            // 开启编码器
            DJISDKManager.getInstance().getLiveStreamManager().setVideoEncodingEnabled(true);
            // 重新开始推流
            mLiveStream.startLiveShow();
            isStreaming = true;
            addChatMessage(Constant.OWNER_BOT, "直播推流已重新启动，新的地址：" + mLiveStream.liveShowUrl);
        } catch (Exception e) {
            addChatMessage(Constant.OWNER_BOT, "重新启动推流失败，错误信息：" + e.getMessage());
        }
    }

    /**
     * 弹窗输入直播 URL
     */
    private void showSetLiveUrlDialog(@Nullable OnDialogCompleteListener listener) {
        // 创建 EditText 让用户输入 URL
        final EditText input = new EditText(this);
        input.setHint("请输入直播地址");
        input.setText("rtmp://your-server-address/live/stream"); // 提供默认值

        // 创建 AlertDialog
        new AlertDialog.Builder(this)
                .setTitle("设置直播地址")
                .setView(input)
                .setCancelable(false) // 防止用户直接取消弹窗
                .setPositiveButton("确定", (dialog, which) -> {
                    String newUrl = input.getText().toString().trim();
                    if (isValidLiveUrl(newUrl)) { // 验证 URL 格式
                        mLiveStream.liveShowUrl = newUrl;
                        addChatMessage(Constant.OWNER_BOT, "直播地址已更新为：" + mLiveStream.liveShowUrl);

                        // 如果外面传了 listener，就通知它
                        if (listener != null) {
                            listener.onComplete();
                        }
                    } else {
                        addChatMessage(Constant.OWNER_BOT, "无效的直播地址，请重新输入！");
                        showSetLiveUrlDialog(listener); // 再次显示弹窗
                    }
                })
                .setNegativeButton("取消", (dialog, which) -> {
                    addChatMessage(Constant.OWNER_BOT, "未设置直播地址，无法继续！");
                })
                .show();
    }

    /**
     * 验证直播 URL 的格式
     */
    private boolean isValidLiveUrl(String url) {
        String regex = "^(rtmp|rtsp|http|https)://[a-zA-Z0-9._-]+(:[0-9]+)?(/[a-zA-Z0-9._-]+)*$";
        return url.matches(regex);
    }

    /**
     * 保存为图像文件
     * @param bitmap
     * @param filename
     * @return
     */
    private File saveBitmapAsFile(Bitmap bitmap, String filename) {
        File file = new File(getCacheDir(), filename); // 保存到应用的缓存目录
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out); // 压缩并保存为 JPEG
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        return file;
    }

    /**
     * 帮助界面
     */
    private void showHelpDialog() {
        // 创建指令示例内容
        String helpMessage = "这是你可以跟控制大模型对话的命令:\n\n" +
                "1. 开始推流:RTSP推流到局域网\n" +
                "2. 定位:定位无人机当前位置\n" +
                "3. 执行任务：执行航点任务\n" +
                "4. 识别+问题：分析当前帧的图片内容\n" +
                "5. 无人机控制命令：起飞，降落，向(方向)移动(路程值)米，向(方向)转(角度值)度\n" +
                "6. 飞到+目的地:添加目的地航点\n" +
                "7. 删除+目的地:删除目的地航点\n" +
                "8. 配置航点：配置航点速度，高度，结束后的动作，航线方向\n" +
                "9. 清除航点: 清除所有的航点\n" +
                "11. 上传航点：上传航点到无人机\n" +
                "12. 停止任务：停止航点任务\n" +
                "13. 手动添加：开启手动添加，点击地图即可添加航点\n" +
                "14. 高度+高度值：最大飞行高度设置\n" +
                "15. 返航+高度值：返回起始点时的高度设置\n" +
                "16. 速度+速度值：最大飞行速度设置\n" +
                "17. 开启智能飞行助手：开启避障，视觉定位等功能\n" +
                "18. 用户追踪：定位到用户所在的位置\n" +
                "19. 修改地址:停止推流，修改直播地址并重新开启推流\n" +
                "20. 飞向+lat,lon：飞向指定点\n" +
                "21. 俯视图：切换俯视\n" +
                "22. 前视图：切换前视\n" +
                "23. 自动搜索：自动搜索白车靠近并识别车标";

        // 创建并显示消息框
        new AlertDialog.Builder(this)
                .setTitle("帮助-命令")
                .setMessage(helpMessage)
                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                .setIcon(android.R.drawable.ic_dialog_info)
                .show();
    }

    /**
     * 展示飞行数据
     */
    private void showFlightDataDialog() {
        // 创建一个新的 AlertDialog，并初始化一个 TextView 来显示数据
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("飞行数据实时更新");

        // 创建一个 TextView，显示初始的飞行数据
        final TextView textView = new TextView(this);
        textView.setText("加载中...");
        builder.setView(textView);

        // 设置对话框的按钮
        builder.setPositiveButton("关闭", (dialog, which) -> dialog.dismiss());

        // 显示对话框
        final AlertDialog dialog = builder.create();
        dialog.show();

        // 创建一个 Handler 来定时更新飞行数据
        final Handler handler = new Handler();

        // 使用 Runnable 来定时更新数据
        Runnable updateFlightDataRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    // 假设获取飞行数据
                    if (mCI != null && mCI.getmSingletonVirtualStickExecutor() != null) {
                        mCI.getmSingletonVirtualStickExecutor().getFlight(mFlightData);
                    }

                    // 检查飞行数据是否为空
                    if (mFlightData == null) {
                        throw new NullPointerException("飞行数据为空！");
                    }

                    // 确保其他飞行数据有效，防止为空
                    double latitude = mDroneLocation != null ? mDroneLocation.latitude : 0.0;
                    double longitude = mDroneLocation != null ? mDroneLocation.longitude : 0.0;
                    double altitude = (mAltitudeData != -1) ? mAltitudeData : 0.0;
                    double heading = (mDroneHeading != -1) ? mDroneHeading : 0.0;
                    double horizontalSpeed = (mhs != -1) ? mhs : 0.0;
                    double verticalSpeed = (mvs != -1) ? mvs : 0.0;
                    double distanceToHome = (mdistToHome != -1) ? mdistToHome : 0.0;
                    float  takeoffLocationAltitude = (mCI.mFlightController != null) ? mCI.mFlightController.getState().getTakeoffLocationAltitude() : 0.0f;
                    double pitch = (mCI.mFlightController != null) ? mCI.mFlightController.getState().getAttitude().pitch : 0.0f;
                    double roll = (mCI.mFlightController != null) ? mCI.mFlightController.getState().getAttitude().roll : 0.0f;
                    double yaw = (mCI.mFlightController != null) ? mCI.mFlightController.getState().getAttitude().yaw : 0.0f;
                    double throttle = (mFlightData != null) ? mFlightData.getThrottle() : 0.0f;

                    // 在每次更新时刷新飞行数据
                    String flightDataMessage = String.format(
                            "当前无人机位置: (%.6f, %.6f)\n" +
                                    "当前高度: %.2f 米\n" +
                                    "航向: %.2f 度\n" +
                                    "水平速度: %.2f 米/秒\n" +
                                    "垂直速度: %.2f 米/秒\n" +
                                    "距离家点: %.2f 米\n" +
                                    "俯仰角 (Pitch): %.2f 米/s\n" +
                                    "滚转角 (Roll): %.2f 米/s\n" +
                                    "偏航角 (Yaw): %.2f 度\n" +
                                    "油门 (Throttle): %.2f\n" +
                                    "起飞高度(altitude): %.2f 米\n" +
                                    "电机是否打开: %s \n",
                            latitude, longitude, altitude, heading, horizontalSpeed, verticalSpeed, distanceToHome,
                            pitch, roll, yaw, throttle,takeoffLocationAltitude,mCI.mFlightController.getState().areMotorsOn()
                    );

                    // 动态更新 TextView 中的飞行数据
                    textView.setText(flightDataMessage);

                } catch (Exception e) {
                    // 捕获异常并显示错误信息
                    textView.setText("无法获取飞行数据: " + e.getMessage());
                }

                // 每隔1秒更新一次飞行数据
                handler.postDelayed(this, 1000);  // 每隔1秒刷新一次数据
            }
        };

        // 开始定时更新
        handler.post(updateFlightDataRunnable);
    }

    /**
     * 可视化配置航点界面
     */
    private void showSettingDialog() {
        LinearLayout wayPointSettings = (LinearLayout) getLayoutInflater().inflate(R.layout.dialog_waypoint2setting, null);

        final TextView wpAltitude_TV = (TextView) wayPointSettings.findViewById(R.id.altitude);
        RadioGroup speed_RG = (RadioGroup) wayPointSettings.findViewById(R.id.speed);
        RadioGroup actionAfterFinished_RG = (RadioGroup) wayPointSettings.findViewById(R.id.actionAfterFinished);
        RadioGroup heading_RG = (RadioGroup) wayPointSettings.findViewById(R.id.heading);
        RadioGroup firstModeRg = wayPointSettings.findViewById(R.id.go_to_first_mode);

        firstModeRg.setOnCheckedChangeListener((group, checkedId) -> {
            switch (checkedId) {
                case R.id.rb_p2p:
                    mWaypoint.firstMode = WaypointV2MissionTypes.MissionGotoWaypointMode.POINT_TO_POINT;
                    break;
                case R.id.rb_safely:
                    mWaypoint.firstMode = WaypointV2MissionTypes.MissionGotoWaypointMode.SAFELY;
                    break;
            }
        });

        speed_RG.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == R.id.lowSpeed) {
                    mWaypoint.mSpeed = 3.0f;
                } else if (checkedId == R.id.MidSpeed) {
                    mWaypoint.mSpeed = 5.0f;
                } else if (checkedId == R.id.HighSpeed) {
                    mWaypoint.mSpeed = 10.0f;
                }
            }

        });

        actionAfterFinished_RG.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                Log.d(TAG, "Select finish action");
                if (checkedId == R.id.finishNone) {
                    mWaypoint.mFinishedAction = WaypointV2MissionTypes.MissionFinishedAction.NO_ACTION;
                } else if (checkedId == R.id.finishGoHome) {
                    mWaypoint.mFinishedAction = WaypointV2MissionTypes.MissionFinishedAction.GO_HOME;
                } else if (checkedId == R.id.finishAutoLanding) {
                    mWaypoint.mFinishedAction = WaypointV2MissionTypes.MissionFinishedAction.AUTO_LAND;
                } else if (checkedId == R.id.finishToFirst) {
                    mWaypoint.mFinishedAction = WaypointV2MissionTypes.MissionFinishedAction.GO_FIRST_WAYPOINT;
                }
//                else if (checkedId == R.id.untilStop) {
//                    mFinishedAction = WaypointV2MissionTypes.MissionFinishedAction.CONTINUE_UNTIL_STOP;
//                }
            }
        });

        heading_RG.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                Log.d(TAG, "Select heading");

                if (checkedId == R.id.headingNext) {
                    mWaypoint.mHeadingMode = WaypointMissionHeadingMode.AUTO;
                } else if (checkedId == R.id.headingInitDirec) {
                    mWaypoint.mHeadingMode = WaypointMissionHeadingMode.USING_INITIAL_DIRECTION;
                } else if (checkedId == R.id.headingRC) {
                    mWaypoint.mHeadingMode = WaypointMissionHeadingMode.CONTROL_BY_REMOTE_CONTROLLER;
                } else if (checkedId == R.id.headingWP) {
                    mWaypoint.mHeadingMode = WaypointMissionHeadingMode.USING_WAYPOINT_HEADING;
                }
            }
        });

        new AlertDialog.Builder(this)
                .setTitle("")
                .setView(wayPointSettings)
                .setPositiveButton("Finish", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {

                        String altitudeString = wpAltitude_TV.getText().toString();
                        mWaypoint.altitude = Integer.parseInt(nulltoIntegerDefalt(altitudeString));
                        Log.e(TAG, "altitude " + mWaypoint.altitude);
                        Log.e(TAG, "speed " + mWaypoint.mSpeed);
                        Log.e(TAG, "mFinishedAction " + mWaypoint.mFinishedAction);
                        Log.e(TAG, "mHeadingMode " + mWaypoint.mHeadingMode);
                        try{
                            mWaypoint.configWayPointMission(mMissionOperator);
                        }
                        catch (Exception e){
                            addChatMessage(Constant.OWNER_BOT, "配置航点任务时发生错误: " + e.getMessage());
                            e.printStackTrace();  // 打印详细的错误信息
                        }
                    }

                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }

                })
                .create()
                .show();
    }

    /**
     * 显示诊断信息框
     */
    private void showDiagnosticsDialog(String diagnosticsMessage) {
        new AlertDialog.Builder(this)
                .setTitle("设备诊断信息")
                .setMessage(diagnosticsMessage)
                .setPositiveButton("确定", null)
                .setCancelable(true)
                .show();
    }

    /**
     * 转换函数
     * @param value
     * @return
     */
    String nulltoIntegerDefalt(String value) {
        if (!isIntValue(value)) {
            value = "0";
        }
        return value;
    }
    boolean isIntValue(String val) {
        try {
            val = val.replace(" ", "");
            Integer.parseInt(val);
        } catch (Exception e) {
            return false;
        }
        return true;
    }
    //endregion

    //region 云台控制

    /**
     * 让云台俯视（垂直向下）
     */
    private void rotateGimbalDownwardView() {
        // 对于绝大多数 DJI 云台来说，-90° 表示正对地面
        rotateGimbal(-90.0f, 0.0f, 0.0f);
    }

    /**
     * 让云台前视（水平向前）
     */
    private void rotateGimbalForwardView() {
        // 0° 表示水平前视
        rotateGimbal(0.0f, 0.0f, 0.0f);
    }

    /**
     * 以绝对坐标系旋转相机云台
     * @param pitchValue
     * @param yawValue
     * @param rollValue
     */
    private void rotateGimbal(float pitchValue,float yawValue,float rollValue) {

        Rotation rotation = new Rotation.Builder().pitch(pitchValue)
                .mode(RotationMode.ABSOLUTE_ANGLE)
                .yaw(yawValue)
                .roll(rollValue)
                .time(0)
                .build();

        sendRotateGimbalCommand(rotation);
    }

    /**
     * 发送旋转命令到云台
     * @param rotation
     */
    private void sendRotateGimbalCommand(Rotation rotation) {

        Gimbal gimbal = getGimbalInstance();
        if (gimbal == null) {
            return;
        }
        gimbal.rotate(rotation, new CallbackHandlers.CallbackToastHandler());
    }

    /**
     * 得到云台对象实例
     * @return
     */
    private Gimbal getGimbalInstance() {
        if (gimbal == null) {
            initGimbal();
        }
        return gimbal;
    }

    /**
     * 初始化云台
     */
    private void initGimbal() {
        if (DJISDKManager.getInstance() != null) {
            BaseProduct product = DJISDKManager.getInstance().getProduct();
            if (product != null) {
                if (product instanceof Aircraft) {
                    gimbal = ((Aircraft) product).getGimbals().get(currentGimbalId);
                } else {
                    gimbal = product.getGimbal();
                }
            }
        }
    }
    //endregion
}
