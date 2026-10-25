import SwiftUI
import UIKit

/*
 Compass Needle Nav — "Northbound", a hiking app whose bottom navigation is a liquid compass.

 Look: a night-green ground; the page (Map with a contour map and the day's trail, Trips, Gear
 with a pack list, Me with stats and badges); a dock with a compass: a knurled anodised housing,
 a bezel with degrees every 30° and the cardinals (N in red), a well of tinted damping fluid with
 a faint card, a red-and-cream faceted needle floating in it with a shadow, an air bubble and a
 glass dome. Map, Trips, Gear and Me sit round it at N, E, S and W.

 Tap a tab: the needle swings to it through the liquid (an underdamped spring, ω 7 rad/s,
 ζ 0.4 — it overshoots 25 % and settles in ~1.4 s), the bubble drifts against the spin, and the
 pages, which hang on a wheel centred on the compass one every 90°, swing round so the new page
 comes in from the side its tab sits on (critically damped, ω 5.5 rad/s), fading with the angle.
 Drag round the bezel and the needle and the wheel follow the finger (a bead marks it, a light
 tick at every quarter turn); let go and it settles on the nearest tab. 2 s after the last touch
 everything eases back to N (0.9 s) and the loop (CompassNeedleNavMotion.swift, loop = 8 s: E, S,
 then on round to N) starts again. Reduced motion: no loop, no swing; a tab shows its page at once.
*/
struct CompassNeedleNav: View {
    /// Plays the 8 s loop (N → E → S → round to N) until touched.
    var autoPlay = false

