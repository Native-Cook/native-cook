import CoreText
import SwiftUI
import UIKit

enum RaffleDrumSpinDraw {
    static let cardCopy = [
        ["A single chance to", "enter the Raffle and", "compete for prizes"],
        ["Double your chances", "to win a prize with", "two Raffle Entries"],
        ["Triple your odds", "with three Raffle", "Entries in the drum"]
    ]

    static func base(context: inout GraphicsContext, size: CGSize, frame: RaffleDrumSpinMotion.Frame) {
        let scale = RaffleDrumSpinScale(size: size)
        drawShadow(context: &context, scale: scale)
        drawTitle(context: &context, scale: scale, frame: frame)
        drawChips(context: &context, scale: scale, frame: frame)
        drawClose(context: &context, scale: scale)
    }

    static func page(context: inout GraphicsContext, size: CGSize, frame: RaffleDrumSpinMotion.Frame, fallbackDrum: Bool = false) {
#if DEBUG
        RaffleDrumSpinRenderTrace.shared.record(3,frame.t)
#endif
        let scale = RaffleDrumSpinScale(size: size)
        drawAmbientConfetti(context: &context, scale: scale, frame: frame)
        if fallbackDrum { drawDrum(context: &context, scale: scale, frame: frame) }
        drawSparkles(context: &context, scale: scale, frame: frame)
    }

    static func pageGlow(context: inout GraphicsContext, size: CGSize, frame: RaffleDrumSpinMotion.Frame) {
        drawGlow(context:&context,scale:RaffleDrumSpinScale(size:size),frame:frame)
    }

    static func cards(context: inout GraphicsContext, size: CGSize, frame: RaffleDrumSpinMotion.Frame) {
#if DEBUG
        RaffleDrumSpinRenderTrace.shared.record(2,frame.t)
#endif
        drawCards(context: &context, scale: RaffleDrumSpinScale(size: size), frame: frame)
    }

    static func pageOverlay(context: inout GraphicsContext, size: CGSize, frame: RaffleDrumSpinMotion.Frame) {
        let scale = RaffleDrumSpinScale(size: size)
        drawDim(context: &context, scale: scale, frame: frame)
        if frame.focus < 0.001 { drawButton(context: &context, scale: scale, frame: frame) }
    }

    static func top(context: inout GraphicsContext, size: CGSize, frame: RaffleDrumSpinMotion.Frame, ticket: Image?) {
#if DEBUG
        RaffleDrumSpinRenderTrace.shared.record(0,frame.t)
#endif
        let scale = RaffleDrumSpinScale(size: size)
        if frame.focus > 0.001 { drawButton(context:&context,scale:scale,frame:frame) }
        drawRevealLight(context: &context, scale: scale, frame: frame)
        if frame.morph < 1, let hero = frame.hero {
            drawHeroMorph(context: &context, scale: scale, frame: frame, hero: hero, ticket: ticket)
        }
    }

    static func ticket(context: inout GraphicsContext, size: CGSize, frame: RaffleDrumSpinMotion.Frame, ticket: Image?) {
#if DEBUG
        RaffleDrumSpinRenderTrace.shared.record(1,frame.t)
#endif
        guard frame.morph >= 1,
              frame.burn < 1 || frame.tear > 0 || frame.t < RaffleDrumSpinMotion.T.burn.upperBound + RaffleDrumSpinMotion.T.dust
        else { return }
        drawTicket(context: &context, scale: RaffleDrumSpinScale(size: size), frame: frame, ticket: ticket)
    }

    private static func drawAmbientConfetti(context: inout GraphicsContext, scale: RaffleDrumSpinScale, frame: RaffleDrumSpinMotion.Frame) {
        let slips: [(Double, Double, Double, Double, Double)] = [
            (112, 137, -62, 24, 0), (219, 156, -32, 18, 0), (92, 240, 12, 26, 0),
            (318, 222, 28, 26, 0), (20, 365, -58, 46, 6), (370, 330, 35, 34, 5),
            (87, 459, 22, 30, 1), (298, 323, -64, 14, 0), (175, 283, -58, 10, 0),
            (345, 448, 70, 16, 0), (394, 640, 10, 38, 7), (362, 236, 22, 20, 0)
        ]
        for (index, slip) in slips.enumerated() {
            let phase = frame.t / RaffleDrumSpinMotion.loop * .pi * 2
            let ox = slip.0 - 205
            let oy = slip.1 - 178
            let distance = max(40, hypot(ox, oy))
            let gust = frame.speed * (60 / distance) * 9
            let x = slip.0 + 5 * sin(phase * 2 + Double(index)) + ox / distance * gust + 3 * frame.speed * sin(frame.t * 11 + Double(index) * 1.7)
            let y = slip.1 + 4 * sin(phase * 3 + Double(index) * 2.3) + oy / distance * gust
            let turn = 8 * sin(phase * 2 + Double(index) * 0.7) + 14 * frame.speed * sin(frame.t * 7 + Double(index))
            let flip = 0.55 + 0.45 * cos(phase * Double(1 + index % 3) + Double(index) + frame.speed * sin(frame.t * 13 + Double(index)))
            var copy = context
            copy.translateBy(x: scale.x(x), y: scale.y(y))
            copy.rotate(by: .degrees(slip.2 + turn))
            copy.scaleBy(x: 1, y: flip)
            if slip.4 > 0 { copy.addFilter(.blur(radius: scale.x(slip.4))) }
            let rect = CGRect(x: scale.x(-slip.3 / 2), y: scale.x(-slip.3 * 0.21), width: scale.x(slip.3), height: scale.x(slip.3 * 0.42))
            copy.fill(Path(roundedRect: rect, cornerRadius: scale.x(2)), with: .linearGradient(
                Gradient(colors: [rgb(0xa58aff, alpha: 0.95), rgb(0x7b4cf0, alpha: 0.9)]),
                startPoint: CGPoint(x: rect.midX, y: rect.minY),
                endPoint: CGPoint(x: rect.midX, y: rect.maxY)
            ))
        }
    }

    private static func drawShadow(context: inout GraphicsContext, scale: RaffleDrumSpinScale) {
        var broad = context
        broad.addFilter(.blur(radius: scale.x(14)))
        broad.fill(Path(ellipseIn: CGRect(x: scale.x(116), y: scale.y(288), width: scale.x(184), height: scale.y(22))), with: .color(rgb(0x05010f, alpha: 0.55)))
        var core = context
        core.addFilter(.blur(radius: scale.x(4)))
        core.fill(Path(ellipseIn: CGRect(x: scale.x(136), y: scale.y(295), width: scale.x(144), height: scale.y(8))), with: .color(rgb(0x020008, alpha: 0.6)))
    }

    private static func drawGlow(context: inout GraphicsContext, scale: RaffleDrumSpinScale, frame: RaffleDrumSpinMotion.Frame) {
        let amount = frame.glow * 0.5
        guard amount > 0.004 else { return }
        let center = scale.point(205, 148)
        let radius = scale.size.width * 0.62
        var glow = context
        glow.blendMode = .plusLighter
        glow.fill(Path(ellipseIn: CGRect(x: center.x - radius, y: center.y - radius, width: radius * 2, height: radius * 2)), with: .radialGradient(
            Gradient(stops: [
                .init(color: rgb(0x331f6b, alpha: amount), location: 0),
                .init(color: rgb(0x331f6b, alpha: amount * 0.55), location: 0.35),
                .init(color: rgb(0x331f6b, alpha: amount * 0.16), location: 0.7),
                .init(color: .clear, location: 1)
            ]), center: center, startRadius: 0, endRadius: radius
        ))
    }

    private static func drawDim(context: inout GraphicsContext, scale: RaffleDrumSpinScale, frame: RaffleDrumSpinMotion.Frame) {
        guard frame.dim > 0.004 else { return }
        let center = scale.point(201, 430)
        context.fill(Path(CGRect(origin: .zero, size: scale.size)), with: .radialGradient(
            Gradient(colors: [rgb(0x0a0418, alpha: 0.2 * frame.dim), rgb(0x04010b, alpha: 0.85 * frame.dim)]),
            center: center, startRadius: 0, endRadius: scale.size.height * 0.75
        ))
    }

