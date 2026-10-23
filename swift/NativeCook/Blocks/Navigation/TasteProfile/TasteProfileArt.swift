import SwiftUI

/// Drawing helpers of the screen (everything but the board): one method per piece, in the pin's ref px.
struct TasteProfileArt {
    let geo: TasteProfileGeo
    var frame = TasteProfileFrame(change: TasteProfileMotion.loopChange(0), sx: 0, sk: 0, hold: .init(),
                                  followProgress: 0, followPress: 0, following: false)

    var width: Double { geo.width }
    var scale: Double { geo.scale }
    var pointScale: Double { geo.pointScale }
    var y0: Double { geo.y0 }
    var displayScale: Double { geo.pixel }

    func drawStatic(in context: inout GraphicsContext) {
        drawChevron(in: &context)
        drawNavPill(TasteProfileTheme.Box.send, in: &context)
        drawSend(in: &context)
        drawNavPill(TasteProfileTheme.Box.share, in: &context)
        drawShare(in: &context)

        drawLine(TasteProfileTheme.TextToken.name, in: &context)
        drawLine(TasteProfileTheme.TextToken.handle, in: &context)
        drawGlyphRun(TasteProfileTheme.GlyphToken.n39, in: &context)
        drawLine(TasteProfileTheme.TextToken.following, in: &context)
        drawLine(TasteProfileTheme.TextToken.mutual2, in: &context)
        drawLine(TasteProfileTheme.TextToken.mutual, in: &context)
        drawLine(TasteProfileTheme.TextToken.friends, in: &context)
        drawLine(TasteProfileTheme.TextToken.rankB, in: &context)
        drawLine(TasteProfileTheme.TextToken.weekA, in: &context)
        drawLine(TasteProfileTheme.TextToken.weekB, in: &context)
        drawPin(in: &context)
        drawLine(TasteProfileTheme.TextToken.location, in: &context)
        drawLine(TasteProfileTheme.TextToken.bio, in: &context)
        drawLine(TasteProfileTheme.TextToken.viewAll, in: &context)
        drawLine(TasteProfileTheme.TextToken.photos, in: &context)
    }

    /// The stat tiles with their icons and labels; the held tile is drawn by the chart instead.
    func drawTilesLayer(skipping held: Int, in context: inout GraphicsContext) {
        drawTiles(in: &context, skipping: held)
        drawTileIcons(in: &context, skipping: held)
        for (tile, spec) in TasteProfileTheme.tileLabels.enumerated() where tile != held { drawLine(spec, in: &context) }
    }

    func drawMotion(in context: inout GraphicsContext) {
        drawFollow(in: &context)
        drawFollowers(in: &context)
        drawCoin(in: &context)
        drawGem(in: &context)
        drawRankWord(in: &context)
        drawChips(in: &context)
    }

    func drawLine(_ spec: TasteProfileTheme.TextSpec, in context: inout GraphicsContext,
                          text: String? = nil, color: Color? = nil, x: Double? = nil,
                          yOffset: Double = 0, opacity: Double = 1) {
        guard opacity > 0.001 else { return }
        let fontSize = spec.size * pointScale
        let label = Text(text ?? spec.text)
            .font(.custom(spec.font, fixedSize: fontSize))
            .tracking(spec.tracking * pointScale)
            .foregroundStyle(color ?? spec.color)
        var layer = context
        layer.opacity = opacity
        layer.draw(label, at: CGPoint(x: p(x ?? spec.x), y: y(spec.baseline) - TasteProfileTheme.ascent * fontSize + p(yOffset)), anchor: .topLeading)
    }

    func drawGlyphRun(_ run: TasteProfileTheme.GlyphRun, in context: inout GraphicsContext,
                              glyphs: [TasteProfileTheme.Glyph]? = nil, yOffset: Double = 0,
                              opacity: Double = 1, color: Color? = nil, clipOrigin: (x: Double, y: Double)? = nil) {
        for glyph in glyphs ?? run.glyphs {
            drawGlyph(glyph, run: run, in: &context, yOffset: yOffset, opacity: opacity, color: color, clipOrigin: clipOrigin)
        }
    }

