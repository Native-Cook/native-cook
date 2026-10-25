import SwiftUI
import UIKit

/*
 Spirit Level Slider — step 3 of a roof-estimate form ("How steep is your roof?"). The slider is a
 yellow aluminium spirit level: drag either end up or down to tilt it about its centre and the
 bubble slides along the curved lime vial to the higher end, lagging on a spring, stretched by its
 speed and squashed against the vial's ends. The pitch card above follows: the degrees and the
 rise per 12 roll like an odometer, the pitch word rolls in its pill, and the house's roof tilts to
 the same pitch. Presets under the level spring it to 4/12, 6/12, 9/12 or 12/12.

 One Canvas: a baked background (paper, card, house walls), the roof and readouts drawn per frame,
 and the level as a baked image under its rotation with the bubble and glass on top. The bakes run
 off the main thread. Timeline and bubble physics: SpiritLevelSliderMotion.swift.

 Touch: the level follows the finger (rubber-band past −6° and 48°); on release it springs back
 inside the range (ω 12, ζ 0.8). A selection tick each whole step of rise, a light tap when the
 bubble returns between the centre marks. 2 s after the last touch the level eases back to 0°
 (0.9 s) and the loop restarts. Reduced motion: no loop; the bubble sits where the tilt puts it.
 */
struct SpiritLevelSlider: View {
    /// Tilts the level to 28°, past 40° and down to 18°, then back to level, on an 8 s loop.
    var autoPlay = false

