import SwiftUI
import UIKit

// The reusable piece: a first-run tour over any screen. A dim layer with one soft cutout that morphs from
// target to target (one even-odd path driven by one progress value), and a tooltip bubble that slides after
// it, re-points its arrow, and carries step dots, Skip and Next. Next, a tap on the highlighted element or a
// swipe of the tooltip moves the tour; Skip / Done dismisses it.

private typealias M = CoachMarkSpotlightMotion

struct CoachMarkSpotlightPose {
    /// Fractional step of the cutout: 0 first target, 1 second, …
    var p: Double = 0
    /// The tooltip's own copy of p, a beat behind.
    var tipP: Double = 0
    /// Swipe offset of the tooltip, pt.
    var tipX: Double = 0
    /// Dim layer and ring: 0 hidden, 1 shown.
    var shown: Double = 1
    /// Tooltip: 0 hidden (scaled down), 1 shown.
    var tipShow: Double = 1
    /// 1 = the cutout is a circle much larger than the first target around it (the tour opening).
    var iris: Double = 0
    /// The ring breathing out of the cutout: phase 0..1 and how much of it shows.
    var pulse: Double = 0
    var pulseGate: Double = 0
    /// Press feedback on Next, Skip and the highlighted element (0..1).
    var nextPress: Double = 0
    var skipPress: Double = 0
    var targetPress: Double = 0
    /// The tour takes touches (shown); hidden, touches reach the app.
    var visible = true
}

/// The finger's springs for the tour. Writes happen only on touches; frames sample the tracks.
@Observable
final class CoachMarkSpotlightTourMotion {
    typealias Track = CoachMarkSpotlightTrack

    var p = Track(0)
    var tipP = Track(0)
    var tipX = Track(0)
    var shown = Track(1)
    var tipShow = Track(1)
    var iris = Track(0)
    var nextPress = Track(0)
    var skipPress = Track(0)
    var targetPress = Track(0)
    var step = 0
    var visible = true
    var calm = false
    /// The pulse runs (only while autoPlay is on), from this clock.
    var pulsing = false
    var pulseStart = Date.now
    /// The ring shows once the step has settled; it fades out while the tour moves.
    private var moveStart = Date.distantPast
    private var settledAt = Date.distantPast
    var busyUntil = Date.distantPast
    let count: Int

    init(count: Int) {
        self.count = count
    }

    func pose(at date: Date) -> CoachMarkSpotlightPose {
        var pose = CoachMarkSpotlightPose(
            p: p.value(at: date), tipP: tipP.value(at: date), tipX: tipX.value(at: date),
            shown: shown.value(at: date), tipShow: tipShow.value(at: date), iris: iris.value(at: date),
            nextPress: nextPress.value(at: date), skipPress: skipPress.value(at: date),
            targetPress: targetPress.value(at: date), visible: visible)
        if pulsing {
            pose.pulse = (date.timeIntervalSince(pulseStart) / M.pulsePeriod).truncatingRemainder(dividingBy: 1)
            let gate = date < settledAt
                ? max(0, 1 - date.timeIntervalSince(moveStart) / 0.3)
                : M.clamp01(date.timeIntervalSince(settledAt) / 0.3)
            pose.pulseGate = gate * pose.shown
        }
        return pose
    }

    /// Takes over a pose (from the script) as the tracks' starting point.
    func load(_ pose: CoachMarkSpotlightPose, at date: Date) {
        p.set(pose.p, at: date)
        tipP.set(pose.tipP, at: date)
        tipX.set(pose.tipX, at: date)
        shown.set(pose.shown, at: date)
        tipShow.set(pose.tipShow, at: date)
        iris.set(pose.iris, at: date)
        nextPress.set(pose.nextPress, at: date)
        skipPress.set(pose.skipPress, at: date)
        targetPress.set(pose.targetPress, at: date)
        visible = pose.visible
        pulseStart = date.addingTimeInterval(-pose.pulse * M.pulsePeriod)
        moveStart = date
        settledAt = pose.pulseGate > 0.5 ? date.addingTimeInterval(-0.3) : date
    }

    private func busy(_ track: Track) {
        let end = track.settles()
        if end > busyUntil { busyUntil = end }
    }

