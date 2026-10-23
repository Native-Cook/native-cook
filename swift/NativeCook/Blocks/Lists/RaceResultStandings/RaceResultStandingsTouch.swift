import SwiftUI
import UIKit

/// A simultaneous zero-delay recognizer survives the gallery's 24pt drag recognition.
struct RaceResultStandingsTouch: UIViewRepresentable {
    let controller: RaceResultStandingsController
    let snapshot: RaceResultStandingsController.Snapshot
    let scale: Double
    let safeTop: Double

    func makeUIView(context: Context) -> Surface { Surface() }
    func updateUIView(_ view: Surface, context: Context) {
        view.controller = controller; view.scale = scale; view.safeTop = safeTop
        view.updateAccessibility(snapshot)
    }

    final class ActionElement: UIAccessibilityElement {
        var action: (() -> Void)?
        override func accessibilityActivate() -> Bool { action?(); return action != nil }
    }

    final class Surface: UIView, UIGestureRecognizerDelegate {
        weak var controller: RaceResultStandingsController?
        var scale = 1.0, safeTop = 0.0
        private var start = CGPoint.zero, previous = CGPoint.zero
        private var previousTime = 0.0
        private var velocity = CGPoint.zero
        private var elements: [ActionElement] = []

        override init(frame: CGRect) {
            super.init(frame: frame)
            let recognizer = UILongPressGestureRecognizer(target: self, action: #selector(handle(_:)))
            recognizer.minimumPressDuration = 0
            recognizer.allowableMovement = .greatestFiniteMagnitude
            recognizer.cancelsTouchesInView = false
            recognizer.delegate = self
            addGestureRecognizer(recognizer)
            isOpaque = false; backgroundColor = .clear; isAccessibilityElement = false
            for _ in 0..<17 { elements.append(ActionElement(accessibilityContainer: self)) }
            accessibilityElements = elements
        }
        required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
        func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool { true }

        @objc private func handle(_ recognizer: UILongPressGestureRecognizer) {
            guard let controller else { return }
            let point = recognizer.location(in: self), now = Date.timeIntervalSinceReferenceDate
            switch recognizer.state {
            case .began:
                start = point; previous = point; previousTime = now; velocity = .zero
                controller.begin(at: now, location: point, scale: scale, safeTop: safeTop)
            case .changed:
                let dt = max(0.001, now - previousTime)
                velocity = CGPoint(x: (point.x - previous.x) / dt, y: (point.y - previous.y) / dt)
                previous = point; previousTime = now
                controller.move(at: now, translation: CGPoint(x: point.x - start.x, y: point.y - start.y), width: bounds.width)
            case .ended, .cancelled, .failed:
                // A held finger has no release velocity, even if its last move was fast.
                if now - previousTime > 0.08 { velocity = .zero }
                controller.end(at: now, translation: CGPoint(x: point.x - start.x, y: point.y - start.y), velocity: velocity, cancelled: recognizer.state != .ended)
            default: break
            }
        }
        func updateAccessibility(_ snapshot: RaceResultStandingsController.Snapshot) {
            let session = RaceResultStandingsData.sessions[snapshot.shown]
            accessibilityElements = controller?.reduced == true ? Array(elements.prefix(16)) : elements
            func element(_ index: Int, _ label: String, _ rect: CGRect, button: Bool = false) {
                let item = elements[index]
                item.accessibilityLabel = label
                item.accessibilityTraits = button ? .button : .staticText
                item.accessibilityFrameInContainerSpace = CGRect(x: rect.minX * scale, y: safeTop + (rect.minY - 62) * scale, width: rect.width * scale, height: rect.height * scale)
            }
            element(0, "Back", CGRect(x: 8.005, y: 61.335, width: 42.33, height: 42.33), button: true)
            elements[0].action = { [weak controller] in controller?.accessibilityBack(at: Date.timeIntervalSinceReferenceDate) }
            element(1, "\(session.word) results", CGRect(x: 111.92, y: 66.44544, width: 180.41, height: 44), button: true)
            elements[1].accessibilityHint = "Switches to \(RaceResultStandingsData.sessions[1 - snapshot.shown].word) results"
            elements[1].action = { [weak controller] in controller?.accessibilityToggle(at: Date.timeIntervalSinceReferenceDate) }
            element(2, "Round 09", CGRect(x: 20.34, y: 132, width: 90, height: 20))
            element(3, "Harbor GP", CGRect(x: 20.07, y: 152, width: 200, height: 30))
            for slot in 0..<3 {
                let driver = RaceResultStandingsData.drivers[session.order[slot]]
                element(4 + slot, driver.code, CGRect(x: [54.89,126.22,199.22][slot], y: 199, width: 30, height: 16.33))
            }
            element(7, "\(session.fastest), Fastest Lap", CGRect(x: 53.26, y: 244, width: 240, height: 34))
            element(8, "Mira Chen, Driver of the Day", CGRect(x: 53.13, y: 292, width: 150, height: 34))
            element(9, "Tidewave (2.140s), Fastest Pitstop", CGRect(x: 226.26, y: 292, width: 155, height: 34))
            // During the replay the rows are read in running order: "n. Name, Team, racing", and the points
            // again once the last car has taken the flag.
            let places = (0..<6).map { RaceResultStandingsMotion.replayPlace($0, replay: snapshot.frame.replay) }
            for slot in 0..<6 {
                var index = session.order[slot]
                var flagged = true
                if places.contains(where: { $0 >= 0 }), let racing = places.firstIndex(where: { $0 % RaceResultStandingsMotion.replayFlagged == slot }) {
                    index = racing
                    flagged = places[racing] >= RaceResultStandingsMotion.replayFlagged
                }
                let driver = RaceResultStandingsData.drivers[index]
                element(10 + slot, "\(slot + 1). \(driver.name), \(driver.team.name)" + (flagged ? ", \(session.points[slot]) points" : ", racing"), CGRect(x: 0, y: 364.68 + Double(slot) * 83.15, width: 402, height: 83.15))
            }
            element(16, "Replay the race", CGRect(x: 295, y: 145.67, width: 90, height: 96.67), button: true)
            elements[16].action = { [weak controller] in
                controller?.accessibilityReplay(at: Date.timeIntervalSinceReferenceDate)
            }
        }
    }
}
