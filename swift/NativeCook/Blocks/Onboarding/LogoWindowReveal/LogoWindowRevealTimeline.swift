import SwiftUI

/// The show as a function of its time `t`, in seconds: the mark, the flight through its window
/// into the photo, the photo's fall into a list of cards, the list's zoom out into three columns,
/// and the last row left hanging over the name. Nothing is stored per frame.
enum LogoWindowRevealTimeline {
    /// autoPlay runs the show every `loop` seconds. By hand it stops at `end`, with everything in place.
    static let loop = 11.0
    static let swellAt = 0.6
    static let flyAt = 1.0
    static let heroAt = 1.7
    static let shrinkAt = 2.7
    static let listAt = 3.4
    static let zoomAt = 6.4
    static let hangAt = 8.2
    static let end = 9.6
    static let closeAt = 10.3
    /// After a touch autoPlay waits this long before it closes the show and starts over.
    static let userHold = 3.0
    /// A list that was scrolled by hand goes on by itself this long after the finger left.
    static let scrollHold = 2.0

    // The mark: a disc with a rounded window cut out, a little right of its centre.
    static let markSize = 68.0
    static let windowBox = (width: 26.0, height: 30.0, radius: 9.0, dx: 2.0)
    // It swells with a grey halo behind it, then grows until the window has swallowed the screen.
    static let swell = 1.9
    static let flyOver = 1.25
    static let flyPower = 1.6
    static let halo = 1.2
    static let haloOpacity = 0.5
    // The photo shows through the window from the first moment of the swell.
    static let photoFaint = 0.2
    static let photoFullAt = 0.6
    /// The hero photo is asked for this tall, so that it covers the screen and the card with its whole width.
    static let heroAspect = 0.4

    // The hero's two lines rise into place one after the other, and leave when the photo shrinks.
    static let textIn = 0.4
    static let textGap = 0.08
    static let textRise = 20.0
    static let textOut = 0.25

    // The list: square cards one under the other. The photo lands as the second one.
    static let card = 330.0
    static let gap = 18.0
    static let pitch = card + gap
    static let cardRadius = 22.0
    static let heroRow = 1
    static let land = (stiffness: 170.0, damping: 20.0)
    // Its neighbours come in from above and below, from this far.
    static let slide = 420.0
    static let cardTextIn = 0.3
    // The list scrolls by itself at this speed, in points a second, after a short run-up.
    static let speed = 230.0
    static let runUp = 0.3
    // Zoom out: the column shrinks to this scale about the middle of the screen while the scroll
    // slows to a stop, and a column fades in on each side, half a card higher.
    static let zoom = 0.4
    static let zoomFor = hangAt - zoomAt
    static let sideIn = 0.6
    /// Rows in a column; the side columns have no first row.
    static let rows = 8
    // The end: the grid runs up until its last row hangs at the top, the middle card larger.
    static let hangFor = 1.0
    static let hung = (size: 154.0, y: 140.0)
    static let hungSide = (size: 105.0, x: 40.5)
    // The name's letters come in one after the other, then the line under it.
    static let nameAfter = 0.5
    static let letterGap = 0.04
    static let letterIn = 0.3
    static let letterRise = 10.0
    static let taglineAfter = 0.9
    static let taglineIn = 0.4
    // Closing: the hanging cards leave upwards, the name shrinks into the middle and the mark takes its place.
    static let leave = 260.0
    static let leaveFor = 0.4
    static let nameOutFor = 0.3
    static let nameShrink = 0.6
    static let markAfter = 0.2
    static let markIn = 0.35
    // Reduced motion: the mark and the end fade into each other.
    static let stillIn = 1.0
    static let stillFade = 0.4

    static func clamp01(_ t: Double) -> Double { min(1, max(0, t)) }

    static func smooth(_ t: Double, _ from: Double, _ to: Double) -> Double {
        let u = clamp01((t - from) / (to - from))
        return u * u * (3 - 2 * u)
    }

