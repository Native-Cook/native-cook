import SwiftUI
import UIKit

struct PullLeverRefresh: View {
    static let loop: TimeInterval = PullLeverRefreshTokens.loop

    let autoPlay: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @StateObject private var model = PullLeverRefreshModel()

    init(autoPlay: Bool = false) {
        self.autoPlay = autoPlay
    }

    var body: some View {
        GeometryReader { proxy in
            let safeTop = max(proxy.safeAreaInsets.top, 44)
            let layout = PullLeverRefreshLayout(size: proxy.size, safeTop: safeTop)
            ZStack {
                PullLeverRefreshStaticArtwork(layout: layout)
                PullLeverRefreshDynamicArtwork(model: model, layout: layout, reduceMotion: reduceMotion)
                PullLeverRefreshCaption(
                    stage: model.stage,
                    seconds: min(27, Int((model.brew * 27).rounded())),
                    safeTop: safeTop
                )
                PullLeverRefreshTouchView(
                    onBegin: model.begin,
                    onMove: model.move,
                    onEnd: model.end
                )
            }
            .frame(width: proxy.size.width, height: proxy.size.height, alignment: .topLeading)
            .contentShape(Rectangle())
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("Espresso lever")
            .accessibilityValue(model.stage.subtitle(seconds: min(27, Int((model.brew * 27).rounded()))))
            .accessibilityHint("Pull the lever three times to build pressure, then release to brew.")
            .accessibilityAddTraits(.isButton)
            .accessibilityAction { model.accessibilityActivate() }
            .onAppear { model.configure(autoPlay: autoPlay, reduceMotion: reduceMotion, layout: layout) }
            .onChange(of: layout) { _, next in
                model.configure(autoPlay: autoPlay, reduceMotion: reduceMotion, layout: next)
            }
            .onChange(of: reduceMotion) { _, next in
                model.configure(autoPlay: autoPlay, reduceMotion: next, layout: layout)
            }
            .onDisappear { model.disappear() }
        }
        .ignoresSafeArea()
        .statusBarHidden()
        .persistentSystemOverlays(.hidden)
    }
}

private struct PullLeverRefreshStaticArtwork: View {
    let layout: PullLeverRefreshLayout

    var body: some View {
        Image(uiImage: PullLeverRefreshArtworkCache.image(for: layout))
            .resizable()
            .interpolation(.high)
            .frame(width: layout.size.width, height: layout.size.height)
            .allowsHitTesting(false)
    }
}

@MainActor
private enum PullLeverRefreshArtworkCache {
    private static let cache = NSCache<NSString, UIImage>()

    static func image(for layout: PullLeverRefreshLayout) -> UIImage {
        let key = "\(Int(layout.size.width.rounded()))x\(Int(layout.size.height.rounded()))-\(Int(layout.safeTop.rounded()))" as NSString
        if let image = cache.object(forKey: key) { return image }
        let renderer = UIGraphicsImageRenderer(size: layout.size)
        let image = renderer.image { output in
            drawBackground(in: output.cgContext, size: layout.size)
            output.cgContext.saveGState()
            output.cgContext.translateBy(x: layout.pivot.x, y: layout.pivot.y)
            output.cgContext.scaleBy(x: layout.scale, y: layout.scale)
            drawMachine(in: output.cgContext)
            output.cgContext.restoreGState()
            drawGrain(in: output.cgContext, size: layout.size)
        }
        cache.setObject(image, forKey: key)
        return image
    }