    private func springTo(_ track: inout Track, _ to: Double, _ s: CoachMarkSpotlightSpring, velocity: Double = 0,
                          delay: Double = 0, at date: Date) {
        if calm { track.set(to, at: date) } else { track.spring(to: to, s, velocity: velocity, delay: delay, at: date) }
        busy(track)
    }

    private func timing(_ track: inout Track, _ to: Double, _ duration: Double, _ curve: Track.Curve = .quad,
                        delay: Double = 0, at date: Date) {
        track.timing(to: to, duration: duration, curve: curve, delay: delay, at: date)
        busy(track)
    }

    /// The ring waits for the cutout to reach its step.
    private func moving(at date: Date) {
        moveStart = date
        settledAt = max(p.settles(within: 0.02), tipX.settles(within: 2))
    }

    /// Dismisses the tour (Skip, Done): the dim lifts, the tooltip shrinks away, touches reach the app.
    func hide(at date: Date) {
        timing(&shown, 0, calm ? 0.2 : 0.36, .inOutCubic, at: date)
        timing(&tipShow, 0, calm ? 0.16 : 0.24, .inCubic, at: date)
        springTo(&tipX, 0, M.dragSpring, at: date)
        visible = false
    }

    /// Moves the tour to step s (past the last one dismisses it); the tooltip follows tipLag later.
    func goTo(_ s: Int, velocity: Double = 0, at date: Date) {
        if s < 0 {
            springTo(&tipX, 0, M.dragSpring, velocity: velocity, at: date)
            moving(at: date)
            return
        }
        if s >= count {
            hide(at: date)
            CoachMarkSpotlightHaptics.light()
            return
        }
        step = s
        springTo(&p, Double(s), M.morphSpring, at: date)
        springTo(&tipP, Double(s), M.tipSpring, delay: M.tipLag, at: date)
        springTo(&tipX, 0, M.dragSpring, velocity: velocity, at: date)
        moving(at: date)
        CoachMarkSpotlightHaptics.light()
    }

    /// Opens the tour from the start: the dim fades in while the cutout irises down onto the first target.
    func open(at date: Date) {
        step = 0
        visible = true
        tipX.set(0, at: date)
        if shown.value(at: date) < 0.5 {
            p.set(0, at: date)
            tipP.set(0, at: date)
            iris.set(calm ? 0 : 1, at: date)
            timing(&shown, 1, 0.4, .outCubic, at: date)
            if !calm { springTo(&iris, 0, M.irisSpring, at: date) }
            timing(&tipShow, 1, 0.4, .outCubic, delay: calm ? 0 : 0.5, at: date)
        } else {
            springTo(&p, 0, M.morphSpring, at: date)
            springTo(&tipP, 0, M.tipSpring, delay: M.tipLag, at: date)
            timing(&shown, 1, 0.2, at: date)
            timing(&tipShow, 1, 0.2, at: date)
            timing(&iris, 0, 0.2, at: date)
        }
        moving(at: date)
    }

    /// A script let go: the tour settles on the step nearest to where it was, or stays dismissed.
    func adopt(at date: Date) {
        timing(&nextPress, 0, 0.12, at: date)
        timing(&skipPress, 0, 0.12, at: date)
        timing(&targetPress, 0, 0.12, at: date)
        if shown.value(at: date) < 0.5 || tipShow.value(at: date) < 0.5 {
            hide(at: date)
            return
        }
        let s = min(max(Int(p.value(at: date).rounded()), 0), count - 1)
        step = s
        visible = true
        springTo(&p, Double(s), M.morphSpring, at: date)
        springTo(&tipP, Double(s), M.tipSpring, at: date)
        springTo(&tipX, 0, M.dragSpring, at: date)
        timing(&shown, 1, 0.16, at: date)
        timing(&tipShow, 1, 0.16, at: date)
        timing(&iris, 0, 0.16, at: date)
        if abs(p.value(at: date) - Double(s)) > 0.02 { moving(at: date) }
    }

    // MARK: Finger

    /// Swipe resistance: free in a direction that moves the tour, rubbery in one that does not.
    func drag(_ dx: Double, at date: Date) {
        let free = dx > 0 ? step > 0 : true
        let x: Double
        if free {
            x = dx
        } else {
            let a = abs(dx)
            x = (dx < 0 ? -1 : 1) * (a * M.swipeRubber / (1 + a / 160))
        }
        tipX.set(x, at: date)
        moveStart = min(moveStart, date)
        if abs(x) > 2 && settledAt <= date {
            moveStart = date
            settledAt = .distantFuture
        }
    }

