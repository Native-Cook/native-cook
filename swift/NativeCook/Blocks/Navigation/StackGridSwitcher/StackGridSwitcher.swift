import SwiftUI
import UIKit

struct StackGridSwitcherSleeve: Equatable {
    enum Figure { case chevron, disc, rings, stripes, square, dots, wave, triangle }

    var title: String
    var artist: String
    /// The cover's gradient, top to bottom.
    var colors: [Color]
    /// The one figure drawn on the cover, and its colour.
    var figure: Figure
    var tint: Color
    /// The sleeve's front edge, where the title is written.
    var edge: Color
    /// The edge is light: its title is written in black.
    var light = false
}

struct StackGridSwitcherSection {
    var title: String
    var items: [StackGridSwitcherSleeve]
}

/// A music library with two views of the same sleeves. The stack shows them lying flat, one above
/// the other, in perspective: above eye level you look at their underside, below it at their covers,
/// and scrolling moves them through that. The toggle at the top right blows the stack apart and
/// assembles a flat grid of the covers, and back.
struct StackGridSwitcher: View {
    /// The two halves of the library; the segmented control chooses one.
    var sections: [StackGridSwitcherSection] = StackGridSwitcherSection.samples
    /// Starts on the grid instead of the stack.
    var initialGrid = false
    /// Presses the view toggle by itself, to the grid and back, every `loop` seconds.
    var autoPlay = false
    /// Called with a sleeve or a cover that was tapped.
    var onSelect: ((StackGridSwitcherSleeve) -> Void)? = nil
    /// Called when the toggle changes the view.
    var onViewChange: ((Bool) -> Void)? = nil
    var onSort: (() -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    // The view shown or arriving; nil until the toggle is pressed.
    @State private var grid: Bool?
    // The segment chosen, and the one whose sleeves are drawn: it follows half a swap later.
    @State private var segment = 0
    @State private var shown = 0
    // Changes on every touch, which restarts the autoPlay loop behind a pause.
    @State private var touches = 0
    // Runs from 0 (the stack) to `switchEnd` (the grid) and back; nil until the toggle is pressed.
    @State private var phase: Double?
    @State private var scrollY = 0.0
    // Changes when the lists are to be scrolled back to the top.
    @State private var scrollHome = 0
    // The sleeve under a finger, and how far it has come forward.
    @State private var pressed = -1
    @State private var press = 0.0
    // 0 to 1 across a change of segment; 0 at rest.
    @State private var swap = 0.0
    // The toggle is pressed in.
    @State private var pushed = false
    @State private var dragging = false
    // A switch is under way: the toggle waits for it.
    @State private var switching = false
    // Tells one switch from the next, for the moment it ends.
    @State private var run = 0

    private typealias M = StackGridSwitcherMotion

    private struct LoopKey: Equatable {
        let on: Bool
        let touches: Int
    }

    private var isGrid: Bool { grid ?? initialGrid }
    private var currentPhase: Double { phase ?? (initialGrid ? M.switchEnd : 0) }

    var body: some View {
        GeometryReader { proxy in
            StackGridSwitcherScene(
                phase: currentPhase, swap: swap, press: press,
                toGrid: isGrid, pressed: pressed, pushed: pushed, segment: segment,
                sections: sections, items: sections[min(shown, sections.count - 1)].items,
                still: reduceMotion, frame: StackGridSwitcherFrame(proxy: proxy),
                scrollY: scrollY, scrollHome: scrollHome,
                onFlip: {
                    touched()
                    flip(to: !isGrid, auto: false)
                },
                onChoose: { index in
                    touched()
                    choose(index)
                },
                onSort: {
                    touched()
                    onSort?()
                },
                onSelect: { item in
                    touched()
                    UISelectionFeedbackGenerator().selectionChanged()
                    onSelect?(item)
                },
                onLift: { index, down in
                    if down {
                        touched()
                        pressed = index
                    }
                    withAnimation(.easeInOut(duration: M.pressFor)) { press = down ? 1 : 0 }
                },
                onScroll: { scrollY = $0 }
            )
            .ignoresSafeArea()
            // A scroll of either view is a touch too.
            .simultaneousGesture(
                DragGesture(minimumDistance: 8)
                    .onChanged { _ in
                        if !dragging {
                            dragging = true
                            touched()
                        }
                    }
                    .onEnded { _ in dragging = false }
            )
        }
        .preferredColorScheme(.dark)
        .task(id: LoopKey(on: autoPlay, touches: touches)) { await loop() }
    }

    // MARK: The moves

    private func still(_ change: () -> Void) {
        var transaction = Transaction()
        transaction.disablesAnimations = true
        withTransaction(transaction, change)
    }

    /// The toggle: the view on screen leaves and the other one assembles.
    private func flip(to next: Bool, auto: Bool) {
        guard !switching, next != isGrid else { return }
        if !auto { UISelectionFeedbackGenerator().selectionChanged() }
        pushed = true
        Task {
            try? await Task.sleep(for: .milliseconds(90))
            pushed = false
        }
        onViewChange?(next)
        // The clock starts again from 0 for the new direction without the phase moving.
        still {
            phase = currentPhase
            grid = next
        }
        switching = true
        run += 1
        let id = run
        withAnimation(.linear(duration: M.switchEnd), completionCriteria: .logicallyComplete) {
            phase = next ? M.switchEnd : 0
        } completion: {
            if id == run { switching = false }
        }
    }

    /// The segmented control: the capsule slides, and what is shown is swapped while it is out of sight.
    private func choose(_ next: Int, auto: Bool = false) {
        guard next != segment, swap == 0 else { return }
        if !auto { UISelectionFeedbackGenerator().selectionChanged() }
        segment = next
        withAnimation(.linear(duration: M.swap / 2), completionCriteria: .logicallyComplete) {
            swap = 0.5
        } completion: {
            shown = next
            scrollHome += 1
            withAnimation(.linear(duration: M.swap / 2), completionCriteria: .logicallyComplete) {
                swap = 1
            } completion: {
                still { swap = 0 }
            }
        }
    }

    /// The state every loop starts from, set at once.
    private func rest() {
        run += 1
        still {
            switching = false
            phase = initialGrid ? M.switchEnd : 0
            grid = initialGrid
            swap = 0
            press = 0
            pushed = false
            segment = 0
            shown = 0
        }
        if scrollY != 0 { scrollHome += 1 }
    }

    /// A touch takes over from the autoPlay loop, which starts again from rest a while later.
    private func touched() {
        touches += 1
    }

    // MARK: The loop

    @MainActor
    private func loop() async {
        guard autoPlay else { return }
        let time = ContinuousClock()
        do {
            if touches > 0 {
                // After a touch: back to the first view of the first segment, then the loop again.
                try await time.sleep(for: .seconds(M.userHold), tolerance: .zero)
                while switching || swap > 0 { try await time.sleep(for: .milliseconds(100)) }
                if segment != 0 {
                    choose(0, auto: true)
                    try await time.sleep(for: .seconds(M.swap + 0.1), tolerance: .zero)
                }
                if isGrid != initialGrid {
                    flip(to: initialGrid, auto: true)
                    try await time.sleep(for: .seconds(M.switchEnd + 0.1), tolerance: .zero)
                }
            }
            var origin = time.now
            while true {
                rest()
                try await time.sleep(until: origin + .seconds(M.toGridAt), tolerance: .zero)
                flip(to: !initialGrid, auto: true)
                try await time.sleep(until: origin + .seconds(M.toStackAt), tolerance: .zero)
                flip(to: initialGrid, auto: true)
                origin += .seconds(M.loop)
                try await time.sleep(until: origin, tolerance: .zero)
            }
        } catch {}
    }
}

// MARK: - Numbers

/// The timeline and the curves, the same numbers as the React Native and Compose versions.
enum StackGridSwitcherMotion {
    // A sleeve lies flat. Its far edge is drawn at this scale, and its cover rises (or hangs) by up
    // to `liftMax` points, reached `reach` points from eye level; at eye level only the front edge shows.
    static let far = 0.7
    static let liftMax = 71.0
    static let reach = 300.0
    static let shade = 0.3
    static let pressScale = 0.05
    static let pressFor = 0.14
    static let gridCamera = 800.0

