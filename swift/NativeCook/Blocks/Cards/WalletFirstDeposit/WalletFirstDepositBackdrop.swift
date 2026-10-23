import SwiftUI

struct WalletFirstDepositBackdrop: View, Animatable {
    let frame: WalletFirstDepositFrame
    let scale: Double
    let y0: Double
    var bannerPress: Double = 0

    var animatableData: Double {
        get { bannerPress }
        set { bannerPress = newValue }
    }

    var body: some View {
        Canvas { context, _ in
            context.scaleBy(x: scale, y: scale)
            context.translateBy(x: 0, y: y0 / scale)
            drawHeader(&context)
            drawBanner(&context)
            drawPills(&context)
            drawTransactions(&context)
            drawRecent(&context)
            drawAnalytics(&context)
        }
        .allowsHitTesting(false)
    }

    private func rounded(
        _ context: inout GraphicsContext,
        _ rect: CGRect,
        radius: Double,
        color: Color,
        opacity: Double = 1
    ) {
        context.fill(Path(roundedRect: rect, cornerRadius: radius), with: .color(color.opacity(opacity)))
    }

    private func drawHeader(_ context: inout GraphicsContext) {
        context.fill(
            Path(ellipseIn: CGRect(x: 1037, y: 209, width: 120, height: 120)),
            with: .color(WalletFirstDepositMetrics.card)
        )
        var nut = Path()
        for index in 0..<6 {
            let angle = Double(index) * .pi / 3 - .pi / 2
            let point = CGPoint(x: 1098 + cos(angle) * 21, y: 267.5 + sin(angle) * 21)
            if index == 0 { nut.move(to: point) } else { nut.addLine(to: point) }
        }
        nut.closeSubpath()
        context.stroke(
            nut,
            with: .color(Color(hex: 0xc4c4c6)),
            style: StrokeStyle(lineWidth: 19, lineCap: .round, lineJoin: .round)
        )
        context.fill(
            Path(ellipseIn: CGRect(x: 1088.5, y: 258, width: 19, height: 19)),
            with: .color(WalletFirstDepositMetrics.card)
        )
        orb(
            &context,
            cx: 100,
            cy: 268,
            radius: 54.5,
            colors: [0x0a0596, 0x2336b5, 0x4196d9, 0x70c4e7, 0xc3ebfc]
        )
        if frame.dot < 1 {
            let diameter = 11 * (1 - 0.5 * frame.dot)
            context.fill(
                Path(ellipseIn: CGRect(x: 141.5 - diameter / 2, y: 226.5 - diameter / 2, width: diameter, height: diameter)),
                with: .color(Color(hex: 0xd9474c).opacity(1 - frame.dot))
            )
        }
    }

    private func drawBanner(_ context: inout GraphicsContext) {
        let alpha = 1 - frame.banner
        guard alpha > 0.001 else { return }
        let banner = CGRect(x: 22.25, y: 745, width: 1159.25, height: 313.8)
        let center = CGPoint(x: banner.midX, y: banner.midY)
        let pressScale = (1 - 0.03 * frame.banner) * (1 - 0.03 * bannerPress)
        var layer = context
        layer.opacity = alpha
        layer.translateBy(x: 0, y: -30 * frame.banner)
        layer.translateBy(x: center.x, y: center.y)
        layer.scaleBy(x: pressScale, y: pressScale)
        layer.translateBy(x: -center.x, y: -center.y)
        rounded(&layer, banner, radius: 48, color: WalletFirstDepositMetrics.card)

        let track = CGRect(x: 57, y: 994.5, width: 1091, height: 12)
        rounded(&layer, track, radius: 6, color: Color(hex: 0xdadadc))
        let fraction = interpolate(0.428, 1, frame.fill)
        let fillWidth = track.width * fraction
        let fade = min(80 * (1 - frame.fill), fillWidth)
        let fadeStart = max(0.01, (fillWidth - fade) / fillWidth)
        let stops = [
            Gradient.Stop(color: Color(hex: 0x1455ba), location: 0),
            Gradient.Stop(color: Color(hex: 0x1a98e6), location: fadeStart),
            Gradient.Stop(color: Color(hex: 0x1a98e6).opacity(0), location: 1),
        ]
        layer.fill(
            Path(roundedRect: CGRect(x: track.minX, y: track.minY, width: fillWidth, height: track.height), cornerRadius: 6),
            with: .linearGradient(
                Gradient(stops: stops),
                startPoint: CGPoint(x: track.minX, y: track.midY),
                endPoint: CGPoint(x: track.minX + fillWidth, y: track.midY)
            )
        )
    }

