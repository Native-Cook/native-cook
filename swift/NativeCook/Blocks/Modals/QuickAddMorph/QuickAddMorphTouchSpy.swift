import SwiftUI
import UIKit

/// Reports how many fingers are on the window, like RN's `onTouchStart` / `onTouchEnd` on the
/// screen's root view, without taking any touch away from the controls under it.
struct QuickAddMorphTouchSpy: UIViewRepresentable {
    var onChange: (Int) -> Void

    func makeUIView(context: Context) -> QuickAddMorphTouchSpyView {
        let view = QuickAddMorphTouchSpyView()
        view.onChange = onChange
        return view
    }

    func updateUIView(_ view: QuickAddMorphTouchSpyView, context: Context) {
        view.onChange = onChange
    }

    static func dismantleUIView(_ view: QuickAddMorphTouchSpyView, coordinator: ()) {
        view.detach()
    }
}

final class QuickAddMorphTouchSpyView: UIView {
    var onChange: ((Int) -> Void)?
    private let recognizer = QuickAddMorphTouchRecognizer()
    private weak var host: UIWindow?

    override init(frame: CGRect) {
        super.init(frame: frame)
        isUserInteractionEnabled = false
        recognizer.report = { [weak self] count in self?.onChange?(count) }
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) { nil }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        detach()
        if let window {
            window.addGestureRecognizer(recognizer)
            host = window
        }
    }

    func detach() {
        host?.removeGestureRecognizer(recognizer)
        host = nil
    }
}

/// Never recognizes: it only watches touches go by, then fails so nothing else is held up.
final class QuickAddMorphTouchRecognizer: UIGestureRecognizer, UIGestureRecognizerDelegate {
    var report: ((Int) -> Void)?
    private var down = Set<ObjectIdentifier>()

    init() {
        super.init(target: nil, action: nil)
        cancelsTouchesInView = false
        delaysTouchesBegan = false
        delaysTouchesEnded = false
        delegate = self
    }

    func gestureRecognizer(_ g: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer) -> Bool {
        true
    }

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent) {
        for touch in touches { down.insert(ObjectIdentifier(touch)) }
        report?(down.count)
    }

    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent) {}

    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent) {
        for touch in touches { down.remove(ObjectIdentifier(touch)) }
        report?(down.count)
        if down.isEmpty { state = .failed }
    }

    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent) {
        for touch in touches { down.remove(ObjectIdentifier(touch)) }
        report?(down.count)
        if down.isEmpty { state = .failed }
    }

    override func reset() {
        super.reset()
        if !down.isEmpty {
            down.removeAll()
            report?(0)
        }
    }
}