    func release(velocity: Double, at date: Date) {
        let dx = tipX.value(at: date)
        let s = step
        if (dx > M.swipeStep || velocity > M.swipeVelocity) && s > 0 {
            goTo(s - 1, velocity: velocity, at: date)
        } else if dx < -M.swipeStep || velocity < -M.swipeVelocity {
            goTo(s + 1, velocity: velocity, at: date)
        } else {
            springTo(&tipX, 0, M.dragSpring, velocity: velocity, at: date)
            settledAt = tipX.settles(within: 2)
        }
    }
}

enum CoachMarkSpotlightHaptics {
    static func light() { UIImpactFeedbackGenerator(style: .light).impactOccurred() }
}

struct CoachMarkSpotlightTipPlace {
    let x: Double
    let y: Double
    let arrowX: Double
    let below: Double
}

enum CoachMarkSpotlightTip {
    static let bubble = Color(hex: 0xFFFDF9)
    static let ink = Color(hex: 0x1E2420)
    static let mute = Color(hex: 0x6E746F)
    static let accent = Color(hex: 0x2F7D5B)
    static let nextW: Double = 84
    static let skipW: Double = 56
    static let footH: Double = 36
    static let pad: Double = 18

    /// Where the tooltip sits for each target: under it in the top half of the screen, above it otherwise.
    static func places(_ targets: [CoachMarkSpotlightBox], width: Double, height: Double, tipW: Double) -> [CoachMarkSpotlightTipPlace] {
        targets.map { b in
            let cx = b.x + b.w / 2
            let below = b.y + b.h / 2 < height / 2
            let x = min(max(cx - tipW / 2, 16), width - 16 - tipW)
            let y = below ? b.y + b.h + M.tipGap + M.arrowH : b.y - M.tipGap - M.arrowH - M.tipH
            let arrowX = min(max(cx - x, 28), tipW - 28)
            return CoachMarkSpotlightTipPlace(x: x, y: y, arrowX: arrowX, below: below ? 1 : 0)
        }
    }
}

/// The bubble's arrow, 20 × 9 with a softened tip.
private struct CoachMarkSpotlightArrow: Shape {
    let down: Bool

    func path(in rect: CGRect) -> Path {
        let w = M.arrowW
        let h = M.arrowH
        let d = down
            ? "M0 0 H\(w) L\(w / 2 + 2) \(h - 1) Q\(w / 2) \(h + 0.6) \(w / 2 - 2) \(h - 1) Z"
            : "M0 \(h + 1) H\(w) L\(w / 2 + 2) 2 Q\(w / 2) 0.4 \(w / 2 - 2) 2 Z"
        return CoachMarkSpotlightSVG.path(d)
    }
}

/// The tour layer. Put it over the screen it explains, full size.
struct CoachMarkSpotlightMarks: View {
    let steps: [CoachMarkSpotlightStep]
    /// One box per step, in screen points, already padded; r is the corner radius (w / 2 for a circle).
    let targets: [CoachMarkSpotlightBox]
    let pose: CoachMarkSpotlightPose
    let motion: CoachMarkSpotlightTourMotion
    let width: Double
    let height: Double
    /// A finger on the tour (hands it over from a script).
    var grab: () -> Void = {}

    @State private var dragging = false
    @State private var dragIgnored = false
    @State private var startX = 0.0
    @GestureState private var pressing = false

