package com.ngocdevv.nativecook.blocks.gestures.rewardcardreveal.shared.graphics

/**
 * Compose forwards this value directly to RenderNode's Sk3DView camera, which converts
 * inches to screen pixels with a factor of 72. Convert the desired dp perspective to
 * physical pixels first so the projection matches RN/iOS at every display density.
 */
fun cameraDistanceForPerspective(perspectiveDp: Float, density: Float): Float =
    perspectiveDp * density / 72f

/** RN `perspective: 900` used by the awakening card sway and flip. */
const val CARD_PERSPECTIVE_DP = 900f

/** RN `perspective: 600` used by the holographic card tilt. */
const val HOLO_PERSPECTIVE_DP = 600f
