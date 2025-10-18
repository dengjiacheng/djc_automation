package com.automation.infrastructure.location;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.SystemClock;

import com.automation.domain.location.MockLocationData;

/**
 * Lightweight helper for injecting mock location updates.
 */
public class MockLocationProvider {

    private final String providerName;
    private final Context context;

    public MockLocationProvider(String providerName, Context context) {
        this.providerName = providerName;
        this.context = context.getApplicationContext();

        LocationManager lm = (LocationManager) this.context.getSystemService(Context.LOCATION_SERVICE);
        if (lm != null) {
            lm.addTestProvider(providerName, false, false, false, false, false,
                    true, true, 0, 5);
            lm.setTestProviderEnabled(providerName, true);
        }
    }

    public void pushLocation(MockLocationData data) {
        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) {
            return;
        }
        Location mockLocation = new Location(providerName);
        mockLocation.setLatitude(data.getLatitude());
        mockLocation.setLongitude(data.getLongitude());
        mockLocation.setAltitude(data.getAltitude());
        mockLocation.setAccuracy(data.getAccuracy());
        mockLocation.setTime(System.currentTimeMillis());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            mockLocation.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
        }
        lm.setTestProviderLocation(providerName, mockLocation);
    }

    public void pushLocation(double lat, double lon, double alt, float accuracy) {
        pushLocation(new MockLocationData(lat, lon, alt, accuracy));
    }

    public void shutdown() {
        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (lm != null) {
            lm.removeTestProvider(providerName);
        }
    }
}
