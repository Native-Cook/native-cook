import SwiftUI
import UIKit

/// The touch surface: chips, the follow button, the photo strip (swipe) and the four stat tiles (hold).
/// Each zone listens with a zero-delay long-press recognizer that runs alongside the gallery's edge-pan
/// recognizer (a plain SwiftUI drag is cancelled by it after about 24 pt).
struct TasteProfileTouch: UIViewRepresentable {
    let engine: TasteProfileEngine
    let geo: TasteProfileGeo
    let city: Int
    let following: Bool
    let onTouch: () -> Void

    func makeUIView(context: Context) -> Surface { Surface(engine: engine) }

    func updateUIView(_ view: Surface, context: Context) {
        view.geo = geo
        view.city = city
        view.following = following
        view.onTouch = onTouch
        engine.setGeometry(cell: geo.p(TasteProfileTheme.Box.strip.w / 4), unit: geo.scale,
                           tileCardX: (0..<4).map { TasteProfileChart.rect(tile: $0).x })
        view.setNeedsLayout()
        view.refreshAccessibility()
    }

    final class Zone: UIView, UIGestureRecognizerDelegate {
        var began: ((CGPoint) -> Void)?
        var moved: ((CGPoint) -> Void)?
        var ended: ((CGPoint, Bool) -> Void)?
        var activate: (() -> Bool)?

        override init(frame: CGRect) {
            super.init(frame: frame)
            backgroundColor = .clear
            let recognizer = UILongPressGestureRecognizer(target: self, action: #selector(handle(_:)))
            recognizer.minimumPressDuration = 0
            recognizer.allowableMovement = .greatestFiniteMagnitude
            recognizer.cancelsTouchesInView = false
            recognizer.delegate = self
            addGestureRecognizer(recognizer)
        }

        required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

        func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer,
                               shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer) -> Bool { true }

        override func accessibilityActivate() -> Bool { activate?() ?? false }

        @objc private func handle(_ recognizer: UILongPressGestureRecognizer) {
            let point = recognizer.location(in: superview)
            switch recognizer.state {
            case .began: began?(point)
            case .changed: moved?(point)
            case .ended: ended?(point, false)
            case .cancelled, .failed: ended?(point, true)
            default: break
            }
        }
    }

    final class Surface: UIView {
        let engine: TasteProfileEngine
        var geo = TasteProfileGeo(width: 1, height: 1, scale: 1, pointScale: 1, y0: 0, pixel: 1)
        var city = 0
        var following = false
        var onTouch: () -> Void = {}

        private let chips = (0..<4).map { _ in Zone() }
        private let followZone = Zone()
        private let stripZone = Zone()
        private let tiles = (0..<4).map { _ in Zone() }

        private var stripStart = CGPoint.zero
        private var stripActive = false
        private var stripFailed = false
        private var samples: [(t: Double, x: Double)] = []
        private var tileStart = CGPoint.zero
        private var tileFailed = false

        init(engine: TasteProfileEngine) {
            self.engine = engine
            super.init(frame: .zero)
            backgroundColor = .clear
            for (index, zone) in chips.enumerated() { setUpChip(zone, index) }
            setUpFollow()
            setUpStrip()
            for (index, zone) in tiles.enumerated() { setUpTile(zone, index) }
            (chips + [followZone, stripZone] + tiles).forEach { addSubview($0) }
        }

        required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

        private var now: Double { Date.timeIntervalSinceReferenceDate }

        override func point(inside point: CGPoint, with event: UIEvent?) -> Bool {
            subviews.contains { $0.frame.contains(point) }
        }

        override func layoutSubviews() {
            super.layoutSubviews()
            let box = TasteProfileTheme.Box.self
            for (index, chip) in box.chips.enumerated() {
                chips[index].frame = CGRect(x: geo.p(chip.x), y: geo.y(box.chipY) - 6, width: geo.p(chip.w), height: geo.p(box.chipH) + 12)
            }
            followZone.frame = CGRect(x: geo.p(box.follow.x) - 4, y: geo.y(box.follow.y) - 6,
                                      width: geo.p(box.follow.w) + 4, height: geo.p(box.follow.h) + 12)
            stripZone.frame = CGRect(x: geo.p(box.strip.x), y: geo.y(box.strip.y), width: geo.p(box.strip.w), height: geo.p(box.strip.h))
            for (index, x) in box.tiles.enumerated() {
                tiles[index].frame = CGRect(x: geo.p(x), y: geo.y(box.tileY), width: geo.p(box.tileW), height: geo.p(box.tileH))
            }
        }