    private func drawPills(_ context: inout GraphicsContext) {
        let pills = [
            (progress: frame.pill0, rect: CGRect(x: 46.3, y: 722.4, width: 330.9, height: 127.2), icon: CGPoint(x: 125.5, y: 786.5)),
            (progress: frame.pill1, rect: CGRect(x: 394.2, y: 722.4, width: 330.5, height: 127.2), icon: CGPoint(x: 500.5, y: 786.5)),
        ]
        for (index, pill) in pills.enumerated() where pill.progress > 0.001 {
            let amount = 0.9 + 0.1 * pill.progress
            let alpha = min(1, pill.progress * 2)
            var layer = context
            layer.translateBy(x: pill.rect.midX, y: pill.rect.midY)
            layer.scaleBy(x: amount, y: amount)
            layer.translateBy(x: -pill.rect.midX, y: -pill.rect.midY)
            rounded(&layer, pill.rect, radius: pill.rect.height / 2, color: .black, opacity: alpha)
            layer.stroke(
                arrow(cx: pill.icon.x, cy: pill.icon.y, size: 24, downLeft: index == 0),
                with: .color(.white.opacity(alpha)),
                style: StrokeStyle(lineWidth: 6.2, lineCap: .round, lineJoin: .round)
            )
        }
    }

    private func drawTransactions(_ context: inout GraphicsContext) {
        let rise: (Double) -> Double = { 48 * (1 - $0) }
        if frame.txHead > 0.001 {
            rounded(
                &context,
                CGRect(x: 984.8, y: 942.9 + rise(frame.txHead), width: 168.2, height: 85.1),
                radius: 42.55,
                color: WalletFirstDepositMetrics.pill,
                opacity: frame.txHead
            )
        }
        if frame.txCard > 0.001 {
            let dy = rise(frame.txCard)
            rounded(
                &context,
                CGRect(x: 22.25, y: 1051.7 + dy, width: 1159.25, height: 436.2),
                radius: 72,
                color: WalletFirstDepositMetrics.card,
                opacity: frame.txCard
            )
            context.fill(
                Path(CGRect(x: 22.25, y: 1264.75 + dy, width: 1159.25, height: 1.5)),
                with: .color(Color(hex: 0xe7e7ea).opacity(frame.txCard))
            )
        }
        if frame.txRow0 > 0.001 {
            drawCafe(&context, cy: 1160 + rise(frame.txRow0), opacity: frame.txRow0)
        }
        if frame.txRow1 > 0.001 {
            orb(
                &context,
                cx: 129.5,
                cy: 1380 + rise(frame.txRow1),
                radius: 61,
                colors: [0x0a63a8, 0x1a82c1, 0x55b9d0, 0x97d6c5, 0xeaf8b4],
                opacity: frame.txRow1
            )
        }
    }

    private func drawCafe(_ context: inout GraphicsContext, cy: Double, opacity: Double) {
        let cx = 130.0
        let radius = 60.5
        context.fill(
            Path(ellipseIn: CGRect(x: cx - radius, y: cy - radius, width: radius * 2, height: radius * 2)),
            with: .color(Color(hex: 0x0b7449).opacity(opacity))
        )
        context.stroke(
            Path(ellipseIn: CGRect(x: cx - radius * 0.84, y: cy - radius * 0.84, width: radius * 1.68, height: radius * 1.68)),
            with: .color(.white.opacity(0.9 * opacity)),
            style: StrokeStyle(lineWidth: 3, lineCap: .round, lineJoin: .round)
        )
        let halfHeight = radius * 0.42
        let halfWidth = radius * 0.3
        var monogram = Path()
        monogram.move(to: CGPoint(x: cx - halfWidth, y: cy - halfHeight * 0.55))
        monogram.addLine(to: CGPoint(x: cx - halfWidth, y: cy + halfHeight))
        monogram.move(to: CGPoint(x: cx + halfWidth, y: cy - halfHeight * 0.55))
        monogram.addLine(to: CGPoint(x: cx + halfWidth, y: cy + halfHeight))
        monogram.move(to: CGPoint(x: cx - halfWidth, y: cy + halfHeight * 0.15))
        monogram.addLine(to: CGPoint(x: cx + halfWidth, y: cy + halfHeight * 0.15))
        context.stroke(monogram, with: .color(.white.opacity(opacity)), style: StrokeStyle(lineWidth: 7.5, lineCap: .butt))
        var roof = Path()
        roof.move(to: CGPoint(x: cx - halfWidth * 1.45, y: cy - halfHeight * 0.35))
        roof.addLine(to: CGPoint(x: cx, y: cy - halfHeight * 1.15))
        roof.addLine(to: CGPoint(x: cx + halfWidth * 1.45, y: cy - halfHeight * 0.35))
        context.stroke(roof, with: .color(.white.opacity(opacity)), style: StrokeStyle(lineWidth: 6, lineCap: .round, lineJoin: .round))
    }

