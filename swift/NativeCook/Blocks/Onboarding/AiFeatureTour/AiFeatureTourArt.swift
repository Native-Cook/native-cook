import CoreText
import SwiftUI
import UIKit

// Coordinates stay in the reference pin's pixels. Only the root transform scales them.
enum AiFeatureTourArt {
    typealias C = AiFeatureTourTheme.C
    typealias M = AiFeatureTourMotion
    typealias UI = AiFeatureTourTheme.UI
    static let blueDays = [2, 4, 6, 8, 10, 12, 15, 17, 19, 22, 24, 26]

    static func tile(_ canvas: AiFeatureTourCanvas, _ x: Double, _ y: Double, _ w: Double,
                     _ h: Double, _ r: Double, _ lift: Double = 0) {
        canvas.round(x + 2, y + 8 + 4 * lift, w - 4, h, r, C.tileShade, 0.35 + 0.15 * lift, blur: 9 + 4 * lift)
        canvas.round(x, y, w, h, r, C.tile)
        canvas.round(x, y, w, h, r, C.tileEdge, width: 1.5)
    }
    static func check(_ canvas: AiFeatureTourCanvas, _ x: Double, _ y: Double, _ r: Double,
                      _ color: String, _ k: Double, _ filled: Bool) {
        canvas.circle(x, y, r, color, width: filled ? 0 : 2.4)
        if k <= 0 { return }
        let a = CGPoint(x: x - r * 0.42, y: y + r * 0.02)
        let b = CGPoint(x: x - r * 0.1, y: y + r * 0.34)
        let end = CGPoint(x: x + r * 0.46, y: y - r * 0.32)
        let l1 = hypot(b.x - a.x, b.y - a.y)
        let l2 = hypot(end.x - b.x, end.y - b.y)
        let distance = M.clamp01(k) * (l1 + l2)
        let path = CGMutablePath()
        path.move(to: a)
        if distance <= l1 {
            path.addLine(to: CGPoint(x: M.mix(a.x, b.x, distance / l1), y: M.mix(a.y, b.y, distance / l1)))
        } else {
            path.addLine(to: b)
            path.addLine(to: CGPoint(x: M.mix(b.x, end.x, (distance - l1) / l2), y: M.mix(b.y, end.y, (distance - l1) / l2)))
        }
        canvas.path(path, filled ? "#FFFFFF" : color, width: r * 0.28)
    }
    static func runner(_ canvas: AiFeatureTourCanvas, _ x: Double, _ y: Double, _ s: Double, _ color: String) {
        canvas.circle(x + 3 * s, y - 9 * s, 2.6 * s, color)
        for coordinates in [(1.0, -5.0, -2.0, 3.0), (-2, 3, -7, 9), (-2, 3, 4, 6), (4, 6, 4, 11), (0, -3, -6, -1), (0, -3, 6, 0)] {
            canvas.line(x + coordinates.0 * s, y + coordinates.1 * s, x + coordinates.2 * s, y + coordinates.3 * s, color, 2.6 * s)
        }
    }
    static func star(_ canvas: AiFeatureTourCanvas, _ x: Double, _ y: Double, _ r: Double, _ color: String, _ alpha: Double = 1) {
        let path = CGMutablePath()
        path.move(to: CGPoint(x: x, y: y - r))
        for point in [CGPoint(x: x + r, y: y), CGPoint(x: x, y: y + r), CGPoint(x: x - r, y: y), CGPoint(x: x, y: y - r)] {
            path.addQuadCurve(to: point, control: CGPoint(x: x, y: y))
        }
        path.closeSubpath()
        canvas.path(path, color, alpha)
    }
    static func calendar(_ canvas: AiFeatureTourCanvas, _ p: Double) {
        tile(canvas, -57, -50, 114, 108, 18, p >= 0 ? M.bump(p, 0, 1) : 0)
        canvas.saved {
            canvas.clipRound(-57, -50, 114, 108, 18)
            canvas.rect(-57, -50, 114, 26, C.calTop)
        }
        for x in [-38.0, 38.0] {
            canvas.round(x - 3, -60, 6, 20, 3, "#F3F5F6")
            canvas.round(x - 3, -60, 6, 20, 3, C.calRing, width: 2)
        }
        for k in 0..<28 {
            let x = -42.0 + Double(k % 7) * 14
            let y = -10.0 + Double(k / 7) * 14
            let blue = blueDays.firstIndex(of: k)
            var scale = 1.0
            if let blue, p >= 0 { scale += 0.9 * M.bump(p, 0.08 + Double(blue) * 0.06, 0.22) }
            canvas.circle(x, y, 3.2 * scale, blue != nil ? C.dotBlue : C.dotGrey)
            if let blue, p >= 0 {
                let ring = M.bump(p, 0.1 + Double(blue) * 0.06, 0.3)
                if ring > 0.01 { canvas.circle(x, y, 3.2 + 7 * ring, C.dotBlue, 0.6 * ring, width: 1.6) }
            }
        }
    }
    static func trend(_ canvas: AiFeatureTourCanvas, _ p: Double) {
        tile(canvas, -57, -57, 114, 114, 20, p >= 0 ? M.bump(p, 0, 1) : 0)
        for k in 1..<4 {
            let v = -57.0 + Double(k) * 28
            canvas.line(v, -55, v, 55, C.grid, 1.5)
            canvas.line(-55, v, 55, v, C.grid, 1.5)
        }
        var progress = 1.0
        var dash = 1.0
        if p >= 0 {
            progress = p < 0.1 ? 1 - p / 0.1 : M.easeOut((p - 0.12) / 0.45)
            dash = p < 0.1 ? 1 - p / 0.1 : M.smooth((p - 0.6) / 0.25)
        }
        let tip = CGPoint(x: M.mix(-40, 12, progress), y: M.mix(-28, 28, progress))
        canvas.line(-40, -28, tip.x, tip.y, C.greenLine, 7)
        canvas.circle(-40, -28, 7, "#FFFFFF")
        canvas.circle(-40, -28, 7, C.green, width: 4)
        if dash > 0.01 {
            for i in 0..<4 {
                let u0 = Double(i) / 4
                let u1 = min(u0 + 0.12, dash)
                if u0 > dash { break }
                canvas.line(M.mix(12, 42, u0), M.mix(28, -12, u0), M.mix(12, 42, u1), M.mix(28, -12, u1), C.checkGrey, 4)
            }
            canvas.circle(42, -12, 7 * (p >= 0 ? 1 + 0.3 * M.bump(p, 0.8, 0.2) : 1), "#FFFFFF", dash)
            canvas.circle(42, -12, 7, C.checkGrey, dash, width: 4)
        }
        canvas.circle(tip.x, tip.y, 11, C.green)
        canvas.circle(tip.x, tip.y, 11, "#FFFFFF", width: 3)
    }
    static func templates(_ canvas: AiFeatureTourCanvas, _ p: Double) {
        let out = p >= 0 ? M.bump(p, 0.05, 0.9) : 0
        let ck = p >= 0 ? (p < 0.35 ? 1 - M.smooth(p / 0.15) : M.smooth((p - 0.45) / 0.3)) : 1
        for (y, alpha) in [(-42.0, 0.85), (42.0, 0.6)] {
            canvas.saved {
                canvas.translate(-4 * out, y + (y < 0 ? -6 : 6) * out)
                canvas.round(-56, -16, 112, 32, 10, C.tileShade, 0.25 * alpha, blur: 5)
                canvas.round(-56, -16, 112, 32, 10, "#FFFFFF", alpha)
                canvas.round(-56, -16, 112, 32, 10, C.tileEdge, alpha, width: 1.2)
                if y < 0 {
                    canvas.circle(-38, -2, 3, "#9AA2AA", alpha)
                    canvas.line(-44, 4, -32, 4, "#9AA2AA", 2.2, alpha)
                } else { canvas.line(-44, 2, -32, -3, "#B0B6BD", 2.2, alpha) }
                canvas.round(-20, -6, 40, 5, 2.5, C.line, alpha)
                canvas.round(-20, 3, 28, 4, 2, C.line, alpha)
                check(canvas, 40, 0, 9, y < 0 ? "#9AA2AA" : C.checkGrey, 1, y < 0)
            }
        }
        canvas.saved {
            canvas.translate(30 * out, 0)
            canvas.scale(1 + 0.06 * out)
            canvas.round(-66, -20, 132, 40, 12, C.tileShade, 0.45, blur: 8)
            canvas.round(-66, -20, 132, 40, 12, "#FFFFFF")
            canvas.round(-66, -20, 132, 40, 12, C.tileEdge, width: 1.2)
            runner(canvas, -46, 0, 1, "#1E1E22")
            canvas.round(-26, -7, 46, 6, 3, C.line)
            canvas.round(-26, 4, 32, 5, 2.5, C.line)
            check(canvas, 46, 0, 11, C.check, ck, true)
        }
    }
    static func avocado(_ canvas: AiFeatureTourCanvas, _ p: Double) {
        let next = p >= 0 ? M.bump(p, 0.55, 0.45) : 0
        canvas.saved {
            canvas.translate(20 + 14 * next, 28 - 4 * next)
            canvas.rotate(4 + 6 * next)
            tile(canvas, -38, -38, 76, 76, 14)
            canvas.circle(18, 4, 9, "#E9C9A8")
            canvas.circle(15, 1, 4, "#C49A70")
        }
        canvas.saved {
            canvas.translate(-12, -10)
            canvas.rotate(-2)
            tile(canvas, -48, -46, 96, 96, 16, p >= 0 ? M.bump(p, 0, 1) : 0)
            canvas.saved {
                canvas.rotate(-28)
                canvas.oval(-26, -34, 52, 66, C.avoSkin)
                canvas.oval(-21, -28, 42, 56, C.avoFlesh)
                canvas.radialOval(-18, -24, 36, 50, 0, 6, 24, C.avoInner, C.avoFlesh)
                canvas.radialOval(-12, -5, 24, 24, -4, 2, 14, "#C9783A", C.pit)
                canvas.circle(-4, 3, 3.5, "#FFFFFF", 0.5)
            }
            if p >= 0 {
                let sy = M.mix(-46, 50, M.easeOut((p - 0.08) / 0.5))
                let sa = M.bump(p, 0.06, 0.56)
                if sa > 0.01 {
                    canvas.saved {
                        canvas.clipRound(-48, -46, 96, 96, 16)
                        canvas.linear(CGRect(x: -48, y: sy - 26, width: 96, height: 26), 0, sy - 26, 0, sy,
                                 ["#8E7BFF", "#8E7BFF"], [0, 0.28 * sa], [0, 1])
                        canvas.line(-48, sy, 48, sy, "#7C68FF", 2.5, sa)
                    }
                }
                for k in 0..<3 {
                    let scale = M.bump(p, 0.55 + Double(k) * 0.08, 0.35)
                    if scale > 0.01 { star(canvas, [-38.0, 40, 30][k], [-40.0, -30, 40][k], 9 * scale, "#8E7BFF", scale) }
                }
            }
        }
    }
    static func clipboard(_ canvas: AiFeatureTourCanvas, _ p: Double) {
        tile(canvas, -48, -56, 96, 118, 12, p >= 0 ? M.bump(p, 0, 1) : 0)
        canvas.round(-26, -70, 52, 22, 9, "#C8CBD0")
        canvas.circle(0, -66, 5, "#E8EAED")
        for k in 0..<3 {
            let y = -20.0 + Double(k) * 30
            var draw = 1.0
            if p >= 0 { draw = p < 0.12 ? 1 - p / 0.12 : M.easeOut((p - (0.18 + Double(k) * 0.2)) / 0.22) }
            let alpha = M.clamp01(draw * 1.5)
            if k == 0 { runner(canvas, -28, y, 0.75, C.runner) }
            else { check(canvas, -28, y, 7, "#B4BAC1", draw, false) }
            canvas.round(-12, y - 6, 44 * draw, 4.5, 2, "#C4C9CF", alpha)
            canvas.round(-12, y + 3, 32 * draw, 4, 2, "#D9DDE1", alpha)
        }
    }
    static func icon(_ canvas: AiFeatureTourCanvas, _ i: Int, _ p: Double) {
        switch i {
        case 0: calendar(canvas, p)
        case 1: trend(canvas, p)
        case 2: templates(canvas, p)
        case 3: avocado(canvas, p)
        default: clipboard(canvas, p)
        }
    }
    static func draw(_ canvas: AiFeatureTourCanvas, _ width: Double, _ height: Double, _ frame: M.Frame) {
        let scale = width / AiFeatureTourTheme.REF_W * AiFeatureTourTheme.S
        let h = height / scale
        canvas.scale(scale)
        canvas.linear(CGRect(x: 0, y: 0, width: 720, height: h), 0, 0, 0, 520,
                 [C.bgTop, C.bgMid, C.bg], [1, 1, 1], [0, 0.45, 1])
        canvas.radial(CGRect(x: 0, y: 600, width: 720, height: max(0, h - 600)), 360, 1250, 520, [C.glow, C.glow], [0.9, 0])
        canvas.text(AiFeatureTourTheme.title, UI.title.x, UI.title.y, "Bold", 42, C.title, ax: 0.5)
        let rows = UI.rows
        let button = UI.button
        let bc = CGPoint(x: button.x + button.w / 2, y: button.y + button.h / 2)
        for i in AiFeatureTourTheme.rows.indices {
            let ty = rows.y0 + Double(i) * rows.pitch
            let iy = ty + 42
            let fade = i == 5 ? 0.35 : 1.0
            let row = AiFeatureTourTheme.rows[i]
            canvas.text(row.title, rows.textX, ty, "SemiBold", 30, C.rowTitle, fade)
            let act0 = i < 5 ? frame.act[i] : -1
            if act0 >= 0 {
                let tw = canvas.textWidth(row.title, "SemiBold", 30)
                let gx = rows.textX - 80 + (tw + 160) * M.smooth(act0 / 0.8)
                canvas.titleLight(row.title, rows.textX, ty, "SemiBold", 30, gx)
            }
            for l in 0..<2 { canvas.text(row.sub[l], rows.textX, ty + rows.sub[l], "Regular", 27, C.sub, fade) }
            if i == 5 {
                canvas.saved {
                    canvas.translate(rows.iconX + 20, iy)
                    canvas.circle(-4, -6, 26, "#C9CCD3", width: 9)
                    canvas.line(15, 13, 34, 32, "#C9CCD3", 11)
                }
                continue
            }
            var x = rows.iconX
            var y = iy
            var size = 1.0
            var rotation = 0.0
            var alpha = 1.0
            if frame.fly >= 0 {
                let fl = M.flight(i, frame.fly)
                if fl.gone { continue }
                if fl.back > 0 {
                    size = M.mix(0.55, 1, fl.back)
                    alpha = M.clamp01(fl.back * 1.6)
                } else if fl.out > 0 {
                    let e = fl.out
                    let cx = rows.iconX + 260
                    let cy = M.mix(iy, bc.y, 0.2) - 120
                    x = (1 - e) * (1 - e) * rows.iconX + 2 * (1 - e) * e * cx + e * e * bc.x
                    y = (1 - e) * (1 - e) * iy + 2 * (1 - e) * e * cy + e * e * bc.y
                    size = M.mix(1, 0.25, e)
                    rotation = 40 * e * (i % 2 == 0 ? 1 : -1)
                    alpha = 1 - M.smooth((e - 0.75) / 0.25)
                }
            }
            let act = frame.act[i]
            if act >= 0 { size *= 1 + 0.05 * M.bump(act, 0, 1) }
            canvas.saved {
                canvas.translate(x, y)
                canvas.rotate(rotation)
                canvas.scale(size)
                canvas.layer(alpha < 0.999 ? alpha : 1) { icon(canvas, i, act) }
            }
        }
        canvas.linear(CGRect(x: 0, y: button.y - 70, width: 720, height: max(0, h - button.y + 70)),
                 0, button.y - 70, 0, button.y + 30, [C.bg, C.bg], [0, 0.9], [0, 1])
        let sp = frame.fly >= 0 ? frame.fly - (M.T.sparkle - M.T.press) : -1
        let glow = sp >= 0 ? M.bump(sp, 0, 0.9) : 0
        canvas.saved {
            canvas.translate(bc.x, bc.y)
            canvas.scale(1 - 0.04 * frame.press + 0.02 * glow)
            if glow > 0.01 { canvas.round(-button.w / 2 - 6, -button.h / 2 - 6, button.w + 12, button.h + 12, button.h / 2 + 6, C.sparkle, 0.7 * glow, blur: 18) }
            canvas.round(-button.w / 2, -button.h / 2, button.w, button.h, button.h / 2, C.button)
            let tw = canvas.textWidth(AiFeatureTourTheme.button, "SemiBold", 34)
            canvas.text(AiFeatureTourTheme.button, -(tw + 50) / 2, 0, "SemiBold", 34, "#FFFFFF")
            let ax = -(tw + 50) / 2 + tw + 18 + 6 * glow
            canvas.line(ax, 0, ax + 32, 0, "#FFFFFF", 3.6)
            canvas.line(ax + 20, -12, ax + 32, 0, "#FFFFFF", 3.6)
            canvas.line(ax + 20, 12, ax + 32, 0, "#FFFFFF", 3.6)
        }
        if sp >= 0 && sp < 1.1 {
            let k = M.easeOut(sp / 0.35) * (1 - M.smooth((sp - 0.55) / 0.5))
            if k > 0.01 {
                canvas.saved {
                    canvas.translate(bc.x, bc.y - 2)
                    canvas.rotate(90 * sp)
                    star(canvas, 0, 0, 46 * k, C.sparkle, 0.95 * k)
                    star(canvas, 0, 0, 24 * k, "#FFFFFF", k)
                }
                for i in 0..<8 {
                    let angle = Double(i) / 8 * Double.pi * 2 + 0.3
                    let distance = 40 + 120 * M.easeOut(sp / 0.8)
                    star(canvas, bc.x + cos(angle) * distance * 1.6, bc.y + sin(angle) * distance * 0.55,
                         8 * (1 - M.smooth(sp / 0.9)), i % 2 != 0 ? "#FFFFFF" : C.sparkle, 1 - M.smooth(sp / 0.9))
                }
            }
        }
    }
}
