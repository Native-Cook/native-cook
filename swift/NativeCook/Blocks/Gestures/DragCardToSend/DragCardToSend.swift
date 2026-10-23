import SwiftUI
import UIKit

struct DragCardToSend: View {
    static let loop: TimeInterval = 8.5

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var cycleStart = Date()
    @State private var autoplaying = true
    @State private var drag = DragCardToSendDragState()
    @State private var manualSend: DragCardToSendManualSend?
    @State private var undoStart: Date?
    @State private var resumeTask: Task<Void, Never>?
    @State private var lastDetent = -2
    @State private var lastPerson = -2

    let autoPlay: Bool

    var body: some View {
        GeometryReader { proxy in
            let layout = DragCardToSendLayout(width: proxy.size.width)
            let paused = reduceMotion || (!autoplaying && !drag.active && manualSend == nil && undoStart == nil)

            TimelineView(.animation(minimumInterval: 1 / 60, paused: paused)) { timeline in
                DragCardToSendScene(
                    layout: layout,
                    frame: frame(at: timeline.date, layout: layout),
                    dragGesture: cardGesture(layout: layout),
                    onUndo: { undo(at: timeline.date) }
                )
            }
        }
        .background(DragCardToSendPalette.page)
        .ignoresSafeArea()
        .onAppear {
            autoplaying = autoPlay && !reduceMotion
            cycleStart = Date()
        }
        .onChange(of: reduceMotion) { _, reduced in
            resumeTask?.cancel()
            drag = DragCardToSendDragState()
            manualSend = nil
            undoStart = nil
            autoplaying = autoPlay && !reduced
            cycleStart = Date()
        }
        .onDisappear { resumeTask?.cancel() }
    }

    private func frame(at date: Date, layout: DragCardToSendLayout) -> DragCardToSendFrame {
        if reduceMotion { return .rest(layout: layout) }
        if drag.active { return .dragging(drag, layout: layout) }
        if let send = manualSend {
            if let undoStart {
                return .undoing(send, elapsed: date.timeIntervalSince(undoStart), layout: layout)
            }
            return .manual(send, elapsed: date.timeIntervalSince(send.started), layout: layout)
        }
        guard autoplaying else { return .rest(layout: layout) }
        let elapsed = max(0, date.timeIntervalSince(cycleStart))
        return .scripted(elapsed.truncatingRemainder(dividingBy: Self.loop), layout: layout)
    }

    private func cardGesture(layout: DragCardToSendLayout) -> some Gesture {
        DragGesture(minimumDistance: 0, coordinateSpace: .named("DragCardToSendScreen"))
            .onChanged { value in
                guard !reduceMotion else { return }
                if !drag.active {
                    resumeTask?.cancel()
                    autoplaying = false
                    manualSend = nil
                    undoStart = nil
                    drag = DragCardToSendDragState(active: true, translation: .zero, location: value.location)
                }
                drag.translation = value.translation
                drag.location = value.location

                let step = DragCardToSendFrame.stepIndex(for: value.translation.height)
                let person = step >= 0 ? layout.nearestPerson(to: value.location.x) : -1
                if step != lastDetent || person != lastPerson {
                    UISelectionFeedbackGenerator().selectionChanged()
                    lastDetent = step
                    lastPerson = person
                }
            }
            .onEnded { value in
                guard !reduceMotion else { return }
                let step = DragCardToSendFrame.stepIndex(for: value.translation.height)
                let person = step >= 0 ? layout.nearestPerson(to: value.location.x) : -1
                drag = DragCardToSendDragState()
                lastDetent = -2
                lastPerson = -2

                if step >= 0, person >= 0 {
                    manualSend = DragCardToSendManualSend(started: Date(), step: step, person: person)
                    UINotificationFeedbackGenerator().notificationOccurred(.success)
                    scheduleAutoplay(after: 2)
                } else {
                    UIImpactFeedbackGenerator(style: .light).impactOccurred(intensity: 0.65)
                    scheduleAutoplay(after: 2)
                }
            }
    }

    private func undo(at date: Date) {
        guard manualSend != nil else { return }
        UIImpactFeedbackGenerator(style: .light).impactOccurred(intensity: 0.7)
        undoStart = date
        scheduleAutoplay(after: 2)
    }

