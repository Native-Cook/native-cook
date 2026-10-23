import Foundation

// Measured composition and source-identical prize, badge and particle trajectories.
enum ChromePrizeWheelGeometry {
    struct Backdrop {
        var x: Double
        var y: Double
        var width: Double
        var height: Double
        init(x: Double = 0.0, y: Double = 0.0, width: Double = 402.0, height: Double = 874.0) {
            self.x = x
            self.y = y
            self.width = width
            self.height = height
        }
    }
    struct Layout {
        var width: Double
        var height: Double
        var k: Double
        var fontScale: Double
        var cx: Double
        var cy: Double
        var radius: Double
        var titleTop: Double
        var titleSize: Double
        var titleLine: Double
        var subtitleTop: Double
        var bodySize: Double
        var bodyLine: Double
        var pointerX: Double
        var pointerY: Double
        var heroX: Double
        var heroY: Double
        var heroSize: Double
        var captionTop: Double
        var numeralSize: Double
        var numeralLine: Double
        var labelTop: Double
        var captionY: Double
        var buttonX: Double
        var buttonY: Double
        var buttonTop: Double
        var buttonHeight: Double
        var buttonWidth: Double
        var hintWidth: Double
        var hintHeight: Double
        var backdrop: Backdrop
        init(
            width: Double = 402.0, height: Double = 874.0, k: Double = 1.0, fontScale: Double = 1.0, cx: Double = 201.0,
            cy: Double = 424.0, radius: Double = 164.0, titleTop: Double = 78.0, titleSize: Double = 34.0,
            titleLine: Double = 40.0, subtitleTop: Double = 126.0, bodySize: Double = 17.0, bodyLine: Double = 24.0,
            pointerX: Double = 17.319999999999993, pointerY: Double = 424.0, heroX: Double = 201.0,
            heroY: Double = 404.0, heroSize: Double = 216.0, captionTop: Double = 628.0, numeralSize: Double = 56.0,
            numeralLine: Double = 64.0, labelTop: Double = 696.0, captionY: Double = 660.0, buttonX: Double = 201.0,
            buttonY: Double = 788.0, buttonTop: Double = 760.0, buttonHeight: Double = 56.0,
            buttonWidth: Double = 354.0, hintWidth: Double = 196.0, hintHeight: Double = 48.0,
            backdrop: Backdrop = Backdrop()
        ) {
            self.width = width
            self.height = height
            self.k = k
            self.fontScale = fontScale
            self.cx = cx
            self.cy = cy
            self.radius = radius
            self.titleTop = titleTop
            self.titleSize = titleSize
            self.titleLine = titleLine
            self.subtitleTop = subtitleTop
            self.bodySize = bodySize
            self.bodyLine = bodyLine
            self.pointerX = pointerX
            self.pointerY = pointerY
            self.heroX = heroX
            self.heroY = heroY
            self.heroSize = heroSize
            self.captionTop = captionTop
            self.numeralSize = numeralSize
            self.numeralLine = numeralLine
            self.labelTop = labelTop
            self.captionY = captionY
            self.buttonX = buttonX
            self.buttonY = buttonY
            self.buttonTop = buttonTop
            self.buttonHeight = buttonHeight
            self.buttonWidth = buttonWidth
            self.hintWidth = hintWidth
            self.hintHeight = hintHeight
            self.backdrop = backdrop
        }
    }
    struct PoseInput {
        var winner: Double
        var theta: Double
        var lift: Double
        var wheelScale: Double
        var swayA: Double
        var swayB: Double
        var tiltX: Double
        var tiltY: Double
        var hero: Double
        var heroSquash: Double
        var heroX: Double
        var heroY: Double
        var heroTilt: Double
        var collect: Double
        var time: Double
        var reduced: Bool
        init(
            winner: Double = 0.0, theta: Double = 0.0, lift: Double = 0.0, wheelScale: Double = 1.0,
            swayA: Double = 0.0, swayB: Double = 0.0, tiltX: Double = 0.0, tiltY: Double = 0.0, hero: Double = 0.0,
            heroSquash: Double = 0.0, heroX: Double = 0.0, heroY: Double = 0.0, heroTilt: Double = 0.0,
            collect: Double = 0.0, time: Double = 0.0, reduced: Bool = false
        ) {
            self.winner = winner
            self.theta = theta
            self.lift = lift
            self.wheelScale = wheelScale
            self.swayA = swayA
            self.swayB = swayB
            self.tiltX = tiltX
            self.tiltY = tiltY
            self.hero = hero
            self.heroSquash = heroSquash
            self.heroX = heroX
            self.heroY = heroY
            self.heroTilt = heroTilt
            self.collect = collect
            self.time = time
            self.reduced = reduced
        }
    }
    struct PrizePose {
        var x: Double
        var y: Double
        var side: Double
        var angle: Double
        var sx: Double
        var sy: Double
        var baseShift: Double
        var opacity: Double
        var startX: Double
        var startY: Double
        var tile: Double
        var sway: Double
        var fx: Double
        var fy: Double
        init(
            x: Double = 295.3, y: Double = 424.0, side: Double = 75.44, angle: Double = 0.0, sx: Double = 1.0,
            sy: Double = 0.9999680001706663, baseShift: Double = 0.0, opacity: Double = 1.0, startX: Double = 295.3,
            startY: Double = 424.0, tile: Double = 75.44, sway: Double = 0.0, fx: Double = 1.0,
            fy: Double = 0.9999680001706663
        ) {
            self.x = x
            self.y = y
            self.side = side
            self.angle = angle
            self.sx = sx
            self.sy = sy
            self.baseShift = baseShift
            self.opacity = opacity
            self.startX = startX
            self.startY = startY
            self.tile = tile
            self.sway = sway
            self.fx = fx
            self.fy = fy
        }
    }
    struct BadgePose {
        var x: Double
        var y: Double
        var size: Double
        var angle: Double
        var sx: Double
        var sy: Double
        var opacity: Double
        init(
            x: Double = 313.4056, y: Double = 443.6137723425475, size: Double = 22.14, angle: Double = 0.0,
            sx: Double = 1.0, sy: Double = 0.9999680001706663, opacity: Double = 1.0
        ) {
            self.x = x
            self.y = y
            self.size = size
            self.angle = angle
            self.sx = sx
            self.sy = sy
            self.opacity = opacity
        }
    }
    struct SkyPose {
        var x: Double
        var y: Double
        var size: Double
        var angle: Double
        var tile: Double
        var opacity: Double
        init(
            x: Double = 58.0, y: Double = 207.0, size: Double = 44.0, angle: Double = -0.3, tile: Double = 0.0,
            opacity: Double = 0.72
        ) {
            self.x = x
            self.y = y
            self.size = size
            self.angle = angle
            self.tile = tile
            self.opacity = opacity
        }
    }
    struct ParticlePose {
        var near: Bool
        var x: Double
        var y: Double
        var size: Double
        var angle: Double
        var tile: Double
        var opacity: Double
        init(
            near: Bool = false, x: Double = 201.0, y: Double = 404.0324337621673, size: Double = 13.59072868776566,
            angle: Double = -0.039385846992139144, tile: Double = 0.0, opacity: Double = 0.0
        ) {
            self.near = near
            self.x = x
            self.y = y
            self.size = size
            self.angle = angle
            self.tile = tile
            self.opacity = opacity
        }
    }
    struct ButtonPose {
        var width: Double
        var height: Double
        var scale: Double
        var x: Double
        init(width: Double = 196.0, height: Double = 48.0, scale: Double = 1.0, x: Double = 0.0) {
            self.width = width
            self.height = height
            self.scale = scale
            self.x = x
        }
    }
    struct ASSETSData {
        struct envData {
            let width: Double = 1024
            let height: Double = 512
        }
        let env = envData()
        struct envSoftData {
            let width: Double = 256
            let height: Double = 128
        }
        let envSoft = envSoftData()
        struct prizesData {
            let width: Double = 1152
            let height: Double = 768
            let tile: Double = 384
            let columns: Double = 3
            struct badgeData {
                let x: Double = 0.74
                let y: Double = 0.76
                let size: Double = 0.27
            }
            let badge = badgeData()
        }
        let prizes = prizesData()
        struct heroData {
            let size: Double = 768
        }
        let hero = heroData()
        struct badgesData {
            let width: Double = 768
            let height: Double = 128
            let tile: Double = 128
        }
        let badges = badgesData()
        struct pointerData {
            let width: Double = 264
            let height: Double = 216
            let pivotX: Double = 60
            let pivotY: Double = 108
            let pxPerRadius: Double = 492
        }
        let pointer = pointerData()
        struct particlesData {
            let width: Double = 768
            let height: Double = 192
            let tile: Double = 192
        }
        let particles = particlesData()
        struct backdropData {
            let width: Double = 804
            let height: Double = 1748
        }
        let backdrop = backdropData()
        struct swipeData {
            let size: Double = 96
        }
        let swipe = swipeData()
    }
    static let ASSETS = ASSETSData()
    struct PHASEData {
        let idle: Double = 0
        let held: Double = 1
        let spinning: Double = 2
        let landed: Double = 3
        let reveal: Double = 4
        let collecting: Double = 5
    }
    static let PHASE = PHASEData()
    struct POINTERData {
        let angle: Double = 3.141592653589793
        let pivot: Double = 1.12
        let length: Double = 0.25
        let halfBase: Double = 0.095
    }
    static let POINTER = POINTERData()
    static let PRIZE_RADIUS: Double = 0.575
    static let PRIZE_SWAY: [[Double]] = [
        [1, 0.25], [0.7, -0.6], [0.85, 0.5], [1.05, -0.2], [0.6, 0.75], [0.9, -0.45],
    ]
    static let PRIZE_TILE: Double = 0.46
    static let SEGMENT_ANGLE: Double = 1.0471975511965976
    static let SKY_STARS: [[Double]] = [
        [-143, -217, 44, -0.3, 0], [111, -217, 35, 0.23, 0], [-48, -244, 16, 0.28, 1], [32, -207, 19, -0.2, 1],
        [-170, -129, 23, 0.38, 0], [174, -113, 16, -0.3, 1], [183, -42, 13, 0.32, 0], [179, 102, 13, -0.3, 1],
        [-175, 139, 15, 0.1, 1], [-147, 225, 40, -0.25, 1], [143, 244, 32, 0.2, 0], [-126, 300, 16, 0.3, 1],
        [165, 188, 17, -0.14, 0], [-68, 183, 15, 0.2, 1],
    ]
    static let SKY_DOTS: [[Double]] = [
        [-113, -243, 13], [-64, -205, 7], [147, -255, 11], [61, -247, 6], [5, -229, 10], [-169, -178, 7],
        [160, -171, 9], [-187, -79, 10], [191, 26, 6], [-187, 69, 6], [-181, 197, 8], [-106, 211, 11], [176, 288, 7],
        [88, 301, 8], [-30, 301, 5], [65, 179, 9], [159, 140, 7], [-162, 270, 5],
    ]
    static func wheelLayout(_ width: Double, _ height: Double, _ safeTop: Double, _ safeBottom: Double) -> Layout {
        let k = min(width / 402.0, height / 874.0)
        let fontScale = max(k, 0.82)
        let exact = width == 402.0 && height == 874.0 && safeTop == 62.0 && safeBottom == 34.0
        let cx = width / 2.0
        let cy = (exact ? 424.0 : safeTop + (height - safeTop - safeBottom) * 0.465)
        let radius = 164.0 * k
        let buttonHeight = (height < 700.0 ? 48.0 : 56.0)
        let buttonY = height - safeBottom - 24.0 - buttonHeight / 2.0
        let buttonTop = buttonY - buttonHeight / 2.0
        let captionHeight = 88.0 * fontScale + 4.0
        let captionTop = (exact ? 628.0 : (cy + radius + buttonTop - captionHeight) / 2.0)
        let backdropScale = max(width / 804.0, height / 1748.0, cy / 848.0, (height - cy) / 900.0)
        return Layout(
            width: width, height: height, k: k, fontScale: fontScale, cx: cx, cy: cy, radius: radius,
            titleTop: safeTop + 16.0 * k, titleSize: 34.0 * fontScale, titleLine: 40.0 * fontScale,
            subtitleTop: safeTop + 16.0 * k + 48.0 * fontScale, bodySize: 17.0 * fontScale, bodyLine: 24.0 * fontScale,
            pointerX: cx - POINTER.pivot * radius, pointerY: cy, heroX: cx, heroY: cy - 20.0 * k, heroSize: 216.0 * k,
            captionTop: captionTop, numeralSize: 56.0 * fontScale, numeralLine: 64.0 * fontScale,
            labelTop: captionTop + 64.0 * fontScale + 4.0, captionY: captionTop + 32.0 * fontScale, buttonX: cx,
            buttonY: buttonY, buttonTop: buttonTop, buttonHeight: buttonHeight, buttonWidth: width - 48.0,
            hintWidth: 196.0 * max(k, 0.9), hintHeight: 48.0,
            backdrop: Backdrop(
                x: (width - 804.0 * backdropScale) / 2.0, y: cy - 848.0 * backdropScale, width: 804.0 * backdropScale,
                height: 1748.0 * backdropScale))
    }
    static func clamp01(_ v: Double) -> Double {
        return max(0.0, min(1.0, v))
    }
    static func blend(_ a: Double, _ b: Double, _ t: Double) -> Double {
        return a + (b - a) * t
    }
    static func printedPrizeOpacity(_ phase: Double, _ squash: Double, _ printed: Double) -> Double {
        return (phase == PHASE.reveal && squash < -0.00001 ? 0.0 : printed)
    }
    static func prizePose(_ l: Layout, _ v: PoseInput) -> PrizePose {
        let index = max(0.0, min(5.0, v.winner))
        let h = (v.reduced ? 1.0 : v.hero)
        let hc = clamp01(h)
        let a = index * SEGMENT_ANGLE + v.theta
        let sway = v.swayA * PRIZE_SWAY[Int(index)][0] + v.swayB * PRIZE_SWAY[Int(index)][1]
        let tile = PRIZE_TILE * l.radius * (1.0 + 0.07 * v.lift) * v.wheelScale
        let orbit = PRIZE_RADIUS * l.radius * (1.0 + 0.07 * v.lift) * v.wheelScale
        let fx = cos(v.tiltX + 0.008 * sin(v.theta))
        let fy = cos(v.tiltY + 0.008 * cos(v.theta))
        let startX = l.cx + (cos(a) * orbit - sin(sway) * tile / 2.0) * fx
        let startY = l.cy + (sin(a) * orbit + (cos(sway) - 1.0) * tile / 2.0) * fy
        let floating = (v.reduced ? 0.0 : sin(v.time * Double.pi * 2.0 / 2.4))
        let x = blend(startX, l.heroX + v.heroX, h)
        let y = blend(startY, l.heroY + v.heroY + floating * 4.0 * l.k, h) - 42.0 * l.k * sin(Double.pi * hc)
        let side = tile * pow(l.heroSize / tile, h)
        let q = (v.reduced ? 0.0 : v.heroSquash)
        let take = (v.reduced ? 0.0 : v.collect)
        let angle =
            blend(sway, v.heroTilt + floating * Double.pi / 120.0, hc)
            + ((v.reduced ? 0.0 : 0.11 * sin(hc * Double.pi))) - take * 0.35
        return PrizePose(
            x: blend(x, l.buttonX, take) + sin(take * Double.pi) * 42.0 * l.k,
            y: blend(y, l.buttonY, take) - sin(take * Double.pi) * 38.0 * l.k, side: side * pow(0.18, take),
            angle: angle, sx: (1.0 - q * 0.18) * blend(fx, 1.0, hc), sy: (1.0 + q * 0.22) * blend(fy, 1.0, hc),
            baseShift: -side * q * 0.11,
            opacity: (v.reduced ? clamp01(v.hero) * (1.0 - v.collect) : 1.0 - clamp01((take - 0.75) * 4.0)),
            startX: startX, startY: startY, tile: tile, sway: sway, fx: fx, fy: fy)
    }
    static func badgePose(_ l: Layout, _ v: PoseInput, _ progress: Double) -> BadgePose {
        let p = prizePose(
            l,
            PoseInput(
                winner: v.winner, theta: v.theta, lift: v.lift, wheelScale: v.wheelScale, swayA: v.swayA,
                swayB: v.swayB, tiltX: v.tiltX, tiltY: v.tiltY, hero: 0.0, heroSquash: 0.0, heroX: v.heroX,
                heroY: v.heroY, heroTilt: v.heroTilt, collect: 0.0, time: v.time, reduced: false))
        let dx = (ASSETS.prizes.badge.x - 0.5) * p.tile
        let dy = (ASSETS.prizes.badge.y - 0.5) * p.tile
        let x = p.startX + (dx * cos(p.sway) - dy * sin(p.sway)) * p.fx
        let y = p.startY + (dx * sin(p.sway) + dy * cos(p.sway)) * p.fy
        let t = clamp01(progress)
        let open = clamp01((t - 0.67) / 0.33)
        return BadgePose(
            x: blend(x, l.cx, t), y: blend(y, l.captionY, t) - 24.0 * l.k * sin(t * Double.pi),
            size: blend(p.tile * ASSETS.prizes.badge.size / 0.92, 76.0 * l.fontScale, t) * (1.0 + open * 0.38),
            angle: p.sway * (1.0 - t), sx: blend(p.fx, 1.0, t), sy: blend(p.fy, 1.0, t), opacity: 1.0 - open)
    }
    static func skyPose(
        _ l: Layout, _ i: Double, _ time: Double, _ swirl: Double, _ burst: Double, _ tiltX: Double, _ tiltY: Double,
        _ reduced: Bool
    ) -> SkyPose {
        let star = i < Double(SKY_STARS.count)
        let s = (star ? SKY_STARS[Int(i)] : SKY_DOTS[Int(i - Double(SKY_STARS.count))])
        let dx = s[0] * l.k
        let dy = s[1] * l.k
        let r = sqrt(dx * dx + dy * dy)
        let falloff = min(1.0, l.radius * l.radius / (r * r))
        let turn = (reduced ? 0.0 : max(-0.35, min(0.35, swirl * 0.35)) * falloff)
        let kick = (reduced || burst < 0.0 ? 0.0 : 14.0 * l.k * exp(-3.5 * burst) * sin(burst * 13.0) * falloff)
        let t = (reduced ? 0.0 : time)
        var x = l.cx + (dx * cos(turn) - dy * sin(turn)) * (1.0 + kick / r)
        var y = l.cy + (dx * sin(turn) + dy * cos(turn)) * (1.0 + kick / r)
        x += (reduced ? 0.0 : tiltX * 28.0 * falloff)
        y += (reduced ? 0.0 : sin(t * (0.45 + i * 0.018) + i) * 2.0 * l.k + tiltY * 28.0 * falloff)
        let titleBottom = l.subtitleTop + l.bodyLine + 12.0
        let half = s[2] * l.k / 2.0
        let clearTop = clamp01((y - half - titleBottom) / (12.0 * l.k))
        let clearBottom = clamp01((l.buttonTop - 16.0 - y - half) / (12.0 * l.k))
        let inCaption = y + half > l.captionTop - 8.0 && y - half < l.labelTop + l.bodyLine + 8.0
        let clearCaption = (inCaption && abs(x - l.cx) < 116.0 * l.fontScale + half ? 0.0 : 1.0)
        return SkyPose(
            x: x, y: y, size: s[2] * l.k,
            angle: ((star ? SKY_STARS[Int(i)][3] : i)) + ((reduced ? 0.0 : 0.06 * sin(t * 0.4 + i))),
            tile: (star
                ? SKY_STARS[Int(i)][4] : (((i).truncatingRemainder(dividingBy: 4.0) == 0.0 ? 2.0 : 3.0))),
            opacity: clearTop * clearBottom * clearCaption * ((star ? 0.72 : 0.3 + 0.18 * sin(t * 1.3 + i))))
    }
    static func particlePose(_ l: Layout, _ i: Double, _ age: Double, _ power: Double) -> ParticlePose {
        let wave = i >= 64.0
        let n = (i).truncatingRemainder(dividingBy: 64.0)
        let seed = sin(i * 127.1 + 19.7) * 43758.5453
        let random = seed - floor(seed)
        let near = (n).truncatingRemainder(dividingBy: 3.0) != 0.0
        let t = age - ((wave ? 0.22 : 0.0)) - random * 0.025
        let life = 1.2 + random
        let active =
            age >= 0.0 && t > 0.0 && t < life && n < floor(64.0 * power) && (!wave || (power >= 0.99 && n < 24.0))
        let a = n * 2.399963 + ((wave ? 0.7 : 0.0))
        let speed = (210.0 + random * 350.0) * ((wave ? 0.7 : 1.0)) * power
        let travel = speed * (1.0 - exp(-2.1 * max(0.0, t))) / 2.1
        let depth = (near ? 1.0 + max(0.0, t) * (0.6 + random * 2.2) : 0.7)
        let progress = max(0.0, t) / life
        let x = l.heroX + cos(a) * travel * l.k * depth
        let y = l.heroY + ((sin(a) - 0.25) * travel * depth + 90.0 * t * t) * l.k
        let size = (8.0 + random * 17.0) * l.k * ((near ? depth * depth : 0.65))
        let half = size * 0.4
        let topClear = clamp01((y - half - l.subtitleTop - l.bodyLine - 12.0) / (24.0 * l.k))
        let bottomClear = clamp01((l.buttonTop - 12.0 - y - half) / (24.0 * l.k))
        let captionDistance = max(
            l.captionTop - 8.0 - y - half, y - half - l.labelTop - l.bodyLine - 8.0,
            abs(x - l.cx) - 116.0 * l.fontScale - half)
        let copyClear = topClear * bottomClear * clamp01(captionDistance / (16.0 * l.k))
        return ParticlePose(
            near: near, x: x, y: y, size: size, angle: a + t * (random - 0.5) * 8.0,
            tile: ((n).truncatingRemainder(dividingBy: 7.0) < 4.0
                ? (n).truncatingRemainder(dividingBy: 2.0)
                : ((n).truncatingRemainder(dividingBy: 7.0) == 4.0 ? 2.0 : 3.0)),
            opacity: (active
                ? copyClear * ((near ? 0.92 : 0.43)) * clamp01(t / 0.045) * pow(1.0 - clamp01(progress), 1.2) : 0.0))
    }
    static func buttonPose(_ l: Layout, _ button: Double, _ press: Double, _ nudge: Double) -> ButtonPose {
        let t = clamp01(button)
        return ButtonPose(
            width: blend(l.hintWidth, l.buttonWidth, t), height: blend(l.hintHeight, l.buttonHeight, t),
            scale: (press >= 0.0 ? 1.0 - press * 0.03 : 1.0 - press * 0.04), x: 6.0 * nudge)
    }
}