    private func drawRecent(_ context: inout GraphicsContext) {
        let cards = [frame.card0, frame.card1, frame.card2]
        for index in 0..<3 {
            let from = CGRect(x: 22.25 + Double(index) * 388.85, y: 1234.8, width: 381.6, height: 414.6)
            let to = CGRect(x: 22.25 + Double(index) * 291.3, y: 1665, width: 285.3, height: 307)
            let rect = interpolate(from, to, cards[index])
            rounded(&context, rect, radius: interpolate(81, 72, cards[index]), color: WalletFirstDepositMetrics.card)
        }
        if frame.card3 > 0.001 {
            rounded(
                &context,
                CGRect(x: 896.15 + 150 * (1 - frame.card3), y: 1665, width: 285.3, height: 307),
                radius: 72,
                color: WalletFirstDepositMetrics.card,
                opacity: frame.card3
            )
        }
        if frame.names > 0.001 {
            rounded(
                &context,
                CGRect(x: 983.3, y: 1555.3, width: 169.5, height: 84.7),
                radius: 42.35,
                color: WalletFirstDepositMetrics.pill,
                opacity: frame.names
            )
        }

        let iconAlpha = 1 - frame.actionsOut
        if iconAlpha > 0.001 {
            let centers = [CGPoint(x: 120.5, y: 1335.5), CGPoint(x: 509.1, y: 1335.5), CGPoint(x: 898.45, y: 1335.5)]
            for index in 0..<3 {
                let center = centers[index]
                var layer = context
                let iconScale = 0.94 + 0.06 * iconAlpha
                layer.translateBy(x: center.x, y: center.y)
                layer.scaleBy(x: iconScale, y: iconScale)
                layer.translateBy(x: -center.x, y: -center.y)
                if index == 0 {
                    let half = 40.0
                    layer.stroke(
                        Path { path in
                            path.move(to: CGPoint(x: center.x - half, y: center.y))
                            path.addLine(to: CGPoint(x: center.x + half, y: center.y))
                        },
                        with: .linearGradient(
                            Gradient(colors: [Color(hex: 0x5e9488).opacity(iconAlpha * iconAlpha), Color(hex: 0xd19aef).opacity(iconAlpha * iconAlpha)]),
                            startPoint: CGPoint(x: center.x - half, y: center.y),
                            endPoint: CGPoint(x: center.x + half, y: center.y)
                        ),
                        style: StrokeStyle(lineWidth: 10, lineCap: .round, lineJoin: .round)
                    )
                    layer.stroke(
                        Path { path in
                            path.move(to: CGPoint(x: center.x, y: center.y - half))
                            path.addLine(to: CGPoint(x: center.x, y: center.y + half))
                        },
                        with: .linearGradient(
                            Gradient(colors: [Color(hex: 0xd69cf2).opacity(iconAlpha * iconAlpha), Color(hex: 0x5e8c78).opacity(iconAlpha * iconAlpha)]),
                            startPoint: CGPoint(x: center.x, y: center.y - half),
                            endPoint: CGPoint(x: center.x, y: center.y + half)
                        ),
                        style: StrokeStyle(lineWidth: 10, lineCap: .round, lineJoin: .round)
                    )
                } else {
                    let upRight = index == 1
                    let iconPath = arrow(cx: center.x, cy: center.y, size: 66, downLeft: !upRight)
                    let start = upRight
                        ? CGPoint(x: center.x - 33, y: center.y + 33)
                        : CGPoint(x: center.x + 33, y: center.y - 33)
                    let end = upRight
                        ? CGPoint(x: center.x + 33, y: center.y - 33)
                        : CGPoint(x: center.x - 33, y: center.y + 33)
                    let colors = upRight
                        ? [Color(hex: 0x6cc77a).opacity(iconAlpha * iconAlpha), Color(hex: 0x6fd3db).opacity(iconAlpha * iconAlpha)]
                        : [Color(hex: 0x4d82e0).opacity(iconAlpha * iconAlpha), Color(hex: 0xa33cc8).opacity(iconAlpha * iconAlpha)]
                    layer.stroke(
                        iconPath,
                        with: .linearGradient(Gradient(colors: colors), startPoint: start, endPoint: end),
                        style: StrokeStyle(lineWidth: 10, lineCap: .round, lineJoin: .round)
                    )
                }
            }
        }

        let orbColors: [[UInt32]] = [
            [0x08a606, 0x5e9e78, 0xbb8ddc, 0xdeaedb, 0xf7e5c1],
            [0x7fa012, 0x77b07a, 0x63bbc6, 0x94d7bd, 0xe5fab6],
            [0xb02baf, 0x8c3cb9, 0x3f60c9, 0x759ebe, 0xd8eeb5],
            [0xa70300, 0xc01d0d, 0xf84535, 0xf28c4b, 0xe8ea7e],
        ]
        let pops = [frame.orb0, frame.orb1, frame.orb2, frame.orb3]
        for index in 0..<4 where pops[index] > 0.001 {
            let center = CGPoint(x: 164.9 + Double(index) * 291.3, y: 1785.5)
            let alpha = min(1, pops[index] * 3)
            let pop = 0.6 + 0.4 * pops[index]
            var layer = context
            layer.translateBy(x: center.x, y: center.y)
            layer.scaleBy(x: pop, y: pop)
            layer.translateBy(x: -center.x, y: -center.y)
            orb(&layer, cx: center.x, cy: center.y, radius: 61, colors: orbColors[index], opacity: alpha)
            let badgeCenter = CGPoint(x: center.x + 34, y: center.y + 34)
            layer.fill(
                Path(ellipseIn: CGRect(x: badgeCenter.x - 23, y: badgeCenter.y - 23, width: 46, height: 46)),
                with: .color(.white.opacity(alpha))
            )
            badgeGlyph(&layer, index: index, center: badgeCenter, size: 19, opacity: alpha)
        }
    }

