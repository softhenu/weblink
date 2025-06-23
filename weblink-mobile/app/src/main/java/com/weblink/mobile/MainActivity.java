package com.weblink.mobile;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputLayout;
import com.weblink.mobile.utils.InvitationCodeGenerator;

/**
 * 应用主活动
 */
public class MainActivity extends AppCompatActivity {
    
    private Button screenShareButton;
    private Button videoCallButton;
    private Button joinButton;
    private EditText codeEditText;
    private TextInputLayout codeInputLayout;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // 初始化UI组件
        initViews();
        
        // 设置按钮点击事件
        setupClickListeners();
    }
    
    /**
     * 初始化视图组件
     */
    private void initViews() {
        screenShareButton = findViewById(R.id.screenShareButton);
        videoCallButton = findViewById(R.id.videoCallButton);
        joinButton = findViewById(R.id.joinButton);
        codeEditText = findViewById(R.id.codeEditText);
        codeInputLayout = findViewById(R.id.codeInputLayout);
    }
    
    /**
     * 设置点击监听器
     */
    private void setupClickListeners() {
        // 屏幕共享按钮
        screenShareButton.setOnClickListener(v -> {
            // 生成随机邀请码
            String invitationCode = InvitationCodeGenerator.generateNumericInvitationCode();
            
            // 启动屏幕共享活动
            Intent intent = new Intent(MainActivity.this, ScreenShareActivity.class);
            intent.putExtra("invitationCode", invitationCode);
            intent.putExtra("isCreator", true);
            startActivity(intent);
        });
        
        // 视频通话按钮
        videoCallButton.setOnClickListener(v -> {
            // 生成随机邀请码
            String invitationCode = InvitationCodeGenerator.generateNumericInvitationCode();
            
            // 启动视频通话活动
            Intent intent = new Intent(MainActivity.this, VideoCallActivity.class);
            intent.putExtra("invitationCode", invitationCode);
            intent.putExtra("isCreator", true);
            startActivity(intent);
        });
        
        // 加入按钮
        joinButton.setOnClickListener(v -> {
            String code = codeEditText.getText().toString().trim();
            
            // 验证邀请码
            if (code.isEmpty()) {
                codeInputLayout.setError(getString(R.string.enter_code));
                return;
            }
            
            if (!InvitationCodeGenerator.isValidNumericInvitationCode(code)) {
                codeInputLayout.setError(getString(R.string.invalid_code));
                return;
            }
            
            codeInputLayout.setError(null);
            
            // 根据邀请码前缀判断是屏幕共享还是视频通话
            // 这里简单处理，实际应用中可能需要向服务器查询房间类型
            Intent intent;
            if (code.startsWith("9")) {  // 假设9开头是视频通话
                intent = new Intent(MainActivity.this, VideoCallActivity.class);
            } else {
                intent = new Intent(MainActivity.this, ScreenShareActivity.class);
            }
            
            intent.putExtra("invitationCode", code);
            intent.putExtra("isCreator", false);
            startActivity(intent);
        });
    }
    
    /**
     * 显示Toast消息
     */
    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
} 