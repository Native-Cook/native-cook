import SwiftUI

struct GiftCardPickerOrb: View {
    let width: CGFloat
    let height: CGFloat
    let scale: CGFloat
    let ky: CGFloat
    let stage: Double
    let reduceMotion: Bool
    let time: TimeInterval

    private func clamp(_ value: Double) -> Double { min(1, max(0, value)) }
    private func easeOut(_ value: Double) -> Double { 1 - pow(1 - value, 3) }
    private func y(_ pin: CGFloat) -> CGFloat { (pin - 62) * ky }

    private var cloudA: (scale: Double, opacity: Double, text: Double) {
        if reduceMotion {
            let o = 1 - clamp((stage - 0.35) / 0.3)
            return (1, o, o)
        }
        let s = clamp(stage)
        let k = clamp((s - 0.243) / (0.471 - 0.243))
        let scale = s <= 0.243 ? 1 - 0.07 * easeOut(s / 0.243) : 0.93 - 0.73 * k * k
        return (scale, stage >= 1 ? 0 : 1 - k, 1 - clamp((s - 0.243) / ((0.471 - 0.243) * 0.8)))
    }

    private var sphere: (scale: Double, opacity: Double) {
        if reduceMotion { return (1, stage <= 1 ? clamp((stage - 0.35) / 0.3) : 1 - clamp((stage - 1.35) / 0.3)) }
        if stage <= 1 { return (0.35 + 0.65 * easeOut(clamp((clamp(stage) - 0.3) / 0.7)), clamp((clamp(stage) - 0.3) / 0.12)) }
        let e = clamp(stage - 1)
        let scale: Double
        if e <= 0.217 { scale = 1 - 0.07 * easeOut(e / 0.217) }
        else if e <= 0.383 { scale = 0.93 - 0.33 * pow((e - 0.217) / (0.383 - 0.217), 2) }
        else { scale = 0.6 - 0.1 * clamp((e - 0.383) / 0.2) }
        return (scale, 1 - clamp((e - 0.333) / (0.55 - 0.333)))
    }

    private var cloudB: (scale: Double, opacity: Double) {
        if reduceMotion { return (1, clamp((stage - 1.35) / 0.3)) }
        let e = clamp(stage - 1)
        return (0.3 + 0.7 * easeOut(clamp((e - 0.333) / (1 - 0.333))), stage <= 1 ? 0 : clamp((e - 0.333) / (0.55 - 0.333)))
    }

    var body: some View {
        let ca = cloudA
        let cb = cloudB
        let sp = sphere
        let anchor = UnitPoint(x: 0.5, y: y(425) / height)
        ZStack(alignment: .topLeading) {
            GiftCardPickerTheme.lightSheet
            GiftCardPickerFieldImage(name: "cloudA", samples: GiftCardPickerFields.cloudA, width: width, height: height, blur: 16)
                .scaleEffect(ca.scale, anchor: anchor).opacity(ca.opacity)
            GiftCardPickerFieldImage(name: "cloudB", samples: GiftCardPickerFields.cloudB, width: width, height: height, blur: 16)
                .scaleEffect(cb.scale, anchor: anchor).opacity(cb.opacity)
            GiftCardPickerSphere(scale: scale, time: time, still: reduceMotion)
                .frame(width: 303 * scale, height: 303 * scale)
                .scaleEffect(sp.scale)
                .opacity(sp.opacity)
                .position(x: 201 * scale, y: y(452))
            ZStack(alignment: .topLeading) {
                line("Discover your", size: 32, weight: .bold, centerY: 405, color: 0x4E4A50)
                line("gift amount", size: 32, weight: .bold, centerY: 446, color: 0x4E4A50)
                line("Then you'll choose a company", size: 17, weight: .regular, centerY: 488, color: 0x4A4648)
            }
            .frame(width: width, height: height, alignment: .topLeading)
            .scaleEffect(ca.scale, anchor: anchor).compositingGroup().opacity(ca.text).blendMode(.colorDodge)
            ZStack(alignment: .topLeading) {
                line("USD\u{2009}3", size: 46, weight: .bold, centerY: 431, color: 0x4E4A50)
                line("Now it’s time to choose", size: 17, weight: .regular, centerY: 480, color: 0x4A4648)
                line("your gift stock", size: 17, weight: .regular, centerY: 502, color: 0x4A4648)
            }
            .frame(width: width, height: height, alignment: .topLeading)
            .scaleEffect(cb.scale, anchor: anchor).compositingGroup().opacity(cb.opacity).blendMode(.colorDodge)
        }
        .frame(width: width, height: height, alignment: .topLeading)
        .compositingGroup()
        .allowsHitTesting(false)
    }

