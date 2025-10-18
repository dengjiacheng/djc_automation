package com.automation.domain.scenario.device;

import android.graphics.Point;
import android.graphics.Rect;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import com.automation.domain.scenario.SelectorCondition;

import java.util.Objects;
import java.util.Random;

/**
 * 基于 UiDevice 的操作工具，提供模拟真人的点击与滑动。
 */
public final class DeviceActions {

    private static final String TAG = "DeviceActions";
    private static final int DEFAULT_WAIT_MS = 1200;

    private final UiDevice device;
    private final Random random = new Random();

    public DeviceActions(@NonNull UiDevice device) {
        this.device = Objects.requireNonNull(device, "device");
    }

    public boolean click(int x, int y) {
        Point jitter = applyJitter(new Point(x, y), 6, 18);
        return device.click(jitter.x, jitter.y);
    }

    public boolean click(@NonNull SelectorCondition condition) {
        return click(condition.selector(), condition.timeoutMs());
    }

    public boolean click(@NonNull BySelector selector, long timeoutMs) {
        UiObject2 target = waitForObject(selector, timeoutMs);
        if (target == null) {
            Log.w(TAG, "click: 未找到目标元素");
            return false;
        }
        Rect bounds = target.getVisibleBounds();
        Point point = randomPointIn(bounds);
        return device.click(point.x, point.y);
    }

    public boolean longPress(@NonNull BySelector selector, long durationMs) {
        UiObject2 target = waitForObject(selector, durationMs);
        if (target == null) {
            return false;
        }
        Rect bounds = target.getVisibleBounds();
        Point point = randomPointIn(bounds);
        return longPress(point.x, point.y, durationMs);
    }

    public boolean longPress(int x, int y, long durationMs) {
        int steps = Math.max(15, (int) (durationMs / 8));
        Point p = applyJitter(new Point(x, y), 4, 10);
        return device.swipe(p.x, p.y, p.x, p.y, steps);
    }

    public boolean swipe(@NonNull Point start, @NonNull Point end, int durationMs) {
        Point jitterStart = applyJitter(start, 8, 24);
        Point jitterEnd = applyJitter(end, 8, 24);
        Point[] path = buildBezierPath(jitterStart, jitterEnd, 32);
        int steps = Math.max(10, durationMs / 8);
        return device.swipe(path, steps);
    }

    public boolean swipe(@NonNull SwipeDirection direction, float distanceRatio, int durationMs) {
        int width = device.getDisplayWidth();
        int height = device.getDisplayHeight();
        distanceRatio = clamp(distanceRatio, 0.2f, 0.95f);

        int startX = width / 2;
        int startY = height / 2;

        int deltaX = (int) (width * distanceRatio / 2);
        int deltaY = (int) (height * distanceRatio / 2);

        Point start = new Point(startX, startY);
        Point end;
        switch (direction) {
            case UP -> end = new Point(startX, startY - deltaY);
            case DOWN -> end = new Point(startX, startY + deltaY);
            case LEFT -> end = new Point(startX - deltaX, startY);
            case RIGHT -> end = new Point(startX + deltaX, startY);
            default -> end = new Point(startX, startY);
        }
        return swipe(start, end, durationMs);
    }

    public boolean exists(@NonNull SelectorCondition condition) {
        return condition.isPresent(device);
    }

    public boolean waitGone(@NonNull SelectorCondition condition) {
        return condition.isGone(device);
    }

    private UiObject2 waitForObject(BySelector selector, long timeoutMs) {
        long wait = timeoutMs > 0 ? timeoutMs : DEFAULT_WAIT_MS;
        return device.wait(Until.findObject(selector), wait);
    }

    private Point randomPointIn(Rect bounds) {
        int width = bounds.width();
        int height = bounds.height();
        if (width <= 0 || height <= 0) {
            return new Point(bounds.centerX(), bounds.centerY());
        }
        float rx = 0.25f + random.nextFloat() * 0.5f;
        float ry = 0.25f + random.nextFloat() * 0.5f;
        int x = bounds.left + Math.round(width * rx);
        int y = bounds.top + Math.round(height * ry);
        return new Point(x, y);
    }

    private Point applyJitter(Point origin, int min, int max) {
        int jitterX = randomRange(min, max);
        int jitterY = randomRange(min, max);
        int directionX = random.nextBoolean() ? 1 : -1;
        int directionY = random.nextBoolean() ? 1 : -1;
        return new Point(origin.x + jitterX * directionX, origin.y + jitterY * directionY);
    }

    private int randomRange(int min, int max) {
        if (max <= min) {
            return min;
        }
        return min + random.nextInt(max - min + 1);
    }

    private Point[] buildBezierPath(Point start, Point end, int points) {
        Point[] path = new Point[Math.max(2, points)];
        path[0] = start;
        double dx = end.x - start.x;
        double dy = end.y - start.y;
        double distance = Math.hypot(dx, dy);
        if (distance < 1.0d) {
            path[path.length - 1] = end;
            return path;
        }

        boolean vertical = Math.abs(dy) > Math.abs(dx);
        double offsetRatio = vertical ? randomBetween(0.08, 0.15) : randomBetween(0.08, 0.12);
        double offset = distance * offsetRatio;
        double direction = vertical
                ? (dy > 0 ? -1 : 1)
                : (dx > 0 ? 1 : -1);

        double controlX = (start.x + end.x) / 2.0 + dy / distance * offset * direction;
        double controlY = (start.y + end.y) / 2.0 - dx / distance * offset * direction;

        for (int i = 1; i < path.length; i++) {
            double t = i / (double) (path.length - 1);
            double eased = easeInOutCubic(t);
            double x = bezier(start.x, controlX, end.x, eased);
            double y = bezier(start.y, controlY, end.y, eased);
            path[i] = new Point((int) Math.round(x), (int) Math.round(y));
        }
        path[path.length - 1] = end;
        return path;
    }

    private double bezier(double p0, double p1, double p2, double t) {
        double oneMinusT = 1.0 - t;
        return oneMinusT * oneMinusT * p0 + 2 * oneMinusT * t * p1 + t * t * p2;
    }

    private double easeInOutCubic(double t) {
        if (t < 0.5d) {
            return 4 * t * t * t;
        }
        double f = -2 * t + 2;
        return 1 - Math.pow(f, 3) / 2.0;
    }

    private double randomBetween(double min, double max) {
        return min + (max - min) * random.nextDouble();
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
