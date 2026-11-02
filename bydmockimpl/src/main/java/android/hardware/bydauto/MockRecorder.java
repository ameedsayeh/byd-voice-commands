package android.hardware.bydauto;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;

/** Forwards each BYD API call to the recorder app (com.offline.bydmock). */
public final class MockRecorder {
    private static Context ctx;

    public static void attach(Context c) { if (c != null) ctx = c.getApplicationContext(); }

    public static void record(String device, String method, String args) {
        enforce(device);
        Log.i("BYDMOCK-IMPL", device + "." + method + "(" + args + ")");
        if (ctx == null) return;
        try {
            Intent i = new Intent("com.offline.bydmock.RECORD")
                    .setClassName("com.offline.bydmock", "com.offline.bydmock.RecordReceiver")
                    .putExtra("device", device)
                    .putExtra("method", method)
                    .putExtra("args", args);
            ctx.sendBroadcast(i);
        } catch (Throwable t) {
            Log.w("BYDMOCK-IMPL", "forward failed", t);
        }
    }

    /**
     * Like the car: refuse the call unless the device's *_COMMON permission is granted. Only when the
     * recorder app defines those permissions, so older setups keep working.
     */
    private static void enforce(String device) {
        if (ctx == null) return;
        String area;
        switch (device) {
            case "AcDevice": area = "AC"; break;
            case "BodyworkDevice": area = "BODYWORK"; break;
            case "SettingDevice": area = "SETTING"; break;
            case "LightDevice": area = "LIGHT"; break;
            case "EnergyDevice": area = "ENERGY"; break;
            case "DoorLockDevice": area = "DOOR_LOCK"; break;
            default: return;
        }
        String perm = "android.permission.BYDAUTO_" + area + "_COMMON";
        try {
            ctx.getPackageManager().getPermissionInfo(perm, 0);
        } catch (PackageManager.NameNotFoundException e) {
            return;
        }
        if (ctx.checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Permission Denial: requires " + perm);
        }
    }
}
