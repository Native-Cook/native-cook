import CoreText
import SwiftUI

private enum TiltSwipeActionsTokenColor {
    static func make(_ token: String) -> Color {
        if token.hasPrefix("#"), let value = UInt64(token.dropFirst(), radix: 16) {
            return Color(.sRGB, red: Double((value >> 16) & 0xff) / 255, green: Double((value >> 8) & 0xff) / 255, blue: Double(value & 0xff) / 255, opacity: 1)
        }
        let values = token.replacingOccurrences(of: "rgba(", with: "").replacingOccurrences(of: ")", with: "").split(separator: ",").compactMap { Double($0) }
        guard values.count == 4 else { return .clear }
        return Color(.sRGB, red: values[0] / 255, green: values[1] / 255, blue: values[2] / 255, opacity: values[3])
    }
}

enum TiltSwipeActionsColor {
    private typealias Token = TiltSwipeActionsTokens.Color
    static let gradient = Token.gradient.map(TiltSwipeActionsTokenColor.make)
    static let gradientStops = Token.gradientStops.map { CGFloat($0) }
    static let desk = Token.desk.map(TiltSwipeActionsTokenColor.make)
    static let plum = TiltSwipeActionsTokenColor.make(Token.plum)
    static let plumInk = TiltSwipeActionsTokenColor.make(Token.plumInk)
    static let cardInk = TiltSwipeActionsTokenColor.make(Token.cardInk)
    static let white = TiltSwipeActionsTokenColor.make(Token.white)
    static let pink = TiltSwipeActionsTokenColor.make(Token.pink)
    static let pinkDeep = TiltSwipeActionsTokenColor.make(Token.pinkDeep)
    static let title = TiltSwipeActionsTokenColor.make(Token.title)
    static let subtitle = TiltSwipeActionsTokenColor.make(Token.subtitle)
    static let cardLabel = TiltSwipeActionsTokenColor.make(Token.cardLabel)
    static let cardCurrency = TiltSwipeActionsTokenColor.make(Token.cardCurrency)
    static let slab = TiltSwipeActionsTokenColor.make(Token.slab)
    static let slabEdge = TiltSwipeActionsTokenColor.make(Token.slabEdge)
    static let slabShadow = TiltSwipeActionsTokenColor.make(Token.slabShadow)
    static let panel = TiltSwipeActionsTokenColor.make(Token.panel)
    static let panelEdge = TiltSwipeActionsTokenColor.make(Token.panelEdge)
    static let deskEdge = TiltSwipeActionsTokenColor.make(Token.deskEdge)
    static let panelIcon = TiltSwipeActionsTokenColor.make(Token.panelIcon)
    static let panelIconActive = TiltSwipeActionsTokenColor.make(Token.panelIconActive)
    static let menuInk = TiltSwipeActionsTokenColor.make(Token.menuInk)
    static let menuPlus = TiltSwipeActionsTokenColor.make(Token.menuPlus)
    static let menuScrim = TiltSwipeActionsTokenColor.make(Token.menuScrim)
    static let income = TiltSwipeActionsTokenColor.make(Token.income)
    static let transfer = TiltSwipeActionsTokenColor.make(Token.transfer)
    static let scrim = TiltSwipeActionsTokenColor.make(Token.scrim)
    static let finger = TiltSwipeActionsTokenColor.make(Token.finger)
    static let lens = TiltSwipeActionsTokenColor.make(Token.lens)
    static let lensRim = TiltSwipeActionsTokenColor.make(Token.lensRim)
    static let glassEdge = TiltSwipeActionsTokenColor.make(Token.glassEdge)
    static let pinkGlow = TiltSwipeActionsTokenColor.make(Token.pinkGlow)
    static let sheen = Token.sheen.map(TiltSwipeActionsTokenColor.make)
    static let fingerEdge = TiltSwipeActionsTokenColor.make(Token.fingerEdge)
}

