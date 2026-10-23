// Vendored from expo-content-transition 0.1.7 (MIT, rit3zh / 650 Industries). See THIRD_PARTY.md.
// The block is built for Android 12 (API 31) and newer; the demo checks before showing it.
@file:androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.S)

package com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.contenttransition.records

import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.contenttransition.enums.SlotRole

internal data class GlyphSlot(val role: SlotRole, val index: Int)
