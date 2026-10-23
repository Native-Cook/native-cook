import SwiftUI
import UIKit

/// A simultaneous zero-delay recognizer keeps the sheet drag alive beside the gallery's edge swipe.
struct SavedPlacesMapTouch: UIViewRepresentable {
    let playback: SavedPlacesMapPlayback
    let frame: SavedPlacesMapMotion.Frame
    let reduced: Bool
    let onActivity: () -> Void

    func makeUIView(context: Context) -> Surface { Surface(playback: playback) }
    func updateUIView(_ view: Surface, context: Context) {
        view.playback = playback
        view.motionFrame = frame
        view.reduced = reduced
        view.onActivity = onActivity
        view.updateAccessibility()
    }

    final class Element: UIAccessibilityElement {
        var activate: (() -> Void)?
        var adjust: ((Int) -> Void)?
        override func accessibilityActivate() -> Bool { activate?(); return activate != nil }
        override func accessibilityIncrement() { adjust?(1) }
        override func accessibilityDecrement() { adjust?(-1) }
    }

    final class Surface: UIView, UIGestureRecognizerDelegate {
        var playback: SavedPlacesMapPlayback
        var motionFrame = SavedPlacesMapMotion.Frame.rest
        var reduced = false
        var onActivity: () -> Void = {}
        private var start = CGPoint.zero
        private var previous = CGPoint.zero
        private var previousAt = 0.0
        private var velocityY = 0.0
        private var startSheet = 0.0
        private var maximumDistance = 0.0
        private var target = SavedPlacesMapPlayback.Hit.none
        private var markerIndex = -1
        private var dragging = false
        private var elements: [Element] = []
        private let impact = UIImpactFeedbackGenerator(style: .light)
        private let selection = UISelectionFeedbackGenerator()
        private let notification = UINotificationFeedbackGenerator()

