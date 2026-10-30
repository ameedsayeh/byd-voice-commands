package android.hardware.bydauto;

import android.content.Context;
import android.hardware.IBYDAutoListener;

/** MOCK: records the generic set(deviceType, featureId, value) path. */
public abstract class AbsBYDAutoDevice {
    public AbsBYDAutoDevice(Context context) { MockRecorder.attach(context); }
    public abstract int getDevicetype();
    public abstract int getType();
    public abstract int[] getFeatureList();
    public abstract String getGetPermission();
    public abstract String getSetPermission();
    public int set(int deviceType, int featureId, int value) {
        MockRecorder.record("Generic", "set", "type=" + deviceType + " " + BYDAutoFeatureIds.nameOf(featureId) + " value=" + value);
        return 0;
    }
    public int set(int deviceType, int[] featureIds, int[] values) { return 0; }
    public void registerListener(IBYDAutoListener l) {}
    public void registerListener(IBYDAutoListener l, int[] ids) {}
    public void unregisterListener(IBYDAutoListener l) {}
}
