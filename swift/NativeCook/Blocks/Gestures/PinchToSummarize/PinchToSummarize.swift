import SwiftUI
import UIKit

/// Pinch an article into an AI summary; spread the card, tap it, or close it to read the article again.
struct PinchToSummarize: View {
    static let loop: Double = 11

    var autoPlay: Bool

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var motion: PinchToSummarizeMotion

    init(autoPlay: Bool = false) {
        self.autoPlay = autoPlay
        _motion = State(initialValue: PinchToSummarizeMotion(autoPlay: autoPlay))
    }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let size = CGSize(
                width: proxy.size.width + insets.leading + insets.trailing,
                height: proxy.size.height + insets.top + insets.bottom
            )
            let stage = PinchToSummarizeStage(
                width: size.width,
                height: size.height,
                insetTop: insets.top
            )

            if stage.width > 0, stage.height > 0 {
                TimelineView(.animation(paused: !motion.needsFrames)) { timeline in
                    PinchToSummarizeScene(
                        motion: motion,
                        stage: stage,
                        insetBottom: insets.bottom,
                        snapshot: motion.snapshot,
                        reduceMotion: reduceMotion
                    )
                    .onChange(of: timeline.date, initial: true) { _, date in
                        motion.step(to: date)
                    }
                }
                .onChange(of: stage, initial: true) { _, nextStage in
                    motion.configure(
                        stage: nextStage,
                        reduceMotion: reduceMotion,
                        revealTotal: PinchToSummarizeSummary.revealTotal(for: nextStage.width)
                    )
                }
                .onChange(of: reduceMotion, initial: true) { _, reduced in
                    motion.configure(
                        stage: stage,
                        reduceMotion: reduced,
                        revealTotal: PinchToSummarizeSummary.revealTotal(for: stage.width)
                    )
                }
                .onChange(of: autoPlay, initial: true) { _, plays in
                    motion.autoPlay = plays
                }
                .frame(width: stage.width, height: stage.height, alignment: .topLeading)
                .background(PinchToSummarizeTheme.Colors.page)
                .clipped()
                .offset(x: -insets.leading, y: -insets.top)
            }
        }
        .onAppear(perform: PinchToSummarizeAssets.assertLoaded)
        .preferredColorScheme(motion.mode == .reading ? .light : .dark)
    }
}

private struct PinchToSummarizeScene: View {
    let motion: PinchToSummarizeMotion
    let stage: PinchToSummarizeStage
    let insetBottom: CGFloat
    let snapshot: PinchToSummarizeSnapshot
    let reduceMotion: Bool

    var body: some View {
        ZStack(alignment: .topLeading) {
            PinchToSummarizeFieldCanvas(
                stage: stage,
                snapshot: snapshot,
                reduceMotion: reduceMotion
            )

            PinchToSummarizeArticle(
                stage: stage,
                insetBottom: insetBottom,
                snapshot: snapshot,
                scrollLocked: motion.scrollLocked,
                onSummarize: motion.summarize,
                onShowArticle: motion.showArticle
            )

            PinchToSummarizeSummary(
                stage: stage,
                insetBottom: insetBottom,
                snapshot: snapshot,
                onShowArticle: motion.showArticle
            )

            PinchToSummarizeLightCanvas(
                stage: stage,
                snapshot: snapshot,
                reduceMotion: reduceMotion
            )
        }
        .frame(width: stage.width, height: stage.height, alignment: .topLeading)
        .contentShape(Rectangle())
        .overlay {
            PinchToSummarizeInteractionBridge(motion: motion)
                .frame(width: stage.width, height: stage.height)
        }
    }
}

/// Attaches UIKit recognizers to the scene's container instead of placing a hit-testing view above
/// SwiftUI. The recognizers therefore see the live pinch centroid and velocity while buttons,
/// article scrolling, and the gallery's back swipe keep receiving the same touches.
private struct PinchToSummarizeInteractionBridge: UIViewRepresentable {
    let motion: PinchToSummarizeMotion

    func makeCoordinator() -> Coordinator {
        Coordinator(motion: motion)
    }

    func makeUIView(context: Context) -> PinchToSummarizeInteractionView {
        let view = PinchToSummarizeInteractionView()
        view.backgroundColor = .clear
        view.isUserInteractionEnabled = false
        view.coordinator = context.coordinator
        return view
    }

    func updateUIView(_ view: PinchToSummarizeInteractionView, context: Context) {
        context.coordinator.motion = motion
        view.installRecognizerHost()
    }

