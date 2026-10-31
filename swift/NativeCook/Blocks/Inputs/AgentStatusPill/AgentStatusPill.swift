import SwiftUI
import UIKit

// Atlas's chat, faithfully ported from AgentStatusPill.tsx and agentStatusShader.ts.
// Loop: four 2.2 s steps; each look and label cross-fades over its final 0.4 s.
// Ticks appear 0.2 s before each boundary and clear over 8.4–8.8 s.
// As in the RN reference, tapping advances to the current step's fade and keeps playing.
// Reduced motion and autoPlay=false hold the reference's initial clock at 0.6 s.
struct AgentStatusPill: View {
    static let loop: Double = 8.8
    var autoPlay: Bool = false

    private static let step = 2.2
    private static let fade = 0.4
    private static let pillWidth: CGFloat = 300
    private static let pillHeight: CGFloat = 84
    private static let labels = ["Thinking…", "Searching…", "Planning…", "Writing…"]
    private static let rows = [
        "Reading your request", "Searching flights and stays",
        "Planning a three-day route", "Writing your itinerary"
    ]

    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scene
    @State private var origin = Date()
    @State private var pausedAt: Date?
    @State private var haptic = UIImpactFeedbackGenerator(style: .light)

    var body: some View {
        GeometryReader { geometry in
            let safe = geometry.safeAreaInsets
            let size = CGSize(
                width: geometry.size.width + safe.leading + safe.trailing,
                height: geometry.size.height + safe.top + safe.bottom
            )
            let pillY = size.height * 0.4 - Self.pillHeight / 2
            ZStack(alignment: .topLeading) {
                Self.color(0x15171A)
                chrome(size: size, safe: safe)
                TimelineView(.animation(paused: reduced || !autoPlay || scene != .active)) { timeline in
                    let t = clock(at: timeline.date)
                    let modes = Self.modes(at: t)
                    ZStack(alignment: .topLeading) {
                        pill(modes: modes, time: t)
                            .position(x: size.width / 2, y: pillY + Self.pillHeight / 2)
                        steps(time: t)
                            .frame(width: size.width - 80, alignment: .leading)
                            .offset(x: 40, y: pillY + Self.pillHeight + 34)
                    }
                    .frame(width: size.width, height: size.height, alignment: .topLeading)
                }
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .offset(x: -safe.leading, y: -safe.top)
        }
        .background(Self.color(0x15171A).ignoresSafeArea())
        .preferredColorScheme(.dark)
        .onAppear {
            origin = .now
            haptic.prepare()
        }
        .onChange(of: scene) { _, phase in
            if phase == .active {
                if let pausedAt { origin = origin.addingTimeInterval(Date().timeIntervalSince(pausedAt)) }
                pausedAt = nil
            } else if pausedAt == nil {
                pausedAt = .now
            }
        }
    }

    private func chrome(size: CGSize, safe: EdgeInsets) -> some View {
        let bubbleWidth = min(290, size.width - 40)
        return ZStack(alignment: .topLeading) {
            HStack {
                Text("Atlas")
                    .font(.system(size: 20, weight: .heavy))
                    .foregroundStyle(.white)
                Spacer(minLength: 0)
                Text("Trip assistant")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(.white.opacity(0.5))
            }
            .frame(width: size.width - 40, height: 34)
            .offset(x: 20, y: safe.top + 8)

            Text("Plan me three slow days in Lisbon in May — good coffee, no rush.")
                .font(.system(size: 16))
                .foregroundStyle(.white)
                .lineSpacing(22 - UIFont.systemFont(ofSize: 16).lineHeight)
                .fixedSize(horizontal: false, vertical: true)
                .frame(width: bubbleWidth - 32, alignment: .leading)
                // RN reserves the full 22 pt line box, including the two outside half-leadings.
                .padding(.vertical, (22 - UIFont.systemFont(ofSize: 16).lineHeight) / 2)
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
                .background(
                    UnevenRoundedRectangle(
                        topLeadingRadius: 22, bottomLeadingRadius: 22,
                        bottomTrailingRadius: 6, topTrailingRadius: 22
                    ).fill(Self.color(0x2A2D33))
                )
                .offset(x: size.width - 20 - bubbleWidth, y: safe.top + 64)

            HStack(spacing: 0) {
                Text("Ask a follow-up…")
                    .font(.system(size: 16))
                    .foregroundStyle(.white.opacity(0.45))
                Spacer(minLength: 0)
                Text("↑")
                    .font(.system(size: 20, weight: .heavy))
                    .foregroundStyle(Self.color(0x15171A))
                    .frame(width: 42, height: 42)
                    .background(.white, in: Circle())
            }
            .padding(.leading, 20)
            .padding(.trailing, 6)
            .frame(width: size.width - 32, height: 54)
            .background(Self.color(0x22252A), in: Capsule())
            .offset(x: 16, y: size.height - safe.bottom - 12 - 54)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
    }

    private func pill(modes: [Double], time: Double) -> some View {
        ZStack {
            Rectangle()
                .fill(.white)
                .colorEffect(ShaderLibrary.agentStatusPillMaterial(
                    .float2(Float(Self.pillWidth), Float(Self.pillHeight)),
                    .float4(Float(modes[0]), Float(modes[1]), Float(modes[2]), Float(modes[3])),
                    .float(Float(time / Self.loop * 2 * .pi))
                ))
                .clipShape(Capsule())
            RoundedRectangle(cornerRadius: 41)
                .stroke(
                    LinearGradient(
                        colors: [.white.opacity(0.85), .white.opacity(0.15), .white.opacity(0.6)],
                        startPoint: UnitPoint(x: 0.5, y: -1 / 82.0),
                        endPoint: UnitPoint(x: 0.5, y: 83 / 82.0)
                    ),
                    lineWidth: 2
                )
                .frame(width: Self.pillWidth - 2, height: Self.pillHeight - 2)
            ForEach(0..<4, id: \.self) { index in
                Text(Self.labels[index])
                    .font(.system(size: 22, weight: .semibold))
                    .foregroundStyle(.white)
                    .shadow(color: .black.opacity(0.35), radius: 6)
                    .opacity(modes[index])
                    .offset(y: 6 * (1 - modes[index]))
            }
        }
        .frame(width: Self.pillWidth, height: Self.pillHeight)
        .contentShape(Rectangle())
        .onTapGesture { skipAhead() }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Agent status. Tap to skip ahead.")
        .accessibilityAddTraits(.isButton)
    }

    private func steps(time: Double) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            ForEach(0..<4, id: \.self) { index in
                let active = time >= Double(index) * Self.step && time < Double(index + 1) * Self.step
                let reached = time >= Double(index) * Self.step
                let done = time > Double(index + 1) * Self.step - Self.fade / 2
                let clear = Self.smooth((time - (Self.loop - Self.fade)) / Self.fade)
                HStack(spacing: 12) {
                    ZStack {
                        Canvas { context, _ in
                            let circle = Path(ellipseIn: CGRect(x: 3, y: 3, width: 16, height: 16))
                            context.stroke(circle, with: .color(.white.opacity(0.2)), lineWidth: 2)
                            var top = Path()
                            top.addArc(center: CGPoint(x: 11, y: 11), radius: 8,
                                       startAngle: .degrees(-135), endAngle: .degrees(-45), clockwise: false)
                            context.stroke(top, with: .color(.white), lineWidth: 2)
                        }
                        .rotationEffect(.degrees(time / Self.loop * 360 * 8))
                        .opacity(active ? 1 : 0.4)
                        Text("✓")
                            .font(.system(size: 13, weight: .black))
                            .foregroundStyle(Self.color(0x15171A))
                            .frame(width: 22, height: 22)
                            .background(Self.color(0x3CCB7F), in: Circle())
                            .opacity(done ? 1 - clear : 0)
                    }
                    .frame(width: 22, height: 22)
                    .accessibilityHidden(true)
                    Text(Self.rows[index])
                        .font(.system(size: 16))
                        .foregroundStyle(.white)
                        .opacity(reached ? 1 : 0.4)
                }
                .frame(height: 22)
            }
        }
    }

