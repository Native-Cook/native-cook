import CoreGraphics
import UIKit

/*
 The spirit level's baked art (a port of spiritArt.ts): the warm paper background, the pitch card
 with the house's walls, the level itself (anodised yellow body, machined end caps, etched ruler,
 the vial window with its lime liquid and centre marks) and the level's soft shadow. Baked once,
 off the main thread.
 */

struct SpiritLevelSliderGeo: Equatable {
    var w: CGFloat
    var h: CGFloat
    var card: CGRect
    /// The house: centre x, ground y, half width, wall height.
    var houseX: CGFloat
    var houseY: CGFloat
    var houseHW: CGFloat
    var houseWH: CGFloat
    var cx: CGFloat
    var cy: CGFloat
    /// Level length and body thickness, the vial's inner half length and the bubble's size.
    var len: CGFloat
    var thick: CGFloat
    var vialHalf: CGFloat
    var bubbleLen: CGFloat
    var bubbleH: CGFloat
    var travel: CGFloat
    var chipsTop: CGFloat
    var ctaTop: CGFloat
    var top: CGFloat

    static let pad: CGFloat = 24
    /// The padding around the baked shadow image.
    static let shadowPad: CGFloat = 44

    init(w: CGFloat, h: CGFloat, top: CGFloat, bottom: CGFloat) {
        let cardY = top + 150
        let cardH = min(max(h * 0.2, 140), 176)
        let card = CGRect(x: 16, y: cardY, width: w - 32, height: cardH)
        let ctaTop = h - max(bottom, 16) - 8 - 56
        let chipsTop = ctaTop - 16 - 40
        let heroTop = cardY + cardH + 12
        let heroBottom = chipsTop - 12
        let heroH = heroBottom - heroTop
        let sin44 = CGFloat(sin(44 * Double.pi / 180))
        let len = max(200, min(w - 56, 340, (heroH - 56) / sin44))
        let thick = len * 0.135
        let vialHalf = (len * 0.44 - 12) / 2
        let bubbleLen = thick * 0.74
        let hw = min(card.width * 0.25, 86)
        self.w = w
        self.h = h
        self.top = top
        self.card = card
        self.houseX = card.minX + card.width * 0.7
        self.houseY = card.minY + card.height - 22
        self.houseHW = hw
        self.houseWH = card.height * 0.24
        self.cx = w / 2
        self.cy = (heroTop + heroBottom) / 2
        self.len = len
        self.thick = thick
        self.vialHalf = vialHalf
        self.bubbleLen = bubbleLen
        self.bubbleH = thick * 0.3
        self.travel = vialHalf - bubbleLen / 2 - 3
        self.chipsTop = chipsTop
        self.ctaTop = ctaTop
    }

    /// Size of the baked level image, in points.
    var levelBox: CGSize { CGSize(width: len + Self.pad * 2, height: thick * 1.18 + Self.pad * 2) }

    /// The tube's centre line height (local y) at local x: arched, highest in the middle.
    func tubeY(_ x: CGFloat) -> CGFloat {
        let u = x / vialHalf
        return -thick * 0.02 + 3.2 * u * u
    }
}

enum SpiritLevelSliderArt {
    private typealias P = SpiritLevelSliderPaint

    static let ink: UInt32 = 0x2A241C
    static let lime: UInt32 = 0xC6E23F
    static let kicker = P.sans(11, .bold)
    static let etch = P.sans(7, .bold)

