import SwiftUI

/// Colours, sizes and type measured from the source's to-do list (1 px = 1.426 pt).
enum DragMultiSelectColor {
    private typealias Token = DragMultiSelectTokens.Color
    static let background = Color(hex: Token.background)
    /// The source's titles read softer than pure ink.
    static let ink = Color(hex: Token.ink)
    static let mute = Color(hex: Token.mute)
    static let check = Color(hex: Token.check)
    static let note = Color(hex: Token.note)
    static let nav = Color(hex: Token.nav)
    static let tray = Color(hex: Token.tray)
    static let trayLine = Color(hex: Token.trayLine)
    static let blue = Color(hex: Token.blue)
    static let blueDeep = Color(hex: Token.blueDeep)
    /// Sampled at row centres on the rectified frame.
    static let band = Color(hex: Token.band)
    /// The soft 1–2 px seam between stacked selected rows.
    static let bandSeam = Color(.sRGB, red: Double(Token.bandSeam.r) / 255, green: Double(Token.bandSeam.g) / 255, blue: Double(Token.bandSeam.b) / 255, opacity: Token.bandSeam.a)
    /// The three stops used by the light sweep that crosses a selected band.
    /// Keep the transparent stops white so this matches RN's `rgba(255,255,255,0)`.
    static let gloss = [
        Color(.sRGB, red: Double(Token.gloss[0].r) / 255, green: Double(Token.gloss[0].g) / 255, blue: Double(Token.gloss[0].b) / 255, opacity: Token.gloss[0].a),
        Color(.sRGB, red: Double(Token.gloss[1].r) / 255, green: Double(Token.gloss[1].g) / 255, blue: Double(Token.gloss[1].b) / 255, opacity: Token.gloss[1].a),
        Color(.sRGB, red: Double(Token.gloss[2].r) / 255, green: Double(Token.gloss[2].g) / 255, blue: Double(Token.gloss[2].b) / 255, opacity: Token.gloss[2].a),
    ]
    static let circle = Color(hex: Token.circle)
    static let toolbar = Color(hex: Token.toolbar)
    static let toolbarInk = Color(hex: Token.toolbarInk)
    static let white = Color(hex: Token.white)
    static let finger = Color(.sRGB, red: Double(Token.finger.r) / 255, green: Double(Token.finger.g) / 255, blue: Double(Token.finger.b) / 255, opacity: Token.finger.a)
    static let fingerEdge = Color(.sRGB, red: Double(Token.fingerEdge.r) / 255, green: Double(Token.fingerEdge.g) / 255, blue: Double(Token.fingerEdge.b) / 255, opacity: Token.fingerEdge.a)

    /// The two ends of the add button's colour morph, as sRGB channels.
    static let blueRGB = rgb(Token.blue)
    static let toolbarRGB = rgb(Token.toolbar)

    private static func rgb(_ hex: UInt32) -> (r: Double, g: Double, b: Double) {
        (Double((hex >> 16) & 0xFF) / 255, Double((hex >> 8) & 0xFF) / 255, Double(hex & 0xFF) / 255)
    }
}

/// Spacing scale from the React Native block. Measured geometry keeps its explicit values;
/// additions use these named steps so the three ports share the same source tokens.
enum DragMultiSelectSpace {
    static let xs = DragMultiSelectTokens.Space.xs
    static let sm = DragMultiSelectTokens.Space.sm
    static let md = DragMultiSelectTokens.Space.md
    static let lg = DragMultiSelectTokens.Space.lg
    static let gutter = DragMultiSelectTokens.Space.gutter
    static let xl = DragMultiSelectTokens.Space.xl
    static let xxl = DragMultiSelectTokens.Space.xxl
}

enum DragMultiSelectLayout {
    private typealias Token = DragMultiSelectTokens.Layout
    /// Nested source names are kept alongside the flattened aliases used by the Swift draft.
    enum Done {
        static let width = Token.doneWidth
        static let height = Token.doneHeight
        static let radius = Token.doneRadius
    }

    enum Toolbar {
        static let width = Token.toolbarWidth
        static let height = Token.toolbarHeight
        static let radius = Token.toolbarRadius
        static let bottom = Token.toolbarBottom
    }

    static let gutter = Token.gutter
    static let nav = Token.nav
    static let navTop = Token.navTop
    static let row = Token.row
    static let listTop = Token.listTop
    static let check = Token.check
    static let checkRadius = Token.checkRadius
    static let textLeft = Token.textLeft
    static let circle = Token.circle
    static let circleRight = Token.circleRight
    /// Touch area of the circles, from the right edge.
    static let circleColumn = Token.circleColumn
    static let bandInset = Token.bandInset
    static let bandRadius = Token.bandRadius
    static let doneWidth = Done.width
    static let doneHeight = Done.height
    static let doneRadius = Done.radius
    static let more = Token.more
    /// Measured on the rectified frame: 37–365 pt, bottom at 838.7 pt.
    static let toolbarWidth = Toolbar.width
    static let toolbarHeight = Toolbar.height
    static let toolbarRadius = Toolbar.radius
    static let toolbarBottom = Toolbar.bottom
    /// Tool centres from the toolbar's left edge (When and Move groups, trash, more).
    static let tools = Token.tools
    static let toolIcon = Token.toolIcon
    static let fab = Token.fab
    static let fabBottom = Token.fabBottom
    /// Auto-scroll zone.
    static let edge = Token.edge
    static let finger = Token.finger
}

enum DragMultiSelectType {
    // Overlaid on the source's rows.
    static let row = Font.system(size: DragMultiSelectTokens.Typography.rowFontSize, weight: .regular)
    static let rowLineHeight = DragMultiSelectTokens.Typography.rowLineHeight
    static let nav = Font.system(size: DragMultiSelectTokens.Typography.navFontSize, weight: .semibold)
    static let navLineHeight = DragMultiSelectTokens.Typography.navLineHeight
    static let done = Font.system(size: DragMultiSelectTokens.Typography.doneFontSize, weight: .semibold)
    static let doneLineHeight = DragMultiSelectTokens.Typography.doneLineHeight
    static let tool = Font.system(size: DragMultiSelectTokens.Typography.toolFontSize, weight: .semibold)
    static let toolLineHeight = DragMultiSelectTokens.Typography.toolLineHeight
}
