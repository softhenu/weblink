package com.weblink.mobile.utils;

import android.graphics.Bitmap;
import android.graphics.Color;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

/**
 * 二维码生成工具类
 */
public class QRCodeGenerator {
    
    /**
     * 生成二维码
     * @param content 二维码内容
     * @param width 宽度
     * @param height 高度
     * @return 二维码位图
     */
    public static Bitmap generateQRCode(String content, int width, int height) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, width, height);
            
            int[] pixels = new int[width * height];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    pixels[y * width + x] = bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE;
                }
            }
            
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
            
            return bitmap;
        } catch (WriterException e) {
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * 生成带Logo的二维码
     * @param content 二维码内容
     * @param width 宽度
     * @param height 高度
     * @param logo Logo位图
     * @return 二维码位图
     */
    public static Bitmap generateQRCodeWithLogo(String content, int width, int height, Bitmap logo) {
        Bitmap qrBitmap = generateQRCode(content, width, height);
        
        if (qrBitmap == null) {
            return null;
        }
        
        if (logo == null) {
            return qrBitmap;
        }
        
        // 计算Logo大小，一般为二维码的1/5
        int logoWidth = width / 5;
        int logoHeight = height / 5;
        
        // 缩放Logo
        logo = Bitmap.createScaledBitmap(logo, logoWidth, logoHeight, false);
        
        // 计算放置Logo的位置
        int logoX = (width - logoWidth) / 2;
        int logoY = (height - logoHeight) / 2;
        
        // 创建新的位图并绘制
        Bitmap combined = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(combined);
        canvas.drawBitmap(qrBitmap, 0, 0, null);
        canvas.drawBitmap(logo, logoX, logoY, null);
        
        return combined;
    }
} 