    private func badgeGlyph(
        _ context: inout GraphicsContext,
        index: Int,
        center: CGPoint,
        size: Double,
        opacity: Double
    ) {
        if index == 3 {
            let rect = CGRect(x: center.x - size * 0.42, y: center.y - size * 0.3, width: size * 0.84, height: size * 0.6)
            rounded(&context, rect, radius: size * 0.12, color: Color(hex: 0x111111), opacity: opacity)
            context.fill(
                Path(CGRect(x: center.x - size * 0.42, y: center.y - size * 0.12, width: size * 0.84, height: size * 0.12)),
                with: .color(.white.opacity(opacity))
            )
            return
        }

        var glyph = Path()
        if index == 0 {
            glyph.move(to: CGPoint(x: center.x - size * 0.35, y: center.y - size * 0.35))
            glyph.addLine(to: CGPoint(x: center.x + size * 0.35, y: center.y - size * 0.35))
            glyph.addLine(to: CGPoint(x: center.x - size * 0.05, y: center.y + size * 0.4))
            glyph.move(to: CGPoint(x: center.x - size * 0.2, y: center.y + size * 0.02))
            glyph.addLine(to: CGPoint(x: center.x + size * 0.18, y: center.y + size * 0.02))
        } else if index == 1 {
            glyph.move(to: CGPoint(x: center.x - size * 0.42, y: center.y - size * 0.35))
            glyph.addLine(to: CGPoint(x: center.x - size * 0.12, y: center.y + size * 0.38))
            glyph.addLine(to: CGPoint(x: center.x + size * 0.18, y: center.y - size * 0.35))
            context.fill(
                Path(ellipseIn: CGRect(
                    x: center.x + size * 0.42 - size * 0.11,
                    y: center.y + size * 0.3 - size * 0.11,
                    width: size * 0.22,
                    height: size * 0.22
                )),
                with: .color(Color(hex: 0x111111).opacity(opacity))
            )
        } else {
            glyph.move(to: CGPoint(x: center.x - size * 0.3, y: center.y + size * 0.4))
            glyph.addQuadCurve(
                to: CGPoint(x: center.x + size * 0.35, y: center.y - size * 0.38),
                control: CGPoint(x: center.x - size * 0.35, y: center.y - size * 0.4)
            )
            glyph.addQuadCurve(
                to: CGPoint(x: center.x - size * 0.3, y: center.y + size * 0.4),
                control: CGPoint(x: center.x + size * 0.3, y: center.y + size * 0.35)
            )
        }
        context.stroke(
            glyph,
            with: .color(Color(hex: 0x111111).opacity(opacity)),
            style: StrokeStyle(lineWidth: size * 0.2, lineCap: .round, lineJoin: .round)
        )
    }