    func drawGlyph(_ glyph: TasteProfileTheme.Glyph, run: TasteProfileTheme.GlyphRun,
                           in context: inout GraphicsContext, yOffset: Double = 0, opacity: Double = 1,
                           color: Color? = nil, clipOrigin: (x: Double, y: Double)? = nil) {
        guard opacity > 0.001 else { return }
        let ox = clipOrigin?.x ?? 0
        let oy = clipOrigin?.y ?? 0
        if glyph.character == "1" {
            let x = p(glyph.inkLeft - ox)
            let top = y(run.baseline - run.digitH - oy + yOffset)
            let w = p(glyph.inkWidth)
            let h = p(run.digitH)
            var path = Path()
            path.move(to: CGPoint(x: x + w * 0.9706, y: top))
            path.addLine(to: CGPoint(x: x + w * 0.4706, y: top))
            path.addLine(to: CGPoint(x: x, y: top + h * 0.125))
            path.addLine(to: CGPoint(x: x, y: top + h * 0.3611))
            path.addLine(to: CGPoint(x: x + w * 0.5, y: top + h * 0.3611))
            path.addLine(to: CGPoint(x: x + w * 0.5, y: top + h))
            path.addLine(to: CGPoint(x: x + w * 0.9706, y: top + h))
            path.closeSubpath()
            context.fill(path, with: .color((color ?? run.color).opacity(opacity)))
            return
        }
        guard let metric = TasteProfileTheme.GlyphToken.em[glyph.character] else { return }
        let fontSize = run.size * pointScale
        let horizontalScale = glyph.inkWidth / (metric.width * run.size * 3)
        let left = p(glyph.inkLeft - ox) - metric.bearing * fontSize * horizontalScale
        let top = y(run.baseline - oy + yOffset) - TasteProfileTheme.ascent * fontSize
        let label = Text(String(glyph.character)).font(.custom(run.font, fixedSize: fontSize)).foregroundStyle(color ?? run.color)
        var layer = context
        layer.opacity = opacity
        layer.translateBy(x: left, y: top)
        layer.scaleBy(x: horizontalScale, y: 1)
        layer.draw(label, at: .zero, anchor: .topLeading)
    }

    func drawChevron(in context: inout GraphicsContext) {
        let box = TasteProfileTheme.Box.chevron
        var path = Path()
        let inset = TasteProfileTheme.Box.chevronStroke / 2
        path.move(to: CGPoint(x: p(box.x + box.w - inset), y: y(box.y + inset)))
        path.addLine(to: CGPoint(x: p(box.x + inset), y: y(box.y + box.h / 2)))
        path.addLine(to: CGPoint(x: p(box.x + box.w - inset), y: y(box.y + box.h - inset)))
        context.stroke(path, with: .color(.black), style: StrokeStyle(lineWidth: p(TasteProfileTheme.Box.chevronStroke), lineCap: .round, lineJoin: .round))
    }

    func drawNavPill(_ box: TasteProfileTheme.BoxToken, in context: inout GraphicsContext,
                             x: Double? = nil, width: Double? = nil, pressScale: Double = 1) {
        let actualX = x ?? box.x
        let actualWidth = width ?? box.w
        let rect = CGRect(x: p(actualX), y: y(box.y), width: p(actualWidth), height: p(box.h))
        let center = CGPoint(x: rect.midX, y: rect.midY)
        var scaled = context
        scaled.translateBy(x: center.x, y: center.y)
        scaled.scaleBy(x: pressScale, y: pressScale)
        scaled.translateBy(x: -center.x, y: -center.y)
        let path = Path(roundedRect: rect, cornerRadius: p(TasteProfileTheme.Radius.nav))
        scaled.drawLayer { shadow in
            shadow.addFilter(.shadow(color: .black.opacity(0.1), radius: 5 * pointScale, x: 0, y: 3 * pointScale))
            shadow.fill(path, with: .color(TasteProfileTheme.ColorToken.chipFill))
        }
        scaled.fill(path, with: .color(TasteProfileTheme.ColorToken.chipFill))
        scaled.fill(path, with: .linearGradient(Gradient(stops: [
            .init(color: .clear, location: 0), .init(color: .black.opacity(0.03), location: 0.6), .init(color: .black.opacity(0.06), location: 1),
        ]), startPoint: CGPoint(x: rect.midX, y: rect.minY), endPoint: CGPoint(x: rect.midX, y: rect.maxY)))
        scaled.stroke(path, with: .color(TasteProfileTheme.ColorToken.hairline), lineWidth: max(1, p(1.6)))
    }

