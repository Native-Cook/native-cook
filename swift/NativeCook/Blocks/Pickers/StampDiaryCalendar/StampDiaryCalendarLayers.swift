import SwiftUI

/// Reference points -> screen points of the current layout.
struct StampDiaryCalendarSpace: Equatable {
    var size: CGSize
    var s: Double
    var oy: Double

    func x(_ ref: Double) -> Double { ref * s }
    func y(_ ref: Double) -> Double { oy + ref * s }
}

/// One shader-drawn stamp: a view that only bounds the stamp on screen, drawn by the Metal effect.
struct StampDiaryCalendarShaderStamp: View {
    typealias M = StampDiaryCalendarMotion

    var space: StampDiaryCalendarSpace
    var projection: StampDiaryCalendarProjection
    var halfWidth: Double
    var halfHeight: Double
    /// Extra screen margin for a blur applied on top.
    var margin = 0.0
    var art: Image
    var artSide: Double
    var mark: Image
    var w: Double
    var h: Double
    var aa: Double
    var dim: Double
    var holes: (across: Int, down: Int)
    var shadow = 0.0
    var curl = M.flat
    var sheenPos = 0.0
    var sheenAmt = 0.0
    var inkWet = 0.0
    var inkOn = 0.0
    var inkSpread = 0.0
    var inkThreshold = 1.0

    var body: some View {
        let rect = projection.bounds(halfWidth: halfWidth, halfHeight: halfHeight)
        let frame = CGRect(
            x: space.x(rect.minX) - margin - 1, y: space.y(rect.minY) - margin - 1,
            width: rect.width * space.s + 2 * margin + 2, height: rect.height * space.s + 2 * margin + 2
        )
        let rows = projection.inverseRows()
        let crop = Self.crop(w: w, h: h, side: artSide)
        let P = StampDiaryCalendarPostmarkSpec.self
        let k = P.outer / (StampDiaryCalendarInkMark.radius * w)
        let shader = ShaderLibrary.stampDiaryCalendarStamp(
            .float4(Float(frame.minX), Float(frame.minY - space.oy), Float(space.s), 0),
            .float3(rows[0].x, rows[0].y, rows[0].z),
            .float3(rows[1].x, rows[1].y, rows[1].z),
            .float3(rows[2].x, rows[2].y, rows[2].z),
            .float4(Float(w), Float(h), Float(aa), Float(dim)),
            .float4(Float(crop.x), Float(crop.y), Float(crop.z), Float(crop.w)),
            .float4(Float(holes.across), Float(holes.down), Float(StampDiaryCalendarTheme.perforationRadius), Float(shadow)),
            .float4(Float(curl.nx), Float(curl.ny), Float(curl.a), Float(curl.r)),
            .float4(Float(sheenPos), Float(sheenAmt), Float(inkWet), Float(inkOn)),
            .float4(Float(StampDiaryCalendarInkMark.x * w), Float(StampDiaryCalendarInkMark.y * h), Float(k), Float(StampDiaryCalendarInkMark.rotation)),
            .float4(Float(inkSpread), Float(inkThreshold), Float(P.ringX), Float(P.ringY)),
            .float2(Float(halfWidth), Float(halfHeight)),
            .image(art),
            .image(mark)
        )
        Color.white
            .frame(width: frame.width, height: frame.height)
            .colorEffect(shader)
            .position(x: frame.midX, y: frame.midY)
    }

    /// The part of the art the stamp shows (cover fit), in image pixels.
    static func crop(w: Double, h: Double, side: Double) -> SIMD4<Double> {
        if w < h {
            let cw = side * w / h
            return SIMD4((side - cw) / 2, 0, cw, side)
        }
        let ch = side * h / w
        return SIMD4(0, (side - ch) / 2, side, ch)
    }
}

/// The blurred drop shadow of the moving stamp.
struct StampDiaryCalendarShadow: View {
    var space: StampDiaryCalendarSpace
    var projection: StampDiaryCalendarProjection
    var w: Double
    var h: Double
    var sigma: Double
    var opacity: Double

