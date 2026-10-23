import SwiftUI

/// The held tile grown into a bar chart for the shown city: a light veil over the screen, the tile's card
/// growing to two tiles by three (its header stays where it was), six months of bars rising in a stagger,
/// the highlighted bar following the finger.
struct TasteProfileChart: View {
    typealias M = TasteProfileMotion
    typealias C = TasteProfileTheme.Chart

    let hold: M.HoldView
    let tile: Int
    let city: Int
    let reduced: Bool
    let geo: TasteProfileGeo

    /// The card's rect in ref px: tiles 0–2 grow to the right, the last one to the left.
    static func rect(tile: Int) -> (x: Double, y: Double, w: Double, h: Double) {
        let x0 = TasteProfileTheme.Box.tiles[tile]
        return (tile == 3 ? x0 + TasteProfileTheme.Box.tileW - C.w : x0, TasteProfileTheme.Box.tileY, C.w, C.h)
    }

    private func barX(_ i: Int) -> Double {
        let gap = (C.w - 2 * C.pad - 6 * C.barW) / 5
        return C.pad + Double(i) * (C.barW + gap)
    }

    var body: some View {
        let box = TasteProfileTheme.Box.self
        let r = Self.rect(tile: tile)
        let e = reduced ? (hold.e > 0 ? 1.0 : 0.0) : hold.e
        let left = M.lerp(box.tiles[tile], r.x, e)
        let top = M.lerp(geo.y(box.tileY), geo.y(r.y), e)
        let width = M.lerp(box.tileW, r.w, e)
        let height = M.lerp(box.tileH, r.h, e)
        let scale = 1 - 0.03 * hold.press
        let cardOpacity = reduced && hold.press == 0 ? hold.e : 1
        ZStack(alignment: .topLeading) {
            ZStack {
                TasteProfileBlur(intensity: 14)
                C.veil
            }
            .frame(width: geo.width, height: geo.height)
            .opacity(hold.e)
            RoundedRectangle(cornerRadius: geo.p(TasteProfileTheme.Radius.tile))
                .fill(TasteProfileTheme.ColorToken.tileFill)
                .shadow(color: .black.opacity(M.Expand.shadowTo), radius: 16 * geo.pointScale, y: 6 * geo.pointScale)
                .frame(width: geo.p(width), height: geo.p(height))
                .scaleEffect(scale)
                .opacity(reduced ? hold.e : M.clamp01(hold.e))
                .offset(x: geo.p(left), y: top)
            RoundedRectangle(cornerRadius: geo.p(TasteProfileTheme.Radius.tile))
                .fill(TasteProfileTheme.ColorToken.tileFill)
                .overlay {
                    RoundedRectangle(cornerRadius: geo.p(TasteProfileTheme.Radius.tile))
                        .strokeBorder(TasteProfileTheme.ColorToken.tileHairline, lineWidth: max(1, geo.p(2.4)))
                }
                .shadow(color: .black.opacity(M.Expand.shadowFrom), radius: 4 * geo.pointScale, y: 2 * geo.pointScale)
                .frame(width: geo.p(width), height: geo.p(height))
                .scaleEffect(scale)
                .opacity(cardOpacity)
                .offset(x: geo.p(left), y: top)
            header
            content(r)
        }
        .frame(width: geo.width, height: geo.height, alignment: .topLeading)
        .allowsHitTesting(false)
    }

    /// The tile's own icon, number and label, standing in for the tile while it is held.
    private var header: some View {
        let box = TasteProfileTheme.Box.self
        let run = TasteProfileTheme.GlyphToken.statNumbers[tile]
        let glyphs = TasteProfileBoardData.statGlyphs[tile][city]
        let art = TasteProfileArt(geo: geo)
        let centre = CGPoint(x: geo.p(box.tiles[tile] + box.tileW / 2), y: geo.y(box.tileY + box.tileH / 2))
        return Canvas { context, _ in
            art.drawIcon(tile, in: &context)
            for glyph in glyphs {
                art.drawGlyph(glyph, run: run, in: &context)
            }
            art.drawLabel(TasteProfileTheme.tileLabels[tile], in: &context)
        }
        .frame(width: geo.width, height: geo.height)
        .scaleEffect(1 - 0.03 * hold.press, anchor: UnitPoint(x: centre.x / geo.width, y: centre.y / geo.height))
    }