    func drawSend(in context: inout GraphicsContext) {
        let box = TasteProfileTheme.Box.sendIcon
        var path = Path()
        path.move(to: local(2.5, 16, box))
        path.addLine(to: local(39.5, 2.5, box))
        path.addLine(to: local(26, 39.5, box))
        path.addLine(to: local(18.5, 22.5, box))
        path.closeSubpath()
        path.move(to: local(39.5, 2.5, box))
        path.addLine(to: local(18.5, 22.5, box))
        context.stroke(path, with: .color(.black), style: StrokeStyle(lineWidth: p(5), lineCap: .round, lineJoin: .round))
    }

    func drawShare(in context: inout GraphicsContext) {
        let box = TasteProfileTheme.Box.shareIcon
        var path = Path()
        path.move(to: local(2.5, 16, box))
        path.addLine(to: local(2.5, 33, box))
        path.addCurve(to: local(10, 40.5, box), control1: local(2.5, 37.15, box), control2: local(5.85, 40.5, box))
        path.addLine(to: local(33, 40.5, box))
        path.addCurve(to: local(40.5, 33, box), control1: local(37.15, 40.5, box), control2: local(40.5, 37.15, box))
        path.addLine(to: local(40.5, 16, box))
        path.move(to: local(21.5, 28, box)); path.addLine(to: local(21.5, 3.5, box))
        path.move(to: local(12, 12.5, box)); path.addLine(to: local(21.5, 3, box)); path.addLine(to: local(31, 12.5, box))
        context.stroke(path, with: .color(.black), style: StrokeStyle(lineWidth: p(5), lineCap: .round, lineJoin: .round))
    }

    func drawPin(in context: inout GraphicsContext) {
        let box = TasteProfileTheme.Box.pin
        var path = Path()
        path.move(to: local(21, 48.5, box))
        path.addCurve(to: local(3, 20.5, box), control1: local(11, 38, box), control2: local(3, 30, box))
        path.addArc(center: local(21, 20.5, box), radius: p(18), startAngle: .degrees(180), endAngle: .degrees(0), clockwise: false)
        path.addCurve(to: local(21, 48.5, box), control1: local(39, 30, box), control2: local(31, 38, box))
        path.closeSubpath()
        path.addEllipse(in: CGRect(x: p(box.x + 14.5), y: y(box.y + 14), width: p(13), height: p(13)))
        context.stroke(path, with: .color(TasteProfileTheme.ColorToken.pinIcon), style: StrokeStyle(lineWidth: p(4.5), lineCap: .round, lineJoin: .round))
    }

    func drawTiles(in context: inout GraphicsContext, skipping held: Int) {
        let xs = [TasteProfileTheme.Box.tiles[0] - (TasteProfileTheme.Box.tileW + 19)] + TasteProfileTheme.Box.tiles
        for (index, x) in xs.enumerated() where index - 1 != held {
            let rect = CGRect(x: p(x), y: y(TasteProfileTheme.Box.tileY), width: p(TasteProfileTheme.Box.tileW), height: p(TasteProfileTheme.Box.tileH))
            let path = Path(roundedRect: rect, cornerRadius: p(TasteProfileTheme.Radius.tile))
            context.drawLayer { shadow in
                shadow.addFilter(.shadow(color: .black.opacity(0.05), radius: 4 * pointScale, x: 0, y: 2 * pointScale))
                shadow.fill(path, with: .color(TasteProfileTheme.ColorToken.tileFill))
            }
            context.fill(path, with: .color(TasteProfileTheme.ColorToken.tileFill))
            context.stroke(path, with: .color(TasteProfileTheme.ColorToken.tileHairline), lineWidth: max(1, p(2.4)))
        }
    }

    func drawIcon(_ tile: Int, in context: inout GraphicsContext) {
        let box = TasteProfileTheme.Box.tileIcons[tile]
        switch tile {
        case 0: drawStar(box, in: &context)
        case 2: drawMeh(box, in: &context)
        default: drawThumb(box, down: tile == 3, in: &context)
        }
    }

