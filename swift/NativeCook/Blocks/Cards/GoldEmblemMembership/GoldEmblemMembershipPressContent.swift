import SwiftUI

struct GoldEmblemMembershipPressContent: View {
    let configuration: PrimitiveButtonStyleConfiguration
    let reduced: Bool
    @State private var pressed = false
    @GestureState private var touching = false
    var body: some View {
        configuration.label
            .scaleEffect(pressed && !reduced ? 0.96 : 1)
            .opacity(pressed && reduced ? 0.72 : 1)
            .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.15), value: pressed)
            .overlay {
                GeometryReader { proxy in
                    Color.clear.contentShape(Rectangle())
                        .gesture(DragGesture(minimumDistance: 0).updating($touching) { _, active, _ in active = true }
                            .onChanged { event in
                                pressed = CGRect(origin: .zero, size: proxy.size).insetBy(dx: -16, dy: -16).contains(event.location)
                            }.onEnded { _ in
                                if pressed { configuration.trigger() }; pressed = false
                            })
                }.accessibilityHidden(true)
            }
            .onChange(of: touching) { _, active in if !active { pressed = false } }
    }
}
