import SwiftUI

/*
 Crayon Box Picker — what is drawn. Baked once at 2x: the desk with the paper and the open box's
 back, the box's printed front, each of the twelve crayons, and the paper's tooth. Per frame: the
 swatches (wax strokes with paper tooth knocked out of them), the crayons at their poses, the box
 front over them, and the crayon in flight on top.
 */

struct CrayonBoxPickerGeo: Equatable {
    var w: Double
    var h: Double
    var cx: Double
    var boxX: Double
    var boxW: Double
    var rimY: Double
    var frontH: Double
    var pitch: Double
    var cw: Double
    var cl: Double
    /// Visible length above the rim, front and back row.
    var showFront: Double
    var showBack: Double
    var paperY: Double
    var paperH: Double
    /// Swatch centre and radii.
    var sx: Double
    var sy: Double
    var rx: Double
    var ry: Double

    init(w: Double, h: Double, hero: CGRect) {
        // Everything scales with the hero's height (designed for 470 pt) so a pulled crayon always
        // stays inside it: a crayon comes fully out of the box at the top of the hero.
        let u = min(1, hero.height / 470)
        self.w = w
        self.h = h
        boxW = min(330, hero.width - 16)
        frontH = 92 * u
        showFront = 84 * u
        rimY = hero.minY + 182 * u
        pitch = (boxW - 28) / 6
        paperY = rimY + frontH + 22 * u
        paperH = max(80, hero.minY + hero.height - paperY - 6)
        cx = hero.midX
        boxX = hero.minX + (hero.width - boxW) / 2
        cw = pitch * 0.6
        cl = frontH - 8 + showFront
        showBack = showFront + 24 * u
        sx = hero.midX + 8
        sy = paperY + paperH * 0.5
        rx = min(112, hero.width * 0.3)
        ry = min(44, paperH * 0.3)
    }

    /// A crayon's slot: centre x, centre y, row (0 back, 1 front), visible length.
    func slot(_ i: Int) -> (x: Double, y: Double, row: Int, show: Double) {
        let back = i < 6
        let k = Double(back ? i : i - 6)
        let x = boxX + 14 + (k + 0.5) * pitch + (back ? -pitch * 0.22 : pitch * 0.22)
        let show = back ? showBack : showFront
        return (x, rimY - show + cl / 2, back ? 0 : 1, show)
    }

    /// Vector from a crayon's centre to its tip for a rotation of `deg`.
    func tipOffset(_ deg: Double) -> (Double, Double) {
        let a = deg * .pi / 180
        return (cl / 2 * sin(a), -(cl / 2) * cos(a))
    }

    /// Which crayon a point is on, front row first; -1 for none.
    func crayonAt(_ x: Double, _ y: Double) -> Int {
        for row in [1, 0] {
            for k in 0..<6 {
                let i = row == 0 ? k : 6 + k
                let s = slot(i)
                let top = rimY - s.show - 12
                if abs(x - s.x) < pitch * 0.5 && y > top && y < rimY + (row == 1 ? frontH * 0.6 : 0) { return i }
            }
        }
        return -1
    }
}

/// The scribble's path on the paper, sampled (points flat, running lengths), plus a jittered copy
/// for the wax (Skia's discrete path effect: 5 pt segments, 1.1 pt deviation).
struct CrayonBoxPickerScribble {
    var pts: [Double] = [0, 0]
    var len: [Double] = [0]
    var wax: [Double] = [0, 0]
    var waxLen: [Double] = [0]
    var total: Double { len.last ?? 0 }

    init() {}

    init(_ g: CrayonBoxPickerGeo) {
        var pts: [Double] = []
        let rows = 15
        var seed = 3
        func rnd() -> Double {
            seed = (seed * 16807) % 2_147_483_647
            return Double(seed) / 2_147_483_647
        }
        var side = -1.0
        let slant = tan(14 * Double.pi / 180)
        for k in 0..<rows {
            let v = -1 + 2 * Double(k) / Double(rows - 1)
            let y = v * g.ry * 0.92
            let hw = g.rx * sqrt(max(0.05, 1 - v * v)) * (0.86 + rnd() * 0.16)
            let x0 = side * hw, x1 = -side * hw
            for s in 0...10 {
                let u = Double(s) / 10
                let x = x0 + (x1 - x0) * u
                let yy = y + sin(u * .pi) * (2 + rnd() * 1.5) + x * slant * 0.3 + g.ry * 1.85 / Double(rows - 1) * (u - 0.5) * 0.5
                pts.append(g.sx + x)
                pts.append(g.sy + yy)
            }
            side = -side
        }
        var len: [Double] = []
        var run = 0.0
        var i = 0
        while i < pts.count {
            if i > 0 { run += hypot(pts[i] - pts[i - 2], pts[i + 1] - pts[i - 1]) }
            len.append(run)
            i += 2
        }
        self.pts = pts
        self.len = len
        // The wax's jitter, sampled every 5 pt along the scribble with a seeded offset.
        var wax: [Double] = []
        var waxLen: [Double] = []
        var jseed = 4
        func jr() -> Double {
            jseed = (jseed * 48271) % 2_147_483_647
            return Double(jseed) / 2_147_483_647 * 2 - 1
        }
        var d = 0.0
        while d <= run {
            let p = Self.point(pts, len, d)
            let edge = d == 0
            wax.append(p.0 + (edge ? 0 : jr() * 1.1))
            wax.append(p.1 + (edge ? 0 : jr() * 1.1))
            waxLen.append(d)
            d += 5
        }
        let end = Self.point(pts, len, run)
        wax.append(end.0)
        wax.append(end.1)
        waxLen.append(run)
        self.wax = wax
        self.waxLen = waxLen
    }

