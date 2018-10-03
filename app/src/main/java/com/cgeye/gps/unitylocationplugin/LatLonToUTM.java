package com.cgeye.gps.unitylocationplugin;

/**
 * Created by CGEye.
 */
public class LatLonToUTM {

    // equatorial radius
    private double equatorialRadius = 6378137;

    // polar radius
    private double polarRadius = 6356752.314;

    // scale factor
    private double k0 = 0.9996;

    // eccentricity
    private double e = Math.sqrt(1 - Math.pow(polarRadius / equatorialRadius, 2));
    private double e1sq = e * e / (1 - e * e);
    private double n = (equatorialRadius - polarRadius) / (equatorialRadius + polarRadius);

    // r curv 1
    private double rho = 6368573.744;
    // r curv 2
    private double nu = 6389236.914;

    // Calculate Meridional Arc Length
    // Meridional Arc
    private double S = 5103266.421;
    private double A0 = 6367449.146;
    private double B0 = 16038.42955;
    private double C0 = 16.83261333;
    private double D0 = 0.021984404;
    private double E0 = 0.000312705;

    // Calculation Constants
    // Delta Long
    private double p = -0.483084;
    private double sin1 = 4.84814E-06;

    // Coefficients for UTM Coordinates
    private double K1 = 5101225.115;
    private double K2 = 3750.291596;
    private double K3 = 1.397608151;
    private double K4 = 214839.3105;
    private double K5 = -2.995382942;
    private double A6 = -1.00541E-07;

    private double _easting;
    private double _northing;

    public LatLonToUTM() {
    }

    public void convertLatLonToUTM(double latitude, double longitude) {
        String UTM = "";

        setVariables(latitude, longitude);
        //String longZone = getLongZone(longitude);
        //LatZones latZones = new LatZones();
        //String latZone = latZones.getLatZone(latitude);

        _easting = getEasting();
        _northing = getNorthing(latitude);

        UTM = "" + ((int) _easting) + " "
                + ((int) _northing);
        // UTM = longZone + " " + latZone + " " + decimalFormat.format(_easting) +
        // " "+ decimalFormat.format(_northing);

        //return UTM;
    }

    private void setVariables(double latitude, double longitude) {
        latitude = latitude * Math.PI / 180;
        rho = equatorialRadius * (1 - e * e)
                / Math.pow(1 - Math.pow(e * Math.sin(latitude), 2), 3 / 2.0);

        nu = equatorialRadius / Math.pow(1 - Math.pow(e * Math.sin(latitude), 2), (1 / 2.0));

        double var1;
        if (longitude < 0.0)
        {
            var1 = ((int) ((180 + longitude) / 6.0)) + 1;
        }
        else
        {
            var1 = ((int) (longitude / 6)) + 31;
        }

        double var2 = (6 * var1) - 183;
        double var3 = longitude - var2;
        p = var3 * 3600 / 10000;

        S = A0 * latitude - B0 * Math.sin(2 * latitude) + C0 * Math.sin(4 * latitude) - D0
                * Math.sin(6 * latitude) + E0 * Math.sin(8 * latitude);

        K1 = S * k0;
        K2 = nu * Math.sin(latitude) * Math.cos(latitude) * Math.pow(sin1, 2) * k0 * (100000000)
                / 2;
        K3 = ((Math.pow(sin1, 4) * nu * Math.sin(latitude) * Math.pow(Math.cos(latitude), 3)) / 24)
                * (5 - Math.pow(Math.tan(latitude), 2) + 9 * e1sq * Math.pow(Math.cos(latitude), 2) + 4
                * Math.pow(e1sq, 2) * Math.pow(Math.cos(latitude), 4))
                * k0
                * (10000000000000000L);

        K4 = nu * Math.cos(latitude) * sin1 * k0 * 10000;

        K5 = Math.pow(sin1 * Math.cos(latitude), 3) * (nu / 6)
                * (1 - Math.pow(Math.tan(latitude), 2) + e1sq * Math.pow(Math.cos(latitude), 2)) * k0
                * 1000000000000L;

        A6 = (Math.pow(p * sin1, 6) * nu * Math.sin(latitude) * Math.pow(Math.cos(latitude), 5) / 720)
                * (61 - 58 * Math.pow(Math.tan(latitude), 2) + Math.pow(Math.tan(latitude), 4) + 270
                * e1sq * Math.pow(Math.cos(latitude), 2) - 330 * e1sq
                * Math.pow(Math.sin(latitude), 2)) * k0 * (1E+24);
    }

    private String getLongZone(double longitude)
    {
        double longZone;
        if (longitude < 0.0) {
            longZone = ((180.0 + longitude) / 6) + 1;
        }
        else {
            longZone = (longitude / 6) + 31;
        }

        String val = String.valueOf((int) longZone);
        if (val.length() == 1) {
            val = "0" + val;
        }

        return val;
    }

    private double getNorthing(double latitude)
    {
        double northing = K1 + K2 * p * p + K3 * Math.pow(p, 4);
        if (latitude < 0.0) {
            northing = 10000000 + northing;
        }

        return northing;
    }

    private double getEasting() {
        return 500000 + (K4 * p + K5 * Math.pow(p, 3));
    }

    public double returnEasting() {
        return _easting;
    }

    public double returnNorthing() {
        return _northing;
    }

}

class LatZones {
    private char[] letters = { 'A', 'C', 'D', 'E', 'F', 'G', 'H', 'J', 'K',
            'L', 'M', 'N', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Z' };

    private int[] degrees = { -90, -84, -72, -64, -56, -48, -40, -32, -24, -16,
            -8, 0, 8, 16, 24, 32, 40, 48, 56, 64, 72, 84 };

    private char[] negLetters = { 'A', 'C', 'D', 'E', 'F', 'G', 'H', 'J', 'K',
            'L', 'M' };

    private int[] negDegrees = { -90, -84, -72, -64, -56, -48, -40, -32, -24,
            -16, -8 };

    private char[] posLetters = { 'N', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W',
            'X', 'Z' };

    private int[] posDegrees = { 0, 8, 16, 24, 32, 40, 48, 56, 64, 72, 84 };

    private int arrayLength = 22;

    public LatZones() {
    }

    public int getLatZoneDegree(String letter) {
        char ltr = letter.charAt(0);
        for (int i = 0; i < arrayLength; i++) {
            if (letters[i] == ltr) {
                return degrees[i];
            }
        }

        return -100;
    }

    public String getLatZone(double latitude) {
        int latIndex = -2;
        int lat = (int) latitude;

        if (lat >= 0) {
            int len = posLetters.length;
            for (int i = 0; i < len; i++) {
                if (lat == posDegrees[i]) {
                    latIndex = i;
                    break;
                }

                if (lat > posDegrees[i]) {
                    continue;
                }
                else {
                    latIndex = i - 1;
                    break;
                }
            }
        }
        else {
            int len = negLetters.length;
            for (int i = 0; i < len; i++) {
                if (lat == negDegrees[i]) {
                    latIndex = i;
                    break;
                }

                if (lat < negDegrees[i]) {
                    latIndex = i - 1;
                    break;
                }
                else {
                    continue;
                }

            }

        }

        if (latIndex == -1) {
            latIndex = 0;
        }
        if (lat >= 0) {
            if (latIndex == -2) {
                latIndex = posLetters.length - 1;
            }

            return String.valueOf(posLetters[latIndex]);
        }
        else {
            if (latIndex == -2) {
                latIndex = negLetters.length - 1;
            }

            return String.valueOf(negLetters[latIndex]);
        }
    }

}