    func drawTileIcons(in context: inout GraphicsContext, skipping held: Int) {
        for tile in 0..<4 where tile != held { drawIcon(tile, in: &context) }
    }

    func drawLabel(_ spec: TasteProfileTheme.TextSpec, in context: inout GraphicsContext) {
        drawLine(spec, in: &context)
    }

    func drawStar(_ box: TasteProfileTheme.BoxToken, in context: inout GraphicsContext) {
        var path = Path()
        for index in 0..<10 {
            let radius = index.isMultiple(of: 2) ? 18.5 : 9.3
            let angle = (-90 + Double(index) * 36) * .pi / 180
            let point = local(box.w / 2 + radius * cos(angle), 2.75 + 18.5 + radius * sin(angle), box)
            if index == 0 { path.move(to: point) } else { path.addLine(to: point) }
        }
        path.closeSubpath()
        context.stroke(path, with: .color(.black), style: StrokeStyle(lineWidth: p(5.5), lineCap: .round, lineJoin: .round))
    }

    func drawThumb(_ box: TasteProfileTheme.BoxToken, down: Bool, in context: inout GraphicsContext) {
        func point(_ x: Double, _ yy: Double) -> CGPoint { local(x, down ? box.h - yy : yy, box) }
        var path = Path()
        path.move(to: point(3, 18)); path.addLine(to: point(11, 18)); path.addLine(to: point(11, 37)); path.addLine(to: point(3, 37)); path.closeSubpath()
        path.move(to: point(11, 19)); path.addLine(to: point(17.5, 18.5)); path.addLine(to: point(20.5, 4.5))
        path.addCurve(to: point(24.5, 4.5), control1: point(21, 2.8), control2: point(23.5, 3))
        path.addCurve(to: point(25.5, 13.5), control1: point(26.5, 7.5), control2: point(26, 10.5))
        path.addLine(to: point(25, 16.5)); path.addLine(to: point(33.5, 16.5))
        path.addCurve(to: point(37, 21), control1: point(36, 16.5), control2: point(37.5, 18.5))
        path.addLine(to: point(35, 32.5)); path.addCurve(to: point(30, 37), control1: point(34.5, 35.5), control2: point(32.5, 37))
        path.addLine(to: point(11, 37))
        context.stroke(path, with: .color(.black), style: StrokeStyle(lineWidth: p(5.5), lineCap: .round, lineJoin: .round))
    }

    func drawMeh(_ box: TasteProfileTheme.BoxToken, in context: inout GraphicsContext) {
        let center = CGPoint(x: p(box.x + box.w / 2), y: y(box.y + box.h / 2))
        context.stroke(Path(ellipseIn: CGRect(x: center.x - p(box.w / 2 - 2.5), y: center.y - p(box.w / 2 - 2.5), width: p(box.w - 5), height: p(box.w - 5))),
                       with: .color(.black), style: StrokeStyle(lineWidth: p(5), lineCap: .round, lineJoin: .round))
        var mouth = Path(); mouth.move(to: CGPoint(x: center.x - p(6.5), y: center.y + p(7.5))); mouth.addLine(to: CGPoint(x: center.x + p(6.5), y: center.y + p(7.5)))
        context.stroke(mouth, with: .color(.black), style: StrokeStyle(lineWidth: p(5), lineCap: .round))
        for x in [-6.5, 6.5] { context.fill(Path(ellipseIn: CGRect(x: center.x + p(x - 2.6), y: center.y - p(6.6), width: p(5.2), height: p(5.2))), with: .color(.black)) }
    }

    func drawFollow(in context: inout GraphicsContext) {
        let progress = TasteProfileMotion.clamp01(frame.followProgress)
        let grow = TasteProfileMotion.Follow.grow * progress
        let pressScale = 1 - (1 - TasteProfileMotion.Press.scale) * frame.followPress
        let box = TasteProfileTheme.Box.follow
        drawNavPill(box, in: &context, x: box.x - grow, width: box.w + grow, pressScale: pressScale)

        let center = CGPoint(x: p(box.x + box.w / 2), y: y(box.y + box.h / 2))
        var layer = context
        layer.translateBy(x: center.x, y: center.y)
        layer.scaleBy(x: pressScale, y: pressScale)
        layer.translateBy(x: -center.x, y: -center.y)
        let contentShift = -grow
        drawUserMark(progress: progress, xShift: contentShift, in: &layer)
        drawLine(TasteProfileTheme.TextToken.follow, in: &layer, x: TasteProfileTheme.TextToken.follow.x + contentShift)
        let ingOpacity = TasteProfileMotion.clamp01(progress * 1.6 - 0.4)
        drawLine(TasteProfileTheme.TextToken.follow, in: &layer, text: "ing",
                 x: TasteProfileTheme.TextToken.followIngX + contentShift - 10 * (1 - progress), opacity: ingOpacity)
    }

