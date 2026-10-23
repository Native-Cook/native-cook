package com.ngocdevv.nativecook.blocks.animations.superchargecard

internal object M {
    const val step = 0.004166666666666667
    const val maxFrame = 0.03333333333333333
    const val epsilon = 1e-9
    const val seed = 5360382.0
    const val chargeDuration = 2.4
    const val chargeKnee = 0.85
    const val chargeLinearEnd = 0.85

    object charging {
        const val glow = 0.18
        const val glyphPower = 0.8
        const val flickerUntil = 0.5
    }

    object press {
        const val sink = 6.0
        const val scale = 0.985
        const val stiffness = 520.0
        const val damping = 34.0
    }

    object release {
        const val stiffness = 260.0
        const val damping = 20.0
    }

    object strike {
        const val leader = 0.1
        const val end = 1.6
        const val ringAt = 0.17
        const val treeDuration = 0.11
        const val flash = 0.92
        const val flashHold = 0.03333333333333333
        const val flashTau = 0.07
        const val drop = 5.0
        const val dipScale = 0.97
        const val kick = 14.0
        const val rotation = 0.024434609527920613
        const val glyph = 1.8
        const val glyphScale = 1.14
        const val glyphCool = 0.6
        const val stiffness = 420.0
        const val damping = 18.0
        const val glowRise = 0.22
        const val stampAt = 0.23
        const val stampDuration = 0.2
        const val branchEnd = 0.16
        const val channelEnd = 0.43
        const val treeHot = 1.6
        const val treeCold = 0.3
        const val treeTau = 0.7
        const val ringHot = 1.6
        const val ringSettle = 0.5
        const val shock = 0.52
        const val dim = 0.35
        const val dimDuration = 0.06
        const val glyphScaleTau = 0.12
        const val leaderJump = 0.02
        const val leaderSteps = 5.0
        const val leaderLevel = 0.3
        const val branchLevel = 0.8
        val pulseEnds = doubleArrayOf(0.15, 0.19, 0.245, 0.295)
        val pulseLevels = doubleArrayOf(1.0, 0.25, 0.8, 0.15, 0.5)
        const val shimmerInterval = 0.04
        const val shimmerVariants = 3.0
        const val shimmerPoints = 0.38
        const val shaft = 1.5
    }

    object drain {
        const val energy = 0.9
        const val glow = 0.22
        const val ring = 0.3
        const val type = 0.2
        const val end = 1.2
        const val rate = 0.6
        const val acceleration = 2.2
        const val glyph = 0.9
        const val glyphPower = 0.7
        const val flickerLevel = 0.45
        const val bezierX1 = 0.77
        const val bezierX2 = 0.175
        val flickers = doubleArrayOf(0.16, 0.205, 0.39, 0.445)
    }

    object touch {
        const val rise = 0.09
        const val fall = 0.06
        const val inset = 10.0
        const val bottom = 440.0
        const val hitSlop = 16.0
        const val holdSink = 4.0
        const val tapTime = 0.25
        const val tapTravel = 12.0
    }

    object tick {
        const val slow = 0.22
        const val fast = 0.045
        const val light = 0.4
        const val medium = 0.75
    }

    object shake {
        const val base = 0.3
        const val gain = 3.4
        const val rotation = 0.008726646259971648
        const val interval = 0.016666666666666666
    }

    object ember {
        const val base = 0.12
        const val amplitude = 0.02
        const val period = 4.0
        const val tickPeriod = 4.5
        const val tickDuration = 0.05
        const val tickLevel = 0.4
    }

    object flicker {
        const val period = 0.19
        const val duration = 0.045
        const val depth = 0.55
    }

    object charged {
        const val breath = 0.06
        const val period = 3.2
        const val pulsePeriod = 2.4
        const val pulseDuration = 0.7
    }

    object filament {
        const val interval = 0.07
        const val fastInterval = 0.055
        const val fastAt = 0.8
        const val thirdAt = 0.6
        const val base = 0.35
        const val gain = 0.65
    }

    object arc {
        const val threshold = 0.15
        const val slowPause = 0.26
        const val fastPause = 0.04
        const val lifeMin = 0.05
        const val lifeMax = 0.11
        const val chargedPeriod = 1.4
    }

    object corona {
        const val threshold = 0.35
        const val period = 0.12
        const val lifeMin = 0.04
        const val lifeMax = 0.09
        const val chargedPeriod = 1.8
    }

    object spark {
        const val ringCount = 30.0
        const val boltCount = 14.0
        const val groundCount = 12.0
        const val drainCount = 6.0
        const val minSpeed = 120.0
        const val maxSpeed = 760.0
        const val gravity = 900.0
        const val drag = 0.65
        const val minLife = 0.35
        const val maxLife = 1.3
        const val birthSpan = 0.05
        const val streak = 0.022
        const val minLength = 0.6
    }

    object reduced {
        const val flash = 0.2
        const val channel = 0.3
        const val channelLevel = 0.6
    }

    object script {
        const val period = 12.0
        const val firstDown = 0.8
        const val firstUp = 1.9
        const val secondDown = 2.9
        const val release = 5.8
        val firstPoint = doubleArrayOf(98.0, 330.0)
        val secondPoint = doubleArrayOf(300.0, 410.0)
        val playFrom = doubleArrayOf(96.0, 312.0)
        val playTo = doubleArrayOf(312.0, 420.0)
        const val playDown = 8.3
        const val playUp = 9.3
        const val tap = 10.0
        const val resume = 3.5
        const val wanderX = 46.0
        const val wanderY = 22.0
        const val frequencyX = 0.43
        const val frequencyY = 0.61
    }

    object print {
        const val dotPop = 0.12
        const val stampDrop = 6.0
        const val stampScale = 0.15
        const val bodyRise = 4.0
        const val captionRise = 4.0
        const val captionOut = 0.09
        const val captionIn = 0.24
        const val captionAt = 1.0
        const val captionDuration = 0.16
        const val releaseHz = 2.0
        const val stampDamping = 6.0
        const val stampFrequency = 10.0
    }

    const val fadeMs = 260.0
    const val accessibilityInterval = 0.1
    const val settleTolerance = 0.001
}