    /// The same projected fallback used when the reference disables WebGPU for reduced motion.
    private static func drawDrum(context: inout GraphicsContext, scale: RaffleDrumSpinScale, frame: RaffleDrumSpinMotion.Frame) {
        typealias M = RaffleDrumSpinMotion
        let sx = scale.sx
        func project(_ x: Double, _ y: Double, _ z: Double) -> M.Projection { M.project(x,y,z,sx) }
        func stroke(_ points: [M.Projection], _ width: Double, _ ink: Int, _ alpha: Double) {
            guard let first=points.first else { return }
            var path=Path();path.move(to:CGPoint(x:first.x,y:first.y))
            for p in points.dropFirst() { path.addLine(to:CGPoint(x:p.x,y:p.y)) }
            context.stroke(path,with:.color(rgb(ink,alpha:alpha)),style:StrokeStyle(lineWidth:width,lineCap:.round))
        }
        func rot(_ x: Double, _ y: Double, _ z: Double) -> (Double,Double,Double) {
            let sn=sin(frame.theta),cs=cos(frame.theta)
            return (x,cs*y-sn*z,sn*y+cs*z)
        }
        for side in [-1.0,1.0] {
            var points: [M.Projection]=[]
            for i in 0...16 {
                let t=Double(i)/16,u=1-t
                let x=side*(1.18*u*u*u+1.36*3*u*u*t+1.34*3*u*t*t+1.02*t*t*t)
                let y = -0.42*3*u*u*t-1.02*3*u*t*t-1.3*t*t*t
                points.append(project(x,y,0))
            }
            stroke(points,7*sx,0x8d8a99,1);stroke(points,2.5*sx,0xe9e7f0,0.9)
        }
        let corners=[project(-1.15,-1.3,0.72),project(1.15,-1.3,0.72),project(1.15,-1.3,-0.72),project(-1.15,-1.3,-0.72)]
        var top=Path();top.move(to:CGPoint(x:corners[0].x,y:corners[0].y))
        for p in corners.dropFirst() { top.addLine(to:CGPoint(x:p.x,y:p.y)) };top.closeSubpath()
        context.fill(top,with:.color(rgb(0x3b3846)))
        func halves(_ front: Bool) {
            for k in 0..<7 {
                let a=Double(k)/7 * .pi
                var run: [M.Projection]=[]
                func flush() {
                    if run.count>1 { stroke(run,3.2*sx,front ? 0x9c99aa:0x4a4660,1);stroke(run,1.1*sx,front ? 0xffffff:0x8d88a6,0.85) }
                    run.removeAll(keepingCapacity:true)
                }
                for i in 0...72 {
                    let u=Double(i)/72 * .pi * 2
                    let xyz=rot(cos(u),sin(u)*cos(a),sin(u)*sin(a))
                    let p=project(xyz.0,xyz.1,xyz.2)
                    if (p.z<6)==front { run.append(p) } else { flush() }
                }
                flush()
            }
        }
        halves(false)
        stroke([project(-1.24,0,0),project(1.36,0,0)],4*sx,0xb9b6c4,1)
        for ball in frame.balls where ball.a>0 && ball.z>3.5 { drawBaseball(context:&context,scale:scale,ball:ball) }
        halves(true)
        let h=rot(1.36,0.3,0),h0=project(h.0,h.1,h.2),h1=project(1.66,h.1,h.2)
        stroke([project(1.36,0,0),h0],3.5*sx,0xc9c6d4,1);stroke([h0,h1],8*sx,0x151418,1)
        for ball in frame.balls where ball.a>0 && ball.z<=3.5 { drawBaseball(context:&context,scale:scale,ball:ball) }
    }

    private static func drawTitle(context: inout GraphicsContext, scale: RaffleDrumSpinScale, frame: RaffleDrumSpinMotion.Frame) {
        let alpha = 1 - frame.dim
        let titleSize = fittedSize(name: RaffleDrumSpinAssets.archivo, text: "Raffle Rumble", width: scale.x(272))
        var glow = context
        glow.addFilter(.blur(radius: scale.x(10)))
        drawBaselineText(context: &glow, "Raffle Rumble", name: RaffleDrumSpinAssets.archivo, size: titleSize, color: rgb(0xb9a2ff, alpha: 0.28 * alpha), centerX: scale.x(201), baseline: scale.y(386))
        context.drawLayer { layer in
            let baseline = scale.y(386)
            drawBaselineText(context: &layer, "Raffle Rumble", name: RaffleDrumSpinAssets.archivo, size: titleSize, color: .white, centerX: scale.x(201), baseline: baseline)
            layer.blendMode = .sourceIn
            layer.fill(
                Path(CGRect(x: scale.x(50), y: baseline - titleSize, width: scale.x(302), height: titleSize)),
                with: .linearGradient(
                    Gradient(stops: [
                        .init(color: rgb(0xffffff, alpha: alpha), location: 0),
                        .init(color: rgb(0xe7e3ef, alpha: alpha), location: 0.55),
                        .init(color: rgb(0xb9b3c6, alpha: alpha), location: 1)
                    ]),
                    startPoint: CGPoint(x: 0, y: baseline - titleSize * 0.75),
                    endPoint: CGPoint(x: 0, y: baseline)
                )
            )
        }
        let subSize = scale.x(15.5)
        drawBaselineText(context: &context, "Buy tickets to enter the raffle", name: RaffleDrumSpinAssets.interRegular, size: subSize, color: rgb(0x9b93ad, alpha: alpha), centerX: scale.x(201), baseline: scale.y(431))
        drawBaselineText(context: &context, "for awesome prizes.", name: RaffleDrumSpinAssets.interRegular, size: subSize, color: rgb(0x9b93ad, alpha: alpha), centerX: scale.x(201), baseline: scale.y(453))
    }

    private static func drawSparkles(context: inout GraphicsContext, scale: RaffleDrumSpinScale, frame: RaffleDrumSpinMotion.Frame) {
        let alpha = 1 - frame.dim
        let phase = frame.t / RaffleDrumSpinMotion.loop * .pi * 4
        let left = 0.7 + 0.3 * sin(phase)
        let right = 0.7 + 0.3 * sin(phase + 2)
        drawSparkle(context: &context, point: scale.point(50, 346), radius: scale.x(11 * left), alpha: 0.85 * alpha)
        drawSparkle(context: &context, point: scale.point(345, 388), radius: scale.x(8 * right), alpha: 0.7 * alpha)
    }

    private static func drawChips(context: inout GraphicsContext, scale: RaffleDrumSpinScale, frame: RaffleDrumSpinMotion.Frame) {
        let alpha = 1 - frame.dim
        let chipSize = scale.x(11)
        let favSize = scale.x(11)
        let pill0 = textWidth("15+", name: RaffleDrumSpinAssets.interMedium, size: chipSize) + scale.x(16)
        let pill1 = textWidth("$1+", name: RaffleDrumSpinAssets.interMedium, size: chipSize) + scale.x(16)
        let pill2 = textWidth("unlimited", name: RaffleDrumSpinAssets.interMedium, size: chipSize) + scale.x(16)
        let fav = textWidth("Favorite Games", name: RaffleDrumSpinAssets.interSemiBold, size: favSize)
        let gap = scale.x(6)
        let star = scale.x(30)
        var x = scale.x(201) - (pill0 + gap + pill1 + star + fav + star + pill2) / 2
        func drawPill(_ string: String, width: Double, x: Double) {
            let rect = CGRect(x: x + scale.x(0.5), y: scale.y(492) - scale.x(10), width: width - scale.x(1), height: scale.x(20))
            context.stroke(Path(roundedRect: rect, cornerRadius: rect.height / 2), with: .color(rgb(0x6e6880, alpha: 0.9 * alpha)), lineWidth: scale.x(1))
            drawBaselineText(context: &context, string, name: RaffleDrumSpinAssets.interMedium, size: chipSize, color: rgb(0xb4adc4, alpha: alpha), x: x + scale.x(8), baseline: scale.y(492) + scale.x(4))
        }
        drawPill("15+", width: pill0, x: x); x += pill0 + gap
        drawPill("$1+", width: pill1, x: x); x += pill1
        let sparkle1 = x + star / 2; x += star
        drawBaselineText(context: &context, "Favorite Games", name: RaffleDrumSpinAssets.interSemiBold, size: favSize, color: rgb(0xd9d3e6, alpha: alpha), x: x, baseline: scale.y(492) + scale.x(4)); x += fav
        let sparkle2 = x + star / 2; x += star
        drawPill("unlimited", width: pill2, x: x)
        for point in [sparkle1, sparkle2] {
            var line = Path()
            line.move(to: CGPoint(x: point - scale.x(10), y: scale.y(492)))
            line.addLine(to: CGPoint(x: point + scale.x(10), y: scale.y(492)))
            context.stroke(line, with: .color(rgb(0x8a83a0, alpha: 0.7 * alpha)), lineWidth: scale.x(0.9))
            drawSparkle(context: &context, point: CGPoint(x: point, y: scale.y(492)), radius: scale.x(5), alpha: 0.85 * alpha)
        }
    }

    private static func drawCards(context: inout GraphicsContext, scale: RaffleDrumSpinScale, frame: RaffleDrumSpinMotion.Frame) {
        for card in 0..<3 {
            drawCard(context: &context, scale: scale, frame: frame, card: card)
        }
    }

