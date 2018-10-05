package com.cgeye.gps.unitylocationplugin.interfaces;

import com.cgeye.gps.unitylocationplugin.services.KalmanLocationService.ServiceStatus;

/**
 * Created by CGEye on 2/13/18.
 */

public interface LocationServiceStatusInterface {
    void serviceStatusChanged(ServiceStatus status);
    void GPSStatusChanged(int activeSatellites);
    void GPSEnabledChanged(boolean enabled);
    void lastLocationAccuracyChanged(float accuracy);
}