    func drawUserMark(progress: Double, xShift: Double, in context: inout GraphicsContext) {
        let box = TasteProfileTheme.Box.followIcon
        func q(_ x: Double, _ yy: Double) -> CGPoint { CGPoint(x: p(box.x + xShift + x), y: y(box.y + yy)) }
        var path = Path()
        path.addEllipse(in: CGRect(x: p(box.x + xShift + 10.8), y: y(box.y + 2.6), width: p(12.4), height: p(12.4)))
        path.move(to: q(4.5, 37.5)); path.addLine(to: q(4.5, 34))
        path.addCurve(to: q(18.5, 27.5), control1: q(4.5, 28.5), control2: q(9, 27.5))
        path.addCurve(to: q(32.5, 34), control1: q(28, 27.5), control2: q(32.5, 28.5)); path.addLine(to: q(32.5, 37.5))
        func mix(_ a: Double, _ b: Double) -> Double { TasteProfileMotion.lerp(a, b, progress) }
        path.move(to: q(mix(34.7, 33.6), mix(19, 19.6))); path.addLine(to: q(mix(47, 38.8), mix(19, 25.4)))
        path.move(to: q(mix(40.9, 38.8), mix(12.8, 25.4))); path.addLine(to: q(mix(40.9, 47.6), mix(25.2, 12.6)))
        context.stroke(path, with: .color(.black), style: StrokeStyle(lineWidth: p(5), lineCap: .round, lineJoin: .round))
    }

    func drawFollowers(in context: inout GraphicsContext) {
        let progress = TasteProfileMotion.clamp01(frame.followProgress)
        let run = TasteProfileTheme.GlyphToken.n60
        let six = run.glyphs[0]
        let zero = run.glyphs[1]
        let one = TasteProfileTheme.Glyph(character: "1", inkLeft: zero.inkLeft, inkWidth: (17 * run.digitH / 36).rounded())
        drawGlyph(six, run: run, in: &context)
        let pad = 8.0
        let travel = run.digitH + pad
        let wx = zero.inkLeft - 4
        let wy = run.baseline - run.digitH - pad
        var clipped = context
        clipped.clip(to: Path(CGRect(x: p(wx), y: y(wy), width: p(zero.inkWidth + 8), height: p(run.digitH + 2 * pad))))
        drawGlyph(zero, run: run, in: &clipped, yOffset: -progress * travel, opacity: 1 - progress)
        drawGlyph(one, run: run, in: &clipped, yOffset: (1 - progress) * travel, opacity: progress)
        let shift = zero.inkWidth - one.inkWidth
        drawLine(TasteProfileTheme.TextToken.followers, in: &context, x: TasteProfileTheme.TextToken.followers.x - shift * progress)
    }

    func drawCoin(in context: inout GraphicsContext) {
        let pose = TasteProfileMotion.coin(frame.change)
        let shadow = TasteProfileTheme.Sprite.coinShadow
        if pose.lift > 0.001 {
            let width = p(shadow.w * (1 - 0.25 * pose.lift))
            let rect = CGRect(x: p(shadow.cx) - width / 2, y: y(shadow.cy - shadow.h / 2), width: width, height: p(shadow.h))
            context.fill(Path(ellipseIn: rect), with: .radialGradient(Gradient(colors: [.black.opacity(TasteProfileMotion.Coin.shadow * pose.lift), .clear]),
                                                                       center: CGPoint(x: rect.midX, y: rect.midY), startRadius: 0, endRadius: rect.width / 2))
        }
        let sprite = TasteProfileTheme.Sprite.coin
        let index = min(max(0, TasteProfileMotion.coinFrame(angle: pose.angle, back: pose.back)), TasteProfileAssets.coinFrames.count - 1)
        guard TasteProfileAssets.coinFrames.indices.contains(index) else { return }
        let rect = CGRect(x: p(sprite.x), y: y(sprite.y - TasteProfileMotion.Coin.lift * pose.lift), width: p(sprite.w), height: p(sprite.h))
        var layer = context
        layer.translateBy(x: rect.midX, y: rect.midY)
        layer.scaleBy(x: 1 + TasteProfileMotion.Coin.grow * pose.lift, y: 1 + TasteProfileMotion.Coin.grow * pose.lift)
        layer.translateBy(x: -rect.midX, y: -rect.midY)
        layer.draw(Image(uiImage: TasteProfileAssets.coinFrames[index]), in: rect)
    }

