import SwiftUI

private typealias S = MorphingBadgeTourScenes
private typealias M = MorphingBadgeTourMotion

/// Draws the cast of the tour into a canvas.
enum MorphingBadgeTourStage {
    private static func color(_ value: Double, _ alpha: Double = 1) -> Color {
        Color(hex: UInt32(value), opacity: alpha)
    }

    /// A rounded rectangle round (x, y), filled with a gradient from `a` to `b`: down at tilt 0, across at 1.
    private static func block(
        _ context: inout GraphicsContext, x: Double, y: Double, w: Double, h: Double, radius: Double, alpha: Double, a: Double, b: Double, tilt: Double
    ) {
        guard alpha > 0.003, w > 0.5, h > 0.5 else { return }
        let dx = sin(tilt * .pi / 2) * w / 2
        let dy = cos(tilt * .pi / 2) * h / 2
        let r = min(radius, w / 2, h / 2)
        let path = Path(roundedRect: CGRect(x: x - w / 2, y: y - h / 2, width: w, height: h), cornerRadius: r, style: .circular)
        var layer = context
        layer.opacity = alpha
        layer.fill(
            path,
            with: .linearGradient(Gradient(colors: [color(a), color(b)]), startPoint: CGPoint(x: x - dx, y: y - dy), endPoint: CGPoint(x: x + dx, y: y + dy))
        )
    }

    /// A polygon whose points start at `first` in `points`, drawn round (x, y) at `scale`.
    private static func polygon(_ context: inout GraphicsContext, _ points: [Double], first: Int, x: Double, y: Double, scale: Double, color tint: Double, alpha: Double) {
        var path = Path()
        var i = first
        while i + 1 < points.count {
            let point = CGPoint(x: x + points[i] * scale, y: y + points[i + 1] * scale)
            if i == first { path.move(to: point) } else { path.addLine(to: point) }
            i += 2
        }
        path.closeSubpath()
        context.fill(path, with: .color(color(tint, alpha)))
    }

    private static func glyph(_ context: inout GraphicsContext, _ index: Int, x: Double, y: Double, scale: Double, color tint: Double, alpha: Double) {
        guard alpha > 0.003 else { return }
        for part in S.glyphs[index] {
            if part[0] == 0 {
                polygon(&context, part, first: 1, x: x, y: y, scale: scale, color: tint, alpha: alpha)
            } else if part[0] == 2 {
                let box = CGRect(x: x + (part[1] - part[3]) * scale, y: y + (part[2] - part[3]) * scale, width: part[3] * 2 * scale, height: part[3] * 2 * scale)
                context.fill(Path(ellipseIn: box), with: .color(color(tint, alpha)))
            } else {
                let box = CGRect(x: x + part[1] * scale, y: y + part[2] * scale, width: part[3] * scale, height: part[4] * scale)
                context.fill(Path(roundedRect: box, cornerRadius: part[5] * scale, style: .circular), with: .color(color(tint, alpha)))
            }
        }
    }

    /// Draws the cast round `center`. `read(k, i)` gives field `i` of piece `k`; `veil` multiplies
    /// every opacity; `clock` is the time for the bobbing, the breathing and the sparkles (nil: none).
    static func paint(_ context: inout GraphicsContext, read: (Int, Int) -> Double, veil: Double, clock: Double?, center: CGPoint) {
        guard veil > 0.003 else { return }
        let cx = center.x
        let cy = center.y
        let bobbing = clock == nil ? 0 : read(S.idle, 0)
        let breathing = clock == nil ? 0 : read(S.idle, 1)
        let time = clock ?? 0

        // The capsule, which is the beam in the last scene.
        block(
            &context, x: cx + read(S.capsule, 0), y: cy + read(S.capsule, 1), w: read(S.capsule, 2), h: read(S.capsule, 3), radius: read(S.capsule, 4),
            alpha: read(S.capsule, 5) * veil, a: read(S.capsule, 6), b: read(S.capsule, 7), tilt: read(S.capsule, 8)
        )

        // The diamond is in front of the beam and behind the wallet it drops into.
        let diamond = read(S.diamond, 5) * veil
        if diamond > 0.003 {
            for facet in S.diamondFacets {
                polygon(&context, facet, first: 1, x: cx + read(S.diamond, 0), y: cy + read(S.diamond, 1), scale: read(S.diamond, 2) / 2, color: facet[0], alpha: diamond)
            }
        }

        for n in 0..<(S.extras + S.tokens) {
            // The small discs first: the badges lie over them.
            let k = n < S.extras ? S.extra + n : S.token + n - S.extras
            let x = read(k, 0)
            let y = read(k, 1) - M.bob(k, amount: bobbing, clock: time)
            let size = M.breathe(x: x, y: y, amount: breathing, clock: time)
            let w = read(k, 2) * size
            let h = read(k, 3) * size
            let alpha = read(k, 5) * veil
            block(&context, x: cx + x, y: cy + y, w: w, h: h, radius: read(k, 4) * size, alpha: alpha, a: read(k, 6), b: read(k, 7), tilt: read(k, 8))
            if k >= S.token, k < S.token + S.tokens {
                let index = k - S.token
                glyph(&context, index, x: cx + x, y: cy + y, scale: min(w, h) * S.glyphScale, color: S.glyphWhite[index] ? 0xffffff : 0, alpha: alpha * read(k, 9))
            }
        }

        // The thin line inside the wallet.
        let outline = read(S.outline, 5) * veil
        if outline > 0.003 {
            let w = read(S.outline, 2)
            let h = read(S.outline, 3)
            let box = CGRect(x: cx + read(S.outline, 0) - w / 2, y: cy + read(S.outline, 1) - h / 2, width: w, height: h)
            context.stroke(Path(roundedRect: box, cornerRadius: read(S.outline, 4), style: .circular), with: .color(color(Double(S.background), outline)), lineWidth: 1.5)
        }

        // The hero badge: a blue ring, a white disc and a black rounded square, in the proportions 78 : 52 : 18.
        let hero = read(S.hero, 5) * veil
        if hero > 0.003 {
            let x = cx + read(S.hero, 0)
            let y = cy + read(S.hero, 1)
            let unit = read(S.hero, 2) / 78
            context.stroke(Path(ellipseIn: CGRect(x: x - 36 * unit, y: y - 36 * unit, width: 72 * unit, height: 72 * unit)), with: .color(color(Double(S.heroRing), hero)), lineWidth: 6 * unit)
            context.fill(Path(ellipseIn: CGRect(x: x - 26 * unit, y: y - 26 * unit, width: 52 * unit, height: 52 * unit)), with: .color(.white.opacity(hero)))
            let square = CGRect(x: x - 9 * unit, y: y - 9 * unit, width: 18 * unit, height: 18 * unit)
            context.fill(Path(roundedRect: square, cornerRadius: 4 * unit, style: .circular), with: .color(.black.opacity(hero)))
        }

        for j in 0..<S.sparks {
            let alpha = read(S.spark + j, 5) * veil
            if alpha <= 0.003 { continue }
            let size = read(S.spark + j, 2) / 2 * (clock == nil ? 1 : M.pulse(j, clock: time))
            polygon(&context, S.sparkShape, first: 0, x: cx + read(S.spark + j, 0), y: cy + read(S.spark + j, 1), scale: size, color: Double(S.sparkle), alpha: alpha)
        }
    }
}
