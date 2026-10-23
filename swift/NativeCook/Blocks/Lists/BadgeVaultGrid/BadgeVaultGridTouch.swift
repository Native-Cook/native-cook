import QuartzCore
import SwiftUI
import UIKit

struct BadgeVaultGridTouchView: UIViewRepresentable {
    let engine: BadgeVaultGridEngine
    let share: () -> Void

    func makeUIView(context: Context) -> BadgeVaultGridTouchSurface {
        let view = BadgeVaultGridTouchSurface()
        view.engine = engine
        view.share = share
        view.backgroundColor = .clear
        view.isOpaque = false
        return view
    }

    func updateUIView(_ view: BadgeVaultGridTouchSurface, context: Context) {
        view.engine = engine
        view.share = share
    }
}

final class BadgeVaultGridTouchSurface: UIView, UIGestureRecognizerDelegate {
    weak var engine: BadgeVaultGridEngine?
    var share: (() -> Void)?
    private var start = CGPoint.zero
    private var previous = CGPoint.zero
    private var previousTime = 0.0
    private var target = -1
    private var mode = ""
    private var axis = 0
    private var startSpin = 0.0
    private var startScroll = 0.0
    private var velocity = CGPoint.zero

    override init(frame: CGRect) {
        super.init(frame: frame)
        let recognizer = UILongPressGestureRecognizer(target: self, action: #selector(handle(_:)))
        recognizer.minimumPressDuration = 0
        recognizer.allowableMovement = .greatestFiniteMagnitude
        recognizer.cancelsTouchesInView = false
        recognizer.delegate = self
        addGestureRecognizer(recognizer)
        isAccessibilityElement = false
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer,
                           shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool { true }

    @objc private func handle(_ recognizer: UILongPressGestureRecognizer) {
        guard let engine else { return }
        let point = recognizer.location(in: self)
        let time = CACurrentMediaTime()
        switch recognizer.state {
        case .began:
            start = point
            previous = point
            previousTime = time
            axis = 0
            velocity = .zero
            startScroll = engine.scrollY
            startSpin = engine.hero.pose.spin
            engine.touch(true)
            if engine.selected >= 0 {
                mode = "hero"
                target = -1
            } else if point.x >= engine.layout.width - 156 &&
                        point.y >= engine.layout.footerY - engine.scrollY + 24 &&
                        point.y <= engine.layout.footerY - engine.scrollY + 72 {
                mode = "pill"
                engine.action("pill-in")
            } else {
                mode = "grid"
                let row = Int(floor((Double(point.y) + engine.scrollY - engine.layout.headerHeight) / engine.layout.pitch))
                let column = (0..<3).first { index in
                    let col = engine.layout.column(index)
                    return Double(point.x) >= col.x && Double(point.x) < col.x + col.width
                } ?? -1
                target = row >= 0 && row < 6 && column >= 0 ? row * 3 + column : -1
                if target >= 0 {
                    let col = engine.layout.column(column)
                    let localX = (Double(point.x) - col.x - col.width / 2) / engine.layout.radius
                    let localY = (Double(point.y) + engine.scrollY - engine.layout.headerHeight -
                                  Double(row) * engine.layout.pitch - engine.layout.badgeY) / engine.layout.radius
                    engine.action("press", index: target, x: localX, y: localY)
                }
            }
        case .changed:
            let dt = max(0.001, time - previousTime)
            velocity = CGPoint(x: (point.x - previous.x) / dt, y: (point.y - previous.y) / dt)
            previous = point
            previousTime = time
            let dx = Double(point.x - start.x)
            let dy = Double(point.y - start.y)
            if mode == "grid" && abs(dy) > 8 {
                engine.scrollY = BadgeVaultGridMath.clamp(startScroll - dy, 0, engine.layout.scrollEnd)
            }
            if mode == "hero" && abs(dx) + abs(dy) > 10 {
                if axis == 0 { axis = abs(dx) > abs(dy) ? 1 : 2 }
                if !engine.reduced && axis == 1 {
                    engine.turnMode = "drag"
                    engine.turn.x = startSpin + dx / (2 * engine.layout.heldR) * .pi
                } else if axis == 2 {
                    engine.dragging = true
                    engine.drag = .init(x: BadgeVaultGridMath.resistDrag(dy), v: 0)
                }
            }
        case .ended, .cancelled, .failed:
            let dx = Double(point.x - start.x)
            let dy = Double(point.y - start.y)
            if mode == "grid" {
                if target >= 0 { engine.action("release", index: target) }
                if abs(dx) + abs(dy) < 12 && target >= 0 { engine.action("open", index: target) }
                else if abs(dy) > 8 { engine.inertia.v = -Double(velocity.y) }
            } else if mode == "pill" {
                engine.action("pill-out")
                if abs(dx) + abs(dy) < 15 && engine.count < 18 { engine.action("log") }
            } else if mode == "hero" {
                if axis == 1 && !engine.reduced {
                    engine.turn = BadgeVaultGridMath.flickSpin(engine.turn.x,
                        Double(velocity.x) / (2 * engine.layout.heldR) * .pi, front: false)
                    engine.turnMode = "free"
                } else if axis == 2 {
                    engine.dragging = false
                    engine.drag.v = Double(velocity.y)
                    if dy > 90 || velocity.y > 700 { engine.action("close", y: Double(velocity.y)) }
                } else if abs(dx) + abs(dy) < 12 {
                    if point.x < 56 && point.y < engine.layout.safeTop + 44 {
                        engine.action("close")
                    } else if point.y >= engine.layout.shareY - 4 {
                        share?()
                    } else {
                        let pose = engine.hero.pose
                        let bx = Double(point.x) - pose.cx
                        let by = Double(point.y) - pose.cy
                        if bx * bx + by * by > pose.r * pose.r * 1.5 { engine.action("close") }
                    }
                }
            }
            engine.dragging = false
            engine.touch(false)
            mode = ""
        default: break
        }
    }
}