enum TiltSwipeActionsTint {
    private typealias Token = TiltSwipeActionsTokens.IconTint
    static let yellow = TiltSwipeActionsTokenColor.make(Token.yellow)
    static let lilac = TiltSwipeActionsTokenColor.make(Token.lilac)
    static let peach = TiltSwipeActionsTokenColor.make(Token.peach)
    static let mint = TiltSwipeActionsTokenColor.make(Token.mint)
    static let sky = TiltSwipeActionsTokenColor.make(Token.sky)
    static let rose = TiltSwipeActionsTokenColor.make(Token.rose)
}

enum TiltSwipeActionsTiltView {
    private typealias Token = TiltSwipeActionsTokens.TiltView
    static let p0 = (x: Double(Token.p0.x), y: Double(Token.p0.y))
    static let u = (x: Double(Token.u.x), y: Double(Token.u.y))
    static let v = (x: Double(Token.v.x), y: Double(Token.v.y))
    static let cropX = Token.cropX; static let scale = Token.scale; static let screenRadius = Token.screenRadius
}

enum TiltSwipeActionsFlat {
    private typealias Token = TiltSwipeActionsTokens.Flat
    static let card = Token.card; static let section = Token.section; static let rowsTop = Token.rowsTop
    static let rowLeft = Token.rowLeft; static let rowRight = Token.rowRight; static let panel = Token.panel
    static let tabs = Token.tabs; static let tabY = Token.tabY; static let fab = Token.fab; static let menu = Token.menu; static let cards = Token.cards
    static let width = TiltSwipeActionsTokens.flatWidth; static let height = TiltSwipeActionsTokens.flatHeight
    static let planeWidth = TiltSwipeActionsTokens.planeWidth; static let planeHeight = TiltSwipeActionsTokens.planeHeight
}

enum TiltSwipeActionsLayout {
    private typealias Token = TiltSwipeActionsTokens.Layout
    static let gutter = Token.gutter; static let cardTop = Token.cardTop; static let cardHeight = Token.cardHeight
    static let cardRadius = Token.cardRadius; static let cardBadge = Token.cardBadge; static let cardBadgeRadius = Token.cardBadgeRadius
    static let sectionGap = Token.sectionGap; static let sectionTitle = Token.sectionTitle; static let sectionBottom = Token.sectionBottom
    static let row = Token.row; static let circle = Token.circle; static let textLeft = Token.textLeft; static let rowRadius = Token.rowRadius
    static let tile = Token.tile; static let tileHeight = Token.tileHeight; static let tileRadius = Token.tileRadius; static let tileGap = Token.tileGap
    static let panel = Token.panel; static let panelRadius = Token.panelRadius; static let fab = Token.fab; static let fabRadius = Token.fabRadius
    static let fabRight = Token.fabRight; static let pillHeight = Token.pillHeight; static let pillGap = Token.pillGap
    static let menuItem = Token.menuItem; static let lens = Token.lens; static let menuWidth = Token.menuWidth; static let menuRadius = Token.menuRadius
    static let finger = Token.finger; static let openX = TiltSwipeActionsTokens.OPEN_X
    static let pillBottom = TiltSwipeActionsFlat.planeHeight - TiltSwipeActionsFlat.panel.top + TiltSwipeActionsTokens.pillBottomOffset
}

struct TiltSwipeActionsTextStyle {
    let size: CGFloat
    let lineHeight: CGFloat
    let fontName: String
    var kerning: CGFloat = 0
}

