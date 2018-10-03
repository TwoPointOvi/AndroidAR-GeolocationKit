package com.cgeye.gps.unitylocationplugin;

/**
 * Created by CGEye.
 */

public class KalmanLatLongFilter {

    private final float MinAccuracy = 1;

    private float qMtrsPerSecond;
    private long timeStampMilliseconds;
    private double lat;
    private double lng;
    private float variance; // P matrix. Negative means object uninitialised.
    public int consecutiveRejectCount;

    public KalmanLatLongFilter(float qMtrsPerSecond) {
        this.qMtrsPerSecond = qMtrsPerSecond;
        variance = -1;
        consecutiveRejectCount = 0;
    }

    public long get_TimeStamp() {
        return timeStampMilliseconds;
    }

    public double getLatitude() {
        return lat;
    }

    public double getLongitude() {
        return lng;
    }

    public float getAccuracy() {
        return (float) Math.sqrt(variance);
    }

    public void SetState(double lat, double lng, float accuracy, long timeStampMilliseconds) {
        this.lat = lat;
        this.lng = lng;
        variance = accuracy * accuracy;
        this.timeStampMilliseconds = timeStampMilliseconds;
    }

    /**
     * Kalman filter processing for latitude and longitude
     *
     * latMeasurement - new measurement of latitude
     * lngMeasurement - new measurement of longitude
     * accuracy - measurement of one standard deviation error in meters
     * timeStampMilliseconds - time of measurement in millis
     */
    public void process(double latMeasurement, double lngMeasurement, float accuracy,
                        long timeStampMilliseconds, float qMtrsPerSecond) {

        this.qMtrsPerSecond = qMtrsPerSecond;

        //comment if there is no min Accuracy
        if (accuracy < MinAccuracy) {
            accuracy = MinAccuracy;
        }

        if (variance < 0) {
            // if variance < 0, object is unitialised, so initialise with current values
            this.timeStampMilliseconds = timeStampMilliseconds;
            lat = latMeasurement;
            lng = lngMeasurement;
            variance = accuracy * accuracy;
        } else {
            // else apply Kalman filter methodology
            long TimeInc_milliseconds = timeStampMilliseconds
                    - this.timeStampMilliseconds;
            if (TimeInc_milliseconds > 0) {
                // time has moved on, so the uncertainty in the current position increases
                variance += TimeInc_milliseconds * qMtrsPerSecond * qMtrsPerSecond / 1000;
                this.timeStampMilliseconds = timeStampMilliseconds;

                //TODO: USE VELOCITY INFORMATION HERE TO GET A BETTER ESTIMATE OF CURRENT POSITION FOR BETTER ESTIMATE
            }

            // Kalman gain matrix K = Covarariance * Inverse(Covariance + MeasurementVariance)
            // NB: because K is dimensionless, it doesn't matter that varianc has different units to lat and lng
            float K = variance / (variance + accuracy * accuracy);
            lat += K * (latMeasurement - lat);
            lng += K * (lngMeasurement - lng);
            // new Covariance matrix is (IdentityMatrix - K) * Covariance
            variance = (1 - K) * variance;
        }
    }

    public int getConsecutiveRejectCount() {
        return consecutiveRejectCount;
    }

    public void setConsecutiveRejectCount(int consecutiveRejectCount) {
        this.consecutiveRejectCount = consecutiveRejectCount;
    }
}
