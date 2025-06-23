package com.weblink.mobile.utils;

import java.security.SecureRandom;

/**
 * 邀请码生成工具类
 */
public class InvitationCodeGenerator {
    
    private static final String ALLOWED_CHARACTERS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int CODE_LENGTH = 6;
    
    private static final SecureRandom random = new SecureRandom();
    
    /**
     * 生成随机邀请码
     * @return 6位随机邀请码
     */
    public static String generateInvitationCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(ALLOWED_CHARACTERS.charAt(random.nextInt(ALLOWED_CHARACTERS.length())));
        }
        return sb.toString();
    }
    
    /**
     * 生成数字邀请码
     * @return 6位数字邀请码
     */
    public static String generateNumericInvitationCode() {
        int code = 100000 + random.nextInt(900000);
        return String.valueOf(code);
    }
    
    /**
     * 验证邀请码格式是否正确
     * @param code 邀请码
     * @return 是否有效
     */
    public static boolean isValidInvitationCode(String code) {
        if (code == null || code.length() != CODE_LENGTH) {
            return false;
        }
        
        for (char c : code.toCharArray()) {
            if (ALLOWED_CHARACTERS.indexOf(c) == -1) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * 验证数字邀请码格式是否正确
     * @param code 数字邀请码
     * @return 是否有效
     */
    public static boolean isValidNumericInvitationCode(String code) {
        if (code == null || code.length() != CODE_LENGTH) {
            return false;
        }
        
        try {
            int numericCode = Integer.parseInt(code);
            return numericCode >= 100000 && numericCode <= 999999;
        } catch (NumberFormatException e) {
            return false;
        }
    }
} 