    private static func drawCard(context: inout GraphicsContext, scale: RaffleDrumSpinScale, frame: RaffleDrumSpinMotion.Frame, card: Int) {
        let selected: Double
        if card == frame.to && card == frame.from { selected = 1 }
        else if card == frame.to { selected = frame.selection }
        else if card == frame.from { selected = 1 - frame.selection }
        else { selected = 0 }
        let x = RaffleDrumSpinMotion.Layout.cardX + Double(card) * (RaffleDrumSpinMotion.Layout.cardWidth + RaffleDrumSpinMotion.Layout.cardGap) + frame.strip
        let rect = CGRect(x: scale.x(x), y: scale.y(RaffleDrumSpinMotion.Layout.cardY - 2 * selected), width: scale.x(RaffleDrumSpinMotion.Layout.cardWidth), height: scale.y(RaffleDrumSpinMotion.Layout.cardHeight))
        guard rect.maxX > -4, rect.minX < scale.size.width + 4 else { return }
        let path = ticketPath(rect: rect, radius: scale.x(18), notchWidth: scale.x(58), notchDepth: scale.x(7))
        let alpha = 1 - frame.dim * 0.85
        var cardContext = context
        let pressedScale = 1 - 0.04 * frame.cardPress[card]
        cardContext.translateBy(x: rect.midX, y: rect.midY)
        cardContext.scaleBy(x: pressedScale, y: pressedScale)
        cardContext.translateBy(x: -rect.midX, y: -rect.midY)
        if card == frame.to && frame.absorb > 0.01 {
            strokeSolid(context: &cardContext, path: path, ink: 0xffd27a, alpha: 0.85 * frame.absorb, width: scale.x(2), radius: scale.x(6))
        }
        if selected > 0.01 {
            var glow = cardContext
            glow.clip(to: path, options: .inverse)
            glow.addFilter(.blur(radius: scale.x(12)))
            glow.fill(path, with: .color(rgb(0x6d3ff0, alpha: 0.55 * selected * alpha)))
        }
        cardContext.fill(path, with: .linearGradient(
            Gradient(stops: [
                .init(color: rgb(0x2a1d4a, alpha: 0.92 * alpha), location: 0),
                .init(color: rgb(0x150e26, alpha: 0.92 * alpha), location: 0.45),
                .init(color: rgb(0x0b0814, alpha: 0.95 * alpha), location: 1)
            ]), startPoint: CGPoint(x: rect.minX, y: rect.minY), endPoint: CGPoint(x: rect.minX, y: rect.maxY)
        ))
        let rayCenter = CGPoint(x: rect.midX, y: rect.minY + scale.y(60))
        let iconValues = [[(84.0, 28.0)], [(55.0, 24.0), (107.0, 24.0)], [(42.0, 20.0), (83.0, 20.0), (124.0, 20.0)]]
        var clipped = cardContext
        clipped.clip(to: path)
        clipped.fill(Path(CGRect(x: rect.minX, y: rect.minY, width: rect.width, height: rect.height * 0.62)), with: .shader(ShaderLibrary.raffleDrumSpinRays(
            .float2(Float(rayCenter.x), Float(rayCenter.y)), .float(Float((0.25 + 0.75 * selected) * alpha)), .float(Float(frame.t))
        )))
        for value in iconValues[card] {
            let center = CGPoint(x: rect.minX + scale.x(value.0), y: rayCenter.y)
            let radius = scale.x(value.1 * 1.05)
            var bloom = clipped
            bloom.addFilter(.blur(radius: scale.x(12)))
            bloom.fill(Path(ellipseIn: CGRect(x: center.x-radius, y: center.y-radius, width: radius*2, height: radius*2)), with: .color(rgb(0x8b62ff, alpha: 0.45 * alpha)))
        }
        clipped.fill(Path(rect), with: .linearGradient(
            Gradient(colors: [rgb(0xffffff, alpha: 0.07 * alpha), .clear]),
            startPoint: CGPoint(x: rect.minX, y: rect.minY), endPoint: CGPoint(x: rect.minX + rect.width * 0.4, y: rect.minY + rect.height * 0.5)
        ))
        for index in iconValues[card].indices {
            let value = iconValues[card][index]
            let ia = frame.iconA[card][index]
            let isc = RaffleDrumSpinMotion.mix(0.92, 1, ia)
            let spinK = frame.t / RaffleDrumSpinMotion.loop * .pi * 2
            let ball = RaffleDrumSpinMotion.Ball(
                id: index, x: rect.minX + scale.x(value.0), y: rayCenter.y,
                r: scale.x(value.1) * isc, z: 3, rx: 0.4 + Double(index) * 1.3,
                ry: 0.6 + 0.15 * sin(spinK + Double(index)), rz: 0.3 * Double(index), a: ia * alpha, glow: 0, sq: 1
            )
            drawBaseball(context: &cardContext, scale: scale, ball: ball)
        }
        let edgeWidth = scale.x(RaffleDrumSpinMotion.mix(1, 2.2, selected))
        if selected > 0.02 && selected < 0.98 {
            cardContext.stroke(path, with: .color(rgb(0x2a2140, alpha: (1-selected) * alpha)), lineWidth: scale.x(1))
            cardContext.stroke(path, with: .color(rgb(0x6d3ff0, alpha: selected * alpha)), lineWidth: edgeWidth)
        } else {
            cardContext.stroke(path, with: .color(rgb(selected > 0.5 ? 0x6d3ff0 : 0x2a2140, alpha: RaffleDrumSpinMotion.mix(0.9, 1, selected) * alpha)), lineWidth: edgeWidth)
        }
        if card == frame.to && frame.sweep > 0 && frame.sweep < 1 {
            let glint = RaffleDrumSpinContour.highlight(path, key: "card-\(scale.sx)-\(scale.sy)", sweep: frame.sweep, trail: scale.x(90))
            strokeSolid(context: &cardContext, path: glint, ink: 0xd9ccff, alpha: sin(.pi * frame.sweep) * alpha, width: scale.x(2.6), radius: scale.x(3))
        }
        let checkCenter = CGPoint(x: rect.minX + scale.x(143), y: rect.minY + scale.y(23))
        cardContext.fill(Path(ellipseIn: CGRect(x: checkCenter.x - scale.x(12), y: checkCenter.y - scale.x(12), width: scale.x(24), height: scale.x(24))), with: .color(rgb(0x1d1730, alpha: 0.9 * alpha)))
        cardContext.stroke(Path(ellipseIn: CGRect(x: checkCenter.x - scale.x(12), y: checkCenter.y - scale.x(12), width: scale.x(24), height: scale.x(24))), with: .color(rgb(0x3a3150, alpha: (1 - selected) * alpha)), lineWidth: scale.x(1.2))
        let pop = card == frame.to ? max(0, frame.check) * selected : selected
        let gold = frame.entered[card]
        if pop > 0.01 || gold > 0.01 {
            let amount = min(1, max(pop, gold))
            let ps = RaffleDrumSpinMotion.mix(0.6, 1, amount) * (pop > 1 ? pop : 1)
            var check = cardContext
            check.translateBy(x: checkCenter.x, y: checkCenter.y)
            check.scaleBy(x: ps, y: ps)
            let circle = Path(ellipseIn: CGRect(x: -scale.x(12), y: -scale.x(12), width: scale.x(24), height: scale.x(24)))
            check.fill(circle, with: .color(rgb(0x6d3ff0, alpha: alpha * amount)))
            if gold > 0.01 {
                var glow = check
                glow.addFilter(.blur(radius: scale.x(6)))
                glow.fill(Path(ellipseIn: CGRect(x: -scale.x(13.2), y: -scale.x(13.2), width: scale.x(26.4), height: scale.x(26.4))), with: .color(rgb(0xffd27a, alpha: 0.6 * gold * alpha)))
                check.fill(circle, with: .linearGradient(
                    Gradient(colors: [rgb(0xfff0c2, alpha: gold * alpha), rgb(0xe0a83e, alpha: gold * alpha)]),
                    startPoint: CGPoint(x: 0, y: -scale.x(12)), endPoint: CGPoint(x: 0, y: scale.x(12))
                ))
            }
            var tick = Path()
            tick.move(to: CGPoint(x: -scale.x(5), y: scale.x(0.5)))
            tick.addLine(to: CGPoint(x: -scale.x(1.5), y: scale.x(4)))
            tick.addLine(to: CGPoint(x: scale.x(5.5), y: -scale.x(4)))
            check.stroke(tick, with: .color(rgb(gold > 0.5 ? 0x3a2206 : 0xffffff, alpha: alpha * amount)), style: StrokeStyle(lineWidth: scale.x(2.2), lineCap: .round, lineJoin: .round))
        }
        for line in cardCopy[card].indices {
            drawBaselineText(context: &cardContext, cardCopy[card][line], name: RaffleDrumSpinAssets.interRegular, size: scale.x(15), color: rgb(selected > 0.5 ? 0xc7c0d6 : 0xa9a2ba, alpha: alpha), x: rect.minX + scale.x(13), baseline: rect.minY + scale.y(140 + Double(line) * 18))
        }
    }

