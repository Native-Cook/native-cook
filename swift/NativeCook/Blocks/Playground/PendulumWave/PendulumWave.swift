import CoreText
import SwiftUI
import UIKit

/// Fifteen brass-bob pendulums on a walnut frame, tuned to drift in and out of step through one
/// 30 s cycle: travelling waves, a snake, interleaved rows, apparent chaos, then one line again.
/// Drag sideways and a brass comb gathers the bobs and pulls them aside; let go to release them.
/// A tap turns the camera to the next view (3/4, top, side) with a morph.
struct PendulumWave: View {
    /// Pulls the bobs aside with the comb, lets go, tours the views through one cycle, catches them.
    var autoPlay = false

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.displayScale) private var displayScale
    @State private var engine = PendulumWaveEngine()
    @State private var viewName = PendulumWaveTuning.viewNames[PendulumWaveTuning.viewThreeQuarter]
    /// True while the drag is alive; it resets without `onEnded` when the system cancels the touch.
    @GestureState private var touching = false

    private typealias M = PendulumWaveMetrics
    private typealias T = PendulumWaveTuning

    var body: some View {
        // The block draws under the system bars and keeps its text clear of them itself.
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            stage(size: size, insets: insets)
                .frame(width: size.width, height: size.height)
                .offset(x: -insets.leading, y: -insets.top)
        }
    }

    private func stage(size: CGSize, insets: EdgeInsets) -> some View {
        let width = size.width
        let height = size.height
        let settings = PendulumWaveSettings(
            auto: autoPlay && !reduceMotion,
            reduced: reduceMotion,
            width: width,
            focal: min(width, height * 0.5) * M.focal,
            centreY: height * M.centreAt,
            onView: { index in viewName = T.viewNames[index] }
        )
        let readoutY = insets.top + 70
        let scale = displayScale
        return ZStack(alignment: .topLeading) {
            TimelineView(.animation) { timeline in
                let frame = engine.step(to: timeline.date, settings: settings)
                Canvas { context, _ in
                    // The frame's tick is read so each new frame redraws the canvas.
                    _ = frame.tick
                    let s = frame.sim
                    let v = PendulumWaveView(frame.camera, width: width, focal: settings.focal, centreY: settings.centreY)
                    let topness = M.smooth01((frame.camera.pitch - M.topFrom) / (M.topTo - M.topFrom))
                    PendulumWaveDrawing.drawScene(&context, v, s, width: width, height: height, topness: topness, scale: scale)
                    // The cycle readout: a hairline that fills over the cycle, and the seconds since release.
                    let running = frame.sinceRelease >= 0
                    let t = running ? frame.sinceRelease.truncatingRemainder(dividingBy: T.cycle) : 0
                    context.fill(Path(CGRect(x: M.side, y: readoutY, width: width - M.side * 2, height: 1)), with: .color(M.ink.opacity(0.14)))
                    context.fill(
                        Path(CGRect(x: M.side, y: readoutY - 0.5, width: (width - M.side * 2) * t / T.cycle, height: 2)),
                        with: .color(PendulumWaveDrawing.rgba(1, 214.0 / 255, 150.0 / 255, 0.85))
                    )
                    let label = running ? String(format: "%.1f s / %d s", t, Int(T.cycle)) : "— / \(Int(T.cycle)) s"
                    PendulumWaveDrawing.trailingText(&context, label, right: width - M.side, baseline: readoutY - 10, font: M.mono, color: M.inkSoft)
                }
                .frame(width: width, height: height)
            }

            VStack(alignment: .leading, spacing: 5) {
                Text("PENDULUM WAVE")
                    .font(.system(size: 11, weight: .bold))
                    .kerning(3)
                    .foregroundStyle(M.ink)
                Text("Fifteen bobs, one \(Int(T.cycle)) s cycle")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(M.inkSoft)
            }
            .padding(.horizontal, M.side)
            .padding(.top, insets.top + 18)

            VStack(spacing: 10) {
                HStack(spacing: 22) {
                    ForEach(T.viewNames, id: \.self) { name in
                        Text(name.uppercased())
                            .font(.system(size: 12, weight: .bold))
                            .kerning(1.6)
                            .foregroundStyle(name == viewName ? M.ink : M.inkFaint)
                    }
                }
                Text("Drag sideways to release · Tap to change view")
                    .font(.system(size: 12, weight: .medium))
                    .kerning(0.3)
                    .foregroundStyle(M.inkSoft)
            }
            .padding(.horizontal, M.side)
            .frame(width: width, height: height - insets.bottom - 22, alignment: .bottom)
        }
        .frame(width: width, height: height, alignment: .topLeading)
        .background(M.background)
        .clipped()
        .contentShape(Rectangle())
        .gesture(
            // One recogniser stands in for the race of a pan (after 10 pt of sideways travel) and a
            // tap (a touch lifted within 350 ms that never became a pan).
            DragGesture(minimumDistance: 0)
                .updating($touching) { _, state, _ in state = true }
                .onChanged { engine.touchMoved($0) }
                .onEnded { engine.touchEnded($0) }
        )
        .onChange(of: touching) { _, isTouching in
            if !isTouching { engine.touchCancelled() }
        }
        .accessibilityElement()
        .accessibilityLabel("Pendulum wave, \(viewName) view. Drag sideways to pull the pendulums aside and release them; tap to change the view.")
    }
}

