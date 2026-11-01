import SwiftUI
import UIKit

final class DollhouseFloorsClock: ObservableObject {
    @Published var running = false
    let driver = DollhouseFloorsDriver()
    private var date: Date?
    private var artCache: DollhouseFloorsArt?
    func art(_ size: CGSize) -> DollhouseFloorsArt {
        if let artCache, artCache.size == size { return artCache }
        let art = DollhouseFloorsArt(size); artCache = art; return art
    }
    var canResumeAuto: Bool { driver.scripted || (driver.e < 0.001 && driver.sel == -1) }
    func sample(_ at: Date, auto: Bool, pinned: Double?) -> DollhouseFloorsMotion.Frame {
        if let pinned { return DollhouseFloorsMotion.pinned(pinned) }
        // Keep the script on wall time, including slow Xcode Cloud simulator frames.
        let wall = min(0.5, max(0, date.map { at.timeIntervalSince($0) } ?? 0.016))
        date = at
        let frame = driver.step(wall, auto: auto)
        if running && (!auto || !canResumeAuto) && driver.settled {
            DispatchQueue.main.async { [weak self] in self?.running = false }
        }
        return frame
    }
    func wake() { if !running { date = nil }; running = true; objectWillChange.send() }
    func pause() { date = nil }
}

struct DollhouseFloors: View {
    static let loop = DollhouseFloorsMotion.loop
    var autoPlay = false
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var phase
    @StateObject private var clock = DollhouseFloorsClock()
    private var pinned: Double? {
        #if DEBUG
        if let value = UserDefaults.standard.object(forKey: "dollhouseFloorsTime") {
            return (value as? NSNumber)?.doubleValue ?? Double("\(value)")
        }
        #endif
        return nil
    }
    var body: some View {
        GeometryReader { safe in
            GeometryReader { full in
                TimelineView(.animation(paused: pinned != nil || phase != .active || (!(autoPlay && !reduced && clock.canResumeAuto) && !clock.running))) { timeline in
                    let frame = clock.sample(timeline.date, auto: autoPlay && !reduced, pinned: pinned)
                    let art = clock.art(full.size)
                    ZStack(alignment: .topLeading) {
                        DollhouseFloorsArt.color(0xFBF6EE)
                        Canvas(opaque: false, colorMode: .nonLinear) { context, _ in
                            art.draw(context, frame, reduced: reduced)
                        }.allowsHitTesting(false).accessibilityHidden(true)
                        DollhouseFloorsInterface(frame: frame, safe: safe.safeAreaInsets, pinned: pinned != nil, clock: clock)
                    }
                    .frame(width: full.size.width, height: full.size.height, alignment: .topLeading)
                }
            }.ignoresSafeArea()
        }
        .preferredColorScheme(.light)
        .onChange(of: phase) { _, _ in clock.pause() }
        .onChange(of: reduced) { _, _ in clock.wake() }
        .onChange(of: autoPlay) { _, _ in clock.wake() }
        .onDisappear { clock.pause() }
    }
}

struct DollhouseFloorsInterface: UIViewRepresentable {
    var frame: DollhouseFloorsMotion.Frame
    var safe: EdgeInsets
    var pinned: Bool
    var clock: DollhouseFloorsClock
    func makeUIView(context: Context) -> DollhouseFloorsInterfaceView {
        DollhouseFloorsInterfaceView(clock: clock)
    }
    func updateUIView(_ view: DollhouseFloorsInterfaceView, context: Context) {
        view.update(frame, top: safe.top, bottom: safe.bottom, pinned: pinned)
    }
}