    /// Skia BlurStyle.Solid: the opaque stroke over its Gaussian halo, then paint alpha.
    private static func strokeSolid(context: inout GraphicsContext, path: Path, ink: Int, alpha: Double, width: Double, radius: Double) {
        var group = context
        group.opacity *= alpha
        group.drawLayer { layer in
            layer.opacity = 1
            layer.addFilter(.shadow(color: rgb(ink), radius: radius, x: 0, y: 0))
            layer.stroke(path, with: .color(rgb(ink)), style: StrokeStyle(lineWidth: width, lineCap: .round))
        }
    }

    private static func drawButton(context: inout GraphicsContext, scale: RaffleDrumSpinScale, frame: RaffleDrumSpinMotion.Frame) {
        let rect = scale.rect(RaffleDrumSpinMotion.Layout.play)
        let k = 1 - 0.04 * max(frame.press, frame.claimPress)
        var button = context
        button.translateBy(x: rect.midX, y: rect.midY)
        button.scaleBy(x: k, y: k)
        button.translateBy(x: -rect.midX, y: -rect.midY)
        let rr = Path(roundedRect: rect, cornerRadius: rect.height / 2)
        var glow = button
        glow.addFilter(.blur(radius: scale.x(16)))
        let glowRect = CGRect(x: rect.minX + 20, y: rect.minY + 10, width: rect.width - 40, height: rect.height - 6)
        glow.fill(Path(roundedRect: glowRect, cornerRadius: rect.height / 2), with: .color(rgb(0x6a1cd6, alpha: 0.4 + 0.3 * frame.speed)))
        button.fill(rr, with: .linearGradient(
            Gradient(stops: [.init(color: rgb(0x7426e6), location: 0), .init(color: rgb(0x6a1cd6), location: 0.4), .init(color: rgb(0x4a0ca8), location: 1)]),
            startPoint: CGPoint(x: rect.minX, y: rect.minY), endPoint: CGPoint(x: rect.minX, y: rect.maxY)
        ))
        if frame.fill > 0.001 && frame.drain < 0.999 {
            var liquid = button
            liquid.clip(to: rr)
            liquid.fill(Path(rect), with: .shader(ShaderLibrary.raffleDrumSpinLiquid(
                .float2(Float(rect.minX), Float(rect.minY)), .float2(Float(rect.width), Float(rect.height)),
                .float(Float(frame.fill)), .float(Float(frame.drain)),
                .float2(Float(rect.minX + scale.x(frame.drainAt)), Float(rect.midY)), .float(Float(frame.t))
            )))
            if frame.claimGlint > 0 && frame.claimGlint < 1 && frame.drain == 0 {
                let gx = RaffleDrumSpinMotion.mix(rect.minX - scale.x(60), rect.maxX + scale.x(60), frame.claimGlint)
                liquid.blendMode = .plusLighter
                liquid.fill(rr, with: .linearGradient(
                    Gradient(colors: [.clear, rgb(0xfffaeb, alpha: 0.55), .clear]),
                    startPoint: CGPoint(x: gx - scale.x(40), y: rect.minY), endPoint: CGPoint(x: gx + scale.x(40), y: rect.maxY)
                ))
            }
        }
        button.stroke(Path(roundedRect: rect.insetBy(dx: 0.5, dy: 0.5), cornerRadius: rect.height / 2), with: .color(rgb(0xb79cff, alpha: 0.55)), lineWidth: scale.x(1))
        if frame.shimmer > 0 && frame.shimmer < 1 {
            var shimmer = button
            shimmer.clip(to: rr)
            shimmer.blendMode = .plusLighter
            let sx = RaffleDrumSpinMotion.mix(rect.minX - scale.x(80), rect.maxX + scale.x(80), frame.shimmer)
            shimmer.fill(Path(rect), with: .linearGradient(
                Gradient(colors: [.clear, rgb(0xdccdff, alpha: 0.5), .clear]),
                startPoint: CGPoint(x: sx - scale.x(60), y: rect.minY), endPoint: CGPoint(x: sx + scale.x(60), y: rect.maxY)
            ))
        }
        func label(_ string: String, _ alpha: Double, _ ink: Int, _ lift: Double) {
            guard alpha > 0.01 else { return }
            var text = button
            let blur = scale.x(2 * (1 - alpha))
            if blur > 0.2 { text.addFilter(.blur(radius: blur)) }
            drawBaselineText(context: &text, string, name: RaffleDrumSpinAssets.interSemiBold, size: scale.x(17), color: rgb(ink, alpha: alpha), centerX: rect.midX, baseline: rect.midY + scale.x(6.2) + lift)
        }
        let out = RaffleDrumSpinMotion.seg(frame.claimK, 0, 0.45)
        let into = RaffleDrumSpinMotion.seg(frame.claimK, 0.55, 1)
        label("Play", 1 - out, 0xffffff, scale.x(-5 * out))
        label("Claim ticket", into, 0x3b2205, scale.x(5 * (1 - into)))
    }

    private static func drawClose(context: inout GraphicsContext, scale: RaffleDrumSpinScale) {
        let center = scale.point(362, 88)
        let circle = CGRect(x: center.x - scale.x(22), y: center.y - scale.x(22), width: scale.x(44), height: scale.x(44))
        context.fill(Path(ellipseIn: circle), with: .color(rgb(0x120a22, alpha: 0.75)))
        context.stroke(
            Path(ellipseIn: circle.insetBy(dx: scale.x(0.5), dy: scale.x(0.5))),
            with: .linearGradient(
                Gradient(colors: [rgb(0xc8b9f0, alpha: 0.55), rgb(0x7864aa, alpha: 0.15)]),
                startPoint: CGPoint(x: center.x, y: circle.minY), endPoint: CGPoint(x: center.x, y: circle.maxY)
            ), lineWidth: scale.x(1.1)
        )
        var cross = Path()
        cross.move(to: CGPoint(x: center.x - scale.x(8), y: center.y - scale.x(8)))
        cross.addLine(to: CGPoint(x: center.x + scale.x(8), y: center.y + scale.x(8)))
        cross.move(to: CGPoint(x: center.x + scale.x(8), y: center.y - scale.x(8)))
        cross.addLine(to: CGPoint(x: center.x - scale.x(8), y: center.y + scale.x(8)))
        context.stroke(cross, with: .color(rgb(0xf4f1fb)), style: StrokeStyle(lineWidth: scale.x(2.4), lineCap: .round))
    }

    private static func drawRevealLight(context: inout GraphicsContext, scale: RaffleDrumSpinScale, frame: RaffleDrumSpinMotion.Frame) {
        guard frame.streak > 0.001 || frame.rays > 0.001 || frame.ring > 0.001 || frame.aurora > 0.001 else { return }
        let ticket = scale.point(201, 432)
        let streak = CGPoint(x: frame.streakAt[0], y: frame.streakAt[1])
        let shader = ShaderLibrary.raffleDrumSpinLight(
            .float2(Float(scale.size.width), Float(scale.size.height)),
            .float2(Float(streak.x), Float(streak.y)),
            .float(Float(frame.streak)),
            .float2(Float(ticket.x), Float(ticket.y)),
            .float(Float(frame.rays)),
            .float(Float(frame.t * 0.22)),
            .float2(Float(ticket.x), Float(ticket.y)),
            .float(Float(scale.x(frame.ringR))),
            .float(Float(frame.ring)),
            .float2(Float(ticket.x), Float(ticket.y)),
            .float(Float(frame.aurora)),
            .float(Float(frame.t + frame.idleT)),
            .float(Float(scale.sx))
        )
        let bounds: CGRect
        if frame.streak <= 0.001, frame.rays <= 0.001, frame.ring <= 0.001 {
            bounds = CGRect(x: 0, y: ticket.y - scale.y(240), width: scale.size.width, height: scale.y(480))
        } else {
            bounds = CGRect(origin: .zero, size: scale.size)
        }
        context.fill(Path(bounds), with: .shader(shader, bounds: bounds))
    }

