import SwiftUI

enum LiveTimerTabMotion {
    /// Seconds of one autoPlay loop, and when in it the timer starts, the page scrolls up and back, and the timer stops.
    static let loop = 10.0
    static let startAt = 0.8
    static let scrollAt = 2.2
    static let backAt = 6.2
    static let stopAt = 8.6
    /// Seconds after a touch before the loop starts again.
    static let userHold = 3.0

    /// How far the loop scrolls, in points, and for how long each way.
    static let autoScroll = 420.0
    static let scrollUp = 1.2
    static let scrollBack = 1.0
    /// Seconds the page takes to return to the top when the loop takes over again.
    static let scrollHome = 0.5

    /// Seconds from the black button to the light box, and back.
    static let start = 0.35
    static let stop = 0.3
    /// Seconds the stopwatch takes to swell into the pill, and the pill to collapse.
    static let form = 0.4
    static let collapse = 0.35
    /// A changing digit: seconds, and the points the old one falls and the new one drops in from.
    static let roll = 0.3
    static let rollShift = 8.0
    /// Seconds the pill's time takes to fade when the pill collapses.
    static let timeOut = 0.15
    /// Seconds of the cross-fade between the pages.
    static let tabFade = 0.25

    /// The curves: a strong ease-out, the swell that passes its end, and the scroll.
    static let easeOut = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.22, y: 1), endControlPoint: UnitPoint(x: 0.36, y: 1))
    static let swell = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.34, y: 1.3), endControlPoint: UnitPoint(x: 0.64, y: 1))
    static let glide = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.45, y: 0), endControlPoint: UnitPoint(x: 0.55, y: 1))

    static let margin = 20.0
    static let hero = 240.0
    static let titleGap = 24.0
    static let titleLine = 34.0
    static let subtitleGap = 6.0
    static let subtitleLine = 20.0
    static let controlGap = 24.0
    static let control = 68.0
    static let controlRadius = 14.0
    static let boxWidth = 160.0
    /// The middle of the timer control, from the top of the page.
    static let controlCentre = hero + titleGap + titleLine + subtitleGap + subtitleLine + controlGap + control / 2
    static let bodyGap = 28.0
    static let bodyLine = 28.0

    /// The tab bar above the bottom inset, and the least that inset counts for.
    static let tabBar = 60.0
    static let tabBottom = 24.0
    /// The page's last lines fade out over this height above the bar.
    static let fade = 60.0
    /// From the screen's edges to the middle of the outer tabs.
    static let tabInset = 48.0
    static let tabIcon = 26.0
    static let tabStroke = 1.8
    static let pillWidth = 86.0
    static let pillHeight = 46.0
    /// The stopwatch's ring, and how far its middle sits under the middle of the icon.
    static let dial = 19.0
    static let dialDrop = 1.5
    /// The stopwatch's hand, from the middle of the ring.
    static let hand = 4.6
    /// The dash above the ring: its width, and its middle above the ring's top edge.
    static let tick = 6.0
    static let tickGap = 3.0

    /// The control has left the page when its middle is this far past an edge of what shows, and is back this far inside it.
    static let hysteresis = 8.0
    /// What the timer counts down from, in seconds.
    static let timer = 900
    /// The time that rises from the pill to the box: its size at the pill, and the part of the way it fades in over.
    static let ghostScale = 13.0 / 19.0
    static let ghostIn = 0.15

    static func clamp01(_ t: Double) -> Double { min(1, max(0, t)) }

    static func mix(_ a: Double, _ b: Double, _ t: Double) -> Double { a + (b - a) * t }

    /// "14:59" for 899 seconds.
    static func clock(_ seconds: Int) -> String {
        let whole = max(0, seconds)
        return String(format: "%02d:%02d", whole / 60, whole % 60)
    }
}
