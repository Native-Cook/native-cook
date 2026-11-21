package com.ngocdevv.nativecook.blocks.progress.pressedstampcard

internal class PressedStampCardFrame(
    val seals: List<PressedStampCardSeal>,
    var count: Int, var light: Double = 315.0,
    var oldCount: Int = count, var roll: Double = 1.0, var orders: Double = count.toDouble(),
    var dip: Double = 0.0, var ripple: DoubleArray = DoubleArray(4), var gold: Double = 0.0,
    var fly: PressedStampCardSeal = PressedStampCardSeal(), var target: Int = -1,
    var event: Double = -1.0, var phase: Double = 0.0, var tilt: Double = 0.0,
    var dieTilt: Double = 0.0, var dieGlow: Double = 0.0, var dent: Double = 0.0,
    var push: Double = 1.0, var sweep: Double = -1.0, var buttonGold: Double = 0.0,
    var buttonRoll: Double = 0.0, var redeemRing: Double = 0.0,
    var flip: Double = 0.0, var flipFlash: Double = 0.0,
)
