/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.patches.youtube.interaction.skipsilence

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patches.shared.misc.settings.preference.SwitchPreference
import app.morphe.patches.youtube.layout.buttons.overlay.addPlayerOverlayPreferences
import app.morphe.patches.youtube.layout.buttons.overlay.playerOverlayButtonsSettingsPatch
import app.morphe.patches.youtube.layout.captions.StartVideoInformerFingerprint
import app.morphe.patches.youtube.layout.player.buttons.playerOverlayButtonsHookPatch
import app.morphe.patches.youtube.misc.extension.sharedExtensionPatch
import app.morphe.patches.youtube.misc.playercontrols.addTopControl
import app.morphe.patches.youtube.misc.playercontrols.initializeTopControl
import app.morphe.patches.youtube.misc.playercontrols.legacyPlayerControlsPatch
import app.morphe.patches.youtube.misc.playercontrols.legacyPlayerControlsResourcePatch
import app.morphe.patches.youtube.misc.settings.settingsPatch
import app.morphe.patches.youtube.shared.Constants.COMPATIBILITY_YOUTUBE
import app.morphe.patches.youtube.video.voiceovertranslation.AudioSinkSetVolumeFingerprint
import app.morphe.util.ResourceGroup
import app.morphe.util.copyResources

private val skipSilenceButtonResourcePatch = resourcePatch {
    dependsOn(legacyPlayerControlsResourcePatch)

    execute {
        copyResources(
            "skipsilencebutton",
            ResourceGroup(
                "drawable",
                "morphe_skip_silence_button_on.xml",
                "morphe_skip_silence_button_off.xml",
                "morphe_skip_silence_button_on_bold.xml",
                "morphe_skip_silence_button_off_bold.xml"
            )
        )
    }

    finalize {
        addTopControl(
            "skipsilencebutton",
            "@+id/morphe_skip_silence_button",
            "@+id/morphe_skip_silence_button"
        )
    }
}

private const val EXTENSION_BUTTON =
    "Lapp/morphe/extension/youtube/videoplayer/SkipSilenceButton;"

private const val EXTENSION_PATCH =
    "Lapp/morphe/extension/youtube/patches/SkipSilencePatch;"

val skipSilenceButtonPatch = bytecodePatch(
    name = "Fast forward in silence button",
    description = "Adds an option to display a fast forward in silence (skip silence) button in the video player overlay.",
) {
    dependsOn(
        sharedExtensionPatch,
        settingsPatch,
        skipSilenceButtonResourcePatch,
        playerOverlayButtonsSettingsPatch,
        legacyPlayerControlsPatch,
        playerOverlayButtonsHookPatch
    )

    compatibleWith(COMPATIBILITY_YOUTUBE)

    execute {
        addPlayerOverlayPreferences(
            SwitchPreference("morphe_skip_silence_button")
        )

        initializeTopControl(EXTENSION_BUTTON)
        StartVideoInformerFingerprint.method.addInstruction(
            0,
            "invoke-static { }, $EXTENSION_BUTTON->resetSkipSilenceButton()V"
        )
        StartVideoInformerFingerprint.method.addInstruction(
            0,
            "invoke-static { }, $EXTENSION_PATCH->resetSkipSilence()V"
        )
        AudioSinkSetVolumeFingerprint.method.addInstruction(
            0,
            "invoke-static { p0 }, $EXTENSION_PATCH->setAudioSink(Ljava/lang.Object;)V"
        )
    }
}