    var body: some View {
        let count = steps.count
        let tipW = min(width - 32, M.tipMaxW)
        let places = CoachMarkSpotlightTip.places(targets, width: width, height: height, tipW: tipW)
        let box = M.cutoutAt(targets, pose.p, pose.iris)
        let irisFade = M.clamp01(1 - pose.iris * 3)
        let ringOpacity = pose.shown * (0.42 + 0.4 * pose.targetPress) * irisFade
        let grow = 1 - pow(1 - pose.pulse, 2)
        let pulseOpacity = M.pulseAlpha * (1 - pose.pulse) * pose.pulseGate * pose.shown * irisFade

        ZStack(alignment: .topLeading) {
            Canvas { ctx, _ in
                var dim = ctx
                dim.opacity = pose.shown
                dim.addFilter(.blur(radius: M.softEdge))
                var path = Path(CGRect(x: -40, y: -40, width: width + 80, height: height + 80))
                path.addPath(Path(roundedRect: box.rect, cornerRadius: box.r, style: .circular))
                dim.fill(path, with: .color(M.dim), style: FillStyle(eoFill: true))

                func ring(_ g: Double, _ opacity: Double) {
                    guard opacity > 0.001 else { return }
                    let r = box.rect.insetBy(dx: -g, dy: -g)
                    ctx.stroke(Path(roundedRect: r, cornerRadius: box.r + g, style: .circular),
                               with: .color(.white.opacity(opacity)), lineWidth: 1.5)
                }
                ring(M.ringInset + M.pulseGrow * grow, pulseOpacity)
                ring(M.ringInset, ringOpacity)
            }
            .frame(width: width, height: height)
            .allowsHitTesting(false)

            // Taps: inside the cutout they move the tour on; on the dim they do nothing.
            Color.clear
                .contentShape(Rectangle())
                .frame(width: width, height: height)
                .onTapGesture(coordinateSpace: .local) { at in
                    grab()
                    let now = Date.now
                    guard motion.visible else { return }
                    let b = M.cutoutAt(targets, motion.p.value(at: now), motion.iris.value(at: now))
                    if b.rect.contains(at) { motion.goTo(motion.step + 1, at: now) }
                }
                .allowsHitTesting(pose.visible)
                .accessibilityHidden(true)

            tooltip(width: tipW, places: places, count: count)
        }
        .frame(width: width, height: height, alignment: .topLeading)
    }