    // A switch, in seconds from the tap: the view on screen leaves for `leave`, the page is empty for
    // a beat, and from `arrive` the other view assembles on springs, one piece after another.
    static let leave = 0.32
    static let arrive = 0.6
    static let spread = 120.0
    static let leaveScale = 0.8
    static let leaveBlur = 16.0
    static let arriveBlur = 12.0
    static let arriveClear = 0.4
    static let sleeveStagger = 0.035
    static let sleeveRanks = 8
    static let sleeveFrom = 0.6
    static let labelAt = 1.05
    static let labelIn = 0.3
    static let coverStagger = 0.03
    static let coverRanks = 20
    static let coverTilt = 70 * Double.pi / 180
    static let coverShift = 60.0
    static let coverLeave = 0.22
    static let coverLeaveStagger = 0.01
    static let pieceIn = 0.12
    // The spring of every arriving piece (mass 1, stiffness 220, damping 20), as a function of time:
    // 1 - e^(-decay t) (cos(rate t) + decay / rate sin(rate t)); it is taken as settled after `springFor`.
    static let springDecay = 10.0
    static let springRate = 10.954
    static let springFor = 0.7
    static let glyphFrom = 0.25
    static let glyphTo = 0.55
    // The clock stops here, and the toggle takes another press.
    static let switchEnd = 1.75
    // With reduced motion the two views cross-fade.
    static let fade = 0.25
    // Changing the segment: what is shown fades and blurs out, the other half comes in the same way.
    static let swap = 0.5
    static let swapBlur = 10.0
    static let segmentSlide = 0.25

