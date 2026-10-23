import CoreText
import SwiftUI
import UIKit

enum GiftSlotDropTokens {
    enum Colors {
        static let page = "#0D0B09"
        static let ink = "#F6F2EC"
        static let mute = "rgba(246,242,236,0.58)"
        static let faint = "rgba(246,242,236,0.36)"
        static let amber = "#FFB54A"
        static let amberInk = "#FFD08A"
        static let slotDeep = "#050403"
        static let lip = "rgba(255,236,200,0.55)"
        static let button = "#F6F2EC"
        static let buttonInk = "#16120D"
    }

    enum Layout {
        static let edge: CGFloat = 16
        static let nav: CGFloat = 44
        static let eyebrowY: CGFloat = 70
        static let titleY: CGFloat = 106
        static let titleLine: CGFloat = 34
        static let cardY: CGFloat = 356
        static let cardW: CGFloat = 212
        static let cardH: CGFloat = 272
        static let cardR: CGFloat = 22
        static let sideScale = 0.84
        static let sideTurn: CGFloat = 20
        static let gap: CGFloat = 16
        static let shelfY: CGFloat = 612
        static let shelfW: CGFloat = 304
        static let shelfH: CGFloat = 64
        static let slotW: CGFloat = 236
        static let slotH: CGFloat = 12
        static let hintY: CGFloat = 548
        static let giftW: CGFloat = 248
        static let giftH: CGFloat = 318
        static let giftY: CGFloat = 330
        static let stepsY: CGFloat = 522
        static let buttonH: CGFloat = 52
    }

    struct ScreenLayout: Equatable {
        let width: CGFloat
        let height: CGFloat
        let top: CGFloat
        let bottom: CGFloat
        let cx: CGFloat
        let cardY: CGFloat
        let spacing: CGFloat
        let slotY: CGFloat
        let hintY: CGFloat
        let giftY: CGFloat
        let stepsY: CGFloat
        let buttonY: CGFloat
        let dotsY: CGFloat
        let printY: CGFloat

        init(width: CGFloat, height: CGFloat, top: CGFloat, bottom: CGFloat) {
            self.width = width
            self.height = height
            self.top = top
            self.bottom = bottom
            cx = width / 2
            let sideWidth = Layout.cardW * Layout.sideScale
            spacing = Layout.cardW / 2 + sideWidth / 2 + Layout.gap
            cardY = top + Layout.cardY
            slotY = top + Layout.shelfY
            hintY = top + Layout.hintY
            giftY = top + Layout.giftY
            stepsY = top + Layout.stepsY
            buttonY = height - max(bottom, Layout.edge) - 8 - Layout.buttonH
            dotsY = top + Layout.shelfY + 76
            printY = height - max(bottom, Layout.edge) - 20
        }
    }

    struct Gift: Identifiable, Equatable {
        enum Mark { case bowl, rails, leaf, bean }

        let id: String
        let shop: String
        let kind: String
        let offer: String
        let line: String
        let cap: String
        let code: String
        let steps: [String]
        let action: String
        let bg: String
        let bg2: String
        let ink: String
        let sub: String
        let mark: Mark
    }

    static let gifts = [
        Gift(
            id: "kiln", shop: "Kiln & Clay", kind: "studio ceramics", offer: "20%", line: "off your first order",
            cap: "up to $30", code: "KILN-20-H7QM", steps: ["Add pieces worth $40 or more", "Paste the code at checkout"],
            action: "Shop Kiln & Clay", bg: "#B9573A", bg2: "#8E3B24", ink: "#FFF4EA",
            sub: "rgba(255,244,234,0.72)", mark: .bowl
        ),
        Gift(
            id: "northline", shop: "Northline", kind: "rail tickets", offer: "$15", line: "off your first trip",
            cap: "any route, any day", code: "NORTH-15-K4QZ", steps: ["Book any single or return", "Paste the code before paying"],
            action: "Book with Northline", bg: "#1F4D40", bg2: "#123128", ink: "#EAF4EE",
            sub: "rgba(234,244,238,0.70)", mark: .rails
        ),
        Gift(
            id: "fernhouse", shop: "Fernhouse", kind: "houseplants", offer: "2 for 1", line: "on any houseplant",
            cap: "pots included", code: "FERN-241-P2LD", steps: ["Pick two plants, pay for one", "Paste the code at checkout"],
            action: "Shop Fernhouse", bg: "#EDE6D6", bg2: "#D9CFB9", ink: "#1F2A22",
            sub: "rgba(31,42,34,0.66)", mark: .leaf
        ),
        Gift(
            id: "ember", shop: "Ember Roasters", kind: "coffee", offer: "Free", line: "flat white for a week",
            cap: "one a day, 7 days", code: "EMBER-7-W9TB", steps: ["Show the code at the counter", "One flat white a day, 7 days"],
            action: "Find a café", bg: "#2C1E17", bg2: "#1A110C", ink: "#F1D9BC",
            sub: "rgba(241,217,188,0.66)", mark: .bean
        ),
    ]
    static let startIndex = 1

