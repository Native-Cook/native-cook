import SwiftUI
import UIKit

/*
 Boba Pearl Progress — "Pearl Lane", an order brewing. A clear cup fills as the order
 progresses: pearls drop in and settle, milk tea pours, the cup is shaken, sealed and pierced by a
 straw; the percentage and a four-step tracker follow.

 Touch: drag the cup sideways to tip it (±12°, rubber band past it): the cup follows on a spring
 (ζ 0.75), the tea's surface stays level and sloshes behind (ζ 0.22), the pearls roll to the low
 side. "Shake it" rocks the cup for 1.4 s. Meanwhile the order keeps brewing. 2 s after the last
 touch the cup is swapped for an empty one (0.9 s) and the loop starts again. Reduced motion: the
 finished cup at 100 %, still; tipping follows the finger without slosh.

 Everything moving is one Canvas per frame (the pearls are one pre-rendered sprite drawn 40 times,
 the shadows pre-rendered images); the backdrop is a Canvas drawn once.
 */
struct BobaPearlProgress: View {
    /// Brews an order from empty cup to "Ready", then swaps in the next cup, on a 10 s loop.
    var autoPlay = false

    static let loop = BobaPearlProgressMotion.loop

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.displayScale) private var displayScale
    @State private var engine = BobaPearlProgressEngine()
    /// Keeps the timeline running outside autoPlay while something moves (the order brews at first).
    @State private var awake = true
    @State private var dragging = false

    private typealias Art = BobaPearlProgressArt

    var body: some View {
        // The block draws under the system bars and keeps its content clear of them itself.
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            stage(size: size, top: insets.top, bottom: insets.bottom)
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                .offset(x: -insets.leading, y: -insets.top)
        }
    }

    private func stage(size: CGSize, top: Double, bottom: Double) -> some View {
        let g = BobaPearlProgressGeo(w: size.width, h: size.height, top: top, bottom: bottom)
        let auto = autoPlay && !reduceMotion
        let reduced = reduceMotion
        let running = auto || awake
        let sprites = Art.sprites(g, scale: min(3, max(1, displayScale)))
        let pearls = engine.pearls(for: g)
        return ZStack(alignment: .topLeading) {
            Canvas { c, _ in
                Art.drawBackdrop(c, g)
            }
            .frame(width: size.width, height: size.height)

            TimelineView(.animation(paused: !running)) { timeline in
                let n = timeline.date.timeIntervalSinceReferenceDate
                let frame = engine.step(n, g: g, auto: auto, reduced: reduced)
                Canvas { c, _ in
                    for cup in frame.scene.cups {
                        Art.drawCup(c, g, cup, pearls, sprites, clock: frame.scene.clock)
                    }
                    Art.drawPanel(c, g, frame.scene)
                }
                .frame(width: size.width, height: size.height)
                .onChange(of: frame.idle, initial: true) { _, idle in
                    if idle && !auto { awake = false }
                }
            }
            .allowsHitTesting(false)

            // Drag the cup sideways to tip it.
            Color.clear
                .contentShape(Rectangle())
                .frame(width: g.rimW * 2 + 80, height: g.cupH + 70)
                .padding(.leading, g.cx - g.rimW - 40)
                .padding(.top, g.top - 40)
                .gesture(
                    DragGesture(minimumDistance: 2)
                        .onChanged { v in
                            awake = true
                            if !dragging {
                                dragging = true
                                engine.tipBegan()
                            }
                            engine.tip(v.translation.width, reduced: reduced)
                        }
                        .onEnded { _ in
                            dragging = false
                            engine.tipEnded(auto: auto, reduced: reduced)
                        }
                )
                .accessibilityElement()
                .accessibilityLabel("Your drink. Drag sideways to tip the cup.")

            VStack(alignment: .leading, spacing: 4) {
                Text("PEARL LANE")
                    .font(.system(size: 12, weight: .bold))
                    .kerning(2.4)
                    .foregroundStyle(Art.rgb(0xA06A48))
                Text("Order #318")
                    .font(.system(size: 30, weight: .bold).monospacedDigit())
                    .kerning(-0.5)
                    .foregroundStyle(Art.ink)
                Text("Brown Sugar Milk Tea · Large · 50% sweet")
                    .font(.system(size: 15, weight: .regular))
                    .foregroundStyle(Art.inkSoft)
            }
            .frame(width: max(0, size.width - 48), alignment: .leading)
            .padding(.leading, 24)
            .padding(.top, top + 8)
            .allowsHitTesting(false)

            Button {
                awake = true
                engine.shake(auto: auto, reduced: reduced)
            } label: {
                Text("Shake it")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(Art.rgb(0xFBEBDD))
                    .frame(width: max(0, size.width - 48), height: 52)
            }
            .buttonStyle(BobaPearlProgressButtonStyle())
            .padding(.leading, 24)
            .padding(.top, g.buttonY)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .clipped()
    }
}