    private func scheduleAutoplay(after delay: TimeInterval) {
        guard autoPlay else { return }
        resumeTask?.cancel()
        resumeTask = Task { @MainActor in
            try? await Task.sleep(for: .seconds(delay))
            guard !Task.isCancelled, !reduceMotion else { return }
            manualSend = nil
            undoStart = nil
            cycleStart = Date()
            autoplaying = true
        }
    }
}

enum DragCardToSendPalette {
    static let page = Color(red: 244 / 255, green: 245 / 255, blue: 247 / 255)
    static let ink = Color(red: 11 / 255, green: 12 / 255, blue: 15 / 255)
    static let mute = Color(red: 94 / 255, green: 99 / 255, blue: 109 / 255)
    static let accent = Color(red: 43 / 255, green: 80 / 255, blue: 240 / 255)
    static let hairline = Color(red: 11 / 255, green: 12 / 255, blue: 15 / 255).opacity(0.08)
}

struct DragCardToSendPerson: Identifiable {
    let id: String
    let name: String
    let fullName: String
}

struct DragCardToSendActivity: Identifiable {
    let id: String
    let name: String
    let meta: String
    let cents: Int
    let person: Int?
    let merchant: DragCardToSendMerchant?
}

enum DragCardToSendMerchant { case coffee, grocer, transit }

enum DragCardToSendData {
    static let people = [
        DragCardToSendPerson(id: "maya", name: "Maya", fullName: "Maya Lindqvist"),
        DragCardToSendPerson(id: "theo", name: "Theo", fullName: "Theo Brandt"),
        DragCardToSendPerson(id: "priya", name: "Priya", fullName: "Priya Shah"),
        DragCardToSendPerson(id: "jonas", name: "Jonas", fullName: "Jonas Weber"),
        DragCardToSendPerson(id: "ana", name: "Ana", fullName: "Ana Costa")
    ]
    static let steps = [5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 60, 70, 80, 90, 100, 125, 150, 175, 200]
    static let rows = [
        DragCardToSendActivity(id: "r1", name: "Arlo’s Coffee", meta: "Today, 8:12 AM", cents: -480, person: nil, merchant: .coffee),
        DragCardToSendActivity(id: "r2", name: "Priya Shah", meta: "Yesterday · Concert tickets", cents: 3200, person: 2, merchant: nil),
        DragCardToSendActivity(id: "r3", name: "Northside Grocer", meta: "Mon, Oct 19", cents: -6317, person: nil, merchant: .grocer),
        DragCardToSendActivity(id: "r4", name: "City Transit", meta: "Sun, Oct 18 · Monthly pass", cents: -8900, person: nil, merchant: .transit)
    ]
}

struct DragCardToSendLayout {
    let width: CGFloat
    let edge: CGFloat = 16
    let cardTop: CGFloat = 140
    let cardWidth: CGFloat
    let cardHeight: CGFloat
    let sectionTop: CGFloat
    let avatarTop: CGFloat
    let activityTop: CGFloat
    let rowTop: CGFloat
    let avatarPitch: CGFloat

    init(width: CGFloat) {
        self.width = width
        cardWidth = width - 32
        cardHeight = round(cardWidth / 1.586)
        sectionTop = ceil((140 + cardHeight + 32) / 4) * 4
        avatarTop = sectionTop + 40
        activityTop = avatarTop + 108
        rowTop = activityTop + 32
        avatarPitch = (cardWidth - 52) / 4
    }

    var avatarCenters: [CGFloat] { (0..<5).map { edge + 26 + avatarPitch * CGFloat($0) } }
    var avatarY: CGFloat { avatarTop + 26 }

    func nearestPerson(to x: CGFloat) -> Int {
        min(4, max(0, Int(round((x - avatarCenters[0]) / avatarPitch))))
    }
}

struct DragCardToSendDragState {
    var active = false
    var translation: CGSize = .zero
    var location: CGPoint = .zero
}

struct DragCardToSendManualSend {
    let started: Date
    let step: Int
    let person: Int
}