    enum Drag {
        static let decide: CGFloat = 8
        static let commit: CGFloat = 150
        static let full: CGFloat = 210
        static let minScale = 0.8
        static let leanDeg: CGFloat = 46
        static let lift = 1.03
        static let upResist = 0.2
        static let sideResist = 0.25
        static let swipeVelocity: CGFloat = 600
    }

    struct Spring { let duration: Double; let dampingRatio: Double; let stiffness: Double; let damping: Double }
    enum Springs {
        static let snap = Spring(duration: 420, dampingRatio: 0.86, stiffness: 235, damping: 26)
        static let back = Spring(duration: 380, dampingRatio: 0.8, stiffness: 270, damping: 26)
        static let lift = Spring(duration: 200, dampingRatio: 1, stiffness: 500, damping: 34)
    }

    enum Sequence {
        static let sink = 380.0
        static let read = 1_000.0
        static let rise = 700.0
        static let pause = 140.0
        static let flip = 560.0
        static let stagger = 60.0
        static let detailsIn = 260.0
    }

    enum Close { static let out = 220.0; static let back = 420.0 }
    enum Title { static let out = 140.0; static let inMs = 220.0; static let rise: CGFloat = 6 }
    enum ButtonPress { static let scale = 0.97; static let inMs = 100.0; static let outMs = 160.0 }

    static let reducedMs = 200.0
    static let loop = 10.0
    static let resumeAfterMs = 2_000.0

    enum Script {
        static let swipes = [(at: 500.0, ms: 460.0, dx: -220.0), (at: 8_950.0, ms: 460.0, dx: 220.0)]
        static let pullDown = 1_450.0
        static let pullUp = 2_500.0
        static let pullBy: CGFloat = 196
        static let done = 7_900.0
    }

    static func color(_ source: String) -> Color {
        Color(uiColor: uiColor(source))
    }

    static func uiColor(_ source: String) -> UIColor {
        if source.hasPrefix("#") {
            let value = UInt64(source.dropFirst(), radix: 16) ?? 0
            return UIColor(
                red: CGFloat((value >> 16) & 0xFF) / 255,
                green: CGFloat((value >> 8) & 0xFF) / 255,
                blue: CGFloat(value & 0xFF) / 255,
                alpha: 1
            )
        }
        let values = source
            .replacingOccurrences(of: "rgba(", with: "")
            .replacingOccurrences(of: ")", with: "")
            .split(separator: ",")
            .compactMap { Double($0.trimmingCharacters(in: .whitespaces)) }
        guard values.count == 4 else { return .clear }
        return UIColor(
            red: values[0] / 255,
            green: values[1] / 255,
            blue: values[2] / 255,
            alpha: values[3]
        )
    }
}

enum GiftSlotDropFonts {
    static let regular = "Inter24pt-Regular"
    static let medium = "Inter-Medium"
    static let semibold = "Inter-SemiBold"
    static let mono = "JetBrainsMono-Medium"

    private static var registered = false

    static func register() {
        guard !registered else { return }
        registered = true
        for file in ["Inter-Regular", "Inter-Medium", "Inter-SemiBold", "JetBrainsMono-Medium"] {
            guard let url = Bundle.main.url(forResource: "GiftSlotDrop-\(file)", withExtension: "ttf") else {
                assertionFailure("Missing Gift Slot Drop font: \(file)")
                continue
            }
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
    }

    static func font(_ name: String, size: CGFloat) -> Font {
        register()
        return .custom(name, size: size)
    }
}
