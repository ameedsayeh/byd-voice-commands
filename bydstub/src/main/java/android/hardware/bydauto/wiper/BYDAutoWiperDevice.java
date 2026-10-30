package android.hardware.bydauto.wiper;

import android.content.Context;
import android.hardware.bydauto.BYDAutoEventValue;

/** STUB. diplus only reaches this device through the generic set()/get() feature-id path. */
public class BYDAutoWiperDevice {
    public static BYDAutoWiperDevice getInstance(Context context) { return null; }
    public int set(int[] featureIds, BYDAutoEventValue value) { return 0; }
    public BYDAutoEventValue get(int[] featureIds, Class<?> type) { return null; }
}
