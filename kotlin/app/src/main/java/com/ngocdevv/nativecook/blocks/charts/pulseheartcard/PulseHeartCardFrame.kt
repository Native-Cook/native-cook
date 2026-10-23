package com.ngocdevv.nativecook.blocks.charts.pulseheartcard

/** Everything the shader and the reading need for one frame (FRAME in HeartCanvas.tsx), plus the chart cursor. */
internal class PulseHeartCardFrame(
    val beat: PulseHeartCardMotion.Beat = PulseHeartCardMotion.Beat(),
    val tau0: Double = PulseHeartCardMotion.NEVER,
    val tau1: Double = PulseHeartCardMotion.NEVER,
    val phi: Double = PulseHeartCardEngine.REST_PHI,
    val traceStart: Double = 0.0,
    val traceEnd: Double = -1.0,
    /** Selected chart sample, or -1. */
    val selected: Int = -1,
    val cursorOpacity: Double = 0.0,
) {
    val bpm: Int get() = if (selected >= 0) PulseHeartCardMotion.samples[selected] else PulseHeartCardMotion.REST_BPM.toInt()
}
