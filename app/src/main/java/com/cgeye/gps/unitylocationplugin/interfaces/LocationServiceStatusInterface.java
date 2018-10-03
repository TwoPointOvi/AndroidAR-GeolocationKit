package com.cgeye.gps.unitylocationplugin.interfaces;

//import kalmangps.cgeye.com.kalmangpsmanager.Services.KalmanLocationService.ServiceStatus;
import com.cgeye.gps.unitylocationplugin.services.KalmanLocationService.ServiceStatus;

/**
 * Created by lezh1k on 2/13/18.
 */

public interface LocationServiceStatusInterface {
    void serviceStatusChanged(ServiceStatus status);
    void GPSStatusChanged(int activeSatellites);
    void GPSEnabledChanged(boolean enabled);
    void lastLocationAccuracyChanged(float accuracy);
}