    // The loop: the toggle is pressed to go to the grid, and again to come back.
    static let loop = 9.0
    static let toGridAt = 1.2
    static let toStackAt = 5.0
    static let userHold = 3.0

    static func clamp01(_ x: Double) -> Double { min(max(x, 0), 1) }

    static func smooth(_ x: Double, _ from: Double, _ to: Double) -> Double {
        let t = clamp01((x - from) / (to - from))
        return t * t * (3 - 2 * t)
    }

    static func easeIn(_ t: Double) -> Double {
        let c = clamp01(t)
        return c * c * c
    }

    /// Where an arriving piece is, `t` seconds after it set off: 0 to 1 with one small overshoot.
    static func spring(_ t: Double) -> Double {
        if t <= 0 { return 0 }
        if t >= springFor { return 1 }
        return 1 - exp(-springDecay * t) * (cos(springRate * t) + springDecay / springRate * sin(springRate * t))
    }

    /// How far out of focus a view is, against `arriveBlur`: going out as it leaves, coming in as it assembles.
    static func blur(_ t: Double, leaving: Bool) -> Double {
        leaving ? leaveBlur / arriveBlur * easeIn(t / leave) : 1 - smooth(t, arrive, arrive + arriveClear)
    }

    /// The turn of a flat sleeve's cover about its front edge, and the camera that draws it, `h` points below eye level.
    static func tilt(_ h: Double, size: Double) -> (angle: Double, camera: Double) {
        let near = 1 - min(abs(h) / reach, 1)
        let lift = liftMax * (1 - near * near)
        let angle = acos(min(lift / (size * far), 1))
        // Past a right angle the cover hangs below the edge and is seen from underneath.
        return (h >= 0 ? angle : .pi - angle, far * size * sin(angle) / (1 - far))
    }
}

/// Where things are on a screen of a given size.
struct StackGridSwitcherFrame {
    // The top bar, under the safe area's top edge.
    static let barTop = 6.0
    static let bar = 35.0
    static let barSide = 22.0
    static let segment = 81.0
    static let segmentInset = 2.5
    static let dockWidth = 188.0
    static let dockHeight = 55.0
    // The stack: the middle of the first sleeve's front edge under the safe area's top edge, the
    // distance from one sleeve to the next, the edge's height and the room at the sides.
    static let stackLead = 66.0
    static let pitch = 86.0
    static let strip = 14.0
    static let stackSide = 36.0
    // The eye is level with this fraction of the screen's height.
    static let eyeLevel = 0.453
    // The grid.
    static let columns = 3
    static let gridTop = 45.0
    static let gridSide = 4.5
    static let gridGap = 6.0

    let width: Double
    let height: Double
    let top: Double
    let bottom: Double

    init(proxy: GeometryProxy) {
        let insets = proxy.safeAreaInsets
        width = proxy.size.width + insets.leading + insets.trailing
        height = proxy.size.height + insets.top + insets.bottom
        top = insets.top
        bottom = max(insets.bottom, 12)
    }

    var stackTop: Double { top + Self.stackLead }
    var eye: Double { height * Self.eyeLevel }
    var sleeve: Double { width - Self.stackSide * 2 }
    var cell: Double { (width - Self.gridSide * 2 - Self.gridGap * Double(Self.columns - 1)) / Double(Self.columns) }
}

// MARK: - The screen

private struct StackGridSwitcherScene: View, Animatable {
    var phase: Double
    var swap: Double
    var press: Double
    let toGrid: Bool
    let pressed: Int
    let pushed: Bool
    let segment: Int
    let sections: [StackGridSwitcherSection]
    let items: [StackGridSwitcherSleeve]
    let still: Bool
    let frame: StackGridSwitcherFrame
    let scrollY: Double
    let scrollHome: Int
    let onFlip: () -> Void
    let onChoose: (Int) -> Void
    let onSort: () -> Void
    let onSelect: (StackGridSwitcherSleeve) -> Void
    let onLift: (Int, Bool) -> Void
    let onScroll: (Double) -> Void

    private typealias M = StackGridSwitcherMotion
    private typealias F = StackGridSwitcherFrame

    private enum Palette {
        static let page = Color(hex: 0x9198AC)
        static let pageLight = Color(hex: 0xB4B8C6)
        static let segmentFill = Color.white.opacity(0.25)
        static let segmentInk = Color(hex: 0x111111)
        static let segmentMute = Color.white.opacity(0.85)
        static let dockFill = Color.white.opacity(0.4)
        static let dockInk = Color(hex: 0x1A1A1E)
        static let dockMute = Color(hex: 0x7C7C88)
    }

    private struct ScrollKey: PreferenceKey {
        static let defaultValue = 0.0
        static func reduce(value: inout Double, nextValue: () -> Double) { value = nextValue() }
    }

