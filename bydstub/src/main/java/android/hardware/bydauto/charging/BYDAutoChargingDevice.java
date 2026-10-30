package android.hardware.bydauto.charging;

import android.content.Context;

/** STUB — real class provided by the DiLink ROM. */
public class BYDAutoChargingDevice {
    public static BYDAutoChargingDevice getInstance(Context context) { return null; }
    public int getChargerWorkState() { return 0; }
    public int getChargingGunState() { return 0; }
    public int setSocSaveSwitch(int on) { return 0; }
    public void unregisterListener(AbsBYDAutoChargingListener l) { }
}