    static func point(_ pts: [Double], _ len: [Double], _ d: Double) -> (Double, Double) {
        let n = len.count
        if n == 0 { return (0, 0) }
        for k in 1..<max(1, n) where len[k] >= d {
            let f = (d - len[k - 1]) / max(0.0001, len[k] - len[k - 1])
            return (pts[k * 2 - 2] + (pts[k * 2] - pts[k * 2 - 2]) * f, pts[k * 2 - 1] + (pts[k * 2 + 1] - pts[k * 2 - 1]) * f)
        }
        return (pts[n * 2 - 2], pts[n * 2 - 1])
    }

    func point(_ d: Double) -> (Double, Double) { Self.point(pts, len, d) }

    /// The first `upto` pt of a sampled polyline.
    static func prefix(_ pts: [Double], _ len: [Double], _ upto: Double) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: pts[0], y: pts[1]))
        for k in 1..<len.count {
            if len[k] <= upto {
                p.addLine(to: CGPoint(x: pts[k * 2], y: pts[k * 2 + 1]))
            } else {
                let q = point(pts, len, upto)
                p.addLine(to: CGPoint(x: q.0, y: q.1))
                break
            }
        }
        return p
    }
}

struct CrayonBoxPickerImages {
    let desk: Image
    let front: Image
    let crayons: [Image]
    let tooth: Image
    let toothRect: CGRect
}

/// A crayon's pose: x, y, rotation (deg), scale, lift, drawn on top of the box.
struct CrayonBoxPickerPose {
    var x: Double
    var y: Double
    var deg: Double
    var scale: Double
    var lift: Double
    var top: Bool
}

enum CrayonBoxPickerArt {
    /// One swatch: the first `upto` pt of the scribble in crayon `c`, at `alpha`.
    static func drawSwatch(_ ctx: GraphicsContext, _ s: CrayonBoxPickerScribble, _ art: CrayonBoxPickerImages, _ c: Int, _ upto: Double, _ alpha: Double) {
        if upto <= 0 || alpha <= 0.003 { return }
        let rgb = CrayonBoxPickerMotion.crayons[c].rgb
        let wax = CrayonBoxPickerScribble.prefix(s.wax, s.waxLen, upto)
        let line = CrayonBoxPickerScribble.prefix(s.pts, s.len, upto)
        var outer = ctx
        outer.opacity = alpha
        outer.drawLayer { l in
            l.stroke(wax, with: .color(Color(red: rgb.0, green: rgb.1, blue: rgb.2, opacity: 0.9)), style: StrokeStyle(lineWidth: 12, lineCap: .round, lineJoin: .round))
            var hi = l
            hi.translateBy(x: -1.5, y: -2)
            hi.stroke(line, with: .color(Color.white.opacity(0.14)), style: StrokeStyle(lineWidth: 4, lineCap: .round, lineJoin: .round))
            // Paper tooth: the wax skips over the grain, so speckles of paper show through.
            l.blendMode = .destinationOut
            l.draw(art.tooth, in: art.toothRect)
        }
    }

    /// A crayon image centred at (x, y), turned by `deg`, lifted by `lift` (bigger shadow).
    static func drawCrayon(_ ctx: GraphicsContext, _ g: CrayonBoxPickerGeo, _ img: GraphicsContext.ResolvedImage, _ q: CrayonBoxPickerPose, lift: Double) {
        let rect = CGRect(x: -g.cw / 2, y: -g.cl / 2, width: g.cw, height: g.cl)
        if lift > 0.001 {
            ctx.drawLayer { l in
                l.addFilter(.blur(radius: 3 + 9 * lift))
                l.translateBy(x: q.x + 6 + 16 * lift, y: q.y + 8 + 22 * lift)
                l.rotate(by: .degrees(q.deg))
                l.fill(Path(roundedRect: rect, cornerRadius: g.cw / 2), with: .color(Color(red: 0.2, green: 0.13, blue: 0.06, opacity: 0.28 - 0.1 * lift)))
            }
        }
        var c = ctx
        c.translateBy(x: q.x, y: q.y)
        c.rotate(by: .degrees(q.deg))
        c.scaleBy(x: q.scale, y: q.scale)
        c.draw(img, in: rect)
    }