    var body: some View {
        let halfW = w / 2 + 3.2 * sigma
        let halfH = h / 2 + 3.2 * sigma
        let rect = projection.bounds(halfWidth: halfW, halfHeight: halfH)
        let frame = CGRect(x: space.x(rect.minX) - 1, y: space.y(rect.minY) - 1, width: rect.width * space.s + 2, height: rect.height * space.s + 2)
        let rows = projection.inverseRows()
        let shader = ShaderLibrary.stampDiaryCalendarShadow(
            .float4(Float(frame.minX), Float(frame.minY - space.oy), Float(space.s), 0),
            .float3(rows[0].x, rows[0].y, rows[0].z),
            .float3(rows[1].x, rows[1].y, rows[1].z),
            .float3(rows[2].x, rows[2].y, rows[2].z),
            .float4(Float(w), Float(h), Float(sigma), Float(opacity))
        )
        Color.white
            .frame(width: frame.width, height: frame.height)
            .colorEffect(shader)
            .position(x: frame.midX, y: frame.midY)
    }
}

/// The collage: seven stamps, the two at the bottom soft (depth of field).
struct StampDiaryCalendarPile: View {
    typealias M = StampDiaryCalendarMotion

    var space: StampDiaryCalendarSpace
    var dim: Double
    var nudge: Double
    var landing: StampDiaryCalendarQuad

    var body: some View {
        let T = StampDiaryCalendarTheme.self
        let clip = CGRect(x: space.x(-60), y: space.y(-120), width: space.x(T.refWidth + 120), height: (T.heroBottom + 120) * space.s)
        let lx = landing.x + landing.w / 2
        let ly = landing.y + landing.h / 2
        ZStack {
            ForEach(Array(StampDiaryCalendarData.pile.enumerated()), id: \.offset) { _, item in
                if item.blurBottom <= 0 {
                    stamp(item, blur: 0)
                } else {
                    // Depth of field: soft at the top, softer towards the bottom of the hero.
                    let top = item.quad.y + item.quad.h * item.soft
                    ZStack {
                        stamp(item, blur: item.blurTop)
                        stamp(item, blur: item.blurBottom)
                            .mask { fade(from: top) }
                    }
                }
            }
        }
        .frame(width: space.size.width, height: space.size.height, alignment: .topLeading)
        .mask { Path(clip).fill(Color.black) }
        .scaleEffect(1 - nudge, anchor: UnitPoint(x: space.x(lx) / space.size.width, y: space.y(ly) / space.size.height))
        .allowsHitTesting(false)
    }

    private func stamp(_ item: StampDiaryCalendarPileStamp, blur: Double) -> some View {
        let q = item.quad
        return StampDiaryCalendarShaderStamp(
            space: space,
            projection: .quad(q),
            halfWidth: q.w / 2 + 2, halfHeight: q.h / 2 + 2,
            margin: blur * space.s * 3.2,
            art: StampDiaryCalendarArt.image(item.art), artSide: StampDiaryCalendarArt.size,
            mark: StampDiaryCalendarArt.blank,
            w: q.w, h: q.h, aa: 0.7 / (3 * space.s), dim: dim,
            holes: M.holesOfQuad(q)
        )
        .blur(radius: blur * space.s)
    }

    /// Opaque from `top` (reference y) down, fading in over 50 pt; clear above.
    private func fade(from top: Double) -> some View {
        let T = StampDiaryCalendarTheme.self
        let height = (T.heroBottom - top + 60) * space.s
        return Rectangle()
            .fill(LinearGradient(
                stops: [.init(color: .clear, location: 0), .init(color: .black, location: min(1, 50 * space.s / height))],
                startPoint: .top, endPoint: .bottom
            ))
            .frame(width: space.size.width + 120 * space.s, height: height)
            .position(x: space.x(StampDiaryCalendarTheme.refWidth / 2), y: space.y(top) + height / 2)
            .frame(width: space.size.width, height: space.size.height, alignment: .topLeading)
    }
}