    private static func drawBackground(in context: CGContext, size: CGSize) {
        let colors = [
            UIColor(red: 0.027, green: 0.024, blue: 0.021, alpha: 1).cgColor,
            UIColor(red: 0.105, green: 0.097, blue: 0.087, alpha: 1).cgColor,
            UIColor(red: 0.034, green: 0.031, blue: 0.028, alpha: 1).cgColor,
        ] as CFArray
        let gradient = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: colors, locations: [0, 0.44, 1])!
        context.drawRadialGradient(
            gradient,
            startCenter: CGPoint(x: size.width * 0.5, y: size.height * 0.46),
            startRadius: 0,
            endCenter: CGPoint(x: size.width * 0.5, y: size.height * 0.46),
            endRadius: size.height * 0.72,
            options: [.drawsAfterEndLocation]
        )
        context.setFillColor(UIColor.black.withAlphaComponent(0.18).cgColor)
        context.fill(CGRect(x: 0, y: size.height * 0.64, width: size.width, height: size.height * 0.36))
    }

    private static func drawMachine(in context: CGContext) {
        context.saveGState()
        context.setShadow(offset: CGSize(width: 0, height: 7), blur: 12, color: UIColor.black.withAlphaComponent(0.75).cgColor)
        context.setFillColor(UIColor.black.withAlphaComponent(0.55).cgColor)
        context.fillEllipse(in: CGRect(x: -158, y: 304, width: 270, height: 22))
        context.restoreGState()

        let body = UIBezierPath(roundedRect: CGRect(x: -152, y: -124, width: 94, height: 434), cornerRadius: 14)
        context.saveGState()
        body.addClip()
        drawLinearGradient(
            context,
            colors: [UIColor(white: 0.055, alpha: 1), UIColor(white: 0.19, alpha: 1), UIColor(white: 0.075, alpha: 1)],
            from: CGPoint(x: -152, y: 0),
            to: CGPoint(x: -58, y: 0),
            locations: [0, 0.55, 1]
        )
        context.restoreGState()
        context.setStrokeColor(UIColor(white: 0.28, alpha: 0.42).cgColor)
        context.setLineWidth(1.2)
        context.addPath(body.cgPath)
        context.strokePath()

        let cap = UIBezierPath(roundedRect: CGRect(x: -151, y: -124, width: 92, height: 12), cornerRadius: 10)
        context.saveGState()
        cap.addClip()
        drawLinearGradient(context, colors: [.white, UIColor(white: 0.38, alpha: 1), UIColor(white: 0.08, alpha: 1)], from: CGPoint(x: -150, y: -124), to: CGPoint(x: -62, y: -112), locations: [0, 0.23, 1])
        context.restoreGState()

        drawGauge(in: context)
        drawTimer(in: context)
        drawGroup(in: context)
        drawPortafilter(in: context)
        drawTray(in: context)
    }

    private static func drawGauge(in context: CGContext) {
        let center = CGPoint(x: -105, y: -44)
        context.saveGState()
        context.setShadow(offset: CGSize(width: 0, height: 2), blur: 4, color: UIColor.black.withAlphaComponent(0.9).cgColor)
        context.setFillColor(UIColor(white: 0.06, alpha: 1).cgColor)
        context.fillEllipse(in: CGRect(x: center.x - 38, y: center.y - 38, width: 76, height: 76))
        context.restoreGState()
        context.setStrokeColor(UIColor(white: 0.65, alpha: 0.9).cgColor)
        context.setLineWidth(1.6)
        context.strokeEllipse(in: CGRect(x: center.x - 36, y: center.y - 36, width: 72, height: 72))
        context.setStrokeColor(UIColor(white: 0.28, alpha: 1).cgColor)
        context.setLineWidth(1)
        context.strokeEllipse(in: CGRect(x: center.x - 33.5, y: center.y - 33.5, width: 67, height: 67))

        for tick in 0...30 {
            let angle = (135 + CGFloat(tick) / 30 * 270) * .pi / 180
            let major = tick % 5 == 0
            let r0: CGFloat = major ? 26.5 : 29
            let r1: CGFloat = 32
            context.setStrokeColor(UIColor(white: major ? 0.88 : 0.68, alpha: 1).cgColor)
            context.setLineWidth(major ? 1.4 : 0.8)
            context.move(to: CGPoint(x: center.x + cos(angle) * r0, y: center.y + sin(angle) * r0))
            context.addLine(to: CGPoint(x: center.x + cos(angle) * r1, y: center.y + sin(angle) * r1))
            context.strokePath()
        }
        let paragraph = NSMutableParagraphStyle()
        paragraph.alignment = .center
        let numberAttributes: [NSAttributedString.Key: Any] = [
            .font: UIFont.systemFont(ofSize: 8, weight: .semibold),
            .foregroundColor: UIColor(white: 0.9, alpha: 1),
            .paragraphStyle: paragraph,
        ]
        for value in stride(from: 0, through: 12, by: 3) {
            let angle = (135 + CGFloat(value) / 12 * 270) * .pi / 180
            let p = CGPoint(x: center.x + cos(angle) * 20 - 8, y: center.y + sin(angle) * 20 - 5)
            "\(value)".draw(in: CGRect(x: p.x, y: p.y, width: 16, height: 10), withAttributes: numberAttributes)
        }
        let unitAttributes: [NSAttributedString.Key: Any] = [
            .font: UIFont.systemFont(ofSize: 6, weight: .semibold),
            .foregroundColor: UIColor(white: 0.54, alpha: 1),
            .kern: 1.0,
            .paragraphStyle: paragraph,
        ]
        "BAR".draw(in: CGRect(x: center.x - 14, y: center.y - 7, width: 28, height: 9), withAttributes: unitAttributes)
        context.setFillColor(UIColor(white: 0.68, alpha: 1).cgColor)
        context.fillEllipse(in: CGRect(x: center.x - 2.2, y: center.y - 2.2, width: 4.4, height: 4.4))
    }

    private static func drawTimer(in context: CGContext) {
        let frame = UIBezierPath(roundedRect: CGRect(x: -134, y: 232, width: 58, height: 40), cornerRadius: 9)
        context.setFillColor(UIColor(white: 0.07, alpha: 1).cgColor)
        context.addPath(frame.cgPath)
        context.fillPath()
        context.setStrokeColor(UIColor(white: 0.28, alpha: 0.75).cgColor)
        context.setLineWidth(1.2)
        context.addPath(frame.cgPath)
        context.strokePath()
        context.setStrokeColor(UIColor.black.withAlphaComponent(0.8).cgColor)
        context.move(to: CGPoint(x: -105, y: 234))
        context.addLine(to: CGPoint(x: -105, y: 270))
        context.strokePath()

        let attributes: [NSAttributedString.Key: Any] = [
            .font: UIFont.systemFont(ofSize: 8, weight: .semibold),
            .foregroundColor: UIColor(white: 0.65, alpha: 1),
            .kern: 1.6,
        ]
        "SHOT".draw(at: CGPoint(x: -132, y: 216), withAttributes: attributes)
    }

    private static func drawGroup(in context: CGContext) {
        context.setFillColor(UIColor(white: 0.12, alpha: 1).cgColor)
        context.fill(CGRect(x: -63, y: 53, width: 15, height: 12))
        metalCylinder(in: context, rect: CGRect(x: -54, y: 48, width: 18, height: 18), radius: 3)
        metalCylinder(in: context, rect: CGRect(x: -37, y: 14, width: 74, height: 114), radius: 26)
        context.setFillColor(UIColor(white: 0.06, alpha: 0.9).cgColor)
        context.fill(CGRect(x: -34, y: 51, width: 68, height: 2))
        context.fill(CGRect(x: -34, y: 85, width: 68, height: 2))
        metalCylinder(in: context, rect: CGRect(x: -42, y: 110, width: 84, height: 18), radius: 7)
    }

    private static func drawPortafilter(in context: CGContext) {
        metalCylinder(in: context, rect: CGRect(x: -30, y: 127, width: 60, height: 21), radius: 8)
        context.setFillColor(UIColor(white: 0.78, alpha: 1).cgColor)
        context.fill(CGRect(x: -7.5, y: 146, width: 15, height: 13))
        context.setStrokeColor(UIColor(white: 0.72, alpha: 1).cgColor)
        context.setLineWidth(4)
        context.move(to: CGPoint(x: -3, y: 157))
        context.addLine(to: CGPoint(x: -10, y: 174))
        context.move(to: CGPoint(x: 3, y: 157))
        context.addLine(to: CGPoint(x: 10, y: 174))
        context.strokePath()

        context.saveGState()
        context.translateBy(x: 22, y: 136)
        context.rotate(by: 6 * .pi / 180)
        metalCylinder(in: context, rect: CGRect(x: 0, y: -4.5, width: 43, height: 9), radius: 4.5)
        drawWood(in: context, rect: CGRect(x: 42, y: -8, width: 82, height: 16), radius: 8)
        context.restoreGState()
    }

    private static func drawTray(in context: CGContext) {
        context.setFillColor(UIColor(white: 0.10, alpha: 1).cgColor)
        context.fill(CGRect(x: -72, y: 274, width: 172, height: 36))
        context.saveGState()
        context.clip(to: CGRect(x: -72, y: 274, width: 172, height: 12))
        drawLinearGradient(context, colors: [UIColor(white: 0.62, alpha: 1), UIColor(white: 0.18, alpha: 1)], from: CGPoint(x: -72, y: 274), to: CGPoint(x: -72, y: 286), locations: [0, 1])
        context.restoreGState()
        context.setFillColor(UIColor(white: 0.08, alpha: 1).cgColor)
        for x in stride(from: -68 as CGFloat, through: 96, by: 5.3) {
            context.fill(CGRect(x: x, y: 275, width: 2.3, height: 10))
        }
        context.setFillColor(UIColor(white: 0.12, alpha: 1).cgColor)
        context.fill(CGRect(x: -151, y: 310, width: 22, height: 6))
        context.fill(CGRect(x: -83, y: 310, width: 20, height: 6))
        context.fill(CGRect(x: 79, y: 310, width: 20, height: 6))
    }

    private static func metalCylinder(in context: CGContext, rect: CGRect, radius: CGFloat) {
        let path = UIBezierPath(roundedRect: rect, cornerRadius: radius)
        context.saveGState()
        path.addClip()
        drawLinearGradient(
            context,
            colors: [UIColor(white: 0.08, alpha: 1), .white, UIColor(white: 0.19, alpha: 1), UIColor(white: 0.82, alpha: 1), UIColor(white: 0.04, alpha: 1)],
            from: CGPoint(x: rect.minX, y: rect.midY),
            to: CGPoint(x: rect.maxX, y: rect.midY),
            locations: [0, 0.24, 0.45, 0.68, 1]
        )
        context.restoreGState()
        context.setStrokeColor(UIColor(white: 0.45, alpha: 0.65).cgColor)
        context.setLineWidth(0.7)
        context.addPath(path.cgPath)
        context.strokePath()
    }

    private static func drawWood(in context: CGContext, rect: CGRect, radius: CGFloat) {
        let path = UIBezierPath(roundedRect: rect, cornerRadius: radius)
        context.saveGState()
        path.addClip()
        drawLinearGradient(context, colors: [UIColor(red: 0.13, green: 0.045, blue: 0.02, alpha: 1), UIColor(red: 0.37, green: 0.16, blue: 0.09, alpha: 1), UIColor(red: 0.12, green: 0.035, blue: 0.014, alpha: 1)], from: CGPoint(x: rect.minX, y: rect.minY), to: CGPoint(x: rect.maxX, y: rect.maxY), locations: [0, 0.55, 1])
        context.setStrokeColor(UIColor(red: 0.04, green: 0.012, blue: 0.006, alpha: 0.5).cgColor)
        context.setLineWidth(0.8)
        for y in stride(from: rect.minY + 3, to: rect.maxY, by: 3.2) {
            context.move(to: CGPoint(x: rect.minX + 5, y: y))
            context.addCurve(to: CGPoint(x: rect.maxX - 4, y: y + 1), control1: CGPoint(x: rect.midX - 14, y: y - 2), control2: CGPoint(x: rect.midX + 12, y: y + 2))
            context.strokePath()
        }
        context.restoreGState()
    }

    private static func drawLinearGradient(_ context: CGContext, colors: [UIColor], from: CGPoint, to: CGPoint, locations: [CGFloat]) {
        guard let gradient = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: colors.map(\.cgColor) as CFArray, locations: locations) else { return }
        context.drawLinearGradient(gradient, start: from, end: to, options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
    }

    private static func drawGrain(in context: CGContext, size: CGSize) {
        var seed: UInt64 = 0x8A5CD789635D2DFF
        func random() -> CGFloat {
            seed = seed &* 6364136223846793005 &+ 1442695040888963407
            return CGFloat((seed >> 40) & 0xFFFF) / CGFloat(0xFFFF)
        }
        for _ in 0..<2600 {
            let alpha = 0.012 + random() * 0.025
            context.setFillColor(UIColor(white: random() > 0.48 ? 1 : 0, alpha: alpha).cgColor)
            context.fill(CGRect(x: random() * size.width, y: random() * size.height, width: 0.7, height: 0.7))
        }
    }
}

