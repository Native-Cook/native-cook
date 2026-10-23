import SwiftUI

/// Where the surface, its icons and its mark are for a given state. The same numbers and curves
/// as the React Native and Compose versions.
enum RailToBarMorphGeometry {
    /// The screen the surface lives on: its size, the top safe-area inset and the bar's whole height.
    struct Frame {
        var width: Double
        var height: Double
        var top: Double
        var bar: Double
    }

    static let list = 290.0
    static let rail = 64.0
    static let listRadius = 28.0
    static let railRadius = 24.0
    static let barRadius = 28.0
    /// The bar's height above the bottom safe-area inset.
    static let bar = 62.0

    static let mark = 28.0
    /// The mark's centre and the first row's centre, below the top safe-area inset.
    static let markY = 24.0
    static let rowY = 96.0
    static let pitch = 56.0
    static let icon = 24.0
    static let barIcon = 26.0
    /// The centre of the icon column in the list and in the rail.
    static let listX = 42.0
    static let railX = 32.0
    static let labelX = 72.0

    /// The rail becomes the bar in `dockIn` seconds and the bar becomes the list in `dockOut`;
    /// each of the bar's icons sets off `stagger` seconds after the one below it.
    static let dockIn = 0.7
    static let dockOut = 0.8
    static let stagger = 0.025
    /// The icons that do not go to the bar are gone after this part of the way.
    static let spareOut = 0.3

    static func clamp(_ t: Double) -> Double { min(1, max(0, t)) }

    static func mix(_ from: Double, _ to: Double, _ t: Double) -> Double { from + (to - from) * t }

    static func easeInOut(_ t: Double) -> Double {
        let c = clamp(t)
        return c < 0.5 ? 4 * c * c * c : 1 - pow(-2 * c + 2, 3) / 2
    }

    static func easeOut(_ t: Double) -> Double { 1 - pow(1 - clamp(t), 3) }

    /// How far the icon that sets off in place `order` is along its flight to the bar, as a
    /// quarter turn in radians. `docked` icons set off one after another within the clock `dock`.
    static func turn(_ order: Int, docked: Int, dock: Double) -> Double {
        let lag = stagger / dockIn
        return easeInOut((dock - Double(order) * lag) / (1 - Double(docked - 1) * lag)) * .pi / 2
    }

    /// The white surface. `wide` is 1 for the list and 0 for the rail; `dock` is the clock of the
    /// rail becoming the bar. The left and bottom edges stay where they are. The right edge goes
    /// out with the first icon to leave and the top edge comes down with the last one, so that
    /// every icon stays on the surface all the way; the rounding moves from the right corners to
    /// the top corners.
    static func panel(wide: Double, dock: Double, docked: Int, frame: Frame) -> (rect: CGRect, radii: RectangleCornerRadii) {
        let e = easeInOut(dock)
        let strip = mix(rail, list, wide)
        let corner = mix(railRadius, listRadius, clamp(wide))
        let top = (1 - cos(turn(docked - 1, docked: docked, dock: dock))) * (frame.height - frame.bar)
        let width = mix(strip, frame.width, sin(turn(0, docked: docked, dock: dock)))
        return (
            CGRect(x: 0, y: top, width: max(0, width), height: frame.height - top),
            RectangleCornerRadii(topLeading: barRadius * e, bottomLeading: 0, bottomTrailing: corner * (1 - e), topTrailing: mix(corner, barRadius, e))
        )
    }

    struct IconPlace {
        var x: Double
        var y: Double
        var scale: Double
        var alpha: Double
        /// How far along its flight to the bar the icon is, 0 to 1.
        var way: Double
    }

    /// Where icon `index` is. In the list and the rail the icons are a column; the first `docked`
    /// of them go to the bar, the lowest first, each along a quarter of an ellipse: out to the
    /// right, then down, so that mid-way they lie on a diagonal curve in their order. The others
    /// fade where they are.
    static func icon(_ index: Int, docked: Int, wide: Double, dock: Double, frame: Frame) -> IconPlace {
        let x = mix(railX, listX, wide)
        let y = frame.top + rowY + Double(index) * pitch
        guard index < docked else { return IconPlace(x: x, y: y, scale: 1, alpha: 1 - clamp(dock / spareOut), way: 0) }
        let turn = turn(docked - 1 - index, docked: docked, dock: dock)
        let way = turn / (.pi / 2)
        let toX = frame.width * (Double(index) + 0.5) / Double(docked)
        let toY = frame.height - frame.bar + bar / 2
        return IconPlace(x: x + (toX - x) * sin(turn), y: y + (toY - y) * (1 - cos(turn)), scale: mix(1, barIcon / icon, way), alpha: 1, way: way)
    }

    /// The mark rides above the icon column and flies to the top centre as the bar forms.
    static func markPoint(wide: Double, dock: Double, frame: Frame) -> CGPoint {
        CGPoint(x: mix(mix(railX, listX, wide), frame.width / 2, easeInOut(dock)), y: frame.top + markY)
    }
}

/// A number on its way to a target, read against the timeline's date: a clock that runs evenly
/// for a duration, or a spring.
struct RailToBarMorphTween {
    private var from: Double
    private var to: Double
    private var start = Date.distantPast
    private var duration = 0.0
    private var spring: Spring?

    init(_ value: Double) {
        from = value
        to = value
    }

    func value(at date: Date) -> Double {
        let t = date.timeIntervalSince(start)
        if t <= 0 { return from }
        if let spring { return from + (to - from) * spring.value(target: 1.0, time: t) }
        return duration > 0 ? from + (to - from) * min(1, t / duration) : to
    }

    /// Sets off from where the number is at `date`, after `delay` seconds.
    mutating func move(to target: Double, at date: Date, duration: Double = 0, spring: Spring? = nil, delay: Double = 0) {
        from = value(at: date)
        to = target
        start = date + delay
        self.duration = duration
        self.spring = spring
    }
}

/// The surface's outline on the screen: a rectangle with its own radius at each corner.
struct RailToBarMorphPanel: Shape {
    var frame: CGRect
    var radii: RectangleCornerRadii

    func path(in rect: CGRect) -> Path {
        UnevenRoundedRectangle(cornerRadii: radii, style: .continuous).path(in: frame)
    }
}

/// A disc with its top right quarter cut out.
struct RailToBarMorphMark: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        let center = CGPoint(x: rect.midX, y: rect.midY)
        path.move(to: center)
        path.addArc(center: center, radius: rect.width / 2, startAngle: .degrees(0), endAngle: .degrees(270), clockwise: false)
        path.closeSubpath()
        return path
    }
}