/// The dark pill: two shadows, a faint top inner highlight, a 0.97 press.
private struct BobaPearlProgressButtonStyle: ButtonStyle {
    private typealias Art = BobaPearlProgressArt

    func makeBody(configuration: Configuration) -> some View {
        let shape = RoundedRectangle(cornerRadius: 26, style: .continuous)
        configuration.label
            .background {
                shape
                    .fill(Art.ink)
                    .overlay {
                        // inset 0 1px 0 rgba(255,255,255,0.12)
                        shape
                            .inset(by: 0.5)
                            .stroke(
                                LinearGradient(
                                    stops: [
                                        .init(color: Color.white.opacity(0.12), location: 0),
                                        .init(color: Color.white.opacity(0), location: 0.06),
                                    ],
                                    startPoint: .top,
                                    endPoint: .bottom
                                ),
                                lineWidth: 1
                            )
                    }
                    .shadow(color: Art.rgb(0x3B2418, 0.2), radius: 12, x: 0, y: 10)
                    .shadow(color: Art.rgb(0x3B2418, 0.3), radius: 1, x: 0, y: 1)
            }
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .animation(
                configuration.isPressed ? .easeInOut(duration: 0.11) : .spring(duration: 0.35, bounce: 0.3),
                value: configuration.isPressed
            )
    }
}

struct BobaPearlProgressFrame {
    var scene: BobaPearlProgressScene
    var idle: Bool
}

/// The clock and the hand-driven cup, stepped once a frame like the RN frame callback.
@MainActor
final class BobaPearlProgressEngine {
    private typealias M = BobaPearlProgressMotion
    /// Tip per point of drag (radians) and the limit (12°).
    private static let tipGain = 0.0028
    private static let tipLimit = 12 * Double.pi / 180
    private static let cupK = 260.0
    private static let cupC = 2 * 0.75 * sqrt(cupK)
    private static let sloshK = 90.0
    private static let sloshC = 2 * 0.22 * sqrt(sloshK)
    private static let rollK = 70.0
    private static let rollC = 2 * 0.45 * sqrt(rollK)
    private static let swap = 0.9
    private static let readyTc = 8.2
    private static let never = -1e9

    private var prev: Double?
    private var now = 0.0
    private var start: Double?
    private var origin = 0.0
    private var resumeAt: Double?
    private var configured = false
    // The hand-driven cup.
    private var tc = 0.0
    private var rot = 0.0
    private var rotV = 0.0
    private var rotTarget = 0.0
    private var tilt = 0.0
    private var tiltV = 0.0
    private var roll = 0.0
    private var rollV = 0.0
    private var shakeAt = BobaPearlProgressEngine.never
    private var swapAt = BobaPearlProgressEngine.never
    private var swapTc = 0.0
    /// Step shown, the one before, and when it changed.
    private var stepChange: (Int, Int, Double) = (0, 0, BobaPearlProgressEngine.never)
    private var pastLimit = false
    private var reduced = false

    private var pearlCache: (BobaPearlProgressGeo, [BobaPearlProgressMotion.Pearl])?

    private let tick = UISelectionFeedbackGenerator()
    private let light = UIImpactFeedbackGenerator(style: .light)

    private var clock: Double { Date().timeIntervalSinceReferenceDate }

    func pearls(for g: BobaPearlProgressGeo) -> [BobaPearlProgressMotion.Pearl] {
        if let pearlCache, pearlCache.0 == g { return pearlCache.1 }
        let p = M.packPearls(g)
        pearlCache = (g, p)
        return p
    }

    private static func rubber(_ x: Double, _ limit: Double) -> Double {
        let a = abs(x)
        if a <= limit { return x }
        let over = a - limit
        return (x < 0 ? -1 : 1) * (limit + (over * 0.55) / (1 + over / 0.25))
    }

