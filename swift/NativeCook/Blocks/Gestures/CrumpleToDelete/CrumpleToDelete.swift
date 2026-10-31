/// UIKit touch targets and a display-link-driven Metal surface for the full-screen note demo.
import MetalKit
import SwiftUI
import UIKit

/// Renders the note, trash bin, and Undo controls with live autoplay or finger-driven poses.
struct CrumpleToDelete: View {
    static let loop = CrumpleToDeleteMotion.loop
    var autoPlay = false
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var phase
    private var pinned: Double? {
        #if DEBUG
            if let value = UserDefaults.standard.object(forKey: "crumpleToDeleteTime") {
                return (value as? NSNumber)?.doubleValue ?? Double("\(value)") ?? 0
            }
        #endif
        return nil
    }
    var body: some View {
        GeometryReader { proxy in
            CrumpleToDeleteRepresentable(
                autoPlay: autoPlay, reduced: reduced, active: phase == .active, pinned: pinned
            )
            .frame(width: proxy.size.width, height: proxy.size.height, alignment: .topLeading)
        }
        .ignoresSafeArea().background(Color(red: 217.0 / 255, green: 200.0 / 255, blue: 176.0 / 255))
        .preferredColorScheme(.light)
        .persistentSystemOverlays(.hidden)
    }
}
struct CrumpleToDeleteRepresentable: UIViewRepresentable {
    var autoPlay: Bool, reduced: Bool, active: Bool
    var pinned: Double?
    func makeUIView(context: Context) -> CrumpleToDeleteSurface { CrumpleToDeleteSurface() }
    func updateUIView(_ view: CrumpleToDeleteSurface, context: Context) {
        view.update(autoPlay, reduced, active, pinned)
    }
    static func dismantleUIView(_ view: CrumpleToDeleteSurface, coordinator: ()) { view.stop() }
}

