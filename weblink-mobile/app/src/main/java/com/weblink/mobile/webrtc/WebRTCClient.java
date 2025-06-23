package com.weblink.mobile.webrtc;

import android.content.Context;
import android.util.Log;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.ScreenCapturerAndroid;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.util.ArrayList;
import java.util.List;

/**
 * WebRTC客户端，处理WebRTC连接和媒体流
 */
public class WebRTCClient {
    private static final String TAG = "WebRTCClient";
    private static final String LOCAL_TRACK_ID = "local_track";
    private static final String LOCAL_STREAM_ID = "local_stream";
    
    // WebRTC组件
    private PeerConnectionFactory peerConnectionFactory;
    private PeerConnection peerConnection;
    private EglBase eglBase;
    private VideoTrack localVideoTrack;
    private AudioTrack localAudioTrack;
    private VideoCapturer videoCapturer;
    private VideoSource videoSource;
    private AudioSource audioSource;
    private SurfaceTextureHelper surfaceTextureHelper;
    
    // 上下文和回调
    private Context context;
    private PeerConnectionObserver observer;
    
    // 状态标志
    private boolean isScreenCaptureEnabled = false;
    private boolean isCameraEnabled = false;
    private boolean isAudioEnabled = false;
    
    public WebRTCClient(Context context, PeerConnectionObserver observer) {
        this.context = context;
        this.observer = observer;
        this.eglBase = EglBase.create();
        
        initializePeerConnectionFactory();
    }
    
    /**
     * 初始化PeerConnectionFactory
     */
    private void initializePeerConnectionFactory() {
        // 初始化PeerConnectionFactory
        PeerConnectionFactory.InitializationOptions initializationOptions =
                PeerConnectionFactory.InitializationOptions.builder(context)
                        .createInitializationOptions();
        PeerConnectionFactory.initialize(initializationOptions);
        
        // 创建PeerConnectionFactory
        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        DefaultVideoEncoderFactory videoEncoderFactory =
                new DefaultVideoEncoderFactory(eglBase.getEglBaseContext(), true, true);
        DefaultVideoDecoderFactory videoDecoderFactory =
                new DefaultVideoDecoderFactory(eglBase.getEglBaseContext());
        
        peerConnectionFactory = PeerConnectionFactory.builder()
                .setOptions(options)
                .setVideoEncoderFactory(videoEncoderFactory)
                .setVideoDecoderFactory(videoDecoderFactory)
                .createPeerConnectionFactory();
    }
    
    /**
     * 创建PeerConnection
     */
    public void createPeerConnection() {
        List<PeerConnection.IceServer> iceServers = new ArrayList<>();
        
        // 添加STUN服务器
        iceServers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());
        
