package android.hardware.bydauto.bodywork;

import android.content.Context;
import android.hardware.bydauto.BYDAutoEventValue;

/** STUB. Window codes: 0 keep, 1 open, 2 close, 3 stop, 4 half, 5 vent. */
public class BYDAutoBodyworkDevice {
    public static BYDAutoBodyworkDevice getInstance(Context context) { return null; }

    public String getAutoVIN() { return null; }
    public int getWindowOpenPercent(int area) { return 0; }

    public int setAllWindowState(int frontLeft, int frontRight, int rearLeft, int rearRight) { return 0; }
    public int setMoonRoofState(int state) { return 0; }
    public int setSunshadeState(int percent) { return 0; }

    public int set(int[] featureIds, BYDAutoEventValue value) { return 0; }
    public BYDAutoEventValue get(int[] featureIds, Class<?> type) { return null; }
    public void registerListener(AbsBYDAutoBodyworkListener l) { }
    public void registerListener(AbsBYDAutoBodyworkListener l, int[] ids) { }
    public void unregisterListener(AbsBYDAutoBodyworkListener l) { }
}
