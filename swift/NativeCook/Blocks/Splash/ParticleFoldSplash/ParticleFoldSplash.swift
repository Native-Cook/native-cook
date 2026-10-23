import SwiftUI
import UIKit

/// A deterministic particle plane folds into a rotating perspective cube.
struct ParticleFoldSplash: View {
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
                    context.fill(Path(CGRect(origin: .zero, size: size)), with: .color(Self.color(0x060708)))
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
            .accessibilityLabel("Forme. Small pieces. A bigger picture. Drag to fold the particles.")
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
        let fold = ease((t - 0.6) / 2) * (1 - ease((t - 5) / 2.4))
        let turn = ease((t - 2.6) / 4.8) * .pi * 2
        let yaw = -0.55 * fold + turn, pitch = -0.35 * fold
        for face in 0..<6 {
            for row in 0..<9 {
                for col in 0..<9 {
                    let u = Double(col) / 4 - 1, v = Double(row) / 4 - 1
                    var x = u, y = v, z = -1.0
                    switch face {
                    case 1: z = 1
                    case 2: x = -1; z = u
                    case 3: x = 1; z = u
                    case 4: y = -1; z = v
                    case 5: y = 1; z = v
                    default: break
                    }
                    let index = face * 81 + row * 9 + col
                    let flatX = (Double(index % 27) - 13) * 8
                    let flatY = (Double(index / 27) - 8.5) * 8
                    x = flatX * (1 - fold) + x * 68 * fold
                    y = flatY * (1 - fold) + y * 68 * fold
                    z *= 68 * fold
                    let rx = x * cos(yaw) + z * sin(yaw), rz = -x * sin(yaw) + z * cos(yaw)
                    let ry = y * cos(pitch) - rz * sin(pitch)
                    let depth = y * sin(pitch) + rz * cos(pitch)
                    let perspective = 400 / (400 + depth)
                    let px = 196.5 + rx * perspective, py = 350 + ry * perspective
                    let near = min(1, max(0, 0.5 - depth / 240))
                    let edge = (row == 0 || row == 8 || col == 0 || col == 8) ? fold : 0
                    let radius = 0.9 + near * 0.7 + 0.35 * edge
                    context.fill(Path(ellipseIn:CGRect(x:px-radius,y:py-radius,width:radius*2,height:radius*2)),with:.color(.white.opacity(min(1, 0.18 + 0.72 * near + 0.18 * edge))))
                }
            }
        }
        label("forme",196.5,175,34,.white,.bold,context:context)
        label("EVERY DETAIL FINDS ITS PLACE",196.5,488,11,color(0x85888F),context:context)
        label("Small pieces.",196.5,568,30,.white,.bold,context:context)
        label("A bigger picture.",196.5,605,30,.white,.bold,context:context)
        label("Give your next idea a little dimension.",196.5,649,14,color(0xA5A5AE),context:context)
        context.fill(Path(roundedRect:CGRect(x:28,y:700,width:337,height:54),cornerRadius:27),with:.color(.white))
        label("Bring it together",196.5,727,15,color(0x060708),.semibold,context:context)
        label("Made of possibilities",196.5,783,11,color(0x85888F),context:context)
    }
}
