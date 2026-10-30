package android.hardware.bydauto;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

/** Forwards each BYD API call to the recorder app (com.offline.bydmock). */
public final class MockRecorder {
    private static Context ctx;

    public static void attach(Context c) { if (c != null) ctx = c.getApplicationContext(); }

    public static void record(String device, String method, String args) {
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
}
