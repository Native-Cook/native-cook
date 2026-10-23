// The block is built for Android 12 (API 31) and newer; the demo checks before showing it.
@file:androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.S)

package com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.console

// Signature effects of the tactile console. All were chosen (2026-10-02); the
// switches stay as a config so any one can be turned off in a single place.
data class Effects(
    /** Light illuminates the housing, the knob rim and the "table" below. */
    val spill: Boolean = true,
    /** Brightness level dims the page around the device. */
    val ambient: Boolean = true,
    /** The fill's leading edge has mass: it stretches with speed and wobbles. */
    val meniscus: Boolean = true,
    /** Knob detent haptics per groove and inertial flick (decay). */
    val crown: Boolean = true,
    /** Brushed-metal sheen on the knob that stays put while grooves turn. */
    val sheen: Boolean = true,
    /** Device tilt moves highlights (bevel band, knob sheen). Needs a device. */
    val tilt: Boolean = true,
    /** On entry the light rises from 0, the number rolls up, the knob homes. */
    val powerOn: Boolean = true,
    /** Faint glass streak over the track. */
    val glass: Boolean = true,
    /** Selected mode button gets a rim in its channel color. */
    val rim: Boolean = true,
    /** Soft haptic at the trough of a mode switch, same frame as the color. */
    val troughHaptic: Boolean = true,
    /** Gentle magnet at 50% with a haptic. */
    val magnet: Boolean = true,
    /** Drag the finger below the track for ¼-speed fine adjustment. */
    val fineScrub: Boolean = true,
    /** Glass-floor reflection: the live device mirrored under itself. */
    val reflection: Boolean = true,
    /** A soft click per knob detent, with the haptic. Off by default. */
    val sound: Boolean = false,
    /** Hold the housing to pick the console up as a 3D object (Compose face
     * on a Filament body), turn it over, tap to set it down. */
    val lift: Boolean = true,
    /** Lifted: the light is a real light in the 3D scene (knob, chamfer), the
     * object glows, and the glass floor mirrors the lifted object. */
    val liftLight: Boolean = true,
)

val allEffects = Effects()
