import SwiftUI

enum StarfieldPaymentSuccessSpaceMotion {
    static let loop = 11.0, firstKeyAt = 0.8, keyEvery = 0.35, keyFor = 0.12
    static let pressAt = 2.6, pressFor = 0.15, leaveAfter = 3.6, userHold = 3.0
    static let drop = 0.28, dissolveAt = 0.06, dissolveFor = 0.45, starsAt = 0.4, starsIn = 0.6
    static let textAt = 0.5, textIn = 0.35, waitOut = 0.3, checkAfter = 0.35
    static let barAfter = 0.55, barIn = 0.45, resultAfter = 1.45, resultIn = 0.35, returnTime = 0.6, markY = 0.36
}
enum StarfieldPaymentSuccessMath {
    static func clamp01(_ value: Double) -> Double { min(1, max(0, value)) }
    static func smooth(_ value: Double, _ from: Double, _ to: Double) -> Double {
        let t = clamp01((value - from) / (to - from))
        return t * t * (3 - 2 * t)
    }
    static func backOut(_ value: Double) -> Double {
        let t = clamp01(value) - 1
        return 1 + 2.70158 * t * t * t + 1.70158 * t * t
    }
}
private enum StarfieldPaymentSuccessSpaceSpec {
    static let glowAt = 0.75, glowIn = 0.4, planeAt = 1.0, planeIn = 0.25
    static let checkIn = 0.5, ringFor = 0.8, swellAfter = 0.85, swellFor = 0.3
    static let turnAfter = swellAfter + swellFor, turnEase = 0.3, spinPeriod = 1.3
    static let planeY = 0.398, starCount = 240, seed: UInt32 = 20261012
    static let starSpeed = 0.05, starRush = 0.3, near = 0.18, starHalo = 2.2, reach = 0.5
    static let glow = 27.0, glowReach = 11.0, check = 87.0, swell = 1.3, coin = 112.0, coinEdge = 14.0, coinTilt = -0.14
    static let radius = coin / 2, planeScale = 1.45, dollarScale = 2.5
    static let lobes = [[0.4, 2.3, 0.0, 1.05], [2.0, 1.7, 1.9, 0.86], [3.5, 2.9, 3.4, 1.0], [5.2, 2.1, 5.0, 0.8]]
    static let ring = Color(hex: 0xE5432A), green = [30.0, 224.0, 122.0], greenDark = [18.0, 150.0, 79.0]
    static let planeInk = Color(hex: 0x1B1206), checkInk = Color(hex: 0x06140C)
    static let bloom = Color.rgba(255, 78, 10, 0.9), yellow = Color(hex: 0xFFC61C), core = Color(hex: 0xFFF4AE)
    static let coinGlow = Color.rgba(255, 178, 58, 0.14), shade = Color(hex: 0x1A0E00)
    static let edge = [0x6B4A12, 0xF6DC92, 0x8A6119, 0x2E1F06, 0x4A3209, 0xC9A046, 0x3A2707].map { Color(hex: UInt32($0)) }
    static let edgeAt = [0.0, 0.12, 0.26, 0.5, 0.7, 0.86, 1.0]
    static let face = [0xFCEFC9, 0xE8C57F, 0xB98A35].map { Color(hex: UInt32($0)) }
    static let rim = [0xC7963C, 0xF3D894, 0xFBEABA].map { Color(hex: UInt32($0)) }
    static let back = [0xF1D596, 0xD2A551].map { Color(hex: UInt32($0)) }
    static let groove = Color.rgba(150, 104, 30, 0.75), innerRim = Color.rgba(255, 246, 216, 0.7)
    static let dollarShadow = Color(hex: 0x8F6420), dollarLight = Color(hex: 0xFFF7DC), dollar = Color(hex: 0xD9B266)
    static let sheen = [Color.rgba(255, 255, 255, 0), Color.rgba(255, 252, 235, 0.6), Color.rgba(255, 255, 255, 0)]
}
private struct StarfieldPaymentSuccessStar {
    let x: Double, y: Double, depth: Double, bright: Double
}
private enum StarfieldPaymentSuccessSpaceArt {
    static let stars: [StarfieldPaymentSuccessStar] = {
        var seed = StarfieldPaymentSuccessSpaceSpec.seed
        func next() -> Double { seed = seed &* 1664525 &+ 1013904223; return Double(seed) / 4294967296 }
        var stars: [StarfieldPaymentSuccessStar] = []
        while stars.count < StarfieldPaymentSuccessSpaceSpec.starCount {
            let x = next() * 2 - 1, y = next() * 2 - 1
            let star = StarfieldPaymentSuccessStar(x: x, y: y, depth: next(), bright: 0.55 + 0.45 * next())
            if hypot(x, y) > 0.08 { stars.append(star) }
        }
        return stars
    }()
    static let plane: Path = {
        var p = Path(); p.move(to: CGPoint(x: 22, y: 2)); p.addLine(to: CGPoint(x: 14.6, y: 21.6)); p.addLine(to: CGPoint(x: 11.2, y: 12.8)); p.addLine(to: CGPoint(x: 2.4, y: 9.4)); p.closeSubpath(); return p
    }()
    static let streaks: Path = {
        var p = Path()
        for line in [[2.2, 17.4, 6.4, 13.2], [4.8, 21, 9.6, 16.2], [9, 22.4, 11.8, 19.6]] { p.move(to: CGPoint(x: line[0], y: line[1])); p.addLine(to: CGPoint(x: line[2], y: line[3])) }
        return p
    }()
    static let tick: Path = {
        var p = Path(); p.move(to: CGPoint(x: -15.2, y: 1)); p.addLine(to: CGPoint(x: -5.7, y: 10.5)); p.addLine(to: CGPoint(x: 15.2, y: -10.5)); return p
    }()
    static let dollar: Path = {
        var p = Path(); p.move(to: CGPoint(x: 12, y: 1)); p.addLine(to: CGPoint(x: 12, y: 23))
        p.move(to: CGPoint(x: 17, y: 5)); p.addLine(to: CGPoint(x: 9.5, y: 5))
        p.addArc(center: CGPoint(x: 9.5, y: 8.5), radius: 3.5, startAngle: .degrees(-90), endAngle: .degrees(90), clockwise: true)
        p.addLine(to: CGPoint(x: 14.5, y: 12))
        p.addArc(center: CGPoint(x: 14.5, y: 15.5), radius: 3.5, startAngle: .degrees(-90), endAngle: .degrees(90), clockwise: false)
        p.addLine(to: CGPoint(x: 6, y: 19)); return p
    }()
}