private struct PullLeverRefreshDynamicArtwork: View {
    @ObservedObject var model: PullLeverRefreshModel
    let layout: PullLeverRefreshLayout
    let reduceMotion: Bool

    var body: some View {
        Canvas(rendersAsynchronously: true) { context, _ in
            context.translateBy(x: layout.pivot.x, y: layout.pivot.y)
            context.scaleBy(x: layout.scale, y: layout.scale)
            drawGauge(context: &context)
            drawLever(context: &context)
            drawCoffee(context: &context)
            drawCup(context: &context)
            drawTimer(context: &context)
        }
        .allowsHitTesting(false)
    }

    private func drawGauge(context: inout GraphicsContext) {
        let center = CGPoint(x: -105, y: -44)
        let zoneStart = Angle.degrees(135 + 8 / 12 * 270)
        let zoneEnd = Angle.degrees(135 + 10 / 12 * 270)
        var zone = Path()
        zone.addArc(center: center, radius: 31.8, startAngle: zoneStart, endAngle: zoneEnd, clockwise: false)
        context.stroke(zone, with: .color(PullLeverRefreshTokens.zone.opacity(model.bar >= 8 ? 0.95 : 0.42)), lineWidth: model.bar >= 8 ? 2.2 : 1.3)

        let angle = (135 + model.bar / 12 * 270) * .pi / 180
        let tip = CGPoint(x: center.x + cos(angle) * 27, y: center.y + sin(angle) * 27)
        var needle = Path()
        needle.move(to: CGPoint(x: center.x - cos(angle) * 3, y: center.y - sin(angle) * 3))
        needle.addLine(to: tip)
        context.stroke(needle, with: .color(PullLeverRefreshTokens.needle), style: StrokeStyle(lineWidth: 1.55, lineCap: .round))
        context.fill(Path(ellipseIn: CGRect(x: center.x - 2.2, y: center.y - 2.2, width: 4.4, height: 4.4)), with: .color(Color(white: 0.72)))
    }