struct DragCardToSendFrame {
    var cardOffset: CGFloat = 0
    var cardScale: CGFloat = 1
    var amount: Int?
    var selectedPerson: Int?
    var dropCenter: CGPoint?
    var dropRadius: CGFloat = 0
    var dropStretch: CGFloat = 0
    var receiptProgress: CGFloat = 0
    var receiptPerson: Int = 1
    var receiptAmount: Int = 45
    var undoProgress: CGFloat = 0
    var catchProgress: CGFloat = 0
    var sentCommitted = false
    var closing = false
    var sending = false

    static func rest(layout: DragCardToSendLayout) -> Self { Self() }

    static func stepIndex(for pull: CGFloat) -> Int {
        guard pull >= 20 else { return -1 }
        return min(DragCardToSendData.steps.count - 1, max(0, Int(floor((pull - 20) / 16))))
    }

    static func dragging(_ drag: DragCardToSendDragState, layout: DragCardToSendLayout) -> Self {
        let pull = drag.translation.height
        let step = stepIndex(for: pull)
        let amount = step >= 0 ? DragCardToSendData.steps[step] : nil
        let radius = amount.map { 12 + 5.2 * CGFloat(cbrt(Double($0))) } ?? 0
        let cardOffset = pull >= 0 ? 34 * (1 - exp(-pull / 80)) : pull * 0.12
        return Self(cardOffset: cardOffset, cardScale: 0.98, amount: amount,
                    selectedPerson: step >= 0 ? layout.nearestPerson(to: drag.location.x) : nil,
                    dropCenter: amount == nil ? nil : CGPoint(x: layout.width / 2, y: 50.35 + radius * 0.58),
                    dropRadius: radius)
    }

    static func scripted(_ t: TimeInterval, layout: DragCardToSendLayout) -> Self {
        let down = 0.7, pullStart = 0.82, pullEnd = 2.05
        let slideStart = 2.25, slideEnd = 2.75, release = 3.15
        let contact = release + 0.49, flightEnd = release + 0.7, undo = 6.35

        if t < down { return .rest(layout: layout) }
        if t < release {
            let pull = 156 * smoother((t - pullStart) / (pullEnd - pullStart))
            let step = stepIndex(for: pull)
            let amount = step >= 0 ? DragCardToSendData.steps[step] : nil
            let radius = amount.map { 12 + 5.2 * CGFloat(cbrt(Double($0))) } ?? 0
            let slide = smoother((t - slideStart) / (slideEnd - slideStart))
            let fingerX = 221 + (layout.avatarCenters[1] - 221) * slide
            return Self(cardOffset: 34 * (1 - exp(-pull / 80)),
                        cardScale: 1 - 0.02 * easeOut((t - down) / 0.15), amount: amount,
                        selectedPerson: step >= 0 ? layout.nearestPerson(to: fingerX) : nil,
                        dropCenter: amount == nil ? nil : CGPoint(x: layout.width / 2, y: 50.35 + radius * 0.58),
                        dropRadius: radius)
        }
        if t < flightEnd { return flightFrame(elapsed: t - release, person: 1, amount: 45, layout: layout) }
        if t < undo {
            return Self(receiptProgress: 1, receiptPerson: 1, receiptAmount: 45,
                        undoProgress: max(0, 1 - CGFloat((t - contact) / 4)), sentCommitted: true)
        }
        let close = easeInOut((t - undo) / 0.24)
        let roll = min(1, max(0, (t - undo) / 0.4))
        return Self(receiptProgress: 1 - close, receiptPerson: 1, receiptAmount: 45,
                    sentCommitted: roll < 1, closing: true)
    }

    static func manual(_ send: DragCardToSendManualSend, elapsed: TimeInterval, layout: DragCardToSendLayout) -> Self {
        let amount = DragCardToSendData.steps[send.step]
        if elapsed < 0.7 { return flightFrame(elapsed: elapsed, person: send.person, amount: amount, layout: layout) }
        return Self(receiptProgress: 1, receiptPerson: send.person, receiptAmount: amount,
                    undoProgress: max(0, 1 - CGFloat((elapsed - 0.49) / 4)), sentCommitted: true)
    }

    static func undoing(_ send: DragCardToSendManualSend, elapsed: TimeInterval, layout: DragCardToSendLayout) -> Self {
        Self(receiptProgress: 1 - easeInOut(elapsed / 0.24), receiptPerson: send.person,
             receiptAmount: DragCardToSendData.steps[send.step], sentCommitted: elapsed < 0.4, closing: true)
    }

