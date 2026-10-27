package com.ngocdevv.nativecook.blocks.modals.confettigiftoffer

/**
 * The screen the block is laid out on, in dp (React Native's points): `useWindowDimensions`,
 * `useSafeAreaInsets` and the scale factors of ConfettiGiftOffer.tsx:209–211.
 */
internal class ConfettiGiftOfferMetrics(
    val width: Float,
    val height: Float,
    val fontScale: Float,
    val insetTop: Float,
    val insetBottom: Float,
) {
    /** Vertical scale against the reference's 402 × 874 screen. */
    val u: Float = height / 874f

    /** Horizontal scale against the reference's 402 × 874 screen. */
    val w: Float = width / 402f

    /** Big text or a short screen: the scenes flow and scroll instead of sitting on fixed positions. */
    val largeText: Boolean = fontScale > 1.3f || height < 700f
}
