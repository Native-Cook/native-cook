import SwiftUI

/// The departure board: the four stat numbers, the photo strip, its pager dots and the photo count.
/// At rest it shows what the pin shows; a city change turns every digit and photo into a split flap;
/// a swipe turns the strip into a 3D cover-flow.
struct TasteProfileBoard: View {
    typealias M = TasteProfileMotion
    typealias Data = TasteProfileBoardData

    let frame: TasteProfileFrame
    let geo: TasteProfileGeo

    private var c: M.Change { frame.change }

    var body: some View {
        ZStack(alignment: .topLeading) {
            ForEach(0..<4, id: \.self) { tile in
                if tile != frame.hold.tile {
                    TasteProfileNumber(from: Data.statGlyphs[tile][c.from], to: Data.statGlyphs[tile][c.to],
                                       local: c.from == c.to ? -1 : c.tau - M.Board.digitDelay - Double(tile) * M.Board.digitTileStagger,
                                       slotStagger: M.Board.digitSlotStagger, step: M.Board.digitStep, card: M.Board.digitCard,
                                       reduced: c.reduced, tau: c.tau, alignRight: false,
                                       run: TasteProfileTheme.GlyphToken.statNumbers[tile], geo: geo)
                }
            }
            TasteProfileNumber(from: Data.countGlyphs[c.from], to: Data.countGlyphs[c.to],
                               local: c.from == c.to ? -1 : c.tau - M.Board.countDelay,
                               slotStagger: M.Board.countSlotStagger, step: M.Board.countStep, card: M.Board.countCard,
                               reduced: c.reduced, tau: c.tau, alignRight: true, run: Data.countRun, geo: geo)
            strip
        }
        .frame(width: geo.width, height: geo.height, alignment: .topLeading)
        .allowsHitTesting(false)
    }

    private func lenOf(_ city: Int) -> Double { Double(max(1, M.stripPhotos[city].count - 4)) }

    @ViewBuilder
    private var strip: some View {
        let changing = c.tau >= 0 && c.tau < M.settle && (c.from != c.to || c.x0 != 0)
        if c.tau < 0 {
            TasteProfileStrip(city: c.from, x: c.x0, k: 0, geo: geo)
            TasteProfileDots(index: c.x0 / lenOf(c.from) * 3, geo: geo)
        } else if changing && !c.reduced {
            TasteProfileStripFlaps(change: c, geo: geo)
            TasteProfileDots(index: c.x0 / lenOf(c.from) * 3 * (1 - M.easeInOut(M.clamp01(c.tau / 0.5))), geo: geo)
        } else if c.reduced && c.tau < M.Reduced.fade && (c.from != c.to || c.x0 != 0) {
            let half = c.tau < M.Reduced.fade / 2
            TasteProfileStrip(city: half ? c.from : c.to, x: half ? c.x0 : 0, k: 0, geo: geo)
                .opacity(M.reducedDip(c.tau))
            TasteProfileDots(index: half ? c.x0 / lenOf(c.from) * 3 : 0, geo: geo)
        } else {
            TasteProfileStrip(city: c.to, x: frame.sx, k: frame.sk, geo: geo)
            TasteProfileDots(index: frame.sx / lenOf(c.to) * 3, geo: geo)
        }
    }
}

/// Geometry of the strip in points.
private struct TasteProfileStripRect {
    let x: Double
    let y: Double
    let w: Double
    let h: Double
    let radius: Double
    var cell: Double { w / 4 }

    init(_ geo: TasteProfileGeo) {
        let box = TasteProfileTheme.Box.strip
        x = geo.p(box.x)
        y = geo.y(box.y)
        w = geo.p(box.w)
        h = geo.p(box.h)
        radius = geo.p(TasteProfileTheme.Radius.photo)
    }
}

private struct TasteProfileStripBorder: View {
    let rect: TasteProfileStripRect
    let geo: TasteProfileGeo
    let alpha: Double

    var body: some View {
        if alpha > 0 {
            RoundedRectangle(cornerRadius: rect.radius)
                .strokeBorder(Color.white.opacity(0.55 * alpha), lineWidth: max(1, geo.p(2)))
                .frame(width: rect.w, height: rect.h)
                .offset(x: rect.x, y: rect.y)
        }
    }
}

/// The strip flat or as a cover-flow: a city's photos from offset `x` (photos), separation `k`.
struct TasteProfileStrip: View {
    typealias M = TasteProfileMotion

    let city: Int
    let x: Double
    let k: Double
    let geo: TasteProfileGeo

    var body: some View {
        let rect = TasteProfileStripRect(geo)
        let grow = M.Cover.grow * geo.scale * k
        let list = M.stripPhotos[city]
        ZStack(alignment: .topLeading) {
            if k < 0.0005 {
                flat(rect, list: list)
            } else {
                coverFlow(rect, list: list, grow: grow)
            }
        }
        .frame(width: rect.w + grow * 6, height: rect.h + grow * 2, alignment: .topLeading)
        .clipShape(RoundedRectangle(cornerRadius: rect.radius))
        .offset(x: rect.x - grow * 3, y: rect.y - grow)
        TasteProfileStripBorder(rect: rect, geo: geo, alpha: 1 - k)
    }

    @ViewBuilder
    private func flat(_ rect: TasteProfileStripRect, list: [Int]) -> some View {
        let base = Int(floor(x))
        let frac = x - Double(base)
        let valid = (-1...4).filter { base + $0 >= 0 && base + $0 < list.count }
        ForEach(valid, id: \.self) { i in
            Image(uiImage: TasteProfileAssets.photos[list[base + i]])
                .resizable()
                .scaledToFill()
                .frame(width: rect.cell, height: rect.h)
                .clipped()
                .offset(x: (Double(i) - frac) * rect.cell)
        }
    }