    private static func flightFrame(elapsed: TimeInterval, person: Int, amount: Int, layout: DragCardToSendLayout) -> Self {
        let radius = 12 + 5.2 * CGFloat(cbrt(Double(amount)))
        let raw = min(1, max(0, elapsed / 0.7))
        let travel = easeInOut(raw / 0.78)
        let start = CGPoint(x: layout.width / 2, y: 50.35 + radius * 0.58)
        let end = CGPoint(x: layout.avatarCenters[person], y: layout.avatarY)
        let control = CGPoint(x: start.x + (end.x - start.x) * 0.12, y: start.y + (end.y - start.y) * 0.55)
        let point = quadratic(start, control, end, travel)
        let drain = easeOut((raw - 0.64) / 0.36)
        let receipt = raw >= 0.7 ? spring((raw - 0.7) / (0.3 / 0.7), damping: 0.8) : 0
        let contact = raw >= 0.7
        return Self(cardOffset: 34 * exp(-elapsed * 10), cardScale: 1 - 0.02 * max(0, 1 - CGFloat(elapsed / 0.1)),
                    amount: amount, selectedPerson: person, dropCenter: point, dropRadius: radius * (1 - drain),
                    dropStretch: 0.2 * sin(.pi * travel), receiptProgress: receipt,
                    receiptPerson: person, receiptAmount: amount, undoProgress: contact ? 1 : 0,
                    catchProgress: contact ? exp(-CGFloat(raw - 0.7) * 7) * sin(CGFloat(raw - 0.7) * 22) : 0,
                    sentCommitted: contact, sending: true)
    }

    private static func quadratic(_ a: CGPoint, _ b: CGPoint, _ c: CGPoint, _ t: CGFloat) -> CGPoint {
        let u = 1 - t
        return CGPoint(x: u * u * a.x + 2 * u * t * b.x + t * t * c.x,
                       y: u * u * a.y + 2 * u * t * b.y + t * t * c.y)
    }

    private static func smoother(_ value: TimeInterval) -> CGFloat {
        let x = CGFloat(min(1, max(0, value)))
        return x * x * x * (x * (x * 6 - 15) + 10)
    }
    private static func easeOut(_ value: TimeInterval) -> CGFloat {
        DragCardToSendBezier.solve(CGFloat(min(1, max(0, value))), x1: 0.23, y1: 1, x2: 0.32, y2: 1)
    }
    private static func easeInOut(_ value: TimeInterval) -> CGFloat {
        DragCardToSendBezier.solve(CGFloat(min(1, max(0, value))), x1: 0.77, y1: 0, x2: 0.175, y2: 1)
    }
    private static func spring(_ value: TimeInterval, damping: CGFloat) -> CGFloat {
        let x = CGFloat(min(1, max(0, value)))
        return min(1.08, max(0, 1 - exp(-8 * damping * x) * cos(10 * x)))
    }
}

enum DragCardToSendBezier {
    static func solve(_ x: CGFloat, x1: CGFloat, y1: CGFloat, x2: CGFloat, y2: CGFloat) -> CGFloat {
        var lo: CGFloat = 0, hi: CGFloat = 1
        for _ in 0..<10 {
            let t = (lo + hi) / 2
            if cubic(t, x1, x2) < x { lo = t } else { hi = t }
        }
        return cubic((lo + hi) / 2, y1, y2)
    }
    private static func cubic(_ t: CGFloat, _ p1: CGFloat, _ p2: CGFloat) -> CGFloat {
        let u = 1 - t
        return 3 * u * u * t * p1 + 3 * u * t * t * p2 + t * t * t
    }
}

enum DragCardToSendFormat {
    static func money(_ cents: Int, signed: Bool) -> String {
        let formatter = NumberFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.numberStyle = .currency
        formatter.currencyCode = "USD"
        formatter.currencySymbol = "$"
        let number = formatter.string(from: NSNumber(value: Double(abs(cents)) / 100)) ?? "$0.00"
        guard signed else { return number }
        if cents < 0 { return "−\(number)" }
        if cents > 0 { return "+\(number)" }
        return number
    }
}
