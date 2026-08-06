package app.morphe.extension.youtube.videoplayer;

import static app.morphe.extension.shared.StringRef.str;
import static app.morphe.extension.youtube.patches.LegacyPlayerControlsPatch.RESTORE_OLD_PLAYER_BUTTONS;

import android.view.View;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.ResourceType;
import app.morphe.extension.shared.ResourceUtils;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.youtube.patches.MonoAudioPatch;
import app.morphe.extension.youtube.settings.Settings;

@SuppressWarnings("unused")
public class MonoAudioButton {

    static {
        if (Settings.MONO_AUDIO_BUTTON.get()) {
            LegacyPlayerControlButton.incrementUpperButtonCount();
        }
    }

    @Nullable
    private static LegacyPlayerControlButton legacy;

    private static final int MONO_AUDIO_ON = ResourceUtils.getIdentifierOrThrow(
            ResourceType.DRAWABLE,
            RESTORE_OLD_PLAYER_BUTTONS
                    ? "morphe_mono_audio_button_on"
                    : "morphe_mono_audio_button_on_bold");
    private static final int MONO_AUDIO_OFF = ResourceUtils.getIdentifierOrThrow(
            ResourceType.DRAWABLE,
            RESTORE_OLD_PLAYER_BUTTONS
                    ? "morphe_mono_audio_button_off"
                    : "morphe_mono_audio_button_off_bold");

    /**
     * Injection point.
     */
    public static void initializeLegacyButton(View controlsView) {
        try {
            legacy = new LegacyPlayerControlButton(
                    controlsView,
                    "morphe_mono_audio_button",
                    null,
                    null,
                    Settings.MONO_AUDIO_BUTTON,
                    MonoAudioButton::handleClick,
                    null
            );
            updateButtonIcon();
        } catch (Exception ex) {
            Logger.printException(() -> "initializeLegacyButton failure", ex);
        }
    }

    /**
     * Injection point.
     */
    public static void resetMonoAudioButton() {
        updateButtonIcon();
    }

    private static void handleClick(View buttonView) {
        if (legacy == null) return;
        Utils.verifyOnMainThread();

        final boolean newState = !Settings.MONO_AUDIO.get();
        MonoAudioPatch.setMonoAudioEnabled(newState);

        Utils.showToastShort(str(newState
                ? "morphe_mono_audio_button_toast_on"
                : "morphe_mono_audio_button_toast_off"));

        animateButtonTransition(buttonView, getTargetIcon());
    }

    private static int getTargetIcon() {
        return Settings.MONO_AUDIO.get() ? MONO_AUDIO_ON : MONO_AUDIO_OFF;
    }

    private static void updateButtonIcon() {
        LegacyPlayerControlButton localInstance = legacy;
        if (localInstance == null) return;
        localInstance.setIcon(getTargetIcon());
    }

    private static void animateButtonTransition(View buttonView, int newIcon) {
        LegacyPlayerControlButton localInstance = legacy;
        if (localInstance == null) return;

        if (!(buttonView instanceof ImageView imageView)) {
            localInstance.setIcon(newIcon);
            return;
        }

        imageView.animate()
                .alpha(0.3f)
                .scaleX(1.15f)
                .scaleY(1.15f)
                .setDuration(100)
                .withEndAction(() -> {
                    localInstance.setIcon(newIcon);
                    imageView.animate()
                            .alpha(1.0f)
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(100)
                            .start();
                })
                .start();
    }
}
