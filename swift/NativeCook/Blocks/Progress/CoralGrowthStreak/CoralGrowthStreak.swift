// Coral Growth Streak — a habit's week, kept as a reef that grows a ring for every day done.
//
// Tidepool's streak screen: a round window onto a sea bed where a Gray-Scott reaction-diffusion
// simulation on the GPU (CoralGrowthStreakReef, constants in CoralGrowthStreakMotion) grows brain
// coral. Each completed day fills its dot, rolls the count and opens the next ring of the reef with a
// few seeds; the seventh day sends a band of light across it.
//
// autoPlay: loop = 10 s. The coral dissolves from the rim inwards at the end and the field is cleared
// at t = 0, so frame 10 s equals frame 0. A touch on the reef opens the whole window and grows coral
// where the finger is; 2 s after the last touch the reef dissolves and the loop restarts.
// Reduced motion: the full week, a still reef, no loop.

import SwiftUI
import UIKit

struct CoralGrowthStreak: View {
    static let loop: Double = CoralGrowthStreakMotion.loop
    private typealias M = CoralGrowthStreakMotion

    private let autoPlay: Bool
    @Environment(\.accessibilityReduceMotion) private var reduced
    @State private var clock = CoralGrowthStreakClock()
    @State private var touching = false
    @GestureState private var held = false

    init(autoPlay: Bool = true) {
        self.autoPlay = autoPlay
    }

    private static let ink = Color(hex: 0xEAF6F4)
    private static let mute = Color.rgba(234, 246, 244, 0.62)
    private static let coral = Color(hex: 0xF4705F)
    private static let deep = Color(hex: 0x05222B)
    private static func pale(_ a: Double) -> Color { .rgba(214, 240, 236, a) }
    private static let digit = 56.0
    private static let dot = 36.0
    private static let letters = ["M", "T", "W", "T", "F", "S", "S"]
    private static let stats = [("This month", "18 of 24"), ("Average", "34 min"), ("Water", "17°")]

    var body: some View {
        GeometryReader { proxy in
            let top = proxy.safeAreaInsets.top, bottom = proxy.safeAreaInsets.bottom
            let w = proxy.size.width, h = proxy.size.height + top + bottom
            page(w, h, top, bottom)
                .frame(width: w, height: h, alignment: .top)
                .background(
                    LinearGradient(stops: [.init(color: Color(hex: 0x0B3540), location: 0), .init(color: Color(hex: 0x072A34), location: 0.5), .init(color: Color(hex: 0x041C24), location: 1)], startPoint: .top, endPoint: .bottom)
                )
                .clipped()
                .offset(y: -top)
        }
        .background(Self.deep.ignoresSafeArea())
        .preferredColorScheme(.dark)
        .onChange(of: held) { _, now in
            // a touch the system took away never reaches onEnded
            if !now, touching {
                touching = false
                clock.touchEnded()
            }
        }
    }

    private func page(_ w: Double, _ h: Double, _ top: Double, _ bottom: Double) -> some View {
        let compact = h < 740
        let reef = (max(200, min(320, w - 76, h * 0.36))).rounded()
        let _ = clock.reef = reef
        let _ = clock.auto = autoPlay && !reduced
        let _ = reduced ? clock.showStill() : ()
        // only what moves is inside a timeline: a gesture inside one is rebuilt every frame and never fires
        return VStack(spacing: 0) {
            header(compact)
            Spacer(minLength: 0)
            live { window(reef) }
                .frame(width: reef + 20, height: reef + 20)
                .overlay {
                    Color.clear
                        .frame(width: reef, height: reef)
                        .contentShape(Circle())
                        .gesture(reduced ? nil : touch)
                }
            Spacer(minLength: 0)
            live { streakRow }
            Spacer(minLength: 0)
            live { week }
            Spacer(minLength: 0)
            statsCard
            Spacer(minLength: 0)
            live {
                Text("Touch the reef to plant coral")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(Self.ink)
                    .opacity(clock.hint * 0.7)
            }
        }
        .padding(.horizontal, 24)
        .padding(.top, top + (compact ? 8 : 14))
        .padding(.bottom, max(bottom, 12) + (compact ? 6 : 14))
        .frame(width: w, height: h)
    }

