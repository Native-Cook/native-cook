import CoreText
import SwiftUI
import UIKit

enum HoldDragResizePalette {
    static let page = Color(red: 6 / 255, green: 8 / 255, blue: 13 / 255)
    static let ink = Color(red: 245 / 255, green: 246 / 255, blue: 248 / 255)
    static let mute = Color(red: 235 / 255, green: 238 / 255, blue: 245 / 255).opacity(0.56)
    static let faint = Color(red: 235 / 255, green: 238 / 255, blue: 245 / 255).opacity(0.34)
    static let focus = Color(red: 1, green: 181 / 255, blue: 71 / 255)
    static let focusInk = Color(red: 1, green: 208 / 255, blue: 138 / 255)
    static let task = Color(red: 124 / 255, green: 196 / 255, blue: 1)
    static let meeting = Color(red: 217 / 255, green: 222 / 255, blue: 232 / 255)
    static let now = Color(red: 1, green: 90 / 255, blue: 78 / 255)
    static let onAccent = Color(red: 26 / 255, green: 18 / 255, blue: 6 / 255)
    static let finger = Color.white.opacity(0.14)
    static let fingerEdge = Color.white.opacity(0.42)
}

enum HoldDragResizeFonts {
    static let regular = "Inter24pt-Regular"
    static let medium = "Inter-Medium"
    static let semibold = "Inter-SemiBold"
    static let mono = "JetBrainsMono-Medium"
    static let monoBold = "JetBrainsMono-Bold"

    private static var registered = false

    static func register() {
        guard !registered else { return }
        registered = true
        let resources = [
            "HoldDragResize-Inter-Regular",
            "HoldDragResize-Inter-Medium",
            "HoldDragResize-Inter-SemiBold",
            "HoldDragResize-JetBrainsMono-Medium",
            "HoldDragResize-JetBrainsMono-Bold",
        ]
        for resource in resources {
            guard let url = Bundle.main.url(forResource: resource, withExtension: "ttf") else { continue }
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
    }
}

enum HoldDragResizeKind: Int {
    case meeting
    case task
    case focus
}

struct HoldDragResizePlanItem: Identifiable {
    let id: String
    let kind: HoldDragResizeKind
    let start: Double
    let end: Double
    let title: String
    let note: String
}

struct HoldDragResizeWeekday {
    let day: String
    let date: Int
    let dots: [HoldDragResizeKind]
}

enum HoldDragResizeData {
    static let dayStart = 9.0 * 60
    static let dayEnd = 18.0 * 60
    static let now = 9.0 * 60 + 41
    static let memo = 1

    static let plan: [HoldDragResizePlanItem] = [
        .init(id: "standup", kind: .meeting, start: 540, end: 570, title: "Standup", note: "Payments squad"),
        .init(id: "memo", kind: .focus, start: 600, end: 660, title: "Pricing memo, first draft", note: "Focus"),
        .init(id: "review", kind: .task, start: 675, end: 705, title: "Review Ada’s checkout PR", note: ""),
        .init(id: "dentist", kind: .task, start: 720, end: 750, title: "Call the dentist", note: ""),
        .init(id: "lunch", kind: .meeting, start: 780, end: 840, title: "Lunch with Maya", note: "Brindisa, Borough Market"),
        .init(id: "crit", kind: .meeting, start: 870, end: 930, title: "Design crit", note: "Room 4B · 8 people"),
        .init(id: "plan", kind: .task, start: 960, end: 990, title: "Plan next week", note: ""),
        .init(id: "climb", kind: .meeting, start: 1020, end: 1065, title: "Climbing with Jo", note: "The Castle, Green Lanes"),
    ]

    static let week: [HoldDragResizeWeekday] = [
        .init(day: "M", date: 13, dots: [.meeting, .task]),
        .init(day: "T", date: 14, dots: [.focus, .meeting, .task]),
        .init(day: "W", date: 15, dots: [.meeting]),
        .init(day: "T", date: 16, dots: [.focus, .task, .meeting]),
        .init(day: "F", date: 17, dots: [.focus, .meeting]),
        .init(day: "S", date: 18, dots: [.task]),
        .init(day: "S", date: 19, dots: []),
    ]

    static func clock(_ minutes: Double) -> String {
        let value = Int(minutes.rounded())
        return String(format: "%02d:%02d", value / 60, value % 60)
    }

    static func length(_ minutes: Double) -> String {
        let value = Int(abs(minutes).rounded())
        let hours = value / 60
        let mins = value % 60
        if hours == 0 { return "\(mins) min" }
        return mins == 0 ? "\(hours) h" : "\(hours) h \(mins)"
    }
}

struct HoldDragResizeLayout: Equatable {
    let width: CGFloat
    let height: CGFloat
    let top: CGFloat
    let bottom: CGFloat
    let gridTop: CGFloat
    let barTop: CGFloat
    let ppm: CGFloat
    let cardX: CGFloat = 76
    let cardW: CGFloat
    let right: CGFloat

    init(width: CGFloat, height: CGFloat, top: CGFloat, bottom: CGFloat) {
        self.width = width
        self.height = height
        self.top = top
        self.bottom = bottom
        gridTop = top + 160
        barTop = height - max(bottom, 16) - 8 - 52
        ppm = min(1.2, (barTop - 16 - gridTop) / 540)
        cardW = width - 76 - 16
        right = width - 16
    }

    func y(_ minute: Double) -> CGFloat {
        gridTop + CGFloat(minute - HoldDragResizeData.dayStart) * ppm
    }
}

enum HoldDragResizeMetrics {
    static let edge: CGFloat = 16
    static let cardInset: CGFloat = 2
    static let radius: CGFloat = 12
    static let padX: CGFloat = 12
    static let padY: CGFloat = 8
    static let pillHeight: CGFloat = 22
    static let labelRight: CGFloat = 50
    static let railX: CGFloat = 63
    static let capsule: CGFloat = 6
    static let finger: CGFloat = 44
    static let holdDuration = 0.3
    static let resumeDuration = 2.0
}