    static func background(_ g: SpiritLevelSliderGeo, scale: CGFloat) -> UIImage {
        P.bake(CGSize(width: g.w, height: g.h), scale: scale) { c in
            let all = P.rect(0, 0, g.w, g.h)
            P.linear(c, all, from: .zero, to: CGPoint(x: 0, y: g.h), [P.cg(0xF5F0E6), P.cg(0xECE4D5), P.cg(0xDFD5C2)], [0, 0.55, 1])
            P.radial(c, all, center: CGPoint(x: g.cx, y: g.cy), radius: g.w * 0.9, [P.cg(0xFFFAF0, 0.55), P.cg(0xFFFAF0, 0)], [0, 1])
            P.grain(c, CGRect(x: 0, y: 0, width: g.w, height: g.h), alpha: 0.09, seed: 7)
            let card = g.card
            let rr = P.rrect(card.minX, card.minY, card.width, card.height, 24)
            P.fill(c, P.rrect(card.minX, card.minY + 12, card.width, card.height, 24), 0x5A4A30, 0.14, blur: 18)
            P.fill(c, P.rrect(card.minX, card.minY + 1.5, card.width, card.height, 24), 0x5A4A30, 0.18, blur: 1.5)
            P.linear(c, rr, from: CGPoint(x: 0, y: card.minY), to: CGPoint(x: 0, y: card.maxY),
                     [P.cg(0xFFFDF8), P.cg(0xFBF7EF), P.cg(0xF4EEE2)], [0, 0.6, 1])
            P.stroke(c, rr, 0xFFFFFF, width: 1, 0.9)
            P.spaced("PITCH", x: card.minX + 20, y: card.minY + 28, kicker, ink, 0.45, track: 1.8)
            // Ground, a hedge and the walls: everything of the house but its roof.
            let gy = g.houseY
            P.stroke(c, P.line(card.minX + card.width * 0.42, gy, card.maxX - 18, gy), ink, width: 1.2, 0.25)
            for i in 0..<7 {
                let x = g.houseX + g.houseHW + 12 + CGFloat(i) * 7
                if x > card.maxX - 22 { break }
                P.fill(c, P.circle(x, gy - 6 - CGFloat(i % 2) * 2, 7), i % 2 == 1 ? 0x7C8F4E : 0x6B7F42, 0.9)
            }
            let left = g.houseX - g.houseHW
            let wall = P.rect(left, gy - g.houseWH, g.houseHW * 2, g.houseWH)
            P.linear(c, wall, from: CGPoint(x: left, y: 0), to: CGPoint(x: left + g.houseHW * 2, y: 0),
                     [P.cg(0xE9DCC6), P.cg(0xF1E6D3), P.cg(0xDCCBB0)], [0, 0.5, 1])
            P.stroke(c, wall, ink, width: 1, 0.35)
            let dw = g.houseHW * 0.26
            P.fill(c, P.rrect(g.houseX - dw / 2, gy - g.houseWH * 0.72, dw, g.houseWH * 0.72, 2), 0x8A5A3A)
            P.fill(c, P.circle(g.houseX + dw * 0.28, gy - g.houseWH * 0.36, 1.3), 0xF2C66B)
            for side in [-1.0, 1.0] {
                let wx = g.houseX + CGFloat(side) * g.houseHW * 0.58 - g.houseHW * 0.17
                let r = CGRect(x: wx, y: gy - g.houseWH * 0.74, width: g.houseHW * 0.34, height: g.houseWH * 0.36)
                P.linear(c, CGPath(rect: r, transform: nil), from: CGPoint(x: 0, y: r.minY), to: CGPoint(x: 0, y: r.maxY),
                         [P.cg(0xBCD3DC), P.cg(0x8FB0BF), P.cg(0x6F93A4)], [0, 0.5, 1])
                P.stroke(c, CGPath(rect: r, transform: nil), 0xFDFAF3, width: 1.5)
            }
        }
    }

    /// The level's soft shadow, centred in its image (the level's size plus shadowPad around).
    static func shadow(_ g: SpiritLevelSliderGeo, scale: CGFloat) -> UIImage {
        let pad = SpiritLevelSliderGeo.shadowPad
        return P.bake(CGSize(width: g.len + pad * 2, height: g.thick + pad * 2), scale: scale) { c in
            P.fill(c, P.rrect(pad, pad, g.len, g.thick, 8), 0x4A3A20, 0.22, blur: 14)
        }
    }

