import SwiftUI

struct HabitBubbleCluster: View {
    static let loop: Double = HabitBubbleClusterTokens.loopMs / 1000

    @Environment(\.accessibilityReduceMotion) private var accessibilityReduceMotion
    @State private var model: HabitBubbleClusterModel
    private let assets = HabitBubbleClusterAssets()

    init(autoPlay: Bool = false) {
        _model = State(initialValue: HabitBubbleClusterModel(autoPlay: autoPlay, reducedMotion: false))
    }

    var body: some View {
        GeometryReader { safeAreaProxy in
            let safeTop = safeAreaProxy.safeAreaInsets.top
            GeometryReader { proxy in
                ZStack(alignment: .topLeading) {
                    HabitBubbleClusterCanvas(model: model, assets: assets)
                    HabitBubbleClusterTouchView(model: model)
                    HabitBubbleClusterChrome(model: model)
                    HabitBubbleClusterAccessibility(model: model)
                }
                .frame(width: proxy.size.width, height: proxy.size.height, alignment: .topLeading)
                .onAppear {
                    assert(Self.loop * 1000 == HabitBubbleClusterTokens.loopMs, "loop and LOOP_MS disagree")
                    model.reducedMotion = accessibilityReduceMotion
                    model.update(size: proxy.size, safeTop: safeTop)
                    model.start()
                }
                .onChange(of: proxy.size) { _, size in model.update(size: size, safeTop: safeTop) }
                .onChange(of: accessibilityReduceMotion) { _, value in model.reducedMotion = value }
                .onDisappear { model.stop() }
            }
            .ignoresSafeArea()
        }
        .background(HabitBubbleClusterColor(HabitBubbleClusterTokens.Color.bg).ignoresSafeArea())
        .preferredColorScheme(.dark)
    }
}

struct HabitBubbleClusterAccessibility: View {
    let model: HabitBubbleClusterModel

    var body: some View {
        VStack(spacing: 0) {
            ForEach(HabitBubbleClusterSimulation.entities.indices, id: \.self) { index in
                let entity = HabitBubbleClusterSimulation.entities[index]
                if entity.set == model.tab && !entity.quiet {
                    Button(entity.name) { model.toggle(index) }
                        .accessibilityValue(model.simulation.doneTarget[index] > 0.5 ? "Completed" : "Open")
                        .accessibilityHint(model.simulation.doneTarget[index] > 0.5 ? "Double tap to reopen" : "Double tap to complete")
                }
            }
        }
        .frame(width: 1, height: 1)
        .clipped()
        .accessibilityLabel("Habits")
    }
}