    var animatableData: AnimatablePair<Double, AnimatablePair<Double, Double>> {
        get { AnimatablePair(phase, AnimatablePair(swap, press)) }
        set {
            phase = newValue.first
            swap = newValue.second.first
            press = newValue.second.second
        }
    }

    /// Seconds since the toggle was pressed; `switchEnd` at rest.
    private var clock: Double { toGrid ? phase : M.switchEnd - phase }

    var body: some View {
        let width = frame.width
        let height = frame.height
        let there = M.smooth(abs(swap - 0.5) * 2, 0, 1)
        ZStack(alignment: .topLeading) {
            RadialGradient(
                colors: [Palette.pageLight, Palette.page],
                center: UnitPoint(x: 0.5, y: 0.45), startRadius: 0, endRadius: height * 0.55
            )

            // Both views, through a change of segment.
            ZStack(alignment: .topLeading) {
                stack
                    .blur(radius: still ? 0 : M.arriveBlur * M.blur(clock, leaving: toGrid))
                    .allowsHitTesting(!toGrid)
                    .accessibilityHidden(toGrid)
                grid
                    .blur(radius: still ? 0 : M.arriveBlur * M.blur(clock, leaving: !toGrid))
                    .allowsHitTesting(toGrid)
                    .accessibilityHidden(!toGrid)
            }
            .frame(width: width, height: height, alignment: .topLeading)
            .scaleEffect(still ? 1 : 0.97 + 0.03 * there)
            .blur(radius: still ? 0 : M.swapBlur * (1 - there))
            .opacity(there)

            bar
                .frame(width: width - F.barSide * 2, height: F.bar)
                .offset(x: F.barSide, y: frame.top + F.barTop)

            dock
                .offset(x: (width - F.dockWidth) / 2, y: height - frame.bottom - F.dockHeight)
        }
        .frame(width: width, height: height, alignment: .topLeading)
    }

    // MARK: The stack

    /// Where a sleeve's front edge is, how big and how visible the sleeve is, and its title.
    private func sleevePose(_ index: Int) -> (y: Double, scale: Double, opacity: Double, label: Double) {
        let t = clock
        let base = frame.stackTop + Double(index) * F.pitch - scrollY
        if still {
            let there = M.smooth(t, 0, M.fade)
            return (base, 1, toGrid ? 1 - there : there, 1)
        }
        if toGrid {
            // Leaving: the sleeves above eye level rise and the others sink, shrinking.
            let u = M.easeIn(t / M.leave)
            return (base + (base < frame.eye ? -M.spread : M.spread) * u, 1 - (1 - M.leaveScale) * u, 1 - M.smooth(u, 0.4, 1), 1)
        }
        // Arriving: from eye level out to their shelves, the middle ones first.
        let middle = Int(((frame.eye - frame.stackTop + scrollY) / F.pitch).rounded())
        let since = t - M.arrive - Double(min(abs(index - middle), M.sleeveRanks)) * M.sleeveStagger
        // A sleeve whose shelf is off the screen is put there without the trip across it.
        let a = base < -F.pitch || base > frame.height + F.pitch ? 1 : M.spring(since)
        return (frame.eye + (base - frame.eye) * a, M.sleeveFrom + (1 - M.sleeveFrom) * a, M.smooth(since, 0, M.pieceIn), M.smooth(t, M.labelAt, M.labelAt + M.labelIn))
    }

    /// A square turned about the middle of its bottom edge, drawn by a camera `camera` points in front of that edge.
    private static func turned(angle: Double, camera: Double, scale: Double, size: Double, pivotY: Double) -> ProjectionTransform {
        var turn = CATransform3DIdentity
        turn.m34 = -1 / camera
        turn = CATransform3DRotate(turn, angle, 1, 0, 0)
        turn = CATransform3DScale(turn, scale, scale, 1)
        let toPivot = CATransform3DMakeTranslation(-size / 2, -pivotY, 0)
        let back = CATransform3DMakeTranslation(size / 2, pivotY, 0)
        return ProjectionTransform(CATransform3DConcat(CATransform3DConcat(toPivot, turn), back))
    }