    static let loop = 8.0

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.displayScale) private var displayScale
    @State private var engine = SpiritLevelSliderEngine()
    @State private var art: SpiritLevelSliderImages?
    @State private var awake = true

    private typealias P = SpiritLevelSliderPaint
    private static let ink = P.color(0x2A241C)
    private static let presets = [4, 6, 9, 12]

    var body: some View {
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            let g = SpiritLevelSliderGeo(w: size.width, h: size.height, top: insets.top, bottom: insets.bottom)
            stage(g)
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                .offset(x: -insets.leading, y: -insets.top)
                .task(id: g) { await bake(g) }
        }
        .background(P.color(0xECE4D5).ignoresSafeArea())
    }

    private func bake(_ g: SpiritLevelSliderGeo) async {
        guard g.w > 0, g.h > 0, art?.geo != g else { return }
        let scale = min(displayScale, 3)
        art = await Task.detached(priority: .userInitiated) {
            _ = SpiritLevelSliderMotion.bubbleTable
            return SpiritLevelSliderImages(
                geo: g,
                background: SpiritLevelSliderArt.background(g, scale: scale),
                body: SpiritLevelSliderArt.levelBody(g, scale: scale),
                shadow: SpiritLevelSliderArt.shadow(g, scale: scale)
            )
        }.value
    }

    @ViewBuilder
    private func stage(_ g: SpiritLevelSliderGeo) -> some View {
        let auto = autoPlay && !reduceMotion
        let reduced = reduceMotion
        ZStack(alignment: .topLeading) {
            P.color(0xECE4D5)
            if let art, art.geo == g {
                TimelineView(.animation(paused: !auto && !awake)) { timeline in
                    Canvas { ctx, _ in
                        let pose = engine.step(timeline.date, auto: auto, reduced: reduced)
                        if engine.settled && !auto {
                            DispatchQueue.main.async { if engine.settled { awake = false } }
                        }
                        ctx.draw(Image(uiImage: art.background), in: CGRect(x: 0, y: 0, width: g.w, height: g.h))
                        SpiritLevelSliderFrame.roof(ctx, g, angle: pose.angle)
                        SpiritLevelSliderFrame.readout(ctx, g, angle: pose.angle)
                        SpiritLevelSliderFrame.level(ctx, g, body: art.body, shadow: art.shadow,
                                                     angle: pose.angle, bubble: pose.bubble, speed: pose.speed)
                    }
                }
                .frame(width: g.w, height: g.h)
                .contentShape(Rectangle())
                .gesture(
                    DragGesture(minimumDistance: 0)
                        .onChanged { v in
                            if engine.drag(v.startLocation, translationY: v.translation.height, g: g) { awake = true }
                        }
                        .onEnded { _ in
                            engine.release()
                            awake = true
                        }
                )
                .accessibilityElement()
                .accessibilityLabel("Roof pitch. Drag the level's end up or down to tilt it.")
                .accessibilityAddTraits(.allowsDirectInteraction)

                header(g)
                chips(g)
                cta(g)
            }
        }
        .frame(width: g.w, height: g.h, alignment: .topLeading)
        .clipped()
    }

    private func header(_ g: SpiritLevelSliderGeo) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 12) {
                Button {} label: {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundStyle(Self.ink)
                        .frame(width: 40, height: 40)
                        .background(Circle().fill(P.color(0xFFFDF8, 0.9)))
                        .shadow(color: P.color(0x5A4A30, 0.18), radius: 1, y: 1)
                        .shadow(color: P.color(0x5A4A30, 0.10), radius: 8, y: 6)
                }
                .buttonStyle(SpiritLevelSliderPress())
                .accessibilityLabel("Back")
                VStack(alignment: .leading, spacing: 6) {
                    Text("Step 3 of 5")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(Self.ink.opacity(0.55))
                    GeometryReader { track in
                        ZStack(alignment: .leading) {
                            Capsule().fill(Self.ink.opacity(0.1))
                            Capsule().fill(P.color(0xE8862A)).frame(width: track.size.width * 0.6)
                        }
                    }
                    .frame(height: 4)
                }
            }
            .frame(height: 40)
            .padding(.bottom, 14)
            Text("ROOF ESTIMATE")
                .font(.system(size: 11, weight: .bold))
                .kerning(1.8)
                .foregroundStyle(Self.ink.opacity(0.5))
            Text("How steep is your roof?")
                .font(.system(size: 28, weight: .bold))
                .kerning(-0.4)
                .foregroundStyle(Self.ink)
                .lineLimit(1)
                .minimumScaleFactor(0.8)
                .padding(.top, 6)
            Text("Tilt the level to match its pitch.")
                .font(.system(size: 15))
                .foregroundStyle(Self.ink.opacity(0.62))
                .padding(.top, 4)
        }
        .frame(width: g.w - 48, alignment: .leading)
        .offset(x: 24, y: g.top + 4)
    }

    private func chips(_ g: SpiritLevelSliderGeo) -> some View {
        HStack(spacing: 8) {
            ForEach(Self.presets, id: \.self) { r in
                Button {
                    engine.choose(rise: Double(r))
                    awake = true
                } label: {
                    Text("\(r)/12")
                        .font(.system(size: 15, weight: .semibold).monospacedDigit())
                        .foregroundStyle(Self.ink)
                        .frame(maxWidth: .infinity, minHeight: 40, maxHeight: 40)
                        .background(Capsule().fill(P.color(0xFFFDF8, 0.85)))
                        .overlay(Capsule().strokeBorder(Self.ink.opacity(0.14), lineWidth: 1 / displayScale))
                        .contentShape(Capsule())
                }
                .buttonStyle(SpiritLevelSliderPress())
                .accessibilityLabel("\(r) in 12 pitch")
            }
        }
        .frame(width: g.w - 32, height: 40)
        .offset(x: 16, y: g.chipsTop)
    }

    private func cta(_ g: SpiritLevelSliderGeo) -> some View {
        Button {} label: {
            HStack {
                Text("Continue")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(P.color(0xF8F2E6))
                Spacer()
                Text("Next: roof area")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(P.color(0xF8F2E6, 0.55))
            }
            .padding(.horizontal, 24)
            .frame(width: g.w - 32, height: 56)
            .background(Capsule().fill(Self.ink))
            .shadow(color: P.color(0x2A241C, 0.3), radius: 1, y: 1)
            .shadow(color: P.color(0x2A241C, 0.22), radius: 12, y: 10)
            .contentShape(Capsule())
        }
        .buttonStyle(SpiritLevelSliderPress())
        .offset(x: 16, y: g.ctaTop)
    }
}

private struct SpiritLevelSliderPress: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .animation(.spring(response: 0.25, dampingFraction: 0.8), value: configuration.isPressed)
    }
}

struct SpiritLevelSliderImages {
    let geo: SpiritLevelSliderGeo
    let background: UIImage
    let body: UIImage
    let shadow: UIImage
}

/// The clock, the level's spring, the bubble and the finger, stepped once a frame.
final class SpiritLevelSliderEngine {
    private typealias M = SpiritLevelSliderMotion

    private static let idle = 2.0
    private static let returnTime = 0.9
    private static let levelOmega = 12.0
    private static let levelZeta = 0.8

    /// 0 loop / rest, 1 a finger (or a preset) is in charge, 2 easing back to rest.
    private var mode = 0
    private var loopT = -1.0
    private var last: Double?
    private var wasAuto: Bool?
    private var lastTouch = 0.0
    private var dragging = false
    /// The drag's start: the level's angle then and the finger's x; nil when the touch missed it.
    private var grab: (angle: Double, x: CGFloat)?
    private var touchStarted = false
    private var angle = 0.0
    private var angleV = 0.0
    private var goal = 0.0
    private var bubble = 0.0
    private var bubbleV = 0.0
    private var returnFrom = (angle: 0.0, at: 0.0)
    private var hapticKey = -1
    private(set) var settled = false
    private let tick = UISelectionFeedbackGenerator()
    private let tap = UIImpactFeedbackGenerator(style: .light)

