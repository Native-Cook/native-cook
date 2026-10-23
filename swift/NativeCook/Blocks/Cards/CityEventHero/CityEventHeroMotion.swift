import CoreGraphics
import Foundation

enum CityEventHeroMotion {
    static let LOOP = 10.0
    static let loop = LOOP
    static let RESUME_AFTER = 2_000.0
    static let RETURN_MS = 800.0
    static let EASE_OUT = [0.23, 1.0, 0.32, 1.0]
    static let EASE_NIGHT_TAP = [0.3, 0.55, 0.3, 1.0]
    static let EASE_IN_OUT = [0.77, 0.0, 0.175, 1.0]
    // Existing controller APIs express these two durations in seconds.
    static let resumeAfter = RESUME_AFTER / 1_000
    static let returnDuration = RETURN_MS / 1_000
    static let reducedFade = 0.2
    static let REST_NAME = 0
    static let restName = REST_NAME
    static let CENTRE_SLOT = 3
    static let centerSlot = CENTRE_SLOT
    static let SLOT_COUNT = CityEventHeroTheme.Layout.guests.count

    struct Spring: Sendable {
        let duration: Double
        let dampingRatio: Double
    }

    enum Springs {
        static let fan = Spring(duration: 0.55, dampingRatio: 0.72)
        static let spot = Spring(duration: 0.4, dampingRatio: 0.62)
        static let ui = Spring(duration: 0.4, dampingRatio: 0.8)
        static let pop = Spring(duration: 0.32, dampingRatio: 0.3)
        static let nudge = Spring(duration: 0.45, dampingRatio: 0.55)
        static let night = Spring(duration: 0.6, dampingRatio: 1)
    }

    enum Fan {
        static let stagger = 0.045
        static let gap = 18.0
        static let arcRadius = 2_400.0
        static let arcR = arcRadius
        static let centerRise = 8.0
        static let centreRise = centerRise
        static let tilt = 0.6
        static let flightLift = 14.0
        static let flightScale = 0.03
    }

    enum Spot {
        static let lift = 18.0
        static let scale = 0.07
    }

    enum Roll {
        static let name = 0.32
        static let nameRise = 26.0
        static let count = 0.3
        static let countRise = 22.0
    }

    enum Like {
        static let dip = 0.07
        static let dipScale = 0.14
        static let fill = 0.12
        static let ring = 0.5
        static let ringFrom = 0.9
        static let ringTo = 1.9
        static let ringStroke = 3.5
        static let unfill = 0.2
    }

    enum View {
        static let pressIn = 0.12
        static let hold = 0.15
        static let pressScale = 0.03
        static let nudge = 10.0
        static let nudgeOut = 0.12
        static let sweep = 0.5
        static let bandLead = 1.05
        static let band = 110.0
        static let bandSkew = -20.0
    }

    enum Ambient {
        // Retained for the original Swift renderer; Round 3 uses Space for camera geometry.
        static let zoom = 0.035
        static let focusX = 603.0
        static let focusY = 1_000.0
        static let mist = 0.15
        static let drift = 22.0
    }

    enum Space {
        static let focusX = 428.0
        static let focusY = 650.0
        static let far = 0.08
        static let perDepth = 0.32
        static let tower = 0.5
        static let near = 0.78
        static let ampX = 32.0
        static let ampY = 24.0
        static let dolly = 0.1
        static let coverX = 1.0 / 400
        static let coverY = 1.0 / 600
        static let title = 0.3
        static let follow = 0.12
        static let reach = 1.4
        static let tilt = 30.0
        static let tiltTau = 1.2
    }

    static let DEPTH_SCALE = 5.2
    static let depthScale = DEPTH_SCALE
    static let FAN_STAGGER = 0.045

    enum CameraSpring {
        static let value = Spring(duration: 0.7, dampingRatio: 0.82)
    }

    typealias SPRINGS = Springs
    typealias FAN = Fan
    typealias SPOT = Spot
    typealias ROLL = Roll
    typealias LIKE = Like
    typealias VIEW = View
    typealias AMBIENT = Ambient
    typealias SPACE = Space
    typealias NIGHT = Night