    private func line(_ text: String, size: CGFloat, weight: Font.Weight, centerY: CGFloat, color: UInt32) -> some View {
        Text(text).font(.system(size: size, weight: weight)).foregroundStyle(Color(hex: color))
            .fixedSize().position(x: width / 2, y: y(centerY))
    }
}

struct GiftCardPickerSphere: View {
    let scale: CGFloat
    let time: TimeInterval
    let still: Bool
    private static let blobs: [(x: Double, y: Double, r: Double, color: UInt32, alpha: Double, phase: Double)] = [
        (7.5, -47.4, 100, 0xC2D63A, 1, 0),
        (40.2, 22.3, 62, 0x4F86E6, 0.85, 1.7),
        (-39.8, 23, 86, 0xF08A2C, 0.95, 3.2),
        (-21.3, 5.7, 46, 0xEA4A2A, 0.6, 4.4),
        (4, 6.9, 40, 0xD0679F, 0.7, 5.6)
    ]
    var body: some View {
        Canvas { context, size in
            let radius = 151.5 * scale
            let center = CGPoint(x: size.width / 2, y: size.height / 2)
            let disc = Path(ellipseIn: CGRect(origin: .zero, size: size))
            let base = Gradient(stops: [
                .init(color: Color(hex: 0xE9E9EE), location: 0), .init(color: Color(hex: 0xEAEAEE), location: 0.7),
                .init(color: Color(hex: 0xECECF0), location: 0.93), .init(color: Color(hex: 0xEEEEF1), location: 1)
            ])
            context.fill(disc, with: .radialGradient(base, center: center, startRadius: 0, endRadius: radius))
            context.clip(to: disc)
            var spinning = context
            spinning.translateBy(x: center.x, y: center.y)
            spinning.rotate(by: .radians(still ? 0 : -((time - 4.6) / 3) * .pi * 2))
            for blob in Self.blobs {
                let phase = time / 9.75 * .pi * 2 + blob.phase
                let cx = blob.x * scale + (still ? 0 : sin(phase) * 4)
                let cy = blob.y * scale + (still ? 0 : cos(phase) * 4)
                let r = blob.r * scale
                let color = Color(hex: blob.color)
                let gradient = Gradient(stops: [
                    .init(color: color.opacity(blob.alpha), location: 0),
                    .init(color: color.opacity(blob.alpha * 0.8), location: 0.4),
                    .init(color: color.opacity(blob.alpha * 0.42), location: 0.72),
                    .init(color: color.opacity(0), location: 1)
                ])
                spinning.fill(Path(ellipseIn: CGRect(x: cx - r, y: cy - r, width: r * 2, height: r * 2)), with: .radialGradient(gradient, center: CGPoint(x: cx, y: cy), startRadius: 0, endRadius: r))
            }
            let rim = Gradient(stops: [
                .init(color: Color(hex: 0xE9E9EE, opacity: 0), location: 0),
                .init(color: Color(hex: 0xE9E9EE, opacity: 0), location: 0.55),
                .init(color: Color(hex: 0xEAEAEF, opacity: 0.85), location: 0.86),
                .init(color: Color(hex: 0xEEEEF1), location: 1)
            ])
            context.fill(disc, with: .radialGradient(rim, center: center, startRadius: 0, endRadius: radius))
        }
    }
}