    private static func drawHeroMorph(context: inout GraphicsContext, scale: RaffleDrumSpinScale, frame: RaffleDrumSpinMotion.Frame, hero: RaffleDrumSpinMotion.Ball, ticket: Image?) {
        guard let ticket else {
            drawBaseball(context: &context, scale: scale, ball: hero, stitchProgress: frame.stitches, split: frame.morph)
            return
        }
        let center = CGPoint(x:hero.x,y:hero.y)
        let radius = hero.radius
        let target = scale.rect(RaffleDrumSpinMotion.Layout.ticket)
        let padding = 1.95 * radius
        let morphing = frame.morph > 0
        let bounds = CGRect(
            x: morphing ? min(center.x - padding, target.minX - scale.x(8)) : center.x - padding,
            y: morphing ? min(center.y - padding, target.minY - scale.y(8)) : center.y - padding,
            width: 0,
            height: 0
        )
        let maxX = morphing ? max(center.x + padding, target.maxX + scale.x(8)) : center.x + padding
        let maxY = morphing ? max(center.y + padding, target.maxY + scale.y(8)) : center.y + padding
        let drawBounds = CGRect(x: bounds.minX, y: bounds.minY, width: maxX - bounds.minX, height: maxY - bounds.minY)
        let shader = ShaderLibrary.raffleDrumSpinMorph(
            .image(ticket),
            .float2(Float(center.x), Float(center.y)),
            .float(Float(radius)),
            .float3(Float(hero.rx), Float(hero.ry), 0),
            .float(Float(frame.stitches)),
            .float(Float(frame.morph)),
            .float2(Float(scale.x(RaffleDrumSpinMotion.ticket.x)), Float(scale.y(RaffleDrumSpinMotion.ticket.y))),
            .float2(Float(scale.x(RaffleDrumSpinMotion.ticket.w)), Float(scale.y(RaffleDrumSpinMotion.ticket.h))),
            .float(Float(RaffleDrumSpinMotion.ticket.body)),
            .float2(1200, 444),
            .float(Float(scale.x(12))),
            .float(Float(hero.glow)),
            .float(1)
        )
        var copy = context
        if hero.squash != 1 {
            copy.translateBy(x: center.x, y: center.y + radius)
            copy.scaleBy(x: 1 / sqrt(hero.squash), y: hero.squash)
            copy.translateBy(x: -center.x, y: -(center.y + radius))
        }
        copy.fill(Path(drawBounds), with: .shader(shader, bounds: drawBounds))
        drawPoppedThreads(context: &copy, scale: scale, frame: frame, hero: hero)
        if frame.morph > 0.78 {
            drawTicketText(context: &context, scale: scale, frame: frame, rect: target, alpha: RaffleDrumSpinMotion.clamp01((frame.morph - 0.78) / 0.22))
        }
    }

    private static func drawPoppedThreads(
        context: inout GraphicsContext,
        scale: RaffleDrumSpinScale,
        frame: RaffleDrumSpinMotion.Frame,
        hero: RaffleDrumSpinMotion.Ball
    ) {
        guard frame.stitchT >= 0 else { return }
        let duration = RaffleDrumSpinMotion.T.stitches.upperBound - RaffleDrumSpinMotion.T.stitches.lowerBound
        let center = CGPoint(x:hero.x,y:hero.y)
        let radius = hero.radius
        for index in 0..<24 {
            let seamT = Double(index) / 24 * .pi * 2
            let order = positiveModulo(seamT / (.pi * 2) + 0.25, 1)
            let age = frame.stitchT - order * duration
            guard age >= 0, age <= 0.6 else { continue }
            let normal = seamToView(seamPoint(seamT), rx: hero.rx, ry: hero.ry)
            guard normal.z >= 0 else { continue }
            let length = max(1e-12, hypot(normal.x, normal.y))
            let dx = normal.x / length
            let dy = -normal.y / length
            let k = age / 0.6
            let distance = (150 * (1 - exp(-age * 5)) / 5) * scale.sx
            var x = center.x + normal.x * radius + dx * distance
            var y = center.y - normal.y * radius + dy * distance + 130 * age * age * scale.sy
            let spin = (RaffleDrumSpinMotion.random(index, 41) > 0.5 ? 1.0 : -1.0)
                * (2 + 3 * RaffleDrumSpinMotion.random(index, 42))
            var angle = atan2(dy, dx) + spin * age * 3
            let bend = sin(age * 26 + Double(index)) * 0.9 * (1 - k) + 0.5
            let segment = (3.6 + 1.6 * RaffleDrumSpinMotion.random(index, 43)) * scale.sx
            var thread = Path()
            thread.move(to: CGPoint(x: x, y: y))
            for _ in 0..<3 {
                x += cos(angle) * segment
                y += sin(angle) * segment
                thread.addLine(to: CGPoint(x: x, y: y))
                angle += bend
            }
            context.stroke(
                thread,
                with: .color(rgb(0xb3242a, alpha: 1 - k * k)),
                style: StrokeStyle(lineWidth: 1.5 * scale.sx, lineCap: .round)
            )
            if age < 0.09 {
                let pin = CGPoint(x: center.x + normal.x * radius, y: center.y - normal.y * radius)
                context.fill(
                    Path(ellipseIn: CGRect(
                        x: pin.x - (1.2 + 2.4 * age / 0.09) * scale.sx,
                        y: pin.y - (1.2 + 2.4 * age / 0.09) * scale.sx,
                        width: 2 * (1.2 + 2.4 * age / 0.09) * scale.sx,
                        height: 2 * (1.2 + 2.4 * age / 0.09) * scale.sx
                    )),
                    with: .color(rgb(0xffe6b0, alpha: 1 - age / 0.09))
                )
            }
        }
    }

    private static func seamPoint(_ t: Double) -> (x: Double, y: Double, z: Double) {
        let a = 0.78
        let b = 0.22
        return (
            a * cos(t) + b * cos(3 * t),
            a * sin(t) - b * sin(3 * t),
            2 * sqrt(a * b) * sin(2 * t)
        )
    }

    private static func seamToView(
        _ point: (x: Double, y: Double, z: Double),
        rx: Double,
        ry: Double
    ) -> (x: Double, y: Double, z: Double) {
        let cy = cos(-ry)
        let sy = sin(-ry)
        let x1 = cy * point.x + sy * point.z
        let z1 = -sy * point.x + cy * point.z
        let cx = cos(-rx)
        let sx = sin(-rx)
        return (x1, cx * point.y - sx * z1, sx * point.y + cx * z1)
    }

    private static func positiveModulo(_ value: Double, _ divisor: Double) -> Double {
        ((value.truncatingRemainder(dividingBy: divisor)) + divisor).truncatingRemainder(dividingBy: divisor)
    }

    private static func drawTicket(context: inout GraphicsContext, scale: RaffleDrumSpinScale, frame: RaffleDrumSpinMotion.Frame, ticket: Image?) {
        guard frame.burn < 1 else { return }
        let base = scale.rect(RaffleDrumSpinMotion.Layout.ticket)
        let bob = sin((frame.t - RaffleDrumSpinMotion.T.morph.upperBound) * 2.1) * scale.y(3) * (1 - frame.burn)
        let rect = base.offsetBy(dx: 0, dy: bob)
        let bodyWidth = rect.width * RaffleDrumSpinMotion.Layout.ticketBody
        var body = context
        body.clip(to: Path(CGRect(
            x: rect.minX,
            y: rect.minY,
            width: frame.tear == 0 ? rect.width : bodyWidth,
            height: rect.height
        )))
        if let ticket {
            func foil(_ mode: Float, sweep: Double = 0, lit: Double = 0) -> Shader {
                ShaderLibrary.raffleDrumSpinFoil(
                    .image(ticket),
                    .float2(Float(rect.minX), Float(rect.minY)),
                    .float2(Float(rect.width), Float(rect.height)),
                    .float2(1200, 444),
                    .float2(Float(frame.tilt[0]), Float(frame.tilt[1])),
                    .float(Float(lit)),
                    .float(Float(sweep)),
                    .float(mode),
                    .float(Float(frame.burn)),
                    .float(Float(RaffleDrumSpinMotion.Layout.ticketBody))
                )
            }
            if frame.burn > 0 {
                body.drawLayer { layer in
                    layer.fill(Path(rect), with: .shader(foil(0, sweep: frame.foil, lit: frame.lit)))
                    drawTicketText(
                        context: &layer, scale: scale, frame: frame, rect: rect, alpha: 1,
                        stub: frame.tear == 0, body: true
                    )
                    var mask = layer
                    mask.blendMode = .destinationIn
                    mask.fill(
                        Path(CGRect(x: rect.minX, y: rect.minY, width: bodyWidth, height: rect.height)),
                        with: .shader(foil(1))
                    )
                }
                var edge = body
                edge.blendMode = .plusLighter
                edge.fill(
                    Path(CGRect(x: rect.minX, y: rect.minY, width: bodyWidth, height: rect.height)),
                    with: .shader(foil(2))
                )
            } else {
                body.fill(Path(rect), with: .shader(foil(0, sweep: frame.foil, lit: frame.lit)))
                drawTicketText(
                    context: &body, scale: scale, frame: frame, rect: rect, alpha: 1,
                    stub: frame.tear == 0, body: true
                )
            }
        } else {
            body.fill(Path(roundedRect: rect, cornerRadius: scale.x(12)), with: .color(Color(red: 0.95, green: 0.72, blue: 0.28)))
            drawTicketText(
                context: &body, scale: scale, frame: frame, rect: rect, alpha: 1,
                stub: frame.tear == 0, body: true
            )
        }

    }

    static func ticketGlow(context: inout GraphicsContext, size: CGSize, frame: RaffleDrumSpinMotion.Frame) {
        guard frame.morph >= 1, frame.burn < 1 else { return }
        let scale = RaffleDrumSpinScale(size: size)
        let tr = scale.rect(RaffleDrumSpinMotion.Layout.ticket)
        let held = frame.t - RaffleDrumSpinMotion.T.morph.upperBound
        let bob = held > 0 ? sin(held * 2.1) * scale.y(3) * (1 - frame.burn) : 0
        let rect = CGRect(x: tr.minX + 12, y: tr.minY + bob + 16, width: tr.width - 24, height: tr.height - 8)
        var glow = context
        glow.addFilter(.blur(radius: scale.x(28)))
        glow.fill(Path(roundedRect: rect, cornerRadius: 20), with: .color(rgb(0xffd27a, alpha: 0.32 * (1-frame.burn))))
    }