    enum Night {
        static let fall = 2.5
        static let fallEnd = 3.4
        static let lift = 8.0
        static let liftEnd = 9.0
        static let grade = 0.7
        static let frontFrom = 0.15
        static let soft = 0.035
        static let edge = 0.03
        static let scrubX = 0.6
        static let scrub = 0.4
        static let flick = 0.8
        static let toggle = 2.2
        static let fireFade = 0.6
        // Kept for the existing touch controller's re-arm threshold.
        static let rearm = 0.6
    }

    enum Day {
        static let from = 0.7
        static let to = 2.5
        static let hourFrom = 9.0
        static let hourDusk = 19.0
        static let hourEnd = 23.75
        static let azFrom = 110.0
        static let azTo = 345.0
        static let elPeak = 50.0
        static let rise = 7.2
        static let set = 19.1
        static let breakIn = 0.14
        static let fadeFrom = 0.93
    }
    typealias DAY = Day

    enum ViewCamera {
        static let focalLength = 1_000.0
        static let centerX = 425.0
        static let horizon = 930.0
        static let f = focalLength
        static let cx = centerX
    }
    typealias VIEW_CAM = ViewCamera

    enum Title {
        static let inTime = 0.75
        static let inEnd = 1.0
        static let out = 3.05
        static let outEnd = 3.4
        static let hold = 0.6
        static let fade = 0.35
        static let step = 5.0
        static let base = 640.0
        static let size = 92.0
        static let suffix = 26.0
        static let separation = 8.0
        static let sep = separation
    }
    typealias TITLE = Title

    enum CTA {
        static let press = 3.2
        static let release = 3.4
        static let dusk = 1.1
    }

    enum NightSpring {
        static let value = Spring(duration: 0.6, dampingRatio: 1)
    }
    static let NIGHT_SPRING = NightSpring.value

    enum Tower {
        static let tipX = 428.0
        static let tipY = 293.0
        static let baseY = 1_320.0
    }
    typealias TOWER = Tower

    enum LED {
        static let at = 3.45
        static let cols = 29
        static let row = 3.0
        static let mid = 427.5
        static let eye = 930.0
        static let sag = 0.035
        static let rows = 342.0
        static let boot = 0.35
        static let words = ["SEE", "YOU", "THERE"]
        static let pages = [0.3, 0.72, 1.14, 1.62]
        static let pagesEnd = 2.22
        static let enter = 0.16
        static let leave = 0.1
        static let climb = 300.0
        static let drift = 20.0
        static let center = 200.0
        static let centre = center
        static let slot = 40.0
        static let dateGap = 8.0
        static let colsFrom = 7
        static let glyphCols = 3
        static let glyphRows = 5
        static let stagger = 0.04
        static let letterFlash = 0.18
        static let dateFrom = 31.0
        static let dateTo = 17.0
        static let roll = 0.4
        static let avatarIn = 2.2
        static let avatarInEnd = 2.36
        static let avatarRow = 150.0
        static let climbEnd = 2.95
        static let avatarR = 1.5
        static let fire = 2.95
        static let out = 2.95
        static let outEnd = 3.4
        static let fieldWave = 120.0
        static let fieldSpeed = 180.0
    }

    enum TowerHalfWidth {
        static let y0 = 293.0
        static let step = 16.0
        static let values = [
            0.0, 4.2, 8, 11.1, 13.9, 16.6, 18.2, 19, 20, 21, 21.8, 22.1, 23, 24, 25, 25.8,
            26.1, 27, 28, 28.9, 29.1, 30, 30, 30.5, 32, 32, 32.8, 34, 34, 34.9, 36, 36,
            37, 37.9, 38, 39, 39.5, 40, 40, 40.3, 41, 41, 42, 42, 42, 42, 42, 42, 42.5,
            43, 43, 43.5, 43.5, 43.6, 44.4, 44.5, 44.5, 45, 45.5, 46, 46, 46, 46, 46.1, 46,
        ]
        static let hw = values
    }
    typealias TOWER_HW = TowerHalfWidth

