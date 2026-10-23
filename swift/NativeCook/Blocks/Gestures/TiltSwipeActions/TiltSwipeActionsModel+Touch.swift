import SwiftUI
import UIKit

/// What the finger is doing: where it began, what it began on, and how it has moved.
struct TiltSwipeActionsTouch {
    enum Target: Equatable {
        /// A view that takes the touch and does nothing with it, or no view at all.
        case nothing
        case fab
        case menuItem(Int)
        case scrim
        case tab(Int)
        case undo
        case flag(String)
        case delete(String)
        /// The row's content: a pan, or a tap that closes an open row.
        case content(String)
    }

    var target: Target
    /// The press area of a button, in the flat app's points.
    var rect: CGRect
    /// Screen points of the touch-down.
    var start: CGPoint
    var startTime: Double
    var activated = false
    var failed = false
    var buttonPressed = false
    var samples: [(time: Double, point: CGPoint)] = []
}

/// The one finger on the screen: found in the flat app's coordinates, then handed to a row's swipe or to
/// the button under it, like the React Native block's gesture handlers and `Pressable`s.
extension TiltSwipeActionsModel {
    private typealias Flat = TiltSwipeActionsFlat
    private typealias Layout = TiltSwipeActionsLayout

    /// A Pressable keeps the press while the finger stays near it.
    private static let retention = TiltSwipeActionsTokens.Touch.retention

    private func flat(from screen: CGPoint) -> CGPoint {
        screen.applying(TiltSwipeActionsGeometry.plane.inverted())
    }

    func touchBegan(at screen: CGPoint) {
        guard touch == nil else { return }
        let now = CACurrentMediaTime()
        demoTouchStarted()
        let point = flat(from: screen)
        let (target, rect) = hit(point)
        var next = TiltSwipeActionsTouch(target: target, rect: rect, start: point, startTime: now, samples: [(now, point)])
        setPressed(target, pressed: true)
        next.buttonPressed = true
        touch = next
    }

    func touchMoved(to screen: CGPoint) {
        guard var current = touch else { return }
        let now = CACurrentMediaTime()
        let point = flat(from: screen)
        current.samples.append((now, point))
        current.samples.removeAll { now - $0.time > TiltSwipeActionsTokens.Touch.sampleWindow }
        switch current.target {
        case let .content(key):
            if let row = rows.first(where: { $0.key == key }) {
                let dx = point.x - current.start.x
                let dy = point.y - current.start.y
                if !current.activated && !current.failed {
                    if abs(dy) > TiltSwipeActionsMotion.Swipe.failOffsetY {
                        current.failed = true
                    } else if abs(dx) >= TiltSwipeActionsMotion.Swipe.activeOffsetX {
                        current.activated = true
                        begin(row)
                    }
                }
                if current.activated {
                    move(row, translation: dx, velocity: velocityX(current, at: now), silent: false)
                }
            }
        case .nothing:
            break
        default:
            let isPressed = retained(current, at: point)
            if isPressed != current.buttonPressed {
                setPressed(current.target, pressed: isPressed)
                current.buttonPressed = isPressed
            }
        }
        touch = current
    }

    func touchEnded(at screen: CGPoint, cancelled: Bool) {
        guard let current = touch else { return }
        touch = nil
        let point = flat(from: screen)
        setPressed(current.target, pressed: false)
        let now = CACurrentMediaTime()
        switch current.target {
        case let .content(key):
            guard let row = rows.first(where: { $0.key == key }) else { break }
            if current.activated {
                end(row, velocity: cancelled ? 0 : velocityX(current, at: now))
            } else if !cancelled && now - current.startTime <= TiltSwipeActionsTokens.Touch.tapMaximum && row.opened {
                close(row)
            }
        case .nothing:
            break
        default:
            if !cancelled && retained(current, at: point) { pressed(current.target) }
        }
        demoTouchEnded()
    }

    private func velocityX(_ touch: TiltSwipeActionsTouch, at now: Double) -> Double {
        let recent = touch.samples.filter { now - $0.time <= TiltSwipeActionsTokens.Touch.velocityWindow }
        guard let first = recent.first, let last = recent.last, last.time - first.time >= TiltSwipeActionsTokens.Touch.minimumVelocitySpan else { return 0 }
        return (last.point.x - first.point.x) / (last.time - first.time)
    }

    private func retained(_ touch: TiltSwipeActionsTouch, at point: CGPoint) -> Bool {
        touch.rect.insetBy(dx: -Self.retention, dy: -Self.retention).contains(point)
    }

    /// `Pressable.onPress`.
    private func pressed(_ target: TiltSwipeActionsTouch.Target) {
        switch target {
        case .fab:
            setMenu(open: !menuOpen)
        case .menuItem, .scrim:
            setMenu(open: false)
        case let .tab(index):
            selectTab(index)
        case .undo:
            undoPressed()
        case let .flag(key):
            toggleFlag(key)
            setActiveKey("")
        case let .delete(key):
            removeNow(key)
        case .nothing, .content:
            break
        }
    }

    // MARK: Hit testing, front to back as the React Native tree stacks it

    private var menuRect: CGRect {
        CGRect(x: Flat.menu.left, y: Flat.menu.top, width: Flat.menu.width, height: Flat.menu.height)
    }

