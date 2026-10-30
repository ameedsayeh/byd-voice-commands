package android.hardware.bydauto;

import java.lang.reflect.Field;

/**
 * MOCK. Real names/values mined from diplus, plus the provisional names BydController probes for
 * (marked "mock-only") so those code paths can be exercised on an emulator.
 */
public final class BYDAutoFeatureIds {
    public static final int SET_HUD_SWITCH_SET = 1276174371;
    public static final int ADAS_HMA_STATE_SET = 944767008;
    public static final int SET_INSIDE_LIGHT_STATE_SET = 1330643002;
    public static final int DOOR_LOCK_COMMAND_AREA_CHILDLOCK_LEFT_SET = 1276141584;
    public static final int DOOR_LOCK_COMMAND_AREA_CHILDLOCK_RIGHT_SET = 1276141586;
    // mock-only (provisional names probed at runtime on the car)
    public static final int DOOR_LOCK_COMMAND_AREA_ALL_SET = 1276141590;
    public static final int REAR_VIEW_MIRROR_FOLD_SET = 1300000001;
    public static final int WIPER_MAINTENANCE_MODE_SET = 1300000002;
    public static final int SET_IAL_COLOR_SET = 1300000003;
    public static final int SENSOR_INCAR_TEMPERATURE = 1300000004;

    public static final class Light {
        public static final int LIGHT_TURN_SIGNAL_LIGHT_SET = 871366669;
        public static final int LIGHT_FRONT_FOG_LIGHT_SET = 871366675;
        public static final int LIGHT_REAR_FOG_LIGHT_SET = 871366667;
    }

    /** For readable recordings: id -> field name. */
    public static String nameOf(int id) {
        for (Class<?> c : new Class<?>[]{BYDAutoFeatureIds.class, Light.class}) {
            for (Field f : c.getFields()) {
                try { if (f.getInt(null) == id) return f.getName(); } catch (Throwable ignored) {}
            }
        }
        return String.valueOf(id);
    }
}