    static func easeOut(_ t: Double) -> Double { 1 - (1 - t) * (1 - t) * (1 - t) }

    static func easeInOut(_ t: Double) -> Double { t < 0.5 ? 4 * t * t * t : 1 - pow(-2 * t + 2, 3) / 2 }

    /// How far a spring of mass 1 has come from 0 to 1 after `t` seconds; it overshoots a little.
    static func spring(_ t: Double, _ spring: (stiffness: Double, damping: Double)) -> Double {
        guard t > 0 else { return 0 }
        let w = spring.stiffness.squareRoot()
        let z = spring.damping / (2 * w)
        let wd = w * (1 - z * z).squareRoot()
        return 1 - exp(-z * w * t) * (cos(wd * t) + (z * w / wd) * sin(wd * t))
    }

    /// Reduced motion shows two pictures, the mark and the end: how much of the end at `t`.
    static func stillEnd(_ t: Double) -> Double {
        smooth(t, stillIn, stillIn + stillFade) * (1 - smooth(t, closeAt, closeAt + stillFade))
    }

    /// The mark: its scale, and how much of it and of its halo shows.
    static func mark(_ t: Double, _ size: CGSize) -> (scale: Double, opacity: Double, halo: Double) {
        if t >= closeAt {
            let u = clamp01((t - closeAt - markAfter) / markIn)
            return (nameShrink + (1 - nameShrink) * easeOut(u), u, 0)
        }
        if t >= heroAt { return (1, 0, 0) }
        if t < swellAt { return (1, 1, 0) }
        if t < flyAt {
            let u = (t - swellAt) / (flyAt - swellAt)
            return (1 + (swell - 1) * easeInOut(u), 1, haloOpacity * smooth(u, 0, 0.5))
        }
        // Each moment multiplies the size by the same factor, so the flight feels even, after a slow start.
        let u = (t - flyAt) / (heroAt - flyAt)
        let full = max(size.width / windowBox.width, size.height / windowBox.height) * flyOver
        return (swell * pow(full / swell, pow(u, flyPower)), 1, haloOpacity * (1 - smooth(u, 0, 0.3)))
    }

    /// The window of a mark of that scale.
    static func window(of scale: Double, _ size: CGSize) -> (rect: CGRect, radius: Double) {
        let w = windowBox.width * scale
        let h = windowBox.height * scale
        return (CGRect(x: size.width / 2 + windowBox.dx * scale - w / 2, y: size.height / 2 - h / 2, width: w, height: h), windowBox.radius * scale)
    }

    /// The hero photo: the box it is seen through, the scale of the photo behind it, and how much shows.
    static func hero(_ t: Double, _ size: CGSize) -> (rect: CGRect, radius: Double, k: Double, opacity: Double) {
        if t < heroAt {
            let box = window(of: mark(t, size).scale, size)
            let opacity = t < swellAt ? 0 : t < flyAt
                ? photoFaint * (t - swellAt) / (flyAt - swellAt)
                : photoFaint + (1 - photoFaint) * clamp01((t - flyAt) / (heroAt - flyAt) / photoFullAt)
            return (box.rect, box.radius, 1, opacity)
        }
        let p = t >= listAt ? 1 : t < shrinkAt ? 0 : spring(t - shrinkAt, land)
        let w = size.width + (card - size.width) * p
        let h = size.height + (card - size.height) * p
        return (CGRect(x: (size.width - w) / 2, y: (size.height - h) / 2, width: w, height: h), cardRadius * clamp01(p), w / size.width, t >= listAt ? 0 : 1)
    }

    /// One of the hero's two lines: how much shows, and how far under its place it is.
    static func heroLine(_ t: Double, _ line: Int) -> (opacity: Double, rise: Double) {
        let u = clamp01((t - heroAt - Double(line) * textGap) / textIn)
        return (u * (1 - smooth(t, shrinkAt, shrinkAt + textOut)), textRise * (1 - easeOut(u)))
    }

