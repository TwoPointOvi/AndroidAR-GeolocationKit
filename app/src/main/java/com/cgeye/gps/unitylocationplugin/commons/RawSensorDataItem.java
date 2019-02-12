package com.cgeye.gps.unitylocationplugin.commons;

/**
 * Created by CGEye.
 */
public class RawSensorDataItem {
    double north;
    double east;
    double up;

    public RawSensorDataItem(double north, double east, double up) {
        this.north = north;
        this.east = east;
        this.up = up;
    }

    public double getNorth() {
        return north;
    }

    public double getEast() {
        return east;
    }

    public double getUp() {
        return up;
    }
}