/// Owns the display link, touch recognizer, and accessible targets above the Metal surface.
final class CrumpleToDeleteSurface: UIView, UIGestureRecognizerDelegate {
    typealias M = CrumpleToDeleteMotion
    private let metalView = MTKView(frame: .zero)
    private let renderer = CrumpleToDeleteRenderer()
    private let driver = CrumpleToDeleteDriver()
    private let eyebrow = UILabel(), title = UILabel(), subtitle = UILabel(), toast = UIView(),
        toastText = UILabel(), undoButton = CrumpleToDeleteUndoButton(type: .custom), noteHit = UIView()
    private var displayLink: CADisplayLink?, previousTimestamp: CFTimeInterval?
    private var geo: M.Geo?
    private var autoPlay = false, reduced = false, active = true, pinned: Double?
    private var previousPose: M.Pose?
    private var start = CGPoint.zero, lastPoint = CGPoint.zero, lastTouch = 0.0, velocity = 0.0, dragging = false
    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = CrumpleToDeleteResources.color(0xD9C8B0)
        clipsToBounds = true
        addSubview(metalView)
        metalView.isUserInteractionEnabled = false
        let r = CrumpleToDeleteResources.shared
        func label(_ l: UILabel, _ face: String, _ size: Double, _ color: UInt32) {
            l.font = r.font(face, size)
            l.textColor = CrumpleToDeleteResources.color(color)
            l.numberOfLines = 1
            addSubview(l)
        }
        label(eyebrow, "JetBrainsMono-Medium", 11, 0x6F665C)
        eyebrow.attributedText = NSAttributedString(
            string: "NOTES · TODAY",
            attributes: [.font: eyebrow.font!, .kern: 1.6, .foregroundColor: eyebrow.textColor!])
        label(title, "Archivo-ExtraBold", 36, 0x2C2722)
        title.attributedText = NSAttributedString(
            string: "Notes", attributes: [.font: title.font!, .kern: -0.7, .foregroundColor: title.textColor!])
        title.accessibilityTraits = .header
        label(subtitle, "Archivo-Medium", 15, 0x6F665C)
        subtitle.text = "Pull a note into the bin to delete it"
        addSubview(noteHit)
        noteHit.isUserInteractionEnabled = false
        noteHit.isAccessibilityElement = true
        noteHit.accessibilityTraits = .adjustable
        noteHit.accessibilityLabel = "Note: Saturday market"
        noteHit.accessibilityHint = "Drag down into the bin to delete"
        noteHit.accessibilityCustomActions = [
            UIAccessibilityCustomAction(name: "Delete note", target: self, selector: #selector(deleteNote))
        ]
        addSubview(toast)
        toast.backgroundColor = CrumpleToDeleteResources.color(0x221E1A, 0.92)
        toast.layer.cornerRadius = 26
        toastText.font = r.font("Archivo-SemiBold", 15)
        toastText.textColor = CrumpleToDeleteResources.color(0xF6EFE6)
        toastText.text = "Note deleted"
        toast.addSubview(toastText)
        undoButton.setTitle("Undo", for: .normal)
        undoButton.titleLabel?.font = r.font("Archivo-SemiBold", 15)
        undoButton.setTitleColor(CrumpleToDeleteResources.color(0xFFD7A3), for: .normal)
        undoButton.backgroundColor = UIColor.white.withAlphaComponent(0.14)
        undoButton.layer.cornerRadius = 18
        toast.addSubview(undoButton)
        undoButton.accessibilityLabel = "Undo"
        undoButton.addTarget(self, action: #selector(undo), for: .touchUpInside)
        undoButton.addTarget(self, action: #selector(pressUndo), for: .touchDown)
        undoButton.addTarget(
            self, action: #selector(releaseUndo), for: [.touchUpInside, .touchUpOutside, .touchCancel])
        let gesture = UILongPressGestureRecognizer(target: self, action: #selector(touch(_:)))
        gesture.minimumPressDuration = 0
        gesture.allowableMovement = .greatestFiniteMagnitude
        gesture.delegate = self
        gesture.cancelsTouchesInView = false
        addGestureRecognizer(gesture)
    }
    @available(*, unavailable) required init?(coder: NSCoder) { nil }
    func update(_ autoPlay: Bool, _ reduced: Bool, _ active: Bool, _ pinned: Double?) {
        self.autoPlay = autoPlay
        self.reduced = reduced
        self.active = active
        self.pinned = pinned
        setNeedsLayout()
        wake()
    }
    func stop() {
        displayLink?.invalidate()
        displayLink = nil
    }
    override func didMoveToWindow() {
        super.didMoveToWindow()
        if window == nil { stop() } else { wake() }
    }
    private func wake() {
        guard window != nil, active else {
            displayLink?.isPaused = true
            previousTimestamp = nil
            return
        }
        if displayLink == nil {
            let l = CADisplayLink(target: self, selector: #selector(frame(_:)))
            l.add(to: .main, forMode: .common)
            displayLink = l
        }
        displayLink?.isPaused = false
        previousTimestamp = nil
    }
    override func layoutSubviews() {
        super.layoutSubviews()
        guard bounds.width > 0, bounds.height > 0 else { return }
        metalView.frame = bounds
        let safe = window?.safeAreaInsets ?? safeAreaInsets
        let geometry = M.Geo(width: bounds.width, height: bounds.height, top: safe.top, bottom: safe.bottom)
        if geo?.width != geometry.width || geo?.height != geometry.height || geo?.top != geometry.top
            || geo?.bottom != geometry.bottom
        {
            if geo == nil {
                driver.reset(geometry)
            } else if !driver.auto && driver.mode == .rest {
                driver.pose.x = geometry.hx
                driver.pose.y = geometry.hy
            }
            geo = geometry
            renderer?.configure(metalView, geometry)
            previousPose = nil
        }
        // RN's native Text line boxes: the title is 40pt and the subtitle is 20pt.
        let scale = UIFont.preferredFont(forTextStyle: .body, compatibleWith: traitCollection).pointSize / 17
        let small = min(1.3, scale)
        let large = min(1.2, scale)
        eyebrow.frame = CGRect(
            x: 24, y: geometry.top + 12 + 1.0 / 3, width: geometry.width - 48, height: 14 * small)
        title.frame = CGRect(
            x: 24, y: geometry.top + 12 + 14 * small + 9, width: geometry.width - 48, height: 40 * large)
        subtitle.frame = CGRect(
            x: 24, y: geometry.top + 12 + 14 * small + 8 + 40 * large + 2 + 2.0 / 3, width: geometry.width - 48,
            height: 20 * small)
        toastText.frame = CGRect(x: 20, y: 0, width: 180, height: 52)
        let width = ("Undo" as NSString).size(withAttributes: [.font: undoButton.titleLabel!.font!]).width + 32
        undoButton.frame = CGRect(x: geometry.width - 40 - 8 - width, y: 8, width: width, height: 36)
        driver.step(0, geometry, autoPlay, reduced, pinned)
        paint()
    }
    override func traitCollectionDidChange(_ previousTraitCollection: UITraitCollection?) {
        super.traitCollectionDidChange(previousTraitCollection)
        guard previousTraitCollection?.preferredContentSizeCategory != traitCollection.preferredContentSizeCategory
        else { return }
        let r = CrumpleToDeleteResources.shared
        let scale = UIFont.preferredFont(forTextStyle: .body, compatibleWith: traitCollection).pointSize / 17
        eyebrow.font = r.font("JetBrainsMono-Medium", 11 * min(1.3, scale))
        title.font = r.font("Archivo-ExtraBold", 36 * min(1.2, scale))
        subtitle.font = r.font("Archivo-Medium", 15 * min(1.3, scale))
        eyebrow.attributedText = NSAttributedString(
            string: "NOTES · TODAY",
            attributes: [.font: eyebrow.font!, .kern: 1.6, .foregroundColor: eyebrow.textColor!])
        title.attributedText = NSAttributedString(
            string: "Notes", attributes: [.font: title.font!, .kern: -0.7, .foregroundColor: title.textColor!])
        toastText.font = r.font("Archivo-SemiBold", 15 * scale)
        undoButton.titleLabel?.font = r.font("Archivo-SemiBold", 15 * scale)
        setNeedsLayout()
    }
    @objc private func frame(_ sender: CADisplayLink) {
        guard let geometry = geo, active else { return }
        let dt = min(max(0, previousTimestamp.map { sender.timestamp - $0 } ?? 0.016), 0.05)
        previousTimestamp = sender.timestamp
        driver.step(dt, geometry, autoPlay, reduced, pinned)
        paint()
        if previousPose == driver.pose
            && (pinned != nil || (driver.mode == .rest && !driver.auto && (!autoPlay || reduced)))
        {
            sender.isPaused = true
            previousTimestamp = nil
        }
    }
    /// Draws a changed pose and synchronizes the native note and Undo targets.
    private func paint() {
        guard let geometry = geo else { return }
        let pose = driver.pose
        if pose != previousPose, renderer?.requestDraw(metalView, pose) == true { previousPose = pose }
        noteHit.frame = CGRect(
            x: pose.x - geometry.nw / 2, y: pose.y - geometry.nh / 2, width: geometry.nw, height: geometry.nh)
        noteHit.accessibilityElementsHidden = driver.gone
        toast.frame = CGRect(
            x: 20, y: geometry.top + 96 + 16 * (1 - pose.toast), width: geometry.width - 40, height: 52)
        toast.alpha = pose.toast
        toast.isUserInteractionEnabled = driver.gone
        toast.accessibilityElementsHidden = !driver.gone
    }
    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldReceive touch: UITouch) -> Bool {
        guard pinned == nil, !driver.gone, let geometry = geo else { return false }
        if touch.view?.isDescendant(of: toast) == true { return false }
        return CGRect(
            x: driver.pose.x - geometry.nw / 2, y: driver.pose.y - geometry.nh / 2, width: geometry.nw,
            height: geometry.nh
        ).contains(touch.location(in: self))
    }
    func gestureRecognizer(
        _ gestureRecognizer: UIGestureRecognizer,
        shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
    ) -> Bool { true }
    @objc private func touch(_ gesture: UILongPressGestureRecognizer) {
        guard let geometry = geo, pinned == nil else { return }
        let point = gesture.location(in: self)
        let now = ProcessInfo.processInfo.systemUptime
        switch gesture.state {
        case .began:
            driver.interrupt(geometry)
            start = point
            lastPoint = point
            lastTouch = now
            velocity = 0
            dragging = false
            wake()
        case .changed:
            let translation = CGPoint(x: point.x - start.x, y: point.y - start.y)
            if !dragging && hypot(translation.x, translation.y) >= 2 {
                driver.startDrag()
                dragging = true
                // A non-autoplay surface may have paused while the finger was still.
                wake()
            }
            velocity = (point.y - lastPoint.y) / max(0.001, now - lastTouch)
            lastPoint = point
            lastTouch = now
            if dragging { driver.move(translation, geometry) }
        case .ended, .cancelled, .failed:
            if now - lastTouch > 0.08 {
                velocity = 0
            } else if abs(point.y - lastPoint.y) > 0.01 {
                velocity = (point.y - lastPoint.y) / max(0.001, now - lastTouch)
            }
            // RNGH calls onEnd for an active cancellation too; RN ignores its success flag.
            if dragging { driver.end(velocity) }
            dragging = false
        default: break
        }
    }
    @objc private func deleteNote() -> Bool {
        guard let geometry = geo, pinned == nil else { return false }
        driver.interrupt(geometry)
        driver.release()
        driver.rspin = 0
        wake()
        return true
    }
    @objc private func undo() {
        guard let geometry = geo, pinned == nil else { return }
        driver.undo(geometry)
        wake()
    }
    @objc private func pressUndo() {
        undoButton.transform = CGAffineTransform(scaleX: 0.96, y: 0.96)
        undoButton.backgroundColor = UIColor.white.withAlphaComponent(0.22)
    }
    @objc private func releaseUndo() {
        undoButton.transform = .identity
        undoButton.backgroundColor = UIColor.white.withAlphaComponent(0.14)
    }
}

final class CrumpleToDeleteUndoButton: UIButton {
    override func point(inside point: CGPoint, with event: UIEvent?) -> Bool {
        bounds.insetBy(dx: -10, dy: -10).contains(point)
    }
}
