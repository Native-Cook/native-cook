import SwiftUI
import UIKit

// Lumo Frame's art-mode switch, ported from the RN reference without changing its field.
// 8 s: off 0–1.2; knob 1.2–1.7 (back ease), darkness 1.2–1.6 (cubic);
// flood 1.4–2.4 (out cubic); flowing on 2.4–6.2; drain 6.2–6.8 (in cubic),
// knob back 6.4–6.9 and pearl 6.5–6.9 (in/out cubic); off through 8, equal to 0.
// Tap toggles; a 6 pt drag scrubs 128 pt of travel, then snaps to the nearer side.
// The chosen side holds for 2 s, then rejoins the loop at t = 3 (on) or 0 (off).
struct NeonEclipseToggle: View {
    static let loop: Double = 8

    var brand = "Lumo Frame"
    var title = "Make the\nwall glow"
    var autoPlay = false

    private static let trackWidth = 236.0
    private static let trackHeight = 108.0
    private static let travel = 128.0
    private static let hold = 2.0

    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scene
    @State private var visible = false
    @State private var origin = Date()
    @State private var pausedAt: Date?
    @State private var manual = false
    @State private var touching = false
    @State private var tracking = false
    @State private var touchBegan = Date()
    @State private var grab = 0.0
    @State private var target = 0.0
    @State private var response = Pose()
    @State private var responseAt = Date()
    @State private var released = Date()
    @State private var idle = Pose()
    @State private var reducedPose = Pose()
    @State private var interaction = 0
    @State private var haptic = UIImpactFeedbackGenerator(style: .light)
    @GestureState private var gestureActive = false

    private struct Pose {
        var pos = 0.0
        var fill = 0.0
        var dark = 0.0
        var phase = 0.0
    }