    static func ticketExtras(context: inout GraphicsContext, size: CGSize, frame: RaffleDrumSpinMotion.Frame, ticket: Image?) {
        let scale = RaffleDrumSpinScale(size: size)
        if frame.morph >= 1 {
            let base = scale.rect(RaffleDrumSpinMotion.Layout.ticket)
            let held = frame.t - RaffleDrumSpinMotion.T.morph.upperBound
            let bob = held > 0 ? sin(held * 2.1) * scale.y(3) * (1 - frame.burn) : 0
            let rect = base.offsetBy(dx: 0, dy: bob)
            drawBurnDust(context: &context, scale: scale, frame: frame, rect: rect)
            if frame.tear > 0 {
                drawStub(context: &context, scale: scale, frame: frame, rect: rect, ticket: ticket)
                let ripK = RaffleDrumSpinMotion.seg(frame.tear, 0, 0.25)
                if ripK > 0 && ripK < 1 {
                    let fx = base.minX + base.width * RaffleDrumSpinMotion.Layout.ticketBody
                    for index in 0..<10 {
                        let fy = base.minY + (Double(index) + 0.5) * base.height / 10
                        let life = sin(.pi * RaffleDrumSpinMotion.seg(ripK, Double(index)*0.06, Double(index)*0.06+0.4))
                        guard life > 0 else { continue }
                        let x = fx + (RaffleDrumSpinMotion.random(index, 81) - 0.3) * scale.x(10) * ripK
                        let r = scale.x(1.4)
                        context.fill(Path(ellipseIn: CGRect(x:x-r,y:fy-r,width:r*2,height:r*2)), with: .color(rgb(0xfff1c8, alpha: life)))
                    }
                }
            }
        }
        drawLanding(context: &context, scale: scale, frame: frame)
        drawCrankHint(context: &context, scale: scale, frame: frame)
    }

    private static func drawLanding(context: inout GraphicsContext, scale: RaffleDrumSpinScale, frame: RaffleDrumSpinMotion.Frame) {
        guard frame.land >= 0 else { return }
        let x = scale.x(RaffleDrumSpinMotion.Layout.cardX + Double(frame.to) * (RaffleDrumSpinMotion.Layout.cardWidth + RaffleDrumSpinMotion.Layout.cardGap) + frame.strip + 143)
        let y = scale.y(RaffleDrumSpinMotion.Layout.cardY - 2 + 23)
        let k = RaffleDrumSpinMotion.easeOut(min(1, frame.land / 0.5))
        let r = scale.x(RaffleDrumSpinMotion.mix(12, 34, k))
        context.stroke(Path(ellipseIn: CGRect(x: x-r, y: y-r, width: 2*r, height: 2*r)), with: .color(rgb(0xffdf8a, alpha: 1-k)), lineWidth: scale.x(RaffleDrumSpinMotion.mix(2.6, 0.6, k)))
        if frame.land < 0.3 {
            let q = frame.land / 0.3
            let r0 = scale.x(RaffleDrumSpinMotion.mix(14, 22, RaffleDrumSpinMotion.easeOut(q)))
            let r1 = r0 + scale.x(RaffleDrumSpinMotion.mix(6, 2, q))
            var rays = Path()
            for index in 0..<8 {
                let angle = Double(index) / 8 * .pi * 2 + 0.3
                rays.move(to: CGPoint(x: x + cos(angle)*r0, y: y + sin(angle)*r0))
                rays.addLine(to: CGPoint(x: x + cos(angle)*r1, y: y + sin(angle)*r1))
            }
            context.stroke(rays, with: .color(rgb(0xfff3c8, alpha: 1-q)), style: StrokeStyle(lineWidth: scale.x(1.6), lineCap: .round))
        }
    }

    private static func drawCrankHint(context: inout GraphicsContext, scale: RaffleDrumSpinScale, frame: RaffleDrumSpinMotion.Frame) {
        guard frame.focus <= 0.001 else { return }
        let u = (frame.t + 0.45).truncatingRemainder(dividingBy: RaffleDrumSpinMotion.loop) / 0.9
        guard u < 1 else { return }
        let a = sin(.pi * u)
        let p = RaffleDrumSpinMotion.project(1.58, -0.2 * cos(frame.theta), -0.2 * sin(frame.theta), scale.sx)
        var glow = context
        glow.addFilter(.blur(radius: scale.x(6)))
        let r = scale.x(8)
        glow.fill(Path(ellipseIn: CGRect(x: p.x-r, y: p.y-r, width: 2*r, height: 2*r)), with: .color(rgb(0xfff1dc, alpha: 0.55*a)))
        let sparkX = p.x + scale.x(RaffleDrumSpinMotion.mix(-10, 10, u))
        let sparkY = p.y - scale.x(1.5)
        let sr = scale.x(1.6)
        context.fill(Path(ellipseIn: CGRect(x: sparkX-sr, y: sparkY-sr, width: 2*sr, height: 2*sr)), with: .color(.white.opacity(0.9*a)))
    }

    private static func drawStub(context: inout GraphicsContext, scale: RaffleDrumSpinScale, frame: RaffleDrumSpinMotion.Frame, rect: CGRect, ticket: Image?) {
        let motion = RaffleDrumSpinMotion.self
        let split = motion.Layout.ticketBody
        let sw: Double = rect.width * (1 - split)
        let sx0: Double = rect.minX + rect.width * split + sw / 2
        let sy0: Double = scale.y(432)
        let kx: Double = scale.x(motion.Layout.cardX + Double(frame.to) * (motion.Layout.cardWidth + motion.Layout.cardGap) + frame.strip + 143)
        let ky: Double = scale.y(motion.Layout.cardY - 2 + 23)
        let ripK: Double = motion.clamp01(frame.tear / 0.25)
        let flyK: Double = motion.easeInOut(motion.clamp01((frame.tear - 0.25) / 0.75))
        let v: Double = 1 - flyK
        let px: Double = v * v * (sx0 + scale.x(8) * ripK) + 2 * v * flyK * motion.mix(sx0, kx, 0.5) + flyK * flyK * kx
        let py: Double = v * v * sy0 + 2 * v * flyK * (min(sy0, ky) - scale.y(120)) + flyK * flyK * ky
        let rot: Double = -9 * sin(.pi * min(1, ripK * 1.4)) * (1 - flyK) + 140 * flyK * (1 - flyK)
        let sc: Double = motion.mix(1, scale.x(24) / rect.height, flyK)
        let flip: Double = cos(flyK * .pi * 2)
        let alpha: Double = 1 - motion.clamp01((frame.tear - 0.9) / 0.1)
        guard alpha > 0.01 else { return }
        var stub = context
        stub.translateBy(x: px, y: py)
        stub.rotate(by: .degrees(rot))
        stub.scaleBy(x: sc * max(0.04, abs(flip)), y: sc)
        stub.opacity = alpha
        let target = CGRect(x: -sw / 2, y: -rect.height / 2, width: sw, height: rect.height)
        stub.drawLayer { layer in
            if flip >= 0, let ticket {
                layer.fill(Path(target), with: .shader(ShaderLibrary.raffleDrumSpinFoil(
                    .image(ticket),
                    .float2(Float(-sw / 2 - rect.width * split), Float(-rect.height / 2)),
                    .float2(Float(rect.width), Float(rect.height)), .float2(1200, 444),
                    .float2(Float(flyK * .pi * 2), 0), .float(1), .float(0), .float(0),
                    .float(Float(frame.burn)), .float(Float(split))
                )))
                var printLayer = layer
                printLayer.translateBy(x: -sx0, y: -sy0)
                let base = scale.rect(motion.Layout.ticket)
                drawTicketText(context: &printLayer, scale: scale, frame: frame, rect: base, alpha: 1, stub: true, body: false)
            } else if let back = RaffleDrumSpinAssets.image("golden-ticket-back") {
                layer.clip(to: Path(target))
                layer.draw(back, in: CGRect(x: -sw / 2 - rect.width * split, y: -rect.height / 2, width: rect.width, height: rect.height))
            }
            layer.blendMode = .sourceAtop
            layer.fill(Path(target), with: .color(.black.opacity(0.45 * (1 - abs(flip)))))
        }
    }

