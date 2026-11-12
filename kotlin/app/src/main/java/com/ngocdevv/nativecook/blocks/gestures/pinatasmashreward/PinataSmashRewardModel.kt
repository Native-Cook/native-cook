package com.ngocdevv.nativecook.blocks.gestures.pinatasmashreward

/** Value models shared by the closed-form timeline, finger driver and renderer. */
internal object PinataSmashRewardModel {
    data class PinataSmashRewardPoint(val x: Double, val y: Double)
    data class PinataSmashRewardHit(
        val t: Double, val x: Double, val y: Double, val kind: Int, val amp: Double,
        val inc: Double, val seed: Double, val loot: List<Int>, val conf: Int,
        val tiles: Int, val tokens: Int, val value: Int,
    )
    data class PinataSmashRewardStory(val hits: List<PinataSmashRewardHit> = emptyList(), val clear: Double = -1.0, val clearKind: Int = 0)
    data class PinataSmashRewardGeo(
        val chipX: Double, val chipY: Double, val bulbs: List<PinataSmashRewardPoint>,
        val flank: PinataSmashRewardPoint, val body: PinataSmashRewardPoint, val attach: PinataSmashRewardPoint,
    )
    data class PinataSmashRewardLoot(
        val k: Int, var x: Double, var y: Double, var rot: Double,
        var s: Double = 1.0, var a: Double = 1.0, var sx: Double = 1.0,
    )
    data class PinataSmashRewardConf(val x: Double, val y: Double, val w: Double, val h: Double, val rot: Double, val c: Int, val a: Double, val diamond: Boolean)
    data class PinataSmashRewardToken(val x: Double, val y: Double, val s: Double, val a: Double = 1.0, val tx: List<Double> = emptyList(), val ty: List<Double> = emptyList())
    class PinataSmashRewardFrame {
        var ax = 0.0; var ay = 0.0; var roll = 0.0; var yaw = 0.0; var sq = 0.0; var sqAng = 0.0
        var press = 0.0; var tail = 0.0; var bow = 0.0; var burst = 0.0; var crack1 = 0.0; var crack2 = 0.0
        var bloomX = 0.0; var bloomY = 0.0; var bloom = 0.0; var hole = 0.0
        var bulbBoost = 1.0; var waveFrom = -1; var waveTau = 0.0; var flicker = 1.0
        var rippleX = 0.0; var rippleY = 0.0; var rippleR = 0.0; var rippleA = 0.0
        var ringX = 0.0; var ringY = 0.0; var ringR = 0.0; var ringA = 0.0; var flash = 0.0
        var stage = 1.0; var stageX = 0.0; var stageY = 0.0
        var pillW = 186.0; var wL = 1.0; var wB = 0.0; var wD = 0.0
        var fill = 0.0; var fillGlow = 0.0; var shake = 0.0; var sparks = -1.0; var doneText = 0.0
        var count = 0.0; var chipBump = 0.0
        val tokens = mutableListOf<PinataSmashRewardToken>()
        val loot = mutableListOf<PinataSmashRewardLoot>()
        val conf = mutableListOf<PinataSmashRewardConf>()
        var streamers = -1.0; var fade = 1.0; var shadowLift = 0.0; var swayT = 0.0
    }
    class PinataSmashRewardDriver {
        var mode = 0; var loopT = 0.0; var t = 0.0; var story = PinataSmashRewardStory(); var last = 0.0
        var drag = false; var fx = 0.0; var fy = 0.0; var gx = 0.0; var gy = 0.0
        var ox = 0.0; var oy = 0.0; var vx = 0.0; var vy = 0.0; var th = 0.0; var om = 0.0
        var press = 0.0; var pressAim = 0.0
        var blendT = -1.0; var blendRoll = 0.0; var blendX = 0.0; var blendY = 0.0; var blendPending = false
        var lastAx = 0.0; var lastAy = 0.0; var lastRoll = 0.0
    }
}
