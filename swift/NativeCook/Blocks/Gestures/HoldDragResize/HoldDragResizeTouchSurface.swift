import SwiftUI
import UIKit

struct HoldDragResizeTouchSurface: UIViewRepresentable {
    let interaction: HoldDragResizeInteraction
    let layout: HoldDragResizeLayout
    let autoPlay: Bool
    let reduceMotion: Bool

    func makeUIView(context: Context) -> HoldDragResizeTouchUIView {
        let view = HoldDragResizeTouchUIView()
        view.backgroundColor = .clear
        view.isMultipleTouchEnabled = false
        view.update(interaction: interaction, layout: layout, autoPlay: autoPlay, reduceMotion: reduceMotion)
        return view
    }

    func updateUIView(_ view: HoldDragResizeTouchUIView, context: Context) {
        view.update(interaction: interaction, layout: layout, autoPlay: autoPlay, reduceMotion: reduceMotion)
    }
}

final class HoldDragResizeTouchUIView: UIView {
    private weak var interaction: HoldDragResizeInteraction?
    private var layout = HoldDragResizeLayout(width: 393, height: 852, top: 59, bottom: 34)
    private var autoPlay = false
    private var reduceMotion = false
    private var holding = false
    private var touchActive = false

    override init(frame: CGRect) {
        super.init(frame: frame)
        let hold = UILongPressGestureRecognizer(target: self, action: #selector(handleHold(_:)))
        hold.minimumPressDuration = HoldDragResizeMetrics.holdDuration
        hold.allowableMovement = .greatestFiniteMagnitude
        hold.cancelsTouchesInView = false
        addGestureRecognizer(hold)
    }

    required init?(coder: NSCoder) {
        nil
    }

    @MainActor
    func update(
        interaction: HoldDragResizeInteraction,
        layout: HoldDragResizeLayout,
        autoPlay: Bool,
        reduceMotion: Bool
    ) {
        self.interaction = interaction
        self.layout = layout
        self.autoPlay = autoPlay
        self.reduceMotion = reduceMotion
    }

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        super.touchesBegan(touches, with: event)
        guard let point = touches.first?.location(in: self) else { return }
        holding = false
        touchActive = true
        interaction?.touchBegan(at: point, layout: layout, autoPlay: autoPlay, reduceMotion: reduceMotion)
    }

    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        super.touchesEnded(touches, with: event)
        if touchActive, !holding {
            interaction?.touchEnded(reduceMotion: reduceMotion, autoPlay: autoPlay)
        }
        touchActive = false
    }

    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) {
        super.touchesCancelled(touches, with: event)
        if touchActive, !holding {
            interaction?.touchEnded(reduceMotion: reduceMotion, autoPlay: autoPlay)
        }
        touchActive = false
    }

    @objc private func handleHold(_ gesture: UILongPressGestureRecognizer) {
        guard let interaction else { return }
        let point = gesture.location(in: self)
        switch gesture.state {
        case .began:
            holding = true
            interaction.holdBegan(at: point, layout: layout, reduceMotion: reduceMotion)
        case .changed:
            interaction.holdMoved(to: point, layout: layout, reduceMotion: reduceMotion)
        case .ended, .cancelled, .failed:
            if holding {
                interaction.touchEnded(reduceMotion: reduceMotion, autoPlay: autoPlay)
            }
            holding = false
            touchActive = false
        default:
            break
        }
    }
}