    static let loop = CompassNeedleNavMotion.loop
    private typealias M = CompassNeedleNavMotion
    private typealias Art = CompassNeedleNavArt

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var engine = CompassNeedleNavEngine()
    @State private var handActive = false
    /// Under reduced motion a tab simply shows its page.
    @State private var still = 0.0
    @State private var dragStarted = false
    @State private var idleTask: Task<Void, Never>? = nil
    @State private var light = UIImpactFeedbackGenerator(style: .light)
    @State private var select = UISelectionFeedbackGenerator()

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let s = Art.Scene(w: proxy.size.width + insets.leading + insets.trailing,
                              h: proxy.size.height + insets.top + insets.bottom,
                              top: insets.top, bottom: insets.bottom)
            stage(s, top: insets.top)
                .frame(width: s.w, height: s.h, alignment: .topLeading)
                .offset(x: -insets.leading, y: -insets.top)
        }
        .background(Color(hex: 0x0F1C18).ignoresSafeArea())
        .preferredColorScheme(.dark)
        .onAppear {
            light.prepare()
            select.prepare()
        }
        .onDisappear { idleTask?.cancel() }
    }

    private func stage(_ s: Art.Scene, top: Double) -> some View {
        let auto = autoPlay && !reduceMotion
        let reduced = reduceMotion
        let B = s.box
        return ZStack(alignment: .topLeading) {
            CompassNeedleNavGround(scene: s).equatable()

            TimelineView(.animation(paused: !auto && !handActive)) { timeline in
                let now = timeline.date.timeIntervalSinceReferenceDate
                let pose = engine.pose(at: now, auto: auto, reduced: reduced, still: still)
                let bob = reduced ? 0 : 2 * .pi * 2 * engine.bobTime / M.loop
                ZStack(alignment: .topLeading) {
                    // Pages on the wheel: each hangs at k × 90° and swings round the compass's centre.
                    ZStack(alignment: .topLeading) {
                        ForEach(0..<4, id: \.self) { k in
                            let d = M.wrap(Double(k) * 90 - pose.wheel)
                            let fade = abs(d) >= 89 ? 0 : M.clamp01(1 - pow(abs(d) / 80, 1.5))
                            CompassNeedleNavPage(scene: s, index: k).equatable()
                                .offset(y: s.pageTop)
                                .frame(width: s.w, height: s.h, alignment: .topLeading)
                                .rotationEffect(.degrees(d), anchor: UnitPoint(x: s.cx / s.w, y: s.cy / s.h))
                                .opacity(fade)
                        }
                    }
                    .frame(width: s.w, height: s.h, alignment: .topLeading)
                    .mask(alignment: .topLeading) {
                        Rectangle()
                            .frame(width: s.w, height: s.pageH + 12)
                            .offset(y: s.pageTop - 6)
                    }

                    CompassNeedleNavCompass(scene: s).equatable()
                        .offset(x: s.cx - B / 2, y: s.cy - B / 2)
                    Canvas { ctx, _ in Art.needle(ctx, s, pose, bob: bob) }
                    CompassNeedleNavGlass(scene: s).equatable()
                        .offset(x: s.cx - B / 2, y: s.cy - B / 2)
                    Canvas { ctx, _ in Art.bead(ctx, s, pose) }

                    tabs(s, wheel: pose.wheel, auto: auto, reduced: reduced)
                }
                .frame(width: s.w, height: s.h, alignment: .topLeading)
            }

            header(s, top: top)
                .allowsHitTesting(false)
        }
        .frame(width: s.w, height: s.h, alignment: .topLeading)
        .clipped()
        .gesture(drag(s, reduced: reduced))
        .accessibilityElement(children: .contain)
        .accessibilityLabel("Compass navigation. Drag round the bezel to turn, or tap Map, Trips, Gear or Me.")
    }

    // MARK: Header and tabs

    private func header(_ s: Art.Scene, top: Double) -> some View {
        HStack {
            Text("NORTHBOUND")
                .font(.system(size: 12, weight: .bold))
                .kerning(3)
                .foregroundStyle(Art.accent)
            Spacer(minLength: 8)
            HStack(spacing: 6) {
                CompassNeedleNavSun()
                    .frame(width: 14, height: 14)
                Text("6° · Clear")
                    .font(.system(size: 13, weight: .semibold).monospacedDigit())
                    .foregroundStyle(Art.ink)
            }
            .padding(.horizontal, 10)
            .frame(height: 28)
            .background(Capsule().fill(Color.rgba(238, 241, 232, 0.07)))
            .overlay(Capsule().strokeBorder(Color.rgba(238, 241, 232, 0.16), lineWidth: 0.5))
        }
        .frame(width: s.w - 40, height: 28)
        .offset(x: 20, y: top + 8)
    }

    private func tabs(_ s: Art.Scene, wheel: Double, auto: Bool, reduced: Bool) -> some View {
        let frames: [CGRect] = [
            CGRect(x: s.cx - 50, y: s.cy - s.r - 44, width: 100, height: 32),
            CGRect(x: s.cx + s.r + 18, y: s.cy - 26, width: 56, height: 52),
            CGRect(x: s.cx - 50, y: s.cy + s.r + 10, width: 100, height: 32),
            CGRect(x: s.cx - s.r - 74, y: s.cy - 26, width: 56, height: 52),
        ]
        return ForEach(0..<4, id: \.self) { i in
            let on = M.clamp01(1 - abs(M.wrap(wheel - Double(i) * 90)) / 45)
            Button {
                selectTab(i, reduced: reduced)
            } label: {
                ZStack {
                    tabInner(i, color: Color.rgba(238, 241, 232, 0.55))
                    tabInner(i, color: Art.accent).opacity(on)
                }
                .frame(width: frames[i].width + 20, height: frames[i].height + 20)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .offset(x: frames[i].minX - 10, y: frames[i].minY - 10)
            .accessibilityLabel(Art.tabs[i])
            .accessibilityAddTraits(on > 0.5 ? [.isButton, .isSelected] : .isButton)
        }
    }

    @ViewBuilder
    private func tabInner(_ i: Int, color: Color) -> some View {
        let label = Text(Art.tabs[i])
            .font(.system(size: 13, weight: .semibold))
            .foregroundStyle(color)
        if i == 1 || i == 3 {
            VStack(spacing: 3) {
                CompassNeedleNavIcon(index: i, color: color).frame(width: 18, height: 18)
                label
            }
        } else {
            HStack(spacing: 6) {
                CompassNeedleNavIcon(index: i, color: color).frame(width: 18, height: 18)
                label
            }
        }
    }

    // MARK: Touch

    private func wake() {
        idleTask?.cancel()
        handActive = true
        idleTask = Task { @MainActor in
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(0.5))
                if !Task.isCancelled && engine.mode == .auto && !engine.dragging {
                    handActive = false
                    return
                }
            }
        }
    }

    private func selectTab(_ index: Int, reduced: Bool) {
        light.impactOccurred()
        engine.lastTouch = Date.now.timeIntervalSinceReferenceDate
        if reduced {
            still = Double(index) * 90
            return
        }
        wake()
        engine.takeOver()
        // The shortest way round to the tab's heading.
        engine.target = engine.needle + M.wrap(Double(index) * 90 - engine.needle)
    }

    private func fingerAngle(_ p: CGPoint, _ s: Art.Scene) -> Double {
        atan2(p.x - s.cx, -(p.y - s.cy)) * 180 / .pi
    }

    private func drag(_ s: Art.Scene, reduced: Bool) -> some Gesture {
        DragGesture(minimumDistance: 2)
            .onChanged { v in
                let now = Date.now.timeIntervalSinceReferenceDate
                if !dragStarted {
                    dragStarted = true
                    guard !reduced else { return }
                    let st = v.startLocation
                    let d = hypot(st.x - s.cx, st.y - s.cy)
                    guard d >= s.r * 0.45 && d <= s.r + 40 else { return }
                    wake()
                    engine.takeOver()
                    engine.dragging = true
                    engine.finger = fingerAngle(st, s)
                    engine.lastTouch = now
                }
                guard engine.dragging, let last = engine.finger else { return }
                let a = fingerAngle(v.location, s)
                let before = M.round(engine.target / 90)
                engine.target += M.wrap(a - last)
                engine.finger = a
                if M.round(engine.target / 90) != before { select.selectionChanged() }
                engine.lastTouch = now
            }
            .onEnded { _ in
                dragStarted = false
                guard engine.dragging else { return }
                engine.dragging = false
                engine.target = 90 * M.round(engine.target / 90)
                engine.finger = nil
                engine.lastTouch = Date.now.timeIntervalSinceReferenceDate
                light.impactOccurred()
            }
    }
}

