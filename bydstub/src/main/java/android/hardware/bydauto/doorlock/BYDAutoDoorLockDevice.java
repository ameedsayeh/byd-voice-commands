package android.hardware.bydauto.doorlock;

import android.content.Context;
import android.hardware.bydauto.BYDAutoEventValue;

/** STUB — real class provided by the DiLink ROM. */
public class BYDAutoDoorLockDevice {
    public static BYDAutoDoorLockDevice getInstance(Context context) { return null; }

    public int getDoorLockStatus(int door) { return 0; }

    /** Generic setter path used across BYD devices (featureIds + value). */
    public int set(int[] featureIds, BYDAutoEventValue value) { return 0; }
    public BYDAutoEventValue get(int[] featureIds, Class<?> type) { return null; }

    public void unregisterListener(AbsBYDAutoDoorLockListener l) { }
}
