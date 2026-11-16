import SwiftUI

/// Approved RN ExtrusionStory's three-card showcase, without changes to its choreography.
struct ExtrusionRecapStory: View {
    var autoPlay: Bool
    static let loop = ExtrusionRecapStoryMotion.loop
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.accessibilityVoiceOverEnabled) private var voiceOver
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.displayScale) private var displayScale
    @Environment(\.dismiss) private var dismiss
    @State private var driver = ExtrusionRecapStoryDriver()
    @GestureState private var gestureActive = false
    @State private var gesturePending = false
    @ScaledMetric(relativeTo: .body) private var scaledBody = 17.0
    typealias R = ExtrusionRecapStoryResources

    init(autoPlay: Bool = false) {
        self.autoPlay = autoPlay
        _ = ExtrusionRecapStoryResources.register
    }
    private var pin: Double? {
        #if DEBUG
        let args = ProcessInfo.processInfo.arguments
        if let i = args.firstIndex(of: "-extrusionRecapStoryTime"), i + 1 < args.count,
           let t = Double(args[i + 1]), t.isFinite, t >= 0 { return t }
        #endif
        return nil
    }
    var body: some View {
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let layout = R.ExtrusionRecapStoryLayout(width: geo.size.width + insets.leading + insets.trailing,
                                                     height: geo.size.height + insets.top + insets.bottom,
                                                     top: insets.top, bottom: insets.bottom)
            stage(layout)
                .offset(x: -insets.leading, y: -insets.top)
        }
        .preferredColorScheme(.dark)
        .onAppear { configure() }
        .onDisappear { driver.stop() }
        .onChange(of: scenePhase) { configure() }
        .onChange(of: reduceMotion) { configure() }
        .onChange(of: voiceOver) { configure() }
        .onChange(of: autoPlay) { configure() }
        .onChange(of: gestureActive) {
            if !gestureActive && gesturePending {
                gesturePending = false
                driver.end(dx: 0, velocityX: 0, rightHalf: false, cancelled: true)
            }
        }
    }
    private func configure() {
        driver.configure(auto: autoPlay, reduced: reduceMotion, voiceOver: voiceOver, active: scenePhase == .active, pin: pin)
    }
    private func stage(_ layout: R.ExtrusionRecapStoryLayout) -> some View {
        let scene = driver.scene, s = layout.scale, active = scene.activePage
        return ZStack(alignment: .topLeading) {
            R.color(0x080407)
            ExtrusionRecapStoryFrame(scene: scene, layout: layout, displayScale: displayScale)
            Color.clear.contentShape(Rectangle())
                .gesture(DragGesture(minimumDistance: 0)
                    .updating($gestureActive) { _, state, _ in state = true }
                    .onChanged { value in
                        gesturePending = true
                        if !driver.fingerDown { driver.begin() }
                        driver.move(dx: value.translation.width / s, dy: value.translation.height / s, velocityX: value.velocity.width / s)
                    }
                    .onEnded { value in
                        gesturePending = false
                        driver.end(dx: value.translation.width / s, velocityX: value.velocity.width / s,
                                   rightHalf: value.location.x > layout.width / 2)
                    })
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(R.pages[active].accessibilityLabel)
                .accessibilityValue("Page \(active + 1) of 3")
                .accessibilityHint("Swipe left for the next page. Hold and drag to aim the light.")
                .accessibilityAdjustableAction { direction in
                    switch direction {
                    case .increment: driver.changePage(1)
                    case .decrement: driver.changePage(-1)
                    @unknown default: break
                    }
                }
            ForEach(0..<3, id: \.self) { index in
                ExtrusionRecapStoryPageCopy(scene: scene, layout: layout, index: index, fontScale: scaledBody / 17)
            }
            peek(layout, scene)
            header(layout, active)
            if autoPlay {
                Button { driver.togglePause() } label: {
                    ExtrusionRecapStoryIcon(kind: driver.paused || reduceMotion || voiceOver ? .play : .pause)
                        .frame(width: 20, height: 20)
                        .frame(width: 44, height: 44)
                }
                .buttonStyle(ExtrusionRecapStoryButtonStyle())
                .disabled(reduceMotion || voiceOver)
                .accessibilityLabel(reduceMotion || voiceOver ? "Animated playback disabled by accessibility settings" : driver.paused ? "Play recap" : "Pause recap")
                .accessibilityAddTraits(driver.paused ? .isSelected : [])
                .offset(x: layout.width - 20 * s - 44, y: layout.top + 44)
            }
        }
        .frame(width: layout.width, height: layout.height, alignment: .topLeading)
        .clipped()
    }
    private func peek(_ layout: R.ExtrusionRecapStoryLayout, _ scene: ExtrusionRecapStoryMotion.ExtrusionRecapStoryScene) -> some View {
        Button { driver.advance() } label: {
            ZStack(alignment: .topLeading) {
                ForEach(0..<3, id: \.self) { i in ExtrusionRecapStoryPeekLabel(scene: scene, layout: layout, index: i) }
            }
            .frame(width: layout.width, height: layout.height - layout.cardBottom - 8 * layout.scale, alignment: .topLeading)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(scene.activePage == 2 ? "Replay" : "Up next"): \(R.pages[(scene.activePage + 1) % 3].peekTitle)")
        .offset(y: layout.cardBottom + 8 * layout.scale)
    }
    private func header(_ layout: R.ExtrusionRecapStoryLayout, _ active: Int) -> some View {
        let s = layout.scale
        return ZStack(alignment: .leading) {
            HStack(spacing: 0) {
                Button { dismiss() } label: {
                    ExtrusionRecapStoryIcon(kind: .close).frame(width: 20, height: 20).frame(width: 44, height: 44)
                }
                .buttonStyle(ExtrusionRecapStoryButtonStyle())
                .accessibilityLabel("Close recap")
                .offset(x: -12 * s)
                Spacer(minLength: 0)
                ExtrusionRecapStoryText(text: "\(active + 1) / 3", size: 14 * s, singleLine: true).fixedSize()
            }
            .padding(.horizontal, 20 * s)
            HStack(spacing: 4) {
                ExtrusionRecapStoryText(text: "Lilt", size: 22 * s, bold: true, singleLine: true).fixedSize()
                ExtrusionRecapStoryIcon(kind: .spark).frame(width: 24 * s, height: 24 * s)
                ExtrusionRecapStoryText(text: "Music", size: 22 * s, bold: true, singleLine: true).fixedSize()
            }
            .frame(width: layout.width - 128)
            .offset(x: 64)
            .allowsHitTesting(false)
        }
        .frame(width: layout.width, height: 44)
        .offset(y: layout.top)
    }
}

struct ExtrusionRecapStoryButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View { configuration.label.opacity(configuration.isPressed ? 0.55 : 1) }
}

struct ExtrusionRecapStoryIcon: View {
    enum ExtrusionRecapStoryKind { case close, pause, play, spark }
    let kind: ExtrusionRecapStoryKind
    var body: some View {
        GeometryReader { geo in
            let source = kind == .spark ? 24.0 : 20.0, s = geo.size.width / source
            let p = path.applying(CGAffineTransform(scaleX: s, y: s))
            if kind == .close {
                p.stroke(ExtrusionRecapStoryResources.color(0xF4F83C), style: StrokeStyle(lineWidth: 1.8 * s, lineCap: .round))
            } else { p.fill(ExtrusionRecapStoryResources.color(0xF4F83C)) }
        }
        .accessibilityHidden(true)
    }
    private var path: Path {
        Path { p in
            switch kind {
            case .close:
                p.move(to: CGPoint(x: 3, y: 3)); p.addLine(to: CGPoint(x: 17, y: 17))
                p.move(to: CGPoint(x: 17, y: 3)); p.addLine(to: CGPoint(x: 3, y: 17))
            case .pause:
                p.addRect(CGRect(x: 5, y: 3, width: 2, height: 14)); p.addRect(CGRect(x: 13, y: 3, width: 2, height: 14))
            case .play:
                p.move(to: CGPoint(x: 5, y: 3)); p.addLine(to: CGPoint(x: 17, y: 10)); p.addLine(to: CGPoint(x: 5, y: 17)); p.closeSubpath()
            case .spark:
                let points: [(Double, Double)] = [(12,1),(13.8,8.2),(20,4),(15.8,10.2),(23,12),(15.8,13.8),(20,20),(13.8,15.8),
                                                  (12,23),(10.2,15.8),(4,20),(8.2,13.8),(1,12),(8.2,10.2),(4,4),(10.2,8.2)]
                p.move(to: CGPoint(x: points[0].0, y: points[0].1))
                for point in points.dropFirst() { p.addLine(to: CGPoint(x: point.0, y: point.1)) }
                p.closeSubpath()
            }
        }
    }
}