    private func drawAnalytics(_ context: inout GraphicsContext) {
        let top = interpolate(1823.2, 2147.5, frame.reflow2)
        let bottom = top + interpolate(476.8, 427.5, frame.plot)
        rounded(
            &context,
            CGRect(x: 22.25, y: top, width: 1159.25, height: 582.3),
            radius: 78,
            color: WalletFirstDepositMetrics.card
        )
        rounded(
            &context,
            CGRect(x: 1007, y: interpolate(1715.5, 2039.4, frame.reflow2), width: 146, height: 84),
            radius: 42,
            color: WalletFirstDepositMetrics.pill
        )
        let emptyAlpha = 1 - frame.plotA
        if emptyAlpha > 0.001 {
            let topY = top + 294.8
            let band = CGRect(x: 72, y: topY, width: 1056, height: bottom - topY)
            context.fill(
                Path(band),
                with: .linearGradient(
                    Gradient(colors: [Color(hex: 0xe3e3e5).opacity(emptyAlpha), WalletFirstDepositMetrics.card.opacity(emptyAlpha)]),
                    startPoint: CGPoint(x: band.midX, y: band.minY),
                    endPoint: CGPoint(x: band.midX, y: band.maxY)
                )
            )
            context.stroke(
                Path { path in
                    path.move(to: CGPoint(x: 72, y: topY))
                    path.addLine(to: CGPoint(x: 1128, y: topY))
                },
                with: .color(Color(hex: 0xcfcfd3).opacity(emptyAlpha)),
                style: StrokeStyle(lineWidth: 2.5, lineCap: .butt, dash: [6, 4])
            )
        }
        drawChart(&context, top: top, bottom: bottom)
    }

    private func drawChart(_ context: inout GraphicsContext, top: Double, bottom: Double) {
        guard frame.line > 0.001 || frame.forecast > 0.001 else { return }
        let linePoints: [[Double]] = [
            [72, 2569], [146, 2569], [244, 2486], [262, 2475], [282, 2466], [314, 2466], [350, 2430],
            [366, 2423], [390, 2421], [400, 2418], [416, 2418], [464, 2408], [470, 2407],
        ]
        let forecastPoints: [[Double]] = [
            [470, 2434], [608, 2433], [638, 2420], [722, 2422], [736, 2411], [758, 2403], [810, 2399],
            [826, 2395], [854, 2384], [950, 2383], [962, 2375], [998, 2355], [1058, 2344], [1070, 2336], [1134, 2330],
        ]
        let trendPoints: [[Double]] = [[72, 2569], [146, 2569], [250, 2492], [318, 2484], [352, 2442], [470, 2434]]
        let scaleY = 0.35 + 0.65 * frame.plot
        let deltaY = bottom - 2575

        if frame.forecast > 0.001 {
            let forecast = chartPath(forecastPoints, dy: deltaY, sy: scaleY)
            var area = forecast
            area.addLine(to: CGPoint(x: 1134, y: bottom))
            area.addLine(to: CGPoint(x: 470, y: bottom))
            area.closeSubpath()
            context.fill(
                area,
                with: .linearGradient(
                    Gradient(colors: [Color(hex: 0xe5e5e7).opacity(frame.forecast), WalletFirstDepositMetrics.card.opacity(frame.forecast)]),
                    startPoint: CGPoint(x: 800, y: bottom - 245),
                    endPoint: CGPoint(x: 800, y: bottom)
                )
            )
            context.stroke(
                forecast,
                with: .color(Color(hex: 0xcfcfd3).opacity(frame.forecast)),
                style: StrokeStyle(lineWidth: 2.5, lineCap: .round, lineJoin: .round, dash: [6, 4])
            )
            context.stroke(
                chartPath(trendPoints, dy: deltaY, sy: scaleY),
                with: .color(Color(hex: 0xcfcfd3).opacity(0.8 * frame.forecast)),
                style: StrokeStyle(lineWidth: 2.5, lineCap: .round, lineJoin: .round, dash: [6, 4])
            )
        }

        if frame.line > 0.001 {
            let line = chartPath(linePoints, dy: deltaY, sy: scaleY)
            var area = line
            area.addLine(to: CGPoint(x: 470, y: bottom))
            area.addLine(to: CGPoint(x: 72, y: bottom))
            area.closeSubpath()
            var clipped = context
            clipped.clip(to: Path(CGRect(x: 68, y: top, width: 406 * frame.line, height: bottom + 40 - top)))
            clipped.fill(area, with: .color(Color(hex: 0xf7dcc8).opacity(0.95)))
            clipped.stroke(
                line,
                with: .color(Color(hex: 0xef8838)),
                style: StrokeStyle(lineWidth: 4.5, lineCap: .round, lineJoin: .round)
            )
        }
    }

