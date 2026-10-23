import CoreGraphics
import Foundation

/// The contacts: the four avatars melt into one liquid blob, contact droplets are pulled in from the
/// screen edges, and the blob splits back into the four avatars. The field is drawn by the Metal
/// shader in FindFriendsInviteGoo.metal (ball i is 7 floats: x, y, r, unused, red, green, blue).
enum FindFriendsInviteGoo {
    typealias M = FindFriendsInviteMotion
    static let balls = 9
    static let stride = 7

    struct Ball { var x: Double, y: Double, r: Double }

    struct Geo {
        var rest: [Ball]
        var cx: Double
        var cy: Double
        var width: Double
    }

    struct Face { var tx = 0.0, ty = 0.0, s = 1.0, o = 1.0 }
    struct DropState { var x = 0.0, y = 0.0, s = 1.0, o = 0.0, live = false }

    struct Frame {
        var on = false
        var goo = 0.0
        var gloss = 0.0
        /// 9 balls x 7 floats, colours filled in.
        var data: [Float]
        var faces = [Face](repeating: Face(), count: 4)
        var drops = [DropState](repeating: DropState(), count: 5)
    }

    static func rgb(_ hex: UInt32) -> [Float] {
        [Float((hex >> 16) & 255) / 255, Float((hex >> 8) & 255) / 255, Float(hex & 255) / 255]
    }

    /// The four avatars' colours, then the droplets'.
    static func colors(_ avatars: [UInt32]) -> [[Float]] {
        avatars.map(rgb) + M.Goo.drops.map { rgb($0.color) }
    }

    static func shown(avatarsAt sigma: Double) -> Bool { sigma < M.Goo.rnOff || sigma >= M.Goo.rnOn }
    static func gooOn(_ sigma: Double) -> Bool { sigma >= M.Goo.skiaOn && sigma < M.Goo.skiaOff }

    static func frame(_ sigma: Double, _ g: Geo, _ colors: [[Float]]) -> Frame {
        var out = Frame(data: [Float](repeating: 0, count: balls * stride))
        for i in 0..<balls {
            out.data[i * stride + 4] = colors[i][0]
            out.data[i * stride + 5] = colors[i][1]
            out.data[i * stride + 6] = colors[i][2]
        }
        out.on = gooOn(sigma)
        if !out.on { return out }

        let G = M.Goo.self
        let goo = M.bezier(M.easeInOut, M.seg(sigma, G.gooIn.0, G.gooIn.1))
            * (1 - M.bezier(M.easeOut, M.seg(sigma, G.gooOut.0, G.gooOut.1)))
        out.goo = goo
        out.gloss = goo

        let u = G.pullAmount * M.springStep(sigma - G.pullStart, omega: G.pullOmega, zeta: G.pullZeta)
            * (1 - M.springStep(sigma - G.splitStart, omega: G.splitOmega, zeta: G.splitZeta))

        var swell = 0.0
        for d in G.drops {
            let at = d.start + G.dropDur * 0.85
            swell += G.kickAmp * M.kick(sigma - at, period: G.kickPeriod, decay: G.kickDecay, fadeFrom: G.kickFadeFrom, end: G.kickEnd)
        }
        let env = M.seg(sigma, G.wobbleOn.0, G.wobbleOn.1) * (1 - M.seg(sigma, G.wobbleOff.0, G.wobbleOff.1))

        for i in 0..<4 {
            let b = g.rest[i]
            let ph = Double(i) * 1.7
            let ox = G.wobbleAmp * env * sin(2 * .pi * G.wobbleFreq * sigma + ph)
            let oy = G.wobbleAmp * env * cos(2 * .pi * G.wobbleFreq * 1.3 * sigma + ph)
            let x = b.x + u * (g.cx - b.x) + ox
            let y = b.y + u * (g.cy - b.y) + oy
            let r = b.r * (1 - G.pullShrink * M.clamp01(u)) * (1 + swell)
            out.data[i * stride] = Float(x)
            out.data[i * stride + 1] = Float(y)
            out.data[i * stride + 2] = Float(r)

            let rk = G.rank[i]
            let back = G.faceStart + Double(rk) * G.faceStagger
            var o = 1.0, s = 1.0
            if sigma < back {
                let a = G.sinkStart + Double(rk) * G.sinkStagger
                let e = M.bezier(M.easeOut, M.seg(sigma, a, a + G.sinkDur))
                o = 1 - e
                s = 1 - (1 - G.sinkScale) * e
            } else {
                o = M.bezier(M.easeOut, M.seg(sigma, back, back + G.faceDur))
                s = G.faceFrom + (1 - G.faceFrom) * M.springStep(sigma - back, omega: G.faceOmega, zeta: G.faceZeta)
            }
            out.faces[i] = Face(tx: x - b.x, ty: y - b.y, s: s, o: o)
        }

        for (j, dr) in G.drops.enumerated() {
            let q = M.seg(sigma, dr.start, dr.start + G.dropDur)
            if q <= 0 || q >= 1 { continue }
            let e = M.bezier(M.easeInOut, q)
            let sx = dr.side < 0 ? -G.dropOffscreen - dr.r : g.width + G.dropOffscreen + dr.r
            let sy = g.cy + dr.dy
            let tx = g.cx
            let ty = g.cy + dr.dy * 0.15
            let bow = (dr.dy >= 0 ? 1.0 : -1.0) * G.dropArc * sin(.pi * e)
            let x = sx + (tx - sx) * e
            let y = sy + (ty - sy) * e + bow
            let r = dr.r * (1 - M.seg(e, G.dropAbsorb, 1))
            let k = (4 + j) * stride
            out.data[k] = Float(x)
            out.data[k + 1] = Float(y)
            out.data[k + 2] = Float(r)
            out.drops[j] = DropState(x: x, y: y, s: r / dr.r, o: 1 - M.bezier(M.easeOut, M.seg(e, G.dropFade.0, G.dropFade.1)), live: true)
        }
        return out
    }
}
