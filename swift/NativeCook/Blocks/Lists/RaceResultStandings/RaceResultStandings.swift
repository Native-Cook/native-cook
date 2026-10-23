import SwiftUI

struct RaceResultStandings: View {
    static let loop: Double = 10
    var autoPlay = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.accessibilityVoiceOverEnabled) private var voiceOver
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.displayScale) private var displayScale
    @State private var controller: RaceResultStandingsController
    @State private var revision = 0

    init(autoPlay: Bool = false) {
        self.autoPlay = autoPlay
        _controller = State(initialValue: RaceResultStandingsController(autoPlay: autoPlay))
    }
    private var fixedTime: Double? {
        #if DEBUG
        let args = ProcessInfo.processInfo.arguments
        guard let index = args.firstIndex(of: "-race-result-standings-time"), index + 1 < args.count else { return nil }
        return Double(args[index + 1])
        #else
        return nil
        #endif
    }
    private var debugQuiet: Bool {
        #if DEBUG
        ProcessInfo.processInfo.arguments.contains("-race-result-standings-quiet")
        #else
        false
        #endif
    }
    var body: some View {
        GeometryReader { geometry in
            let safeTop = geometry.safeAreaInsets.top
            let size = CGSize(width: geometry.size.width, height: geometry.size.height + safeTop + geometry.safeAreaInsets.bottom)
            let scale = size.width / RaceResultStandingsData.referenceWidth
            let _ = revision
            TimelineView(.animation(paused: fixedTime != nil || !controller.isAnimating(at: Date.timeIntervalSinceReferenceDate))) { timeline in
                let samplingTime = controller.isAnimating(at: Date.timeIntervalSinceReferenceDate) ? timeline.date.timeIntervalSinceReferenceDate : Date.timeIntervalSinceReferenceDate
                let snapshot = fixedTime.map {
                    RaceResultStandingsController.Snapshot(
                        frame: RaceResultStandingsMotion.frame(at: $0),
                        stretch: 0,
                        backPress: 0,
                        trackPress: 0,
                        contentOpacity: 1
                    )
                } ?? controller.snapshot(at: samplingTime)
                ZStack(alignment: .topLeading) {
                    Canvas(opaque: true, rendersAsynchronously: false) { context, canvasSize in
                        RaceResultStandingsArt.draw(context, size: canvasSize, safeTop: safeTop, scale: scale,
                            frame: snapshot.frame, stretch: snapshot.stretch / scale,
                            backPress: snapshot.backPress, contentOpacity: snapshot.contentOpacity, displayScale: displayScale)
                    }
                    .accessibilityHidden(true)
                    RaceResultStandingsReplay(
                        frame: snapshot.frame,
                        trackPress: snapshot.trackPress,
                        size: size,
                        safeTop: safeTop,
                        scale: scale,
                        displayScale: displayScale
                    )
                    RaceResultStandingsTouch(controller: controller, snapshot: snapshot, scale: scale, safeTop: safeTop)
                        .allowsHitTesting(fixedTime == nil)
                }
                .frame(width: size.width, height: size.height, alignment: .topLeading)
            }
            .offset(y: -safeTop)
        }
        .background(Color(red: 2 / 255, green: 0, blue: 2 / 255).ignoresSafeArea())
        .preferredColorScheme(.dark)
        .onAppear {
            controller.onChange = { revision &+= 1 }
            let now = Date.timeIntervalSinceReferenceDate
            controller.start(at: now)
            controller.setReduced(reduceMotion || voiceOver || debugQuiet, at: now)
            controller.setForeground(scenePhase == .active, at: now)
        }
        .onDisappear { controller.stop() }
        .onChange(of: reduceMotion) { _, value in controller.setReduced(value || voiceOver || debugQuiet, at: Date.timeIntervalSinceReferenceDate) }
        .onChange(of: voiceOver) { _, value in controller.setReduced(reduceMotion || value || debugQuiet, at: Date.timeIntervalSinceReferenceDate) }
        .onChange(of: scenePhase) { _, phase in controller.setForeground(phase == .active, at: Date.timeIntervalSinceReferenceDate) }
        .accessibilityElement(children: .contain)
    }
}
