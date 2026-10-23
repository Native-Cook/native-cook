package com.ngocdevv.nativecook.blocks.gestures.foilpacktear

import kotlin.math.*

internal object MOTION {
    val step = 1.0 / 600.0
    val maxElapsed = 0.1
    val loop = 13.0
    val idleSlow = 6.5
    val idleFast = 13.0 / 3.0
    val resumeDelay = 3.5
    val catchSpeed = 1400.0
    val tearTick = 18.0
    val coastTau = 0.18
    val lightTau = 0.06
    val tapTravel = 8.0
    val tapTime = 0.3
    val peekDelay = 0.14
    val newPackAt = 0.38
    val extractTime = 0.2
    val extractLift = 20.0
    val revealTravel = 0.58
    val revealPackFade = 0.18
    val flipAt = 0.34
    val revealDeadline = 1.5
    val peekFadeIn = 0.3
    val peekFadeOut = 0.12
    val stageKick = SpringConfig(260.0, 16.0)
    val tug = SpringConfig(300.0, 24.0)
    val mouth = SpringConfig(220.0, 20.0)
    val rise = SpringConfig(150.0, 15.0)
    val sink = SpringConfig(180.0, 20.0)
    val pullReturn = SpringConfig(260.0, 22.0)
    val scale = SpringConfig(170.0, 17.0)
    val flip = SpringConfig(62.0, 10.0)
    val tilt = SpringConfig(140.0, 12.0)
    val landing = SpringConfig(190.0, 19.0)
}