    @ViewBuilder
    private func coverFlow(_ rect: TasteProfileStripRect, list: [Int], grow: Double) -> some View {
        let first = max(0, Int(floor(x)) - 2)
        let last = min(list.count - 1, Int(ceil(x)) + 5)
        // Farthest first, so the centre photos are drawn over their neighbours.
        let order = Array(first...max(first, last)).sorted { abs(Double($1) - x - 1.5) < abs(Double($0) - x - 1.5) }
        ForEach(order, id: \.self) { i in
            card(i, photo: list[i], rect: rect, grow: grow)
        }
    }

    private func card(_ i: Int, photo: Int, rect: TasteProfileStripRect, grow: Double) -> some View {
        let o = Double(i) - x - 1.5
        let ao = min(abs(o), M.Cover.span)
        let centreX = rect.w / 2 + o * rect.cell * (1 - M.Cover.squeeze * k * (ao / M.Cover.span))
        let turn = (o == 0 ? 0 : (o > 0 ? -1.0 : 1.0)) * M.Cover.rotate * k * ao / M.Cover.span
        let scale = 1 - M.Cover.shrink * k * (ao / M.Cover.span)
        let width = rect.cell * (1 - M.Cover.inset * k)
        let radius = rect.radius * M.Cover.radius * k
        let dim = M.Cover.dim * k * (ao / M.Cover.span)
        return ZStack {
            Image(uiImage: TasteProfileAssets.photos[photo])
                .resizable()
                .scaledToFill()
                .frame(width: rect.cell, height: rect.h)
                .clipped()
            Color.black.opacity(dim)
        }
        .frame(width: width, height: rect.h)
        .clipShape(RoundedRectangle(cornerRadius: radius))
        .frame(width: rect.cell, height: rect.h)
        .scaleEffect(scale)
        .projectionEffect(TasteProfileProjection.turn(deg: turn, cx: rect.cell / 2, cy: rect.h / 2, d: M.Board.perspPhoto * 1.2))
        .offset(x: grow * 3 + centreX - rect.cell / 2, y: grow)
    }
}

/// The strip as a departure board: four cells flapping from (from, x0) to (to, 0).
struct TasteProfileStripFlaps: View {
    typealias M = TasteProfileMotion

    let change: M.Change
    let geo: TasteProfileGeo

    var body: some View {
        let rect = TasteProfileStripRect(geo)
        let toList = M.stripPhotos[change.to]
        let fromList = M.stripPhotos[change.from]
        ZStack(alignment: .topLeading) {
            ForEach(0..<4, id: \.self) { i in
                let seq0 = fromList[max(0, min(fromList.count - 1, Int(change.x0) + i))]
                let seq3 = toList[max(0, min(toList.count - 1, i))]
                let local = change.tau - M.Board.photoDelay - Double(i) * M.Board.photoCellStagger
                let steps = seq0 == seq3 ? 0 : M.Board.photoSteps
                let f = M.flapAt(local: local, n: steps, step: M.Board.photoStep)
                let mid1 = toList[(i + 4) % toList.count]
                let mid2 = toList[(i + 2) % toList.count]
                let seqAt: (Int) -> Int = { $0 <= 0 ? seq0 : $0 == 1 ? mid1 : $0 == 2 ? mid2 : seq3 }
                let seam = steps == 0 ? 0.0
                    : local < 0 ? M.clamp01((local + 0.1) / 0.1)
                    : local < Double(steps) * M.Board.photoStep ? 1.0
                    : 1 - M.clamp01((local - Double(steps) * M.Board.photoStep) / 0.25)
                TasteProfileCell(a: .photo(steps == 0 ? seq3 : seqAt(f.k)), b: .photo(seqAt(min(f.k + 1, steps))),
                                 x: Double(i) * rect.cell, y: 0, w: rect.cell, h: rect.h,
                                 deg: f.angle, speed: f.angle >= 0 ? f.speed : f.bounce, persp: M.Board.perspPhoto,
                                 cardAlpha: 1, seam: seam, run: TasteProfileTheme.GlyphToken.t125, cardRadius: 0, geo: geo)
            }
        }
        .frame(width: rect.w, height: rect.h, alignment: .topLeading)
        .clipShape(RoundedRectangle(cornerRadius: rect.radius))
        .offset(x: rect.x, y: rect.y)
        TasteProfileStripBorder(rect: rect, geo: geo, alpha: 1)
    }
}

/// The pager dots: the active one (continuous index 0…3) is dark, the others the pin's greys.
struct TasteProfileDots: View {
    let index: Double
    let geo: TasteProfileGeo

    var body: some View {
        Canvas { context, _ in
            for (i, dot) in TasteProfileTheme.Box.dots.enumerated() {
                let k = TasteProfileMotion.clamp01(1 - abs(index - Double(i)))
                let rect = CGRect(x: geo.p(dot.cx - dot.d / 2), y: geo.y(TasteProfileTheme.Box.dotsY - dot.d / 2),
                                  width: geo.p(dot.d), height: geo.p(dot.d))
                if k < 0.999 {
                    let base = i == 0 ? TasteProfileTheme.ColorToken.dotOff : dot.color
                    context.fill(Path(ellipseIn: rect), with: .color(base))
                }
                if k > 0.001 {
                    context.fill(Path(ellipseIn: rect), with: .color(TasteProfileTheme.ColorToken.dotOn.opacity(min(1, k))))
                }
            }
        }
        .frame(width: geo.width, height: geo.height)
    }
}