/// The same seeded field, moving light and projected coin share the payment clock.
struct StarfieldPaymentSuccessSpace: View {
    let elapsed: Double, duration: Double
    let still: Bool
    let width: Double, height: Double
    private typealias S = StarfieldPaymentSuccessSpaceSpec
    private typealias M = StarfieldPaymentSuccessSpaceMotion
    private typealias F = StarfieldPaymentSuccessMath
    private typealias A = StarfieldPaymentSuccessSpaceArt

    var body: some View {
        Canvas { context, _ in
            guard width > 0, height > 0, width.isFinite, height.isFinite else { return }
            let check = duration + M.checkAfter, turn = check + S.turnAfter
            stars(context, check: check)
            glow(context)
            rings(context, check: check)
            let angle = still ? 0 : turned(elapsed - turn)
            let swap = still ? F.smooth(elapsed, turn, turn + 0.3) : angle >= .pi / 2 ? 1.0 : 0.0
            disc(context, check: check, turn: turn, angle: angle, swap: swap)
            if swap > 0 { coin(context, angle: still ? 0 : angle - .pi, opacity: swap) }
        }.frame(width: max(0, width), height: max(0, height)).clipped().allowsHitTesting(false).accessibilityHidden(true)
    }
    private func circle(_ radius: Double, x: Double = 0, y: Double = 0) -> Path { Path(ellipseIn: CGRect(x: x - radius, y: y - radius, width: radius * 2, height: radius * 2)) }
    private func turned(_ time: Double) -> Double {
        let speed = 2 * Double.pi / S.spinPeriod
        if time <= 0 { return 0 }
        return time < S.turnEase ? speed * time * time / (2 * S.turnEase) : speed * (time - S.turnEase / 2)
    }
    private func blurred(_ context: GraphicsContext, radius: Double, color: Color, sigma: Double, x: Double = 0, y: Double = 0, opacity: Double = 1, stroke: Double? = nil) {
        var glow = context
        glow.opacity *= opacity; glow.addFilter(.blur(radius: sigma))
        if let stroke { glow.stroke(circle(radius, x: x, y: y), with: .color(color), lineWidth: stroke) }
        else { glow.fill(circle(radius, x: x, y: y), with: .color(color)) }
    }
    private func gradient(_ colors: [Color], start: CGPoint, end: CGPoint, stops: [Double]? = nil) -> GraphicsContext.Shading {
        let gradient = stops.map { positions in Gradient(stops: zip(colors, positions).map { Gradient.Stop(color: $0.0, location: $0.1) }) } ?? Gradient(colors: colors)
        return .linearGradient(gradient, startPoint: start, endPoint: end)
    }
    private func stars(_ context: GraphicsContext, check: Double) {
        let shown = F.smooth(elapsed, M.starsAt, M.starsAt + M.starsIn)
        guard shown > 0 else { return }
        let travel = still ? 0 : S.starSpeed * elapsed + S.starRush * F.smooth(elapsed, check - 0.1, check + 0.9)
        let reach = S.reach * height
        for star in A.stars {
            var z = (star.depth - travel).truncatingRemainder(dividingBy: 1)
            if z <= 0 { z += 1 }
            let scale = 1 / (S.near + (1 - S.near) * z)
            let x = width / 2 + star.x * reach * scale, y = height * M.markY + star.y * reach * scale
            if x < -6 || x > width + 6 || y < -6 || y > height + 6 { continue }
            let near = 1 - z, radius = 0.65 + 1.7 * near * near * near
            let alpha = shown * star.bright * (0.5 + 0.5 * near) * F.smooth(z, 1, 0.9) * F.smooth(z, 0, 0.06)
            if radius > 1.2 { blurred(context, radius: radius * 1.8, color: .white, sigma: S.starHalo, x: x, y: y, opacity: alpha * 0.5) }
            var point = context; point.opacity *= alpha
            point.fill(circle(radius, x: x, y: y), with: .color(.white))
        }
    }
    private func glow(_ context: GraphicsContext) {
        let grown = 0.5 + 0.5 * F.smooth(elapsed, S.glowAt, S.glowAt + S.glowIn)
        let opacity = F.smooth(elapsed, S.glowAt, S.glowAt + S.glowIn) * (1 - F.smooth(elapsed, duration, duration + M.waitOut))
        guard opacity > 0 else { return }
        let breath = still ? 1 : 1 + 0.05 * sin(elapsed * 3.3), bob = still ? 0 : 2.5 * sin(elapsed * 2.1)
        var light = context; light.opacity *= opacity
        light.translateBy(x: width / 2, y: height * S.planeY + bob); light.scaleBy(x: grown * breath, y: grown * breath)
        let time = still ? 0 : elapsed
        for bloom in [true, false] {
            for lobe in S.lobes {
                let angle = lobe[0] + 0.4 * sin(time * lobe[1] + lobe[2])
                let distance = S.glowReach * (1 + 0.35 * sin(time * lobe[1] * 1.4 + lobe[2] * 2))
                blurred(light, radius: (bloom ? S.glow + 4 : S.glow) * lobe[3], color: bloom ? S.bloom : S.yellow, sigma: bloom ? 7 : 2.5, x: cos(angle) * distance * 1.25, y: sin(angle) * distance)
            }
        }
        blurred(light, radius: 20, color: S.core, sigma: 9)
        var plane = light
        plane.opacity *= F.smooth(elapsed, S.planeAt, S.planeAt + S.planeIn)
        plane.rotate(by: .radians(still ? 0 : 0.05 * sin(elapsed * 4.6)))
        plane.scaleBy(x: S.planeScale, y: S.planeScale); plane.translateBy(x: -12, y: -12)
        plane.fill(A.plane, with: .color(S.planeInk))
        plane.stroke(A.streaks, with: .color(S.planeInk), style: StrokeStyle(lineWidth: 1.3, lineCap: .round))
    }
    private func rings(_ context: GraphicsContext, check: Double) {
        let p = F.clamp01((elapsed - check) / S.ringFor)
        guard !still, p > 0, p < 1 else { return }
        let u = 1 - p, radius = 16 + 0.42 * height * (1 - u * u * u), opacity = 0.6 * (1 - p)
        blurred(context, radius: radius * 2.2, color: S.ring, sigma: 1.5, x: width / 2, y: height * M.markY, opacity: opacity * 0.45, stroke: 1.5)
        blurred(context, radius: radius, color: S.ring, sigma: 1.5, x: width / 2, y: height * M.markY, opacity: opacity, stroke: 1.5)
    }
    private func disc(_ context: GraphicsContext, check: Double, turn: Double, angle: Double, swap: Double) {
        let opacity = F.smooth(elapsed, check, check + 0.2) * (1 - swap)
        guard opacity > 0 else { return }
        let dark = F.smooth(elapsed, check + S.swellAfter, turn)
        let size = (still ? 1 : F.backOut((elapsed - check) / S.checkIn)) * (1 + (S.swell - 1) * dark)
        let mixed = zip(S.green, S.greenDark).map { ($0.0 + ($0.1 - $0.0) * dark).rounded() }
        var disc = context; disc.opacity *= opacity
        disc.translateBy(x: width / 2, y: height * M.markY)
        disc.scaleBy(x: max(0.001, cos(min(angle, .pi / 2))), y: 1); disc.scaleBy(x: size, y: size)
        disc.fill(circle(S.check / 2), with: .color(.rgba(mixed[0], mixed[1], mixed[2], 1)))
        disc.stroke(A.tick, with: .color(S.checkInk), style: StrokeStyle(lineWidth: 5, lineCap: .round, lineJoin: .round))
    }
    private func face(_ context: GraphicsContext, front: Bool) {
        let r = S.radius, start = CGPoint(x: -r, y: -r), end = CGPoint(x: r, y: r)
        context.fill(circle(r), with: gradient(S.face, start: start, end: end))
        context.stroke(circle(r - 2.5), with: gradient(S.rim, start: start, end: end), lineWidth: 5)
        context.stroke(circle(r - 9), with: .color(S.groove), lineWidth: 1.4)
        context.stroke(circle(r - 10.3), with: .color(S.innerRim), lineWidth: 1)
        if front {
            var sign = context; sign.scaleBy(x: S.dollarScale, y: S.dollarScale); sign.translateBy(x: -12, y: -12)
            var shadow = sign; shadow.translateBy(x: 0.45, y: 0.5)
            shadow.stroke(A.dollar, with: .color(S.dollarShadow), style: StrokeStyle(lineWidth: 2.5, lineCap: .round, lineJoin: .round))
            var edge = sign; edge.translateBy(x: -0.35, y: -0.35)
            edge.stroke(A.dollar, with: .color(S.dollarLight), style: StrokeStyle(lineWidth: 2.5, lineCap: .round, lineJoin: .round))
            sign.stroke(A.dollar, with: .color(S.dollar), style: StrokeStyle(lineWidth: 2.4, lineCap: .round, lineJoin: .round))
        } else { context.fill(circle(r - 19), with: gradient(S.back, start: start, end: end)) }
    }
    private func coin(_ context: GraphicsContext, angle: Double, opacity: Double) {
        let r = S.radius, c = cos(angle), sine = sin(angle), scale = max(0.001, abs(c))
        var coin = context; coin.opacity *= opacity
        coin.translateBy(x: width / 2, y: height * M.markY); coin.rotate(by: .radians(S.coinTilt))
        blurred(coin, radius: r * 1.5, color: S.coinGlow, sigma: 30)
        let edge = gradient(S.edge, start: CGPoint(x: 0, y: -r), end: CGPoint(x: 0, y: r), stops: S.edgeAt)
        var behind = coin; behind.translateBy(x: (c >= 0 ? -1 : 1) * (S.coinEdge / 2) * sine, y: 0); behind.scaleBy(x: scale, y: 1)
        behind.fill(circle(r), with: edge)
        let edgeWidth = S.coinEdge * abs(sine)
        coin.fill(Path(CGRect(x: -edgeWidth / 2, y: -r, width: edgeWidth, height: S.coin)), with: edge)
        var front = coin; front.translateBy(x: (c >= 0 ? 1 : -1) * (S.coinEdge / 2) * sine, y: 0); front.scaleBy(x: scale, y: 1)
        front.clip(to: circle(r)); face(front, front: c >= 0)
        var shade = front; shade.opacity *= 0.66 * pow(1 - abs(c), 0.8); shade.fill(circle(r), with: .color(S.shade))
        var sheen = front; sheen.translateBy(x: 1.5 * r * sine, y: 0); sheen.rotate(by: .radians(0.35))
        sheen.fill(Path(CGRect(x: -0.5 * r, y: -1.5 * r, width: r, height: 3 * r)), with: gradient(S.sheen, start: CGPoint(x: -0.5 * r, y: 0), end: CGPoint(x: 0.5 * r, y: 0)))
    }
}