    /// How far the list has scrolled. `extra` is what a finger added; the end ignores it.
    static func scroll(_ t: Double, _ height: Double, _ extra: Double) -> Double {
        let start = pitch * Double(heroRow) + card / 2 - height / 2
        let run = max(0, min(t, zoomAt) - listAt)
        let slow = clamp01((t - zoomAt) / zoomFor) * zoomFor
        let auto = start
            + speed * (run < runUp ? run * run / (2 * runUp) : run - runUp / 2)
            + speed * (slow - slow * slow / (2 * zoomFor))
        // Kept between the first card and the last in the middle of the screen.
        let free = min(max(auto + extra, start - pitch), start + pitch * Double(rows - 1 - heroRow))
        let end = pitch * Double(rows - 1) + card / 2 - height / 2 + (height / 2 - hung.y) / zoom
        return free + (end - free) * easeInOut(clamp01((t - hangAt) / hangFor))
    }

    /// Where a card is: `column` -1, 0 or 1, `row` from the top.
    static func place(column: Int, row: Int, _ t: Double, _ size: CGSize, _ extra: Double) -> (x: Double, y: Double, scale: Double, opacity: Double, text: Double) {
        if t < shrinkAt || (column != 0 && t < zoomAt) || (column == 0 && row == heroRow && t < listAt) {
            return (size.width / 2, -card, 1, 0, 0)
        }
        let c = Double(column)
        let z = 1 + (zoom - 1) * easeInOut(clamp01((t - zoomAt) / zoomFor))
        let inList = Double(row) * pitch + card / 2 - (column == 0 ? 0 : pitch / 2)
        var x = size.width / 2 + c * pitch * z
        var y = size.height / 2 + (inList - scroll(t, size.height, extra) - size.height / 2) * z
        var scale = z
        if t < listAt { y += (row < heroRow ? -1 : 1) * slide * (1 - spring(t - shrinkAt, land)) }
        if row == rows - 1, t > hangAt {
            let p = easeInOut(clamp01((t - hangAt) / hangFor))
            scale = z + ((column == 0 ? hung.size : hungSide.size) / card - z) * p
            x += c * (size.width / 2 - hungSide.x - pitch * z) * p
        }
        if t > closeAt {
            let u = clamp01((t - closeAt) / leaveFor)
            y -= leave * u * u
        }
        return (
            x, y, scale,
            column == 0 ? 1 : smooth(t, zoomAt, zoomAt + sideIn),
            // The landed photo gets its card's lines a moment later.
            column == 0 && row == heroRow ? smooth(t, listAt, listAt + cardTextIn) : 1
        )
    }

    /// A letter of the name: how much shows, and how far under its place it is.
    static func letter(_ t: Double, _ index: Int) -> (opacity: Double, rise: Double) {
        let u = clamp01((t - hangAt - nameAfter - Double(index) * letterGap) / letterIn)
        return (u, letterRise * (1 - easeOut(u)))
    }

    /// The name as a whole while it closes: how much shows, and its scale.
    static func nameOut(_ t: Double) -> (opacity: Double, scale: Double) {
        let u = smooth(t, closeAt, closeAt + nameOutFor)
        return (1 - u, 1 + (nameShrink - 1) * u)
    }

    static func tagline(_ t: Double) -> Double {
        smooth(t, hangAt + taglineAfter, hangAt + taglineAfter + taglineIn) * (1 - smooth(t, closeAt, closeAt + nameOutFor))
    }

    /// The mark of a given scale as one path: the disc, and the window as a hole in it (even-odd).
    static func markPath(_ scale: Double, _ size: CGSize) -> Path {
        let r = markSize / 2 * scale
        let hole = window(of: scale, size)
        var path = Path(ellipseIn: CGRect(x: size.width / 2 - r, y: size.height / 2 - r, width: r * 2, height: r * 2))
        path.addRoundedRect(in: hole.rect, cornerSize: CGSize(width: hole.radius, height: hole.radius), style: .circular)
        return path
    }
}