    private func drawLever(context: inout GraphicsContext) {
        var lever = context
        lever.rotate(by: .degrees(model.angle))
        lever.addFilter(.shadow(color: .black.opacity(0.65), radius: 4, x: 1.5, y: 3))
        let arm = Path(roundedRect: CGRect(x: 6, y: -6, width: 96, height: 12), cornerRadius: 6)
        lever.fill(arm, with: .linearGradient(
            Gradient(colors: [Color(white: 0.16), .white, Color(white: 0.28), Color(white: 0.75), Color(white: 0.08)]),
            startPoint: CGPoint(x: 6, y: -6),
            endPoint: CGPoint(x: 102, y: 6)
        ))
        let handle = Path(roundedRect: CGRect(x: 94, y: -10.6, width: 68, height: 21.2), cornerRadius: 10.6)
        lever.fill(handle, with: .linearGradient(
            Gradient(colors: [Color(red: 0.10, green: 0.035, blue: 0.015), Color(red: 0.38, green: 0.17, blue: 0.09), Color(red: 0.11, green: 0.03, blue: 0.012)]),
            startPoint: CGPoint(x: 94, y: -10),
            endPoint: CGPoint(x: 162, y: 10)
        ))
        for offset in stride(from: -6 as CGFloat, through: 6, by: 3) {
            var grain = Path()
            grain.move(to: CGPoint(x: 102, y: offset))
            grain.addCurve(to: CGPoint(x: 154, y: offset + 1), control1: CGPoint(x: 120, y: offset - 2), control2: CGPoint(x: 140, y: offset + 2))
            lever.stroke(grain, with: .color(Color(red: 0.06, green: 0.015, blue: 0.006).opacity(0.55)), lineWidth: 0.7)
        }
        lever.fill(Path(ellipseIn: CGRect(x: -15, y: -15, width: 30, height: 30)), with: .radialGradient(
            Gradient(colors: [.white, Color(white: 0.42), Color(white: 0.08)]), center: CGPoint(x: -4, y: -5), startRadius: 1, endRadius: 20
        ))
        lever.fill(Path(ellipseIn: CGRect(x: -8.5, y: -8.5, width: 17, height: 17)), with: .radialGradient(
            Gradient(colors: [Color(white: 0.7), Color(white: 0.12), .black]), center: CGPoint(x: -3, y: -3), startRadius: 1, endRadius: 10
        ))
    }

