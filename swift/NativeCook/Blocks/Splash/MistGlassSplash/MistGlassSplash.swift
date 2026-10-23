import SwiftUI
import UIKit

/// A traced glass mark floating through a field of lavender mist.
struct MistGlassSplash: View {
    static let loop = 8.0
    var autoPlay = false
    var previewTime: Double? = nil
    @Environment(\.accessibilityReduceMotion) private var reducedMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var epoch = Date()
    @State private var heldTime = 0.0
    @State private var releasedAt: Date?
    @State private var touching = false

    private func time(_ now: Date) -> Double {
        if let previewTime { return previewTime.truncatingRemainder(dividingBy: Self.loop) }
        if reducedMotion { return 4.8 }
        if touching { return heldTime }
        if let releasedAt {
            let elapsed = now.timeIntervalSince(releasedAt)
            if elapsed < 2 { return heldTime }
            if elapsed < 2.6 { return heldTime * (1 - Self.ease((elapsed - 2) / 0.6)) }
            return autoPlay ? (elapsed - 2.6).truncatingRemainder(dividingBy: Self.loop) : 0
        }
        return autoPlay ? max(0, now.timeIntervalSince(epoch)).truncatingRemainder(dividingBy: Self.loop) : 0
    }

    var body: some View {
        GeometryReader { geometry in
            TimelineView(.animation(paused: reducedMotion || previewTime != nil || scenePhase != .active || (!autoPlay && releasedAt == nil && !touching))) { clock in
                Canvas { context, size in
                    context.fill(Path(CGRect(origin: .zero, size: size)), with: .color(Self.color(0x454FDC)))
                    let scale = min(size.width / 393, size.height / 852)
                    context.translateBy(x: (size.width - 393 * scale) / 2, y: (size.height - 852 * scale) / 2)
                    context.scaleBy(x: scale, y: scale)
                    Self.draw(context, time: time(clock.date))
                }
            }
            .contentShape(Rectangle())
            .gesture(DragGesture(minimumDistance: 0)
                .onChanged { value in
                    guard !reducedMotion else { return }
                    if !touching {
                        heldTime = time(Date())
                        touching = true
                        UIImpactFeedbackGenerator(style: .light).impactOccurred()
                    }
                    heldTime = min(7.4, max(0, value.location.x / geometry.size.width * 7.4))
                }
                .onEnded { value in
                    guard !reducedMotion else { return }
                    if abs(value.translation.width) < 8 { heldTime = 4.8 }
                    touching = false
                    releasedAt = Date()
                })
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("Velune. A calmer kind of everyday. Drag to draw the glass mark.")
            .accessibilityAction(named: "Replay splash") { epoch = Date(); releasedAt = nil }
        }
        .ignoresSafeArea()
        .preferredColorScheme(.dark)
    }

    private static func ease(_ value: Double) -> Double {
        let u = min(1, max(0, value)); return u * u * (3 - 2 * u)
    }
    private static func color(_ hex: UInt32) -> Color {
        Color(red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255, blue: Double(hex & 255) / 255)
    }
    private static func label(_ text: String, _ x: Double, _ y: Double, _ size: Double, _ ink: Color, _ weight: Font.Weight = .regular, context: GraphicsContext) {
        context.draw(Text(text).font(.system(size: size, weight: weight)).foregroundColor(ink), at: CGPoint(x: x, y: y))
    }

