package android.hardware.bydauto.bodywork;

import android.hardware.IBYDAutoListener;

/** STUB. Area 1..4 = windows FL/FR/RL/RR, 5 = roof, 6 = sunshade. */
public abstract class AbsBYDAutoBodyworkListener implements IBYDAutoListener {
    public void onWindowOpenPercentChanged(int area, int percent) { }
}
