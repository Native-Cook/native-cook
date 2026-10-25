import CoreGraphics
import UIKit

/*
 The dice table's baked art (a port of the baked half of diceArt.ts): the dark games-room
 background, the walnut tray with its green felt, and one sprite per player's avatar at rest.
 Baked once, off the main thread.
 */

struct DiceRollPickerPlayer {
    let name: String
    let initials: String
    let range: String
    let sums: [Int]
    let colors: [UInt32]
}

struct DiceRollPickerGeo: Equatable {
    var w: CGFloat
    var h: CGFloat
    var cx: CGFloat
    var cy: CGFloat
    var top: CGFloat
    var avatarsTop: CGFloat
    var resultBase: CGFloat
    var ctaTop: CGFloat

    init(w: CGFloat, h: CGFloat, top: CGFloat, bottom: CGFloat) {
        let ctaTop = h - max(bottom, 16) - 8 - 56
        let avatarsTop = ctaTop - 20 - 92
        let resultBase = avatarsTop - 22
        let headerBottom = top + 112
        // The tray's centre sits midway between the header and the result line, nudged down a
        // little since the far rim draws higher than the near one.
        self.w = w
        self.h = h
        self.top = top
        self.cx = w / 2
        self.cy = (headerBottom + resultBase - 30) / 2 + 26
        self.avatarsTop = avatarsTop
        self.resultBase = resultBase
        self.ctaTop = ctaTop
    }

    var cell: CGFloat { (w - 32) / CGFloat(DiceRollPickerArt.players.count) }
    var avatarR: CGFloat { min(24, cell * 0.4) }
    var avatarY: CGFloat { avatarsTop + avatarR + 14 }

    func project(_ p: DiceRollPickerV3) -> CGPoint {
        let s = DiceRollPickerMath.project(Double(cx), Double(cy), p)
        return CGPoint(x: s.x, y: s.y)
    }
}

enum DiceRollPickerArt {
    private typealias P = DiceRollPickerPaint
    private typealias D = DiceRollPickerMath
    typealias V3 = DiceRollPickerV3

    static let players: [DiceRollPickerPlayer] = [
        DiceRollPickerPlayer(name: "Tomás", initials: "T", range: "2–3", sums: [2, 3], colors: [0x7EC4CF, 0x3F8EA0, 0x245766]),
        DiceRollPickerPlayer(name: "June", initials: "J", range: "4–5", sums: [4, 5], colors: [0xF3B6A0, 0xD77A62, 0x8F4433]),
        DiceRollPickerPlayer(name: "Kofi", initials: "K", range: "6", sums: [6], colors: [0xC9D98A, 0x8FA64A, 0x56682A]),
        DiceRollPickerPlayer(name: "Mira", initials: "M", range: "7", sums: [7], colors: [0xD3B8F0, 0x9A7CC8, 0x5E4686]),
        DiceRollPickerPlayer(name: "Elias", initials: "E", range: "8–9", sums: [8, 9], colors: [0xF2D58A, 0xD0A23F, 0x8A6620]),
        DiceRollPickerPlayer(name: "Priya", initials: "P", range: "10–12", sums: [10, 11, 12], colors: [0xF5A9C4, 0xD9668F, 0x8E3258]),
    ]

    static func playerFor(_ sum: Int) -> Int {
        players.firstIndex { $0.sums.contains(sum) } ?? 0
    }

    /// The padding around an avatar in its sprite.
    static let spritePad: CGFloat = 16
    static let initials = P.sans(18, .bold)

    private static func quad(_ g: DiceRollPickerGeo, _ pts: [V3]) -> CGPath {
        let p = CGMutablePath()
        for (i, v) in pts.enumerated() {
            let s = g.project(v)
            if i == 0 { p.move(to: s) } else { p.addLine(to: s) }
        }
        p.closeSubpath()
        return p
    }

