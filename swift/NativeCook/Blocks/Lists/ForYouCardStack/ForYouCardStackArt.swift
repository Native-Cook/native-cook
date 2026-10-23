import SwiftUI

struct ForYouCardStackCoverView: View {
    let kind: ForYouCardStackCoverKind

    var body: some View {
        ZStack(alignment: .topLeading) {
            ForYouCardStackCoverDrawing(kind: kind)
            ForYouCardStackCoverText(kind: kind)
        }
        .frame(width: ForYouCardStackLayout.coverWidth, height: ForYouCardStackLayout.coverHeight, alignment: .topLeading)
        .clipped()
    }
}

private struct ForYouCardStackCoverDrawing: View {
    let kind: ForYouCardStackCoverKind

    var body: some View {
        switch kind {
        case .loud:
            Canvas { context, _ in
                context.fill(Path(CGRect(x: 0, y: 0, width: 200, height: 300)), with: .color(ForYouCardStackPalette.hex(0xEE3A0A)))
                context.fill(ForYouCardStackPolygon(points: [(0, 170), (170, 98), (170, 135), (108, 300), (0, 300)]), with: .color(ForYouCardStackPalette.hex(0xF6C80E)))
                context.fill(ForYouCardStackPolygon(points: [(0, 0), (170, 0), (170, 98), (0, 170)]), with: .color(ForYouCardStackPalette.hex(0xF2A3BC)))
                for index in 0..<6 {
                    let width: CGFloat = index % 3 == 2 ? 34 : 46
                    context.fill(Path(CGRect(x: 118, y: 112 + CGFloat(index) * 9, width: width, height: 4)), with: .color(ForYouCardStackPalette.hex(0x2A1A14)))
                }
                for index in 0..<5 {
                    let width: CGFloat = index % 2 == 1 ? 46 : 58
                    context.fill(Path(CGRect(x: 12, y: 232 + CGFloat(index) * 8, width: width, height: 3)), with: .color(ForYouCardStackPalette.hex(0x2A1A14)))
                }
            }
        case .seeing:
            Canvas { context, _ in
                context.fill(Path(CGRect(x: 0, y: 0, width: 200, height: 300)), with: .color(ForYouCardStackPalette.hex(0xE9B200)))
                for (index, centerY) in [CGFloat(98), CGFloat(196)].enumerated() {
                    var eye = Path()
                    eye.move(to: CGPoint(x: 30, y: centerY))
                    eye.addQuadCurve(to: CGPoint(x: 170, y: centerY), control: CGPoint(x: 100, y: centerY - 52))
                    eye.addQuadCurve(to: CGPoint(x: 30, y: centerY), control: CGPoint(x: 100, y: centerY + 52))
                    context.fill(eye, with: .color(ForYouCardStackPalette.hex(0xFFFDF6)))
                    context.fill(Path(ellipseIn: CGRect(x: 76, y: centerY - 24, width: 48, height: 48)), with: .color(index == 0 ? ForYouCardStackPalette.hex(0x2D55D6) : ForYouCardStackPalette.hex(0xE2321E)))
                    context.fill(Path(ellipseIn: CGRect(x: 89, y: centerY - 11, width: 22, height: 22)), with: .color(ForYouCardStackPalette.hex(0x16161A)))
                }
            }
        case .objects:
            Canvas { context, _ in
                context.fill(Path(CGRect(x: 0, y: 0, width: 200, height: 300)), with: .color(ForYouCardStackPalette.hex(0xE2C81C)))
                var pot = Path()
                pot.move(to: CGPoint(x: 28, y: 300))
                pot.addLine(to: CGPoint(x: 30, y: 262))
                pot.addQuadCurve(to: CGPoint(x: 46, y: 214), control: CGPoint(x: 24, y: 236))
                pot.addLine(to: CGPoint(x: 52, y: 184))
                pot.addQuadCurve(to: CGPoint(x: 58, y: 166), control: CGPoint(x: 49, y: 172))
                pot.addLine(to: CGPoint(x: 60, y: 152))
                pot.addLine(to: CGPoint(x: 66, y: 152))
                pot.addLine(to: CGPoint(x: 68, y: 166))
                pot.addQuadCurve(to: CGPoint(x: 74, y: 184), control: CGPoint(x: 77, y: 172))
                pot.addLine(to: CGPoint(x: 80, y: 214))
                pot.addQuadCurve(to: CGPoint(x: 96, y: 244), control: CGPoint(x: 92, y: 226))
                pot.addLine(to: CGPoint(x: 104, y: 228))
                pot.addQuadCurve(to: CGPoint(x: 132, y: 214), control: CGPoint(x: 118, y: 204))
                pot.addQuadCurve(to: CGPoint(x: 120, y: 252), control: CGPoint(x: 140, y: 230))
                pot.addQuadCurve(to: CGPoint(x: 104, y: 276), control: CGPoint(x: 108, y: 266))
                pot.addLine(to: CGPoint(x: 106, y: 300))
                pot.closeSubpath()
                context.fill(pot, with: .color(ForYouCardStackPalette.hex(0xC2401C)))
                var handle = Path()
                handle.move(to: CGPoint(x: 104, y: 236))
                handle.addQuadCurve(to: CGPoint(x: 136, y: 220), control: CGPoint(x: 126, y: 206))
                handle.addQuadCurve(to: CGPoint(x: 112, y: 262), control: CGPoint(x: 142, y: 240))
                context.stroke(handle, with: .color(ForYouCardStackPalette.hex(0x2A1A10)), lineWidth: 2.4)
                context.fill(Path(CGRect(x: 132, y: 196, width: 40, height: 3)), with: .color(ForYouCardStackPalette.hex(0x8D7A3A)))
                context.fill(Path(CGRect(x: 140, y: 204, width: 32, height: 3)), with: .color(ForYouCardStackPalette.hex(0x8D7A3A)))
            }
        case .pages:
            Canvas { context, _ in
                context.fill(Path(CGRect(x: 0, y: 0, width: 200, height: 300)), with: .color(ForYouCardStackPalette.hex(0xEC3A1E)))
                for index in 0..<3 {
                    context.fill(Path(CGRect(x: 40, y: 196 + CGFloat(index) * 9, width: 120, height: 3)), with: .color(ForYouCardStackPalette.hex(0xF7B8A8)))
                }
                context.fill(Path(CGRect(x: 58, y: 252, width: 86, height: 7)), with: .color(.white))
            }
        case .sky:
            Image(uiImage: ForYouCardStackAssets.image(named: "ForYouCardStack-cover-sky.png"))
                .resizable()
                .scaledToFill()
                .frame(width: 200, height: 300)
        case .arches:
            Canvas { context, _ in
                context.fill(Path(CGRect(x: 0, y: 0, width: 200, height: 300)), with: .color(ForYouCardStackPalette.hex(0x3A6FE0)))
                context.fill(Path(CGRect(x: 0, y: 0, width: 96, height: 86)), with: .color(ForYouCardStackPalette.hex(0x2FA05C)))
                var arch = Path()
                arch.move(to: CGPoint(x: 0, y: 300))
                arch.addLine(to: CGPoint(x: 0, y: 150))
                arch.addQuadCurve(to: CGPoint(x: 48, y: 98), control: CGPoint(x: 0, y: 98))
                arch.addQuadCurve(to: CGPoint(x: 96, y: 150), control: CGPoint(x: 96, y: 98))
                arch.addLine(to: CGPoint(x: 96, y: 300))
                arch.closeSubpath()
                context.fill(arch, with: .color(ForYouCardStackPalette.hex(0x14603C)))
                context.fill(Path(ellipseIn: CGRect(x: 100, y: 4, width: 96, height: 96)), with: .color(ForYouCardStackPalette.hex(0xF1EEE6)))
                for y in [CGFloat(120), CGFloat(170), CGFloat(220)] {
                    var arc = Path()
                    arc.move(to: CGPoint(x: 100, y: y))
                    arc.addArc(center: CGPoint(x: 148, y: y), radius: 48, startAngle: .degrees(180), endAngle: .degrees(360), clockwise: true)
                    arc.closeSubpath()
                    context.fill(arc, with: .color(ForYouCardStackPalette.hex(0xA9C6F2)))
                }
            }
        case .grid:
            Canvas { context, _ in
                context.fill(Path(CGRect(x: 0, y: 0, width: 200, height: 300)), with: .color(ForYouCardStackPalette.hex(0xF2C21A)))
                var lines = Path()
                lines.move(to: CGPoint(x: 120, y: 40))
                lines.addQuadCurve(to: CGPoint(x: 176, y: 60), control: CGPoint(x: 160, y: 30))
                lines.move(to: CGPoint(x: 150, y: 120))
                lines.addLine(to: CGPoint(x: 196, y: 96))
                context.stroke(lines, with: .color(ForYouCardStackPalette.hex(0x1A1410)), lineWidth: 3)
            }
        case .lines:
            Canvas { context, _ in
                context.fill(Path(CGRect(x: 0, y: 0, width: 200, height: 300)), with: .color(ForYouCardStackPalette.hex(0x5EC3F2)))
                let lineYs: [CGFloat] = [40, 90, 140, 190, 240]
                for (index, y) in lineYs.enumerated() {
                    var line = Path()
                    line.move(to: CGPoint(x: -10, y: y))
                    line.addCurve(to: CGPoint(x: 120, y: y), control1: CGPoint(x: 40, y: y - 40), control2: CGPoint(x: 80, y: y + 50))
                    line.addCurve(to: CGPoint(x: 220, y: y + 10), control1: CGPoint(x: 160, y: y - 50), control2: CGPoint(x: 190, y: y - 30))
                    context.stroke(line, with: .color(index.isMultiple(of: 2) ? ForYouCardStackPalette.hex(0x0E2B78) : ForYouCardStackPalette.hex(0x1F3FA8)), style: StrokeStyle(lineWidth: 10, lineCap: .round))
                }
                context.fill(Path(CGRect(x: 18, y: 226, width: 70, height: 46)), with: .color(.white))
            }
        case .shapes:
            Canvas { context, _ in
                context.fill(Path(CGRect(x: 0, y: 0, width: 200, height: 300)), with: .color(ForYouCardStackPalette.hex(0xF6A6C8)))
                context.fill(Path(CGRect(x: 0, y: 170, width: 200, height: 130)), with: .color(ForYouCardStackPalette.hex(0xFFD43A)))
                context.fill(Path(CGRect(x: 24, y: 30, width: 86, height: 110)), with: .color(ForYouCardStackPalette.hex(0xF04A3A)))
                context.fill(Path(CGRect(x: 110, y: 196, width: 64, height: 70)), with: .color(.white))
                for index in 0..<5 {
                    context.fill(Path(CGRect(x: 120, y: 206 + CGFloat(index) * 11, width: 44, height: 4)), with: .color(ForYouCardStackPalette.hex(0x2B2B2B)))
                }
                context.fill(Path(ellipseIn: CGRect(x: 124, y: 60, width: 52, height: 52)), with: .color(.white.opacity(0.8)))
            }
        }
    }
}