    private func clock(at date: Date) -> Double {
        guard autoPlay, !reduced else { return 0.6 }
        let elapsed = max(0, (pausedAt ?? date).timeIntervalSince(origin))
        return (elapsed.truncatingRemainder(dividingBy: Self.loop) + Self.loop)
            .truncatingRemainder(dividingBy: Self.loop)
    }

    private func skipAhead() {
        guard autoPlay, !reduced else { return }
        let time = clock(at: .now)
        let next = (floor(time / Self.step) + 1) * Self.step - Self.fade
        if next > time { origin = origin.addingTimeInterval(-(next - time)) }
        haptic.impactOccurred()
        haptic.prepare()
    }

    private static func modes(at time: Double) -> [Double] {
        let stepIndex = Int(floor(time / step))
        let local = time - Double(stepIndex) * step
        let fadeWeight = smooth((local - (step - fade)) / fade)
        var weights = [Double](repeating: 0, count: 4)
        weights[stepIndex % 4] += 1 - fadeWeight
        weights[(stepIndex + 1) % 4] += fadeWeight
        return weights
    }

    private static func smooth(_ value: Double) -> Double {
        let x = min(1, max(0, value))
        return x * x * (3 - 2 * x)
    }

    private static func color(_ hex: UInt32) -> Color {
        Color(.sRGB, red: Double((hex >> 16) & 255) / 255,
              green: Double((hex >> 8) & 255) / 255, blue: Double(hex & 255) / 255, opacity: 1)
    }
}

#Preview {
    AgentStatusPill(autoPlay: true)
}
