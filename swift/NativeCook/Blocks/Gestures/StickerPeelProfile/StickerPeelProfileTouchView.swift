import SwiftUI
import UIKit

struct StickerPeelProfileTouchView: UIViewRepresentable {
    var clock: StickerPeelProfileClock
    var reduced: Bool
    func makeUIView(context: Context) -> UIView {
        let v = UIView(); v.backgroundColor = .clear
        let pan = UILongPressGestureRecognizer(target:context.coordinator,action:#selector(StickerPeelProfileTouchCoordinator.pan(_:)))
        pan.minimumPressDuration = 0; pan.allowableMovement = .greatestFiniteMagnitude
        let pinch = UIPinchGestureRecognizer(target:context.coordinator,action:#selector(StickerPeelProfileTouchCoordinator.pinch(_:)))
        let rotation = UIRotationGestureRecognizer(target:context.coordinator,action:#selector(StickerPeelProfileTouchCoordinator.rotate(_:)))
        for g in [pan,pinch,rotation] { g.delegate = context.coordinator; v.addGestureRecognizer(g) }
        v.isAccessibilityElement = true
        v.accessibilityLabel = "Profile stickers: a bun, a heart, a slay label and a flame. Drag one to peel it off and stick it somewhere else; use two fingers to turn or resize it."
        return v
    }
    func updateUIView(_ uiView: UIView,context: Context) { context.coordinator.reduced = reduced }
    func makeCoordinator() -> StickerPeelProfileTouchCoordinator { StickerPeelProfileTouchCoordinator(clock,reduced) }
}