    static func dismantleUIView(_ view: PinchToSummarizeInteractionView, coordinator: Coordinator) {
        coordinator.detach()
        view.coordinator = nil
    }

    @MainActor
    final class Coordinator: NSObject, UIGestureRecognizerDelegate {
        var motion: PinchToSummarizeMotion
        private weak var hostView: UIView?
        private weak var coordinateView: UIView?
        private let pinch = UIPinchGestureRecognizer()
        private let touch = UILongPressGestureRecognizer()

        init(motion: PinchToSummarizeMotion) {
            self.motion = motion
            super.init()

            pinch.addTarget(self, action: #selector(pinched(_:)))
            pinch.cancelsTouchesInView = false
            pinch.delaysTouchesBegan = false
            pinch.delaysTouchesEnded = false
            pinch.delegate = self

            touch.addTarget(self, action: #selector(touched(_:)))
            touch.minimumPressDuration = 0
            touch.allowableMovement = 10_000
            touch.cancelsTouchesInView = false
            touch.delaysTouchesBegan = false
            touch.delaysTouchesEnded = false
            touch.delegate = self
        }

        func attach(to view: UIView?, coordinateView: UIView) {
            self.coordinateView = coordinateView
            guard let view, hostView !== view else { return }
            detach()
            hostView = view
            view.addGestureRecognizer(pinch)
            view.addGestureRecognizer(touch)
        }

        func detach() {
            hostView?.removeGestureRecognizer(pinch)
            hostView?.removeGestureRecognizer(touch)
            hostView = nil
        }

        @objc private func pinched(_ recognizer: UIPinchGestureRecognizer) {
            guard let coordinateView else { return }
            let focal = recognizer.location(in: coordinateView)
            switch recognizer.state {
            case .began:
                motion.touch(true)
                motion.pinchBegin(focal: focal, scripted: false)
                motion.pinchMove(scale: recognizer.scale, focal: focal)
            case .changed:
                motion.pinchMove(scale: recognizer.scale, focal: focal)
            case .ended:
                motion.pinchMove(scale: recognizer.scale, focal: focal)
                motion.pinchEnd(velocity: recognizer.velocity, cancelled: false)
                motion.touch(false)
            case .cancelled, .failed:
                motion.pinchEnd(velocity: 0, cancelled: true)
                motion.touch(false)
            default:
                break
            }
        }

        @objc private func touched(_ recognizer: UILongPressGestureRecognizer) {
            switch recognizer.state {
            case .began:
                motion.touch(true)
            case .ended, .cancelled, .failed:
                // If a two-finger gesture is still active, its own finalizer owns the release.
                if pinch.state != .began, pinch.state != .changed {
                    motion.touch(false)
                }
            default:
                break
            }
        }

        func gestureRecognizer(
            _ gestureRecognizer: UIGestureRecognizer,
            shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
        ) -> Bool {
            true
        }

        func gestureRecognizer(
            _ gestureRecognizer: UIGestureRecognizer,
            shouldBeRequiredToFailBy otherGestureRecognizer: UIGestureRecognizer
        ) -> Bool {
            false
        }

        func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
            guard let coordinateView, coordinateView.window != nil else { return false }
            return coordinateView.bounds.contains(gestureRecognizer.location(in: coordinateView))
        }
    }
}

@MainActor
private final class PinchToSummarizeInteractionView: UIView {
    weak var coordinator: PinchToSummarizeInteractionBridge.Coordinator?

    override func didMoveToSuperview() {
        super.didMoveToSuperview()
        if superview == nil {
            coordinator?.detach()
        } else {
            installRecognizerHost()
        }
    }


    override func didMoveToWindow() {
        super.didMoveToWindow()
        installRecognizerHost()
    }

    func installRecognizerHost() {
        guard let coordinator else { return }
        guard let window else {
            if superview == nil { coordinator.detach() }
            return
        }
        // The app's hosting view is a common ancestor of SwiftUI controls and UIKit-backed
        // ScrollViews. Installing there observes the same touches without putting a view over them.
        var ancestor: UIView? = superview
        var hostingView: UIView?
        while let current = ancestor, current !== window {
            if NSStringFromClass(type(of: current)).contains("Hosting") {
                hostingView = current
            }
            ancestor = current.superview
        }
        coordinator.attach(to: hostingView ?? window, coordinateView: self)
    }
}
