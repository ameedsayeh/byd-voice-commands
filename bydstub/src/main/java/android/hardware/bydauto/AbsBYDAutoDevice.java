package android.hardware.bydauto;

import android.content.Context;
import android.hardware.IBYDAutoListener;

/**
 * STUB. Base class of every BYD device. diplus subclasses it to reach the generic
 * set(deviceType, featureId, value) entry point; we do the same in GenericBydDevice.
 */
public abstract class AbsBYDAutoDevice {
    public AbsBYDAutoDevice(Context context) { }

    public abstract int getDevicetype();
    public abstract int getType();
    public abstract int[] getFeatureList();
    public abstract String getGetPermission();
    public abstract String getSetPermission();

    public int set(int deviceType, int featureId, int value) { return 0; }
    public int set(int deviceType, int[] featureIds, int[] values) { return 0; }

    public void registerListener(IBYDAutoListener listener) { }
    public void registerListener(IBYDAutoListener listener, int[] ids) { }
    public void unregisterListener(IBYDAutoListener listener) { }
}
