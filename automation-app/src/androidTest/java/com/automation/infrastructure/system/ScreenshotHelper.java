package com.automation.infrastructure.system;

import android.app.UiAutomation;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Utility class for capturing screenshots during instrumentation tests.
 * Prefers in-memory capture via {@link UiAutomation} and falls back to shell commands.
 */
public final class ScreenshotHelper {

    private static final String TAG = "ScreenshotHelper";

    private final UiDevice device;

    public ScreenshotHelper(UiDevice device) {
        this.device = device;
    }

    /** Captures a screenshot into the provided file using UiDevice APIs. */
    public boolean captureToFile(File destination) {
        return device.takeScreenshot(destination);
    }

    /** Captures a screenshot using the shell `screencap` command. */
    public boolean captureViaShell(File destination) {
        try {
            device.executeShellCommand("screencap -p " + destination.getAbsolutePath());
            boolean ok = destination.exists() && destination.length() > 0;
            if (!ok) {
                Log.e(TAG, "screencap produced empty result");
            }
            return ok;
        } catch (IOException e) {
            Log.e(TAG, "Shell screencap error", e);
            return false;
        }
    }

    /** Captures to JPEG bytes in-memory, avoiding filesystem I/O. */
    public byte[] captureToJpegBytes(int quality) {
        try {
            UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
            Bitmap bitmap = uiAutomation.takeScreenshot();
            if (bitmap == null) {
                Log.e(TAG, "UiAutomation.takeScreenshot returned null");
                return null;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, clampQuality(quality), out);
            bitmap.recycle();
            byte[] data = out.toByteArray();
            out.close();
            Log.i(TAG, String.format("Captured screenshot %d KB (quality=%d)", data.length / 1024, quality));
            return data;
        } catch (Exception e) {
            Log.e(TAG, "captureToJpegBytes failed", e);
            return null;
        }
    }

    /** Captures compressed JPEG to destination file using a temporary PNG capture. */
    public boolean captureCompressed(File destination, int quality) {
        File temp = new File(destination.getParentFile(), ".temp_capture.png");
        if (!captureToFile(temp)) {
            return false;
        }
        try {
            Bitmap bitmap = BitmapFactory.decodeFile(temp.getAbsolutePath());
            if (bitmap == null) {
                Log.e(TAG, "Failed to decode temp screenshot");
                return false;
            }
            try (FileOutputStream out = new FileOutputStream(destination)) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, clampQuality(quality), out);
            }
            long targetSize = destination.length();
            Log.i(TAG, "Compressed screenshot size: " + targetSize + " bytes");
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Compression failed", e);
            return false;
        } finally {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
        }
    }

    /** Captures a scaled-down thumbnail image. */
    public boolean captureThumbnail(File destination, int maxWidth, int maxHeight) {
        File temp = new File(destination.getParentFile(), ".temp_capture.png");
        if (!captureToFile(temp)) {
            return false;
        }
        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(temp.getAbsolutePath(), opts);

            int sample = 1;
            if (opts.outWidth > maxWidth || opts.outHeight > maxHeight) {
                int scaleX = Math.max(1, opts.outWidth / maxWidth);
                int scaleY = Math.max(1, opts.outHeight / maxHeight);
                sample = Math.max(scaleX, scaleY);
            }

            opts.inJustDecodeBounds = false;
            opts.inSampleSize = sample;
            Bitmap bitmap = BitmapFactory.decodeFile(temp.getAbsolutePath(), opts);
            if (bitmap == null) {
                Log.e(TAG, "Failed to decode scaled bitmap");
                return false;
            }

            try (FileOutputStream out = new FileOutputStream(destination)) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out);
            }
            Log.i(TAG, String.format("Thumbnail generated (%dx%d -> %dx%d, sample=%d)",
                    opts.outWidth, opts.outHeight, bitmap.getWidth(), bitmap.getHeight(), sample));
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Thumbnail generation failed", e);
            return false;
        } finally {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
        }
    }

    /** Attempts capture via UiDevice and falls back to shell screencap. */
    public boolean captureSmart(File destination) {
        if (captureToFile(destination)) {
            return true;
        }
        Log.w(TAG, "UiDevice screenshot failed, falling back to shell screencap");
        return captureViaShell(destination);
    }

    private int clampQuality(int quality) {
        return Math.max(0, Math.min(quality, 100));
    }
}