enum PendulumWaveMetrics {
    // The camera's focal length, as a share of the smaller of the width and half the height, and
    // where the scene's middle sits on the screen (a share of the height).
    static let focal = 1.75
    static let centreAt = 0.5
    // The beam fades for the view from above between these pitches (degrees).
    static let topFrom = 40.0
    static let topTo = 72.0
    // After a touch the loop waits this long, then starts over.
    static let userHold = 4.0
    static let side = 24.0
    // A drag becomes the comb after this much sideways travel; a shorter touch is a tap.
    static let panSlop = 10.0
    static let tapTime = 0.35

    static let ink = Color(red: 0xED / 255, green: 0xE3 / 255, blue: 0xD2 / 255)
    static let inkSoft = ink.opacity(0.5)
    static let inkFaint = ink.opacity(0.28)
    static let background = Color(red: 0x0D / 255, green: 0x0E / 255, blue: 0x10 / 255)
    static let mono = CTFontCreateWithName("Menlo-Bold" as CFString, 12, nil)

    static func smooth01(_ p: Double) -> Double {
        let t = min(1, max(0, p))
        return t * t * (3 - 2 * t)
    }
}

struct PendulumWaveSettings {
    var auto: Bool
    var reduced: Bool
    var width: Double
    var focal: Double
    var centreY: Double
    var onView: (Int) -> Void
}

/// One frame to draw.
struct PendulumWaveFrame {
    let sim: PendulumWaveSim
    let camera: PendulumWaveCamera
    let sinceRelease: Double
    let tick: Int
}

/// The pendulums, the script's clock and the camera, stepped once a frame like the RN frame
/// callback, and the drag that works the comb.
@MainActor
final class PendulumWaveEngine {
    private typealias T = PendulumWaveTuning
    private typealias M = PendulumWaveMetrics

    private let sim = PendulumWaveSim()
    private var last: Date?
    private var clock = 0.0
    private var hold = 0.0
    private var wasAuto: Bool?
    private var reduced = false
    private var settings: PendulumWaveSettings?
    private var tick = 0
    // The camera: where the last morph started, the view it goes to, how far along (1 = there),
    // and the clock that sways the 3/4 view.
    private var camFrom = PendulumWaveTuning.viewCamera(PendulumWaveTuning.viewThreeQuarter, 0)
    private var viewTo = PendulumWaveTuning.viewThreeQuarter
    private var morph = 1.0
    private var orbit = 0.0
    // Screen points per world unit along the swing, for turning a drag into the comb's travel.
    private var axis = (1.0, 0.0)
    private var dragFrom = 0.0
    private var dragging = false
    private var touchStart: Date?
    private let grabFeel = UIImpactFeedbackGenerator(style: .soft)
    private let releaseFeel = UIImpactFeedbackGenerator(style: .light)
    private let selection = UISelectionFeedbackGenerator()

