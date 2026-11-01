import SwiftUI
import UIKit

struct BoxCatFeed: View {
    static let loop = BoxCatFeedMotion.loop
    var autoPlay = false
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.displayScale) private var displayScale
    @StateObject private var clock = BoxCatFeedClock()
    @State private var idleBlink = true
    private var pinned: Double? {
        #if DEBUG
        if let value = UserDefaults.standard.object(forKey: "boxCatFeedTime") {
            return (value as? NSNumber)?.doubleValue ?? Double("\(value)") ?? 0
        }
        #endif
        return nil
    }
    var body: some View {
        GeometryReader { safe in
            let insets = safe.safeAreaInsets
            GeometryReader { full in
                TimelineView(.animation(paused: pinned != nil || scenePhase != .active || ((!autoPlay || reduced) && !clock.running))) { timeline in
                    let _ = clock.revision
                    let _ = idleBlink
                    let frame = clock.sample(clock.running || (autoPlay && !reduced) ? timeline.date : Date(), auto: autoPlay && !reduced, pinned: pinned)
                    let art = BoxCatFeedArt.shared
                    Canvas { context, size in
                        context.withCGContext { cg in
                            art.draw(cg, size: size, top: insets.top, bottom: insets.bottom, displayScale: displayScale, frame: frame, reduced: reduced)
                        }
                    }
                    .overlay(alignment: .topLeading) {
                        Color.clear.frame(width: 170, height: 20).offset(x: 20, y: insets.top+10)
                            .accessibilityElement().accessibilityLabel("Your bank buddy")
                        Color.clear.frame(width: 170, height: 35).offset(x: 20, y: insets.top+30)
                            .accessibilityElement().accessibilityLabel("Mochi")
                        Color.clear.frame(width: 140, height: 36).offset(x: full.size.width-160, y: insets.top+23)
                            .accessibilityElement().accessibilityLabel("\(1240+frame.count*10) points")
                        Color.clear.frame(width: full.size.width-40, height: 22).offset(x: 20, y: insets.top+86)
                            .accessibilityElement().accessibilityLabel(frame.count > 0 ? "Purring. Thanks for the snack!" : "Peeking out for a snack…")
                        Color.clear.frame(width: 120, height: 22).offset(x: 40, y: full.size.height-insets.bottom-87)
                            .accessibilityElement().accessibilityLabel("Daily treats")
                        if pinned == nil {
                            let s = (full.size.width*0.2).rounded()
                            let rim = (full.size.height*0.56).rounded()
                            BoxCatFeedPetSurface(pet: { clock.pet() })
                                .frame(width: full.size.width-120, height: s*2.4)
                                .offset(x: 60, y: rim-s*1.3)
                                .accessibilityElement(children: .ignore)
                                .accessibilityLabel("Mochi the cat. Tap to pet")
                                .accessibilityAddTraits(.isButton)
                                .accessibilityAction { clock.pet() }
                            BoxCatFeedFlickSurface { translation, velocity in
                                if translation < -24 || velocity < -300 { clock.flick() }
                            }
                            .frame(width: 110, height: 64)
                            .offset(x: full.size.width-140, y: full.size.height-insets.bottom-100)
                            .opacity(frame.u >= 0 && frame.u < 1.4 ? 0 : 1)
                            .accessibilityLabel("Fish treat. Flick it up to Mochi")
                            .accessibilityHint("Swipe up")
                            .accessibilityAddTraits(.isButton)
                            .accessibilityAction { clock.flick() }
                        }
                    }
                    .accessibilityElement(children: .contain)
                }
                .frame(width: full.size.width, height: full.size.height, alignment: .topLeading)
            }.ignoresSafeArea()
        }
        .background(Color(red: 243/255, green: 238/255, blue: 1).ignoresSafeArea())
        .preferredColorScheme(.light)
        .onChange(of: scenePhase) { _, _ in clock.date = nil }
        .task(id: "\(scenePhase)-\(autoPlay)-\(reduced)") {
            guard pinned == nil, scenePhase == .active, !autoPlay || reduced else { return }
            // Wake only at the two blink boundaries while the animation timeline is parked.
            while !Task.isCancelled {
                let phase = clock.blinkPhase
                idleBlink = phase < 0.14
                try? await Task.sleep(for: .seconds(max(0.001, phase < 0.14 ? 0.14-phase : 3.4-phase)))
            }
        }
    }
}

struct BoxCatFeedFlickSurface: UIViewRepresentable {
    var ended: (Double, Double) -> Void
    func makeUIView(context: Context) -> UIView {
        let view = UIView()
        let pan = UIPanGestureRecognizer(target: context.coordinator, action: #selector(BoxCatFeedFlickCoordinator.pan(_:)))
        view.addGestureRecognizer(pan)
        return view
    }
    func updateUIView(_ view: UIView, context: Context) { context.coordinator.ended = ended }
    func makeCoordinator() -> BoxCatFeedFlickCoordinator { .init(ended: ended) }
}
final class BoxCatFeedFlickCoordinator: NSObject {
    var ended: (Double, Double) -> Void
    init(ended: @escaping (Double, Double) -> Void) { self.ended = ended }
    @objc func pan(_ pan: UIPanGestureRecognizer) {
        if pan.state == .ended { ended(pan.translation(in: pan.view).y, pan.velocity(in: pan.view).y) }
    }
}

/// RNGH's Tap has a 500 ms duration and cancels when the pointer leaves its hit view.
struct BoxCatFeedPetSurface: UIViewRepresentable {
    var pet: () -> Void
    func makeUIView(context: Context) -> BoxCatFeedPetView { BoxCatFeedPetView() }
    func updateUIView(_ view: BoxCatFeedPetView, context: Context) { view.pet = pet }
}
final class BoxCatFeedPetView: UIView {
    var pet: (() -> Void)?
    private var began = 0.0
    private var valid = false
    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        began = touches.first?.timestamp ?? 0
        valid = true
    }
    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        if let touch = touches.first, !bounds.contains(touch.location(in: self)) { valid = false }
    }
    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        if valid, let touch = touches.first, touch.timestamp-began <= 0.5, bounds.contains(touch.location(in: self)) { pet?() }
        valid = false
    }
    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) { valid = false }
}
