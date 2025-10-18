package com.automation.domain.scenario.vision;

import android.content.Context;
import android.graphics.Point;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.automation.infrastructure.system.ScreenshotHelper;
import com.automation.infrastructure.vision.ImageRecognition;

import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.imgcodecs.Imgcodecs;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OpenCV 能力统一封装，负责截图与模板匹配。
 */
public final class VisionToolkit {

    private static final String TAG = "VisionToolkit";

    private final Context context;
    private final ScreenshotHelper screenshotHelper;
    private final ImageRecognition imageRecognition;
    private final Map<String, Mat> templateCache = new ConcurrentHashMap<>();

    public VisionToolkit(@NonNull Context context,
                         @NonNull ScreenshotHelper screenshotHelper,
                         @NonNull ImageRecognition imageRecognition) {
        this.context = Objects.requireNonNull(context, "context").getApplicationContext();
        this.screenshotHelper = Objects.requireNonNull(screenshotHelper, "screenshotHelper");
        this.imageRecognition = Objects.requireNonNull(imageRecognition, "imageRecognition");
    }

    public void storeTemplate(String templateId, String base64Data) throws IOException {
        Mat mat = imageRecognition.decodeBase64(base64Data);
        if (mat == null || mat.empty()) {
            throw new IOException("解码模板Base64失败");
        }
        Mat previous = templateCache.put(templateId, mat);
        if (previous != null) {
            previous.release();
        }
    }

    public void removeTemplate(String templateId) {
        Mat mat = templateCache.remove(templateId);
        if (mat != null) {
            mat.release();
        }
    }

    public void clearTemplates() {
        for (Mat mat : templateCache.values()) {
            mat.release();
        }
        templateCache.clear();
    }

    public Point findTemplateFromCache(String templateId, double threshold) throws IOException {
        Mat template = templateCache.get(templateId);
        if (template == null) {
            throw new IOException("模板未加载: " + templateId);
        }
        Mat screenshot = captureScreenshotMat();
        if (screenshot == null || screenshot.empty()) {
            throw new IOException("截屏失败");
        }
        try {
            org.opencv.core.Point match = imageRecognition.findTemplate(screenshot, template, threshold);
            if (match == null) {
                return null;
            }
            return new Point((int) match.x, (int) match.y);
        } finally {
            screenshot.release();
        }
    }

    /**
     * 截图并在图片中查找模板，返回安卓坐标。
     *
     * @param templatePath 模板图片路径
     * @param threshold    匹配阈值
     */
    @Nullable
    public Point captureAndFindTemplate(String templatePath, double threshold) throws IOException {
        Mat screenshot = captureScreenshotMat();
        Mat templateMat = null;
        try {
            templateMat = Imgcodecs.imread(templatePath);
            if (templateMat == null || templateMat.empty()) {
                Log.e(TAG, "模板图片读取失败: " + templatePath);
                return null;
            }
            org.opencv.core.Point match = imageRecognition.findTemplate(screenshot, templateMat, threshold);
            if (match == null) {
                return null;
            }
            return new Point((int) match.x, (int) match.y);
        } finally {
            if (screenshot != null) {
                screenshot.release();
            }
            if (templateMat != null) {
                templateMat.release();
            }
        }
    }

    /**
     * 比较两张图片的相似度。
     */
    public double compareImages(String image1, String image2) {
        return imageRecognition.compareImages(image1, image2);
    }

    /**
     * 检测截图是否包含指定颜色。
     */
    public boolean containsColor(String imagePath, Scalar target, double tolerance) {
        return imageRecognition.hasColor(imagePath, target, tolerance);
    }

    private Mat captureScreenshotMat() throws IOException {
        byte[] jpeg = screenshotHelper.captureToJpegBytes(80);
        if (jpeg == null || jpeg.length == 0) {
            throw new IOException("截屏失败，数据为空");
        }
        Mat screenshot = imageRecognition.decodeImageBytes(jpeg);
        if (screenshot == null || screenshot.empty()) {
            throw new IOException("截屏解码失败");
        }
        return screenshot;
    }
}
