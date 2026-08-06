package app.morphe.extension.youtube.patches;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.youtube.settings.Settings;

/**
 * Extension logic for skipping silence / fast forwarding in silence using ExoPlayer.
 */
public final class SkipSilencePatch {

    private static WeakReference<Object> audioSinkRef = new WeakReference<>(null);

    /**
     * Injection point.
     * Called when the AudioSink / ExoPlayer audio renderer is initialized.
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

            boolean applied = false;
            try {
                Method method = audioSink.getClass().getMethod("setSkipSilenceEnabled", boolean.class);
                method.setAccessible(true);
                method.invoke(audioSink, enabled);
                applied = true;
            } catch (Exception ignored) {}

            if (!applied) {
                for (Method method : audioSink.getClass().getDeclaredMethods()) {
                    if (method.getParameterTypes().length == 1
                            && method.getParameterTypes()[0] == boolean.class
                            && method.getReturnType() == void.class) {
                        try {
                            method.setAccessible(true);
                            method.invoke(audioSink, enabled);
                        } catch (Exception ignored) {}
                    }
                }
            }
        } catch (Exception ex) {
            Logger.printException(() -> "applySkipSilence failure", ex);
        }
    }
}
