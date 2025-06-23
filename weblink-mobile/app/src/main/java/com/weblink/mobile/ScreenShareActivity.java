package com.weblink.mobile;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.weblink.mobile.service.ScreenCaptureService;
import com.weblink.mobile.utils.QRCodeGenerator;
import com.weblink.mobile.webrtc.SignalingClient;
import com.weblink.mobile.webrtc.WebRTCClient;

import org.webrtc.IceCandidate;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.SessionDescription;

/**
 * 屏幕共享活动
 */
public class ScreenShareActivity extends AppCompatActivity implements WebRTCClient.PeerConnectionObserver, SignalingClient.SignalingClientListener {
    private static final String TAG = "ScreenShareActivity";
    private static final int SCREEN_CAPTURE_REQUEST_CODE = 1001;
    private static final int PERMISSION_REQUEST_CODE = 1002;
    
    // UI组件
    private Toolbar toolbar;
    private TextView invitationCodeTextView;
    private ImageView qrCodeImageView;
    private ImageButton shareCodeButton;
    private Button startSharingButton;
    private Button stopSharingButton;
    private View statusIndicator;
    private TextView statusTextView;
    
    // WebRTC相关
    private WebRTCClient webRTCClient;
    private SignalingClient signalingClient;
    
    // 屏幕捕获服务
    private ScreenCaptureService screenCaptureService;
    private boolean isBound = false;
    
    // 状态变量
    private String invitationCode;
    private boolean isCreator;
    private boolean isConnected = false;
    private boolean isSharing = false;
    