    struct Burst: Sendable {
        let at: Double
        let dx: Double
        let dy: Double
        let launchDX: Double
        let launchDY: Double
        let count: Int
        let speed: Double
        let spread: Double
        let upSlow: Double
        let drag: Double
        let gravity: Double
        let life: Double
        let trail: Double
        let crackle: Double
        let hot: SIMD3<Double>
        let color: SIMD3<Double>
        let ember: SIMD3<Double>
        let flash: Double
    }

    enum Fire {
        static let at = LED.at + LED.fire
        static let end = 2.3
        static let flareIn = 0.15
        static let flareOut = 0.55
        static let flashIn = 0.05
        static let flashTau = 0.32
        static let flashGain = 1.1
        static let flashRadius = 260.0
        static let flashR = flashRadius
        static let rise = 0.45
        static let smokeIn = 0.25
        static let smokeTau = 1.1
        static let smokeRadius = 70.0
        static let smokeR = smokeRadius
        static let smokeRise = 18.0
        static let depthDp = 70.0
        static let bursts = [
            Burst(at: 0.12, dx: 0, dy: 0, launchDX: 0, launchDY: 0, count: 96, speed: 520, spread: 1.95, upSlow: 0.5, drag: 1.8, gravity: 360, life: 1.9, trail: 0.26, crackle: 0.15, hot: rgb(0xFFF7E6), color: rgb(0xFFC266), ember: rgb(0xE8642A), flash: 1),
            Burst(at: 0.55, dx: -270, dy: 330, launchDX: 25, launchDY: 600, count: 64, speed: 540, spread: 0, upSlow: 0, drag: 2.6, gravity: 300, life: 1.5, trail: 0.12, crackle: 0.45, hot: rgb(0xFFFFFF), color: rgb(0xE4ECFF), ember: rgb(0x9DB4FF), flash: 0.7),
            Burst(at: 0.72, dx: 260, dy: 260, launchDX: -20, launchDY: 640, count: 64, speed: 560, spread: 0, upSlow: 0, drag: 2.6, gravity: 300, life: 1.4, trail: 0.13, crackle: 0.25, hot: rgb(0xFFF1E8), color: rgb(0xFF6B57), ember: rgb(0xC2311F), flash: 0.75),
        ]
    }
    typealias FIRE = Fire

    enum Spark {
        static let coreWidth = [3.4, 2.8, 2.2]
        static let coreAlpha = [1.0, 0.8, 0.6]
        static let glowWidth = [12.0, 9.0, 7.0]
        static let glowAlpha = [0.38, 0.26, 0.18]
        static let glowBlur = 5.0
        static let coreShare = 0.4
        static let flicker = 30.0
        static let flickerKeep = 0.55
        static let speedBase = 0.8
        static let speedSpread = 0.3
        static let lifeBase = 0.75
        static let lifeSpread = 0.35
        static let angleJitter = 0.6
        static let shellCore = 0.02
        static let shellTrail = 0.1
        static let shellWidth = 3.2
        static let glowIn = 0.15
        static let glowDelay = 0.08
        static let coreIn = 0.08
        static let breakRadius = 30.0
    }
    typealias SPARK = Spark

    enum Reduced {
        static let fade = 0.2
    }
    typealias REDUCED = Reduced

    static let SHOW_END = LED.fire + Fire.end
    static let LED_STILL = 2.0
    static let LED_UNIFORM_COUNT = 16
    static let CAM_SPRING = CameraSpring.value

    struct FanPosition: Sendable {
        let centerX: Double
        let centerY: Double
        let rotation: Double
    }

    struct CameraFrame: Sendable {
        var x = 0.0
        var y = 0.0
        var z = 0.0
    }

    struct ClockFrame: Sendable {
        var hour = 12
        var minute = 0
        var isPM = false
    }

