package com.cgeye.gps.unitylocationplugin;

/**
 * Created by CGEye.
 */

public class LocationData {

    private long time;
    private double latitude;
    private double longitude;

    private String addressLine;
    private String streetName;
    private String city;
    private String state;
    private String subAdminArea;
    private String countryName;
    private String countryCode;
    private String postalCode;
    private String knownName;

    public String getAddressLine() { return addressLine; }

    public void setAddressLine(String addressLine) { this.addressLine = addressLine; }

    public String getStreetName() { return streetName; }

    public void setStreetName(String streetName) { this.streetName = streetName; }

    public String getCity() { return city; }

    public void setCity(String city) { this.city = city; }

    public String getState() { return state; }

    public void setState(String state) { this.state = state; }

    public String getSubAdminArea() { return subAdminArea; }

    public void setSubAdminArea(String subAdminArea) { this.subAdminArea = subAdminArea; }

    public String getCountryName() { return countryName; }

    public void setCountryName(String countryName) { this.countryName = countryName; }

    public String getCountryCode() { return countryCode; }

    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }

    public String getPostalCode() { return postalCode; }

    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }

    public String getKnownName() { return knownName; }

    public void setKnownName(String knownName) { this.knownName = knownName; }

    public long getTime() {
        return time;
    }

    public void setTime(long time) { this.time = time; }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }
}
