import SwiftUI

struct BelieveCardCarouselCard: View {
    let data: BelieveCardCarouselCardData
    let textShift: Double
    let iconShift: Double
    let textY: Double
    let inPerspective: Bool

    var body: some View {
        ZStack(alignment: .topLeading) {
            BelieveCardCarouselPicture(data: data, iconShift: iconShift, inPerspective: inPerspective)
                .offset(x: BelieveCardCarouselMetrics.Card.inset, y: BelieveCardCarouselMetrics.Card.inset)

            Text("I believe in")
                .font(BelieveCardCarouselAssets.font(BelieveCardCarouselAssets.semibold, size: 15))
                .foregroundStyle(BelieveCardCarouselPalette.muted)
                .fixedSize()
                .offset(
                    x: BelieveCardCarouselMetrics.Card.padding + textShift,
                    y: BelieveCardCarouselMetrics.Card.believeTop + textY
                )

            Text(data.name)
                .font(BelieveCardCarouselAssets.font(BelieveCardCarouselAssets.extraBold, size: 26))
                .tracking(-0.5)
                .foregroundStyle(BelieveCardCarouselPalette.ink)
                .lineLimit(1)
                .frame(width: BelieveCardCarouselMetrics.Card.width - BelieveCardCarouselMetrics.Card.padding - 12, alignment: .leading)
                .offset(
                    x: BelieveCardCarouselMetrics.Card.padding + textShift,
                    y: BelieveCardCarouselMetrics.Card.nameTop + textY
                )

            BelieveCardCarouselDateRow(date: data.date)
                .offset(
                    x: BelieveCardCarouselMetrics.Card.padding + textShift,
                    y: BelieveCardCarouselMetrics.Card.dateTop + textY
                )

            BelieveCardCarouselMakerRow(data: data)
                .offset(
                    x: BelieveCardCarouselMetrics.Card.padding + textShift,
                    y: BelieveCardCarouselMetrics.Card.byTop + textY
                )
        }
        .frame(
            width: BelieveCardCarouselMetrics.Card.width,
            height: BelieveCardCarouselMetrics.Card.height,
            alignment: .topLeading
        )
        .background(BelieveCardCarouselPalette.card, in: .rect(cornerRadius: BelieveCardCarouselMetrics.Card.radius, style: .continuous))
        .shadow(color: .black.opacity(0.07), radius: 18, x: 0, y: 8)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(data.name), \(data.date), by \(data.maker)")
    }
}

private struct BelieveCardCarouselPicture: View {
    let data: BelieveCardCarouselCardData
    let iconShift: Double
    let inPerspective: Bool

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: BelieveCardCarouselMetrics.Card.imageRadius, style: .continuous)
                .fill(
                    LinearGradient(
                        stops: [
                            .init(color: data.imageColors[0], location: 0),
                            .init(color: data.imageColors[1], location: 0.55),
                            .init(color: data.imageColors[2], location: 1),
                        ],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                )

            Circle()
                .fill(data.imageColors[3])
                .frame(
                    width: BelieveCardCarouselMetrics.Card.glowRadius * 2,
                    height: BelieveCardCarouselMetrics.Card.glowRadius * 2
                )
                .blur(radius: BelieveCardCarouselMetrics.Card.glowBlur)
                .offset(x: iconShift)

            BelieveCardCarouselIcon(data: data)
                .offset(x: iconShift)
        }
        .frame(
            width: BelieveCardCarouselMetrics.Card.imageWidth,
            height: BelieveCardCarouselMetrics.Card.imageHeight
        )
        .clipped()
        .modifier(BelieveCardCarouselPicturePerspective(enabled: inPerspective))
    }
}

private struct BelieveCardCarouselPicturePerspective: ViewModifier {
    let enabled: Bool

    func body(content: Content) -> some View {
        if enabled {
            content
                .scaleEffect(BelieveCardCarouselMetrics.Row.Picture.scale)
                .transformEffect(
                    CGAffineTransform(
                        a: 1,
                        b: 0,
                        c: tan(BelieveCardCarouselMetrics.Row.Picture.skewX * .pi / 180),
                        d: 1,
                        tx: 0,
                        ty: 0
                    )
                )
                .rotation3DEffect(
                    .degrees(BelieveCardCarouselMetrics.Row.Picture.rotationX),
                    axis: (x: 1, y: 0, z: 0),
                    perspective: 1 / BelieveCardCarouselMetrics.Row.Picture.perspective
                )
                .rotation3DEffect(
                    .degrees(BelieveCardCarouselMetrics.Row.Picture.rotationY),
                    axis: (x: 0, y: 1, z: 0),
                    perspective: 1 / BelieveCardCarouselMetrics.Row.Picture.perspective
                )
                .rotationEffect(.degrees(BelieveCardCarouselMetrics.Row.Picture.rotation))
                .offset(
                    x: BelieveCardCarouselMetrics.Row.Picture.x,
                    y: BelieveCardCarouselMetrics.Row.Picture.y
                )
        } else {
            content
        }
    }
}