    /// The Undo button of the pill, where the pill's own transform puts it.
    private var undoRect: CGRect? {
        guard pill.value > 0.5 else { return nil }
        let p = pill.value.tiltClamped(0, 1.06)
        let right = Flat.planeWidth - (Layout.gutter + Layout.fab + TiltSwipeActionsTokens.Undo.trailingFabGap)
        let font = UIFont(name: TiltSwipeActionsTokens.Font.postScriptBold, size: TiltSwipeActionsType.pillAction.size) ?? UIFont.systemFont(ofSize: TiltSwipeActionsType.pillAction.size, weight: .bold)
        let label = ("Undo" as NSString).size(withAttributes: [.font: font]).width
        let buttonWidth = TiltSwipeActionsTokens.Undo.actionPadding + TiltSwipeActionsTokens.Undo.icon + TiltSwipeActionsTokens.Undo.actionGap + label + TiltSwipeActionsTokens.Undo.actionPadding
        let buttonRight = right - TiltSwipeActionsTokens.Undo.rightPadding
        let centreY = Flat.planeHeight - Layout.pillBottom - Layout.pillHeight / 2
        var rect = CGRect(x: buttonRight - buttonWidth, y: centreY - 22, width: buttonWidth, height: 44)
        if !reduceMotion {
            let scale = 0.22 + 0.78 * p
            rect = CGRect(
                x: right - (right - rect.minX) * scale,
                y: rect.minY + (1 - p) * pillFrom,
                width: rect.width * scale,
                height: rect.height
            )
        }
        return rect.insetBy(dx: -8, dy: -8)
    }

    private func hit(_ point: CGPoint) -> (TiltSwipeActionsTouch.Target, CGRect) {
        let fab = CGRect(x: Flat.fab.left, y: Flat.fab.top, width: Flat.fab.size, height: Flat.fab.size)
        if fab.contains(point) { return (.fab, fab) }

        if menuOpen {
            let menu = menuRect
            guard menu.contains(point) else {
                return (.scrim, CGRect(x: 0, y: 0, width: Flat.planeWidth, height: Flat.planeHeight))
            }
            for index in 0..<3 {
                let item = CGRect(x: menu.minX + Flat.menu.padLeft, y: menu.minY + Flat.menu.padTop + Flat.menu.item * Double(index), width: menu.width - Flat.menu.padLeft - Flat.menu.padRight, height: Flat.menu.item)
                if item.contains(point) { return (.menuItem(index), item) }
            }
            return (.nothing, .zero)
        }

        let panel = CGRect(x: Flat.panel.left, y: Flat.panel.top, width: Flat.planeWidth - Flat.panel.left, height: Flat.planeHeight - Flat.panel.top)
        if panel.contains(point) {
            for index in 0..<3 {
                let hit = TiltSwipeActionsTokens.Panel.tabHit
                let tab = CGRect(x: Flat.tabs[index] - hit / 2, y: Flat.tabY - hit / 2, width: hit, height: hit)
                if tab.contains(point) { return (.tab(index), tab) }
            }
            return (.nothing, .zero)
        }

        if let undo = undoRect, undo.contains(point) { return (.undo, undo) }

        if tab == 2 {
            return hitRows(point)
        }
        return (.nothing, .zero)
    }

    private func hitRows(_ point: CGPoint) -> (TiltSwipeActionsTouch.Target, CGRect) {
        var top = Flat.rowsTop
        for row in rows {
            let height = Layout.row * max(row.height.value, 0)
            defer { top += height }
            guard point.y >= top, point.y < top + height, point.x >= 0, point.x < Flat.width else { continue }
            let offset = row.x.value
            if point.x >= offset && point.x < Flat.width + offset {
                return (.content(row.key), .zero)
            }
            // The uncovered strip: Delete is on top of Flag.
            let strip = max(-offset, 0)
            let full = row.full.value
            let reveal = ((strip - Layout.gutter) / Layout.tile).tiltClamped(0, 1)
            let stretched = max(strip - Layout.gutter - Layout.tileGap, Layout.tile)
            let deleteWidth = (Layout.tile + (stretched - Layout.tile) * full) * (0.6 + 0.4 * reveal)
            let deleteHeight = Layout.tileHeight * (0.6 + 0.4 * reveal)
            let deleteRight = Flat.width - Layout.gutter
            let tileCentre = top + (Layout.row - Layout.tileHeight) / 2 + Layout.tileHeight / 2
            let delete = CGRect(x: deleteRight - deleteWidth, y: tileCentre - deleteHeight / 2, width: deleteWidth, height: deleteHeight)
            if delete.contains(point) { return (.delete(row.key), delete) }
            let flagReveal = ((strip - Layout.gutter - Layout.tile - Layout.tileGap) / Layout.tile).tiltClamped(0, 1)
            let flagScale = (0.6 + 0.4 * flagReveal) * (1 - 0.3 * full)
            let flagSize = CGSize(width: Layout.tile * flagScale, height: Layout.tileHeight * flagScale)
            let flagCentre = CGPoint(x: Flat.width - Layout.gutter - Layout.tile - Layout.tileGap - Layout.tile / 2, y: tileCentre)
            let flag = CGRect(x: flagCentre.x - flagSize.width / 2, y: flagCentre.y - flagSize.height / 2, width: flagSize.width, height: flagSize.height)
            if flag.contains(point) { return (.flag(row.key), flag) }
            return (.nothing, .zero)
        }
        return (.nothing, .zero)
    }
}