    private var stack: some View {
        let width = frame.width
        let height = frame.height
        let size = frame.sleeve
        return ZStack(alignment: .topLeading) {
            ZStack(alignment: .topLeading) {
                ForEach(Array(items.enumerated()), id: \.offset) { index, item in
                    let pose = sleevePose(index)
                    let turn = M.tilt(pose.y - frame.eye, size: size)
                    let scale = pose.scale * (pressed == index ? 1 + M.pressScale * press : 1)
                    StackGridSwitcherCover(item: item, shaded: true)
                        .equatable()
                        .frame(width: size, height: size)
                        .projectionEffect(Self.turned(angle: turn.angle, camera: turn.camera, scale: scale, size: size, pivotY: size))
                        .offset(x: F.stackSide, y: pose.y - size)
                        .opacity(pose.opacity)
                    (Text(item.title).fontWeight(.heavy) + Text(" \(item.artist)"))
                        .font(.system(size: 8.5))
                        .lineLimit(1)
                        .foregroundStyle(item.light ? Color.black : Color.white)
                        .opacity(pose.label)
                        .frame(width: size, height: F.strip)
                        .background(item.edge)
                        .scaleEffect(scale)
                        .offset(x: F.stackSide, y: pose.y - F.strip / 2)
                        .opacity(pose.opacity)
                }
            }
            .frame(width: width, height: height, alignment: .topLeading)
            .allowsHitTesting(false)

            // The sleeves are drawn from the scroll position; what scrolls is a column of clear rows that take the taps.
            ScrollViewReader { reader in
                ScrollView(.vertical, showsIndicators: false) {
                    VStack(spacing: 0) {
                        Color.clear.frame(height: frame.stackTop - F.pitch / 2).id("top")
                        ForEach(Array(items.enumerated()), id: \.offset) { index, item in
                            Button {
                                onSelect(item)
                            } label: {
                                Color.clear.frame(height: F.pitch).contentShape(Rectangle())
                            }
                            .buttonStyle(StackGridSwitcherPress { onLift(index, $0) })
                            .accessibilityLabel("\(item.title), \(item.artist)")
                        }
                        Color.clear.frame(height: height - frame.eye - F.pitch / 2)
                    }
                    .frame(width: width)
                    .background(
                        GeometryReader { inner in
                            Color.clear.preference(key: ScrollKey.self, value: -inner.frame(in: .named("StackGridSwitcherStack")).minY)
                        }
                    )
                }
                .coordinateSpace(name: "StackGridSwitcherStack")
                .scrollDisabled(toGrid)
                .onPreferenceChange(ScrollKey.self) { onScroll($0) }
                .onChange(of: scrollHome) { reader.scrollTo("top", anchor: .top) }
            }
        }
        .frame(width: width, height: height, alignment: .topLeading)
    }

    // MARK: The grid

    /// How a cover of the grid is tipped, moved and shown.
    private func coverPose(_ index: Int) -> (tilt: Double, shift: Double, opacity: Double) {
        let t = clock
        if still {
            let there = M.smooth(t, 0, M.fade)
            return (0, 0, toGrid ? there : 1 - there)
        }
        if toGrid {
            // Arriving in reading order: tipped back and off to the top left, then flat in its cell.
            let since = t - M.arrive - Double(min(index, M.coverRanks)) * M.coverStagger
            let b = M.spring(since)
            return (M.coverTilt * (1 - b), -M.coverShift * (1 - b), M.smooth(since, 0, M.pieceIn))
        }
        // Leaving in reverse order: tipping back.
        let v = M.easeIn((t - Double(min(items.count - 1 - index, M.coverRanks)) * M.coverLeaveStagger) / M.coverLeave)
        return (M.coverTilt * v, -M.coverShift * 0.5 * v, 1 - M.smooth(v, 0.3, 1))
    }

    private var grid: some View {
        let cell = frame.cell
        let rows = (items.count + F.columns - 1) / F.columns
        let content = frame.top + F.gridTop + Double(rows) * (cell + F.gridGap) + frame.bottom + F.dockHeight + 12
        return ScrollViewReader { reader in
            ScrollView(.vertical, showsIndicators: false) {
                ZStack(alignment: .topLeading) {
                    Color.clear.frame(width: frame.width, height: 1).id("top")
                    ForEach(Array(items.enumerated()), id: \.offset) { index, item in
                        let pose = coverPose(index)
                        Button {
                            onSelect(item)
                        } label: {
                            StackGridSwitcherCover(item: item, shaded: false)
                                .equatable()
                                .frame(width: cell, height: cell)
                        }
                        .buttonStyle(StackGridSwitcherSink())
                        .accessibilityLabel("\(item.title), \(item.artist)")
                        .projectionEffect(Self.turned(angle: pose.tilt, camera: M.gridCamera, scale: 1, size: cell, pivotY: cell / 2))
                        .offset(
                            x: F.gridSide + Double(index % F.columns) * (cell + F.gridGap) + pose.shift,
                            y: frame.top + F.gridTop + Double(index / F.columns) * (cell + F.gridGap) + pose.shift
                        )
                        .opacity(pose.opacity)
                    }
                }
                .frame(width: frame.width, height: content, alignment: .topLeading)
            }
            .scrollDisabled(!toGrid)
            .onChange(of: scrollHome) { reader.scrollTo("top", anchor: .top) }
        }
        .frame(width: frame.width, height: frame.height)
    }

    // MARK: The bar and the dock

    private static func stroke(_ width: Double) -> StrokeStyle {
        StrokeStyle(lineWidth: width, lineCap: .round, lineJoin: .round)
    }

