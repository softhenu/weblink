package com.weblink.mobile.webrtc;

import android.content.Context;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URISyntaxException;
import java.util.Arrays;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

/**
 * WebRTC信令客户端，用于与信令服务器通信
 */
public class SignalingClient {
    private static final String TAG = "SignalingClient";
    
    // 信令服务器地址，可以根据需要修改
    private static final String SIGNALING_SERVER_URL = "https://your-signaling-server.com";
    
    private Socket socket;
    private String roomId;
    private SignalingClientListener listener;
    private boolean isInitiator = false;
    
    public SignalingClient(Context context) {
        // 初始化时不立即连接
    }
    
    /**
     * 连接到信令服务器
     * @param serverUrl 服务器URL
     * @param roomId 房间ID
     * @param listener 信令事件监听器
     */
    public void connect(String serverUrl, String roomId, SignalingClientListener listener) {
        this.roomId = roomId;
        this.listener = listener;
        
        try {
            // 设置Socket.IO选项
            IO.Options options = new IO.Options();
            options.reconnection = true;
            options.reconnectionAttempts = 10;
            options.reconnectionDelay = 1000;
            options.timeout = 10000;
            
            // 创建Socket.IO客户端
            socket = IO.socket(serverUrl, options);
            
            // 设置事件监听器
            socket.on(Socket.EVENT_CONNECT, onConnect);
            socket.on(Socket.EVENT_DISCONNECT, onDisconnect);
            socket.on(Socket.EVENT_CONNECT_ERROR, onConnectError);
            
            // 信令事件
            socket.on("created", onCreated);
            socket.on("joined", onJoined);
            socket.on("full", onFull);
            socket.on("ready", onReady);
            socket.on("offer", onOffer);
            socket.on("answer", onAnswer);
            socket.on("candidate", onCandidate);
            socket.on("bye", onBye);
            
            // 连接到服务器
            socket.connect();
            
        } catch (URISyntaxException e) {
            Log.e(TAG, "连接信令服务器失败: " + e.getMessage());
            if (listener != null) {
                listener.onConnectionError("无法连接到信令服务器: " + e.getMessage());
            }
        }
    }
    
    /**
     * 断开与信令服务器的连接
     */
    public void disconnect() {
        if (socket != null) {
            socket.disconnect();
            socket.off();
            socket = null;
        }
    }
    
    /**
     * 发送加入房间请求
     */
    public void joinRoom() {
        if (socket != null && socket.connected() && roomId != null) {
            Log.d(TAG, "加入房间: " + roomId);
            JSONObject message = new JSONObject();
            try {
                message.put("room", roomId);
                socket.emit("join", message);
            } catch (JSONException e) {
                Log.e(TAG, "发送join消息失败: " + e.getMessage());
            }
        }
    }
    
    /**
     * 发送创建房间请求
     */
    public void createRoom() {
        if (socket != null && socket.connected() && roomId != null) {
            Log.d(TAG, "创建房间: " + roomId);
            JSONObject message = new JSONObject();
            try {
                message.put("room", roomId);
                socket.emit("create", message);
            } catch (JSONException e) {
                Log.e(TAG, "发送create消息失败: " + e.getMessage());
            }
        }
    }
    
    /**
     * 发送准备就绪消息
     */
    public void sendReady() {
        if (socket != null && socket.connected() && roomId != null) {
            Log.d(TAG, "发送ready消息");
            JSONObject message = new JSONObject();
            try {
                message.put("room", roomId);
                socket.emit("ready", message);
            } catch (JSONException e) {
                Log.e(TAG, "发送ready消息失败: " + e.getMessage());
            }
        }
    }
    
    /**
     * 发送offer消息
     * @param sdp 会话描述
     */
    public void sendOffer(String sdp) {
        if (socket != null && socket.connected() && roomId != null) {
            Log.d(TAG, "发送offer消息");
            JSONObject message = new JSONObject();
            try {
                message.put("room", roomId);
                message.put("sdp", sdp);
                socket.emit("offer", message);
            } catch (JSONException e) {
                Log.e(TAG, "发送offer消息失败: " + e.getMessage());
            }
        }
    }
    
    /**
     * 发送answer消息
     * @param sdp 会话描述
     */
    public void sendAnswer(String sdp) {
        if (socket != null && socket.connected() && roomId != null) {
            Log.d(TAG, "发送answer消息");
            JSONObject message = new JSONObject();
            try {
                message.put("room", roomId);
                message.put("sdp", sdp);
                socket.emit("answer", message);
            } catch (JSONException e) {
                Log.e(TAG, "发送answer消息失败: " + e.getMessage());
            }
        }
    }
    