private struct ForYouCardStackCoverText: View {
    let kind: ForYouCardStackCoverKind

    var body: some View {
        switch kind {
        case .loud:
            Text("EXTRA")
                .font(.custom(ForYouCardStackFonts.poster, fixedSize: 52))
                .foregroundStyle(ForYouCardStackPalette.hex(0xFBD7E2))
                .shadow(color: ForYouCardStackPalette.hex(0xE8456A), radius: 1.5)
                .frame(width: 150, height: 50, alignment: .leading)
                .position(x: 12 + 75, y: 14 + 25)
            Text("LOUD")
                .font(.custom(ForYouCardStackFonts.poster, fixedSize: 52))
                .foregroundStyle(ForYouCardStackPalette.hex(0xFBD7E2))
                .shadow(color: ForYouCardStackPalette.hex(0xE8456A), radius: 1.5)
                .frame(width: 150, height: 50, alignment: .leading)
                .position(x: 12 + 75, y: 64 + 25)
        case .objects:
            ForYouCardStackCoverLines(
                lines: ["SHAPE", "of EVERYDAY", "OBJECTS"],
                top: 40,
                lineHeight: 26,
                width: 164,
                centerX: 112,
                font: .system(size: 18, weight: .regular),
                color: ForYouCardStackPalette.hex(0x6A5A2A),
                tracking: 2.5
            )
            Text("The")
                .font(.custom("Georgia-Italic", fixedSize: 12))
                .foregroundStyle(ForYouCardStackPalette.hex(0x6A5A2A))
                .position(x: 14 + 12, y: 40 + 8)
        case .pages:
            ForYouCardStackCoverLines(
                lines: ["Layout", "of", "Books"],
                top: 34,
                lineHeight: 46,
                width: 200,
                centerX: 100,
                font: .custom("Georgia-Bold", fixedSize: 40),
                color: .white
            )
            Text("The")
                .font(.custom("Georgia-Italic", fixedSize: 12))
                .foregroundStyle(.white)
                .position(x: 92 + 12, y: 24 + 8)
        case .sky:
            Text("THE")
                .font(.custom("Georgia-Italic", fixedSize: 26))
                .tracking(2)
                .foregroundStyle(.white)
                .frame(width: 54, height: 30, alignment: .leading)
                .position(x: 30 + 27, y: 18 + 15)
            Text("SKY")
                .font(.custom("Georgia-Italic", fixedSize: 26))
                .tracking(2)
                .foregroundStyle(.white)
                .frame(width: 54, height: 30, alignment: .leading)
                .position(x: 70 + 27, y: 50 + 15)
            Text("DAYS")
                .font(.custom("Georgia-Italic", fixedSize: 26))
                .tracking(2)
                .foregroundStyle(.white)
                .frame(width: 70, height: 30, alignment: .leading)
                .position(x: 112 + 35, y: 82 + 15)
        case .arches:
            ZStack(alignment: .topLeading) {
                ForYouCardStackPalette.hex(0xFFFDF7)
                ForYouCardStackCoverLines(
                    lines: ["MARA", "OKONJO"],
                    top: 8,
                    lineHeight: 18,
                    width: 104,
                    centerX: 60,
                    font: .custom("Georgia", fixedSize: 17),
                    color: ForYouCardStackPalette.hex(0x23232A),
                    tracking: 1,
                    alignment: .leading
                )
            }
            .frame(width: 120, height: 50, alignment: .topLeading)
            .position(x: 60, y: 275)
        case .grid:
            Text("CRASHING")
                .font(.custom(ForYouCardStackFonts.poster, fixedSize: 96))
                .foregroundStyle(ForYouCardStackPalette.hex(0xE2231A))
                .frame(width: 360, height: 100, alignment: .center)
                .rotationEffect(.degrees(-72))
                .position(x: -70 + 180, y: 100 + 50)
        case .seeing, .lines, .shapes:
            EmptyView()
        }
    }
}

