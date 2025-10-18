package com.automation.domain.location;

/**
 * 表示一次模拟定位请求，便于后续扩展如速度、朝向等参数。
 */
public class MockLocationData {

    private final double latitude;
    private final double longitude;
    private final double altitude;
    private final float accuracy;

    public MockLocationData(double latitude, double longitude, double altitude, float accuracy) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.altitude = altitude;
        this.accuracy = accuracy;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public double getAltitude() {
        return altitude;
    }

    public float getAccuracy() {
        return accuracy;
    }
}