private struct BelieveCardCarouselIcon: View {
    let data: BelieveCardCarouselCardData

    var body: some View {
        switch data.artwork {
        case .mark:
            ZStack {
                RoundedRectangle(cornerRadius: BelieveCardCarouselMetrics.Card.iconRadius, style: .continuous)
                    .fill(
                        LinearGradient(
                            colors: [Color(red: 0, green: 0xEA / 255, blue: 0x76 / 255), Color(red: 0x01 / 255, green: 0xD4 / 255, blue: 0x54 / 255)],
                            startPoint: .top,
                            endPoint: .bottom
                        )
                    )
                BelieveCardCarouselBrandMark(size: 62, color: .white.opacity(0.86))
                    .shadow(color: Color(red: 0x0A / 255, green: 0x7A / 255, blue: 0x3C / 255).opacity(0.35), radius: 3, x: 0, y: 2)
            }
            .frame(width: BelieveCardCarouselMetrics.Card.icon, height: BelieveCardCarouselMetrics.Card.icon)
            .shadow(color: Color(red: 0x0B / 255, green: 0x5E / 255, blue: 0x33 / 255).opacity(0.18), radius: 10, x: 0, y: 6)

        case .figo:
            ZStack(alignment: .bottom) {
                BelieveCardCarouselPixelBowl()
                Text("a r o")
                    .font(BelieveCardCarouselAssets.font(BelieveCardCarouselAssets.extraBold, size: 21))
                    .tracking(1)
                    .foregroundStyle(.white)
                    .padding(.bottom, 12)
            }
            .frame(width: BelieveCardCarouselMetrics.Card.icon, height: BelieveCardCarouselMetrics.Card.icon)
            .clipShape(.rect(cornerRadius: BelieveCardCarouselMetrics.Card.iconRadius, style: .continuous))
            .shadow(color: Color(red: 0x0B / 255, green: 0x5E / 255, blue: 0x33 / 255).opacity(0.18), radius: 10, x: 0, y: 6)

        case .loop:
            BelieveCardCarouselLoopGlyph()
                .frame(width: BelieveCardCarouselMetrics.Card.icon, height: BelieveCardCarouselMetrics.Card.icon)
                .background(Color(red: 0xB2 / 255, green: 0xF6 / 255, blue: 0xFD / 255))
                .clipShape(.rect(cornerRadius: BelieveCardCarouselMetrics.Card.iconRadius, style: .continuous))
        }
    }
}

struct BelieveCardCarouselBrandMark: View {
    let size: Double
    let color: Color

    var body: some View {
        Canvas { context, canvasSize in
            let sx = canvasSize.width / 90
            let sy = canvasSize.height / 105
            var outer = Path()
            outer.move(to: CGPoint(x: 45 * sx, y: 14 * sy))
            outer.addLine(to: CGPoint(x: 74 * sx, y: 31 * sy))
            outer.addLine(to: CGPoint(x: 74 * sx, y: 69 * sy))
            outer.addLine(to: CGPoint(x: 45 * sx, y: 86 * sy))
            outer.addLine(to: CGPoint(x: 16 * sx, y: 69 * sy))
            outer.addLine(to: CGPoint(x: 16 * sx, y: 31 * sy))
            outer.closeSubpath()
            context.stroke(
                outer,
                with: .color(color),
                style: StrokeStyle(lineWidth: 13 * sx, lineJoin: .round)
            )

            var inner = Path()
            inner.move(to: CGPoint(x: 45 * sx, y: 40 * sy))
            inner.addLine(to: CGPoint(x: 58 * sx, y: 47.5 * sy))
            inner.addLine(to: CGPoint(x: 58 * sx, y: 62.5 * sy))
            inner.addLine(to: CGPoint(x: 45 * sx, y: 70 * sy))
            inner.addLine(to: CGPoint(x: 32 * sx, y: 62.5 * sy))
            inner.addLine(to: CGPoint(x: 32 * sx, y: 47.5 * sy))
            inner.closeSubpath()
            context.fill(inner, with: .color(color))
        }
        .frame(width: size, height: size * 1.17)
        .accessibilityHidden(true)
    }
}

