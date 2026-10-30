import SwiftUI

struct MorphObjectOnboardingArt {
    typealias M = MorphObjectOnboardingMotion
    typealias T = MorphObjectOnboardingTheme
    let resources: MorphObjectOnboardingResources
    let size: CGSize
    private let tau = 2 * Double.pi
    private var scale: Double { size.width / T.width }
    private var dy: Double { size.height - T.height * scale }

    func draw(_ context: GraphicsContext, _ live: M.Live) {
        context.fill(Path(CGRect(origin: .zero, size: size)), with: .color(T.color(T.C.background)))
        var canvas = context
        canvas.translateBy(x: 0, y: dy)
        canvas.scaleBy(x: scale, y: scale)
        drawGlow(canvas, live)
        drawSparkles(canvas, live)
        drawObject(canvas, live)
        drawTop(canvas, live)
        drawCard(canvas, live)
    }
    private func rounded(_ rect: CGRect, _ radius: Double) -> Path {
        Path(roundedRect: rect, cornerRadius: radius)
    }
    private func label(_ context: GraphicsContext, _ label: MorphObjectOnboardingResources.Label,
                       _ x: Double, _ cy: Double, _ anchor: Double, _ color: Color, blur: Double = 0) {
        var canvas = context
        canvas.translateBy(x: x - label.width * anchor, y: cy + label.size * 0.36)
        if blur > 0.05 { canvas.addFilter(.blur(radius: blur)) }
        canvas.fill(label.path, with: .color(color))
    }
    private func image(_ context: GraphicsContext, _ image: CGImage, opacity: Double = 1) {
        var canvas = context
        canvas.opacity *= opacity
        let object = Image(decorative: image, scale: 1).interpolation(.high)
        canvas.draw(object, in: CGRect(x: 0, y: 0, width: T.Morph.box.width, height: T.Morph.box.height))
    }
    private func drawObject(_ context: GraphicsContext, _ live: M.Live) {
        let page = max(0, min(2, live.page))
        let over = live.page - page
        let box = T.Morph.box
        let bob = 4 * sin(tau * 2 * (live.ambient / M.LOOP))
        let tilt = 2 * sin(tau * (live.ambient / M.LOOP) + 0.6)
        var hop = 0.0
        var squash = 0.0
        var twist = 0.0
        if live.hop >= 0 {
            let progress = live.hop / 0.5
            if progress < 1 {
                hop = 16 * sin(.pi * progress)
                twist = 10 * sin(tau * progress)
            } else {
                squash = 0.08 * exp(-(live.hop - 0.5) / 0.14) * cos(tau * (live.hop - 0.5) / 0.22)
            }
        }
        if live.arrive >= 0 { squash += 0.05 * exp(-live.arrive / 0.18) * cos(tau * live.arrive / 0.3) }
        let cx = box.midX
        let cy = box.midY
        var shadow = context
        shadow.addFilter(.blur(radius: 9))
        let shadowWidth = 150 * (1 - hop / 60) * (1 - bob / 80)
        shadow.fill(Path(ellipseIn: CGRect(x: cx - shadowWidth / 2, y: box.minY + box.height * 0.86, width: shadowWidth, height: 16)),
                    with: .color(T.color(0, 0.35 * (1 - hop / 30))))
        var object = context
        object.translateBy(x: cx - over * 26, y: cy + bob - hop)
        object.rotate(by: .degrees(tilt + twist - over * 14))
        object.scaleBy(x: 1 + squash + abs(over) * 0.06, y: 1 - squash - abs(over) * 0.04)
        object.translateBy(x: -box.width / 2, y: -box.height / 2)
        let frame = page * T.Morph.perPage
        let first = Int(floor(frame))
        let second = min(T.Morph.frames - 1, first + 1)
        let blend = frame - Double(first)
        let nearest = Int(M.roundPage(page))
        let sharp = 1 - M.smooth(abs(page - Double(nearest)) / 0.07)
        if sharp < 0.999 {
            image(object, resources.frames[first])
            if blend > 0.002 { image(object, resources.frames[second], opacity: blend) }
        }
        if sharp > 0.001 { image(object, resources.keys[nearest], opacity: sharp) }
        let mid = sin(Double.pi * (page - floor(page)))
        if mid > 0.02 {
            var light = context; light.blendMode = .plusLighter
            let center = CGPoint(x: cx, y: cy + bob - hop)
            light.fill(Path(ellipseIn: CGRect(x: cx - 120, y: center.y - 120, width: 240, height: 240)), with: .radialGradient(Gradient(colors: [.white.opacity(0.22 * mid), .white.opacity(0)]), center: center, startRadius: 0, endRadius: 120))
            for k in 0..<14 {
                let n = Double(k)
                let angle = tau * n / 14 + page * 3.2 + n * 0.4
                let radius = 70 + 30 * sin(n * 1.7) + 26 * mid
                let x = cx + cos(angle) * radius
                let y = cy + bob - hop + sin(angle) * radius * 0.62
                let r = 1.4 + 1.2 * (n * 0.37).truncatingRemainder(dividingBy: 1)
                light.fill(Path(ellipseIn: CGRect(x: x - r, y: y - r, width: 2 * r, height: 2 * r)), with: .color(Color(.sRGB, red: 1, green: 0.98, blue: 0.9, opacity: 0.75 * mid * (0.5 + 0.5 * sin(n * 2.3)))))
            }
        }
    }
    private func drawGlow(_ context: GraphicsContext, _ live: M.Live) {
        let page = max(0, min(2, live.page))
        let index = min(1, Int(floor(page)))
        let progress = page - Double(index)
        let breathe = 0.16 + 0.04 * sin(tau * 2 * (live.ambient / M.LOOP))
        let center = CGPoint(x: T.Morph.box.midX, y: T.Morph.box.midY)
        let colors = [T.mixedColor(T.glows[index], T.glows[index + 1], progress, breathe),
                      T.mixedColor(T.glows[index], T.glows[index + 1], progress, 0)]
        context.fill(Path(ellipseIn: CGRect(x: center.x - 190, y: center.y - 190, width: 380, height: 380)),
                     with: .radialGradient(Gradient(colors: colors), center: center, startRadius: 0, endRadius: 190))
    }
    private func drawTop(_ context: GraphicsContext, _ live: M.Live) {
        let dots = T.UI.dots
        let page = max(0, min(2, live.page))
        var x = dots.x
        for index in 0..<3 {
            let weight = M.smooth(1 - min(1, abs(page - Double(index))))
            let width = M.mix(dots.dot, dots.pill, weight)
            let height = M.mix(dots.dot, dots.height, weight)
            context.fill(rounded(CGRect(x: x, y: dots.y - height / 2, width: width, height: height), height / 2),
                         with: .color(T.mixedColor(T.C.dot, T.C.pill, weight)))
            x += width + dots.gap
        }
        label(context, resources.skip, T.UI.skip.x, T.UI.skip.y, 1, T.color(T.C.skip, 1 - M.smooth((page - 1.4) / 0.5)))
    }
    private static let starPath: Path = {
        var path = Path()
        path.move(to: CGPoint(x: 0, y: -10))
        path.addCurve(to: CGPoint(x: 10, y: 0), control1: CGPoint(x: 1.1, y: -3.4), control2: CGPoint(x: 3.4, y: -1.1))
        path.addCurve(to: CGPoint(x: 0, y: 10), control1: CGPoint(x: 3.4, y: 1.1), control2: CGPoint(x: 1.1, y: 3.4))
        path.addCurve(to: CGPoint(x: -10, y: 0), control1: CGPoint(x: -1.1, y: 3.4), control2: CGPoint(x: -3.4, y: 1.1))
        path.addCurve(to: CGPoint(x: 0, y: -10), control1: CGPoint(x: -3.4, y: -1.1), control2: CGPoint(x: -1.1, y: -3.4))
        path.closeSubpath()
        return path
    }()
    private func star(_ context: GraphicsContext, x: Double, y: Double, half: Double, rotation: Double, color: Color) {
        guard half >= 0.3 else { return }
        var canvas = context
        canvas.translateBy(x: x, y: y)
        canvas.rotate(by: .degrees(rotation))
        canvas.scaleBy(x: half / 10, y: half / 10)
        canvas.fill(Self.starPath, with: .color(color))
    }
    private func drawSparkles(_ context: GraphicsContext, _ live: M.Live) {
        let page = max(0, min(2, live.page))
        let weight = 1 - M.smooth(page / 0.45)
        let twinkle = 0.5 + 0.5 * sin(tau * 3 * (live.ambient / M.LOOP))
        star(context, x: T.UI.sparkle.x, y: T.UI.sparkle.y,
             half: T.UI.sparkle.size * (0.82 + 0.18 * twinkle) * weight,
             rotation: 8 * sin(tau * live.ambient / M.LOOP), color: T.color(T.C.sparkle, weight))
        if live.arrive >= 0 && live.arrive < 0.75 {
            let progress = live.arrive / 0.75
            let index = Int(M.roundPage(page))
            for particle in 0..<7 {
                let angle = tau * Double(particle) / 7 + Double(index)
                let distance = (95 + 25 * Double((particle * 37) % 7) / 7) * (1 - pow(1 - progress, 3))
                let life = sin(.pi * progress)
                star(context, x: T.Morph.box.midX + cos(angle) * distance,
                     y: T.Morph.box.midY + sin(angle) * distance * 0.85,
                     half: Double(4 + (particle % 3) * 2) * life, rotation: 90 * progress,
                     color: T.mixedColor(T.glows[index], 0xFFFFFF, 0.5, life))
            }
        }
    }
    private func drawCard(_ context: GraphicsContext, _ live: M.Live) {
        let page = max(0, min(2, live.page))
        let bump = live.arrive >= 0 ? 0.008 * exp(-live.arrive / 0.2) * cos(tau * live.arrive / 0.35) : 0
        var card = context
        card.translateBy(x: T.width / 2, y: T.UI.cardY)
        card.scaleBy(x: 1 + bump, y: 1 + bump)
        card.translateBy(x: -T.width / 2, y: -T.UI.cardY)
        card.fill(rounded(CGRect(x: 0, y: T.UI.cardY, width: T.width, height: 874 - T.UI.cardY + 60 / scale), T.UI.cardRadius),
                  with: .color(T.color(T.C.card)))
        for index in 0..<3 {
            let distance = Double(index) - page
            let alpha = 1 - min(1, abs(distance) * 1.7)
            if alpha <= 0.003 { continue }
            for line in 0..<2 {
                label(context, resources.titles[index][line], T.width / 2 + distance * Double(70 + line * 14),
                      T.UI.title[line], 0.5, T.color(T.C.ink, alpha), blur: abs(distance) * 7)
                label(context, resources.subtitles[index][line], T.width / 2 + distance * Double(96 + line * 10),
                      T.UI.subtitle[line], 0.5, T.color(T.C.subtitle, alpha), blur: abs(distance) * 7)
            }
        }
        let button = T.UI.button
        let buttonScale = 1 - 0.04 * live.press
        var canvas = context
        canvas.translateBy(x: button.midX, y: button.midY)
        canvas.scaleBy(x: buttonScale, y: buttonScale)
        canvas.fill(rounded(CGRect(x: -button.width / 2, y: -button.height / 2, width: button.width, height: button.height), button.height / 2),
                    with: .color(T.color(T.C.button)))
        let blend = M.smooth((page - 1.35) / 0.55)
        if blend < 0.999 { label(canvas, resources.next, 0, -8 * blend, 0.5, T.color(T.C.buttonInk, 1 - blend)) }
        if blend > 0.001 { label(canvas, resources.start, 0, 8 * (1 - blend), 0.5, T.color(T.C.buttonInk, blend)) }
    }
}
