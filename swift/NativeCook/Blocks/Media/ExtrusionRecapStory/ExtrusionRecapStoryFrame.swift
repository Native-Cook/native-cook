import SwiftUI

struct ExtrusionRecapStoryFrame: View {
    let scene: ExtrusionRecapStoryMotion.ExtrusionRecapStoryScene
    let layout: ExtrusionRecapStoryResources.ExtrusionRecapStoryLayout
    let displayScale: Double
    typealias M = ExtrusionRecapStoryMotion
    typealias R = ExtrusionRecapStoryResources

    var body: some View {
        ZStack(alignment: .topLeading) {
            shaderPass(bloom: false)
            if !scene.reduced {
                // RN's second Skia canvas: quarter-size, sigma 2 there, scaled 4x and composited at .16.
                shaderPass(bloom: true)
                    .blur(radius: 2)
                    .drawingGroup(opaque: false, colorMode: .nonLinear)
                    .scaleEffect(4, anchor: .topLeading)
                    .opacity(0.16)
            }
        }
        .frame(width: layout.width, height: layout.height, alignment: .topLeading)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    private func shaderPass(bloom: Bool) -> some View {
        let factor = bloom ? 4.0 : 1
        return Rectangle().fill(.white)
            .colorEffect(ShaderLibrary.extrusionRecapStory(.image(R.atlas), .floatArray(uniforms(bloom: bloom))))
            .frame(width: layout.width / factor, height: layout.height / factor, alignment: .topLeading)
    }

    private func uniforms(bloom: Bool) -> [Float] {
        let p = M.wrap(scene.progress), i = Int(floor(p)), n = (i + 1) % 3
        let a = R.pages[i], b = R.pages[n], after = R.pages[(n + 1) % 3], factor = bloom ? 4.0 : 1
        var u: [Double] = [layout.width / factor, layout.height / factor, 198, layout.heroY / layout.scale]
        u += a.direction; u += b.direction
        u += [p - Double(i), scene.reduced ? 1 : 0, Double(i), Double(n), 3]
        u += R.rgb(a.fieldA); u += R.rgb(a.fieldB)
        u += R.rgb(b.fieldA); u += R.rgb(b.fieldB); u += R.rgb(after.fieldB)
        u += R.rgb(a.face); u += R.rgb(b.face)
        u += [scene.steerX, scene.steerY, scene.reduced ? 0 : scene.elapsed.truncatingRemainder(dividingBy: 8) / 8,
              scene.age, scene.speed > 2 ? 40 : 64, 1, 1, 402 / layout.width / displayScale,
              layout.cardBottom / layout.scale, 28, 48, scene.stretch, bloom ? 1 : 0]
        return u.map(Float.init)
    }
}

struct ExtrusionRecapStoryPageCopy: View {
    let scene: ExtrusionRecapStoryMotion.ExtrusionRecapStoryScene
    let layout: ExtrusionRecapStoryResources.ExtrusionRecapStoryLayout
    let index: Int
    let fontScale: Double
    typealias M = ExtrusionRecapStoryMotion
    typealias R = ExtrusionRecapStoryResources

    private var alpha: Double {
        if !scene.reduced { return M.pageAlpha(scene.progress, index) }
        let p = M.wrap(scene.progress), i = Int(floor(p)), q = p - Double(i)
        return index == i ? 1 - q : index == (i + 1) % 3 ? q : 0
    }
    private var ink: UInt32 {
        let pass = scene.reduced ? 0 : M.highlightPass(scene.age), base = R.rgb(0xF4F83C), light = R.rgb(0xFFF3A0)
        let c = zip(base, light).map { pair in UInt32(floor((pair.0 + (pair.1 - pair.0) * pass) * 255 + 0.5)) }
        return (c[0] << 16) | (c[1] << 8) | c[2]
    }

    var body: some View {
        let p = R.pages[index], s = layout.scale, large = fontScale >= 1.4
        let opacity = scene.reduced ? alpha : M.clamp((alpha - 0.2) / 0.8)
        let y = layout.titleY + (p.stat != nil && !large ? 170 * s : 0)
        Group {
            if large {
                ScrollView {
                    content
                }
                .frame(maxHeight: 340 * s, alignment: .top)
                .scrollIndicators(.hidden)
            } else { content }
        }
        .frame(width: layout.width - 40 * s, alignment: .top)
        .compositingGroup()
        .opacity(opacity * opacity * (3 - 2 * opacity))
        .offset(x: 20 * s, y: y + (scene.reduced ? 0 : M.copyShift(alpha, scene.age, p.stat != nil) * s))
        .allowsHitTesting(large && scene.activePage == index)
        .accessibilityHidden(true)
    }

