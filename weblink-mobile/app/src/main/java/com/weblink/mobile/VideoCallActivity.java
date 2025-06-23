package com.weblink.mobile;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.weblink.mobile.utils.QRCodeGenerator;
import com.weblink.mobile.webrtc.SignalingClient;
import com.weblink.mobile.webrtc.WebRTCClient;

import org.webrtc.IceCandidate;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceViewRenderer;

/**
 * 视频通话活动
 */
public class VideoCallActivity extends AppCompatActivity implements WebRTCClient.PeerConnectionObserver, SignalingClient.SignalingClientListener {
    private static final String TAG = "VideoCallActivity";
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
    };
    
    // UI组件
    private Toolbar toolbar;
    private TextView invitationCodeTextView;
    private ImageView qrCodeImageView;
    private ImageButton shareCodeButton;
    private SurfaceViewRenderer localVideoView;
    private SurfaceViewRenderer remoteVideoView;
    private ImageButton switchCameraButton;
    private ImageButton muteButton;
    private Button endCallButton;
    private View statusIndicator;
    private TextView statusTextView;
    
    // WebRTC相关
    private WebRTCClient webRTCClient;
    private SignalingClient signalingClient;
    
    // 状态变量
    private String invitationCode;
    private boolean isCreator;
    private boolean isConnected = false;
    private boolean isAudioEnabled = true;
    private boolean isFrontCamera = true;
    
    // 信令服务器地址，可以根据需要修改
    private static final String SIGNALING_SERVER_URL = "https://your-signaling-server.com";
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_call);
        
        // 获取Intent参数
        invitationCode = getIntent().getStringExtra("invitationCode");
        isCreator = getIntent().getBooleanExtra("isCreator", true);
        
        // 初始化UI组件
        initViews();
        
        // 检查权限
        if (checkPermissions()) {
            // 初始化WebRTC
            initWebRTC();
            
            // 设置按钮点击事件
            setupClickListeners();
            
            // 更新UI状态
            updateUIState();
            
            // 连接信令服务器
            connectToSignalingServer();
            
            // 启动摄像头
            startCamera();
        } else {
            requestPermissions();
        }
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
        localVideoView = findViewById(R.id.localVideoView);
        remoteVideoView = findViewById(R.id.remoteVideoView);
        switchCameraButton = findViewById(R.id.switchCameraButton);
        muteButton = findViewById(R.id.muteButton);
        endCallButton = findViewById(R.id.endCallButton);
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
        
        // 初始化视频渲染器
        webRTCClient.initSurfaceRenderer(localVideoView, remoteVideoView);
    }
    
    /**
     * 设置点击监听器
     */
    private void setupClickListeners() {
        // 切换摄像头按钮
        switchCameraButton.setOnClickListener(v -> {
            if (webRTCClient != null) {
                webRTCClient.switchCamera();
                isFrontCamera = !isFrontCamera;
            }
        });
        
        // 静音按钮
        muteButton.setOnClickListener(v -> {
            if (webRTCClient != null) {
                isAudioEnabled = !isAudioEnabled;
                webRTCClient.setAudioEnabled(isAudioEnabled);
                muteButton.setImageResource(isAudioEnabled ? 
                        R.drawable.ic_mic : R.drawable.ic_mic_off);
            }
        });
        
        // 结束通话按钮
        endCallButton.setOnClickListener(v -> {
            finish();
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
            } else {
                statusIndicator.setBackground(ContextCompat.getDrawable(this, R.drawable.status_indicator_disconnected));
                statusTextView.setText(R.string.disconnected);
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
     * 启动摄像头
     */
    private void startCamera() {
        // 创建PeerConnection
        webRTCClient.createPeerConnection();
        
        // 创建摄像头捕获器
        webRTCClient.createCameraCapturer(isFrontCamera);
        
        // 创建音频轨道
        webRTCClient.createAudioTrack();
        
        // 添加本地媒体流
        webRTCClient.addLocalMediaStream();
    }
    
    /**
     * 检查权限
     */
    private boolean checkPermissions() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * 请求权限
     */
    private void requestPermissions() {
        ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, PERMISSION_REQUEST_CODE);
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
                // 初始化WebRTC
                initWebRTC();
                
                // 设置按钮点击事件
                setupClickListeners();
                
                // 更新UI状态
                updateUIState();
                
                // 连接信令服务器
                connectToSignalingServer();
                
                // 启动摄像头
                startCamera();
            } else {
                showToast(getString(R.string.permission_required));
                finish();
            }
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // 释放视频渲染器
        if (localVideoView != null) {
            localVideoView.release();
        }
        
        if (remoteVideoView != null) {
            remoteVideoView.release();
        }
        
        // 断开WebRTC连接
        if (webRTCClient != null) {
            webRTCClient.dispose();
        }
        
        // 断开信令连接
        if (signalingClient != null) {
            signalingClient.sendBye();
            signalingClient.disconnect();
        }
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
        // 处理远程流
        if (mediaStream.videoTracks.size() > 0) {
            runOnUiThread(() -> {
                mediaStream.videoTracks.get(0).addSink(remoteVideoView);
            });
        }
    }
    
    @Override
    public void onRemoveStream(MediaStream mediaStream) {
        // 处理远程流移除
        runOnUiThread(() -> {
            if (mediaStream.videoTracks.size() > 0) {
                mediaStream.videoTracks.get(0).removeSink(remoteVideoView);
            }
        });
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
        // 在视频通话中不需要处理屏幕捕获错误
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
        
        if (isCreator) {
            webRTCClient.createOffer();
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
        updateUIState();
        showToast(getString(R.string.peer_disconnected));
    }
} 