    private static func clamp(_ x: Double) -> Double { min(1, max(0, x)) }
    private static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp((t - a) / (b - a)) }
    private static func back(_ x: Double) -> Double { 1 + 2.4 * pow(x - 1, 3) + 1.4 * pow(x - 1, 2) }
    private static func outCubic(_ x: Double) -> Double { 1 - pow(1 - x, 3) }
    private static func cubic(_ x: Double) -> Double { x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2 }

    private static func loopState(_ time: Double) -> Pose {
        let t = max(0, time).truncatingRemainder(dividingBy: loop)
        return Pose(
            pos: back(seg(t, 1.2, 1.7)) - cubic(seg(t, 6.4, 6.9)),
            fill: outCubic(seg(t, 1.4, 2.4)) * (1 - pow(seg(t, 6.2, 6.8), 3)),
            dark: cubic(seg(t, 1.2, 1.6)) * (1 - cubic(seg(t, 6.5, 6.9))),
            phase: t / loop * 2 * .pi
        )
    }

    // Closed-form equivalents of RN's per-frame exponential easing. Retarget only on touch;
    // TimelineView never writes state, so rendering is independent of dropped frames.
    private func state(at date: Date) -> Pose {
        reduced ? reducedPose : liveState(at: date)
    }

    private func liveState(at date: Date) -> Pose {
        let phase = Self.loopState(date.timeIntervalSince(origin)).phase
        if manual {
            let dt = max(0, date.timeIntervalSince(responseAt))
            return Pose(pos: touching ? response.pos : target + (response.pos - target) * exp(-10 * dt),
                         fill: target + (response.fill - target) * exp(-(target > 0.5 ? 4 : 7) * dt),
                         dark: target + (response.dark - target) * exp(-8 * dt), phase: phase)
        }
        if autoPlay { return Self.loopState(date.timeIntervalSince(origin)) }
        return Pose(pos: idle.pos, fill: idle.fill, dark: idle.dark, phase: phase)
    }

    private static func background(_ fill: Double) -> Color {
        Color(.sRGB, red: (16 - 9 * fill).rounded() / 255,
              green: (16 - 6 * fill).rounded() / 255, blue: (17 + 10 * fill).rounded() / 255)
    }

    var body: some View {
        GeometryReader { geometry in
            let safe = geometry.safeAreaInsets
            let size = CGSize(width: geometry.size.width + safe.leading + safe.trailing,
                              height: geometry.size.height + safe.top + safe.bottom)
            let x = (size.width - Self.trackWidth) / 2
            let y = size.height * 0.46 - Self.trackHeight / 2
            TimelineView(.animation(paused: !visible || scene != .active || reduced || (!autoPlay && !manual && idle.fill == 0))) { timeline in
                let current = state(at: pausedAt ?? timeline.date)
                ZStack(alignment: .topLeading) {
                    Self.background(current.fill)
                    heading(safe: safe, width: size.width)

                    Rectangle()
                        .fill(.white)
                        .frame(width: size.width, height: Self.trackHeight + 360)
                        .colorEffect(ShaderLibrary.neonEclipseToggleField(
                            .float4(x, y, Self.trackWidth, Self.trackHeight),
                            .float4(x + Self.trackHeight / 2 + Self.travel * current.pos,
                                    current.fill, current.dark, current.phase),
                            .float2(0, y - 160)
                        ))
                        .offset(y: y - 160)
                        .allowsHitTesting(false)
                        .accessibilityHidden(true)

                    Color.clear
                        .frame(width: Self.trackWidth + 24, height: Self.trackHeight + 32)
                        .contentShape(Rectangle())
                        .gesture(drag, including: reduced ? .none : .all)
                        .offset(x: x - 12, y: y - 16)
                        .accessibilityElement()
                        .accessibilityLabel("Art mode")
                        .accessibilityValue(current.pos > 0.5 ? "On" : "Off")
                        .accessibilityAddTraits(.isButton)
                        .accessibilityAction {
                            guard !reduced else { return }
                            let now = Date()
                            choose(state(at: now).pos > 0.5 ? 0 : 1, at: now)
                        }

                    Text("ART MODE")
                        .font(.system(size: 14, weight: .heavy))
                        .tracking(3)
                        .foregroundStyle(.white)
                        .frame(width: size.width - 48, alignment: .top)
                        .offset(x: 24, y: y - 46)
                        .allowsHitTesting(false)
                    states(fill: current.fill)
                        .frame(width: size.width - 48, alignment: .top)
                        .offset(x: 24, y: y + Self.trackHeight + 92)
                    footer(safe: safe, width: size.width)
                        .frame(width: size.width, height: size.height, alignment: .bottom)
                }
                .frame(width: size.width, height: size.height, alignment: .topLeading)
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .ignoresSafeArea()
        }
        .preferredColorScheme(.dark)
        .onAppear {
            visible = true
            origin = .now
            pausedAt = reduced || scene != .active ? origin : nil
            haptic.prepare()
        }
        .onDisappear { visible = false }
        .onChange(of: reduced) { _, value in
            if value {
                let now = pausedAt ?? Date()
                reducedPose = liveState(at: now)
                if pausedAt == nil { pausedAt = now }
                interaction += 1
            } else if scene == .active {
                resumeClock()
            }
        }
        .onChange(of: autoPlay) { _, _ in reset() }
        .onChange(of: scene) { _, phase in
            if phase != .active {
                if pausedAt == nil { pausedAt = .now }
                interaction += 1
            } else if !reduced {
                resumeClock()
            }
        }
        .onChange(of: gestureActive) { _, active in
            guard !active else { return }
            Task { @MainActor in
                await Task.yield()
                guard tracking, !gestureActive, !reduced else { return }
                if touching { choose(response.pos > 0.5 ? 1 : 0, at: Date()) }
                tracking = false
            }
        }
        .task(id: interaction) { await resumeLoop() }
    }

    private func heading(safe: EdgeInsets, width: CGFloat) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 0) {
                Text(brand).font(.system(size: 17, weight: .heavy)).tracking(0.2)
                Spacer(minLength: 0)
                Text("Living room").font(.system(size: 14, weight: .semibold)).opacity(0.55)
            }
            .frame(height: 34)
            .offset(y: -1.0 / 3.0)
            VStack(alignment: .leading, spacing: 0) {
                ForEach(Array(title.components(separatedBy: "\n").enumerated()), id: \.offset) { _, line in
                    Text(line)
                        .font(.system(size: 44, weight: .black))
                        .tracking(-1.5)
                        .fixedSize(horizontal: false, vertical: true)
                        .frame(height: 48, alignment: .leading)
                }
            }
            // Match RN's 48 pt line boxes rather than SwiftUI's natural line metrics.
            .offset(y: -2)
            .padding(.top, 16)
        }
        .foregroundStyle(.white)
        .frame(width: width - 48, alignment: .leading)
        .offset(x: 24, y: safe.top + 8)
        .allowsHitTesting(false)
    }

    private func states(fill: Double) -> some View {
        let on = Self.clamp(fill * 2 - 0.4)
        return ZStack(alignment: .top) {
            Text("Off · the frame sleeps").opacity(1 - on).accessibilityHidden(on >= 0.5)
            Text("On · showing “Solar Bloom”").opacity(on).accessibilityHidden(on < 0.5)
        }
        .font(.system(size: 16, weight: .semibold))
        .foregroundStyle(.white.opacity(0.75))
        .allowsHitTesting(false)
    }

    private func footer(safe: EdgeInsets, width: CGFloat) -> some View {
        VStack(spacing: 0) {
            cardRow("Glow schedule", "18:00 – 23:30")
            Rectangle().fill(.white.opacity(0.1)).frame(height: 1)
            cardRow("Brightness", "70 %")
        }
        .padding(.horizontal, 18)
        .padding(1)
        .background(.white.opacity(0.07), in: RoundedRectangle(cornerRadius: 22))
        .overlay(RoundedRectangle(cornerRadius: 22).strokeBorder(.white.opacity(0.1), lineWidth: 1))
        .padding(.horizontal, 20)
        .padding(.bottom, safe.bottom + 16)
        .frame(width: width)
        .allowsHitTesting(false)
    }

    private func cardRow(_ title: String, _ value: String) -> some View {
        HStack(spacing: 0) {
            Text(title).font(.system(size: 16, weight: .semibold)).foregroundStyle(.white)
            Spacer(minLength: 0)
            Text(value).font(.system(size: 16)).foregroundStyle(.white.opacity(0.6))
        }
        .frame(height: 56)
    }

    private var drag: some Gesture {
        DragGesture(minimumDistance: 0)
            .updating($gestureActive) { _, active, _ in active = true }
            .onChanged { value in
                guard !reduced else { return }
                let now = Date()
                if !tracking {
                    tracking = true
                    touchBegan = now
                    grab = state(at: now).pos
                }
                if !touching && hypot(value.translation.width, value.translation.height) < 6 { return }
                let current = state(at: now)
                let pos = Self.clamp(grab + value.translation.width / Self.travel)
                response = Pose(pos: pos, fill: current.fill, dark: current.dark)
                responseAt = now
                target = pos > 0.5 ? 1 : 0
                touching = true
                manual = true
                interaction += 1
            }
            .onEnded { _ in
                guard !reduced else { return }
                let now = Date()
                // Gesture.Tap's default maximum duration is 500 ms in the RN reference.
                guard touching || now.timeIntervalSince(touchBegan) <= 0.5 else {
                    tracking = false
                    return
                }
                let pos = state(at: now).pos
                let chosen = touching ? (pos > 0.5 ? 1.0 : 0.0) : (pos > 0.5 ? 0.0 : 1.0)
                choose(chosen, at: now)
                tracking = false
            }
    }

    private func choose(_ on: Double, at date: Date) {
        response = state(at: date)
        responseAt = date
        touching = false
        manual = true
        target = on
        released = date
        interaction += 1
        haptic.impactOccurred()
        haptic.prepare()
    }

    private func resumeLoop() async {
        guard manual, !touching, !reduced, scene == .active else { return }
        let remaining = max(0, Self.hold - Date().timeIntervalSince(released))
        try? await Task.sleep(for: .seconds(remaining))
        guard !Task.isCancelled, manual, !touching, !reduced, scene == .active else { return }
        idle = liveState(at: released.addingTimeInterval(Self.hold))
        origin = released.addingTimeInterval(Self.hold - (target > 0.5 ? 3 : 0))
        manual = false
    }

    private func reset() {
        manual = false
        touching = false
        tracking = false
        response = Pose()
        idle = Pose()
        reducedPose = Pose()
        target = 0
        origin = .now
        pausedAt = reduced || scene != .active ? origin : nil
        interaction += 1
    }

    private func resumeClock() {
        guard let pause = pausedAt else { return }
        let gap = Date().timeIntervalSince(pause)
        origin = origin.addingTimeInterval(gap)
        responseAt = responseAt.addingTimeInterval(gap)
        released = released.addingTimeInterval(gap)
        touchBegan = touchBegan.addingTimeInterval(gap)
        pausedAt = nil
        interaction += 1
    }
}

#Preview {
    NeonEclipseToggle(autoPlay: true)
}