    struct SunFrame: Sendable {
        var azimuth = 0.0
        var elevation = 0.0
        var amount = 0.0
    }

    struct CTAFrame: Sendable {
        var press = 0.0
        var chevron = 0.0
        var sweep = 0.0
    }

    struct Frame: Sendable {
        var fan = Array(repeating: 0.0, count: SLOT_COUNT)
        var spot = Array(repeating: 0.0, count: SLOT_COUNT)
        var nameFrom = restName
        var nameTo = restName
        var nameProgress = 0.0
        var like = 0.0
        var pop = 1.0
        var ring = 0.0
        var count = 0.0
        var press = 0.0
        var chevron = 0.0
        var sweep = 0.0
        var zoom = 0.0
        var mist = 0.0
        var drift = 0.0
        var guestFade = 1.0
        var day = 0.0
        var night = 0.0
        var show = -1.0
        var fire = -1.0
        var camX = 0.0
        var camY = 0.0
        var camZ = 0.0
        var title = 0.0
        var still = 1.0

        var nameP: Double {
            get { nameProgress }
            set { nameProgress = newValue }
        }

        var chev: Double {
            get { chevron }
            set { chevron = newValue }
        }
    }

    struct SparkLaunch: Sendable {
        let angle: Double
        let speed: Double
    }

    static let slots = CityEventHeroTheme.Layout.guests.sorted { $0.centerX < $1.centerX }
    static let SLOTS = slots
    static let fanned: [FanPosition] = {
        let total = slots.reduce(0) { $0 + $1.diameter } + Fan.gap * Double(slots.count - 1)
        let center = slots[centerSlot]
        var x = center.centerX - total / 2
        return slots.map { guest in
            let centerX = x + guest.diameter / 2
            x += guest.diameter + Fan.gap
            let dx = centerX - center.centerX
            let drop = Fan.arcRadius - sqrt(Fan.arcRadius * Fan.arcRadius - dx * dx)
            let rotation = asin(dx / Fan.arcRadius) * 180 / Double.pi * Fan.tilt
            return FanPosition(centerX: centerX, centerY: center.centerY - Fan.centerRise + drop, rotation: rotation)
        }
    }()
    static let FANNED = fanned

    static func clamp(_ value: Double) -> Double { min(max(value, 0), 1) }
    static func clamp01(_ value: Double) -> Double { clamp(value) }
    static func segment(_ value: Double, _ from: Double, _ to: Double) -> Double { clamp((value - from) / (to - from)) }
    static func seg(_ value: Double, _ from: Double, _ to: Double) -> Double { segment(value, from, to) }

    static func easeOut(_ progress: Double) -> Double { bezier(progress, 0.23, 1, 0.32, 1) }
    static func easeInOut(_ progress: Double) -> Double { bezier(progress, 0.77, 0, 0.175, 1) }
    static func easeNightTap(_ progress: Double) -> Double { bezier(progress, 0.3, 0.55, 0.3, 1) }
    static func easeSine(_ progress: Double) -> Double { (1 - cos(Double.pi * clamp(progress))) / 2 }

    static func bezier(_ progress: Double, _ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double) -> Double {
        let x = clamp(progress)
        if x == 0 || x == 1 { return x }
        var low = 0.0
        var high = 1.0
        for _ in 0..<18 {
            let middle = (low + high) / 2
            let inverse = 1 - middle
            let sampledX = 3 * inverse * inverse * middle * x1 + 3 * inverse * middle * middle * x2 + middle * middle * middle
            if sampledX < x { low = middle } else { high = middle }
        }
        let middle = (low + high) / 2
        let inverse = 1 - middle
        return 3 * inverse * inverse * middle * y1 + 3 * inverse * middle * middle * y2 + middle * middle * middle
    }

