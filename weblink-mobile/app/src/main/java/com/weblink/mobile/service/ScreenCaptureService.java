package com.weblink.mobile.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.weblink.mobile.MainActivity;
import com.weblink.mobile.R;

/**
 * 屏幕捕获前台服务，确保屏幕共享在后台也能正常运行
 */
public class ScreenCaptureService extends Service {
    private static final String TAG = "ScreenCaptureService";
    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "screen_capture_channel";
    
    // 服务绑定器
    private final IBinder binder = new LocalBinder();
    
    // 房间ID
    private String roomId;
    
    // 服务状态
    private boolean isRunning = false;
    
    /**
     * 本地绑定器类
     */
    public class LocalBinder extends Binder {
        public ScreenCaptureService getService() {
            return ScreenCaptureService.this;
        }
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");
        createNotificationChannel();
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");
        
        if (intent != null) {
            roomId = intent.getStringExtra("roomId");
            
            // 创建前台服务通知
            Notification notification = createNotification();
            startForeground(NOTIFICATION_ID, notification);
            isRunning = true;
        }
        
        return START_STICKY;
    }
    
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind");
        return binder;
    }
    
    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        isRunning = false;
        super.onDestroy();
    }
    
    /**
     * 创建通知渠道
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Screen Capture",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Used for screen capture service");
            
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }
    
    /**
     * 创建前台服务通知
     */
    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                notificationIntent,
                PendingIntent.FLAG_IMMUTABLE);
        
        String notificationText = roomId != null ?
                getString(R.string.sharing_active) + " - " + roomId :
                getString(R.string.sharing_active);
        
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(notificationText)
                .setSmallIcon(R.drawable.ic_screen_share)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }
    
    /**
     * 更新通知内容
     */
    public void updateNotification(String status) {
        if (!isRunning) {
            return;
        }
        
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle(getString(R.string.app_name))
                    .setContentText(status)
                    .setSmallIcon(R.drawable.ic_screen_share)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .build();
            
            notificationManager.notify(NOTIFICATION_ID, notification);
        }
    }
    
    /**
     * 设置房间ID
     */
    public void setRoomId(String roomId) {
        this.roomId = roomId;
        updateNotification(getString(R.string.sharing_active) + " - " + roomId);
    }
    
    /**
     * 获取房间ID
     */
    public String getRoomId() {
        return roomId;
    }
    
    /**
     * 检查服务是否正在运行
     */
    public boolean isRunning() {
        return isRunning;
    }
} 