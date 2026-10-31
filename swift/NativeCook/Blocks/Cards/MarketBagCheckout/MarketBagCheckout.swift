import SwiftUI

/// A full-screen checkout whose items fly into a weighted cotton net bag.
/// Layout uses points; the ten-second autoplay demonstrates the same packing and payment as touch.
struct MarketBagCheckout: View {
    static let loop = MarketBagCheckoutMotion.loop
    var autoPlay = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var clock = MarketBagCheckoutClock()
    @State private var isPressing = false
    @GestureState private var payTouchActive = false
    private var pinnedTime: Double? {
        #if DEBUG
            if let v = UserDefaults.standard.object(forKey: "marketBagCheckoutTime") {
                return (v as? NSNumber)?.doubleValue ?? Double("\(v)") ?? 0
            }
        #endif
        return nil
    }
    var body: some View {
        GeometryReader { safeArea in
            GeometryReader { screen in
                let geometry = MarketBagCheckoutGeometry(
                    width: screen.size.width, height: screen.size.height, top: safeArea.safeAreaInsets.top,
                    bottom: safeArea.safeAreaInsets.bottom)
                TimelineView(
                    .animation(
                        paused: clock.sleeping || pinnedTime != nil || scenePhase != .active
                            || ((!autoPlay || reduceMotion) && !clock.running))
                ) { timeline in
                    let pose = clock.sample(
                        timeline.date, autoPlay: autoPlay, reduceMotion: reduceMotion, pinnedTime: pinnedTime)
                    Canvas(opaque: true, colorMode: .nonLinear) { context, _ in
                        MarketBagCheckoutArt(geometry: geometry).draw(
                            context, pose, clock.holding && pinnedTime == nil)
                    }
                    .overlay(alignment: .topLeading) {
                        if pinnedTime == nil { accessibilityControls(geometry, pose) }
                    }
                    .coordinateSpace(name: "marketBagCheckoutScreen")
                }
                .frame(width: geometry.width, height: geometry.height, alignment: .topLeading)
            }
            .ignoresSafeArea()
        }
        .background(Color(red: 239.0 / 255, green: 233.0 / 255, blue: 221.0 / 255))
        .preferredColorScheme(.light)
        .onAppear {
            _ = MarketBagCheckoutResources.shared
            clock.reduceMotion = reduceMotion
            clock.active(scenePhase == .active)
        }
        .onDisappear { clock.active(false) }
        .onChange(of: scenePhase) { _, value in clock.active(value == .active) }
        .onChange(of: reduceMotion) { _, value in clock.reduceMotion = value }
        .onChange(of: payTouchActive) { _, active in
            if !active && isPressing {
                isPressing = false
                clock.holdEnd()
            }
        }
    }

    /// Invisible controls align with the canvas rows and hold button, preserving native accessibility
    /// and the reference press-retention region in screen points.
    private func accessibilityControls(_ geometry: MarketBagCheckoutGeometry, _ pose: MarketBagCheckoutMotion.Pose)
        -> some View
    {
        ZStack(alignment: .topLeading) {
            Color.clear.frame(width: geometry.width - 48, height: 16).position(
                x: geometry.centerX, y: geometry.top + 20
            )
            .accessibilityLabel("CORNER GREENGROCER · PICKUP")
            Color.clear.frame(width: 200, height: 40).position(x: 124, y: geometry.top + 50)
                .accessibilityLabel("Your basket").accessibilityAddTraits(.isHeader)
            ForEach(0..<5, id: \.self) { i in
                let item = MarketBagCheckoutMotion.items[i]
                Button {
                    clock.toggle(i)
                } label: {
                    Color.clear.contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .frame(width: geometry.cardWidth, height: 46)
                .position(x: geometry.centerX, y: geometry.rowY(i))
                .accessibilityLabel("\(item.name), \(item.meta), $\(String(format:"%.2f",item.price))")
                .accessibilityHint("Puts it in the bag, or takes it out")
            }
            Color.clear.frame(width: geometry.cardWidth, height: 46).position(
                x: geometry.centerX, y: geometry.cardY + 12 + 46 * 5 + 23
            )
            .accessibilityLabel("Total, $11.40")
            Color.clear.contentShape(Capsule())
                .frame(width: geometry.cardWidth, height: 56)
                .position(x: geometry.centerX, y: geometry.buttonY + 28)
                .gesture(
                    DragGesture(minimumDistance: 0, coordinateSpace: .named("marketBagCheckoutScreen"))
                        .updating($payTouchActive) { _, active, _ in active = true }
                        .onChanged { value in
                            // Pressability's default retention rectangle: 20 on top/sides, 30 below.
                            let inside =
                                value.location.x >= -4 && value.location.x <= geometry.width + 4
                                && value.location.y >= geometry.buttonY - 20
                                && value.location.y <= geometry.buttonY + 56 + 30
                            if inside && !isPressing {
                                isPressing = true
                                clock.holdStart()
                            } else if !inside && isPressing {
                                isPressing = false
                                clock.holdEnd()
                            }
                        }.onEnded { _ in
                            isPressing = false
                            clock.holdEnd()
                        }
                )
                .accessibilityAddTraits(.isButton)
                .accessibilityLabel(
                    pose.paid >= 0 && pose.reset < 0.5
                        ? "Paid $11.40. Tap to start a new basket" : "Hold to pay $11.40"
                )
                .accessibilityAction {
                    clock.holdStart()
                    DispatchQueue.main.asyncAfter(deadline: .now() + MarketBagCheckoutMotion.hold) {
                        clock.holdEnd()
                    }
                }
        }
    }
}