    private static func drawTicketText(
        context: inout GraphicsContext,
        scale: RaffleDrumSpinScale,
        frame: RaffleDrumSpinMotion.Frame,
        rect: CGRect,
        alpha: Double,
        stub: Bool = true,
        body: Bool = true
    ) {
        let bodyWidth = rect.width * RaffleDrumSpinMotion.Layout.ticketBody
        let bodyCenterX = rect.minX + bodyWidth / 2
        let ink = rgb(0x4a2c07)
        let highlight = rgb(0xfff1c4)
        if body {
            let captionSize = scale.x(10.5)
            let captionBaseline = rect.minY + rect.height * 0.36
            drawTicketTrackedBaselineText(
                context: &context, "WINNING ENTRY", name: RaffleDrumSpinAssets.sourceSerif,
                size: captionSize, color: highlight.opacity(0.5 * alpha), centerX: bodyCenterX,
                baseline: captionBaseline + scale.x(0.7), trackingEm: 0.12
            )
            drawTicketTrackedBaselineText(
                context: &context, "WINNING ENTRY", name: RaffleDrumSpinAssets.sourceSerif,
                size: captionSize, color: ink.opacity(0.85 * alpha), centerX: bodyCenterX,
                baseline: captionBaseline, trackingEm: 0.12
            )

            let entrySize = scale.x(30)
            let digitWidth = textWidth("0", name: RaffleDrumSpinAssets.sourceSerif, size: entrySize)
            let digits = Array("0847")
            let total = digitWidth * Double(digits.count + 1)
            let numberBaseline = rect.minY + rect.height * 0.72
            var numberContext = context
            numberContext.clip(to: Path(CGRect(
                x: bodyCenterX - total / 2 - scale.x(4),
                y: numberBaseline - entrySize * 0.95,
                width: total + scale.x(8),
                height: entrySize * 1.15
            )))
            drawTicketBaselineText(
                context: &numberContext, "#", name: RaffleDrumSpinAssets.sourceSerif, size: entrySize,
                color: highlight.opacity(0.45 * alpha), x: bodyCenterX - total / 2,
                baseline: numberBaseline + scale.x(0.8)
            )
            drawTicketBaselineText(
                context: &numberContext, "#", name: RaffleDrumSpinAssets.sourceSerif, size: entrySize,
                color: ink.opacity(alpha), x: bodyCenterX - total / 2, baseline: numberBaseline
            )
            for (index, character) in digits.enumerated() {
                let progress = RaffleDrumSpinMotion.easeOut(RaffleDrumSpinMotion.clamp01(
                    (frame.number - Double(index) * 0.12) / 0.55
                ))
                let target = Int(String(character)) ?? 0
                let spins = 2 + index
                let position = (1 - progress) * Double(spins * 10)
                let baseStep = floor(position)
                let fraction = position - baseStep
                let x = bodyCenterX - total / 2 + digitWidth * Double(index + 1)
                for offset in -1...1 {
                    let step = Int(baseStep) + offset
                    let digit = ((target - step) % 10 + 10) % 10
                    let y = numberBaseline + (fraction - Double(offset)) * entrySize * -1
                    if progress > 0.6 {
                        drawTicketBaselineText(
                            context: &numberContext, "\(digit)", name: RaffleDrumSpinAssets.sourceSerif,
                            size: entrySize, color: highlight.opacity(0.45 * alpha * progress), x: x,
                            baseline: y + scale.x(0.8)
                        )
                    }
                    var digitContext = numberContext
                    let blur = min(scale.x(3), (1 - progress) * scale.x(6))
                    if blur > scale.x(0.2) { digitContext.addFilter(.blur(radius: blur)) }
                    drawTicketBaselineText(
                        context: &digitContext, "\(digit)", name: RaffleDrumSpinAssets.sourceSerif,
                        size: entrySize, color: ink.opacity(alpha), x: x, baseline: y
                    )
                }
            }
        }
        guard stub else { return }
        let stubCenterX = rect.minX + bodyWidth + (rect.width - bodyWidth) / 2
        let stubSize = scale.x(8)
        drawTicketTrackedBaselineText(
            context: &context, "ENTRIES", name: RaffleDrumSpinAssets.interSemiBold,
            size: stubSize, color: ink.opacity(0.8 * alpha), centerX: stubCenterX,
            baseline: rect.minY + rect.height * 0.4, trackingEm: 0.08
        )
        let stubBigSize = scale.x(22)
        let stubText = "×\(frame.to + 1)"
        let stubBaseline = rect.minY + rect.height * 0.7
        drawTicketBaselineText(
            context: &context, stubText, name: RaffleDrumSpinAssets.sourceSerif, size: stubBigSize,
            color: highlight.opacity(0.45 * alpha), centerX: stubCenterX,
            baseline: stubBaseline + scale.x(0.8)
        )
        drawTicketBaselineText(
            context: &context, stubText, name: RaffleDrumSpinAssets.sourceSerif, size: stubBigSize,
            color: ink.opacity(alpha), centerX: stubCenterX, baseline: stubBaseline
        )
    }

    private static func drawBurst(context: inout GraphicsContext, scale: RaffleDrumSpinScale, frame: RaffleDrumSpinMotion.Frame) {
        guard frame.burstAge >= 0, frame.burstAge < 2.4 else { return }
        let center = scale.point(201, 432)
        let age = frame.burstAge
        for index in 0..<130 {
            let angle = RaffleDrumSpinMotion.random(index, 1) * .pi * 2
            let speed = 260 + 560 * pow(RaffleDrumSpinMotion.random(index, 3), 0.8)
            let travel = speed * (1 - exp(-3.2 * age)) / 3.2
            let fall = 75 * age * age + 26 * age
            let x = center.x + cos(angle) * scale.x(40 + travel * 0.5)
            let y = center.y + sin(angle) * scale.y(25 + travel * 0.35) + scale.y(fall)
            let life = 1 - RaffleDrumSpinMotion.clamp01((age - (1.4 + 0.6 * RaffleDrumSpinMotion.random(index, 12))) / 0.9)
            let length = scale.x(3 + 8 * RaffleDrumSpinMotion.random(index, 5))
            var particle = context
            particle.translateBy(x: x, y: y)
            particle.rotate(by: .radians(angle + age * (5 + 8 * RaffleDrumSpinMotion.random(index, 10))))
            let color = RaffleDrumSpinMotion.random(index, 13) > 0.7 ? Color(red: 0.66, green: 0.45, blue: 1) : Color(red: 1, green: 0.78, blue: 0.34)
            particle.fill(Path(roundedRect: CGRect(x: -length, y: -length * 0.25, width: length * 2, height: length * 0.5), cornerRadius: scale.x(1)), with: .color(color.opacity(life)))
        }
    }

    private static func drawBurnDust(context: inout GraphicsContext, scale: RaffleDrumSpinScale, frame: RaffleDrumSpinMotion.Frame, rect: CGRect) {
        let motion = RaffleDrumSpinMotion.self
        guard frame.t >= motion.T.burn.lowerBound, frame.t <= motion.T.burn.upperBound + motion.T.dust else { return }
        let targetX = motion.Layout.cardX + Double(frame.to) * (motion.Layout.cardWidth + motion.Layout.cardGap) + frame.strip + motion.Layout.cardWidth / 2
        let target = scale.point(targetX, motion.Layout.cardY + 62)
        let duration = motion.T.burn.upperBound - motion.T.burn.lowerBound
        var big = Path()
        var small = Path()
        for index in 0..<120 {
            let u: Double = motion.random(index, 61) * motion.Layout.ticketBody
            let v: Double = 0.12 + 0.76 * motion.random(index, 62)
            let at: Double = (motion.Layout.ticketBody + 0.09 - u + (motion.random(index, 63) - 0.5) * 0.14) / (motion.Layout.ticketBody + 0.27)
            let age: Double = frame.t - (motion.T.burn.lowerBound + at * duration)
            guard age >= 0, age <= motion.T.dust else { continue }
            let k: Double = motion.easeInOut(age / motion.T.dust)
            let x0: Double = rect.minX + u * rect.width
            let y0: Double = rect.minY + v * rect.height
            let qx: Double = x0 + (motion.random(index, 64) - 0.5) * scale.x(90)
            let qy: Double = y0 - scale.y(30 + 50 * motion.random(index, 65))
            let x1: Double = target.x + (motion.random(index, 66) - 0.5) * scale.x(70)
            let y1: Double = target.y + (motion.random(index, 67) - 0.5) * scale.y(40)
            let w: Double = 1 - k
            let swirl: Double = sin(age * 14 + Double(index)) * scale.x(5) * w
            let point = CGPoint(x: w * w * x0 + 2 * w * k * qx + k * k * x1 + swirl, y: w * w * y0 + 2 * w * k * qy + k * k * y1)
            if motion.random(index, 68) > 0.6 {
                big.move(to: point); big.addLine(to: CGPoint(x: point.x + 0.001, y: point.y))
            } else {
                small.move(to: point); small.addLine(to: CGPoint(x: point.x + 0.001, y: point.y))
            }
        }
        let fade = min(1, (frame.t - motion.T.burn.lowerBound) / 0.08)
        for (path, haloWidth, coreWidth) in [(big, 7.0, 2.6), (small, 4.5, 1.6)] {
            var halo = context
            halo.addFilter(.blur(radius: scale.x(3)))
            halo.stroke(path, with: .color(rgb(0xffd27a, alpha: 0.55 * fade)), style: StrokeStyle(lineWidth: scale.x(haloWidth), lineCap: .round))
            context.stroke(path, with: .color(rgb(0xfff6dc, alpha: fade)), style: StrokeStyle(lineWidth: scale.x(coreWidth), lineCap: .round))
        }
    }

