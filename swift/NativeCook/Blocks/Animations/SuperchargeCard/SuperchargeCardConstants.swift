import Foundation

enum SuperchargeCardConstants {
    static let step = 1.0 / 240
    static let maxFrame = 1.0 / 30
    static let epsilon = 1e-9
    static let seed: UInt32 = 0x51cafe
    static let chargeDuration = 2.4
    static let chargeKnee = 0.85
    static let chargeLinearEnd = 0.85
    static let settleTolerance = 0.001

    enum Charging {
        static let glow = 0.18
        static let glyphPower = 0.8
        static let flickerUntil = 0.5
    }

    enum Press {
        static let sink = 6.0
        static let scale = 0.985
        static let stiffness = 520.0
        static let damping = 34.0
    }

    enum Release {
        static let stiffness = 260.0
        static let damping = 20.0
    }

    enum Strike {
        static let leader = 0.1
        static let end = 1.6
        static let ringAt = 0.17
        static let treeDuration = 0.11
        static let flash = 0.92
        static let flashHold = 1.0 / 30
        static let flashTau = 0.07
        static let drop = 5.0
        static let dipScale = 0.97
        static let kick = 14.0
        static let rotation = 1.4 * Double.pi / 180
        static let glyph = 1.8
        static let glyphScale = 1.14
        static let glyphCool = 0.6
        static let stiffness = 420.0
        static let damping = 18.0
        static let glowRise = 0.22
        static let stampAt = 0.23
        static let stampDuration = 0.2
        static let branchEnd = 0.16
        static let channelEnd = 0.43
        static let treeHot = 1.6
        static let treeCold = 0.3
        static let treeTau = 0.7
        static let ringHot = 1.6
        static let ringSettle = 0.5
        static let shock = 0.52
        static let dim = 0.35
        static let dimDuration = 0.06
        static let glyphScaleTau = 0.12
        static let leaderJump = 0.02
        static let leaderSteps = 5
        static let leaderLevel = 0.3
        static let branchLevel = 0.8
        static let pulseEnds = [0.15, 0.19, 0.245, 0.295]
        static let pulseLevels = [1.0, 0.25, 0.8, 0.15, 0.5]
        static let shimmerInterval = 0.04
        static let shimmerVariants = 3
        static let shimmerPoints = 0.38
        static let shaft = 1.5
    }

    enum Drain {
        static let energy = 0.9
        static let glow = 0.22
        static let ring = 0.3
        static let type = 0.2
        static let end = 1.2
        static let rate = 0.6
        static let acceleration = 2.2
        static let glyph = 0.9
        static let glyphPower = 0.7
        static let flickerLevel = 0.45
        static let bezierX1 = 0.77
        static let bezierX2 = 0.175
        static let flickers = [0.16, 0.205, 0.39, 0.445]
    }

    enum Touch {
        static let rise = 0.09
        static let fall = 0.06
        static let inset = 10.0
        static let bottom = 440.0
        static let hitSlop = 16.0
        static let holdSink = 4.0
        static let tapTime = 0.25
        static let tapTravel = 12.0
    }

    enum Tick {
        static let slow = 0.22
        static let fast = 0.045
        static let light = 0.4
        static let medium = 0.75
    }

    enum Shake {
        static let base = 0.3
        static let gain = 3.4
        static let rotation = 0.5 * Double.pi / 180
        static let interval = 1.0 / 60
    }

    enum Ember {
        static let base = 0.12
        static let amplitude = 0.02
        static let period = 4.0
        static let tickPeriod = 4.5
        static let tickDuration = 0.05
        static let tickLevel = 0.4
    }

    enum Flicker {
        static let period = 0.19
        static let duration = 0.045
        static let depth = 0.55
    }

    enum Charged {
        static let breath = 0.06
        static let period = 3.2
        static let pulsePeriod = 2.4
        static let pulseDuration = 0.7
    }

    enum Filament {
        static let interval = 0.07
        static let fastInterval = 0.055
        static let fastAt = 0.8
        static let thirdAt = 0.6
        static let base = 0.35
        static let gain = 0.65
    }

    enum Arc {
        static let threshold = 0.15
        static let slowPause = 0.26
        static let fastPause = 0.04
        static let lifeMin = 0.05
        static let lifeMax = 0.11
        static let chargedPeriod = 1.4
    }

    enum Corona {
        static let threshold = 0.35
        static let period = 0.12
        static let lifeMin = 0.04
        static let lifeMax = 0.09
        static let chargedPeriod = 1.8
    }

    enum Spark {
        static let ringCount = 30
        static let boltCount = 14
        static let groundCount = 12
        static let drainCount = 6
        static let minSpeed = 120.0
        static let maxSpeed = 760.0
        static let gravity = 900.0
        static let drag = 0.65
        static let minLife = 0.35
        static let maxLife = 1.3
        static let birthSpan = 0.05
    }

    enum Reduced {
        static let flash = 0.2
        static let channel = 0.3
        static let channelLevel = 0.6
    }

    enum Script {
        static let period = 12.0
        static let firstDown = 0.8
        static let firstUp = 1.9
        static let secondDown = 2.9
        static let release = 5.8
        static let playDown = 8.3
        static let playUp = 9.3
        static let tap = 10.0
        static let resume = 3.5
        static let firstPoint = SuperchargeCardPoint(x: 98, y: 330)
        static let secondPoint = SuperchargeCardPoint(x: 300, y: 410)
        static let playFrom = SuperchargeCardPoint(x: 96, y: 312)
        static let playTo = SuperchargeCardPoint(x: 312, y: 420)
        static let wanderX = 46.0
        static let wanderY = 22.0
        static let frequencyX = 0.43
        static let frequencyY = 0.61
    }

    enum Print {
        static let captionAt = 1.0
    }

    enum Event {
        static let selection = 1
        static let light = 2
        static let medium = 4
        static let rigid = 8
        static let soft = 16
        static let heavy = 32
        static let success = 64
    }

    enum Layout {
        static let bolt = [
            SuperchargeCardPoint(x: 240, y: 220),
            SuperchargeCardPoint(x: 222, y: 273),
            SuperchargeCardPoint(x: 281, y: 271),
            SuperchargeCardPoint(x: 156, y: 421),
            SuperchargeCardPoint(x: 193, y: 320),
            SuperchargeCardPoint(x: 149, y: 327)
        ]
        static let boltTop = SuperchargeCardPoint(x: 240, y: 220)
        static let pivot = SuperchargeCardPoint(x: 201, y: 458)
        static let glassX = 65.0
        static let glassY = 288.0
        static let glassWidth = 272.0
        static let glassHeight = 334.0
        static let paperLeft = 65.0
        static let paperTop = 448.0
        static let paperRight = 341.0
        static let paperBottom = 628.0
        static let ring = SuperchargeCardPoint(x: 268, y: 466)
        static let ringRadius = 30.0
    }
}
