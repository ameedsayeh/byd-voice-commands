package android.hardware.bydauto.light;

import android.content.Context;
import android.hardware.bydauto.BYDAutoEventValue;

/** STUB — real class provided by the DiLink ROM. */
public class BYDAutoLightDevice {
    public static BYDAutoLightDevice getInstance(Context context) { return null; }

    public int getDayTimeLightState() { return 0; }
    public int getLightStatus(int light) { return 0; }
    public int getTurnLightState(int which) { return 0; }

    public int setDayTimeLightState(int state) { return 0; }
    public int set(int[] featureIds, BYDAutoEventValue value) { return 0; }
    public BYDAutoEventValue get(int[] featureIds, Class<?> type) { return null; }

    public void unregisterListener(AbsBYDAutoLightListener l) { }
}
