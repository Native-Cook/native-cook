import SwiftUI

struct NeonContourMoodFrame: View {
    let frame: NeonContourMoodDriver.Frame
    let layout: NeonContourMoodLayout
    let art: NeonContourMoodArt
    private typealias T = NeonContourMoodTheme
    private typealias M = NeonContourMoodMotion
    private typealias R = NeonContourMoodResources

    var body: some View {
        let l = layout, s = l.scale, look = frame.look
        let thumbX = l.firstX + M.clamp(look.mood, 0, 6) / 6 * (l.lastX - l.firstX) + frame.edge
        let thumbY = l.sliderY + l.sliderHeight / 2
        let radius = 25 * s * (1 + 0.12 * max(look.press, frame.press))
        let hue = T.ramp(look.mood, 0)
        let thumbUniforms = [radius, radius, radius, frame.press] + hue + T.rgb(0x110713)
        ZStack(alignment: .topLeading) {
            T.color(0x110713)
            Rectangle().fill(.white)
                .colorEffect(ShaderLibrary.neonContourMoodField(.floatArray(uniforms)))
                .frame(width: l.width, height: l.height, alignment: .topLeading)
                .mask(alignment: .topLeading) {
                    RoundedRectangle(cornerRadius: 54 * s)
                        .frame(width: l.width, height: l.height, alignment: .topLeading)
                        .offset(y: l.sheetY)
                }
                .opacity(frame.fade)
            Canvas(opaque: false, colorMode: .nonLinear) { c, _ in
                drawChrome(c)
                var labels = c
                labels.opacity = frame.fade
                drawLabels(labels)
                drawControls(c, thumbX: thumbX, thumbY: thumbY, radius: radius, hue: hue)
            }
            .frame(width: l.width, height: l.height, alignment: .topLeading)
            Rectangle().fill(.white)
                .colorEffect(ShaderLibrary.neonContourMoodThumb(.floatArray(thumbUniforms.map(Float.init))))
                .frame(width: 2 * radius, height: 2 * radius, alignment: .topLeading)
                // The source Circle also clips the shader's 0.98..1.02 AA fringe.
                .clipShape(Circle())
                .offset(x: thumbX - radius, y: thumbY - radius)
            Canvas(opaque: false, colorMode: .nonLinear) { c, _ in drawEndpointsAndButton(c) }
                .frame(width: l.width, height: l.height, alignment: .topLeading)
        }
        .frame(width: l.width, height: l.height, alignment: .topLeading)
    }
    private var uniforms: [Float] {
        let l = layout, look = frame.look
        let m = M.clamp(look.echoes[0], 0, 6), a = Int(floor(m)), b = min(6, a + 1), f = m - Double(a)
        let w = f * f * (3 - 2 * f)
        let warm = M.mix(T.moods[a].shape[7], T.moods[b].shape[7], w)
        var u = [l.width / 2, l.heroY, l.heroScale, l.tagY + 120 * l.scale, 140 * l.scale,
                 look.wake, look.breath, look.radiate * max(0, look.time - 3.85) * 26,
                 warm, frame.pulse, 26 + frame.squeeze, 7, frame.leanX, frame.leanY]
        var shapesA: [Double] = [], shapesB: [Double] = [], tints: [Double] = [], lower: [Double] = []
        for k in 0..<7 {
            let e = M.clamp(look.echoes[k], 0, 6), i = Int(floor(e)), j = min(6, i + 1), fm = e - Double(i)
            let mix = fm * fm * (3 - 2 * fm)
            let sh = (0..<7).map { M.mix(T.moods[i].shape[$0], T.moods[j].shape[$0], mix) }
            shapesA += Array(sh[0..<4]); shapesB += [sh[4], sh[5], sh[6], 0]
            tints += T.ramp(e, T.stops[k], linear: true)
            lower += T.ramp(e, min(T.stops[k], 2), linear: true)
        }
        u += shapesA; u += shapesB; u += tints; u += lower
        u += T.ramp(look.echoes[0], 0, linear: true); u += T.rgb(0x110713); u += T.rgb(0x1C0104)
        return u.map(Float.init)
    }
    private func rounded(_ rect: CGRect, _ r: Double) -> Path { Path(roundedRect: rect, cornerRadius: r) }
    private func circle(_ x: Double, _ y: Double, _ r: Double) -> Path {
        Path(ellipseIn: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2))
    }
    private func center(_ c: GraphicsContext, _ line: R.Line, _ y: Double, _ color: Color) {
        R.draw(c, line, x: (layout.width - line.width) / 2, y: y, color: color)
    }
    private func drawChrome(_ context: GraphicsContext) {
        let l = layout, s = l.scale, fs = l.fontScale, hue = T.ramp(frame.look.mood, 0)
        context.fill(rounded(CGRect(x: (l.width - 36 * s) / 2, y: l.sheetY + 16 * s, width: 36 * s, height: 4 * s), 2 * s), with: .color(T.color(0x2A1A32)))
        for i in art.title.indices { center(context, art.title[i], l.titleY + Double(i) * 24 * s * fs, T.color(0xFBF9FC)) }
        var c = context
        c.opacity = 1 - frame.closePress * 0.4
        c.stroke(circle(l.closeX + 22 * s, l.closeY + 22 * s, 22 * s), with: .color(T.color(T.blend(T.rgb(0x8E6F93), hue, 0.3))), lineWidth: 0.65 * s)
        var cross = Path()
        cross.move(to: CGPoint(x: l.closeX + 14 * s, y: l.closeY + 14 * s))
        cross.addLine(to: CGPoint(x: l.closeX + 30 * s, y: l.closeY + 30 * s))
        cross.move(to: CGPoint(x: l.closeX + 30 * s, y: l.closeY + 14 * s))
        cross.addLine(to: CGPoint(x: l.closeX + 14 * s, y: l.closeY + 30 * s))
        c.stroke(cross, with: .color(T.color(0xD2C7DF)), lineWidth: 1.5 * s)
    }
    private func drawLabels(_ context: GraphicsContext) {
        let l = layout, s = l.scale, fs = l.fontScale, lab = frame.lab
        for index in T.moods.indices {
            let word = R.words[index], baseline = l.heroY + 19 * l.heroScale
            let cy = baseline - 54 * l.heroScale * 0.36, zoom = M.word(lab, index)
            var c = context
            c.translateBy(x: l.width / 2, y: cy); c.scaleBy(x: zoom, y: zoom)
            c.translateBy(x: -l.width / 2, y: -cy)
            c.translateBy(x: l.width / 2 - word.width * l.heroScale / 2, y: baseline)
            c.scaleBy(x: l.heroScale, y: l.heroScale)
            for j in word.glyphs.indices {
                let level = M.letter(lab, index, j, 0), bloom = M.letter(lab, index, j, 1)
                guard level > 0.00001 || bloom > 0.00001 else { continue }
                var glyph = c
                glyph.translateBy(x: word.positions[j], y: 0)
                let color = T.color(T.moods[index].ramp[0])
                if bloom > 0.00001 {
                    // Skia BlurMask's parameter is sigma in canvas pt, independent of the group scale.
                    var blur = glyph; blur.opacity *= bloom
                    blur.addFilter(.blur(radius: M.letter(lab, index, j, 2)))
                    blur.fill(word.glyphs[j], with: .color(color))
                }
                if level > 0.00001 {
                    var tight = glyph; tight.opacity *= level; tight.addFilter(.blur(radius: 2.4))
                    tight.fill(word.glyphs[j], with: .color(color))
                    glyph.opacity *= level; glyph.fill(word.glyphs[j], with: .color(color))
                }
            }
        }
        let capsuleWidth = lab[3], capsuleH = 22 * s * fs
        var capsule = context
        capsule.opacity *= M.clamp(capsuleWidth / (18 * s))
        capsule.stroke(rounded(CGRect(x: (l.width - capsuleWidth) / 2, y: l.tagY - capsuleH / 2,
                                     width: capsuleWidth, height: capsuleH), 11 * s * fs),
                       with: .color(T.color(0xFBF9FC)), lineWidth: s * fs)
        for i in T.moods.indices {
            let alpha = M.tag(lab, i, 0)
            guard alpha > 0.00001 else { continue }
            var c = context
            c.opacity *= alpha
            c.translateBy(x: l.width / 2, y: l.tagY + M.tag(lab, i, 1) * s * fs)
            let zoom = M.tag(lab, i, 2)
            c.scaleBy(x: zoom, y: zoom)
            c.translateBy(x: -l.width / 2, y: -l.tagY)
            center(c, art.tags[i], l.tagY + 4.5 * s * fs, T.color(0xFBF9FC))
        }
    }
    private func drawControls(_ context: GraphicsContext, thumbX: Double, thumbY: Double, radius: Double, hue: [Double]) {
        let l = layout, s = l.scale, fs = l.fontScale
        let ink = T.color(T.blend(T.rgb(0xFBF9FC), hue, 0.1))
        for i in art.question.indices { center(context, art.question[i], l.questionY + Double(i) * 34 * s * fs, ink) }
        let track = rounded(CGRect(x: l.sliderX, y: l.sliderY, width: l.sliderWidth, height: l.sliderHeight), 30 * s)
        context.fill(track, with: .color(T.color(0x211525)))
        var c = context
        c.clip(to: track)
        let trail = Path(CGRect(x: l.sliderX, y: l.sliderY, width: max(0, thumbX - l.sliderX), height: l.sliderHeight))
        let gradient = Gradient(stops: [.init(color: T.color(0x211525), location: 0), .init(color: T.color(hue), location: 0.75), .init(color: T.color(0x211525), location: 1)])
        var trailContext = c; trailContext.opacity = 0.5
        trailContext.fill(trail, with: .linearGradient(gradient, startPoint: CGPoint(x: l.sliderX, y: l.sliderY), endPoint: CGPoint(x: thumbX, y: l.sliderY)))
        let pool = CGPoint(x: thumbX, y: thumbY)
        c.fill(circle(thumbX, thumbY, 46 * s), with: .radialGradient(Gradient(colors: [T.color(hue, 0.22), T.color(hue, 0)]), center: pool, startRadius: 0, endRadius: 46 * s))
        for k in 0..<7 {
            let x = l.firstX + Double(k) / 6 * (l.lastX - l.firstX)
            let clear = M.smooth((abs(x - thumbX) - radius) / (4 * s))
            let passed = 0.3 * M.smooth((frame.look.mood - Double(k)) / 0.6) * clear
            c.fill(circle(x, thumbY, 4 * s), with: .color(T.color(0x130B19, clear)))
            c.fill(circle(x, thumbY, 4 * s), with: .color(T.color(T.moods[k].ramp[0], passed)))
        }
    }
    private func drawEndpointsAndButton(_ context: GraphicsContext) {
        let l = layout, s = l.scale, fs = l.fontScale, mood = frame.look.mood
        let secondary = T.rgb(0xB9ADBD), ink = T.rgb(0xFBF9FC)
        for side in 0..<2 {
            let k = M.clamp(1 - (side == 0 ? mood : 6 - mood) / 1.2)
            for i in art.endpoints[side].indices {
                let line = art.endpoints[side][i]
                let x = l.endpointStacked ? (l.width - line.width) / 2 : side == 0 ? 49 * s : l.width - 43 * s - line.width
                let row = i + (side == 1 && l.endpointStacked ? 1 : 0)
                R.draw(context, line, x: x, y: l.endpointY + Double(row) * 17 * s * fs, color: T.color(T.blend(secondary, ink, k)))
            }
        }
        var c = context
        let cy = l.buttonY + l.buttonHeight / 2, scale = 1 - 0.03 * frame.buttonPress
        c.translateBy(x: l.width / 2, y: cy); c.scaleBy(x: scale, y: scale); c.translateBy(x: -l.width / 2, y: -cy)
        let button = rounded(CGRect(x: 32 * s, y: l.buttonY, width: 342 * s, height: l.buttonHeight), 30 * s)
        c.fill(button, with: .color(T.color(0xFCFBFC)))
        var rim = c; rim.clip(to: button)
        let tint = T.ramp(mood, 1)
        let gradient = Gradient(stops: [.init(color: T.color(tint, 0.55), location: 0), .init(color: T.color(tint, 0), location: 0.4), .init(color: T.color(tint, 0), location: 1)])
        rim.stroke(button, with: .linearGradient(gradient, startPoint: CGPoint(x: 0, y: l.buttonY), endPoint: CGPoint(x: 0, y: l.buttonY + l.buttonHeight)), lineWidth: 3 * s)
        center(c, art.button, cy + 6 * s * fs, T.color(0x160D19))
    }
}