/// The tabs' icons, stroked like the React Native SVGs (18 × 18).
private struct CompassNeedleNavIcon: View {
    let index: Int
    let color: Color

    var body: some View {
        Canvas { c, size in
            let k = size.width / 18
            c.scaleBy(x: k, y: k)
            let style = StrokeStyle(lineWidth: 1.7, lineCap: .round, lineJoin: .round)
            func pt(_ x: Double, _ y: Double) -> CGPoint { CGPoint(x: x, y: y) }
            switch index {
            case 0:
                var p = Path()
                p.move(to: pt(2, 4.5))
                p.addLine(to: pt(6.5, 2.5))
                p.addLine(to: pt(11.5, 4.5))
                p.addLine(to: pt(16, 2.5))
                p.addLine(to: pt(16, 13.5))
                p.addLine(to: pt(11.5, 15.5))
                p.addLine(to: pt(6.5, 13.5))
                p.addLine(to: pt(2, 15.5))
                p.closeSubpath()
                p.move(to: pt(6.5, 2.5))
                p.addLine(to: pt(6.5, 13.5))
                p.move(to: pt(11.5, 4.5))
                p.addLine(to: pt(11.5, 15.5))
                c.stroke(p, with: .color(color), style: style)
            case 1:
                var p = Path()
                p.move(to: pt(4, 14))
                p.addCurve(to: pt(14, 5), control1: pt(4, 9), control2: pt(14, 10))
                c.stroke(p, with: .color(color), style: style)
                c.fill(Path(ellipseIn: CGRect(x: 2.2, y: 12.7, width: 3.6, height: 3.6)), with: .color(color))
                c.fill(Path(ellipseIn: CGRect(x: 12.2, y: 1.7, width: 3.6, height: 3.6)), with: .color(color))
            case 2:
                var p = Path()
                p.move(to: pt(5, 6.5))
                p.addCurve(to: pt(9, 2.5), control1: pt(5, 4), control2: pt(6.8, 2.5))
                p.addCurve(to: pt(13, 6.5), control1: pt(11.2, 2.5), control2: pt(13, 4))
                p.move(to: pt(4, 7))
                p.addLine(to: pt(14, 7))
                p.addLine(to: pt(14, 15.5))
                p.addLine(to: pt(4, 15.5))
                p.closeSubpath()
                p.move(to: pt(7, 10.5))
                p.addLine(to: pt(11, 10.5))
                c.stroke(p, with: .color(color), style: style)
            default:
                var p = Path(ellipseIn: CGRect(x: 5.8, y: 2.8, width: 6.4, height: 6.4))
                p.move(to: pt(3, 16))
                p.addCurve(to: pt(9, 11), control1: pt(3.8, 12.6), control2: pt(6.2, 11))
                p.addCurve(to: pt(15, 16), control1: pt(11.8, 11), control2: pt(14.2, 12.6))
                c.stroke(p, with: .color(color), style: style)
            }
        }
    }
}