    private func drawCoffee(context: inout GraphicsContext) {
        guard model.brew > 0, model.brew < 0.97 else { return }
        let flowIn = PullLeverRefreshTokens.smooth(0.05, 0.17, model.brew)
        let flowOut = 1 - PullLeverRefreshTokens.smooth(0.76, 0.95, model.brew)
        let width = 0.7 + 2.2 * flowIn * flowOut
        for x in [-8.5 as CGFloat, 8.5] {
            var stream = Path()
            stream.move(to: CGPoint(x: x, y: 169))
            stream.addCurve(to: CGPoint(x: x * 0.35, y: 235), control1: CGPoint(x: x * 0.85, y: 188), control2: CGPoint(x: x * 0.55, y: 217))
            context.stroke(stream, with: .linearGradient(
                Gradient(colors: [Color(red: 0.09, green: 0.025, blue: 0.008), Color(red: 0.45, green: 0.17, blue: 0.05)]),
                startPoint: CGPoint(x: x, y: 169),
                endPoint: CGPoint(x: 0, y: 235)
            ), style: StrokeStyle(lineWidth: width, lineCap: .round))
        }
    }

    private func drawCup(context: inout GraphicsContext) {
        var cup = context
        cup.translateBy(x: model.cupOffset, y: 0)
        cup.opacity = model.cupOpacity
        cup.addFilter(.shadow(color: .black.opacity(0.55), radius: 5, x: 0, y: 5))

        let handle = Path(ellipseIn: CGRect(x: 30, y: 237, width: 28, height: 24))
        cup.stroke(handle, with: .linearGradient(
            Gradient(colors: [Color(white: 0.96), Color(white: 0.55)]),
            startPoint: CGPoint(x: 30, y: 237),
            endPoint: CGPoint(x: 58, y: 261)
        ), lineWidth: 7)

        var bowl = Path()
        bowl.move(to: CGPoint(x: -37, y: 232))
        bowl.addCurve(to: CGPoint(x: -26, y: 279), control1: CGPoint(x: -36, y: 252), control2: CGPoint(x: -35, y: 271))
        bowl.addCurve(to: CGPoint(x: 26, y: 279), control1: CGPoint(x: -13, y: 286), control2: CGPoint(x: 13, y: 286))
        bowl.addCurve(to: CGPoint(x: 37, y: 232), control1: CGPoint(x: 35, y: 270), control2: CGPoint(x: 36, y: 252))
        bowl.closeSubpath()
        cup.fill(bowl, with: .linearGradient(
            Gradient(colors: [Color(white: 0.58), Color(white: 0.98), Color(white: 0.72)]),
            startPoint: CGPoint(x: -37, y: 250),
            endPoint: CGPoint(x: 37, y: 250)
        ))
        cup.fill(Path(ellipseIn: CGRect(x: -37, y: 227, width: 74, height: 14)), with: .linearGradient(
            Gradient(colors: [Color(white: 0.48), Color(white: 0.96)]),
            startPoint: CGPoint(x: -37, y: 227),
            endPoint: CGPoint(x: 37, y: 241)
        ))
        cup.fill(Path(ellipseIn: CGRect(x: -33, y: 229, width: 66, height: 9)), with: .color(Color(red: 0.035, green: 0.018, blue: 0.012)))

        if model.brew > 0.08 {
            let fill = PullLeverRefreshTokens.smooth(0.08, 0.88, model.brew)
            let inset = 2.5 + fill * 1.5
            cup.fill(Path(ellipseIn: CGRect(x: -33 + inset, y: 230, width: 66 - inset * 2, height: 7.5)), with: .radialGradient(
                Gradient(colors: [Color(red: 0.66, green: 0.38, blue: 0.17), Color(red: 0.42, green: 0.16, blue: 0.05), Color(red: 0.08, green: 0.025, blue: 0.008)]),
                center: CGPoint(x: 0, y: 232), startRadius: 0, endRadius: 33
            ))
        }

        if !reduceMotion, model.brew > 0.88 {
            let phase = CGFloat(model.clock.truncatingRemainder(dividingBy: 1.8) / 1.8)
            for index in 0..<3 {
                let p = (phase + CGFloat(index) / 3).truncatingRemainder(dividingBy: 1)
                var steam = Path()
                steam.move(to: CGPoint(x: CGFloat(index - 1) * 8, y: 227 - p * 18))
                steam.addCurve(to: CGPoint(x: CGFloat(index - 1) * 7 + 3, y: 213 - p * 18), control1: CGPoint(x: -3, y: 222 - p * 18), control2: CGPoint(x: 5, y: 218 - p * 18))
                cup.stroke(steam, with: .color(.white.opacity(Double((1 - p) * 0.18))), lineWidth: 1.1)
            }
        }
    }

    private func drawTimer(context: inout GraphicsContext) {
        let seconds = min(27, Int((model.brew * 27).rounded()))
        let text = Text(String(format: "%02d", seconds))
            .font(.system(size: 22, weight: .semibold, design: .rounded).monospacedDigit())
            .foregroundColor(PullLeverRefreshTokens.digit)
        context.draw(text, at: CGPoint(x: -105, y: 252), anchor: .center)
        let lampColor = model.stage == .brewing || model.stage == .done
            ? Color(red: 1, green: 0.52, blue: 0.20)
            : Color(red: 0.28, green: 0.16, blue: 0.09)
        context.addFilter(.shadow(color: lampColor.opacity(0.65), radius: model.stage == .brewing ? 4 : 1))
        context.fill(Path(ellipseIn: CGRect(x: -82, y: 217, width: 6, height: 6)), with: .color(lampColor))
    }
}
