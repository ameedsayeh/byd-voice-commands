package android.hardware.bydauto.wiper;

import android.content.Context;
import android.hardware.bydauto.BYDAutoEventValue;
import android.hardware.bydauto.BYDAutoFeatureIds;
import android.hardware.bydauto.MockRecorder;
import java.util.Arrays;

public class BYDAutoWiperDevice {
    private static BYDAutoWiperDevice inst;
    public static synchronized BYDAutoWiperDevice getInstance(Context c) { MockRecorder.attach(c); if (inst == null) inst = new BYDAutoWiperDevice(); return inst; }
    private int r(String m, String a) { MockRecorder.record("WiperDevice", m, a); return 0; }
    public int set(int[] ids, BYDAutoEventValue v) { return r("set", (ids.length > 0 ? BYDAutoFeatureIds.nameOf(ids[0]) : "") + " value=" + (v == null ? "" : v.intValue)); }
    public BYDAutoEventValue get(int[] ids, Class<?> t) {
        BYDAutoEventValue v = new BYDAutoEventValue();
        if (ids.length > 0 && ids[0] == BYDAutoFeatureIds.SENSOR_INCAR_TEMPERATURE) v.intValue = 24;
        return v;
    }

}