    /// Closed-form damped spring from 0 to 1, sampled `seconds` after it starts.
    static func spring(_ seconds: Double, _ spring: Spring) -> Double {
        guard seconds > 0 else { return 0 }
        let omega = 2 * Double.pi / spring.duration
        let zeta = spring.dampingRatio
        if zeta == 1 {
            let envelope = exp(-omega * seconds)
            if envelope < 0.0001 { return 1 }
            return 1 - envelope * (1 + omega * seconds)
        }
        if zeta > 1 {
            let root = sqrt(zeta * zeta - 1)
            let first = -omega * (zeta - root)
            let second = -omega * (zeta + root)
            let envelope = (second * exp(first * seconds) - first * exp(second * seconds)) / (second - first)
            return 1 - envelope
        }
        let damped = omega * sqrt(1 - zeta * zeta)
        let envelope = exp(-zeta * omega * seconds)
        if envelope < 0.0001 { return 1 }
        return 1 - envelope * (cos(damped * seconds) + zeta * omega / damped * sin(damped * seconds))
    }

    static func criticalSpring(_ seconds: Double, initialVelocity: Double) -> Double {
        guard seconds > 0 else { return 0 }
        let omega = 2 * Double.pi / NightSpring.value.duration
        let envelope = exp(-omega * seconds)
        if envelope < 0.0001 { return 1 }
        return 1 + (-1 + (initialVelocity - omega) * seconds) * envelope
    }

    static func rank(_ slot: Int) -> Int { abs(slot - centerSlot) }
    static func flight(_ fan: Double) -> Double { let value = clamp(fan); return 4 * value * (1 - value) }

    static func pop(at seconds: Double) -> Double {
        if seconds <= 0 { return 1 }
        if seconds < Like.dip { return 1 - Like.dipScale * easeOut(seconds / Like.dip) }
        return 1 - Like.dipScale + Like.dipScale * spring(seconds - Like.dip, Springs.pop)
    }

    static func nudge(at seconds: Double) -> Double {
        if seconds <= 0 { return 0 }
        if seconds < View.nudgeOut { return easeOut(seconds / View.nudgeOut) }
        return 1 - spring(seconds - View.nudgeOut, Springs.nudge)
    }

    static func night(at time: Double) -> Double { nightAt(time) }

    static func nightAt(_ time: Double) -> Double {
        if time < Night.fall || time >= Night.liftEnd { return 0 }
        if time < Night.fallEnd { return easeSine(segment(time, Night.fall, Night.fallEnd)) }
        if time < Night.lift { return 1 }
        return 1 - easeSine(segment(time, Night.lift, Night.liftEnd))
    }

    static func hash3(burst: Int, spark: Int, channel: Int) -> Double {
        let value = sin(Double(burst) * 127.1 + Double(spark) * 311.7 + Double(channel) * 74.7) * 43_758.5453
        return value - floor(value)
    }

    static func flash(at burst: Burst, time: Double) -> Double { flashAt(burst, tau: time) }
    static func flash(_ burst: Burst, at time: Double) -> Double { flashAt(burst, tau: time) }

    static func flashAt(_ burst: Burst, tau: Double) -> Double {
        guard tau > 0 else { return 0 }
        let attack = tau < Fire.flashIn ? tau / Fire.flashIn : 1
        return burst.flash * attack * exp(-(tau - min(tau, Fire.flashIn)) / Fire.flashTau)
    }

    static func flare(at fire: Double) -> Double { flareAt(fire) }
    static func flare(_ fire: Double) -> Double { flareAt(fire) }

    static func flareAt(_ fire: Double) -> Double {
        if fire < 0 { return 0 }
        if fire < Fire.flareIn { return easeOut(fire / Fire.flareIn) }
        return 1 - easeOut(segment(fire, Fire.flareIn, Fire.flareIn + Fire.flareOut))
    }

    static func fireLight(at fire: Double) -> Double {
        guard fire >= 0, fire < Fire.end else { return 0 }
        var sum = 0.6 * flareAt(fire)
        for burst in Fire.bursts { sum += flashAt(burst, tau: fire - burst.at) }
        return clamp(sum)
    }

    static func fireLight(_ fire: Double) -> Double { fireLight(at: fire) }