    func drawGem(in context: inout GraphicsContext) {
        guard !TasteProfileAssets.gemFrames.isEmpty else { return }
        let sprite = TasteProfileTheme.Sprite.gem
        let phase = 0.0
        let first = Int(floor(phase)) % 12
        let second = (first + 1) % 12
        let fraction = phase - floor(phase)
        let rect = CGRect(x: p(sprite.x), y: y(sprite.y), width: p(sprite.w), height: p(sprite.h))
        context.draw(Image(uiImage: TasteProfileAssets.gemFrames[first]), in: rect)
        if fraction > 0.001 {
            var layer = context
            layer.opacity = fraction
            layer.draw(Image(uiImage: TasteProfileAssets.gemFrames[second]), in: rect)
        }
        let pixels: [Character: [String]] = [
            "3": ["111111", "000011", "000011", "011111", "000011", "000011", "111111"],
            "7": ["111111", "000011", "000110", "001100", "001100", "011000", "011000"],
        ]
        for item in [("3" as Character, 61.0), ("7" as Character, 110.0)] {
            for (row, bits) in pixels[item.0, default: []].enumerated() {
                for (column, bit) in bits.enumerated() where bit == "1" {
                    context.fill(Path(CGRect(x: p(sprite.x + item.1 + Double(column) * 6),
                                             y: y(sprite.y + 63 + Double(row) * 6), width: p(6), height: p(6))), with: .color(.black))
                }
            }
        }
    }

    func drawRankWord(in context: inout GraphicsContext) {
        let group = TasteProfileMotion.RollGroup(layerOf: TasteProfileMotion.rank.map(\.word), values: [0, 1, 2],
                                                 start: TasteProfileMotion.Label.delay, duration: TasteProfileMotion.Label.duration,
                                                 stagger: 0, travel: TasteProfileMotion.Label.travel)
        for layer in TasteProfileMotion.rankWords.indices {
            let style = TasteProfileMotion.rollStyle(frame.change, group: group, layer: layer, slot: 0)
            drawLine(TasteProfileTheme.TextToken.rankA, in: &context, text: TasteProfileMotion.rankWords[layer],
                     yOffset: style.y, opacity: style.opacity)
        }
    }