    static func room(_ g: DiceRollPickerGeo, scale: CGFloat) -> UIImage {
        P.bake(CGSize(width: g.w, height: g.h), scale: scale) { c in
            let all = P.rect(0, 0, g.w, g.h)
            P.linear(c, all, from: .zero, to: CGPoint(x: 0, y: g.h), [P.cg(0x231C17), P.cg(0x1A1512), P.cg(0x110E0C)], [0, 0.5, 1])
            P.radial(c, all, center: CGPoint(x: g.cx, y: g.cy - 40), radius: g.w * 1.1,
                     [P.cg(0x6A5236, 0.35), P.cg(0x3B2C1F, 0.12), P.cg(0x000000, 0)], [0, 0.55, 1])
            let x0 = D.tableX0, x1 = D.tableX1, y0 = D.tableY0, y1 = D.tableY1, rim = D.tableRim, wall = D.tableWall
            // Tray: outer top surface at the wall's height, then the inner walls, then the felt.
            let o = [V3(x0 - rim, y0 - rim, wall), V3(x1 + rim, y0 - rim, wall), V3(x1 + rim, y1 + rim, wall), V3(x0 - rim, y1 + rim, wall)]
            let near = g.project(V3(0, y0 - rim, 0))
            let front = quad(g, [V3(x0 - rim, y0 - rim, 0), V3(x1 + rim, y0 - rim, 0), V3(x1 + rim, y0 - rim, wall), V3(x0 - rim, y0 - rim, wall)])
            P.fill(c, front, 0x000000, 0.5, blur: 22)
            P.fill(c, CGPath(ellipseIn: CGRect(x: g.cx - g.w * 0.48, y: near.y - 6, width: g.w * 0.96, height: 30), transform: nil), 0x000000, 0.55, blur: 14)
            // Front face of the tray (its outer side, toward the player).
            P.linear(c, front, from: CGPoint(x: 0, y: near.y - 18), to: CGPoint(x: 0, y: near.y),
                     [P.cg(0x6B3F22), P.cg(0x4A2A15), P.cg(0x2E1A0C)], [0, 0.5, 1])
            let topP = quad(g, o)
            let ot = g.project(o[3])
            let ob = g.project(o[0])
            P.linear(c, topP, from: CGPoint(x: 0, y: ot.y), to: CGPoint(x: 0, y: ob.y),
                     [P.cg(0x8A5530), P.cg(0x6E3F20), P.cg(0x8F5A33), P.cg(0x5A3218)], [0, 0.35, 0.7, 1])
            // Wood grain along the rails.
            c.saveGState()
            c.addPath(topP)
            c.clip()
            P.brush(c, CGRect(x: 0, y: ot.y - 20, width: g.w, height: ob.y - ot.y + 40), alpha: 0.16)
            c.restoreGState()
            // Inner walls (far and sides), shaded, then the felt.
            let farTop = g.project(V3(0, y1, wall)), farBottom = g.project(V3(0, y1, 0))
            P.linear(c, quad(g, [V3(x0, y1, 0), V3(x1, y1, 0), V3(x1, y1, wall), V3(x0, y1, wall)]),
                     from: CGPoint(x: 0, y: farTop.y), to: CGPoint(x: 0, y: farBottom.y), [P.cg(0x5A3519), P.cg(0x3A210F)], nil)
            P.fill(c, quad(g, [V3(x0, y0, 0), V3(x0, y1, 0), V3(x0, y1, wall), V3(x0, y0, wall)]), 0x4D2D15)
            P.fill(c, quad(g, [V3(x1, y0, 0), V3(x1, y1, 0), V3(x1, y1, wall), V3(x1, y0, wall)]), 0x2F1B0C)
            let felt = quad(g, [V3(x0, y0, 0), V3(x1, y0, 0), V3(x1, y1, 0), V3(x0, y1, 0)])
            let fc = g.project(V3(0, 20, 0))
            P.radial(c, felt, center: fc, radius: g.w * 0.62, [P.cg(0x2F7A59), P.cg(0x22634A), P.cg(0x174433)], [0, 0.55, 1])
            c.saveGState()
            c.addPath(felt)
            c.clip()
            P.grain(c, CGRect(x: 0, y: 0, width: g.w, height: g.h), alpha: 0.3, seed: 21)
            // Shade along the walls: the felt meets the rail in a soft shadow.
            P.stroke(c, felt, 0x0A1F16, width: 18, 0.5, blur: 9)
            c.restoreGState()
            // Rail highlights: the inner and outer lips catch the lamp.
            func lip(_ a: V3, _ b: V3, _ col: UInt32, _ alpha: CGFloat) {
                let p = g.project(a), q = g.project(b)
                P.stroke(c, P.line(p.x, p.y, q.x, q.y), col, width: 1.2, alpha)
            }
            lip(V3(x0, y1, wall), V3(x1, y1, wall), 0xF2C08A, 0.5)
            lip(V3(x0 - rim, y0 - rim, wall), V3(x1 + rim, y0 - rim, wall), 0xF2C08A, 0.45)
            lip(V3(x0, y0, wall), V3(x1, y0, wall), 0x1A0D05, 0.6)
            P.grain(c, CGRect(x: 0, y: 0, width: g.w, height: g.h), alpha: 0.06, seed: 4)
            P.radial(c, all, center: CGPoint(x: g.cx, y: g.h * 0.45), radius: g.h * 0.72,
                     [P.cg(0x000000, 0), P.cg(0x000000, 0.08), P.cg(0x000000, 0.5)], [0, 0.65, 1])
            // The players' names and their sums under the avatars (the chosen name brightens live).
            for (i, p) in players.enumerated() {
                let x = 16 + g.cell * (CGFloat(i) + 0.5)
                let base = g.avatarY + g.avatarR
                P.text(p.name, x: x - P.width(p.name, name) / 2, y: base + 20, name, 0xF3EADB, 0.6)
                P.text(p.range, x: x - P.width(p.range, range) / 2, y: base + 36, range, 0xF3EADB, 0.38)
            }
        }
    }

    static let name = P.sans(13, .semibold)
    static let range = P.sans(11, .medium)

    /// One player's avatar at rest (with its shadow), centred in a square of 2r + 2·spritePad.
    static func avatar(_ i: Int, r: CGFloat, scale: CGFloat) -> UIImage {
        let p = players[i]
        let side = r * 2 + spritePad * 2
        return P.bake(CGSize(width: side, height: side), scale: scale) { c in
            c.translateBy(x: side / 2, y: side / 2)
            P.fill(c, P.circle(0, 3, r), 0x000000, 0.35, blur: 4)
            P.radial(c, P.circle(0, 0, r), center: CGPoint(x: -r * 0.3, y: -r * 0.4), radius: r * 1.4,
                     [P.cg(p.colors[0]), P.cg(p.colors[1]), P.cg(p.colors[2])], [0, 0.55, 1])
            P.stroke(c, P.circle(0, 0, r - 0.5), 0xFFFFFF, width: 1, 0.25)
            let iw = P.width(p.initials, initials)
            P.text(p.initials, x: -iw / 2, y: 6.5, initials, 0xFFFAF2, 0.95)
        }
    }
}