    private static func draw(_ context: GraphicsContext, time t: Double) {
        let reset = 1 - ease((t - 5.8) / 1.6)
        let trace = ease((t - 0.6) / 2.0) * reset
        let lift = ease((t - 2.6) / 1.0) * reset
        let reveal = ease((t - 3.4) / 0.6) * (1 - ease((t - 5.8) / 0.5))
        let phase = ease((t - 0.6) / 6.8) * .pi * 2
        for i in 0..<4 {
            let index = Double(i)
            let x = 196.5 + 145 * sin(phase + index * 1.8)
            let y = 130 + index * 190 + 90 * cos(phase + index)
            let rect = CGRect(x: x - 260, y: y - 260, width: 520, height: 520)
            let tint = color(i % 2 == 0 ? 0xE6AAEF : 0x707AFF)
            context.fill(Path(ellipseIn: rect), with: .radialGradient(Gradient(colors: [tint.opacity(0.85), tint.opacity(0)]), center: CGPoint(x: x, y: y), startRadius: 15, endRadius: 260))
        }
        let side = 136 - 24 * lift, cy = 426 - 108 * lift
        let tile = Path(roundedRect: CGRect(x: 196.5 - side / 2, y: cy - side / 2, width: side, height: side), cornerRadius: 32)
        context.fill(Path(ellipseIn: CGRect(x: 106.5, y: cy - 60, width: 180, height: 180)), with: .radialGradient(Gradient(colors: [color(0x282392).opacity(0.3), .clear]), center: CGPoint(x: 196.5, y: cy + 30), startRadius: 5, endRadius: 90))
        context.fill(tile, with: .linearGradient(Gradient(colors: [.white.opacity(0.17), .white.opacity(0.04)]), startPoint: CGPoint(x: 140, y: cy - 60), endPoint: CGPoint(x: 250, y: cy + 60)))
        context.stroke(tile, with: .linearGradient(Gradient(colors: [.white.opacity(0.45), .white.opacity(0.08)]), startPoint: CGPoint(x: 140, y: cy - 60), endPoint: CGPoint(x: 250, y: cy + 60)), lineWidth: 0.8)
        var ribbon = Path()
        let count = Int(180 * trace)
        for i in 0...max(0, count) {
            let a = Double(i) / 180 * .pi * 2 - .pi / 2
            let point = CGPoint(x: 196.5 + 35 * sin(a) * (1 - 0.15 * lift), y: cy + 20 * sin(2 * a) * (1 - 0.15 * lift))
            if i == 0 { ribbon.move(to: point) } else { ribbon.addLine(to: point) }
        }
        context.stroke(ribbon, with: .color(.white), style: StrokeStyle(lineWidth: 3.5, lineCap: .round, lineJoin: .round))
        let tipAngle = Double(count) / 180 * .pi * 2 - .pi / 2
        let tipX = 196.5 + 35 * sin(tipAngle) * (1 - 0.15 * lift)
        let tipY = cy + 20 * sin(2 * tipAngle) * (1 - 0.15 * lift)
        let tipOpacity = sin(.pi * trace)
        context.fill(Path(ellipseIn: CGRect(x: tipX - 7, y: tipY - 7, width: 14, height: 14)), with: .radialGradient(Gradient(colors: [.white.opacity(tipOpacity * 0.8), .clear]), center: CGPoint(x: tipX, y: tipY), startRadius: 0, endRadius: 7))
        context.fill(Path(ellipseIn: CGRect(x: tipX - 2.5, y: tipY - 2.5, width: 5, height: 5)), with: .color(.white.opacity(tipOpacity)))
        var brand = context; brand.opacity = ease((t - 1.8) / 0.6) * reset
        label("velune", 196.5, cy + side / 2 + 35, 24, .white, .semibold, context: brand)
        var copy = context; copy.opacity = reveal
        label("A calmer kind", 196.5, 568 + 16 * (1 - reveal), 30, .white, .bold, context: copy)
        label("of everyday.", 196.5, 605 + 16 * (1 - reveal), 30, .white, .bold, context: copy)
        label("A moment for you, every day.", 196.5, 649, 14, .white.opacity(0.85), context: copy)
        copy.fill(Path(roundedRect: CGRect(x: 28, y: 700, width: 337, height: 54), cornerRadius: 27), with: .color(.white))
        label("Take a breath", 196.5, 727, 15, color(0x454FDC), .semibold, context: copy)
        label("Make room for a little calm", 196.5, 783, 11, .white.opacity(0.8), context: copy)
    }
}
