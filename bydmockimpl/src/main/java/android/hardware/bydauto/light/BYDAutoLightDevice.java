package android.hardware.bydauto.light;

import android.content.Context;
import android.hardware.bydauto.BYDAutoEventValue;
import android.hardware.bydauto.BYDAutoFeatureIds;
import android.hardware.bydauto.MockRecorder;
import java.util.Arrays;

public class BYDAutoLightDevice {
    private static BYDAutoLightDevice inst;
    public static synchronized BYDAutoLightDevice getInstance(Context c) { MockRecorder.attach(c); if (inst == null) inst = new BYDAutoLightDevice(); return inst; }
    private int r(String m, String a) { MockRecorder.record("LightDevice", m, a); return 0; }
    public int set(int[] ids, BYDAutoEventValue v) { return r("set", (ids.length > 0 ? BYDAutoFeatureIds.nameOf(ids[0]) : "") + " value=" + (v == null ? "" : v.intValue)); }
    public BYDAutoEventValue get(int[] ids, Class<?> t) {
        BYDAutoEventValue v = new BYDAutoEventValue();
        if (ids.length > 0 && ids[0] == BYDAutoFeatureIds.SENSOR_INCAR_TEMPERATURE) v.intValue = 24;
        return v;
    }
    public int getDayTimeLightState() { return 1; }
    public int setDayTimeLightState(int s) { return r("setDayTimeLightState", String.valueOf(s)); }
}