    func step(_ n: Double, g: BobaPearlProgressGeo, auto: Bool, reduced: Bool) -> BobaPearlProgressFrame {
        self.reduced = reduced
        if !configured {
            configured = true
            if reduced {
                tc = Self.readyTc
                stepChange = (3, 3, Self.never)
            }
        }
        let first = prev == nil
        if first {
            origin = n
            if auto { start = n }
        }
        let dt = first ? 0 : min(max(0, n - (prev ?? n)), 1.0 / 30)
        prev = n
        now = n
        let running = start.map { n >= $0 } ?? false
        if !reduced && !running {
            // The order keeps brewing under the finger; the cup, its tea and pearls are springs.
            tc = min(Self.readyTc, tc + dt)
            let total = rot + M.shakeRot(n - shakeAt)
            let a = Self.cupK * (rotTarget - rot) - Self.cupC * rotV
            rotV += a * dt
            rot += rotV * dt
            let ts = Self.sloshK * (-total - tilt) - Self.sloshC * tiltV
            tiltV += ts * dt
            tilt += tiltV * dt
            let rs = Self.rollK * (total * 70 - roll) - Self.rollC * rollV
            rollV += rs * dt
            roll += rollV * dt
            let s = M.stepOf(tc)
            if s != stepChange.0 { stepChange = (s, stepChange.0, n) }
            if let r = resumeAt, n >= r {
                resumeAt = nil
                rotTarget = 0
                if tc > 1.4 {
                    swapAt = n
                    swapTc = tc
                    start = n + Self.swap + 0.05
                } else {
                    start = n + 0.6
                }
            }
        }

        let ambient = reduced ? 0 : (n - origin).truncatingRemainder(dividingBy: M.loop)
        if let st = start, n >= st {
            return BobaPearlProgressFrame(scene: M.loopScene(g, (n - st).truncatingRemainder(dividingBy: M.loop), ambient: ambient), idle: false)
        }
        let sh = n - shakeAt
        var user = BobaPearlProgressCup(
            dx: 0,
            rot: rot + M.shakeRot(sh),
            tc: tc,
            tilt: reduced ? 0 : tilt,
            roll: reduced ? 0 : roll,
            shake: M.shakeEnv(sh),
            phase: sh
        )
        var scene = BobaPearlProgressScene(
            cups: [user],
            percent: M.percent(user.tc),
            step: stepChange.0,
            prevStep: stepChange.1,
            stepFade: min(1, (n - stepChange.2) / 0.3),
            pct: (0, 0, 1),
            clock: ambient
        )
        let sw = n - swapAt
        let swapping = sw >= 0 && sw <= Self.swap + 0.1
        if swapping {
            // The finished cup leaves, an empty one arrives.
            let s = sw / Self.swap
            user.dx = -g.w * 0.8 * M.easeInOutCubic(M.seg(s, 0, 0.55))
            user.tc = swapTc
            let fresh = BobaPearlProgressCup(dx: g.w * 0.8 * (1 - M.easeOutBack(M.seg(s, 0.35, 1))), rot: 0, tc: 0, tilt: 0, roll: 0, shake: 0, phase: 0)
            scene.cups = [user, fresh]
            scene.percent = s > 0.5 ? 0 : M.percent(swapTc)
            scene.pct = (Int(floor(M.percent(swapTc))), 0, M.easeInOutCubic(M.seg(s, 0.4, 0.6)))
            scene.step = s > 0.45 ? 0 : M.stepOf(swapTc)
            scene.prevStep = M.stepOf(swapTc)
            scene.stepFade = s > 0.45 ? min(1, (s - 0.45) / 0.33) : 1
        }
        let total = rot + M.shakeRot(sh)
        let settled = reduced || (
            abs(rotTarget - rot) < 1e-4 && abs(rotV) < 1e-4
                && abs(-total - tilt) < 1e-4 && abs(tiltV) < 1e-4
                && abs(total * 70 - roll) < 1e-3 && abs(rollV) < 1e-3
        )
        let idle = settled && tc >= Self.readyTc && sh >= 1.4 && !swapping && n - stepChange.2 >= 0.3
            && resumeAt == nil && start == nil && rotTarget == 0
        return BobaPearlProgressFrame(scene: scene, idle: idle)
    }

    private func takeOver() {
        let n = clock
        if let st = start, n >= st {
            let t = (n - st).truncatingRemainder(dividingBy: M.loop)
            tc = t >= 8.9 ? 0 : min(t, Self.readyTc)
            let s = t - 5.0
            let r = M.shakeRot(s)
            // The loop's cup at t (the one in the frame's first slot).
            rot = t < 9.0 ? r : 0
            tilt = t < 9.0 ? -r + 1.5 * M.shakeRot(s - 0.07) : 0
            rotV = 0
            tiltV = 0
            roll = 0
            rollV = 0
            shakeAt = t > 5.0 && t < 6.4 ? n - (t - 5.0) : Self.never
            stepChange = (M.stepOf(tc), M.stepOf(tc), Self.never)
        }
        swapAt = Self.never
        start = nil
        resumeAt = nil
    }

    func tipBegan() {
        takeOver()
        pastLimit = false
        tick.prepare()
    }

    func tip(_ dx: Double, reduced: Bool) {
        let raw = dx * Self.tipGain
        rotTarget = Self.rubber(raw, Self.tipLimit)
        if reduced { rot = rotTarget }
        let over = abs(raw) > Self.tipLimit
        if over != pastLimit {
            pastLimit = over
            if over { tick.selectionChanged() }
        }
    }

    func tipEnded(auto: Bool, reduced: Bool) {
        rotTarget = 0
        if reduced { rot = 0 }
        if auto { resumeAt = clock + 2 }
    }

    func shake(auto: Bool, reduced: Bool) {
        light.impactOccurred()
        if reduced { return }
        takeOver()
        shakeAt = clock
        if auto { resumeAt = clock + 2 }
    }
}
