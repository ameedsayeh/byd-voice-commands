package android.hardware.bydauto.bodywork;

import android.content.Context;
import android.hardware.bydauto.BYDAutoEventValue;
import android.hardware.bydauto.MockRecorder;
import java.util.Arrays;
import java.util.concurrent.CopyOnWriteArrayList;

/** MOCK with simulated motors: open/close move ~5%/100ms and report percent; 3 stops. */
public class BYDAutoBodyworkDevice {
    private static BYDAutoBodyworkDevice inst;
    private final int[] pct = new int[7];          // areas 1..6
    private final int[] dir = new int[7];          // +1 opening, -1 closing, 0 still
    private final int[] goal = new int[7];
    private final CopyOnWriteArrayList<AbsBYDAutoBodyworkListener> listeners = new CopyOnWriteArrayList<>();
    private Thread motor;

    public static synchronized BYDAutoBodyworkDevice getInstance(Context c) { MockRecorder.attach(c); if (inst == null) inst = new BYDAutoBodyworkDevice(); return inst; }

    public String getAutoVIN() { return "MOCKVIN000000000"; }
    public int getWindowOpenPercent(int area) { return area >= 1 && area <= 6 ? pct[area] : 0; }

    public int setAllWindowState(int a, int b, int c, int d) {
        MockRecorder.record("BodyworkDevice", "setAllWindowState", a + "," + b + "," + c + "," + d);
        command(1, a); command(2, b); command(3, c); command(4, d);
        return 0;
    }
    public int setMoonRoofState(int s) { MockRecorder.record("BodyworkDevice", "setMoonRoofState", String.valueOf(s)); command(5, s); return 0; }
    public int setSunshadeState(int p) { MockRecorder.record("BodyworkDevice", "setSunshadeState", String.valueOf(p)); pct[6] = p; return 0; }
    public int set(int[] ids, BYDAutoEventValue v) { MockRecorder.record("BodyworkDevice", "set", Arrays.toString(ids) + "=" + (v == null ? "" : v.intValue)); return 0; }
    public BYDAutoEventValue get(int[] ids, Class<?> t) { return new BYDAutoEventValue(); }
    public void registerListener(AbsBYDAutoBodyworkListener l) { listeners.add(l); }
    public void registerListener(AbsBYDAutoBodyworkListener l, int[] ids) { listeners.add(l); }
    public void unregisterListener(AbsBYDAutoBodyworkListener l) { listeners.remove(l); }

    private synchronized void command(int area, int code) {
        switch (code) {
            case 1: dir[area] = 1; goal[area] = 100; break;
            case 2: dir[area] = -1; goal[area] = 0; break;
            case 3: dir[area] = 0; break;
            case 4: dir[area] = pct[area] < 50 ? 1 : -1; goal[area] = 50; break;
            case 5: dir[area] = pct[area] < 15 ? 1 : -1; goal[area] = 15; break;
            default: return;
        }
        if (motor == null || !motor.isAlive()) { motor = new Thread(this::run, "mock-motor"); motor.start(); }
    }

    private void run() {
        while (true) {
            boolean moving = false;
            synchronized (this) {
                for (int a = 1; a <= 5; a++) {
                    if (dir[a] == 0) continue;
                    pct[a] = Math.max(0, Math.min(100, pct[a] + 5 * dir[a]));
                    if ((dir[a] > 0 && pct[a] >= goal[a]) || (dir[a] < 0 && pct[a] <= goal[a])) dir[a] = 0;
                    for (AbsBYDAutoBodyworkListener l : listeners) l.onWindowOpenPercentChanged(a, pct[a]);
                    moving |= dir[a] != 0;
                }
            }
            if (!moving) { MockRecorder.record("BodyworkDevice", "positions", "FL=" + pct[1] + " FR=" + pct[2] + " RL=" + pct[3] + " RR=" + pct[4] + " roof=" + pct[5]); return; }
            try { Thread.sleep(100); } catch (InterruptedException e) { return; }
        }
    }
}
