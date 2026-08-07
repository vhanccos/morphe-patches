package app.morphe.extension.youtube.patches;

import android.util.Log;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import app.morphe.extension.youtube.settings.Settings;

/**
 * Extension logic for skipping silence in ExoPlayer.
 * Phase 1: Diagnostic build - dumps DefaultAudioSink structure to Logcat.
 * Phase 2: Applies skip silence once the correct method/field is identified.
 */
public final class SkipSilencePatch {

    private static final String TAG = "MorpheSkipSilence";
    private static WeakReference<Object> audioSinkRef = new WeakReference<>(null);
    private static boolean structureDumped = false;

    /**
     * Injection point.
     * Called from DefaultAudioSink.setVolume(F)V — p0 is the fully initialized DefaultAudioSink.
     */
    public static void setAudioSink(Object audioSink) {
        if (audioSink == null) {
            Log.w(TAG, "setAudioSink called with NULL");
            return;
        }

        Object prev = audioSinkRef.get();
        if (prev == audioSink) return; // same instance, skip

        audioSinkRef = new WeakReference<>(audioSink);
        Log.i(TAG, "=== setAudioSink captured: " + audioSink.getClass().getName() + " @" + Integer.toHexString(System.identityHashCode(audioSink)));

        if (!structureDumped) {
            structureDumped = true;
            dumpClassStructure(audioSink);
        }

        applySkipSilence();
    }

    public static void setSkipSilenceEnabled(boolean enabled) {
        Log.i(TAG, "setSkipSilenceEnabled: " + enabled);
        Settings.SKIP_SILENCE.save(enabled);
        applySkipSilence();
    }

    public static boolean isSkipSilenceEnabled() {
        return Settings.SKIP_SILENCE.get();
    }

    public static void resetSkipSilence() {
        Log.d(TAG, "resetSkipSilence triggered");
        applySkipSilence();
    }

    /**
     * Dumps ALL methods and fields of the AudioSink class hierarchy to Logcat.
     */
    private static void dumpClassStructure(Object obj) {
        Log.i(TAG, "========== CLASS STRUCTURE DUMP ==========");
        Class<?> clazz = obj.getClass();
        int level = 0;
        while (clazz != null && clazz != Object.class) {
            Log.i(TAG, "--- Level " + level + ": " + clazz.getName() + " ---");

            // Dump methods
            try {
                Method[] methods = clazz.getDeclaredMethods();
                Log.i(TAG, "  Methods (" + methods.length + "):");
                for (Method m : methods) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("    ");
                    sb.append(Modifier.toString(m.getModifiers())).append(" ");
                    sb.append(m.getReturnType().getName()).append(" ");
                    sb.append(m.getName()).append("(");
                    Class<?>[] params = m.getParameterTypes();
                    for (int i = 0; i < params.length; i++) {
                        if (i > 0) sb.append(", ");
                        sb.append(params[i].getName());
                    }
                    sb.append(")");
                    Log.i(TAG, sb.toString());
                }
            } catch (Exception ex) {
                Log.e(TAG, "  Error dumping methods", ex);
            }

            // Dump fields
            try {
                Field[] fields = clazz.getDeclaredFields();
                Log.i(TAG, "  Fields (" + fields.length + "):");
                for (Field f : fields) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("    ");
                    sb.append(Modifier.toString(f.getModifiers())).append(" ");
                    sb.append(f.getType().getName()).append(" ");
                    sb.append(f.getName());

                    // Try to read value for non-primitive object fields
                    if (!f.getType().isPrimitive()) {
                        try {
                            f.setAccessible(true);
                            Object val = f.get(obj);
                            if (val != null) {
                                sb.append(" = ").append(val.getClass().getName())
                                  .append("@").append(Integer.toHexString(System.identityHashCode(val)));
                            } else {
                                sb.append(" = null");
                            }
                        } catch (Exception ignored) {
                            sb.append(" = <inaccessible>");
                        }
                    }

                    Log.i(TAG, sb.toString());
                }
            } catch (Exception ex) {
                Log.e(TAG, "  Error dumping fields", ex);
            }

            clazz = clazz.getSuperclass();
            level++;
        }
        Log.i(TAG, "========== END DUMP ==========");
    }

    /**
     * Attempts to apply skip silence on the captured DefaultAudioSink.
     */
    public static void applySkipSilence() {
        try {
            final boolean enabled = isSkipSilenceEnabled();
            final Object audioSink = audioSinkRef.get();

            if (audioSink == null) {
                Log.w(TAG, "applySkipSilence: audioSink reference is NULL (not captured yet)");
                return;
            }

            Log.i(TAG, "applySkipSilence: enabled=" + enabled + " on " + audioSink.getClass().getName());

            // Strategy 1: Look for a method on DefaultAudioSink itself that takes (boolean) and
            // relates to silence skipping. In obfuscated code it will be something like a(Z)V.
            // We identify it by checking if it accesses an AudioProcessor-like field.
            Class<?> clazz = audioSink.getClass();
            while (clazz != null && clazz != Object.class) {
                for (Method m : clazz.getDeclaredMethods()) {
                    if (m.getParameterTypes().length == 1
                            && m.getParameterTypes()[0] == boolean.class
                            && m.getReturnType() == void.class) {
                        String name = m.getName();
                        // Try to invoke - wrap in try/catch so failures don't propagate
                        try {
                            m.setAccessible(true);
                            m.invoke(audioSink, enabled);
                            Log.i(TAG, "Invoked " + name + "(" + enabled + ") on " + clazz.getName());
                        } catch (Exception ex) {
                            Log.d(TAG, "Skipped " + name + ": " + ex.getMessage());
                        }
                    }
                }
                clazz = clazz.getSuperclass();
            }
        } catch (Exception ex) {
            Log.e(TAG, "applySkipSilence failure", ex);
        }
    }
}