    /// The level in its own frame: origin at the body's centre, baked with `pad` around it.
    static func levelBody(_ g: SpiritLevelSliderGeo, scale: CGFloat) -> UIImage {
        P.bake(g.levelBox, scale: scale) { c in
            let pad = SpiritLevelSliderGeo.pad
            c.translateBy(x: g.len / 2 + pad, y: g.thick / 2 + pad)
            let L = g.len, T = g.thick
            let hl = L / 2, ht = T / 2
            let side = T * 0.18
            // Side face below the top face: the extrusion's thickness.
            P.linear(c, P.rrect(-hl, -ht + 2, L, T + side, 7), from: CGPoint(x: 0, y: ht - 4), to: CGPoint(x: 0, y: ht + side),
                     [P.cg(0xB07714), P.cg(0x8A5A0C), P.cg(0x5C3A06)], [0, 0.6, 1])
            // Anodised top face.
            let body = P.rrect(-hl, -ht, L, T, 6)
            P.linear(c, body, from: CGPoint(x: 0, y: -ht), to: CGPoint(x: 0, y: ht),
                     [P.cg(0xFFE189), P.cg(0xF6BF3A), P.cg(0xE8A522), P.cg(0xC98714)], [0, 0.3, 0.72, 1])
            // Brushed grain along the length.
            c.saveGState()
            c.addPath(body)
            c.clip()
            P.brush(c, CGRect(x: -hl, y: -ht, width: L, height: T), alpha: 0.12)
            c.restoreGState()
            P.stroke(c, P.line(-hl + 6, -ht + 0.8, hl - 6, -ht + 0.8), 0xFFF6D6, width: 1.2, 0.9)
            P.stroke(c, P.line(-hl + 6, ht - 0.6, hl - 6, ht - 0.6), 0x7A4C08, width: 1, 0.5)
            // Etched ruler along the top edge, every 4 pt, longer every 20.
            let etchPath = CGMutablePath()
            let cap = T * 0.62
            var x = -hl + cap + 6
            while x <= hl - cap - 6 {
                let k = Int(((x + hl) / 4).rounded())
                let l: CGFloat = k % 5 == 0 ? 6 : 3
                etchPath.move(to: CGPoint(x: x, y: -ht + 2))
                etchPath.addLine(to: CGPoint(x: x, y: -ht + 2 + l))
                x += 4
            }
            P.stroke(c, etchPath, 0x3A2606, width: 0.7, 0.55)
            P.spaced("TRUELINE 600", x: -hl + cap + 10, y: ht - 7, etch, 0x3A2606, 0.6, track: 1.4)
            let mm = "0.5 mm/m"
            P.text(mm, x: hl - cap - 10 - P.width(mm, etch), y: ht - 7, etch, 0x3A2606, 0.6)
            // Machined end caps with rubber bumpers.
            for s in [-1.0, 1.0] {
                let x0 = s < 0 ? -hl : hl - cap
                P.linear(c, P.rrect(x0, -ht - 1, cap, T + side + 1, 6), from: CGPoint(x: 0, y: -ht), to: CGPoint(x: 0, y: ht + side),
                         [P.cg(0x3A3D42), P.cg(0x24272B), P.cg(0x15171A)], [0, 0.5, 1])
                P.linear(c, P.rrect(x0 + (s < 0 ? 5 : 0), -ht + 3, cap - 5, T - 6, 3), from: CGPoint(x: 0, y: -ht), to: CGPoint(x: 0, y: ht),
                         [P.cg(0xEEF0F2), P.cg(0xB6BBC2), P.cg(0x8A9099)], [0, 0.5, 1])
                for i in 0..<4 {
                    let gx = x0 + (s < 0 ? 9 : 4) + CGFloat(i) * ((cap - 13) / 3)
                    P.stroke(c, P.line(gx, -ht + 7, gx, ht - 7), 0x5D636B, width: 1, 0.55)
                }
                P.stroke(c, P.line(x0 + (s < 0 ? 5 : 0) + 2, -ht + 3.8, x0 + cap - (s < 0 ? 2 : 7), -ht + 3.8), 0xFFFFFF, width: 0.8, 0.8)
            }
            // The vial window: a dark bezel, the lime liquid, the centre marks.
            let vw = g.vialHalf * 2 + 12
            let vh = T * 0.62
            P.fill(c, P.rrect(-vw / 2, -vh / 2, vw, vh, vh / 2), 0x3B2A0A, 0.85)
            P.linear(c, P.rrect(-vw / 2 + 2, -vh / 2 + 2, vw - 4, vh - 4, vh / 2 - 2), from: CGPoint(x: 0, y: -vh / 2), to: CGPoint(x: 0, y: vh / 2),
                     [P.cg(0xF4F0E3), P.cg(0xD9D2BD), P.cg(0xB5AC92)], [0, 0.5, 1])
            let tube = CGMutablePath()
            let th = T * 0.4
            let steps = 24
            for i in 0...steps {
                let x = -g.vialHalf + CGFloat(i) / CGFloat(steps) * g.vialHalf * 2
                let p = CGPoint(x: x, y: g.tubeY(x) - th / 2)
                if i == 0 { tube.move(to: p) } else { tube.addLine(to: p) }
            }
            for i in stride(from: steps, through: 0, by: -1) {
                let x = -g.vialHalf + CGFloat(i) / CGFloat(steps) * g.vialHalf * 2
                tube.addLine(to: CGPoint(x: x, y: g.tubeY(x) + th / 2))
            }
            tube.closeSubpath()
            P.linear(c, tube, from: CGPoint(x: 0, y: -th / 2), to: CGPoint(x: 0, y: th / 2 + 4),
                     [P.cg(0xE8F78A), P.cg(lime), P.cg(0x93B81F), P.cg(0x6F8F14)], [0, 0.35, 0.75, 1])
            P.stroke(c, tube, 0x4D6410, width: 1, 0.55)
            for s in [-1.0, 1.0] {
                let x = CGFloat(s) * (g.bubbleLen / 2 + 2)
                P.stroke(c, P.line(x, g.tubeY(x) - th / 2 - 2, x, g.tubeY(x) + th / 2 + 2), 0x1F1A10, width: 1.6, 0.9)
                let x2 = CGFloat(s) * (g.bubbleLen / 2 + 2 + g.travel * 0.45)
                P.stroke(c, P.line(x2, g.tubeY(x2) - th / 2, x2, g.tubeY(x2) - th / 2 + 4), 0x1F1A10, width: 1, 0.6)
                P.stroke(c, P.line(x2, g.tubeY(x2) + th / 2 - 4, x2, g.tubeY(x2) + th / 2), 0x1F1A10, width: 1, 0.6)
            }
        }
    }
}