    private var content: some View {
        let p = R.pages[index], s = layout.scale, f = s * fontScale
        return VStack(spacing: 0) {
            if let value = p.stat {
                HStack(spacing: 0) {
                    ForEach(0..<String(value).count, id: \.self) { order in
                        ExtrusionRecapStoryRollingDigit(digit: String(Array(String(value))[order]), order: order, page: index,
                                                       scene: scene, size: 78 * f)
                    }
                }
                .frame(maxWidth: .infinity)
            }
            ExtrusionRecapStoryText(text: p.headline, size: (p.stat == nil ? 54 : 32) * f,
                                   lineHeight: (p.stat == nil ? 51 : 34) * f, bold: true, tracking: -2.1 * s, hex: ink)
                .fixedSize(horizontal: false, vertical: true)
            ExtrusionRecapStoryText(text: p.caption, size: 15 * f, lineHeight: 19 * f, hex: 0xF7F1B2)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.top, 22 * s)
        }
    }
}

struct ExtrusionRecapStoryRollingDigit: View {
    @Environment(\.displayScale) private var displayScale
    let digit: String
    let order: Int
    let page: Int
    let scene: ExtrusionRecapStoryMotion.ExtrusionRecapStoryScene
    let size: Double
    var body: some View {
        let row = size * 1.06
        let font = ExtrusionRecapStoryResources.font(true, size, tabular: true)
        let width = ceil((digit as NSString).size(withAttributes: [.font: font]).width * displayScale) / displayScale
        let arrival = scene.reduced ? 1 : ExtrusionRecapStoryMotion.digitArrival(scene.progress, page, order)
        VStack(spacing: 0) {
            digitText(String(((Int(digit) ?? 0) + 9) % 10), row)
            digitText(digit, row)
        }
        .offset(y: -row * arrival)
        .frame(width: width, height: row, alignment: .top)
        .clipped()
    }
    private func digitText(_ value: String, _ row: Double) -> some View {
        ExtrusionRecapStoryText(text: value, size: size, lineHeight: row, bold: true, tabular: true)
            .frame(height: row)
    }
}

struct ExtrusionRecapStoryPeekLabel: View {
    let scene: ExtrusionRecapStoryMotion.ExtrusionRecapStoryScene
    let layout: ExtrusionRecapStoryResources.ExtrusionRecapStoryLayout
    let index: Int
    var body: some View {
        let s = layout.scale
        HStack(spacing: 0) {
            ExtrusionRecapStoryText(text: index == 2 ? "Replay" : "Up next", size: 13 * s, hex: 0x14070F, singleLine: true)
                .fixedSize()
                .opacity(0.62)
                .padding(.trailing, 8 * s)
            ExtrusionRecapStoryText(text: ExtrusionRecapStoryResources.pages[(index + 1) % 3].peekTitle,
                                   size: 15 * s, bold: true, tracking: -0.2 * s, hex: 0x14070F,
                                   alignment: .left, singleLine: true)
            Path { p in
                p.move(to: CGPoint(x: 6 * s, y: 3.5 * s))
                p.addLine(to: CGPoint(x: 10.5 * s, y: 8 * s))
                p.addLine(to: CGPoint(x: 6 * s, y: 12.5 * s))
            }
            .stroke(ExtrusionRecapStoryResources.color(0x14070F), style: StrokeStyle(lineWidth: 1.8 * s, lineCap: .round, lineJoin: .round))
            .frame(width: 16 * s, height: 16 * s)
        }
        .padding(.horizontal, 20 * s)
        .frame(width: layout.width, height: layout.peekRow)
        .opacity(ExtrusionRecapStoryMotion.pageAlpha(scene.progress, index))
        .offset(y: scene.reduced ? 0 : ExtrusionRecapStoryMotion.peekShift(scene.progress, index, scene.age) * s)
    }
}
