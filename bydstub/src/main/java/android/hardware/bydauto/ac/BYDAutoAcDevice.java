package android.hardware.bydauto.ac;

import android.content.Context;
import android.hardware.bydauto.BYDAutoEventValue;

/** STUB — signatures mined from diplus 1.3.8. See docs/BYD_API_MAPPING.md for values. */
public class BYDAutoAcDevice {
    public static BYDAutoAcDevice getInstance(Context context) { return null; }

    public int getAcStartState() { return 0; }
    public int getAcWindLevel() { return 0; }
    public int getAcWindMode() { return 0; }
    public int getAcCycleMode() { return 0; }
    public int getTemperatureUnit() { return 0; }
    public int getTemprature(int area) { return 0; }

    public int setAcControlMode(int area, int mode) { return 0; }
    public int setAcTemperature(int area, int temperature, int reserved, int unit) { return 0; }
    public int setAcWindLevel(int area, int level) { return 0; }
    public int setAcWindMode(int area, int mode) { return 0; }
    public int setAcCycleMode(int area, int mode) { return 0; }
    public int setAcDefrostState(int area, int which, int state) { return 0; }
    public int setAcVentilationState(int area, int state) { return 0; }
    public int stop(int source) { return 0; }

    public int set(int[] featureIds, BYDAutoEventValue value) { return 0; }
    public BYDAutoEventValue get(int[] featureIds, Class<?> type) { return null; }
    public void unregisterListener(AbsBYDAutoAcListener l) { }
}
