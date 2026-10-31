import SwiftUI
import UIKit

/// Full-screen locker pickup: a perspective door, parcel flight and frosted receipt.
/// Geometry is in points. Canvas layers share one pose, including the scene sampled by the blur.
struct ParcelLockerPickup: View {
    static let loop = ParcelLockerPickupMotion.loop
    var autoPlay = false
    @Environment(\.accessibilityReduceMotion) private var reducedMotion
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @StateObject private var clock = ParcelLockerPickupClock()
    private var pinned: Double? {
        #if DEBUG
            if let v = UserDefaults.standard.object(forKey: "parcelLockerPickupTime") {
                return (v as? NSNumber)?.doubleValue ?? Double("\(v)") ?? 0
            }
        #endif
        return nil
    }

    var body: some View {
        GeometryReader { outer in
            let insets = outer.safeAreaInsets
            GeometryReader { proxy in
                let geometry = ParcelLockerPickupGeometry(
                    width: proxy.size.width, height: proxy.size.height, top: insets.top, bottom: insets.bottom)
                let resources = resourcesForType()
                TimelineView(.animation(paused: pinned != nil || scenePhase != .active || clock.sleeping)) { timeline in
                    let pose = clock.sample(timeline.date, autoPlay, reducedMotion, pinned)
                    let art = ParcelLockerPickupArt(geometry: geometry, resources: resources)
                    ZStack(alignment: .topLeading) {
                        let ledTime = pinned ?? (clock.automatic ? clock.loopTime : clock.elapsedTime)
                        ParcelLockerPickupScene(art: art, pose: pose, time: ledTime)
                        // The frost samples every layer behind it, including the animated light.
                        ParcelLockerPickupScene(art: art, pose: pose, time: ledTime)
                            .drawingGroup(opaque: true, colorMode: .nonLinear)
                            .blur(radius: 18)
                            .mask {
                                Canvas { context, _ in
                                    context.fill(
                                        Path(roundedRect: geometry.sheet(pose.sheet), cornerRadius: 28),
                                        with: .color(.white))
                                }
                            }
                        Canvas(colorMode: .nonLinear) { context, _ in
                            art.panel(&context, pose)
                            art.overlay(&context, pose)
                        }
                        if pinned == nil {
                            ParcelLockerPickupTouchView { state, point in
                                clock.touch(state, point, geometry, reducedMotion)
                            }
                            .accessibilityHidden(true)
                            accessibility(geometry, pose)
                        }
                    }
                    .frame(width: geometry.width, height: geometry.height, alignment: .topLeading)
                    .clipped()
                }
            }.ignoresSafeArea()
        }
        .background(ParcelLockerPickupArt.color(0x141312))
        .preferredColorScheme(.dark)
        .onAppear {
            clock.active(true, reducedMotion)
        }
        .onDisappear {
            clock.active(false, reducedMotion)
        }
        .onChange(of: scenePhase) { _, phase in
            clock.active(phase == .active, reducedMotion)
        }
        .onChange(of: reducedMotion) { _, value in
            clock.sleeping = false
            clock.active(true, value)
        }
    }

    /// Chooses cached text bitmaps for Dynamic Type; hit areas use the same measured labels.
    private func resourcesForType() -> ParcelLockerPickupResources {
        let category: UIContentSizeCategory
        switch dynamicTypeSize {
        case .xSmall: category = .extraSmall
        case .small: category = .small
        case .medium: category = .medium
        case .large: category = .large
        case .xLarge: category = .extraLarge
        case .xxLarge: category = .extraExtraLarge
        case .xxxLarge: category = .extraExtraExtraLarge
        case .accessibility1: category = .accessibilityMedium
        case .accessibility2: category = .accessibilityLarge
        case .accessibility3: category = .accessibilityExtraLarge
        case .accessibility4: category = .accessibilityExtraExtraLarge
        default: category = .accessibilityExtraExtraExtraLarge
        }
        let scale =
            UIFontMetrics(forTextStyle: .body).scaledValue(
                for: 17, compatibleWith: UITraitCollection(preferredContentSizeCategory: category)) / 17
        let resources = ParcelLockerPickupResources.forScale(scale)
        clock.resources = resources
        return resources
    }