    private var bar: some View {
        // The toggle shows the view that is on: lines for the stack, squares for the grid.
        let m = M.smooth(clock, M.glyphFrom, M.glyphTo)
        let squares = toGrid ? m : 1 - m
        return HStack(spacing: 0) {
            Button(action: onSort) {
                StackGridSwitcherGlyph(kind: .sort).stroke(.white, style: Self.stroke(1.8)).frame(width: 24, height: 24)
                    .padding(10).contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Sort")
            .padding(-10)

            Spacer(minLength: 0)

            ZStack(alignment: .topLeading) {
                Capsule().fill(Palette.segmentFill)
                Capsule()
                    .fill(.white)
                    .frame(width: F.segment - F.segmentInset, height: F.bar - F.segmentInset * 2)
                    .offset(x: F.segmentInset + Double(segment) * (F.segment - F.segmentInset), y: F.segmentInset)
                    .animation(.timingCurve(0.23, 1, 0.32, 1, duration: M.segmentSlide), value: segment)
                HStack(spacing: 0) {
                    ForEach(Array(sections.prefix(2).enumerated()), id: \.offset) { index, section in
                        Button {
                            onChoose(index)
                        } label: {
                            Text(section.title)
                                .font(.system(size: 14, weight: .semibold))
                                .lineLimit(1)
                                .foregroundStyle(segment == index ? Palette.segmentInk : Palette.segmentMute)
                                .animation(.easeInOut(duration: M.segmentSlide), value: segment)
                                .frame(width: F.segment, height: F.bar)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityAddTraits(segment == index ? .isSelected : [])
                    }
                }
            }
            .frame(width: F.segment * 2, height: F.bar)

            Spacer(minLength: 0)

            Button(action: onFlip) {
                ZStack {
                    StackGridSwitcherGlyph(kind: .lines).stroke(.white, style: Self.stroke(1.8))
                        .opacity(1 - squares)
                        .scaleEffect(0.7 + 0.3 * (1 - squares))
                    StackGridSwitcherGlyph(kind: .squares).stroke(.white, style: Self.stroke(1.6))
                        .opacity(squares)
                        .scaleEffect(0.7 + 0.3 * squares)
                }
                .frame(width: 24, height: 24)
                .scaleEffect(pushed ? 0.86 : 1)
                .opacity(pushed ? 0.65 : 1)
                .animation(.easeOut(duration: pushed ? 0.09 : 0.2), value: pushed)
                .padding(10)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(toGrid ? "Show as a stack" : "Show as a grid")
            .padding(-10)
        }
    }

    private var dock: some View {
        HStack(spacing: 0) {
            ZStack {
                StackGridSwitcherGlyph(kind: .sleeves).stroke(Palette.dockInk, style: Self.stroke(1.8))
                StackGridSwitcherGlyph(kind: .sleeve).fill(Palette.dockInk)
            }
            .frame(width: 24, height: 24)
            .frame(maxWidth: .infinity)
            StackGridSwitcherGlyph(kind: .face).stroke(Palette.dockMute, style: Self.stroke(1.6))
                .overlay(StackGridSwitcherGlyph(kind: .eyes).fill(Palette.dockMute))
                .frame(width: 24, height: 24)
                .frame(maxWidth: .infinity)
            StackGridSwitcherGlyph(kind: .list).stroke(Palette.dockMute, style: Self.stroke(1.6))
                .frame(width: 24, height: 24)
                .frame(maxWidth: .infinity)
        }
        .padding(.horizontal, 20)
        .frame(width: F.dockWidth, height: F.dockHeight)
        .background(Capsule().fill(.ultraThinMaterial).overlay(Capsule().fill(Palette.dockFill)).environment(\.colorScheme, .light))
        .accessibilityHidden(true)
    }
}

// MARK: - Parts

/// Tells when a row of the stack is under a finger.
private struct StackGridSwitcherPress: ButtonStyle {
    let onChange: (Bool) -> Void

    func makeBody(configuration: Configuration) -> some View {
        configuration.label.onChange(of: configuration.isPressed) { _, down in onChange(down) }
    }
}

/// A cover of the grid sinks a little while it is pressed.
private struct StackGridSwitcherSink: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.96 : 1)
            .animation(.easeOut(duration: StackGridSwitcherMotion.pressFor), value: configuration.isPressed)
    }
}

/// A cover made in code: a gradient and one figure, in a box of 100 units.
private struct StackGridSwitcherCover: View, Equatable {
    let item: StackGridSwitcherSleeve
    /// The far half of a sleeve lying flat is in shade.
    let shaded: Bool

