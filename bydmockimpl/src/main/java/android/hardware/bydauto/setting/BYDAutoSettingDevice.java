package android.hardware.bydauto.setting;

import android.content.Context;
import android.hardware.bydauto.BYDAutoEventValue;
import android.hardware.bydauto.BYDAutoFeatureIds;
import android.hardware.bydauto.MockRecorder;
import java.util.Arrays;

public class BYDAutoSettingDevice {
    private static BYDAutoSettingDevice inst;
    public static synchronized BYDAutoSettingDevice getInstance(Context c) { MockRecorder.attach(c); if (inst == null) inst = new BYDAutoSettingDevice(); return inst; }
    private int r(String m, String a) { MockRecorder.record("SettingDevice", m, a); return 0; }
    public int set(int[] ids, BYDAutoEventValue v) { return r("set", (ids.length > 0 ? BYDAutoFeatureIds.nameOf(ids[0]) : "") + " value=" + (v == null ? "" : v.intValue)); }
    public BYDAutoEventValue get(int[] ids, Class<?> t) {
        BYDAutoEventValue v = new BYDAutoEventValue();
        if (ids.length > 0 && ids[0] == BYDAutoFeatureIds.SENSOR_INCAR_TEMPERATURE) v.intValue = 24;
        return v;
    }
    public int setHeadlampLevel(int l) { return r("setHeadlampLevel", String.valueOf(l)); }
    public int setSeatHeatingState(int s, int l) { return r("setSeatHeatingState", s + "," + l); }
    public int setSeatVentilatingState(int s, int l) { return r("setSeatVentilatingState", s + "," + l); }
    public int setSteeringWheelHeatingState(int s) { return r("setSteeringWheelHeatingState", String.valueOf(s)); }
    public int setSteerAssis(int m) { return r("setSteerAssis", String.valueOf(m)); }
    public int setEnergyFeedback(int l) { return r("setEnergyFeedback", String.valueOf(l)); }
    public int setAutoExternalRearMirrorFollowUpSwitch(int s) { return r("setAutoExternalRearMirrorFollowUpSwitch", String.valueOf(s)); }
    public int voiceCtlBackDoor(int a) { return r("voiceCtlBackDoor", String.valueOf(a)); }
    public int setIALBrightness(int area, int l, int x) { return r("setIALBrightness", area + "," + l + "," + x); }
    public int getIALBrightness(int area) { return 0; }
    public int setMassageLevel(int s, int l) { return r("setMassageLevel", s + "," + l); }
    public int setMassageMode(int s, int m) { return r("setMassageMode", s + "," + m); }
    public int setSOCTarget(int t) { return r("setSOCTarget", String.valueOf(t)); }
}