/// The weather chip's sun (14 × 14).
private struct CompassNeedleNavSun: View {
    var body: some View {
        Canvas { c, size in
            let k = size.width / 14
            c.scaleBy(x: k, y: k)
            let sun = Color(hex: 0xF5C26B)
            c.fill(Path(ellipseIn: CGRect(x: 4, y: 4, width: 6, height: 6)), with: .color(sun))
            var rays = Path()
            let segs: [(Double, Double, Double, Double)] = [
                (7, 1, 7, 2.6), (7, 11.4, 7, 13), (1, 7, 2.6, 7), (11.4, 7, 13, 7),
                (2.8, 2.8, 3.9, 3.9), (10.1, 10.1, 11.2, 11.2), (2.8, 11.2, 3.9, 10.1), (10.1, 3.9, 11.2, 2.8),
            ]
            for (x0, y0, x1, y1) in segs {
                rays.move(to: CGPoint(x: x0, y: y0))
                rays.addLine(to: CGPoint(x: x1, y: y1))
            }
            c.stroke(rays, with: .color(sun), style: StrokeStyle(lineWidth: 1.3, lineCap: .round))
        }
    }
}

/// The ground and the dock, drawn once.
private struct CompassNeedleNavGround: View, Equatable {
    let scene: CompassNeedleNavArt.Scene
    var body: some View {
        Canvas { ctx, _ in CompassNeedleNavArt.ground(ctx, scene) }
            .frame(width: scene.w, height: scene.h)
    }
}

/// One page, drawn once.
private struct CompassNeedleNavPage: View, Equatable {
    let scene: CompassNeedleNavArt.Scene
    let index: Int
    var body: some View {
        Canvas { ctx, _ in CompassNeedleNavArt.page(ctx, scene, index) }
            .frame(width: scene.w, height: scene.pageH)
    }
}

/// The compass base, drawn once.
private struct CompassNeedleNavCompass: View, Equatable {
    let scene: CompassNeedleNavArt.Scene
    var body: some View {
        Canvas { ctx, _ in CompassNeedleNavArt.compass(ctx, scene) }
            .frame(width: scene.box, height: scene.box)
    }
}

/// The glass dome, drawn once.
private struct CompassNeedleNavGlass: View, Equatable {
    let scene: CompassNeedleNavArt.Scene
    var body: some View {
        Canvas { ctx, _ in CompassNeedleNavArt.glass(ctx, scene) }
            .frame(width: scene.box, height: scene.box)
    }
}