        func refreshAccessibility() {
            for (index, zone) in chips.enumerated() {
                zone.isAccessibilityElement = true
                zone.accessibilityLabel = TasteProfileMotion.cities[index]
                zone.accessibilityTraits = index == city ? [.button, .selected] : .button
            }
            followZone.isAccessibilityElement = true
            followZone.accessibilityLabel = following ? "Following" : "Follow"
            followZone.accessibilityTraits = following ? [.button, .selected] : .button
            stripZone.isAccessibilityElement = true
            stripZone.accessibilityLabel = "Photos"
            for (index, zone) in tiles.enumerated() {
                zone.isAccessibilityElement = true
                zone.accessibilityLabel = "\(TasteProfileMotion.stats[city][index]) \(TasteProfileTheme.tileLabels[index].text)"
                zone.accessibilityHint = "Hold to see the last six months"
            }
        }

        // MARK: Zones

        private func setUpChip(_ zone: Zone, _ index: Int) {
            zone.began = { [weak self] _ in
                guard let self else { return }
                engine.chipDown(index, now: now)
                onTouch()
            }
            zone.ended = { [weak self, weak zone] point, cancelled in
                guard let self, let zone else { return }
                engine.chipUp(now: now)
                if !cancelled, zone.frame.insetBy(dx: -20, dy: -20).contains(point) { engine.chipTap(index, now: now) }
                onTouch()
            }
            zone.activate = { [weak self] in
                guard let self else { return false }
                engine.chipTap(index, now: now)
                onTouch()
                return true
            }
        }

        private func setUpFollow() {
            followZone.began = { [weak self] _ in
                guard let self else { return }
                engine.followDown(now: now)
                onTouch()
            }
            followZone.ended = { [weak self] point, cancelled in
                guard let self else { return }
                engine.followUp(now: now)
                if !cancelled, followZone.frame.insetBy(dx: -20, dy: -20).contains(point) { engine.followTap(now: now) }
                onTouch()
            }
            followZone.activate = { [weak self] in
                guard let self else { return false }
                engine.followTap(now: now)
                onTouch()
                return true
            }
        }

        private func setUpStrip() {
            stripZone.began = { [weak self] point in
                guard let self else { return }
                engine.stripBegin(now: now)
                stripStart = point
                stripActive = false
                stripFailed = false
                samples = [(now, point.x)]
                onTouch()
            }
            stripZone.moved = { [weak self] point in
                guard let self, !stripFailed else { return }
                let dx = point.x - stripStart.x
                let dy = point.y - stripStart.y
                if !stripActive {
                    if abs(dy) > 12 {
                        stripFailed = true
                        engine.stripFinalize(now: now)
                        onTouch()
                        return
                    }
                    if abs(dx) > 8 {
                        stripActive = true
                        engine.stripStart(now: now)
                    }
                }
                if stripActive {
                    samples.append((now, point.x))
                    if samples.count > 8 { samples.removeFirst() }
                    engine.stripMove(translation: dx, now: now)
                }
                onTouch()
            }
            stripZone.ended = { [weak self] _, cancelled in
                guard let self, !stripFailed else { return }
                if stripActive { engine.stripEnd(velocity: cancelled ? 0 : velocity(), now: now) }
                engine.stripFinalize(now: now)
                onTouch()
            }
        }

        /// Points per second over the last 100 ms; a finger that rested before lifting has none.
        private func velocity() -> Double {
            guard let last = samples.last, now - last.t < 0.08 else { return 0 }
            let recent = samples.filter { last.t - $0.t <= 0.1 }
            guard let first = recent.first, last.t > first.t else { return 0 }
            return (last.x - first.x) / (last.t - first.t)
        }

        private func setUpTile(_ zone: Zone, _ index: Int) {
            zone.began = { [weak self] point in
                guard let self else { return }
                tileStart = point
                tileFailed = false
                engine.tileDown(index, now: now)
                onTouch()
            }
            zone.moved = { [weak self] point in
                guard let self, !tileFailed else { return }
                if engine.tileGrown {
                    engine.tileMove(x: point.x, tile: index, now: now)
                } else if hypot(point.x - tileStart.x, point.y - tileStart.y) > 10 {
                    tileFailed = true
                    engine.tileEnd(now: now)
                }
                onTouch()
            }
            zone.ended = { [weak self] _, _ in
                guard let self else { return }
                if !tileFailed { engine.tileEnd(now: now) }
                onTouch()
            }
        }
    }
}