    private func tooltip(width tipW: Double, places: [CoachMarkSpotlightTipPlace], count: Int) -> some View {
        let q = pose.tipP
        let show = pose.tipShow
        let below = M.along(places.map(\.below), q)
        let x = M.along(places.map(\.x), q) + pose.tipX
        let y = M.along(places.map(\.y), q) + (1 - show) * (below > 0.5 ? -14 : 14)
        let arrowX = M.along(places.map(\.arrowX), q)
        let up = M.clamp01((below - 0.5) * 2)
        let down = M.clamp01((0.5 - below) * 2)
        let T = CoachMarkSpotlightTip.self

        return ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .fill(T.bubble)
                .shadow(color: Color.rgba(8, 12, 18, 0.18), radius: 3, y: 2)
                .shadow(color: Color.rgba(8, 12, 18, 0.28), radius: 20, y: 18)
                .overlay(
                    RoundedRectangle(cornerRadius: 20, style: .continuous).inset(by: 0.5)
                        .stroke(LinearGradient(stops: [.init(color: .white.opacity(0.95), location: 0),
                                                       .init(color: .white.opacity(0), location: 0.04)],
                                               startPoint: .top, endPoint: .bottom), lineWidth: 1)
                )
                .frame(width: tipW, height: M.tipH)

            CoachMarkSpotlightArrow(down: false)
                .fill(T.bubble)
                .frame(width: M.arrowW, height: M.arrowH + 1)
                .scaleEffect(x: 1, y: up, anchor: .bottom)
                .opacity(up)
                .offset(x: arrowX - M.arrowW / 2, y: -M.arrowH)
            CoachMarkSpotlightArrow(down: true)
                .fill(T.bubble)
                .frame(width: M.arrowW, height: M.arrowH + 1)
                .scaleEffect(x: 1, y: down, anchor: .top)
                .opacity(down)
                .offset(x: arrowX - M.arrowW / 2, y: M.tipH - 1)

            ForEach(Array(steps.enumerated()), id: \.offset) { i, step in
                let d = q - Double(i)
                VStack(alignment: .leading, spacing: 0) {
                    Text("\(i + 1) of \(count)")
                        .font(.system(size: 13)).monospacedDigit().foregroundStyle(T.mute)
                        .frame(height: 16)
                    Text(step.title)
                        .font(.system(size: 18, weight: .bold)).foregroundStyle(T.ink).lineLimit(1)
                        .frame(height: 24)
                        .padding(.top, 6)
                    Text(step.body)
                        .font(.system(size: 15)).lineSpacing(20 - 17.9).foregroundStyle(Color(hex: 0x3D443F))
                        .lineLimit(2)
                        .frame(height: 40, alignment: .topLeading)
                        .padding(.top, 4)
                }
                .frame(width: tipW - T.pad * 2, alignment: .leading)
                .opacity(M.clamp01(1 - abs(d) * 2))
                .offset(x: T.pad, y: T.pad - d * 8)
                .allowsHitTesting(false)
                .accessibilityHidden(abs(d) > 0.5)
            }

            HStack(spacing: 0) {
                HStack(spacing: 6) {
                    ForEach(0..<count, id: \.self) { i in
                        let on = M.clamp01(1 - abs(q - Double(i)))
                        Capsule().fill(Color.rgba(30, 36, 32, 0.16))
                            .overlay(Capsule().fill(T.accent).opacity(on))
                            .frame(width: 6 + 14 * on, height: 6)
                    }
                }
                .frame(minWidth: 0, maxWidth: .infinity, alignment: .leading)
                Button {
                    grab()
                    motion.hide(at: .now)
                } label: {
                    Text("Skip")
                        .font(.system(size: 15, weight: .semibold)).foregroundStyle(T.mute)
                        .frame(width: T.skipW, height: T.footH)
                        .opacity(1 - 0.45 * pose.skipPress)
                        .contentShape(Rectangle().inset(by: -8))
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Skip the tour")
                Button {
                    grab()
                    motion.goTo(motion.step + 1, at: .now)
                } label: {
                    ZStack {
                        Text("Next").opacity(M.clamp01((Double(count) - 1.5 - q) * 2))
                        Text("Done").opacity(M.clamp01((q - (Double(count) - 1.5)) * 2))
                    }
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(Color(hex: 0xF4FBF7))
                    .frame(width: T.nextW, height: T.footH)
                    .background(
                        Capsule().fill(T.accent)
                            .shadow(color: Color.rgba(20, 60, 42, 0.25), radius: 1, y: 1)
                            .overlay(Capsule().inset(by: 0.5).stroke(LinearGradient(
                                stops: [.init(color: .white.opacity(0.22), location: 0), .init(color: .white.opacity(0), location: 0.1)],
                                startPoint: .top, endPoint: .bottom), lineWidth: 1))
                    )
                    .scaleEffect(1 - 0.07 * pose.nextPress)
                    .contentShape(Capsule().inset(by: -6))
                }
                .buttonStyle(.plain)
                .padding(.leading, 6)
                .accessibilityLabel("Next tip")
            }
            .frame(width: tipW - T.pad * 2, height: T.footH)
            .offset(x: T.pad, y: M.tipH - T.pad - T.footH)
        }
        .frame(width: tipW, height: M.tipH, alignment: .topLeading)
        .contentShape(Rectangle())
        .gesture(swipe)
        .onChange(of: pressing) { _, now in
            // A cancelled drag never reaches onEnded.
            if !now && dragging {
                dragging = false
                if !dragIgnored { motion.release(velocity: 0, at: .now) }
            }
        }
        .scaleEffect(M.mix(show, M.tipFadeScale, 1))
        .opacity(M.clamp01(show * M.tipOpacityGain))
        .offset(x: x, y: y)
        .allowsHitTesting(pose.visible)
        .accessibilityElement(children: .contain)
        .accessibilityAddTraits(.isModal)
    }

    private var swipe: some Gesture {
        DragGesture(minimumDistance: 10, coordinateSpace: .global)
            .updating($pressing) { _, state, _ in state = true }
            .onChanged { v in
                let now = Date.now
                if !dragging {
                    dragging = true
                    // A mostly vertical drag is not a swipe of the tooltip.
                    dragIgnored = abs(v.translation.height) > abs(v.translation.width)
                    grab()
                    startX = motion.tipX.value(at: now)
                }
                guard !dragIgnored else { return }
                motion.drag(startX + Double(v.translation.width), at: now)
            }
            .onEnded { v in
                guard dragging else { return }
                defer { dragging = false }
                guard !dragIgnored else { return }
                motion.release(velocity: Double(v.velocity.width), at: .now)
            }
    }
}
