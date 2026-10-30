package android.hardware.bydauto.power;

import android.content.Context;

/** STUB — real class provided by the DiLink ROM. */
public class BYDAutoPowerDevice {
    public static BYDAutoPowerDevice getInstance(Context context) { return null; }
    public int getMcuStatus() { return 0; }
    public int wakeUpMcu() { return 0; }
    public void registerListener(AbsBYDAutoPowerListener l) { }
    public void unregisterListener(AbsBYDAutoPowerListener l) { }
}