    private func chartPath(_ points: [[Double]], dy: Double, sy: Double) -> Path {
        Path { path in
            for (index, point) in points.enumerated() {
                let value = CGPoint(x: point[0], y: 2575 + (point[1] - 2575) * sy + dy)
                if index == 0 { path.move(to: value) } else { path.addLine(to: value) }
            }
        }
    }

    private func arrow(cx: Double, cy: Double, size: Double, downLeft: Bool) -> Path {
        let half = size / 2
        return Path { path in
            if downLeft {
                path.move(to: CGPoint(x: cx + half, y: cy - half))
                path.addLine(to: CGPoint(x: cx - half, y: cy + half))
                path.move(to: CGPoint(x: cx - half, y: cy - half * 0.15))
                path.addLine(to: CGPoint(x: cx - half, y: cy + half))
                path.addLine(to: CGPoint(x: cx + half * 0.15, y: cy + half))
            } else {
                path.move(to: CGPoint(x: cx - half, y: cy + half))
                path.addLine(to: CGPoint(x: cx + half, y: cy - half))
                path.move(to: CGPoint(x: cx - half * 0.15, y: cy - half))
                path.addLine(to: CGPoint(x: cx + half, y: cy - half))
                path.addLine(to: CGPoint(x: cx + half, y: cy + half * 0.15))
            }
        }
    }

    private func orb(
        _ context: inout GraphicsContext,
        cx: Double,
        cy: Double,
        radius: Double,
        colors: [UInt32],
        opacity: Double = 1
    ) {
        guard opacity > 0.001 else { return }
        let shape = Path(ellipseIn: CGRect(x: cx - radius, y: cy - radius, width: radius * 2, height: radius * 2))
        let positions = [0.0, 0.22, 0.5, 0.72, 1.0]
        let stops = zip(colors, positions).map {
            Gradient.Stop(color: Color(hex: $0.0).opacity(opacity), location: $0.1)
        }
        context.fill(
            shape,
            with: .linearGradient(
                Gradient(stops: stops),
                startPoint: CGPoint(x: cx - radius * 0.72, y: cy + radius * 0.72),
                endPoint: CGPoint(x: cx + radius * 0.72, y: cy - radius * 0.72)
            )
        )
        context.fill(
            shape,
            with: .radialGradient(
                Gradient(stops: [
                    .init(color: .clear, location: 0),
                    .init(color: .clear, location: 0.62),
                    .init(color: Color(hex: 0x0a1040).opacity(0.22 * opacity), location: 1),
                ]),
                center: CGPoint(x: cx + radius * 0.35, y: cy - radius * 0.35),
                startRadius: 0,
                endRadius: radius * 1.45
            )
        )
    }
}

private func interpolate(_ from: Double, _ to: Double, _ amount: Double) -> Double {
    from + (to - from) * amount
}

private func interpolate(_ from: CGRect, _ to: CGRect, _ amount: Double) -> CGRect {
    CGRect(
        x: interpolate(from.minX, to.minX, amount),
        y: interpolate(from.minY, to.minY, amount),
        width: interpolate(from.width, to.width, amount),
        height: interpolate(from.height, to.height, amount)
    )
}
