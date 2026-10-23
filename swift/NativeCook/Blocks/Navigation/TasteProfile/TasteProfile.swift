import SwiftUI
import UIKit

struct TasteProfile: View {
    static let loop = TasteProfileMotion.loop
    var autoPlay = false

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.displayScale) private var displayScale
    @State private var engine = TasteProfileEngine()
    @State private var clockStopped = false
    @State private var voiceOver = UIAccessibility.isVoiceOverRunning

    init(autoPlay: Bool = false) {
        self.autoPlay = autoPlay
        TasteProfileAssets.register()
        _ = TasteProfileAssets.photos
        _ = TasteProfileAssets.coinFrames
        _ = TasteProfileAssets.gemFrames
    }

    private var looping: Bool { autoPlay && !reduceMotion && !voiceOver }
    private var clock: Double { Date.timeIntervalSinceReferenceDate }

    var body: some View {
        GeometryReader { proxy in
            let width = proxy.size.width
            let scale = width / TasteProfileTheme.refW
            let y0 = -TasteProfileTheme.refInset * scale
            let height = max(proxy.size.height, y0 + TasteProfileTheme.contentBottom * scale)
            let geo = TasteProfileGeo(width: width, height: height, scale: scale, pointScale: width / TasteProfileTheme.refPt,
                                      y0: y0, pixel: displayScale)
            TimelineView(.animation(paused: clockStopped || scenePhase != .active)) { timeline in
                let now = timeline.date.timeIntervalSinceReferenceDate
                let frame = engine.frame(at: now)
                content(frame: frame, geo: geo)
                    .onChange(of: engine.busy(at: now)) { _, busy in
                        if !busy { clockStopped = true }
                    }
            }
            .frame(width: width, height: height, alignment: .topLeading)
            .background(TasteProfileTheme.color(0xF6F6F6).ignoresSafeArea())
            .ignoresSafeArea(.container, edges: .bottom)
            .clipped()
        }
        .background(TasteProfileTheme.color(0xF6F6F6).ignoresSafeArea())
        .onAppear {
            engine.configure(looping: looping, reduced: reduceMotion, now: clock)
            engine.setActive(scenePhase == .active, now: clock)
            clockStopped = false
        }
        .onChange(of: looping) { _, _ in update() }
        .onChange(of: reduceMotion) { _, _ in update() }
        .onChange(of: scenePhase) { _, phase in
            engine.setActive(phase == .active, now: clock)
            clockStopped = false
        }
        .onReceive(NotificationCenter.default.publisher(for: UIAccessibility.voiceOverStatusDidChangeNotification)) { _ in
            voiceOver = UIAccessibility.isVoiceOverRunning
        }
    }

    private func update() {
        engine.configure(looping: looping, reduced: reduceMotion, now: clock)
        clockStopped = false
    }

    @ViewBuilder
    private func content(frame: TasteProfileFrame, geo: TasteProfileGeo) -> some View {
        ZStack(alignment: .topLeading) {
            ZStack(alignment: .topLeading) {
                TasteProfileBackdrop(geo: geo).equatable()
                TasteProfileStaticLayer(geo: geo).equatable()
                TasteProfileTilesLayer(geo: geo, held: frame.hold.tile).equatable()
                TasteProfileMotionLayer(geo: geo, frame: frame)
                TasteProfileBoard(frame: frame, geo: geo)
            }
            .accessibilityHidden(true)

            TasteProfileTouch(engine: engine, geo: geo, city: frame.change.to, following: frame.following) {
                clockStopped = false
            }
            .frame(width: geo.width, height: geo.height)

            if frame.hold.tile >= 0 {
                TasteProfileChart(hold: frame.hold, tile: frame.hold.tile, city: frame.change.to, reduced: frame.change.reduced, geo: geo)
            }
        }
        .frame(width: geo.width, height: geo.height, alignment: .topLeading)
    }
}
