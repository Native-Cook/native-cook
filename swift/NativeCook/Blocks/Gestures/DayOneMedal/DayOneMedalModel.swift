import SwiftUI
import UIKit
import QuartzCore
import CoreText

struct DayOneMedalLayout: Equatable {
    var width = 402.0
    var height = 874.0
    var safeTop = 62.0
    var safeBottom = 34.0
    var sheetTop: Double { safeTop + 19 }
    var sheetHeight: Double { height - sheetTop }
    var scale: Double { min(1, width / 430, sheetHeight / 878) }
    var gridScale: Double { min(1, width / 430, (height - safeTop - safeBottom - 116) / 684) }
    var stageY: Double { 273 * scale }
    var half: Double { 177.5 * scale }
    var titleTop: Double { grid(486 * scale) }
    var bodyTop: Double { grid(558 * scale) }
    var bodyHeight: Double { grid(40 * scale) }
    var dotsTop: Double { bodyTop + bodyHeight + 16 }
    var hintTop: Double { dotsTop + 12 }
    var wearTop: Double { hintTop + 32 }
    var shareTop: Double { wearTop + grid(55 * scale) + 16 }
    var slotWidth: Double { (430 - 32 - 57) * gridScale / 4 - 0.01 }
    var slotHeight: Double { slotWidth * 127 / 86 }
    var gridInset: Double { (width - 430 * gridScale) / 2 + 16 * gridScale }
    var slotX: Double { gridInset + slotWidth * 1.5 + 19 * gridScale }
    var slotY: Double { safeTop + 52 + (18 + 20 + 14) * gridScale * 2 + slotHeight * 1.5 }
    var slotHalf: Double { 210 * min(slotWidth * 0.78 / 240, slotHeight * 0.9 / 448) / 1.009 }
    func grid(_ x: Double) -> Double { (x / 4).rounded() * 4 }
}


@MainActor
final class DayOneMedalModel: NSObject, ObservableObject {
    @Published private(set) var engine = DayOneMedalEngine()
    private(set) var layout = DayOneMedalLayout()
    private(set) var reduced = false
    private var displayLink: CADisplayLink?
    private var clockTarget: DayOneMedalClockTarget?
    private var lastTime = 0.0
    private var started = false
    private let tickFeedback = UISelectionFeedbackGenerator()
    private let successFeedback = UINotificationFeedbackGenerator()
    var rendererReady = false

    func configure(_ layout: DayOneMedalLayout, reduced: Bool, autoPlay: Bool) {
        if self.reduced != reduced || engine.autoplay != autoPlay { started = false }
        self.layout = layout
        self.reduced = reduced
        var path: [[Double]] = engine.config.engravePath
        if path.isEmpty, let url = Bundle.main.url(forResource: "medal-engrave-path", withExtension: "json"),
           let data = try? Data(contentsOf: url), let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
            path = json["points"] as? [[Double]] ?? []
        }
        engine.config = DayOneMedalConfig(reduced: reduced, centre: DayOneMedalPoint(x: layout.width / 2, y: layout.stageY),
            half: layout.half, pivot: DayOneMedalPoint(x: layout.width / 2, y: -40), engravePath: path)
        engine.autoplay = autoPlay
        if reduced {
            engine.demo.stop()
            engine.resumeAt = .infinity
            engine.restartAt = .infinity
        }
    }

    func startClock() {
        guard displayLink == nil else { return }
        let target = DayOneMedalClockTarget()
        target.model = self
        clockTarget = target
        let link = CADisplayLink(target: target, selector: #selector(DayOneMedalClockTarget.frame(_:)))
        link.preferredFrameRateRange = CAFrameRateRange(minimum: 30, maximum: 60, preferred: 60)
        link.add(to: .main, forMode: .common)
        displayLink = link
    }

    func stopClock() {
        displayLink?.invalidate()
        displayLink = nil
        clockTarget = nil
        lastTime = 0
        engine.demo.stop()
        started = false
    }

    func tick(_ timestamp: Double) {
        let dt = lastTime == 0 ? 1 / 60 : min(0.1, max(0, timestamp - lastTime))
        lastTime = timestamp
        if !started && rendererReady {
            started = true
            if engine.autoplay && !reduced { engine.demo.start(engine.elapsed + 0.9) }
        }
        engine.tick(dt, scale: layout.scale)
        feedback()
    }

    private func feedback() {
        let events = engine.events
        engine.events = 0
        if events & 1 != 0 { tickFeedback.selectionChanged() }
        if events & 2 != 0 { UIImpactFeedbackGenerator(style: .light).impactOccurred() }
        if events & 4 != 0 { successFeedback.notificationOccurred(.success) }
        if events & 8 != 0 { UIImpactFeedbackGenerator(style: .heavy).impactOccurred() }
        if events & 16 != 0 { UIImpactFeedbackGenerator(style: .rigid).impactOccurred() }
        if events & 32 != 0 { UIImpactFeedbackGenerator(style: .soft).impactOccurred() }
        if events & 64 != 0 { successFeedback.notificationOccurred(.warning) }
    }

    func command(_ action: DayOneMedalAction) {
        engine.touched()
        engine.act(action)
        feedback()
    }

    func open() {
        engine.touched()
        engine.show()
    }

    func close() {
        engine.touched()
        engine.hide()
    }

    func touch(_ point: CGPoint) {
        guard engine.present == 1 && engine.opened else { return }
        engine.touched()
        engine.act(.touch(point.x, point.y))
        feedback()
    }

    func move(_ point: CGPoint, delta: CGPoint) {
        engine.touched()
        engine.act(.move(point.x, point.y, delta.x, delta.y))
        feedback()
    }

    func release(_ velocity: CGPoint, cancelled: Bool) {
        engine.touched()
        engine.act(.release(velocity.x, velocity.y, cancelled))
        feedback()
    }

    func header(_ translation: Double, ended: Bool, velocity: Double) {
        engine.touched()
        if !reduced { engine.drag = translation > 0 ? translation : translation * 0.12 }
        engine.dragging = !ended
        if ended {
            engine.releaseHeader(velocity: reduced ? 0 : velocity,
                dismiss: translation > layout.sheetHeight * 0.3 || velocity > 700)
        } else { engine.dragDismissTime = -1 }
    }
}

@MainActor
private final class DayOneMedalClockTarget: NSObject {
    weak var model: DayOneMedalModel?
    @objc func frame(_ link: CADisplayLink) { model?.tick(link.timestamp) }
}

enum DayOneMedalFonts {
    static let names: [String] = ["ArchivoExpanded-Regular", "ArchivoExpanded-Black"].map { file in
        guard let url = Bundle.main.url(forResource: file, withExtension: "ttf"),
              let provider = CGDataProvider(url: url as CFURL), let font = CGFont(provider),
              let name = font.postScriptName else { return "HelveticaNeue" }
        CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        return name as String
    }
}
