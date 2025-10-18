package com.automation.infrastructure.vision;

import android.os.Build;
import android.util.Base64;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgcodecs.Imgcodecs;

/**
 * OpenCV 图像识别封装
 * 提供模板匹配、图像对比等功能
 */
public class ImageRecognition {

    static {
        // 加载 OpenCV 库
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    }

    /**
     * 模板匹配 - 在截图中查找模板图片
     * @param screenshotPath 截图路径
     * @param templatePath 模板图片路径
     * @return 匹配位置的中心点坐标，如果未找到返回 null
     */
    public Point findTemplate(String screenshotPath, String templatePath) {
        return findTemplate(screenshotPath, templatePath, 0.8);
    }

    /**
     * 模板匹配（带相似度阈值）
     * @param screenshotPath 截图路径
     * @param templatePath 模板图片路径
     * @param threshold 相似度阈值（0-1），默认 0.8
     * @return 匹配位置的中心点坐标
     */
    public Point findTemplate(String screenshotPath, String templatePath, double threshold) {
        try {
            Mat screenshot = Imgcodecs.imread(screenshotPath);
            Mat template = Imgcodecs.imread(templatePath);
            try {
                if (screenshot.empty() || template.empty()) {
                    return null;
                }
                return findTemplate(screenshot, template, threshold);
            } finally {
                screenshot.release();
                template.release();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public Point findTemplate(Mat screenshot, Mat template, double threshold) {
        if (screenshot == null || template == null || screenshot.empty() || template.empty()) {
            return null;
        }

        int resultCols = screenshot.cols() - template.cols() + 1;
        int resultRows = screenshot.rows() - template.rows() + 1;
        if (resultCols <= 0 || resultRows <= 0) {
            return null;
        }

        Mat result = new Mat(resultRows, resultCols, CvType.CV_32FC1);
        try {
            Imgproc.matchTemplate(screenshot, template, result, Imgproc.TM_CCOEFF_NORMED);
            Core.MinMaxLocResult mmr = Core.minMaxLoc(result);
            if (mmr.maxVal < threshold) {
                return null;
            }
            Point matchLoc = mmr.maxLoc;
            int centerX = (int) (matchLoc.x + template.cols() / 2.0);
            int centerY = (int) (matchLoc.y + template.rows() / 2.0);
            return new Point(centerX, centerY);
        } finally {
            result.release();
        }
    }

    public Mat decodeBase64(String base64) {
        if (base64 == null) {
            return null;
        }
        byte[] data;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                data = java.util.Base64.getDecoder().decode(base64);
            } else {
                data = Base64.decode(base64, Base64.DEFAULT);
            }
        } catch (IllegalArgumentException e) {
            return null;
        }
        return decodeImageBytes(data);
    }

    public Mat decodeImageBytes(byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }
        MatOfByte mob = new MatOfByte(data);
        Mat image = Imgcodecs.imdecode(mob, Imgcodecs.IMREAD_COLOR);
        mob.release();
        if (image == null || image.empty()) {
            return null;
        }
        return image;
    }

    /**
     * 图像相似度对比
     * @param img1Path 图片1路径
     * @param img2Path 图片2路径
     * @return 相似度（0-1），1 表示完全相同
     */
    public double compareImages(String img1Path, String img2Path) {
        try {
            Mat img1 = Imgcodecs.imread(img1Path);
            Mat img2 = Imgcodecs.imread(img2Path);

            if (img1.empty() || img2.empty()) {
                return 0;
            }

            // 调整为相同尺寸
            if (img1.size().width != img2.size().width || img1.size().height != img2.size().height) {
                Imgproc.resize(img2, img2, img1.size());
            }

            // 转灰度
            Mat gray1 = new Mat();
            Mat gray2 = new Mat();
            Imgproc.cvtColor(img1, gray1, Imgproc.COLOR_BGR2GRAY);
            Imgproc.cvtColor(img2, gray2, Imgproc.COLOR_BGR2GRAY);

            // 计算差异
            Mat diff = new Mat();
            Core.absdiff(gray1, gray2, diff);

            // 计算相似度
            Scalar mean = Core.mean(diff);
            double similarity = 1.0 - (mean.val[0] / 255.0);

            // 释放资源
            img1.release();
            img2.release();
            gray1.release();
            gray2.release();
            diff.release();

            return similarity;

        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * 判断图片中是否包含特定颜色
     * @param imagePath 图片路径
     * @param targetColor 目标颜色 (R, G, B)
     * @param tolerance 容差值（0-255）
     * @return 是否包含该颜色
     */
    public boolean hasColor(String imagePath, Scalar targetColor, double tolerance) {
        try {
            Mat image = Imgcodecs.imread(imagePath);
            if (image.empty()) {
                return false;
            }

            // 定义颜色范围
            Scalar lowerBound = new Scalar(
                Math.max(0, targetColor.val[0] - tolerance),
                Math.max(0, targetColor.val[1] - tolerance),
                Math.max(0, targetColor.val[2] - tolerance)
            );

            Scalar upperBound = new Scalar(
                Math.min(255, targetColor.val[0] + tolerance),
                Math.min(255, targetColor.val[1] + tolerance),
                Math.min(255, targetColor.val[2] + tolerance)
            );

            // 颜色范围检测
            Mat mask = new Mat();
            Core.inRange(image, lowerBound, upperBound, mask);

            // 计算非零像素数
            int nonZero = Core.countNonZero(mask);

            image.release();
            mask.release();

            return nonZero > 0;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 获取图片的主要颜色
     * @param imagePath 图片路径
     * @return RGB 颜色值
     */
    public Scalar getDominantColor(String imagePath) {
        try {
            Mat image = Imgcodecs.imread(imagePath);
            if (image.empty()) {
                return new Scalar(0, 0, 0);
            }

            // 缩小图片以加速计算
            Mat resized = new Mat();
            Imgproc.resize(image, resized, new Size(50, 50));

            // 计算平均颜色
            Scalar avgColor = Core.mean(resized);

            image.release();
            resized.release();

            return avgColor;

        } catch (Exception e) {
            e.printStackTrace();
            return new Scalar(0, 0, 0);
        }
    }
}