private struct ForYouCardStackCoverLines: View {
    let lines: [String]
    let top: CGFloat
    let lineHeight: CGFloat
    let width: CGFloat
    let centerX: CGFloat
    let font: Font
    let color: Color
    var tracking: CGFloat = 0
    var alignment: Alignment = .center

    var body: some View {
        ForEach(lines, id: \.self) { line in
            let index = lines.firstIndex(of: line) ?? 0
            Text(line)
                .font(font)
                .tracking(tracking)
                .foregroundStyle(color)
                .frame(width: width, height: lineHeight, alignment: alignment)
                .position(x: centerX, y: top + CGFloat(index) * lineHeight + lineHeight / 2)
        }
    }
}

private func ForYouCardStackPolygon(points: [(CGFloat, CGFloat)]) -> Path {
    var path = Path()
    guard let first = points.first else { return path }
    path.move(to: CGPoint(x: first.0, y: first.1))
    for point in points.dropFirst() {
        path.addLine(to: CGPoint(x: point.0, y: point.1))
    }
    path.closeSubpath()
    return path
}

struct ForYouCardStackSpreadArt: View {
    var body: some View {
        ForYouCardStackNormalizedPage { context in
            context.fill(Path(CGRect(x: 0, y: 0, width: 100, height: 146)), with: .color(ForYouCardStackPalette.hex(0xFBF6EA)))
            context.fill(Path(CGRect(x: 6, y: 6, width: 42, height: 42)), with: .color(ForYouCardStackPalette.hex(0xF7C531)))
            context.fill(Path(ellipseIn: CGRect(x: 54, y: 7, width: 40, height: 40)), with: .color(ForYouCardStackPalette.hex(0x2D55D6)))
            var arc = Path()
            arc.move(to: CGPoint(x: 8, y: 96))
            arc.addArc(center: CGPoint(x: 48, y: 96), radius: 40, startAngle: .degrees(180), endAngle: .degrees(360), clockwise: false)
            arc.closeSubpath()
            context.fill(arc, with: .color(ForYouCardStackPalette.hex(0xE2321E)))
            var star = Path()
            star.move(to: CGPoint(x: 50, y: 104))
            let starPoints: [(CGFloat, CGFloat)] = [(54, 118), (68, 114), (58, 124), (68, 134), (54, 130), (50, 144), (46, 130), (32, 134), (42, 124), (32, 114), (46, 118)]
            for point in starPoints {
                star.addLine(to: CGPoint(x: point.0, y: point.1))
            }
            star.closeSubpath()
            context.fill(star, with: .color(ForYouCardStackPalette.hex(0x16161A)))
            context.fill(Path(ellipseIn: CGRect(x: 9, y: 117, width: 18, height: 18)), with: .color(ForYouCardStackPalette.hex(0x2FA67A)))
            context.fill(Path(CGRect(x: 74, y: 110, width: 20, height: 30)), with: .color(ForYouCardStackPalette.hex(0xF29FC0)))
            var triangle = Path()
            triangle.move(to: CGPoint(x: 58, y: 52))
            triangle.addLine(to: CGPoint(x: 94, y: 52))
            triangle.addLine(to: CGPoint(x: 76, y: 82))
            triangle.closeSubpath()
            context.fill(triangle, with: .color(ForYouCardStackPalette.hex(0x16161A)))
            context.stroke(Path(ellipseIn: CGRect(x: 16, y: 58, width: 20, height: 20)), with: .color(ForYouCardStackPalette.hex(0x16161A)), lineWidth: 4)
        }
    }
}

