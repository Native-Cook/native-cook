import QuartzCore
import SwiftUI
import UIKit

struct SendMoneyCardsTouch: UIViewRepresentable {
    let model: SendMoneyCardsModel
    let width: Double
    let height: Double
    let offsetY: Double

    func makeUIView(context: Context) -> Surface {
        let view = Surface()
        view.model = model
        view.backgroundColor = .clear
        view.isOpaque = false
        return view
    }

    func updateUIView(_ view: Surface, context: Context) {
        view.model = model
        view.width = width
        view.height = height
        view.offsetY = offsetY
    }

    final class Surface: UIView, UIGestureRecognizerDelegate {
        weak var model: SendMoneyCardsModel?
        var width = 402.0
        var height = 874.0
        var offsetY = 0.0
        private var start = CGPoint.zero
        private var previous = CGPoint.zero
        private var previousTime = 0.0
        private var velocityY = 0.0
        private var mode = Mode.none
        private var target: String?

        private enum Mode {
            case none
            case card
            case sheet
            case close
        }

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

        required init?(coder: NSCoder) {
            fatalError("init(coder:) has not been implemented")
        }

        func gestureRecognizer(
            _ gestureRecognizer: UIGestureRecognizer,
            shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
        ) -> Bool {
            true
        }

        @objc private func handle(_ recognizer: UILongPressGestureRecognizer) {
            guard let model else { return }
            let point = recognizer.location(in: self)
            let time = CACurrentMediaTime()
            switch recognizer.state {
            case .began:
                start = point
                previous = point
                previousTime = time
                velocityY = 0
                target = nil
                model.interrupt()
                if model.isOpen {
                    let closeCenter = CGPoint(
                        x: width - SendMoneyCardsSpec.Sheet.closeX,
                        y: offsetY + SendMoneyCardsSpec.Sheet.closeY
                    )
                    let closeDistance = hypot(point.x - closeCenter.x, point.y - closeCenter.y)
                    if closeDistance <= 30 {
                        target = "close"
                        mode = .close
                    } else {
                        mode = .sheet
                        if point.y >= offsetY + SendMoneyCardsSpec.Sheet.buttonTop &&
                            point.y <= offsetY + SendMoneyCardsSpec.Sheet.buttonTop + SendMoneyCardsSpec.Sheet.buttonHeight {
                            target = "next"
                        } else if point.x >= SendMoneyCardsSpec.Sheet.addNewX - 40 &&
                                    point.x <= SendMoneyCardsSpec.Sheet.addNewX + 40 &&
                                    point.y >= offsetY + SendMoneyCardsSpec.Sheet.avatarsY - 24 &&
                                    point.y <= offsetY + SendMoneyCardsSpec.Sheet.avatarsY + 42 {
                            target = "add"
                        } else {
                            target = SendMoneyCardsSpec.contacts.indices.first { index in
                                let centerY = offsetY + SendMoneyCardsSpec.Sheet.contactsTop +
                                    Double(index) * SendMoneyCardsSpec.Sheet.contactPitch
                                return point.y >= centerY - 22 && point.y <= centerY + 22
                            }.map { "contact-\($0)" }
                        }
                    }
                    model.press(target)
                    model.beginSheet()
                } else {
                    let pose = model.pose(at: Date.timeIntervalSinceReferenceDate)
                    let card = CGRect(
                        x: SendMoneyCardsSpec.Card.inset,
                        y: offsetY + SendMoneyCardsSpec.Card.top + pose.lift,
                        width: width - SendMoneyCardsSpec.Card.inset * 2,
                        height: SendMoneyCardsSpec.Card.height
                    )
                    if card.contains(point) {
                        mode = .card
                        model.beginCard()
                    } else {
                        mode = .none
                    }
                }
            case .changed:
                let elapsed = max(0.001, time - previousTime)
                velocityY = (point.y - previous.y) / elapsed
                previous = point
                previousTime = time
                if mode == .card {
                    model.dragCard(translationY: point.y - start.y)
                }
            case .ended, .cancelled, .failed:
                let translation = point.y - start.y
                switch mode {
                case .card:
                    if recognizer.state == .ended {
                        model.endCard(translationY: translation, velocityY: velocityY)
                    } else {
                        model.cancelCard()
                    }
                case .sheet:
                    model.endSheet(translationY: translation, velocityY: velocityY)
                case .close:
                    if hypot(point.x - start.x, point.y - start.y) < 16 {
                        model.closeButton()
                    } else {
                        model.endSheet(translationY: translation, velocityY: velocityY)
                    }
                case .none:
                    model.cancelCard()
                }
                model.press(nil)
                target = nil
                mode = .none
            default:
                break
            }
        }
    }
}
