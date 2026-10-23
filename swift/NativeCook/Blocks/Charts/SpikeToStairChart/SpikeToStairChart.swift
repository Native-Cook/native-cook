import SwiftUI

/// A chart film with two interactive resting states, drawn in a fixed design space.
struct SpikeToStairChart: View {
    var autoPlay = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var motion: SpikeToStairChartMotion

    init(autoPlay: Bool = false) {
        self.autoPlay = autoPlay
        _motion = State(initialValue: SpikeToStairChartMotion(autoPlay: autoPlay))
    }

    var body: some View {
        GeometryReader { geometry in
            let scale = min(geometry.size.width/402,geometry.size.height/874)
            let origin = CGPoint(x: (geometry.size.width-402*scale)/2,y: (geometry.size.height-874*scale)/2)
            TimelineView(.animation(minimumInterval: 1.0/60,paused: scenePhase != .active)) { timeline in
                let _ = motion.advance(to: timeline.date)
                let renderer = motion.renderer()
                ZStack {
                    Canvas(opaque: true) { context, size in
                        context.fill(Path(CGRect(origin: .zero,size: size)),with: .color(.black))
                        context.translateBy(x: origin.x,y: origin.y)
                        context.scaleBy(x: scale,y: scale)
                        context.opacity = motion.opacity
                        renderer.draw(context)
                    }
                    .accessibilityHidden(true)
                    SpikeToStairChartTouch(motion: motion,scale: scale,origin: origin,
                                          selected: motion.destination,value: motion.a11y)
                }
            }
        }
        .background(.black)
        .ignoresSafeArea()
        .statusBarHidden()
        .persistentSystemOverlays(.hidden)
        .onAppear { motion.setReduced(reduceMotion) }
        .onChange(of: reduceMotion) { _, value in motion.setReduced(value) }
        .onChange(of: scenePhase) { _, _ in motion.suspend() }
        .onDisappear { motion.suspend() }
    }
}