    // 信令服务器地址，可以根据需要修改
    private static final String SIGNALING_SERVER_URL = "https://your-signaling-server.com";
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_screen_share);
        
        // 获取Intent参数
        invitationCode = getIntent().getStringExtra("invitationCode");
        isCreator = getIntent().getBooleanExtra("isCreator", true);
        
        // 初始化UI组件
        initViews();
        
        // 初始化WebRTC
        initWebRTC();
        
        // 设置按钮点击事件
        setupClickListeners();
        
        // 更新UI状态
        updateUIState();
        
        // 绑定屏幕捕获服务
        bindScreenCaptureService();
        
        // 连接信令服务器
        connectToSignalingServer();
    }
    
    /**
     * 初始化视图组件
     */
    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        
        invitationCodeTextView = findViewById(R.id.invitationCodeTextView);
        qrCodeImageView = findViewById(R.id.qrCodeImageView);
        shareCodeButton = findViewById(R.id.shareCodeButton);
        startSharingButton = findViewById(R.id.startSharingButton);
        stopSharingButton = findViewById(R.id.stopSharingButton);
        statusIndicator = findViewById(R.id.statusIndicator);
        statusTextView = findViewById(R.id.statusTextView);
        
        // 设置邀请码
        invitationCodeTextView.setText(invitationCode);
        
        // 生成二维码
        generateQRCode();
    }
    
    /**
     * 初始化WebRTC
     */
    private void initWebRTC() {
        webRTCClient = new WebRTCClient(this, this);
        signalingClient = new SignalingClient(this);
    }
    
    /**
     * 设置点击监听器
     */
    private void setupClickListeners() {
        // 开始共享按钮
        startSharingButton.setOnClickListener(v -> {
            if (isConnected) {
                startScreenCapture();
            } else {
                showToast(getString(R.string.waiting_for_peer));
            }
        });
        
        // 停止共享按钮
        stopSharingButton.setOnClickListener(v -> {
            stopScreenSharing();
        });
        
        // 分享邀请码按钮
        shareCodeButton.setOnClickListener(v -> {
            shareInvitationCode();
        });
    }
    
    /**
     * 更新UI状态
     */
    private void updateUIState() {
        runOnUiThread(() -> {
            if (isConnected) {
                statusIndicator.setBackground(ContextCompat.getDrawable(this, R.drawable.status_indicator_connected));
                statusTextView.setText(R.string.connected);
                
                if (isSharing) {
                    startSharingButton.setVisibility(View.GONE);
                    stopSharingButton.setVisibility(View.VISIBLE);
                } else {
                    startSharingButton.setVisibility(View.VISIBLE);
                    stopSharingButton.setVisibility(View.GONE);
                }
            } else {
                statusIndicator.setBackground(ContextCompat.getDrawable(this, R.drawable.status_indicator_disconnected));
                statusTextView.setText(R.string.disconnected);
                startSharingButton.setVisibility(View.VISIBLE);
                stopSharingButton.setVisibility(View.GONE);
            }
        });
    }
    
    /**
     * 生成二维码
     */
    private void generateQRCode() {
        String qrContent = "weblink:" + invitationCode;
        Bitmap qrBitmap = QRCodeGenerator.generateQRCode(qrContent, 500, 500);
        if (qrBitmap != null) {
            qrCodeImageView.setImageBitmap(qrBitmap);
        }
    }
    
    /**
     * 分享邀请码
     */
    private void shareInvitationCode() {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name));
        shareIntent.putExtra(Intent.EXTRA_TEXT, getString(R.string.invitation_code) + ": " + invitationCode);
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_code)));
    }
    
    /**
     * 连接到信令服务器
     */
    private void connectToSignalingServer() {
        statusTextView.setText(R.string.connecting);
        statusIndicator.setBackground(ContextCompat.getDrawable(this, R.drawable.status_indicator_connecting));
        
        signalingClient.connect(SIGNALING_SERVER_URL, invitationCode, this);
    }
    
    /**
     * 启动屏幕捕获
     */
    private void startScreenCapture() {
        MediaProjectionManager mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        Intent captureIntent = mediaProjectionManager.createScreenCaptureIntent();
        startActivityForResult(captureIntent, SCREEN_CAPTURE_REQUEST_CODE);
    }
    
    /**
     * 停止屏幕共享
     */
    private void stopScreenSharing() {
        isSharing = false;
        
        if (webRTCClient != null) {
            webRTCClient.setVideoEnabled(false);
        }
        
        updateUIState();
        
        if (screenCaptureService != null) {
            screenCaptureService.updateNotification(getString(R.string.waiting_for_peer));
        }
    }
    
    /**
     * 绑定屏幕捕获服务
     */
    private void bindScreenCaptureService() {
        Intent serviceIntent = new Intent(this, ScreenCaptureService.class);
        serviceIntent.putExtra("roomId", invitationCode);
        startForegroundService(serviceIntent);
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }
    
    /**
     * 服务连接
     */
    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            ScreenCaptureService.LocalBinder binder = (ScreenCaptureService.LocalBinder) service;
            screenCaptureService = binder.getService();
            isBound = true;
            
            screenCaptureService.setRoomId(invitationCode);
        }
        
        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
        }
    };
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == SCREEN_CAPTURE_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                // 创建PeerConnection
                webRTCClient.createPeerConnection();
                
                // 创建屏幕捕获器
                webRTCClient.createScreenCapturer(data);
                
                // 创建音频轨道
                webRTCClient.createAudioTrack();
                
                // 添加本地媒体流
                webRTCClient.addLocalMediaStream();
                
                // 如果是创建者，创建offer
                if (isCreator) {
                    webRTCClient.createOffer();
                }
                
                isSharing = true;
                updateUIState();
                
                if (screenCaptureService != null) {
                    screenCaptureService.updateNotification(getString(R.string.sharing_active));
                }
            } else {
                showToast(getString(R.string.screen_permission_required));
            }
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allPermissionsGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false;
                    break;
                }
            }
            
            if (allPermissionsGranted) {
                startScreenCapture();
            } else {
                showToast(getString(R.string.permission_required));
            }
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // 断开WebRTC连接
        if (webRTCClient != null) {
            webRTCClient.dispose();
        }
        
        // 断开信令连接
        if (signalingClient != null) {
            signalingClient.sendBye();
            signalingClient.disconnect();
        }
        
        // 解绑服务
        if (isBound) {
            unbindService(serviceConnection);
            isBound = false;
        }
        
        // 停止服务
        Intent serviceIntent = new Intent(this, ScreenCaptureService.class);
        stopService(serviceIntent);
    }
    
    /**
     * 显示Toast消息
     */
    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
    
    // WebRTCClient.PeerConnectionObserver 接口实现
    
    @Override
    public void onIceCandidate(IceCandidate iceCandidate) {
        signalingClient.sendIceCandidate(iceCandidate.sdp, iceCandidate.sdpMid, iceCandidate.sdpMLineIndex);
    }
    
    @Override
    public void onAddStream(MediaStream mediaStream) {
        // 在屏幕共享中，我们不需要处理远程流
    }
    
    @Override
    public void onRemoveStream(MediaStream mediaStream) {
        // 在屏幕共享中，我们不需要处理远程流
    }
    
    @Override
    public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
        Log.d(TAG, "ICE连接状态变化: " + iceConnectionState);
        
        if (iceConnectionState == PeerConnection.IceConnectionState.CONNECTED) {
            isConnected = true;
            updateUIState();
        } else if (iceConnectionState == PeerConnection.IceConnectionState.DISCONNECTED || 
                   iceConnectionState == PeerConnection.IceConnectionState.FAILED) {
            isConnected = false;
            isSharing = false;
            updateUIState();
        }
    }
    
    @Override
    public void onRenegotiationNeeded() {
        if (isCreator) {
            webRTCClient.createOffer();
        }
    }
    
    @Override
    public void onLocalDescription(SessionDescription sdp) {
        if (sdp.type == SessionDescription.Type.OFFER) {
            signalingClient.sendOffer(sdp.description);
        } else if (sdp.type == SessionDescription.Type.ANSWER) {
            signalingClient.sendAnswer(sdp.description);
        }
    }
    
    @Override
    public void onScreenCaptureError(String error) {
        showToast(error);
        isSharing = false;
        updateUIState();
    }
    
    // SignalingClient.SignalingClientListener 接口实现
    
    @Override
    public void onConnectionEstablished() {
        Log.d(TAG, "信令连接已建立");
        
        if (isCreator) {
            signalingClient.createRoom();
        } else {
            signalingClient.joinRoom();
        }
    }
    
    @Override
    public void onConnectionClosed() {
        Log.d(TAG, "信令连接已关闭");
        isConnected = false;
        updateUIState();
    }
    
    @Override
    public void onConnectionError(String error) {
        Log.e(TAG, "信令连接错误: " + error);
        showToast(getString(R.string.connection_failed) + ": " + error);
        isConnected = false;
        updateUIState();
    }
    
    @Override
    public void onRoomCreated() {
        Log.d(TAG, "房间已创建");
        runOnUiThread(() -> {
            statusTextView.setText(R.string.waiting_for_peer);
        });
    }
    
    @Override
    public void onRoomJoined() {
        Log.d(TAG, "已加入房间");
        signalingClient.sendReady();
    }
    
    @Override
    public void onRoomFull() {
        Log.d(TAG, "房间已满");
        showToast(getString(R.string.room_not_found));
        finish();
    }
    
    @Override
    public void onPeerReady() {
        Log.d(TAG, "对方已准备就绪");
        isConnected = true;
        updateUIState();
        
        if (!isCreator) {
            webRTCClient.createPeerConnection();
        }
    }
    
    @Override
    public void onOfferReceived(String sdp) {
        Log.d(TAG, "收到offer");
        SessionDescription sessionDescription = new SessionDescription(SessionDescription.Type.OFFER, sdp);
        webRTCClient.setRemoteDescription(sessionDescription);
        webRTCClient.createAnswer();
    }
    
    @Override
    public void onAnswerReceived(String sdp) {
        Log.d(TAG, "收到answer");
        SessionDescription sessionDescription = new SessionDescription(SessionDescription.Type.ANSWER, sdp);
        webRTCClient.setRemoteDescription(sessionDescription);
    }
    
    @Override
    public void onIceCandidateReceived(String candidate, String sdpMid, int sdpMLineIndex) {
        Log.d(TAG, "收到ICE候选者");
        IceCandidate iceCandidate = new IceCandidate(sdpMid, sdpMLineIndex, candidate);
        webRTCClient.addIceCandidate(iceCandidate);
    }
    
    @Override
    public void onPeerDisconnected() {
        Log.d(TAG, "对方已断开连接");
        isConnected = false;
        isSharing = false;
        updateUIState();
        showToast(getString(R.string.peer_disconnected));
    }
} 