    /// Poses for the box with crayon `active` in `phase`. `pen0`/`pen`/`penEnd` are the scribble's
    /// start, the pen's current point and the scribble's end.
    static func poses(_ g: CrayonBoxPickerGeo, _ active: Int, _ phase: CrayonBoxPickerPhase, _ pen0: (Double, Double), _ pen: (Double, Double), _ penEnd: (Double, Double)) -> [CrayonBoxPickerPose] {
        var out: [CrayonBoxPickerPose] = []
        let write = -120.0
        let ph = phase.phase
        let a = phase.amount
        var lean = 0.0
        if ph == 1 { lean = min(1, a) } else if ph >= 2 && ph <= 4 { lean = 1 } else if ph == 5 { lean = a }
        for i in 0..<12 {
            let s = g.slot(i)
            var q = CrayonBoxPickerPose(x: s.x, y: s.y, deg: 0, scale: 1, lift: 0, top: false)
            let out0 = g.cl - s.show + 6
            if i == active {
                let px = s.x, py = s.y - out0
                if ph == 1 || ph == 5 {
                    q.y = s.y - out0 * a
                    q.deg = 4 * phase.wobble
                } else {
                    let tw = g.tipOffset(write)
                    let tipFrom = ph == 4 ? penEnd : pen0
                    let wx = tipFrom.0 - tw.0, wy = tipFrom.1 - tw.1
                    if ph == 3 {
                        let d = write + 3 * sin(a * .pi * 22)
                        let tip = g.tipOffset(d)
                        q.x = pen.0 - tip.0
                        q.y = pen.1 - tip.1
                        q.deg = d
                    } else {
                        // ph 2 flies to the paper, ph 4 flies home: same arc, reversed.
                        let f = ph == 2 ? a : 1 - a
                        let mx = (px + wx) / 2 + 30
                        let my = min(py, wy) - 50
                        q.x = (1 - f) * (1 - f) * px + 2 * f * (1 - f) * mx + f * f * wx
                        q.y = (1 - f) * (1 - f) * py + 2 * f * (1 - f) * my + f * f * wy
                        q.deg = write * CrayonBoxPickerMotion.easeInOut(f)
                        q.lift = sin(.pi * f)
                        q.scale = 1 + 0.08 * q.lift
                    }
                    q.top = true
                }
            } else if active >= 0 && lean > 0 {
                // Neighbours in the same row lean a little into the gap (pivoting at their foot).
                let sameRow = (active < 6) == (i < 6)
                let d = i - active
                if sameRow && abs(d) == 1 {
                    q.deg = -Double(d) * 3.2 * lean
                    let r = q.deg * .pi / 180
                    let foot = g.cl / 2
                    q.x += foot * sin(r)
                    q.y += foot * (1 - cos(r))
                }
            }
            out.append(q)
        }
        return out
    }

    static func drawBox(_ ctx: GraphicsContext, _ g: CrayonBoxPickerGeo, _ art: CrayonBoxPickerImages, _ poses: [CrayonBoxPickerPose], _ swatch: (Int, Double, Int, Double), _ s: CrayonBoxPickerScribble) {
        ctx.draw(art.desk, in: CGRect(x: 0, y: 0, width: g.w, height: g.h))
        drawSwatch(ctx, s, art, swatch.0, s.total, swatch.1)
        drawSwatch(ctx, s, art, swatch.2, s.total * swatch.3, 1)
        let imgs = art.crayons.map { ctx.resolve($0) }
        // Crayons inside the box are clipped to above its floor; the front panel covers their feet.
        var inBox = ctx
        inBox.clip(to: Path(CGRect(x: 0, y: 0, width: g.w, height: g.rimY + g.frontH - 6)))
        for i in 0..<12 where !poses[i].top { drawCrayon(inBox, g, imgs[i], poses[i], lift: 0) }
        ctx.draw(art.front, in: CGRect(x: g.boxX - 20, y: g.rimY - 20, width: g.boxW + 40, height: g.frontH + 40))
        for i in 0..<12 where poses[i].top { drawCrayon(ctx, g, imgs[i], poses[i], lift: poses[i].lift) }
    }

    static func drawPick(_ ctx: GraphicsContext, _ g: CrayonBoxPickerGeo, _ art: CrayonBoxPickerImages, _ s: CrayonBoxPickerScribble, crayon: Int, phase: CrayonBoxPickerPhase, swatch: (Int, Double, Int, Double)) {
        let pen0 = (s.pts[0], s.pts[1])
        let penEnd = (s.pts[s.pts.count - 2], s.pts[s.pts.count - 1])
        let pen = phase.phase == 3 ? s.point(s.total * phase.amount) : pen0
        drawBox(ctx, g, art, poses(g, crayon, phase, pen0, pen, penEnd), swatch, s)
    }

    static func drawLoopFrame(_ ctx: GraphicsContext, _ g: CrayonBoxPickerGeo, _ art: CrayonBoxPickerImages, _ s: CrayonBoxPickerScribble, t: Double) {
        let p = CrayonBoxPickerMotion.loopPick(t)
        drawPick(ctx, g, art, s, crayon: p.0, phase: p.1, swatch: CrayonBoxPickerMotion.loopSwatch(t))
    }
}
