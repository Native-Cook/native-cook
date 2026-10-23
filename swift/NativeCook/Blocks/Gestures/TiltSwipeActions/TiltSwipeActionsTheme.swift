import CoreText
import SwiftUI
import UIKit

enum TiltSwipeActionsColor {
    static let paper = Color(red: 245 / 255, green: 241 / 255, blue: 232 / 255)
    static let stock = Color(red: 251 / 255, green: 249 / 255, blue: 244 / 255)
    static let ink = Color(red: 23 / 255, green: 24 / 255, blue: 29 / 255)
    static let ink2 = Color(red: 110 / 255, green: 105 / 255, blue: 95 / 255)
    static let ink3 = Color(red: 163 / 255, green: 157 / 255, blue: 145 / 255)
    static let ivory = paper
    static let brass = Color(red: 200 / 255, green: 173 / 255, blue: 126 / 255)
    static let brassPaper = Color(red: 139 / 255, green: 107 / 255, blue: 58 / 255)
    static let brassDot = Color(red: 166 / 255, green: 132 / 255, blue: 79 / 255)
    static let rule = ink.opacity(0.10)
    static let ruleBottom = ink.opacity(0.22)
    static let ring = ink.opacity(0.16)
    static let pieceEdge = ink.opacity(0.14)
    static let tabBar = Color(red: 239 / 255, green: 234 / 255, blue: 223 / 255)
    static let monoToast = Color(red: 46 / 255, green: 47 / 255, blue: 54 / 255)
    static let flag = Color(red: 43 / 255, green: 44 / 255, blue: 51 / 255)
    static let delete = Color(red: 143 / 255, green: 58 / 255, blue: 44 / 255)
}

enum TiltSwipeActionsLayout {
    static let gutter: CGFloat = 16
    static let cardHeight: CGFloat = 128
    static let cardRadius: CGFloat = 20
    static let cardFrameInset: CGFloat = 6
    static let cardFrameRadius: CGFloat = 15
    static let cardPad: CGFloat = 24
    static let sectionGap: CGFloat = 28
    static let sectionTitle: CGFloat = 32
    static let listGap: CGFloat = 12
    static let listPad: CGFloat = 5
    static let row: CGFloat = 72
    static let circle: CGFloat = 40
    static let text: CGFloat = 52
    static let inset: CGFloat = 4
    static let bar: CGFloat = 64
    static let outer: CGFloat = 14
    static let mini: CGFloat = 16
    static let mono: CGFloat = 28
    static let hud: CGFloat = 40
    static let hudOver: CGFloat = 56
    static let toast: CGFloat = 56
    static let toastRadius: CGFloat = 16
    static let tile: CGFloat = 72
    static let tabBar: CGFloat = 54
    static let tab: CGFloat = 56
    static let tabHeight: CGFloat = 40
    static let tabGap: CGFloat = 8
    static let fab: CGFloat = 48
    static let finger: CGFloat = 40
    static let menuWidth: CGFloat = 220
    static let menuItem: CGFloat = 52
    static let menuPadY: CGFloat = 8
    static let menuRadius: CGFloat = 16
    static let openX: CGFloat = -144
}

enum TiltSwipeActionsScreenInsets {
    static var current: UIEdgeInsets {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first(where: \.isKeyWindow)?
            .safeAreaInsets ?? .zero
    }
}

struct TiltSwipeActionsScene {
    let width: CGFloat
    let height: CGFloat
    let top: CGFloat
    let bottom: CGFloat

    var listWidth: CGFloat { width - 2 * TiltSwipeActionsLayout.gutter }
    var cardTop: CGFloat { top }
    var sectionTop: CGFloat { cardTop + TiltSwipeActionsLayout.cardHeight + TiltSwipeActionsLayout.sectionGap }
    var listTop: CGFloat { sectionTop + TiltSwipeActionsLayout.sectionTitle + TiltSwipeActionsLayout.listGap }
    var rowsTop: CGFloat { listTop + TiltSwipeActionsLayout.listPad }
    var barTop: CGFloat { height - bottom - TiltSwipeActionsLayout.tabBar }
    var toastTop: CGFloat { barTop - TiltSwipeActionsLayout.listGap - TiltSwipeActionsLayout.toast - 4 }
    var linkTop: CGFloat { barTop - 64 }
    var fabLeft: CGFloat { width - TiltSwipeActionsLayout.gutter - TiltSwipeActionsLayout.fab }
    var fabTop: CGFloat { barTop + 4 }
    func tabCenter(_ index: Int) -> CGFloat {
        TiltSwipeActionsLayout.gutter + CGFloat(index) * (TiltSwipeActionsLayout.tab + TiltSwipeActionsLayout.tabGap) + TiltSwipeActionsLayout.tab / 2
    }
}

enum TiltSwipeActionsFont {
    static let display = "TSABodoni48-Regular"
    static let money22 = "TSABodoni22-Regular"
    static let money22Regular = "TSABodoni22R-Regular"
    static let money = "TSABodoni20-Regular"
    static let moneySmall = "TSABodoni14-Regular"
    static let monogram = "TSABodoni11-Regular"
    static let italic = "TSABodoniItalic26-Regular"
    static let sans = "TSAInstrument400-Regular"
    static let sansMedium = "TSAInstrument500-Regular"
    static let sansSemi = "TSAInstrument600-Regular"

    private static let registration: Void = {
        let files = ["TSABodoni48", "TSABodoni22", "TSABodoni22R", "TSABodoni20", "TSABodoni14", "TSABodoni11", "TSABodoniItalic26", "TSAInstrument400", "TSAInstrument500", "TSAInstrument600"]
        for file in files {
            guard let url = Bundle.main.url(forResource: "TiltSwipeActions-\(file)", withExtension: "ttf") else { continue }
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
    }()

    static func register() { _ = registration }
}

enum TiltSwipeActionsAssets {
    private static func image(_ name: String, scale: CGFloat) -> Image {
        guard let url = Bundle.main.url(forResource: "TiltSwipeActions-\(name)@3x", withExtension: "png"),
              let data = try? Data(contentsOf: url), let uiImage = UIImage(data: data, scale: scale) else {
            assertionFailure("Missing TiltSwipeActions image: \(name)")
            return Image(uiImage: UIImage())
        }
        return Image(uiImage: uiImage)
    }

    static let paper = image("paper", scale: 3)
    static let guilloche = image("guilloche", scale: 3)
}

enum TiltSwipeActionsCurve {
    static let out = Animation.timingCurve(0.23, 1, 0.32, 1)
    static let inOut = Animation.timingCurve(0.77, 0, 0.175, 1)
    static let sheet = Animation.timingCurve(0.32, 0.72, 0, 1)

    static func clamped(_ value: CGFloat, _ low: CGFloat = 0, _ high: CGFloat = 1) -> CGFloat {
        min(max(value, low), high)
    }

    static func smooth(_ value: CGFloat) -> CGFloat {
        let x = clamped(value)
        return x * x * (3 - 2 * x)
    }

    static func interpolate(_ value: CGFloat, from: CGFloat, to: CGFloat) -> CGFloat {
        guard to > from else { return value >= to ? 1 : 0 }
        return clamped((value - from) / (to - from))
    }
}
