package app.morphe.extension.youtube.patches;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.youtube.settings.Settings;

/**
 * Robust extension logic for skipping silence in ExoPlayer using DefaultAudioSink & SilenceSkippingAudioProcessor.
 */
public final class SkipSilencePatch {

    private static WeakReference<Object> audioSinkRef = new WeakReference<>(null);

    /**
     * Injection point.
     * Called when YouTube ExoPlayer's DefaultAudioSink setVolume is invoked.
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
                // 1. Inspect fields of DefaultAudioSink to find SilenceSkippingAudioProcessor
                for (Field field : clazz.getDeclaredFields()) {
                    try {
                        field.setAccessible(true);
                        Object fieldValue = field.get(audioSink);
                        if (fieldValue != null && !fieldValue.getClass().isPrimitive()) {
                            for (Method m : fieldValue.getClass().getDeclaredMethods()) {
                                if (m.getParameterTypes().length == 1 && m.getParameterTypes()[0] == boolean.class) {
                                    try {
                                        m.setAccessible(true);
                                        m.invoke(fieldValue, enabled);
                                    } catch (Exception ignored) {}
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }

                // 2. Also invoke boolean setters on DefaultAudioSink if method contains "silence"
                for (Method method : clazz.getDeclaredMethods()) {
                    if (method.getParameterTypes().length == 1 && method.getParameterTypes()[0] == boolean.class) {
                        String name = method.getName().toLowerCase();
                        if (name.contains("silence")) {
                            try {
                                method.setAccessible(true);
                                method.invoke(audioSink, enabled);
                            } catch (Exception ignored) {}
                        }
                    }
                }

                clazz = clazz.getSuperclass();
            }
        } catch (Exception ex) {
            Logger.printException(() -> "applySkipSilence failure", ex);
        }
    }
}
