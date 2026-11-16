import SwiftUI

/// Value-for-value port of the approved RunReplay React Native screen.
struct ThermalGlassTiles: View {
    var autoPlay = false
    var miles = false
    static let loop = 8.0
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.displayScale) private var displayScale
    @ScaledMetric(relativeTo: .body) private var fontScale = 1.0
    @State private var resources = ThermalGlassTilesResources()
    @State private var driver = ThermalGlassTilesDriver()
    @GestureState private var gestureActive = false
    @State private var gesturePending = false

    init(autoPlay: Bool = false, miles: Bool = false) {
        self.autoPlay = autoPlay; self.miles = miles
        _ = ThermalGlassTilesResources.register
    }

    private var pinnedTime: Double? {
        #if DEBUG
        let args = ProcessInfo.processInfo.arguments
        if let i = args.firstIndex(of: "-thermalGlassTilesTime"), i + 1 < args.count,
           let t = Double(args[i + 1]), t.isFinite, t >= 0 { return t }
        #endif
        return nil
    }

    var body: some View {
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(width: geo.size.width + insets.leading + insets.trailing,
                              height: geo.size.height + insets.top + insets.bottom)
            stage(size, top: insets.top, bottom: insets.bottom)
                .offset(x: -insets.leading, y: -insets.top)
        }
        .preferredColorScheme(.light)
        .onAppear { driver.configure(auto: autoPlay, reduced: reduceMotion) }
        .onChange(of: autoPlay) { driver.configure(auto: autoPlay, reduced: reduceMotion) }
        .onChange(of: reduceMotion) { driver.configure(auto: autoPlay, reduced: reduceMotion) }
        .onChange(of: scenePhase) { driver.resume() }
        .onChange(of: gestureActive) {
            if !gestureActive && gesturePending {
                driver.end(reduced: reduceMotion, cancelled: true); gesturePending = false
            }
        }
    }

    private func stage(_ size: CGSize, top: Double, bottom: Double) -> some View {
        let layout = ThermalGlassTilesLayout(size: size, top: top, bottom: bottom)
        let pin = driver.pinDisabled ? nil : pinnedTime
        return TimelineView(.animation(paused: scenePhase != .active || pin != nil || !driver.awake)) { timeline in
            let _ = driver.revision
            let state = driver.frame(at: timeline.date, auto: autoPlay, reduced: reduceMotion, pin: pin, miles: miles)
            ZStack(alignment: .topLeading) {
                ThermalGlassTilesFrame(state: state, layout: layout, resources: resources,
                                       displayScale: displayScale, fontScale: fontScale, reduced: reduceMotion, miles: miles)
                    .allowsHitTesting(false)
                replayHit(state, layout)
                tileButton(layout, index: 1, label: "Contact time. Replay the longest ground contact.",
                           value: "\(Int(floor(state.sample.contact + 0.5))) milliseconds",
                           target: ThermalGlassTilesMotion.contactExtreme, point: CGPoint(x: 122, y: 583))
                tileButton(layout, index: 2, label: "Vertical oscillation. Replay the highest bounce.",
                           value: "\(String(format: "%.1f", state.sample.oscillation)) centimetres",
                           target: ThermalGlassTilesMotion.verticalExtreme, point: CGPoint(x: 280, y: 583))
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .clipped()
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
    }

    private func replayHit(_ state: ThermalGlassTilesFrameState, _ layout: ThermalGlassTilesLayout) -> some View {
        let r = layout.tile(0), s = layout.scale
        return Color.clear.contentShape(Rectangle())
            .frame(width: r.width, height: r.height)
            .gesture(DragGesture(minimumDistance: 0)
                .updating($gestureActive) { _, active, _ in active = true }
                .onChanged { v in
                    gesturePending = true
                    if !driver.fingerDown {
                        driver.begin(CGPoint(x: 51 + v.startLocation.x / s, y: 196 + v.startLocation.y / s),
                                     at: v.time, reduced: reduceMotion)
                    }
                    driver.move(CGPoint(x: 51 + v.location.x / s, y: 196 + v.location.y / s),
                                velocity: CGSize(width: v.velocity.width / s, height: v.velocity.height / s), reduced: reduceMotion)
                }
                .onEnded { _ in gesturePending = false; driver.end(reduced: reduceMotion) })
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("Run replay")
            .accessibilityHint("Drag along the route to replay your run. Swipe up or down to step one kilometre.")
            .accessibilityValue("\(String(format: "%.1f", state.sample.distance * (miles ? 0.621371 : 1))) \(miles ? "miles" : "kilometres"), \(Int(floor(state.sample.bpm + 0.5))) beats per minute, pace \(ThermalGlassTilesMotion.timeText(state.sample.pace * (miles ? 1.609344 : 1))) per \(miles ? "mile" : "kilometre")")
            .accessibilityAdjustableAction { direction in
                switch direction {
                case .increment: driver.adjust(miles ? 1.609344 : 1, reduced: reduceMotion)
                case .decrement: driver.adjust(miles ? -1.609344 : -1, reduced: reduceMotion)
                @unknown default: break
                }
            }
            .offset(x: r.minX, y: r.minY)
    }

    private func tileButton(_ layout: ThermalGlassTilesLayout, index: Int, label: String, value: String,
                            target: Double, point: CGPoint) -> some View {
        let r = layout.tile(index)
        return Button { driver.shortcut(target, reduced: reduceMotion) } label: {
            Color.clear.contentShape(Rectangle()).frame(width: r.width, height: r.height)
        }
        .buttonStyle(ThermalGlassTilesButtonStyle { driver.warm(point) })
        .accessibilityLabel(label)
        .accessibilityValue(value)
        .offset(x: r.minX, y: r.minY)
    }
}

struct ThermalGlassTilesButtonStyle: PrimitiveButtonStyle {
    let began: () -> Void
    func makeBody(configuration: Configuration) -> some View {
        ThermalGlassTilesTilePress(label: configuration.label, began: began, action: configuration.trigger)
    }
}

/// Gesture Handler Tap defaults: one short tap (<=500 ms), <=10 screen points
/// of travel. A cancelled tap still leaves its warm fingerprint, as in RN.
struct ThermalGlassTilesTilePress<Label: View>: View {
    let label: Label
    let began: () -> Void
    let action: () -> Void
    @State private var started: Date?
    @State private var valid = true
    @GestureState private var tracking = false
    var body: some View {
        label
            .gesture(DragGesture(minimumDistance: 0)
                .updating($tracking) { _, active, _ in active = true }
                .onChanged { v in
                    if started == nil { started = v.time; valid = true; began() }
                    if hypot(v.translation.width, v.translation.height) > 10 { valid = false }
                }
                .onEnded { v in
                    if let start = started, valid, v.time.timeIntervalSince(start) <= 0.5,
                       hypot(v.translation.width, v.translation.height) <= 10 { action() }
                    started = nil
                })
            .onChange(of: tracking) { if !tracking { started = nil } }
            .accessibilityAction(.default, action)
    }
}