        init(playback: SavedPlacesMapPlayback) {
            self.playback = playback
            super.init(frame: .zero)
            backgroundColor = .clear
            isOpaque = false
            isAccessibilityElement = false
            let recognizer = UILongPressGestureRecognizer(target: self, action: #selector(handle(_:)))
            recognizer.minimumPressDuration = 0
            recognizer.allowableMovement = .greatestFiniteMagnitude
            recognizer.cancelsTouchesInView = false
            recognizer.delegate = self
            addGestureRecognizer(recognizer)
                    }

        required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

        func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer,
                               shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool { true }

        @objc private func handle(_ recognizer: UILongPressGestureRecognizer) {
            let point = recognizer.location(in: self)
            let now = Date().timeIntervalSinceReferenceDate
            switch recognizer.state {
            case .began:
                onActivity()
                start = point
                previous = point
                previousAt = now
                velocityY = 0
                dragging = false
                maximumDistance = 0
                playback.beginContact(at: now)
                let result = hit(point)
                target = result.hit
                markerIndex = result.marker
                if target == .checkbox || target == .tile {
                    playback.beginPress(target, at: now)
                } else if target == .sheet {
                    playback.enterTouch(at: now)
                    startSheet = playback.sheet(at: now)
                }
            case .changed:
                let dt = max(1.0 / 240.0, now - previousAt)
                let sample = Double(point.y - previous.y) / dt
                let weight = 1 - exp(-dt / 0.035)
                velocityY += (sample - velocityY) * weight
                previous = point
                previousAt = now
                let translation = Double(point.y - start.y)
                maximumDistance = max(maximumDistance, hypot(Double(point.x - start.x), translation))
                let canBecomeSheetDrag = target == .sheet || target == .checkbox || target == .tile
                if canBecomeSheetDrag, !dragging, abs(translation) >= SavedPlacesMapMotion.DRAG.slop {
                    if target == .checkbox || target == .tile { playback.endPress(at: now) }
                    target = .sheet
                    dragging = true
                    playback.beginDrag(at: now)
                    startSheet = playback.sheet(at: now)
                }
                if dragging {
                    playback.drag(from: startSheet, translationY: translation,
                                  rise: SavedPlacesMapMotion.RISE * scalePoints, at: now)
                }
            case .ended, .cancelled, .failed:
                if now - previousAt > 0.1 { velocityY = 0 }
                let successful = recognizer.state == .ended
                if target == .sheet, dragging {
                    if successful {
                        let normalizedVelocity = -velocityY / (SavedPlacesMapMotion.RISE * scalePoints)
                        if playback.endDrag(start: startSheet, velocity: normalizedVelocity, at: now) {
                            impact.impactOccurred()
                        }
                    } else {
                        playback.cancelDrag(at: now)
                    }
                } else if target == .checkbox || target == .tile {
                    playback.endPress(at: now)
                    let endHit = hit(point).hit
                    if successful, maximumDistance <= 10, endHit == target,
                       let direction = playback.toggleVisited(at: now) {
                        if direction == 1 { notification.notificationOccurred(.success) }
                        else { selection.selectionChanged() }
                    }
                } else if target == .marker, successful, maximumDistance <= 10,
                          hit(point).marker == markerIndex {
                    playback.popMarker(markerIndex, at: now)
                    selection.selectionChanged()
                }
                playback.endContact(at: now)
                target = .none
                markerIndex = -1
                dragging = false
            default:
                break
            }
        }

        private var unit: Double { max(0.0001, Double(bounds.width) / SavedPlacesMapTheme.REF_PX_W) }
        private var scalePoints: Double { max(0.0001, Double(bounds.width) / SavedPlacesMapTheme.REF_W) }
        private var rise: Double { SavedPlacesMapMotion.RISE * 3 }
        private var tilt: Double { reduced ? 0 : SavedPlacesMapMotion.tiltOf(motionFrame.sheet) }

        private func refPoint(_ point: CGPoint) -> CGPoint {
            CGPoint(x: Double(point.x) / unit, y: Double(point.y) / unit)
        }

        private func hit(_ point: CGPoint) -> (hit: SavedPlacesMapPlayback.Hit, marker: Int) {
            let p = refPoint(point)
            let sheetOffset = rise * motionFrame.sheet
            let slop = 66.0
            if abs(p.x - 374.5) <= slop, abs(p.y - (2612.5 - sheetOffset)) <= slop { return (.checkbox, -1) }
            let tile = CGRect(x: SavedPlacesMapTheme.tileXs[0], y: SavedPlacesMapTheme.tileY - sheetOffset,
                              width: SavedPlacesMapTheme.tileW, height: SavedPlacesMapTheme.tileH)
            if tile.contains(p) { return (.tile, -1) }
            if p.y < SavedPlacesMapTheme.sheetTop - sheetOffset {
                // the marker where it is drawn: on its ground spot, lifted and scaled while the map is pitched
                for index in stride(from: SavedPlacesMapData.markers.count - 1, through: 0, by: -1) {
                    let marker = SavedPlacesMapData.markers[index]
                    let disc = discOf(marker)
                    let right = disc.x + (SavedPlacesMapData.markerDiscDx + SavedPlacesMapData.markerHitW) * disc.m
                    if p.x >= disc.x - slop, p.x <= right, abs(p.y - disc.y) <= slop * max(1, disc.m) { return (.marker, index) }
                }
            }
            if p.y >= SavedPlacesMapTheme.sheetTop - sheetOffset { return (.sheet, -1) }
            return (.none, -1)
        }

        private func discOf(_ marker: SavedPlacesMapMarker) -> SavedPlacesMapMotion.Disc {
            let c = marker.center
            return SavedPlacesMapMotion.discAt(c.x, c.y, tilt, SavedPlacesMapTheme.depthRank[marker.id] ?? 0, SavedPlacesMapData.radius)
        }

        private func now() -> Double { Date().timeIntervalSinceReferenceDate }

        private func toggleVisit() {
            onActivity()
            guard let direction = playback.toggleVisited(at: now()) else { return }
            if direction == 1 { notification.notificationOccurred(.success) } else { selection.selectionChanged() }
        }

        private func moveSheet(to target: Double) {
            onActivity()
            if playback.sheetTo(target, at: now()) { impact.impactOccurred() }
        }

        /// The screen reader's view of the screen: cafés (riding their pins), then the sheet's layer.
        func updateAccessibility() {
            typealias Theme = SavedPlacesMapTheme
            let u = unit
            let sheetOffset = rise * motionFrame.sheet
            let expanded = motionFrame.sheet > 0.5
            let visited = motionFrame.fill > 0.5
            var list: [UIAccessibilityElement] = []
            var used = 0
            func element() -> Element {
                if used == elements.count { elements.append(Element(accessibilityContainer: self)) }
                defer { used += 1 }
                let e = elements[used]
                e.activate = nil; e.adjust = nil
                e.accessibilityValue = nil; e.accessibilityHint = nil
                e.accessibilityTraits = .none
                return e
            }
            func rect(_ x: Double, _ y: Double, _ w: Double, _ h: Double) -> CGRect {
                CGRect(x: x * u, y: y * u, width: w * u, height: h * u)
            }
            let raisedTop = Theme.sheetTop - rise
            for (index, marker) in SavedPlacesMapData.markers.enumerated() {
                let c = marker.center
                let r = SavedPlacesMapData.radius
                // hidden while the half-raised sheet covers it
                if expanded && c.y + r > raisedTop { continue }
                let disc = discOf(marker)
                let x0 = c.x - r, y0 = c.y - r
                let w = marker.x + SavedPlacesMapData.markerHitW - x0
                let m = disc.m
                let frame = rect(c.x + (x0 - c.x) * m + disc.x - c.x, c.y + (y0 - c.y) * m + disc.y - c.y, w * m, 2 * r * m)
                let e = element()
                let tag = marker.tag == .saved ? "saved" : "liked"
                e.accessibilityLabel = "\(marker.name), \(marker.subtitle), \(tag)"
                    + (index == SavedPlacesMapData.visitedIndex && visited ? ", \(Theme.Copy.a11yVisited)" : "")
                e.accessibilityHint = Theme.Copy.a11yMarkerHint
                e.accessibilityTraits = .button
                e.accessibilityFrameInContainerSpace = frame
                e.activate = { [weak self] in
                    guard let self else { return }
                    self.onActivity()
                    self.playback.popMarker(index, at: self.now())
                    self.selection.selectionChanged()
                }
                list.append(e)
            }
            let top = Theme.sheetTop
            let sheet = element()
            sheet.accessibilityLabel = "\(Theme.Copy.title), \(Theme.Copy.places), \(Theme.Copy.saves), \(Theme.Copy.views), \(Theme.Copy.a11yBy) \(Theme.Copy.handle)"
            sheet.accessibilityValue = expanded ? Theme.Copy.a11yExpanded : Theme.Copy.a11yCollapsed
            sheet.accessibilityHint = Theme.Copy.a11ySheetHint
            sheet.accessibilityTraits = .adjustable
            sheet.accessibilityFrameInContainerSpace = rect(0, top - sheetOffset, 1206, Theme.tileY - top)
            sheet.adjust = { [weak self] direction in self?.moveSheet(to: direction > 0 ? 1 : 0) }
            sheet.activate = { [weak self] in self?.moveSheet(to: expanded ? 0 : 1) }
            list.append(sheet)

            let visit = element()
            visit.accessibilityLabel = Theme.Copy.visited
            visit.accessibilityValue = "\(visited ? Theme.Copy.visitedNext : Theme.Copy.visitedNum) \(Theme.Copy.a11yOf) \(Theme.Copy.visitedDen.dropFirst())"
            visit.accessibilityHint = Theme.Copy.a11yVisitHint
            visit.accessibilityTraits = .button
            visit.accessibilityFrameInContainerSpace = rect(Theme.tileXs[0], Theme.tileY - sheetOffset, Theme.tileW, Theme.tileH)
            visit.activate = { [weak self] in self?.toggleVisit() }
            list.append(visit)

            for (j, label) in [Theme.Copy.save, Theme.Copy.share, Theme.Copy.similar].enumerated() {
                let e = element()
                e.accessibilityLabel = label
                e.accessibilityTraits = .staticText
                e.accessibilityFrameInContainerSpace = rect(Theme.tileXs[j + 1], Theme.tileY - sheetOffset, Theme.tileW, Theme.tileH)
                list.append(e)
            }
            let card = element()
            card.accessibilityLabel = "\(Theme.Copy.listTitle), \(Theme.Copy.listSub)"
            card.accessibilityTraits = .staticText
            card.accessibilityFrameInContainerSpace = rect(26, 2343 - sheetOffset, 1156, Theme.listCardH)
            list.append(card)

            let box = element()
            box.accessibilityLabel = Theme.Copy.a11yVisitedBox
            box.accessibilityValue = visited ? "checked" : "unchecked"
            box.accessibilityTraits = .button
            box.accessibilityFrameInContainerSpace = rect(374.5 - 66, 2612.5 - 66 - sheetOffset, 132, 132)
            box.activate = { [weak self] in self?.toggleVisit() }
            list.append(box)

            if expanded {
                let more = [("Lumen Coffee", "coffee & pastries"), ("Linden Wick", "pourover coffee")]
                for (i, item) in more.enumerated() {
                    let e = element()
                    e.accessibilityLabel = "\(item.0), \(item.1)"
                    e.accessibilityTraits = .staticText
                    e.accessibilityFrameInContainerSpace = rect(26, 2343 + (Theme.listCardH + Theme.listGap) * Double(i + 1) - sheetOffset, 1156, Theme.listCardH)
                    list.append(e)
                }
            }
            accessibilityElements = list
        }
    }
}
