import SwiftUI

enum RoamingFabTabBarMotion {
    /// Seconds of one autoPlay loop, and when in it each tab is chosen: Search, Friends, Chats, Home.
    static let loop = 9.0
    static let steps: [(Double, Int)] = [(1.2, 1), (2.8, 2), (4.4, 3), (6.4, 0)]
    /// Seconds after a touch before the loop starts again.
    static let userHold = 3.0

    /// Seconds a tab change runs for: the new icon's wiggle. The clock that drives it is linear.
    static let change = 0.5
    /// Seconds an icon takes to change drawing, and a page to fade in.
    static let iconFade = 0.15
    static let pageFade = 0.2
    /// The wiggle: degrees of the first swing, how fast it dies, and how much the icon grows.
    static let wiggle = 16.0
    static let wiggleDecay = 0.8
    static let wiggleGrow = 0.15

    /// Seconds the button takes to shrink away, to pop in, and to slide between its two places.
    static let shrink = 0.2
    static let pop = 0.4
    static let slide = 0.45
    /// The same slide under reduced motion, where it is a fade out and a fade in.
    static let reducedSlide = 0.3
    /// The curves: the shrink eases in, the pop passes its end by a tenth, the slide by less.
    static let shrinkEase = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.4, y: 0), endControlPoint: UnitPoint(x: 1, y: 1))
    static let popEase = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.34, y: 1.56), endControlPoint: UnitPoint(x: 0.64, y: 1))
    static let slideEase = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.34, y: 1.25), endControlPoint: UnitPoint(x: 0.64, y: 1))
    /// How high the button rises on its way across, and how much a press shrinks it.
    static let arc = 10.0
    static let press = 0.08
    static let pressIn = 0.08
    static let pressOut = 0.16

    /// The tab bar above the bottom inset, the least that inset counts for, and the icons' middle below the bar's top.
    static let bar = 56.0
    static let barBottom = 28.0
    static let iconY = 30.0
    /// Half the square a tab answers to a touch in.
    static let tabHit = 28.0
    /// The button on the first tab and on the last: its size and its middle above the bar's top.
    static let homeSize = 58.0
    static let homeRise = 52.0
    static let chatSize = 50.0
    static let chatRise = 46.0
    /// The last tab's button, from the right edge to its middle.
    static let chatInset = 49.0
    /// The button's shadow: its offset, its blur radius, and its strength.
    static let shadowY = 6.0
    static let shadowBlur = 7.0
    static let shadowAlpha = 0.3
    static let glyphStroke = 2.4
    /// The bubble of the last tab's button is the tab's own, a little smaller.
    static let composeScale = 0.92

    static let margin = 20.0
    static let titleTop = 17.0
    static let titleLine = 41.0

    static func clamp01(_ t: Double) -> Double { min(1, max(0, t)) }

    static func mix(_ a: Double, _ b: Double, _ t: Double) -> Double { a + (b - a) * t }

    /// Degrees the chosen icon is turned by, `t` of the way through its wiggle: three swings that die away.
    static func wiggle(at t: Double) -> Double {
        -wiggle * sin(3 * .pi * t) * pow(1 - clamp01(t), wiggleDecay)
    }

    static func swell(at t: Double) -> Double { 1 + wiggleGrow * sin(.pi * clamp01(t)) }

    // What the icons are drawn from, in points around the icon's middle. A piece is a path or a
    // circle, stroked or filled in the icon's colour or, for a detail cut out of a solid shape, in the page's.
    enum Paint { case stroke, fill, cutStroke, cutFill }

    struct Piece {
        let path: Path
        let paint: Paint
        var width = 0.0

        init(_ d: String, _ paint: Paint, _ width: Double = 0) {
            path = RoamingFabTabBarMotion.path(d)
            self.paint = paint
            self.width = width
        }

        init(circle x: Double, _ y: Double, _ r: Double, _ paint: Paint, _ width: Double = 0) {
            path = Path(ellipseIn: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2))
            self.paint = paint
            self.width = width
        }
    }

    struct Icon {
        let pivot: CGPoint
        let off: [Piece]
        let on: [Piece]
    }

    /// A path from M, L, Q, C and Z with absolute numbers, the form the three versions of the block share.
    static func path(_ d: String) -> Path {
        var path = Path()
        var command: Character? = nil
        var numbers = ""
        func flush() {
            let v = numbers.split(separator: " ").compactMap { Double($0) }
            switch command {
            case "M": path.move(to: CGPoint(x: v[0], y: v[1]))
            case "L": path.addLine(to: CGPoint(x: v[0], y: v[1]))
            case "Q": path.addQuadCurve(to: CGPoint(x: v[2], y: v[3]), control: CGPoint(x: v[0], y: v[1]))
            case "C": path.addCurve(to: CGPoint(x: v[4], y: v[5]), control1: CGPoint(x: v[0], y: v[1]), control2: CGPoint(x: v[2], y: v[3]))
            case "Z": path.closeSubpath()
            default: break
            }
        }
        for character in d {
            if character.isLetter {
                flush()
                command = character
                numbers = ""
            } else {
                numbers.append(character)
            }
        }
        flush()
        return path
    }

    private static let house = "M-8.5 -1.6L0 -9L8.5 -1.6L8.5 6.6Q8.5 9 6.1 9L-6.1 9Q-8.5 9 -8.5 6.6Z"
    private static let door = "M-2.3 9L-2.3 4.2Q-2.3 2.2 0 2.2Q2.3 2.2 2.3 4.2L2.3 9Z"
    private static let handle = "M3.2 3.2L8.6 8.6"
    private static let glint = "M-4.9 -1.9Q-4.9 -4.9 -1.9 -4.9"
    private static let shoulders = "M-9.5 8.5C-9.5 3.2 -6 1.4 -2 1.4C0 1.4 1.8 1.9 3.2 2.8"
    private static let add = "M6.5 2.3L6.5 8.7M3.3 5.5L9.7 5.5"
    private static let smile = "M-4.4 -0.4Q-1.7 2.6 1 -0.4"
    private static let shoulder = "M-8.8 9.8Q-8.4 7 -6.2 5.4"
    private static let badgePlus = "M6.8 4L6.8 8.4M4.6 6.2L9 6.2"
    private static let bubbleShape = "M-2.5 -8.5L2.5 -8.5Q9.5 -8.5 9.5 -1.5L9.5 -0.5Q9.5 6.5 2.5 6.5L-3.5 6.5L-8.6 9.2L-7.6 4.6Q-9.5 2.6 -9.5 -0.5L-9.5 -1.5Q-9.5 -8.5 -2.5 -8.5Z"
    private static let bars = "M-3.4 -3L-3.4 1M0 -4.2L0 2.2M3.4 -3L3.4 1"
    static let bubble = path(bubbleShape)
    static let plus = path("M-9 0L9 0M0 -9L0 9")
    static let pencil = path("M-2.8 1.8L3.4 -4.4")

    /// The four tabs: a house, a magnifier that swings on the end of its handle, a person who nods, a bubble.
    static let icons = [
        Icon(
            pivot: CGPoint(x: 0, y: 0),
            off: [Piece(house, .stroke, 2)],
            on: [Piece(house, .fill), Piece(house, .stroke, 2), Piece(door, .cutFill)]
        ),
        Icon(
            pivot: CGPoint(x: 8.6, y: 8.6),
            off: [Piece(circle: -1.6, -1.6, 6.4, .stroke, 2), Piece(handle, .stroke, 2)],
            on: [Piece(circle: -1.6, -1.6, 6.4, .stroke, 3.4), Piece(handle, .stroke, 3.4), Piece(glint, .stroke, 1.6)]
        ),
        Icon(
            pivot: CGPoint(x: 0, y: 10),
            off: [Piece(circle: -2, -5, 3.8, .stroke, 2), Piece(shoulders, .stroke, 2), Piece(add, .stroke, 2)],
            on: [
                Piece(circle: -1.5, -2.5, 7.2, .stroke, 2.2),
                Piece(circle: -4, -4, 1.1, .fill),
                Piece(circle: 0.6, -4, 1.1, .fill),
                Piece(smile, .stroke, 1.6),
                Piece(shoulder, .stroke, 2.2),
                Piece(circle: 6.8, 6.2, 6.2, .cutFill),
                Piece(circle: 6.8, 6.2, 4.6, .fill),
                Piece(badgePlus, .cutStroke, 1.6),
            ]
        ),
        Icon(
            pivot: CGPoint(x: 0, y: 0),
            off: [Piece(bubbleShape, .stroke, 2), Piece(bars, .stroke, 1.8)],
            on: [Piece(bubbleShape, .fill), Piece(bubbleShape, .stroke, 2), Piece(bars, .cutStroke, 1.8)]
        ),
    ]
}