    var body: some View {
        Canvas { context, size in
            var context = context
            context.scaleBy(x: size.width / 100, y: size.height / 100)
            let box = CGRect(x: 0, y: 0, width: 100, height: 100)
            context.fill(Path(box), with: .linearGradient(Gradient(colors: item.colors), startPoint: .zero, endPoint: CGPoint(x: 0, y: 100)))
            let tint = GraphicsContext.Shading.color(item.tint)
            switch item.figure {
            case .chevron:
                var path = Path()
                path.move(to: CGPoint(x: 28, y: 32))
                path.addLine(to: CGPoint(x: 50, y: 68))
                path.addLine(to: CGPoint(x: 72, y: 32))
                context.stroke(path, with: tint, style: StrokeStyle(lineWidth: 10, lineCap: .round, lineJoin: .round))
            case .disc:
                context.fill(Path(ellipseIn: CGRect(x: 28, y: 28, width: 44, height: 44)), with: tint)
            case .rings:
                for r in [14.0, 24, 34] {
                    context.stroke(Path(ellipseIn: CGRect(x: 50 - r, y: 50 - r, width: r * 2, height: r * 2)), with: tint, lineWidth: 1.5)
                }
            case .stripes:
                for y in [26.0, 38, 50, 62, 74] {
                    context.fill(Path(CGRect(x: 22, y: y - 2.5, width: 56, height: 5)), with: tint)
                }
            case .square:
                context.fill(Path(CGRect(x: 34, y: 34, width: 32, height: 32)), with: tint)
            case .dots:
                for y in [26.0, 42, 58, 74] {
                    for x in [26.0, 42, 58, 74] {
                        context.fill(Path(ellipseIn: CGRect(x: x - 3, y: y - 3, width: 6, height: 6)), with: tint)
                    }
                }
            case .wave:
                var path = Path()
                path.move(to: CGPoint(x: 14, y: 54))
                path.addCurve(to: CGPoint(x: 50, y: 54), control1: CGPoint(x: 26, y: 34), control2: CGPoint(x: 38, y: 34))
                path.addCurve(to: CGPoint(x: 86, y: 54), control1: CGPoint(x: 62, y: 74), control2: CGPoint(x: 74, y: 74))
                context.stroke(path, with: tint, style: StrokeStyle(lineWidth: 3, lineCap: .round))
            case .triangle:
                var path = Path()
                path.move(to: CGPoint(x: 50, y: 26))
                path.addLine(to: CGPoint(x: 76, y: 72))
                path.addLine(to: CGPoint(x: 24, y: 72))
                path.closeSubpath()
                context.fill(path, with: tint)
            }
            if shaded {
                let shade = Gradient(colors: [Color.black.opacity(StackGridSwitcherMotion.shade), Color.black.opacity(0)])
                context.fill(Path(box), with: .linearGradient(shade, startPoint: .zero, endPoint: CGPoint(x: 0, y: 100)))
            }
        }
    }
}

/// The glyphs of the bar and the dock, drawn in boxes of 24 points.
private struct StackGridSwitcherGlyph: Shape {
    enum Kind { case sort, lines, squares, sleeves, sleeve, face, eyes, list }
    let kind: Kind

    func path(in rect: CGRect) -> Path {
        var path = Path()
        switch kind {
        case .sort:
            Self.lines(&path, [[7, 20], [7, 4]], [[3, 8], [7, 4]], [[17, 4], [17, 20]], [[21, 16], [17, 20]])
        case .lines:
            Self.lines(&path, [[5, 6], [19, 6]], [[5, 10], [19, 10]], [[5, 14], [19, 14]], [[5, 18], [19, 18]])
        case .squares:
            for origin in [[5.0, 5], [14, 5], [5, 14], [14, 14]] {
                path.addRect(CGRect(x: origin[0], y: origin[1], width: 5, height: 5))
            }
        case .sleeves:
            Self.lines(&path, [[5, 4], [5, 20]], [[9, 4], [9, 20]])
        case .sleeve:
            Self.lines(&path, [[12.5, 4], [19, 6], [19, 18], [12.5, 20]])
            path.closeSubpath()
        case .face:
            path.addEllipse(in: CGRect(x: 3.8, y: 3.8, width: 16.4, height: 16.4))
            path.move(to: CGPoint(x: 8.5, y: 14))
            path.addCurve(to: CGPoint(x: 12, y: 16.4), control1: CGPoint(x: 9.4, y: 15.6), control2: CGPoint(x: 10.6, y: 16.4))
            path.addCurve(to: CGPoint(x: 15.5, y: 14), control1: CGPoint(x: 13.4, y: 16.4), control2: CGPoint(x: 14.6, y: 15.6))
        case .eyes:
            path.addEllipse(in: CGRect(x: 8.2, y: 9, width: 2, height: 2))
            path.addEllipse(in: CGRect(x: 13.8, y: 9, width: 2, height: 2))
        case .list:
            Self.lines(&path, [[4, 6], [16, 6]], [[4, 11], [13, 11]], [[4, 16], [10, 16]], [[17.5, 16.5], [17.5, 9], [20.5, 10]])
            path.addEllipse(in: CGRect(x: 13.9, y: 14.9, width: 3.8, height: 3.8))
        }
        return path.applying(CGAffineTransform(scaleX: rect.width / 24, y: rect.height / 24))
    }

