import SwiftUI

/// The same bottom-anchored composition as the source. Only the sheet travels; fog and CTA stay pinned.
struct IconMorphSheetCanvas: View {
    typealias A = IconMorphSheetAssets
    typealias T = IconMorphSheetTimeline
    let layout: IconMorphSheetLayout
    let frame: T.Frame
    let icon: IconMorphSheetIcon
    let reduced: Bool
    let autoPlay: Bool
    let pixelRatio: Double

    var body: some View {
        Canvas { context, _ in
            drawPage(context)
            drawSheet(context)
            drawButton(context)
            if autoPlay && !reduced { drawMarker(context) }
        }
        .accessibilityHidden(true)
    }

    private func fill(_ context: GraphicsContext, _ rect: CGRect, _ color: Color, radius: Double = 0) {
        context.fill(Path(roundedRect: rect, cornerRadius: radius), with: .color(color))
    }

    private func gradient(_ context: GraphicsContext, _ path: Path, _ colors: [Color], _ start: CGPoint, _ end: CGPoint) {
        context.fill(path, with: .linearGradient(Gradient(colors: colors), startPoint: start, endPoint: end))
    }

    private func text(_ context: GraphicsContext, _ value: String, x: Double, y: Double, size: Double, bold: Bool = false, color: UInt32 = 0xF4F5F8, anchor: UnitPoint = .center) {
        let view = Text(verbatim: value).font(.custom(bold ? "Inter24pt-Bold" : "Inter24pt-Regular", fixedSize: size)).foregroundStyle(A.color(color))
        context.draw(view, at: CGPoint(x: x, y: y), anchor: anchor)
    }

    private func stroke(_ context: GraphicsContext, _ points: [CGPoint], color: Color, width: Double) {
        var path = Path()
        for (i, point) in points.enumerated() {
            if i == 0 { path.move(to: point) } else { path.addLine(to: point) }
        }
        context.stroke(path, with: .color(color), style: StrokeStyle(lineWidth: width, lineCap: .round, lineJoin: .round))
    }

    private func coin(_ context: GraphicsContext, x: Double, y: Double, size: Double) {
        let rect = CGRect(x: x, y: y, width: size, height: size)
        gradient(context, Path(ellipseIn: rect), [A.color(0xFFF17A), A.color(0xFFE23A), A.color(0xCBA614)], CGPoint(x: x, y: y), CGPoint(x: x, y: y + size))
        fill(context, CGRect(x: x + size / 4, y: y + size / 12, width: size / 2, height: size / 12), A.color(0xFFF5B8), radius: 1)
    }

    private func puck(_ context: GraphicsContext, x: Double, y: Double, kind: Int) {
        var c = context
        let size = kind == 0 ? 32.0 : 36.0
        let scale = 1 - 0.08 * (frame.puck == kind ? frame.puckPress : 0)
        c.translateBy(x: x + 22, y: y + 22)
        c.scaleBy(x: scale, y: scale)
        let rect = CGRect(x: -size / 2, y: -size / 2, width: size, height: size)
        c.clip(to: Path(ellipseIn: rect))
        gradient(c, Path(ellipseIn: rect), [A.color(kind == 0 ? 0x23242C : 0x25262E), A.color(kind == 0 ? 0x17181D : 0x1A1B21)], CGPoint(x: 0, y: -size / 2), CGPoint(x: 0, y: size / 2))
        fill(c, CGRect(x: -size / 2 + 6, y: -size / 2 + 1, width: size - 12, height: 1), .white.opacity(0.10), radius: 1)
        let ink = A.color(0xC9CBD3)
        if kind == 0 {
            stroke(c, [CGPoint(x: -4.6, y: -4.6), CGPoint(x: 4.6, y: 4.6)], color: ink, width: 2.25)
            stroke(c, [CGPoint(x: -4.6, y: 4.6), CGPoint(x: 4.6, y: -4.6)], color: ink, width: 2.25)
        } else if kind == 1 {
            stroke(c, [CGPoint(x: -7, y: 0), CGPoint(x: 7, y: 0)], color: ink, width: 2.25)
            stroke(c, [CGPoint(x: -2, y: -5), CGPoint(x: -7, y: 0), CGPoint(x: -2, y: 5)], color: ink, width: 2.25)
        } else {
            c.stroke(Path(ellipseIn: CGRect(x: -7.4, y: -7.4, width: 14.8, height: 14.8)), with: .color(ink), lineWidth: 2.25)
            fill(c, CGRect(x: -1.125, y: -4.25, width: 2.25, height: 2.25), ink, radius: 1.125)
            fill(c, CGRect(x: -1.125, y: -0.25, width: 2.25, height: 5), ink, radius: 1.125)
        }
    }