        // 可以添加TURN服务器，如果需要的话
        // iceServers.add(PeerConnection.IceServer.builder("turn:your-turn-server.com")
        //         .setUsername("username")
        //         .setPassword("password")
        //         .createIceServer());
        
        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);
        rtcConfig.enableDtlsSrtp = true;
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
        
        PeerConnection.Observer pcObserver = new PeerConnection.Observer() {
            @Override
            public void onSignalingChange(PeerConnection.SignalingState signalingState) {
                Log.d(TAG, "onSignalingChange: " + signalingState);
            }
            
            @Override
            public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
                Log.d(TAG, "onIceConnectionChange: " + iceConnectionState);
                observer.onIceConnectionChange(iceConnectionState);
            }
            
            @Override
            public void onIceConnectionReceivingChange(boolean b) {
                Log.d(TAG, "onIceConnectionReceivingChange: " + b);
            }
            
            @Override
            public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
                Log.d(TAG, "onIceGatheringChange: " + iceGatheringState);
            }
            
            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                Log.d(TAG, "onIceCandidate: " + iceCandidate);
                observer.onIceCandidate(iceCandidate);
            }
            
            @Override
            public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {
                Log.d(TAG, "onIceCandidatesRemoved");
            }
            
            @Override
            public void onAddStream(MediaStream mediaStream) {
                Log.d(TAG, "onAddStream: " + mediaStream.getId());
                observer.onAddStream(mediaStream);
            }
            
            @Override
            public void onRemoveStream(MediaStream mediaStream) {
                Log.d(TAG, "onRemoveStream: " + mediaStream.getId());
                observer.onRemoveStream(mediaStream);
            }
            
            @Override
            public void onDataChannel(DataChannel dataChannel) {
                Log.d(TAG, "onDataChannel: " + dataChannel.label());
            }
            
            @Override
            public void onRenegotiationNeeded() {
                Log.d(TAG, "onRenegotiationNeeded");
                observer.onRenegotiationNeeded();
            }
            
            @Override
            public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {
                Log.d(TAG, "onAddTrack");
            }
        };
        
        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, pcObserver);
    }
    
    /**
     * 添加本地媒体流
     */
    public void addLocalMediaStream() {
        MediaStream mediaStream = peerConnectionFactory.createLocalMediaStream(LOCAL_STREAM_ID);
        
        // 如果有视频轨道，添加到媒体流
        if (localVideoTrack != null) {
            mediaStream.addTrack(localVideoTrack);
        }
        
        // 如果有音频轨道，添加到媒体流
        if (localAudioTrack != null) {
            mediaStream.addTrack(localAudioTrack);
        }
        
        // 添加媒体流到PeerConnection
        peerConnection.addStream(mediaStream);
    }
    
    /**
     * 创建视频捕获器 - 摄像头
     */
    public void createCameraCapturer(boolean isFrontCamera) {
        if (videoCapturer != null) {
            videoCapturer.dispose();
            videoCapturer = null;
        }
        
        videoCapturer = createCameraVideoCapturer(isFrontCamera);
        
        if (videoCapturer != null) {
            videoSource = peerConnectionFactory.createVideoSource(false);
            surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.getEglBaseContext());
            videoCapturer.initialize(surfaceTextureHelper, context, videoSource.getCapturerObserver());
            videoCapturer.startCapture(1280, 720, 30);
            
            localVideoTrack = peerConnectionFactory.createVideoTrack(LOCAL_TRACK_ID + "_video", videoSource);
            localVideoTrack.setEnabled(true);
            
            isCameraEnabled = true;
            isScreenCaptureEnabled = false;
        }
    }
    
    /**
     * 创建视频捕获器 - 屏幕共享
     */
    public void createScreenCapturer(android.content.Intent mediaProjectionPermissionResultData) {
        if (videoCapturer != null) {
            videoCapturer.dispose();
            videoCapturer = null;
        }
        
        videoCapturer = new ScreenCapturerAndroid(
                mediaProjectionPermissionResultData, new android.media.projection.MediaProjection.Callback() {
            @Override
            public void onStop() {
                Log.e(TAG, "用户撤销了屏幕捕获权限");
                observer.onScreenCaptureError("用户撤销了屏幕捕获权限");
            }
        });
        
        if (videoCapturer != null) {
            videoSource = peerConnectionFactory.createVideoSource(true);
            surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.getEglBaseContext());
            videoCapturer.initialize(surfaceTextureHelper, context, videoSource.getCapturerObserver());
            videoCapturer.startCapture(1280, 720, 30);
            
            localVideoTrack = peerConnectionFactory.createVideoTrack(LOCAL_TRACK_ID + "_screen", videoSource);
            localVideoTrack.setEnabled(true);
            
            isScreenCaptureEnabled = true;
            isCameraEnabled = false;
        }
    }
    
    /**
     * 创建音频轨道
     */
    public void createAudioTrack() {
        MediaConstraints audioConstraints = new MediaConstraints();
        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googEchoCancellation", "true"));
        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googNoiseSuppression", "true"));
        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googAutoGainControl", "true"));
        
        audioSource = peerConnectionFactory.createAudioSource(audioConstraints);
        localAudioTrack = peerConnectionFactory.createAudioTrack(LOCAL_TRACK_ID + "_audio", audioSource);
        localAudioTrack.setEnabled(true);
        
        isAudioEnabled = true;
    }
    
    /**
     * 创建摄像头视频捕获器
     */
    private CameraVideoCapturer createCameraVideoCapturer(boolean isFrontCamera) {
        CameraEnumerator enumerator;
        if (Camera2Enumerator.isSupported(context)) {
            enumerator = new Camera2Enumerator(context);
        } else {
            enumerator = new Camera1Enumerator(false);
        }
        
        final String[] deviceNames = enumerator.getDeviceNames();
        
        // 先尝试找到前置/后置摄像头
        for (String deviceName : deviceNames) {
            if (isFrontCamera && enumerator.isFrontFacing(deviceName)) {
                return enumerator.createCapturer(deviceName, null);
            } else if (!isFrontCamera && enumerator.isBackFacing(deviceName)) {
                return enumerator.createCapturer(deviceName, null);
            }
        }
        
        // 如果没有找到指定的摄像头，使用任何可用的摄像头
        for (String deviceName : deviceNames) {
            return enumerator.createCapturer(deviceName, null);
        }
        
        return null;
    }
    
    /**
     * 创建offer
     */
    public void createOffer() {
        if (peerConnection == null) {
            Log.e(TAG, "PeerConnection为空，无法创建offer");
            return;
        }
        
        MediaConstraints constraints = new MediaConstraints();
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));
        
        peerConnection.createOffer(new SdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                Log.d(TAG, "创建offer成功");
                peerConnection.setLocalDescription(new SdpObserver() {
                    @Override
                    public void onCreateSuccess(SessionDescription sessionDescription) {
                    }
                    
                    @Override
                    public void onSetSuccess() {
                        Log.d(TAG, "设置本地SDP成功");
                        observer.onLocalDescription(sessionDescription);
                    }
                    
                    @Override
                    public void onCreateFailure(String s) {
                    }
                    
                    @Override
                    public void onSetFailure(String s) {
                        Log.e(TAG, "设置本地SDP失败: " + s);
                    }
                }, sessionDescription);
            }
            
            @Override
            public void onSetSuccess() {
            }
            
            @Override
            public void onCreateFailure(String s) {
                Log.e(TAG, "创建offer失败: " + s);
            }
            
            @Override
            public void onSetFailure(String s) {
            }
        }, constraints);
    }
    
    /**
     * 创建answer
     */
    public void createAnswer() {
        if (peerConnection == null) {
            Log.e(TAG, "PeerConnection为空，无法创建answer");
            return;
        }
        
        MediaConstraints constraints = new MediaConstraints();
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));
        
        peerConnection.createAnswer(new SdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                Log.d(TAG, "创建answer成功");
                peerConnection.setLocalDescription(new SdpObserver() {
                    @Override
                    public void onCreateSuccess(SessionDescription sessionDescription) {
                    }
                    
                    @Override
                    public void onSetSuccess() {
                        Log.d(TAG, "设置本地SDP成功");
                        observer.onLocalDescription(sessionDescription);
                    }
                    
                    @Override
                    public void onCreateFailure(String s) {
                    }
                    
                    @Override
                    public void onSetFailure(String s) {
                        Log.e(TAG, "设置本地SDP失败: " + s);
                    }
                }, sessionDescription);
            }
            
            @Override
            public void onSetSuccess() {
            }
            
            @Override
            public void onCreateFailure(String s) {
                Log.e(TAG, "创建answer失败: " + s);
            }
            
            @Override
            public void onSetFailure(String s) {
            }
        }, constraints);
    }
    
    /**
     * 设置远程描述
     */
    public void setRemoteDescription(SessionDescription sessionDescription) {
        if (peerConnection == null) {
            Log.e(TAG, "PeerConnection为空，无法设置远程描述");
            return;
        }
        
        peerConnection.setRemoteDescription(new SdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
            }
            
            @Override
            public void onSetSuccess() {
                Log.d(TAG, "设置远程SDP成功");
            }
            
            @Override
            public void onCreateFailure(String s) {
            }
            
            @Override
            public void onSetFailure(String s) {
                Log.e(TAG, "设置远程SDP失败: " + s);
            }
        }, sessionDescription);
    }
    
    /**
     * 添加ICE候选者
     */
    public void addIceCandidate(IceCandidate iceCandidate) {
        if (peerConnection == null) {
            Log.e(TAG, "PeerConnection为空，无法添加ICE候选者");
            return;
        }
        
        peerConnection.addIceCandidate(iceCandidate);
    }
    
    /**
     * 初始化视频渲染器
     */
    public void initSurfaceRenderer(SurfaceViewRenderer localRenderer, SurfaceViewRenderer remoteRenderer) {
        try {
            localRenderer.init(eglBase.getEglBaseContext(), null);
            localRenderer.setEnableHardwareScaler(true);
            localRenderer.setMirror(true);
            
            remoteRenderer.init(eglBase.getEglBaseContext(), null);
            remoteRenderer.setEnableHardwareScaler(true);
            remoteRenderer.setMirror(false);
            
            if (localVideoTrack != null) {
                localVideoTrack.addSink(localRenderer);
            }
        } catch (Exception e) {
            Log.e(TAG, "初始化渲染器失败: " + e.getMessage());
        }
    }
    
    /**
     * 切换摄像头
     */
    public void switchCamera() {
        if (videoCapturer != null && videoCapturer instanceof CameraVideoCapturer && isCameraEnabled) {
            ((CameraVideoCapturer) videoCapturer).switchCamera(null);
        }
    }
    
    /**
     * 设置音频启用状态
     */
    public void setAudioEnabled(boolean enabled) {
        if (localAudioTrack != null) {
            localAudioTrack.setEnabled(enabled);
        }
    }
    
    /**
     * 设置视频启用状态
     */
    public void setVideoEnabled(boolean enabled) {
        if (localVideoTrack != null) {
            localVideoTrack.setEnabled(enabled);
        }
    }
    
    /**
     * 释放资源
     */
    public void dispose() {
        if (videoCapturer != null) {
            try {
                videoCapturer.stopCapture();
            } catch (InterruptedException e) {
                Log.e(TAG, "停止视频捕获失败: " + e.getMessage());
            }
            videoCapturer.dispose();
            videoCapturer = null;
        }
        
        if (localVideoTrack != null) {
            localVideoTrack.dispose();
            localVideoTrack = null;
        }
        
        if (localAudioTrack != null) {
            localAudioTrack.dispose();
            localAudioTrack = null;
        }
        
        if (videoSource != null) {
            videoSource.dispose();
            videoSource = null;
        }
        
        if (audioSource != null) {
            audioSource.dispose();
            audioSource = null;
        }
        
        if (peerConnection != null) {
            peerConnection.close();
            peerConnection = null;
        }
        
        if (surfaceTextureHelper != null) {
            surfaceTextureHelper.dispose();
            surfaceTextureHelper = null;
        }
        
        if (peerConnectionFactory != null) {
            peerConnectionFactory.dispose();
            peerConnectionFactory = null;
        }
    }
    
    /**
     * 获取EglBase实例
     */
    public EglBase getEglBase() {
        return eglBase;
    }
    
    /**
     * PeerConnection观察者接口
     */
    public interface PeerConnectionObserver {
        void onIceCandidate(IceCandidate iceCandidate);
        void onAddStream(MediaStream mediaStream);
        void onRemoveStream(MediaStream mediaStream);
        void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState);
        void onRenegotiationNeeded();
        void onLocalDescription(SessionDescription sdp);
        void onScreenCaptureError(String error);
    }
    
    /**
     * SDP观察者接口
     */
    private interface SdpObserver extends org.webrtc.SdpObserver {
    }
} 