    /// The camera right now.
    private var camera: PendulumWaveCamera {
        let target = T.viewCamera(viewTo, orbit)
        return morph < 1 ? T.blendCamera(camFrom, target, morph) : target
    }

    private func setView(_ index: Int, byUser: Bool) {
        if index == viewTo { return }
        camFrom = camera
        viewTo = index
        morph = reduced ? 1 : 0
        let onView = settings?.onView
        DispatchQueue.main.async { onView?(index) }
        if byUser { selection.selectionChanged() }
    }

    func step(to date: Date, settings: PendulumWaveSettings) -> PendulumWaveFrame {
        self.settings = settings
        reduced = settings.reduced
        if wasAuto != settings.auto {
            wasAuto = settings.auto
            clock = 0
            orbit = 0
            hold = 0
        }
        // The script, the pendulums and the camera share one clock, capped so a slow frame cannot
        // skip ahead.
        let raw = last.map { date.timeIntervalSince($0) } ?? 1.0 / 60
        last = date
        let dt = min(max(raw, 0), T.maxFrame)
        if dt > 0 {
            // A comb in the user's hand keeps the script away.
            if dragging { hold = M.userHold }
            if hold > 0 {
                hold -= dt
                if hold <= 0 {
                    clock = 0
                    orbit = 0
                }
            } else if settings.auto {
                let before = clock
                let now = (before + dt).truncatingRemainder(dividingBy: T.loop)
                clock = now
                setView(sim.script(before: before, now: now), byUser: false)
            }
            if !settings.reduced { orbit = (orbit + dt).truncatingRemainder(dividingBy: T.loop) }
            if morph < 1 { morph = min(1, morph + dt / T.morph) }
            sim.step(dt)
            if sim.events != 0 {
                if sim.eventsByUser {
                    if sim.events & T.eventRelease != 0 {
                        releaseFeel.impactOccurred()
                    } else if sim.events & T.eventGrab != 0 {
                        grabFeel.impactOccurred()
                    }
                }
                sim.events = 0
                sim.eventsByUser = false
            }
            tick += 1
        }
        let cam = camera
        if settings.width > 0 {
            let v = PendulumWaveView(cam, width: settings.width, focal: settings.focal, centreY: settings.centreY)
            let a = v.project(0, 6, 0)
            let b = v.project(1, 6, 0)
            axis = (b.x - a.x, b.y - a.y)
        }
        return PendulumWaveFrame(sim: sim, camera: cam, sinceRelease: sim.sinceRelease, tick: tick)
    }

    func touchMoved(_ drag: DragGesture.Value) {
        if touchStart == nil { touchStart = drag.time }
        if !dragging {
            guard abs(drag.translation.width) > M.panSlop else { return }
            // Drag sideways: the comb takes the bobs where they are on average, so a moving row
            // is caught, not yanked, and follows the finger along the swing.
            hold = M.userHold
            var mean = 0.0
            for x in sim.x { mean += x / Double(sim.x.count) }
            dragFrom = mean
            dragging = true
            sim.grab(mean, byUser: true)
        }
        hold = M.userHold
        let per = axis.0 * axis.0 + axis.1 * axis.1
        let along = per > 0 ? (drag.translation.width * axis.0 + drag.translation.height * axis.1) / per : 0
        sim.move(dragFrom + along)
    }

    /// The drag ended without `onEnded` (the system took the touch): the comb lets go, so the
    /// script is not held off for ever. Deferred a turn so a normal `onEnded` always wins.
    func touchCancelled() {
        Task { @MainActor [weak self] in self?.cancelNow() }
    }

    private func cancelNow() {
        guard touchStart != nil || dragging else { return }
        touchStart = nil
        if dragging {
            sim.release(byUser: true, timed: true)
            dragging = false
        }
    }

    func touchEnded(_ drag: DragGesture.Value) {
        defer { touchStart = nil }
        if dragging {
            sim.release(byUser: true, timed: true)
            dragging = false
            return
        }
        // Tap: the next view.
        if let start = touchStart, drag.time.timeIntervalSince(start) <= M.tapTime {
            hold = M.userHold
            setView((viewTo + 1) % T.viewNames.count, byUser: true)
        }
    }
}