    private func drawPage(_ context: GraphicsContext) {
        let l = layout
        var c = context
        c.opacity = 1 - frame.open * 0.3
        puck(c, x: 12, y: l.headerY - 22, kind: 1)
        puck(c, x: l.width - 56, y: l.headerY - 22, kind: 2)
        coin(c, x: l.width / 2 - 29, y: l.headerY - 6, size: 12)
        text(c, "noku", x: l.width / 2 - 11, y: l.headerY, size: 17, bold: true, anchor: .leading)
        var avatar = c
        let avatarRect = CGRect(x: (l.width - l.avatarSize) / 2, y: l.avatarY - l.avatarSize / 2, width: l.avatarSize, height: l.avatarSize)
        avatar.clip(to: Path(roundedRect: avatarRect, cornerRadius: l.compact ? 12 : 14))
        avatar.draw(A.avatar, in: avatarRect)
        if !l.compact {
            text(c, "Mina P.", x: l.width / 2 - 8, y: l.headerY + 104, size: 13, color: 0x8E909A)
            var shield = Path()
            let x = l.width / 2 + 18
            let y = l.headerY + 98
            shield.move(to: CGPoint(x: x + 6, y: y))
            shield.addLines([CGPoint(x: x + 11, y: y + 2), CGPoint(x: x + 11, y: y + 6)])
            shield.addCurve(to: CGPoint(x: x + 6, y: y + 12), control1: CGPoint(x: x + 11, y: y + 9), control2: CGPoint(x: x + 8, y: y + 11))
            shield.addCurve(to: CGPoint(x: x + 1, y: y + 6), control1: CGPoint(x: x + 4, y: y + 11), control2: CGPoint(x: x + 1, y: y + 9))
            shield.addLine(to: CGPoint(x: x + 1, y: y + 2))
            shield.closeSubpath()
            c.fill(shield, with: .color(A.color(0x3D63F5)))
            stroke(c, [CGPoint(x: x + 3.7, y: y + 5.8), CGPoint(x: x + 5.3, y: y + 7.4), CGPoint(x: x + 8.3, y: y + 4.4)], color: .white, width: 1.5)
        }
        text(c, "To Mina Park", x: l.width / 2, y: l.titleY, size: l.compact ? 22 : 28, bold: true)
        text(c, "$24.00", x: l.width / 2, y: l.titleY + l.titleLine, size: l.compact ? 22 : 28, bold: true)
        if !l.compact { text(c, "Send it now?", x: l.width / 2, y: l.titleY + 2 * l.titleLine, size: 28, bold: true, color: 0x6E707A) }
        if l.balanceVisible {
            fill(c, CGRect(x: 16, y: l.balanceTop, width: l.width - 32, height: 48), A.color(0x16171C), radius: 16)
            fill(c, CGRect(x: 32, y: l.balanceTop, width: l.width - 64, height: 1), .white.opacity(0.05))
            coin(c, x: 28, y: l.balanceTop + 10, size: 28)
            text(c, "noku balance", x: 68, y: l.balanceTop + 14, size: 15, bold: true, anchor: .leading)
            text(c, "$182.40 available", x: 68, y: l.balanceTop + 34, size: 13, color: 0x8E909A, anchor: .leading)
            stroke(c, [CGPoint(x: l.width - 41, y: l.balanceTop + 22), CGPoint(x: l.width - 36, y: l.balanceTop + 27), CGPoint(x: l.width - 31, y: l.balanceTop + 22)], color: A.color(0x6E707A), width: 2.25)
        }
    }

