import SwiftUI

struct RainWindowForecast: View {
    static let loop = RainWindowForecastMotion.loop
    var autoPlay = false
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var clock = RainWindowForecastClock()
    private var pinned: Double? {
        #if DEBUG
        if let value = UserDefaults.standard.object(forKey: "rainWindowForecastTime") {
            return (value as? NSNumber)?.doubleValue ?? Double("\(value)") ?? 0
        }
        #endif
        return nil
    }
    var body: some View {
        GeometryReader { safe in
            GeometryReader { full in
                TimelineView(.animation(paused: pinned != nil || scenePhase != .active || (reduced && !clock.clearing))) { timeline in
                    let sample = clock.sample(timeline.date, size: full.size, auto: autoPlay && !reduced, reduced: reduced, pinned: pinned)
                    RainWindowForecastSurface(sample: sample, top: safe.safeAreaInsets.top, bottom: safe.safeAreaInsets.bottom)
                        .overlay {
                            if pinned == nil {
                                RainWindowForecastTouchView(bottom: safe.safeAreaInsets.bottom) { point, ended in
                                    clock.touch(point, ended: ended, reduced: reduced)
                                }
                            }
                        }
                }
                .frame(width: full.size.width, height: full.size.height, alignment: .topLeading)
            }
            .ignoresSafeArea()
        }
        .background(Color(red: 27 / 255, green: 34 / 255, blue: 56 / 255))
        .preferredColorScheme(.dark)
        .onAppear { clock.reset(autoPlay && !reduced) }
        .onChange(of: reduced) { _, _ in clock.configure(autoPlay && !reduced) }
        .onChange(of: autoPlay) { _, _ in clock.configure(autoPlay && !reduced) }
        .onChange(of: scenePhase) { _, _ in clock.suspend() }
    }
}