    /// Places semantic elements over the painted text and buttons, using scene coordinates in points.
    @ViewBuilder private func accessibility(
        _ geometry: ParcelLockerPickupGeometry, _ pose: ParcelLockerPickupMotion.Pose
    ) -> some View {
        let labels = clock.resources.labels
        let rect = geometry.sheet(pose.sheet)
        let eyebrowHeight = labels["eyebrow"]?.size.height ?? 15
        let headingHeight = labels[pose.sheet > 0.5 ? "titleB" : "titleA"]?.size.height ?? 40
        let subtitleHeight = labels[pose.sheet > 0.5 ? "subB" : "subA"]?.size.height ?? 20
        let headingY = geometry.top + 14 + eyebrowHeight + 10
        let titleY = 20 + (labels["brand"]?.size.height ?? 14) + 12
        let subY = titleY + (labels["ready"]?.size.height ?? 28) + 2
        let codeY = subY + (labels["order"]?.size.height ?? 19) + 16
        let receiptButtonY =
            22 + max(28, labels["picked"]?.size.height ?? 32) + 2 + (labels["closed"]?.size.height ?? 19) + 18 + 64 + 20
        let buttonY = rect.minY + (pose.sheet > 0.5 ? receiptButtonY : codeY + 50 + 18)
        Color.clear.frame(width: geometry.width - 48, height: eyebrowHeight).position(
            x: geometry.width / 2, y: geometry.top + 14 + eyebrowHeight / 2
        )
        .accessibilityElement().accessibilityLabel("NORTHWIND LOCKERS · LOBBY").allowsHitTesting(false)
        Color.clear.frame(width: geometry.width - 48, height: headingHeight).position(
            x: geometry.width / 2, y: headingY + headingHeight / 2
        )
        .accessibilityElement().accessibilityLabel(pose.sheet > 0.5 ? "Enjoy your parcel" : "Locker 14B")
        .accessibilityAddTraits(pose.sheet > 0.5 ? .isStaticText : .isHeader).allowsHitTesting(false)
        Color.clear.frame(width: geometry.width - 48, height: subtitleHeight).position(
            x: geometry.width / 2, y: headingY + headingHeight + 4 + subtitleHeight / 2
        )
        .accessibilityElement().accessibilityLabel(
            pose.sheet > 0.5 ? "Locker 14B is closed and free again" : "Middle column, third row · blinking amber"
        )
        .allowsHitTesting(false)
        Color.clear.frame(width: geometry.doorWidth + 60, height: geometry.doorHeight + 40).position(
            x: geometry.doorCenterX, y: geometry.doorCenterY
        )
        .accessibilityElement().accessibilityLabel("Locker 14B").accessibilityHint(
            "Once it is open, takes the parcel out"
        )
        .accessibilityAddTraits(.isButton).accessibilityAction {
            clock.interrupt()
            clock.take(reducedMotion)
        }
        .allowsHitTesting(false)
        Color.clear.frame(width: geometry.width - 76, height: pose.sheet > 0.5 ? 50 : 52).position(
            x: geometry.width / 2, y: buttonY + (pose.sheet > 0.5 ? 25 : 26)
        )
        .accessibilityElement().accessibilityLabel(pose.sheet > 0.5 ? "Done" : "Open locker 14B")
        .accessibilityAddTraits(.isButton).accessibilityAction {
            if pose.sheet > 0.5 {
                clock.done(reducedMotion)
            } else {
                clock.open()
            }
        }
        .allowsHitTesting(false)
        if pose.sheet <= 0.5 {
            Color.clear.frame(width: 170, height: 14).position(x: 123, y: rect.minY + 27)
                .accessibilityElement().accessibilityLabel("NORTHWIND PARCELS").allowsHitTesting(false)
            Color.clear.frame(width: 130, height: 14).position(x: geometry.width - 103, y: rect.minY + 27)
                .accessibilityElement().accessibilityLabel("MEDIUM · 2.4 KG").allowsHitTesting(false)
            Color.clear.frame(width: geometry.width - 76, height: 28).position(
                x: geometry.width / 2, y: rect.minY + titleY + 14
            )
            .accessibilityElement().accessibilityLabel("Your parcel is ready").allowsHitTesting(false)
            Color.clear.frame(width: geometry.width - 76, height: 19).position(
                x: geometry.width / 2, y: rect.minY + subY + 9.5
            )
            .accessibilityElement().accessibilityLabel("Order 48-1173 · picked up by 9 Nov").allowsHitTesting(false)
            Color.clear.frame(width: 246, height: 50).position(x: 161, y: rect.minY + codeY + 25)
                .accessibilityElement().accessibilityLabel("Pickup code 4 8 1 7").allowsHitTesting(false)
        } else {
            Color.clear.frame(width: geometry.width - 76, height: 160).position(
                x: geometry.width / 2, y: rect.minY + 80
            )
            .accessibilityElement().accessibilityLabel("Picked up at 6:42 PM. Locker 14B closed.").allowsHitTesting(
                false)
        }
    }
}

/// Bridges UIKit gesture states into the scene's shared interaction clock.
struct ParcelLockerPickupTouchView: UIViewRepresentable {
    var event: (UIGestureRecognizer.State, CGPoint) -> Void

    func makeUIView(context: Context) -> ParcelLockerPickupTouchSurface {
        let surface = ParcelLockerPickupTouchSurface()
        surface.event = event
        return surface
    }

    func updateUIView(_ surface: ParcelLockerPickupTouchSurface, context: Context) {
        surface.event = event
    }
}
/// A zero-delay press recognizer supplies taps and drags without delaying touch-down.
final class ParcelLockerPickupTouchSurface: UIView, UIGestureRecognizerDelegate {
    var event: ((UIGestureRecognizer.State, CGPoint) -> Void)?
    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .clear
        isOpaque = false
        let recognizer = UILongPressGestureRecognizer(target: self, action: #selector(handle(_:)))
        recognizer.minimumPressDuration = 0
        recognizer.allowableMovement = .greatestFiniteMagnitude
        recognizer.cancelsTouchesInView = false
        recognizer.delegate = self
        addGestureRecognizer(recognizer)
    }
    required init?(coder: NSCoder) {
        nil
    }

    func gestureRecognizer(
        _ gestureRecognizer: UIGestureRecognizer,
        shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
    ) -> Bool {
        true
    }
    @objc private func handle(_ recognizer: UILongPressGestureRecognizer) {
        event?(recognizer.state, recognizer.location(in: self))
    }
}