    static func sparkOffset(speed: Double, angle: Double, time: Double, drag: Double, gravity: Double) -> CGPoint {
        let exponential = 1 - exp(-drag * time)
        let radius = speed * exponential / drag
        return CGPoint(x: cos(angle) * radius, y: sin(angle) * radius + gravity * (time / drag - exponential / (drag * drag)))
    }

    static func sparkLaunch(burst index: Int, spark: Int) -> SparkLaunch {
        let burst = Fire.bursts[index]
        let unit = (Double(spark) + Spark.angleJitter * (hash3(burst: index, spark: spark, channel: 0) - 0.5)) / Double(burst.count)
        let spread = Spark.speedBase + Spark.speedSpread * hash3(burst: index, spark: spark, channel: 2)
        if burst.spread > 0 {
            let side = 2 * unit - 1
            return SparkLaunch(angle: -Double.pi / 2 + burst.spread * side, speed: burst.speed * spread * (1 - burst.upSlow * (1 - abs(side))))
        }
        let depth = 2 * hash3(burst: index, spark: spark, channel: 3) - 1
        return SparkLaunch(angle: 2 * Double.pi * unit, speed: burst.speed * spread * sqrt(1 - depth * depth))
    }

    static func shellLeft(at time: Double) -> Double { 1 - easeOut(clamp(time / Fire.rise)) }

    static func burstColor(_ burst: Burst, fire: Double) -> SIMD3<Double> {
        let progress = clamp((fire - burst.at) / burst.life)
        if progress <= 0.25 { return mix(burst.hot, burst.color, progress / 0.25) }
        return mix(burst.color, burst.ember, (progress - 0.25) / 0.75)
    }

    static func restFrame() -> Frame { Frame() }

    static func easeSineProgress(_ progress: Double) -> Double { easeSine(progress) }

    static func fireOf(_ show: Double) -> Double {
        if show < LED.fire || show >= SHOW_END { return -1 }
        return show - LED.fire
    }

    static func fire(of show: Double) -> Double { fireOf(show) }

    static func hourOf(day: Double, night: Double) -> Double {
        if night > 0 { return Day.hourDusk + (Day.hourEnd - Day.hourDusk) * night }
        return Day.hourFrom + (Day.hourDusk - Day.hourFrom) * day
    }

    static func hour(day: Double, night: Double) -> Double { hourOf(day: day, night: night) }

    static func clockOf(_ hour: Double) -> ClockFrame {
        var output = ClockFrame()
        clockOf(hour, into: &output)
        return output
    }

    static func clock(hour: Double) -> ClockFrame { clockOf(hour) }

    static func clockOf(_ hour: Double, into output: inout ClockFrame) {
        let total = floor((hour * 60) / Title.step + 0.000001) * Title.step
        let hour24 = Int(floor(total / 60)) % 24
        output.minute = Int(total - floor(total / 60) * 60)
        output.isPM = hour24 >= 12
        let hour12 = hour24 % 12
        output.hour = hour12 == 0 ? 12 : hour12
    }

    static func sunOf(_ day: Double) -> SunFrame {
        var output = SunFrame()
        sunOf(day, into: &output)
        return output
    }

    static func sun(day: Double) -> SunFrame { sunOf(day) }

    static func sunOf(_ day: Double, into output: inout SunFrame) {
        let progress = clamp(day)
        let hour = Day.hourFrom + (Day.hourDusk - Day.hourFrom) * progress
        output.azimuth = (Day.azFrom + (Day.azTo - Day.azFrom) * progress) * Double.pi / 180
        let arc = clamp((hour - Day.rise) / (Day.set - Day.rise))
        output.elevation = Day.elPeak * sin(Double.pi * arc) * Double.pi / 180
        let breakProgress = progress / Day.breakIn
        let inAmount = breakProgress >= 1 ? 1 : breakProgress * breakProgress * (3 - 2 * breakProgress)
        let fadeProgress = clamp((progress - Day.fadeFrom) / (1 - Day.fadeFrom))
        output.amount = inAmount * (1 - fadeProgress * fadeProgress * (3 - 2 * fadeProgress))
    }