    private static func drawBaseball(context: inout GraphicsContext, scale: RaffleDrumSpinScale, ball: RaffleDrumSpinMotion.Ball, stitchProgress: Double = 0, split: Double = 0) {
        guard ball.alpha > 0.001 else { return }
        let center = CGPoint(x:ball.x,y:ball.y)
        let radius = ball.radius
        let blurX = 0.0
        let extent = radius * (1 + blurX) + 2
        let bounds = CGRect(x: center.x - extent, y: center.y - radius - 2, width: extent * 2, height: radius * 2 + 4)
        context.fill(Path(bounds), with: .shader(ShaderLibrary.raffleDrumSpinBall(
            .float2(Float(center.x), Float(center.y)),
            .float(Float(radius)),
            .float3(Float(ball.rx), Float(ball.ry), Float(ball.rz)),
            .float(Float(ball.alpha)),
            .float(Float(blurX))
        )))
        _ = stitchProgress
        _ = split
    }

    private static let star: Path = {
        var p = Path()
        p.move(to: CGPoint(x:0,y:-10))
        p.addQuadCurve(to: CGPoint(x:10,y:0), control: CGPoint(x:0.9,y:-0.9))
        p.addQuadCurve(to: CGPoint(x:0,y:10), control: CGPoint(x:0.9,y:0.9))
        p.addQuadCurve(to: CGPoint(x:-10,y:0), control: CGPoint(x:-0.9,y:0.9))
        p.addQuadCurve(to: CGPoint(x:0,y:-10), control: CGPoint(x:-0.9,y:-0.9))
        p.closeSubpath()
        return p
    }()

    private static func drawSparkle(context: inout GraphicsContext, point: CGPoint, radius: Double, alpha: Double) {
        guard alpha > 0.01 else { return }
        let factor = radius / 10
        let path = star.applying(CGAffineTransform(a:factor,b:0,c:0,d:factor,tx:point.x,ty:point.y))
        var glow = context
        glow.addFilter(.blur(radius: 4 * factor))
        glow.fill(path, with: .color(rgb(0xb9a6ff, alpha: 0.5 * alpha)))
        context.fill(path, with: .color(rgb(0xf3efff, alpha: alpha)))
    }

    private static func drawText(context: inout GraphicsContext, _ string: String, font: Font, color: Color, at point: CGPoint, anchor: UnitPoint) {
        context.draw(Text(string).font(font).foregroundStyle(color), at: point, anchor: anchor)
    }

    private static func rgb(_ value: Int, alpha: Double = 1) -> Color {
        Color(
            red: Double((value >> 16) & 255) / 255,
            green: Double((value >> 8) & 255) / 255,
            blue: Double(value & 255) / 255,
            opacity: alpha
        )
    }

    private static func uiFont(name: String, size: Double) -> UIFont {
        UIFont(name: name, size: size) ?? UIFont.systemFont(ofSize: size)
    }

    private static func textWidth(_ string: String, name: String, size: Double) -> Double {
        glyphRuns(string, name: name, size: size).reduce(0) { $0 + $1.advance }
    }

    private static func glyphRuns(_ string: String, name: String, size: Double) -> [(glyph: String, advance: Double)] {
        RaffleDrumSpinText.glyphRuns(string, name: name, size: size)
    }

    private static func fittedSize(name: String, text: String, width: Double) -> Double {
        let probe = textWidth(text, name: name, size: 100)
        return probe > 0 ? width * 100 / probe : 100
    }

    private static func drawTicketBaselineText(context: inout GraphicsContext, _ string: String, name: String, size: Double, color: Color, x: Double? = nil, centerX: Double? = nil, baseline: Double) {
        RaffleDrumSpinText.draw(context:&context,string:string,name:name,size:size,color:color,x:x,centerX:centerX,baseline:baseline,baselineSnap:false)
    }

    private static func drawTicketTrackedBaselineText(context: inout GraphicsContext, _ string: String, name: String, size: Double, color: Color, centerX: Double, baseline: Double, trackingEm: Double) {
        RaffleDrumSpinText.draw(context:&context,string:string,name:name,size:size,color:color,x:nil,centerX:centerX,baseline:baseline,trackingEm:trackingEm,baselineSnap:false)
    }

    private static func drawBaselineText(
        context: inout GraphicsContext,
        _ string: String,
        name: String,
        size: Double,
        color: Color,
        x: Double? = nil,
        centerX: Double? = nil,
        baseline: Double
    ) {
        RaffleDrumSpinText.draw(context: &context, string: string, name: name, size: size, color: color, x: x, centerX: centerX, baseline: baseline)
    }

    private static func drawTrackedBaselineText(
        context: inout GraphicsContext,
        _ string: String,
        name: String,
        size: Double,
        color: Color,
        centerX: Double,
        baseline: Double,
        trackingEm: Double
    ) {
        RaffleDrumSpinText.draw(context: &context, string: string, name: name, size: size, color: color, x: nil, centerX: centerX, baseline: baseline, trackingEm: trackingEm)
    }

    private static func ticketPath(rect: CGRect, radius: Double, notchWidth: Double, notchDepth: Double) -> Path {
        let x = rect.minX, y = rect.minY, width = rect.width, height = rect.height
        let centerX = rect.midX
        var path = Path()
        path.move(to: CGPoint(x: x + radius, y: y))
        path.addLine(to: CGPoint(x: centerX - notchWidth / 2 - notchDepth, y: y))
        path.addCurve(
            to: CGPoint(x: centerX - notchWidth / 2 + notchDepth * 2, y: y + notchDepth),
            control1: CGPoint(x: centerX - notchWidth / 2 + notchDepth * 0.6, y: y),
            control2: CGPoint(x: centerX - notchWidth / 2 + notchDepth * 0.4, y: y + notchDepth)
        )
        path.addLine(to: CGPoint(x: centerX + notchWidth / 2 - notchDepth * 2, y: y + notchDepth))
        path.addCurve(
            to: CGPoint(x: centerX + notchWidth / 2 + notchDepth, y: y),
            control1: CGPoint(x: centerX + notchWidth / 2 - notchDepth * 0.4, y: y + notchDepth),
            control2: CGPoint(x: centerX + notchWidth / 2 - notchDepth * 0.6, y: y)
        )
        path.addLine(to: CGPoint(x: x + width - radius, y: y))
        path.addQuadCurve(to: CGPoint(x: x + width, y: y + radius), control: CGPoint(x: x + width, y: y))
        path.addLine(to: CGPoint(x: x + width, y: y + height - radius))
        path.addQuadCurve(to: CGPoint(x: x + width - radius, y: y + height), control: CGPoint(x: x + width, y: y + height))
        path.addLine(to: CGPoint(x: centerX + notchWidth / 2 + notchDepth, y: y + height))
        path.addCurve(
            to: CGPoint(x: centerX + notchWidth / 2 - notchDepth * 2, y: y + height - notchDepth),
            control1: CGPoint(x: centerX + notchWidth / 2 - notchDepth * 0.6, y: y + height),
            control2: CGPoint(x: centerX + notchWidth / 2 - notchDepth * 0.4, y: y + height - notchDepth)
        )
        path.addLine(to: CGPoint(x: centerX - notchWidth / 2 + notchDepth * 2, y: y + height - notchDepth))
        path.addCurve(
            to: CGPoint(x: centerX - notchWidth / 2 - notchDepth, y: y + height),
            control1: CGPoint(x: centerX - notchWidth / 2 + notchDepth * 0.4, y: y + height - notchDepth),
            control2: CGPoint(x: centerX - notchWidth / 2 + notchDepth * 0.6, y: y + height)
        )
        path.addLine(to: CGPoint(x: x + radius, y: y + height))
        path.addQuadCurve(to: CGPoint(x: x, y: y + height - radius), control: CGPoint(x: x, y: y + height))
        path.addLine(to: CGPoint(x: x, y: y + radius))
        path.addQuadCurve(to: CGPoint(x: x + radius, y: y), control: CGPoint(x: x, y: y))
        path.closeSubpath()
        return path
    }
}

private struct RaffleDrumSpinScale {
    let size: CGSize
    let sx: Double
    let sy: Double

    init(size: CGSize) {
        self.size = size
        sx = size.width / RaffleDrumSpinMotion.referenceWidth
        sy = size.height / RaffleDrumSpinMotion.referenceHeight
    }

    func x(_ value: Double) -> Double { value * sx }
    func y(_ value: Double) -> Double { value * sy }
    func point(_ x: Double, _ y: Double) -> CGPoint { CGPoint(x: self.x(x), y: self.y(y)) }
    func rect(_ rect: CGRect) -> CGRect { CGRect(x: x(rect.minX), y: y(rect.minY), width: x(rect.width), height: y(rect.height)) }
}