    private var clock: Double { Date().timeIntervalSinceReferenceDate }

    func step(_ date: Date, auto: Bool, reduced: Bool) -> (angle: Double, bubble: Double, speed: Double) {
        let t1 = date.timeIntervalSinceReferenceDate
        let raw = last.map { t1 - $0 } ?? 0
        last = t1
        let dt = min(max(raw, 0), 0.05)
        if wasAuto != auto {
            wasAuto = auto
            mode = 0
            loopT = auto ? 0 : -1
        }
        settled = false
        if mode == 0 && loopT >= 0 {
            // A frame that stalls pauses the loop instead of skipping ahead.
            if raw > 0 && raw <= 0.25 { loopT = (loopT + raw).truncatingRemainder(dividingBy: M.loop) }
            let t = loopT
            angle = M.angleAt(t)
            angleV = (M.angleAt(t + 1.0 / 120) - angle) * 120
            (bubble, bubbleV) = M.sampleBubble(t)
        } else {
            if mode == 2 {
                angle = M.mix(returnFrom.angle, 0, M.easeInOut(M.seg(t1, returnFrom.at, returnFrom.at + Self.returnTime)))
                angleV = 0
                if t1 > returnFrom.at + Self.returnTime + 0.6 {
                    mode = 0
                    loopT = 0
                }
            } else if !dragging && dt > 0 {
                // Spring toward the goal (a preset, or back inside the range after a rubber-band).
                let steps = max(1, Int(ceil(dt * 240)))
                let h = dt / Double(steps)
                for _ in 0..<steps {
                    let acc = -Self.levelOmega * Self.levelOmega * (angle - goal) - 2 * Self.levelZeta * Self.levelOmega * angleV
                    angleV += acc * h
                    angle += angleV * h
                }
                if mode == 1 && auto && t1 - lastTouch > Self.idle {
                    mode = 2
                    returnFrom = (angle, t1)
                }
            }
            if reduced {
                bubble = M.bubbleGoal(angle)
                bubbleV = 0
            } else {
                (bubble, bubbleV) = M.stepBubble(bubble, bubbleV, target: M.bubbleGoal(angle), dt: dt)
            }
            settled = !auto && !dragging && mode != 2 && abs(angle - goal) < 0.005 && abs(angleV) < 0.01
                && abs(bubble - M.bubbleGoal(angle)) < 0.0005 && abs(bubbleV) < 0.002
        }
        // Haptics only for a finger: a tick per whole step of rise, a tap back between the centre marks.
        let key = mode == 1 ? Int(floor(M.riseOf(angle) + 0.5)) * 2 + (abs(bubble) < 0.06 ? 1 : 0) : -1
        if hapticKey >= 0 && key >= 0 && key != hapticKey {
            if key >> 1 != hapticKey >> 1 { tick.selectionChanged() } else if key & 1 == 1 { tap.impactOccurred() }
        }
        hapticKey = key
        return (angle, bubble, bubbleV)
    }

    /// A drag on the screen; returns whether it holds the level.
    func drag(_ start: CGPoint, translationY: CGFloat, g: SpiritLevelSliderGeo) -> Bool {
        if !touchStarted {
            touchStarted = true
            guard start.y >= g.card.maxY && start.y <= g.chipsTop - 4 else {
                grab = nil
                return false
            }
            mode = 1
            loopT = -1
            dragging = true
            lastTouch = clock
            // The lever arm is where the finger landed; the level keeps its angle under the finger.
            grab = (angle, start.x)
            angleV = 0
            tick.prepare()
        }
        guard let grab else { return false }
        lastTouch = clock
        let side: Double = grab.x >= g.cx ? 1 : -1
        let arm = max(abs(Double(grab.x - g.cx)), 60)
        let turned = atan2(-Double(translationY) * side, arm) * 180 / .pi
        let a = M.rubber(grab.angle + turned)
        angleV = (a - angle) * 60
        angle = a
        return true
    }

    func release() {
        touchStarted = false
        guard grab != nil else { return }
        dragging = false
        lastTouch = clock
        goal = M.clamp(angle, M.minAngle + 6, M.maxAngle)
        grab = nil
    }

    func choose(rise: Double) {
        tick.selectionChanged()
        mode = 1
        loopT = -1
        lastTouch = clock
        goal = atan(rise / 12) * 180 / .pi
    }
}