    static func towerHalfWidth(_ y: Double) -> Double {
        let position = (y - TowerHalfWidth.y0) / TowerHalfWidth.step
        if position <= 0 { return 0 }
        let last = TowerHalfWidth.values.count - 1
        if position >= Double(last) { return TowerHalfWidth.values[last] }
        let index = Int(floor(position))
        return TowerHalfWidth.values[index] + (TowerHalfWidth.values[index + 1] - TowerHalfWidth.values[index]) * (position - Double(index))
    }

    private static func cube(_ progress: Double) -> Double {
        let value = clamp(progress)
        return value * value * value
    }

    static func pageLength(_ page: Int) -> Int {
        page < LED.words.count ? LED.words[page].count : 5
    }

    static func pageRows(_ page: Int) -> Double {
        Double(pageLength(page)) * LED.slot - (LED.slot - Double(LED.glyphRows * 7)) + (page >= LED.words.count ? LED.dateGap : 0)
    }

    /// Writes the Round 3 façade uniforms in canonical order into a 16-element buffer.
    static func ledOf(_ time: Double, into uniforms: inout [Double]) {
        precondition(uniforms.count >= LED_UNIFORM_COUNT, "CityEventHero LED uniforms require 16 values")
        for index in 0..<LED_UNIFORM_COUNT { uniforms[index] = 0 }
        uniforms[2] = -1
        guard time >= 0, time < SHOW_END else {
            uniforms[1] = -1
            return
        }
        let dissolve = easeSine(segment(time, LED.out, LED.outEnd))
        uniforms[0] = clamp(time / 0.12) * (1 - dissolve)
        uniforms[1] = time < LED.boot ? easeInOut(time / LED.boot) * (LED.rows + 20) : 10_000
        for page in LED.pages.indices {
            let from = LED.pages[page]
            let to = page + 1 < LED.pages.count ? LED.pages[page + 1] : LED.pagesEnd
            guard time >= from, time < to else { continue }
            let height = pageRows(page)
            let rest = LED.center - height / 2
            let enter = easeOut(segment(time, from, from + LED.enter))
            let leave = segment(time, to - LED.leave, to)
            uniforms[2] = Double(page)
            uniforms[3] = -height + (rest + height) * enter + LED.drift * (time - from) + LED.climb * leave * leave * leave
            uniforms[4] = (time - from) / LED.stagger
        }
        let lastPage = LED.pages.count - 1
        let datePage = LED.pages[lastPage]
        uniforms[5] = floor(LED.dateFrom - (LED.dateFrom - LED.dateTo) * easeOut(segment(time, datePage + LED.enter * 0.5, datePage + LED.enter * 0.5 + LED.roll)) + 0.5)
        let climb = cube(segment(time, LED.avatarInEnd, LED.climbEnd))
        let rowAt = LED.avatarRow + (LED.rows - 6 - LED.avatarRow) * climb
        let y = Tower.baseY - rowAt * LED.row
        uniforms[6] = y
        uniforms[7] = LED.avatarR * towerHalfWidth(y) * easeOut(segment(time, LED.avatarIn, LED.avatarInEnd))
        uniforms[8] = time >= LED.avatarIn && time < LED.climbEnd + 0.06 ? 1 : 0
        let bootFlash = time - LED.boot
        let tipFlash = time - LED.fire
        uniforms[9] = (bootFlash > 0 ? 0.55 * exp(-bootFlash / 0.12) : 0) + (tipFlash > 0 ? exp(-tipFlash / 0.2) : 0)
        uniforms[10] = time * LED.fieldSpeed
        uniforms[11] = uniforms[0] * (0.55 + 0.45 * max(uniforms[2] >= 0 ? 1.0 : 0.0, uniforms[8])) + 0.4 * uniforms[9]
        uniforms[12] = dissolve
    }

    static func led(show: Double) -> [Double] {
        var uniforms = Array(repeating: 0.0, count: LED_UNIFORM_COUNT)
        ledOf(show, into: &uniforms)
        return uniforms
    }