    /**
     * 发送ICE候选者信息
     * @param candidate 候选者信息
     */
    public void sendIceCandidate(String candidate, String sdpMid, int sdpMLineIndex) {
        if (socket != null && socket.connected() && roomId != null) {
            Log.d(TAG, "发送candidate消息");
            JSONObject message = new JSONObject();
            try {
                message.put("room", roomId);
                message.put("candidate", candidate);
                message.put("sdpMid", sdpMid);
                message.put("sdpMLineIndex", sdpMLineIndex);
                socket.emit("candidate", message);
            } catch (JSONException e) {
                Log.e(TAG, "发送candidate消息失败: " + e.getMessage());
            }
        }
    }
    
    /**
     * 发送离开消息
     */
    public void sendBye() {
        if (socket != null && socket.connected() && roomId != null) {
            Log.d(TAG, "发送bye消息");
            JSONObject message = new JSONObject();
            try {
                message.put("room", roomId);
                socket.emit("bye", message);
            } catch (JSONException e) {
                Log.e(TAG, "发送bye消息失败: " + e.getMessage());
            }
        }
    }
    
    // Socket.IO事件处理
    private Emitter.Listener onConnect = args -> {
        Log.d(TAG, "已连接到信令服务器");
        if (listener != null) {
            listener.onConnectionEstablished();
        }
    };
    
    private Emitter.Listener onDisconnect = args -> {
        Log.d(TAG, "与信令服务器断开连接");
        if (listener != null) {
            listener.onConnectionClosed();
        }
    };
    
    private Emitter.Listener onConnectError = args -> {
        Log.e(TAG, "连接错误: " + Arrays.toString(args));
        if (listener != null) {
            listener.onConnectionError("连接错误: " + Arrays.toString(args));
        }
    };
    
    // 信令事件处理
    private Emitter.Listener onCreated = args -> {
        Log.d(TAG, "房间已创建");
        isInitiator = true;
        if (listener != null) {
            listener.onRoomCreated();
        }
    };
    
    private Emitter.Listener onJoined = args -> {
        Log.d(TAG, "已加入房间");
        isInitiator = false;
        if (listener != null) {
            listener.onRoomJoined();
        }
    };
    
    private Emitter.Listener onFull = args -> {
        Log.d(TAG, "房间已满");
        if (listener != null) {
            listener.onRoomFull();
        }
    };
    
    private Emitter.Listener onReady = args -> {
        Log.d(TAG, "收到ready消息");
        if (listener != null) {
            listener.onPeerReady();
        }
    };
    
    private Emitter.Listener onOffer = args -> {
        if (args.length > 0 && args[0] instanceof JSONObject) {
            JSONObject data = (JSONObject) args[0];
            try {
                String sdp = data.getString("sdp");
                Log.d(TAG, "收到offer: " + sdp);
                if (listener != null) {
                    listener.onOfferReceived(sdp);
                }
            } catch (JSONException e) {
                Log.e(TAG, "解析offer消息失败: " + e.getMessage());
            }
        }
    };
    
    private Emitter.Listener onAnswer = args -> {
        if (args.length > 0 && args[0] instanceof JSONObject) {
            JSONObject data = (JSONObject) args[0];
            try {
                String sdp = data.getString("sdp");
                Log.d(TAG, "收到answer: " + sdp);
                if (listener != null) {
                    listener.onAnswerReceived(sdp);
                }
            } catch (JSONException e) {
                Log.e(TAG, "解析answer消息失败: " + e.getMessage());
            }
        }
    };
    
    private Emitter.Listener onCandidate = args -> {
        if (args.length > 0 && args[0] instanceof JSONObject) {
            JSONObject data = (JSONObject) args[0];
            try {
                String candidate = data.getString("candidate");
                String sdpMid = data.getString("sdpMid");
                int sdpMLineIndex = data.getInt("sdpMLineIndex");
                Log.d(TAG, "收到candidate: " + candidate);
                if (listener != null) {
                    listener.onIceCandidateReceived(candidate, sdpMid, sdpMLineIndex);
                }
            } catch (JSONException e) {
                Log.e(TAG, "解析candidate消息失败: " + e.getMessage());
            }
        }
    };
    
    private Emitter.Listener onBye = args -> {
        Log.d(TAG, "收到bye消息");
        if (listener != null) {
            listener.onPeerDisconnected();
        }
    };
    
    public boolean isInitiator() {
        return isInitiator;
    }
    
    public interface SignalingClientListener {
        void onConnectionEstablished();
        void onConnectionClosed();
        void onConnectionError(String error);
        
        void onRoomCreated();
        void onRoomJoined();
        void onRoomFull();
        
        void onPeerReady();
        void onOfferReceived(String sdp);
        void onAnswerReceived(String sdp);
        void onIceCandidateReceived(String candidate, String sdpMid, int sdpMLineIndex);
        void onPeerDisconnected();
    }
} 