    private func live<Content: View>(@ViewBuilder _ content: @escaping () -> Content) -> some View {
        TimelineView(.animation(paused: reduced)) { _ in content() }
    }

    private func header(_ compact: Bool) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .firstTextBaseline) {
                Text("Tidepool")
                    .font(.system(size: 15, weight: .semibold))
                    .tracking(0.2)
                    .foregroundStyle(Self.ink)
                Spacer(minLength: 0)
                Text("Week 47")
                    .font(.system(size: 13, weight: .medium).monospacedDigit())
                    .foregroundStyle(Self.mute)
            }
            Text("Morning swim")
                .font(.system(size: compact ? 26 : 32, weight: .bold))
                .tracking(-0.6)
                .foregroundStyle(Self.ink)
                .frame(height: compact ? 30 : 38)
                .padding(.top, compact ? 6 : 14)
                .accessibilityAddTraits(.isHeader)
            Text("A ring of coral for every day you show up.")
                .font(.system(size: 15))
                .foregroundStyle(Self.mute)
                .frame(height: 20)
                .padding(.top, 4)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func window(_ reef: Double) -> some View {
        let rim = reef + 20
        return ZStack {
            Circle().fill(Self.pale(0.10))
            CoralInsetShadow(shape: Circle(), color: .rgba(255, 255, 255, 0.28), blur: 0, y: 1)
            Circle().strokeBorder(Self.pale(0.18), lineWidth: 1)
            ZStack {
                Circle().fill(Color(hex: 0x0A3440))
                CoralGrowthStreakReef(clock: clock, still: reduced)
                    .frame(width: reef, height: reef)
                    .clipShape(Circle())
                    .accessibilityLabel("A round reef of coral that grows with the streak")
                // the glass over the water: shaded under the rim's upper lip, a faint light along the lower edge
                CoralInsetShadow(shape: Circle(), color: .rgba(0, 10, 14, 0.6), blur: 22, y: 10)
                CoralInsetShadow(shape: Circle(), color: Self.pale(0.14), blur: 5, y: -2)
            }
            .frame(width: reef, height: reef)
        }
        .frame(width: rim, height: rim)
        .allowsHitTesting(false)
        .background(
            ZStack {
                Circle().fill(Color.rgba(0, 8, 12, 0.55)).blur(radius: 24).offset(y: 26)
                Circle().fill(Color.rgba(0, 8, 12, 0.4)).blur(radius: 5).offset(y: 4)
            }
        )
        .scaleEffect(1 + 0.02 * sin(.pi * clock.bloom))
    }

    private var touch: some Gesture {
        DragGesture(minimumDistance: 0)
            .updating($held) { _, state, _ in state = true }
            .onChanged { value in
                if touching {
                    clock.touchMoved(x: value.location.x, y: value.location.y)
                } else {
                    touching = true
                    clock.touchBegan(x: value.startLocation.x, y: value.startLocation.y)
                    clock.touchMoved(x: value.location.x, y: value.location.y)
                    UIImpactFeedbackGenerator(style: .light).impactOccurred()
                }
            }
            .onEnded { _ in
                touching = false
                clock.touchEnded()
            }
    }

    private var streakRow: some View {
        HStack(alignment: .bottom, spacing: 0) {
            VStack(spacing: 0) {
                ForEach(0..<8, id: \.self) { n in
                    Text("\(n)")
                        .font(.system(size: 52, weight: .bold).monospacedDigit())
                        .tracking(-1)
                        .foregroundStyle(Self.ink)
                        .frame(height: Self.digit)
                        .accessibilityHidden(true)
                }
            }
            .offset(y: -Self.digit * M.countAt(clock.time) * clock.level)
            .frame(minWidth: 34, alignment: .leading)
            .frame(height: Self.digit, alignment: .top)
            .clipped()
            Text("day streak")
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(Self.ink)
                .accessibilityLabel("\(Int(M.countAt(clock.time) * clock.level + 0.5)) day streak")
                .padding(.leading, 8)
                .padding(.bottom, 10)
            Spacer(minLength: 0)
            Text("Best 21")
                .font(.system(size: 13, weight: .semibold).monospacedDigit())
                .foregroundStyle(Self.ink)
                .padding(.horizontal, 12)
                .frame(height: 28)
                .background(Capsule().fill(Self.pale(0.10)))
                .overlay(Capsule().strokeBorder(Self.pale(0.2), lineWidth: 1 / 3))
                .padding(.bottom, 9)
        }
    }

    private var week: some View {
        HStack(spacing: 0) {
            ForEach(0..<M.days, id: \.self) { i in
                if i > 0 { Spacer(minLength: 0) }
                dayDot(i)
            }
        }
    }

    private func dayDot(_ index: Int) -> some View {
        let at = M.dayTime(index)
        let u = M.seg(clock.time, at, at + M.dot)
        let k = clock.level
        let v = M.seg(clock.time, at + 0.12, at + M.dot + 0.1)
        return VStack(spacing: 8) {
            Text(Self.letters[index])
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(Self.mute)
            ZStack {
                Circle().fill(Self.pale(0.07))
                Circle().strokeBorder(Self.pale(0.16), lineWidth: 1)
                ZStack {
                    Circle().fill(Color.rgba(244, 112, 95, 0.35)).blur(radius: 5).offset(y: 4)
                    Circle().fill(Self.coral)
                    CoralInsetShadow(shape: Circle(), color: .rgba(255, 226, 200, 0.6), blur: 0, y: 1)
                    CoralInsetShadow(shape: Circle(), color: .rgba(150, 40, 60, 0.35), blur: 6, y: -3)
                }
                .frame(width: Self.dot, height: Self.dot)
                .scaleEffect(max(0.01, M.easeOutBack(u) * (0.6 + 0.4 * k)))
                .opacity(min(1, u * 3) * k)
                CoralCheck()
                    .stroke(Color(hex: 0x4A0F1E), style: StrokeStyle(lineWidth: 2.5, lineJoin: .round))
                    .frame(width: 7, height: 13)
                    .rotationEffect(.degrees(45))
                    .scaleEffect(0.5 + 0.5 * M.easeOutBack(v))
                    .offset(y: -1.5)
                    .opacity(v * k)
            }
            .frame(width: Self.dot, height: Self.dot)
        }
        .frame(width: Self.dot)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Day \(index + 1)")
    }

    private var statsCard: some View {
        let shape = RoundedRectangle(cornerRadius: 20, style: .continuous)
        return HStack(spacing: 0) {
            ForEach(Self.stats.indices, id: \.self) { i in
                VStack(spacing: 3) {
                    Text(Self.stats[i].1)
                        .font(.system(size: 17, weight: .semibold).monospacedDigit())
                        .foregroundStyle(Self.ink)
                    Text(Self.stats[i].0)
                        .font(.system(size: 12, weight: .medium))
                        .foregroundStyle(Self.mute)
                }
                .frame(maxWidth: .infinity)
                .overlay(alignment: .leading) {
                    if i > 0 { Rectangle().fill(Self.pale(0.18)).frame(width: 1 / 3) }
                }
            }
        }
        .padding(.vertical, 14)
        .background(shape.fill(Self.pale(0.07)))
        .overlay(CoralInsetShadow(shape: shape, color: .rgba(255, 255, 255, 0.12), blur: 0, y: 1))
        .overlay(shape.strokeBorder(Self.pale(0.16), lineWidth: 1 / 3))
        .background(shape.fill(Color.rgba(0, 8, 12, 0.3)).blur(radius: 12).offset(y: 12))
    }
}

/// a check mark: the right and bottom edges of a small box, which the caller turns 45°
private struct CoralCheck: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: rect.maxX - 1.25, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.maxX - 1.25, y: rect.maxY - 1.25))
        path.addLine(to: CGPoint(x: rect.minX, y: rect.maxY - 1.25))
        return path
    }
}

/// a CSS inset box-shadow: the outside of the shape, moved by y, blurred and clipped to the shape
private struct CoralInsetShadow<S: Shape>: View {
    let shape: S
    let color: Color
    /// CSS blur
    let blur: Double
    let y: Double

    var body: some View {
        let room = blur * 2 + abs(y) + 2
        Rectangle()
            .fill(color)
            .padding(-room)
            .mask(
                ZStack {
                    Rectangle().padding(-room)
                    shape.offset(y: y).blendMode(.destinationOut)
                }
                .compositingGroup()
            )
            .blur(radius: blur / 2)
            .clipShape(shape)
            .allowsHitTesting(false)
    }
}
