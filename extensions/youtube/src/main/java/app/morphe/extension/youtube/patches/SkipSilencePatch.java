package app.morphe.extension.youtube.patches;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.youtube.settings.Settings;

/**
 * Extension logic for skipping silence in ExoPlayer using DefaultAudioSink / SilenceSkippingAudioProcessor.
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
        Logger.printDebug(() -> "setAudioSink captured: " + audioSink.getClass().getName());
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
            if (audioSink == null) {
                Logger.printDebug(() -> "applySkipSilence: audioSink is null");
                return;
            }

            Logger.printDebug(() -> "applySkipSilence: Applying enabled=" + enabled + " on " + audioSink.getClass().getName());

            boolean applied = false;

            Class<?> clazz = audioSink.getClass();
            while (clazz != null && clazz != Object.class) {
                // 1. Check fields for SilenceSkippingAudioProcessor or AudioProcessor instances
                for (Field field : clazz.getDeclaredFields()) {
                    try {
                        field.setAccessible(true);
                        Object fieldValue = field.get(audioSink);
                        if (fieldValue != null) {
                            String typeName = fieldValue.getClass().getName();
                            if (typeName.toLowerCase().contains("silence") || typeName.toLowerCase().contains("audioprocessor")) {
                                for (Method m : fieldValue.getClass().getDeclaredMethods()) {
                                    if (m.getParameterTypes().length == 1 && m.getParameterTypes()[0] == boolean.class) {
                                        try {
                                            m.setAccessible(true);
                                            m.invoke(fieldValue, enabled);
                                            applied = true;
                                            Logger.printDebug(() -> "Successfully invoked " + m.getName() + " on field " + typeName);
                                        } catch (Exception ignored) {}
                                    }
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }

                // 2. Check methods on audioSink itself containing "silence"
                for (Method method : clazz.getDeclaredMethods()) {
                    String methodName = method.getName().toLowerCase();
                    if ((methodName.contains("silence") || methodName.contains("skipsilence"))
                            && method.getParameterTypes().length == 1
                            && method.getParameterTypes()[0] == boolean.class) {
                        try {
                            method.setAccessible(true);
                            method.invoke(audioSink, enabled);
                            applied = true;
                            Logger.printDebug(() -> "Successfully invoked " + method.getName() + " on " + audioSink.getClass().getName());
                        } catch (Exception ignored) {}
                    }
                }

                clazz = clazz.getSuperclass();
            }

            if (!applied) {
                Logger.printDebug(() -> "applySkipSilence: No suitable method/field found on " + audioSink.getClass().getName());
            }
        } catch (Exception ex) {
            Logger.printException(() -> "applySkipSilence failure", ex);
        }
    }
}
