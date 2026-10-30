package android.hardware.bydauto.ac;

import android.content.Context;
import android.hardware.bydauto.BYDAutoEventValue;
import android.hardware.bydauto.BYDAutoFeatureIds;
import android.hardware.bydauto.MockRecorder;
import java.util.Arrays;

public class BYDAutoAcDevice {
    private static BYDAutoAcDevice inst;
    private int temp = 22, wind = 3;
    public static synchronized BYDAutoAcDevice getInstance(Context c) { MockRecorder.attach(c); if (inst == null) inst = new BYDAutoAcDevice(); return inst; }
    private int r(String m, String a) { MockRecorder.record("AcDevice", m, a); return 0; }
    public int getAcStartState() { return 1; }
    public int getAcWindLevel() { return wind; }
    public int getAcWindMode() { return 1; }
    public int getAcCycleMode() { return 0; }
    public int getTemperatureUnit() { return 1; }                 // 1 = Celsius
    public int getTemprature(int area) { return area == 4 ? 18 : temp; }   // 4 = outside
    public int setAcControlMode(int a, int m) { return r("setAcControlMode", a + "," + m); }
    public int setAcTemperature(int a, int t, int c, int u) { temp = t; return r("setAcTemperature", a + "," + t + "," + c + "," + u); }
    public int setAcWindLevel(int a, int l) { wind = l; return r("setAcWindLevel", a + "," + l); }
    public int setAcWindMode(int a, int m) { return r("setAcWindMode", a + "," + m); }
    public int setAcCycleMode(int a, int m) { return r("setAcCycleMode", a + "," + m); }
    public int setAcDefrostState(int a, int w, int s) { return r("setAcDefrostState", a + "," + w + "," + s); }
    public int setAcVentilationState(int a, int s) { return r("setAcVentilationState", a + "," + s); }
    public int stop(int src) { return r("stop", String.valueOf(src)); }
    public int set(int[] ids, BYDAutoEventValue v) { return r("set", Arrays.toString(ids) + "=" + (v == null ? "" : v.intValue)); }
    public BYDAutoEventValue get(int[] ids, Class<?> t) {
        BYDAutoEventValue v = new BYDAutoEventValue();
        if (ids.length > 0 && ids[0] == BYDAutoFeatureIds.SENSOR_INCAR_TEMPERATURE) v.intValue = 24;
        return v;
    }
}
