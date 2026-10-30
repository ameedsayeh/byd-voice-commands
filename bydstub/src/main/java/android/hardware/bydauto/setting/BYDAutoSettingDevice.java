package android.hardware.bydauto.setting;

import android.content.Context;
import android.hardware.bydauto.BYDAutoEventValue;

/** STUB — signatures mined from diplus 1.3.8. */
public class BYDAutoSettingDevice {
    public static BYDAutoSettingDevice getInstance(Context context) { return null; }

    public int setHeadlampLevel(int level) { return 0; }
    public int setSeatHeatingState(int seat, int level) { return 0; }
    public int setSeatVentilatingState(int seat, int level) { return 0; }
    public int setSteeringWheelHeatingState(int state) { return 0; }
    public int setSteerAssis(int mode) { return 0; }
    public int setEnergyFeedback(int level) { return 0; }
    public int setAutoExternalRearMirrorFollowUpSwitch(int state) { return 0; }
    public int voiceCtlBackDoor(int action) { return 0; }
    public int setIALBrightness(int area, int level, int reserved) { return 0; }
    public int getIALBrightness(int area) { return 0; }
    public int setMassageLevel(int seat, int level) { return 0; }
    public int setMassageMode(int seat, int mode) { return 0; }
    public int setSOCTarget(int target) { return 0; }

    public int set(int[] featureIds, BYDAutoEventValue value) { return 0; }
    public BYDAutoEventValue get(int[] featureIds, Class<?> type) { return null; }
    public void unregisterListener(AbsBYDAutoSettingListener l) { }
}