    private func drawSheet(_ context: GraphicsContext) {
        let l = layout
        let f = frame
        let travel = l.travel(f, reduced: reduced)
        let h = l.sheetHeight(f.position)
        let top = l.height - h + travel
        let rect = CGRect(x: 0, y: top, width: l.width, height: h)
        let shape = UnevenRoundedRectangle(topLeadingRadius: 28, bottomLeadingRadius: 0, bottomTrailingRadius: 0, topTrailingRadius: 28, style: .circular).path(in: rect)
        var c = context
        c.opacity = reduced ? f.open : 1
        var shadow = c
        shadow.addFilter(.shadow(color: .black.opacity(0.25), radius: 24, x: 0, y: -8))
        shadow.fill(shape, with: .color(A.color(0x24262C)))
        c.clip(to: shape)
        c.fill(shape, with: .color(A.color(0x24262C)))
        gradient(c, Path(CGRect(x: 0, y: top, width: l.width, height: 240)), [A.color(0x2A2C33), A.color(0x24262C)], CGPoint(x: 0, y: top), CGPoint(x: 0, y: top + 240))
        fill(c, CGRect(x: 28, y: top, width: l.width - 56, height: 1), .white.opacity(0.06))
        for index in 0..<4 {
            var copy = c
            let distance = Double(index) - f.position
            copy.opacity *= f.contents * (1 - min(1, abs(distance) * (reduced ? 2.2 : 1.4)))
            let x = l.width / 2 + (reduced ? 0 : distance * l.width)
            let lines = A.subtitles[index]
            let copyBottom = l.stageTop - 8 + travel
            text(copy, A.titles[index], x: x, y: copyBottom - Double(lines.count) * 20 - 20, size: 20, bold: true)
            for (line, value) in lines.enumerated() {
                text(copy, value, x: x, y: copyBottom - Double(lines.count - line) * 20 + 10, size: 14, color: 0x9A9CA6)
            }
        }
        var stage = c
        stage.opacity *= f.contents
        let scale = reduced ? 1 : 0.92 + 0.08 * f.stage
        stage.translateBy(x: reduced ? 0 : -f.edge, y: l.stageTop + travel + (reduced ? 0 : 24 * (1 - f.stage)))
        stage.translateBy(x: l.width / 2, y: l.stageHeight / 2)
        stage.scaleBy(x: scale, y: scale)
        stage.translateBy(x: -l.width / 2, y: -l.stageHeight / 2)
        let stageRect = CGRect(x: 0, y: 0, width: l.width, height: l.stageHeight)
        stage.fill(Path(stageRect), with: .shader(icon.shader(size: stageRect.size, unit: l.unit, pixelRatio: pixelRatio), bounds: stageRect))
        var dots = c
        dots.opacity *= f.contents
        var dotX = l.width / 2 - 27
        for index in 0..<4 {
            let weight = max(0, 1 - abs(f.position - Double(index)))
            let width = 6 + 12 * weight
            // Reanimated's default RGB interpolation uses gamma 2.2.
            let channels = zip([90.0, 93, 102], [244.0, 245, 248]).map { a, b in pow(T.mix(pow(a / 255, 2.2), pow(b / 255, 2.2), weight), 1 / 2.2) }
            fill(dots, CGRect(x: dotX, y: l.buttonTop - 30 + travel, width: width, height: 6), Color(red: channels[0], green: channels[1], blue: channels[2]), radius: 3)
            dotX += width + 6
        }
        var close = c
        close.opacity *= f.contents
        puck(close, x: l.width - 54, y: top + 6, kind: 0)
        // Counter-travel keeps the fade in physical screen coordinates.
        let fogTop = l.buttonTop - 20
        gradient(c, Path(CGRect(x: 0, y: fogTop, width: l.width, height: 16)), [A.color(0x24262C).opacity(0), A.color(0x24262C)], CGPoint(x: 0, y: fogTop), CGPoint(x: 0, y: fogTop + 16))
        fill(c, CGRect(x: 0, y: fogTop + 16, width: l.width, height: l.height - fogTop - 16), A.color(0x24262C))
    }

    private func drawButton(_ context: GraphicsContext) {
        let l = layout
        var c = context
        let scale = 1 - 0.03 * frame.buttonPress
        c.translateBy(x: l.width / 2, y: l.buttonTop + 28)
        c.scaleBy(x: scale, y: scale)
        let rect = CGRect(x: -(l.width - 32) / 2, y: -28, width: l.width - 32, height: 56)
        let pill = Path(roundedRect: rect, cornerRadius: 28)
        var shadow = c
        shadow.addFilter(.shadow(color: A.color(0xFFE23A).opacity(0.14), radius: 20, x: 0, y: 6))
        shadow.fill(pill, with: .color(A.color(0xFFE23A)))
        gradient(c, pill, [A.color(0xFFEC4F), A.color(0xFFDD1F)], CGPoint(x: 0, y: -28), CGPoint(x: 0, y: 28))
        c.clip(to: pill)
        fill(c, CGRect(x: rect.minX + 20, y: -28, width: rect.width - 40, height: 1), .white.opacity(0.55))
        fill(c, CGRect(x: rect.minX + 20, y: 27, width: rect.width - 40, height: 1), A.color(0x806000).opacity(0.26))
        c.fill(pill, with: .color(A.color(0x4D3D00).opacity(max(0, frame.buttonPress) * 0.09)))
        for i in 0..<3 {
            let label = T.buttonLabel(frame, i)
            var ink = c
            ink.opacity = label.opacity
            text(ink, A.labels[i], x: 0, y: reduced ? 0 : label.y, size: 17, bold: true, color: 0x17181D)
        }
    }

    private func drawMarker(_ context: GraphicsContext) {
        let f = frame
        let l = layout
        let x = f.markerKind == 2 ? l.width / 2 + f.markerX - 201 : l.width / 2
        let y = f.markerKind == 0 ? l.buttonTop + 28 : f.markerKind == 2 ? l.stageTop + l.stageHeight - (744 - f.markerY) : l.stageTop + l.stageHeight / 2
        var c = context
        c.opacity = f.markerOpacity
        let radius = 18 * (1 - 0.14 * f.markerPress)
        let shape = Path(ellipseIn: CGRect(x: x - radius, y: y - radius, width: radius * 2, height: radius * 2))
        c.fill(shape, with: .color(.white.opacity(0.22)))
        c.stroke(shape, with: .color(.white.opacity(0.35)), lineWidth: 1)
    }
}