enum TiltSwipeActionsType {
    private typealias Token = TiltSwipeActionsTokens.Typography
    private static func postScriptName(_ family: String) -> String {
        switch family {
        case TiltSwipeActionsTokens.Font.semibold: return TiltSwipeActionsTokens.Font.postScriptSemibold
        case TiltSwipeActionsTokens.Font.bold: return TiltSwipeActionsTokens.Font.postScriptBold
        default: return TiltSwipeActionsTokens.Font.postScriptMedium
        }
    }
    static let cardLabel = TiltSwipeActionsTextStyle(size: Token.cardLabel.fontSize, lineHeight: Token.cardLabel.lineHeight, fontName: postScriptName(Token.cardLabel.fontFamily))
    static let balance = TiltSwipeActionsTextStyle(size: Token.balance.fontSize, lineHeight: Token.balance.lineHeight, fontName: postScriptName(Token.balance.fontFamily), kerning: Token.balance.letterSpacing)
    static let currency = TiltSwipeActionsTextStyle(size: Token.currency.fontSize, lineHeight: Token.currency.lineHeight, fontName: postScriptName(Token.currency.fontFamily))
    static let section = TiltSwipeActionsTextStyle(size: Token.section.fontSize, lineHeight: Token.section.lineHeight, fontName: postScriptName(Token.section.fontFamily), kerning: Token.section.letterSpacing)
    static let rowTitle = TiltSwipeActionsTextStyle(size: Token.rowTitle.fontSize, lineHeight: Token.rowTitle.lineHeight, fontName: postScriptName(Token.rowTitle.fontFamily))
    static let rowSub = TiltSwipeActionsTextStyle(size: Token.rowSub.fontSize, lineHeight: Token.rowSub.lineHeight, fontName: postScriptName(Token.rowSub.fontFamily))
    static let amount = TiltSwipeActionsTextStyle(size: Token.amount.fontSize, lineHeight: Token.amount.lineHeight, fontName: postScriptName(Token.amount.fontFamily))
    static let tile = TiltSwipeActionsTextStyle(size: Token.tile.fontSize, lineHeight: Token.tile.lineHeight, fontName: postScriptName(Token.tile.fontFamily), kerning: Token.tile.letterSpacing)
    static let pill = TiltSwipeActionsTextStyle(size: Token.pill.fontSize, lineHeight: Token.pill.lineHeight, fontName: postScriptName(Token.pill.fontFamily))
    static let pillAction = TiltSwipeActionsTextStyle(size: Token.pillAction.fontSize, lineHeight: Token.pillAction.lineHeight, fontName: postScriptName(Token.pillAction.fontFamily))
    static let menu = TiltSwipeActionsTextStyle(size: Token.menu.fontSize, lineHeight: Token.menu.lineHeight, fontName: postScriptName(Token.menu.fontFamily))
}

enum TiltSwipeActionsFonts {
    private static let registration: Void = {
        for filename in ["TiltSwipe-Montserrat-Medium", "TiltSwipe-Montserrat-SemiBold", "TiltSwipe-Montserrat-Bold"] {
            guard let url = Bundle.main.url(forResource: filename, withExtension: "ttf") else { continue }
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
    }()

    static func register() {
        _ = registration
    }
}

struct TiltSwipeActionsLabel: View {
    let text: String
    let style: TiltSwipeActionsTextStyle
    let color: Color
    var tabular = false
    var fitsWidth = false

    var body: some View {
        let base = Text(text).font(.custom(style.fontName, fixedSize: style.size)).kerning(style.kerning)
        Group { if tabular { base.monospacedDigit() } else { base } }
            .foregroundStyle(color)
            .lineLimit(1)
            .modifier(TiltSwipeActionsNoWrap(enabled: !fitsWidth))
            .frame(height: style.lineHeight)
    }
}

private struct TiltSwipeActionsNoWrap: ViewModifier {
    let enabled: Bool
    func body(content: Content) -> some View { if enabled { content.fixedSize(horizontal: true, vertical: false) } else { content } }
}

extension Double {
    func tiltClamped(_ low: Double, _ high: Double) -> Double { Swift.min(Swift.max(self, low), high) }
}

enum TiltSwipeActionsGeometry {
    static let plane: CGAffineTransform = {
        typealias T = TiltSwipeActionsTiltView
        return CGAffineTransform(a: T.scale * T.u.x, b: T.scale * T.u.y, c: T.scale * T.v.x, d: T.scale * T.v.y, tx: T.scale * (T.p0.x - T.cropX - TiltSwipeActionsFlat.height * T.v.x), ty: T.scale * (T.p0.y - TiltSwipeActionsFlat.height * T.v.y))
    }()
}
