package app.morphe.extension.youtube.patches;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.youtube.settings.Settings;

/**
 * Extension logic for skipping silence in ExoPlayer using DefaultAudioSink.
 */
public final class SkipSilencePatch {

    private static WeakReference<Object> audioSinkRef = new WeakReference<>(null);

    /**
     * Injection point.
     * Called when YouTube ExoPlayer's DefaultAudioSink is active.
     */
    public static void setAudioSink(Object audioSink) {
        if (audioSink == null) return;
        audioSinkRef = new WeakReference<>(audioSink);
        applySkipSilence();
    }

    /**
     * Toggles or sets skip silence state.
     */
    public static void setSkipSilenceEnabled(boolean enabled) {
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
        applySkipSilence();
    }

    public static void applySkipSilence() {
        try {
            final boolean enabled = isSkipSilenceEnabled();
            final Object audioSink = audioSinkRef.get();
            if (audioSink == null) return;

            Class<?> clazz = audioSink.getClass();
            while (clazz != null && clazz != Object.class) {
                // 1. Invoke single boolean parameter methods on DefaultAudioSink
                for (Method method : clazz.getDeclaredMethods()) {
                    if (method.getParameterTypes().length == 1
                            && method.getParameterTypes()[0] == boolean.class) {
                        try {
                            method.setAccessible(true);
                            method.invoke(audioSink, enabled);
                        } catch (Exception ignored) {}
                    }
                }

                // 2. Set boolean fields on DefaultAudioSink (e.g. skipSilenceEnabled)
                for (Field field : clazz.getDeclaredFields()) {
                    if (field.getType() == boolean.class) {
                        try {
                            field.setAccessible(true);
                            field.setBoolean(audioSink, enabled);
                        } catch (Exception ignored) {}
                    }
                }

                clazz = clazz.getSuperclass();
            }
        } catch (Exception ex) {
            Logger.printException(() -> "applySkipSilence failure", ex);
        }
    }
}