    private func content(_ r: (x: Double, y: Double, w: Double, h: Double)) -> some View {
        let bars = M.monthBars(city: city, tile: tile)
        let maxValue = Double(bars.max() ?? 1)
        let cityName = M.cities[city] == "all" ? "all cities" : M.cities[city]
        let fs = geo.pointScale
        return ZStack(alignment: .topLeading) {
            Text("last 6 months · \(cityName)")
                .font(.custom(TasteProfileTheme.FontToken.body, fixedSize: 10.2 * fs))
                .foregroundStyle(C.caption)
                .fixedSize()
                .offset(x: geo.p(C.pad), y: geo.p(C.captionBaseline - 30))
            ForEach(0..<6, id: \.self) { i in
                bar(i, value: bars[i], maxValue: maxValue)
            }
        }
        .frame(width: geo.p(r.w), height: geo.p(r.h), alignment: .topLeading)
        .opacity(M.clamp01((hold.e - 0.45) / 0.4))
        .offset(x: geo.p(r.x), y: geo.y(r.y) + (1 - hold.e) * 24 * geo.scale)
        .frame(width: geo.width, height: geo.height, alignment: .topLeading)
    }

    private func bar(_ i: Int, value: Int, maxValue: Double) -> some View {
        let left = barX(i)
        let h = max(6, Double(value) / maxValue * C.barMax)
        let room = h + 24
        let b = M.Expand.barDelay + Double(i) * M.Expand.barStagger
        let p = reduced ? 1 : M.spring(hold.since - b, M.Expand.barSpring)
        let on = M.clamp01(1 - abs(hold.hl - Double(i)))
        let labelOn = M.clamp01(1 - abs(hold.hl - Double(i)) * 1.6)
        let fs = geo.pointScale
        let u = geo.scale
        return ZStack(alignment: .topLeading) {
            ZStack(alignment: .bottomLeading) {
                RoundedRectangle(cornerRadius: geo.p(C.barW / 2.6))
                    .fill(mix(on))
                    .frame(width: geo.p(C.barW), height: geo.p(h))
                    .offset(y: (1 - p) * (h + 4) * u)
            }
            .frame(width: geo.p(C.barW), height: geo.p(room), alignment: .bottomLeading)
            .clipped()
            .offset(x: geo.p(left), y: geo.p(C.barsBottom - room))
            Text("\(value)")
                .font(.custom(TasteProfileTheme.FontToken.extra, fixedSize: 12.3 * fs))
                .monospacedDigit()
                .foregroundStyle(Color.black)
                .frame(width: geo.p(C.barW + 40))
                .fixedSize(horizontal: false, vertical: true)
                .opacity(labelOn * M.clamp01(p * 1.4 - 0.4))
                .offset(x: geo.p(left - 20), y: geo.p(C.barsBottom - h - C.valueGap - 34) + (1 - p) * (h + 4) * u)
            Text(M.months[i])
                .font(.custom(TasteProfileTheme.FontToken.body, fixedSize: 10.2 * fs))
                .foregroundStyle(C.caption)
                .frame(width: geo.p(C.barW + 40))
                .fixedSize(horizontal: false, vertical: true)
                .offset(x: geo.p(left - 20), y: geo.p(C.monthBaseline - 28))
        }
    }

    private func mix(_ t: Double) -> Color {
        let a = (r: 0xDC / 255.0, g: 0xDC / 255.0, b: 0xDC / 255.0)
        return Color(.sRGB, red: a.r * (1 - t), green: a.g * (1 - t), blue: a.b * (1 - t))
    }
}
