package com.cgeye.gps.unitylocationplugin.filters;

/**
 * Created by CGEye.
 */
public class LowPassFilter {

    //0 <= ALPHA <= 1 lower the value, the smoother it is
    private static final float ALPHA = 0.7f;

    public static float[] filter(float[] input, float[]prevVal) {
        if (input == null || prevVal == null || (input.length != prevVal.length)) {
            return input;
        }

        for (int i = 0; i < input.length; i++) {
            //prevVal[i] = prevVal[i] + ALPHA * (input[i] - prevVal[i]);
            prevVal[i] = ALPHA * prevVal[i] + (1 - ALPHA) * input[i];
        }
        return prevVal;
    }
}