    func drawChips(in context: inout GraphicsContext) {
        for index in TasteProfileTheme.Box.chips.indices {
            let chip = TasteProfileTheme.Box.chips[index]
            let opacity = 1 - TasteProfileMotion.coverage(frame.change, chip: index)
            let pressScale = TasteProfileMotion.chipScale(frame.change, chip: index)
            let rect = CGRect(x: p(chip.x), y: y(TasteProfileTheme.Box.chipY), width: p(chip.w), height: p(TasteProfileTheme.Box.chipH))
            let center = CGPoint(x: rect.midX, y: rect.midY)
            var layer = context
            layer.opacity = opacity
            layer.translateBy(x: center.x, y: center.y); layer.scaleBy(x: pressScale, y: pressScale); layer.translateBy(x: -center.x, y: -center.y)
            let path = Path(roundedRect: rect, cornerRadius: rect.height / 2)
            layer.drawLayer { shadow in
                shadow.addFilter(.shadow(color: .black.opacity(0.09), radius: 5 * pointScale, x: 0, y: 2.5 * pointScale))
                shadow.fill(path, with: .color(TasteProfileTheme.ColorToken.chipFill))
            }
            layer.fill(path, with: .color(TasteProfileTheme.ColorToken.chipFill))
            layer.fill(path, with: .linearGradient(Gradient(stops: [.init(color: .clear, location: 0), .init(color: .black.opacity(0.025), location: 0.65), .init(color: .black.opacity(0.06), location: 1)]),
                                                         startPoint: CGPoint(x: rect.midX, y: rect.minY), endPoint: CGPoint(x: rect.midX, y: rect.maxY)))
            drawChipLabel(index, color: TasteProfileTheme.ColorToken.chipText, scale: pressScale, in: &context)
        }

        let raw = TasteProfileMotion.pressedPill(frame.change)
        let left = (p(raw.0) * displayScale).rounded() / displayScale
        let right = (p(raw.1) * displayScale).rounded() / displayScale
        let top = y(TasteProfileTheme.Box.chipY)
        let chipHeight = p(TasteProfileTheme.Box.chipH)
        let outerRect = CGRect(x: left, y: top, width: width, height: chipHeight)
        let innerRect = CGRect(x: right - width, y: top, width: width, height: chipHeight)
        context.drawLayer { outer in
            outer.clip(to: Path(roundedRect: outerRect, cornerRadius: chipHeight / 2))
            outer.drawLayer { inner in
                inner.clip(to: Path(roundedRect: innerRect, cornerRadius: chipHeight / 2))
                inner.fill(Path(CGRect(x: 0, y: top, width: width, height: chipHeight)), with: .color(.black))
                inner.fill(Path(CGRect(x: 0, y: top, width: width, height: p(30))),
                           with: .linearGradient(Gradient(colors: [.white.opacity(0.30), .clear]),
                                                 startPoint: CGPoint(x: width / 2, y: top), endPoint: CGPoint(x: width / 2, y: top + p(30))))
                for index in TasteProfileTheme.Box.chips.indices {
                    drawChipLabel(index, color: .white, scale: TasteProfileMotion.chipScale(frame.change, chip: index), in: &inner)
                }
            }
        }
    }

    func drawChipLabel(_ index: Int, color: Color, scale labelScale: Double, in context: inout GraphicsContext) {
        let chip = TasteProfileTheme.Box.chips[index]
        let center = CGPoint(x: p(chip.x + chip.w / 2), y: y(TasteProfileTheme.Box.chipY + TasteProfileTheme.Box.chipH / 2))
        var layer = context
        layer.translateBy(x: center.x, y: center.y); layer.scaleBy(x: labelScale, y: labelScale); layer.translateBy(x: -center.x, y: -center.y)
        if index == 0 {
            drawGlobe(color: color, in: &layer)
            drawGlyphRun(TasteProfileTheme.GlyphToken.chipAll, in: &layer, color: color)
        } else {
            let specs = [TasteProfileTheme.TextToken.chipA, TasteProfileTheme.TextToken.chipB, TasteProfileTheme.TextToken.chipC]
            drawLine(specs[index - 1], in: &layer, color: color)
        }
    }

    func drawGlobe(color: Color, in context: inout GraphicsContext) {
        let box = TasteProfileTheme.Box.globe
        let center = CGPoint(x: p(box.x + box.w / 2), y: y(box.y + box.h / 2))
        let radius = p(box.w / 2 - 2.5)
        var path = Path()
        path.addEllipse(in: CGRect(x: center.x - radius, y: center.y - radius, width: radius * 2, height: radius * 2))
        path.addEllipse(in: CGRect(x: center.x - radius * 0.44, y: center.y - radius, width: radius * 0.88, height: radius * 2))
        path.move(to: CGPoint(x: center.x - radius, y: center.y)); path.addLine(to: CGPoint(x: center.x + radius, y: center.y))
        for offset in [-0.5, 0.5] {
            path.move(to: CGPoint(x: center.x - radius * 0.87, y: center.y + radius * offset))
            path.addLine(to: CGPoint(x: center.x + radius * 0.87, y: center.y + radius * offset))
        }
        context.stroke(path, with: .color(color), style: StrokeStyle(lineWidth: p(5), lineCap: .round, lineJoin: .round))
    }

    func local(_ x: Double, _ localY: Double, _ box: TasteProfileTheme.BoxToken) -> CGPoint {
        CGPoint(x: p(box.x + x), y: y(box.y + localY))
    }

    func p(_ value: Double) -> Double { value * scale }
    func y(_ value: Double) -> Double { value * scale + y0 }
}