    static func led(show: Double, into uniforms: inout [Double]) { ledOf(show, into: &uniforms) }

    static func camOf(_ time: Double) -> CameraFrame {
        var output = CameraFrame()
        camOf(time, into: &output)
        return output
    }

    static func camera(at time: Double) -> CameraFrame { camOf(time) }

    static func camOf(_ time: Double, into output: inout CameraFrame) {
        output.x = time < Day.to
            ? -easeSine(segment(time, Day.from, Day.to))
            : -(1 - easeSine(segment(time, Day.to, LED.at)))
        let back = 1 - easeSine(segment(time, Night.lift, Night.liftEnd))
        output.z = time < Night.lift ? easeSine(segment(time, LED.at, LED.at + 2.55)) : back
        output.y = time < Night.lift
            ? easeSine(segment(time, LED.at + LED.avatarIn - 0.05, LED.at + LED.climbEnd))
            : back
    }

    static func dayAt(_ time: Double) -> Double {
        if time >= Night.fallEnd { return 0 }
        return easeSine(segment(time, Day.from, Day.to))
    }

    static func ctaOf(_ time: Double) -> CTAFrame {
        var output = CTAFrame()
        ctaOf(time, into: &output)
        return output
    }

    static func ctaOf(_ time: Double, into output: inout CTAFrame) {
        let release = CTA.release
        output.press = time < release
            ? easeOut(segment(time, CTA.press, CTA.press + View.pressIn))
            : 1 - spring(time - release, Springs.ui)
        output.chevron = time < release
            ? 0
            : time < release + View.nudgeOut
                ? easeOut(segment(time, release, release + View.nudgeOut))
                : 1 - spring(time - release - View.nudgeOut, Springs.nudge)
        let sweep = segment(time, release, release + View.sweep)
        output.sweep = sweep > 0 && sweep < 1 ? easeInOut(sweep) : 0
    }

    static func frameAt(_ rawTime: Double) -> Frame {
        let remainder = rawTime.truncatingRemainder(dividingBy: LOOP)
        let time = remainder < 0 ? remainder + LOOP : remainder
        var frame = restFrame()
        frame.day = dayAt(time)
        frame.night = nightAt(time)
        frame.show = time >= LED.at && time < LED.at + SHOW_END ? time - LED.at : -1
        frame.fire = fireOf(frame.show)
        let camera = camOf(time)
        frame.camX = camera.x
        frame.camY = camera.y
        frame.camZ = camera.z
        frame.zoom = camera.z
        frame.title = easeOut(segment(time, Title.inTime, Title.inEnd)) * (1 - easeSine(segment(time, Title.out, Title.outEnd)))
        let cta = ctaOf(time)
        frame.press = cta.press
        frame.chevron = cta.chevron
        frame.sweep = cta.sweep
        frame.mist = (1 - cos((2 * Double.pi * time) / LOOP)) / 2
        frame.drift = time
        return frame
    }

    /// Compatibility entry point used by the Swift timeline controller.
    static func frame(at rawTime: Double) -> Frame { frameAt(rawTime) }

    private static func rgb(_ hex: Int) -> SIMD3<Double> {
        SIMD3(Double((hex >> 16) & 0xFF) / 255, Double((hex >> 8) & 0xFF) / 255, Double(hex & 0xFF) / 255)
    }

    private static func mix(_ from: SIMD3<Double>, _ to: SIMD3<Double>, _ amount: Double) -> SIMD3<Double> {
        let gamma = 2.2
        let linearFrom = SIMD3(pow(from.x, gamma), pow(from.y, gamma), pow(from.z, gamma))
        let linearTo = SIMD3(pow(to.x, gamma), pow(to.y, gamma), pow(to.z, gamma))
        let mixed = linearFrom + (linearTo - linearFrom) * amount
        return SIMD3(pow(mixed.x, 1 / gamma), pow(mixed.y, 1 / gamma), pow(mixed.z, 1 / gamma))
    }
}