private struct BelieveCardCarouselLoopGlyph: View {
    var body: some View {
        Canvas { context, size in
            let svgSize = size.width * 100 / BelieveCardCarouselMetrics.Card.icon
            let scale = svgSize / 86
            context.translateBy(x: (size.width - svgSize) / 2, y: (size.height - svgSize) / 2)
            let style = StrokeStyle(lineWidth: 13 * scale)
            context.stroke(
                Path(ellipseIn: CGRect(x: 13 * scale, y: 15 * scale, width: 30 * scale, height: 30 * scale)),
                with: .color(.black),
                style: style
            )
            context.stroke(
                Path(ellipseIn: CGRect(x: 41 * scale, y: 43 * scale, width: 30 * scale, height: 30 * scale)),
                with: .color(.black),
                style: style
            )
            var slash = Path()
            slash.move(to: CGPoint(x: 66 * scale, y: 12 * scale))
            slash.addLine(to: CGPoint(x: 18 * scale, y: 76 * scale))
            context.stroke(slash, with: .color(.black), style: StrokeStyle(lineWidth: 13 * scale, lineCap: .round))
        }
    }
}

private struct BelieveCardCarouselPixelBowl: View {
    private static let rows = [
        "....yyyll.....",
        "..yyllgglly...",
        ".ygglwlggyyl..",
        "bllggyygglgyyb",
        "bddbbbbbbbbddb",
        ".bddddddddddb.",
        "..bddddddddb..",
        "....bbbbbb....",
    ]

    private static let fills: [Character: Color] = [
        "b": Color(red: 0x4A / 255, green: 0x2C / 255, blue: 0x17 / 255),
        "d": Color(red: 0x7A / 255, green: 0x4A / 255, blue: 0x26 / 255),
        "g": Color(red: 0x6D / 255, green: 0xBE / 255, blue: 0x35 / 255),
        "l": Color(red: 0x9B / 255, green: 0xD4 / 255, blue: 0x4A / 255),
        "y": Color(red: 0xF2 / 255, green: 0xD2 / 255, blue: 0x3A / 255),
        "w": Color(red: 0xF7 / 255, green: 0xF3 / 255, blue: 0xC8 / 255),
    ]

    var body: some View {
        Canvas { context, size in
            context.fill(
                Path(CGRect(origin: .zero, size: size)),
                with: .linearGradient(
                    Gradient(colors: [Color(red: 0x2A / 255, green: 0x8F / 255, blue: 0xC4 / 255), Color(red: 0x14 / 255, green: 0x5E / 255, blue: 0x8E / 255)]),
                    startPoint: .zero,
                    endPoint: CGPoint(x: 0, y: size.height)
                )
            )
            let pixel = 6.0
            let originX = (size.width - Double(Self.rows[0].count) * pixel) / 2
            let originY = size.height * 0.24
            for (row, line) in Self.rows.enumerated() {
                for (column, character) in line.enumerated() {
                    guard let fill = Self.fills[character] else { continue }
                    context.fill(
                        Path(CGRect(x: originX + Double(column) * pixel, y: originY + Double(row) * pixel, width: pixel, height: pixel)),
                        with: .color(fill)
                    )
                }
            }
        }
    }
}

private struct BelieveCardCarouselDateRow: View {
    let date: String

    var body: some View {
        HStack(spacing: 8) {
            Text("→")
                .font(BelieveCardCarouselAssets.font(BelieveCardCarouselAssets.semibold, size: 12))
                .foregroundStyle(BelieveCardCarouselPalette.ink)
                .frame(width: 16)
            Text(date)
                .font(BelieveCardCarouselAssets.font(BelieveCardCarouselAssets.medium, size: 14))
                .monospacedDigit()
                .foregroundStyle(BelieveCardCarouselPalette.meta)
        }
        .fixedSize()
    }
}

private struct BelieveCardCarouselMakerRow: View {
    let data: BelieveCardCarouselCardData

    var body: some View {
        HStack(spacing: 8) {
            makerIcon
                .frame(width: 16, height: 16)
                .clipShape(Circle())
            Text(data.maker)
                .font(BelieveCardCarouselAssets.font(BelieveCardCarouselAssets.medium, size: 14))
                .foregroundStyle(BelieveCardCarouselPalette.meta)
        }
        .fixedSize()
    }

    @ViewBuilder
    private var makerIcon: some View {
        if data.artwork == .mark {
            ZStack {
                Circle().fill(Color(red: 0, green: 0xD4 / 255, blue: 0x5F / 255))
                BelieveCardCarouselBrandMark(size: 9, color: .white)
            }
        } else if data.hasAvatar {
            Image(uiImage: BelieveCardCarouselAssets.avatar)
                .resizable()
                .scaledToFill()
        } else {
            Color(red: 0xE6 / 255, green: 0xC9 / 255, blue: 0xB5 / 255)
        }
    }
}