    private static func lines(_ path: inout Path, _ strokes: [[Double]]...) {
        for stroke in strokes {
            path.move(to: CGPoint(x: stroke[0][0], y: stroke[0][1]))
            for point in stroke.dropFirst() {
                path.addLine(to: CGPoint(x: point[0], y: point[1]))
            }
        }
    }
}

// MARK: - Sample library

extension StackGridSwitcherSection {
    private static func sleeve(
        _ title: String, _ artist: String, _ top: UInt32, _ bottom: UInt32, _ figure: StackGridSwitcherSleeve.Figure, _ tint: UInt32, _ edge: UInt32, light: Bool = false
    ) -> StackGridSwitcherSleeve {
        StackGridSwitcherSleeve(title: title, artist: artist, colors: [Color(hex: top), Color(hex: bottom)], figure: figure, tint: Color(hex: tint), edge: Color(hex: edge), light: light)
    }

    static let samples: [StackGridSwitcherSection] = [
        StackGridSwitcherSection(title: "Albums", items: [
            sleeve("Open Window", "Lowe", 0xFFFFFF, 0xD9DBE0, .chevron, 0x1F4FD8, 0xC9CBD1, light: true),
            sleeve("Night Bus", "Ada Renn", 0x0B0B0C, 0x4A2E1C, .disc, 0xF28A2E, 0x1A1310),
            sleeve("Quiet Arcs", "Tomas Vale", 0xE6E6E8, 0xCFCFD4, .rings, 0x111111, 0xB9B9BF, light: true),
            sleeve("Paper Weather", "Jae Min", 0x7B3FE4, 0x2A1E7A, .stripes, 0xFF8CC6, 0x4B2FA6),
            sleeve("Red Room", "Ostrow", 0xFFFFFF, 0xF1F1F1, .square, 0xD92B2B, 0xE4E4E6, light: true),
            sleeve("Gold Standard", "Mara Wilde", 0x0A0A0A, 0x1B1708, .dots, 0xD8B24A, 0x2A2412),
            sleeve("Blue Hour", "Dean Laurel", 0x1E8C8C, 0x06181C, .wave, 0xD6F2EE, 0x123C40),
            sleeve("Half Light", "Noor Sato", 0x2B2B2F, 0x121214, .rings, 0xFFFFFF, 0x0E0E10),
            sleeve("Soft Machines", "Iri", 0xB9A3F2, 0x9C86E0, .triangle, 0x6B52C8, 0x7E66D6),
            sleeve("Salt Lines", "Peder Holm", 0xF4E9D8, 0xE3CDAA, .stripes, 0x9C5B2E, 0xD4BC96, light: true),
            sleeve("Low Sun", "Cass Arden", 0xF2A03D, 0xC63D2F, .disc, 0xFFE9B8, 0x9E2F25),
            sleeve("Glass Garden", "Yun Hale", 0x0E3B2E, 0x061712, .dots, 0x7BE0A8, 0x0A241C),
            sleeve("Field Notes", "Rowan Pike", 0xDDE7F2, 0xB8C8DB, .wave, 0x28456B, 0xA6B7CC, light: true),
            sleeve("Night Shift", "Odile", 0x15182B, 0x05060D, .chevron, 0x7C8CFF, 0x0B0D1A),
            sleeve("Plain Song", "The Marrows", 0xEFE6F0, 0xD9C6DC, .square, 0x5A2A62, 0xC7B1CB, light: true),
        ]),
        StackGridSwitcherSection(title: "Playlists", items: [
            sleeve("Morning Commute", "24 songs", 0xFFD9A8, 0xF29A6B, .disc, 0xFFFFFF, 0xD9794C),
            sleeve("Deep Focus", "41 songs", 0x10233F, 0x060B17, .rings, 0x6FA8FF, 0x0A1528),
            sleeve("Sunday Kitchen", "18 songs", 0xF6F1E4, 0xE6DCC2, .dots, 0xC2542B, 0xD6CAA9, light: true),
            sleeve("Long Drive", "56 songs", 0x3C2A6B, 0x140E2B, .wave, 0xFFB36B, 0x22184A),
            sleeve("Rain Room", "33 songs", 0xC9D6DC, 0x9FB2BC, .stripes, 0x3D5560, 0x8DA1AB, light: true),
            sleeve("After Hours", "27 songs", 0x1A1A1E, 0x000000, .triangle, 0xE0457B, 0x101013),
            sleeve("Fresh Finds", "50 songs", 0xD4F2C4, 0x8FD39A, .chevron, 0x1E5B3A, 0x79BD86, light: true),
            sleeve("Slow Dance", "15 songs", 0xF2C4D0, 0xD98AA4, .square, 0x7A2440, 0xC4728E),
            sleeve("Run Club", "38 songs", 0xFFE14D, 0xF2A81D, .stripes, 0x1A1A1E, 0xD99114, light: true),
        ]),
    ]
}