struct ForYouCardStackSpreadText: View {
    var body: some View {
        ForYouCardStackNormalizedPage { context in
            context.fill(Path(CGRect(x: 0, y: 0, width: 100, height: 146)), with: .color(.white))
            context.fill(Path(CGRect(x: 10, y: 14, width: 40, height: 4)), with: .color(ForYouCardStackPalette.hex(0x2B2B2B)))
            for index in 0..<9 {
                let width: CGFloat = index % 4 == 3 ? 52 : 78
                context.fill(Path(CGRect(x: 10, y: 28 + CGFloat(index) * 7, width: width, height: 2.4)), with: .color(ForYouCardStackPalette.hex(0x9A9AA2)))
            }
        }
    }
}

struct ForYouCardStackSpreadPlate: View {
    var body: some View {
        ForYouCardStackNormalizedPage { context in
            context.fill(Path(CGRect(x: 0, y: 0, width: 100, height: 146)), with: .color(ForYouCardStackPalette.hex(0xD9D9D9)))
            context.fill(Path(CGRect(x: 8, y: 10, width: 84, height: 112)), with: .color(ForYouCardStackPalette.hex(0x8E8E8E)))
            context.fill(Path(ellipseIn: CGRect(x: 28, y: 36, width: 44, height: 44)), with: .color(ForYouCardStackPalette.hex(0xC9C9C9)))
            var bust = Path()
            bust.move(to: CGPoint(x: 18, y: 122))
            bust.addQuadCurve(to: CGPoint(x: 82, y: 122), control: CGPoint(x: 50, y: 76))
            bust.closeSubpath()
            context.fill(bust, with: .color(ForYouCardStackPalette.hex(0x6E6E6E)))
            context.fill(Path(CGRect(x: 8, y: 130, width: 50, height: 3)), with: .color(ForYouCardStackPalette.hex(0x555555)))
        }
    }
}

private struct ForYouCardStackNormalizedPage: View {
    let draw: (inout GraphicsContext) -> Void

    var body: some View {
        Canvas { context, _ in
            draw(&context)
        }
        .frame(width: 100, height: 146, alignment: .topLeading)
        .scaleEffect(x: 2, y: 300 / 146, anchor: .topLeading)
        .frame(width: 200, height: 300, alignment: .topLeading)
        .clipped()
    }
}
