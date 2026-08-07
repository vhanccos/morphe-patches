package app.morphe.extension.youtube.patches;

import android.util.Log;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import app.morphe.extension.youtube.settings.Settings;

/**
 * Robust extension logic for skipping silence in ExoPlayer with detailed Logcat logging.
 */
public final class SkipSilencePatch {

    private static final String TAG = "MorpheSkipSilence";
    private static WeakReference<Object> audioSinkRef = new WeakReference<>(null);

    /**
     * Injection point.
     * Called when YouTube ExoPlayer's DefaultAudioSink setVolume is invoked.
     */
    public static void setAudioSink(Object audioSink) {
        if (audioSink == null) {
            Log.w(TAG, "setAudioSink called with NULL object");
            return;
        }
        audioSinkRef = new WeakReference<>(audioSink);
        Log.i(TAG, "setAudioSink captured: " + audioSink.getClass().getName());
        applySkipSilence();
    }

    /**
     * Toggles or sets skip silence state.
     */
    public static void setSkipSilenceEnabled(boolean enabled) {
        Log.i(TAG, "setSkipSilenceEnabled: " + enabled);
        Settings.SKIP_SILENCE.save(enabled);
        applySkipSilence();
    }

    public static boolean isSkipSilenceEnabled() {
        return Settings.SKIP_SILENCE.get();
    }

    /**
     * Injection point.
     * Reset or re-apply skip silence when starting a new video.
     */
    public static void resetSkipSilence() {
        Log.d(TAG, "resetSkipSilence triggered");
        applySkipSilence();
    }

    public static void applySkipSilence() {
        try {
            final boolean enabled = isSkipSilenceEnabled();
            final Object audioSink = audioSinkRef.get();

            if (audioSink == null) {
                Log.w(TAG, "applySkipSilence: audioSink reference is NULL (not captured yet)");
                return;
            }

            Log.i(TAG, "applySkipSilence: Applying enabled=" + enabled + " to class " + audioSink.getClass().getName());

            boolean success = false;
            Class<?> clazz = audioSink.getClass();
            while (clazz != null && clazz != Object.class) {
                Log.d(TAG, "Inspecting class: " + clazz.getName());

                // 1. Inspect fields of DefaultAudioSink
                Field[] fields = clazz.getDeclaredFields();
                for (Field field : fields) {
                    try {
                        field.setAccessible(true);
                        Object fieldValue = field.get(audioSink);
                        if (fieldValue != null && !fieldValue.getClass().isPrimitive()) {
                            String fieldClassName = fieldValue.getClass().getName();
                            Method[] fieldMethods = fieldValue.getClass().getDeclaredMethods();
                            for (Method m : fieldMethods) {
                                if (m.getParameterTypes().length == 1 && m.getParameterTypes()[0] == boolean.class) {
                                    try {
                                        m.setAccessible(true);
                                        m.invoke(fieldValue, enabled);
                                        success = true;
                                        Log.i(TAG, "Successfully invoked " + m.getName() + "(" + enabled + ") on field " + field.getName() + " of type " + fieldClassName);
                                    } catch (Exception ex) {
                                        Log.e(TAG, "Error invoking " + m.getName() + " on field " + field.getName(), ex);
                                    }
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }

                // 2. Also invoke boolean setters on DefaultAudioSink if method name contains "silence"
                Method[] methods = clazz.getDeclaredMethods();
                for (Method method : methods) {
                    if (method.getParameterTypes().length == 1 && method.getParameterTypes()[0] == boolean.class) {
                        String name = method.getName().toLowerCase();
                        if (name.contains("silence")) {
                            try {
                                method.setAccessible(true);
                                method.invoke(audioSink, enabled);
                                success = true;
                                Log.i(TAG, "Successfully invoked method " + method.getName() + "(" + enabled + ") on " + audioSink.getClass().getName());
                            } catch (Exception ex) {
                                Log.e(TAG, "Error invoking method " + method.getName(), ex);
                            }
                        }
                    }
                }

                clazz = clazz.getSuperclass();
            }

            if (!success) {
                Log.w(TAG, "applySkipSilence: No matching method or field was found to apply skip silence.");
            }
        } catch (Exception ex) {
            Log.e(TAG, "applySkipSilence: Critical exception occurred", ex);
        }
    }
}
