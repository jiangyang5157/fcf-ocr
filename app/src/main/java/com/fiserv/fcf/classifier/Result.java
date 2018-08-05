package com.fiserv.fcf.classifier;

import java.util.ArrayList;
import java.util.Arrays;

public class Result {

    private ArrayList<String> dict = new ArrayList<>(Arrays.asList("A","B","C","D","E","F","G","H","I","J","K","L","M","N","O","P",
            "Q","R","S","T","U","V","W","X","Y","Z"));

    private final int mNumber;
    private final float mProbability;
    private final long mTimeCost;

    public Result(float[] result, long timeCost) {
        mNumber = argmax(result);
        mProbability = result[mNumber];
        mTimeCost = timeCost;
    }

    public int getNumber() {
        return mNumber;
    }

    public String getLetter() {
        return dict.get(mNumber);
    }

    public float getProbability() {
        return mProbability;
    }

    public long getTimeCost() {
        return mTimeCost;
    }

    private static int argmax(float[] probs) {
        int maxIdx = -1;
        float maxProb = 0.0f;
        for (int i = 0; i < probs.length; i++) {
            if (probs[i] > maxProb) {
                maxProb = probs[i];
                maxIdx = i;
            }
        }
        return maxIdx;
    }
}
