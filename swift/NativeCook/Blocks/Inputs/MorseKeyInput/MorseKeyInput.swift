import SwiftUI
import UIKit

/*
 Morse Key Input — "Dot Dash", a puzzle app where the answer is keyed on a brass telegraph key.

 Look: a green leather desk, a telegram card with the clue typed on it and two answer boxes, a
 brass register (with a paper reel turning behind a window) that pays out a strip of tape, and a
 straight key on a walnut base: a brass lever on a trunnion, a black bakelite knob, a spring, a
 silver contact, binding posts wired to the register.

 Press the key: the lever pivots 3.3° about the trunnion onto the contact (a spring, ω 60 rad/s,
 ζ 0.5, stopped by the contact with a little bounce), the contact sparks blue-white and hums while
 closed, the register's pen drops onto the tape, and the tape — running at 34 pt/s while you key
 — prints a dot or a dash (longer than 0.2 s). After a 0.55 s pause the letter is decoded and rolls
 up into the next answer box; two letters show "Correct · +40" (or "Not quite"). 2 s after the
 last touch the printed tape tears off and falls, the answer rolls out and a fresh leader feeds
 out (0.9 s); then the loop (MorseKeyInputMotion.swift, loop = 9 s, "HI" in real timing) starts
 again. Reduced motion: no loop; the key still works, without sparks.
*/
struct MorseKeyInput: View {
    /// Plays the 9 s loop that keys "HI" until touched.
    var autoPlay = false

    static let loop = MorseKeyInputMotion.loop
    private typealias M = MorseKeyInputMotion
    private typealias Art = MorseKeyInputArt

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var engine = MorseKeyInputEngine()
    @State private var handActive = false
    @State private var keyHeld = false
    @State private var idleTask: Task<Void, Never>? = nil
    @State private var light = UIImpactFeedbackGenerator(style: .light)
    @State private var select = UISelectionFeedbackGenerator()
    @State private var success = UINotificationFeedbackGenerator()

    static let ink = Color(hex: 0xEFE6D2)
    static let mute = Color.rgba(239, 230, 210, 0.64)
    static let faint = Color.rgba(239, 230, 210, 0.44)
    static let brass = Color(hex: 0xD8B670)

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let s = Art.Scene(w: proxy.size.width + insets.leading + insets.trailing,
                              h: proxy.size.height + insets.top + insets.bottom,
                              top: insets.top, bottom: insets.bottom)
            stage(s, top: insets.top, bottom: insets.bottom)
                .frame(width: s.w, height: s.h, alignment: .topLeading)
                .offset(x: -insets.leading, y: -insets.top)
        }
        .background(Color(hex: 0x111D1A).ignoresSafeArea())
        .preferredColorScheme(.dark)
        .onAppear {
            light.prepare()
            select.prepare()
            success.prepare()
        }
        .onDisappear { idleTask?.cancel() }
    }

    private func stage(_ s: Art.Scene, top: Double, bottom: Double) -> some View {
        let auto = autoPlay && !reduceMotion
        let reduced = reduceMotion
        return ZStack(alignment: .topLeading) {
            MorseKeyInputDesk(scene: s).equatable()

            TimelineView(.animation(paused: !auto && !handActive)) { timeline in
                let now = timeline.date.timeIntervalSinceReferenceDate
                let pose = framePose(now, s, auto: auto, reduced: reduced)
                ZStack(alignment: .topLeading) {
                    Canvas { ctx, _ in Art.under(ctx, s, pose) }
                    MorseKeyInputRegister(scene: s).equatable()
                    Canvas { ctx, _ in Art.over(ctx, s, pose) }
                }
                .frame(width: s.w, height: s.h, alignment: .topLeading)
            }
            .allowsHitTesting(false)

            // The key: anywhere below the tape.
            Color.clear
                .frame(width: s.w, height: max(0, s.h - (s.tapeY + s.tapeH)))
                .contentShape(Rectangle())
                .offset(y: s.tapeY + s.tapeH)
                .gesture(keyGesture(s))
                .accessibilityElement()
                .accessibilityAddTraits(.isButton)
                .accessibilityLabel("Telegraph key. Tap for a dot, hold for a dash; pause to finish a letter.")

            VStack(alignment: .leading, spacing: 4) {
                Text("DOT DASH")
                    .font(.system(size: 12, weight: .bold))
                    .kerning(3)
                    .foregroundStyle(Self.brass)
                Text("Send your answer")
                    .font(.system(size: 30, weight: .bold))
                    .kerning(-0.5)
                    .foregroundStyle(Self.ink)
                Text("Puzzle 27 · The telegraph room")
                    .font(.system(size: 15, weight: .medium))
                    .foregroundStyle(Self.mute)
            }
            .frame(width: s.w - 40, alignment: .leading)
            .offset(x: 20, y: top + 12)
            .allowsHitTesting(false)

            controls(s)
                .frame(width: s.w - 40, height: max(0, s.h - bottom - 8 - (s.baseTop + 76)))
                .offset(x: 20, y: s.baseTop + 76)
        }
        .frame(width: s.w, height: s.h, alignment: .topLeading)
        .clipped()
    }

    private func framePose(_ now: Double, _ s: Art.Scene, auto: Bool, reduced: Bool) -> M.Pose {
        var p = engine.pose(at: now, leader: s.leader, auto: auto)
        if reduced { p.sparkAge = 99 }
        let landed = engine.landed
        if landed != 0 {
            engine.landed = 0
            // Haptics after the frame is built (the timeline may not write state while drawing).
            DispatchQueue.main.async {
                if landed == 2 { success.notificationOccurred(.success) } else { select.selectionChanged() }
            }
        }
        return p
    }

    // MARK: Controls

    private func controls(_ s: Art.Scene) -> some View {
        VStack(spacing: 12) {
            Spacer(minLength: 0)
            HStack(spacing: 18) {
                legend(label: "Tap") { Circle().fill(Self.ink).frame(width: 7, height: 7) }
                legend(label: "Hold") { Capsule().fill(Self.ink).frame(width: 18, height: 6) }
                legend(label: "Pause for the next letter") {
                    MorseKeyInputSpaceMark()
                        .stroke(Self.ink, lineWidth: 1.5)
                        .frame(width: 18, height: 6)
                        .padding(.top, 2)
                }
            }
            .allowsHitTesting(false)
            Spacer(minLength: 0)
            HStack(spacing: 12) {
                Button(action: clear) {
                    Text("Clear tape")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(Self.ink)
                        .frame(maxWidth: .infinity)
                        .frame(height: 52)
                        .background(Capsule().fill(Color.rgba(239, 230, 210, 0.07)))
                        .overlay(Capsule().strokeBorder(Color.rgba(239, 230, 210, 0.18), lineWidth: 0.5))
                }
                .buttonStyle(MorseKeyInputPressStyle())
                Button(action: send) {
                    Text("Send letter")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundStyle(Color(hex: 0x2A2112))
                        .frame(maxWidth: .infinity)
                        .frame(height: 52)
                        .background {
                            Capsule().fill(Color(hex: 0xD8B670))
                                .shadow(color: .rgba(216, 182, 112, 0.18), radius: 12, y: 10)
                                .shadow(color: .black.opacity(0.45), radius: 1, y: 1)
                        }
                        .overlay(Capsule().strokeBorder(LinearGradient(colors: [.rgba(255, 246, 220, 0.6), .clear], startPoint: .top, endPoint: .center), lineWidth: 1))
                        .overlay(Capsule().strokeBorder(Color(hex: 0xE9CC8E), lineWidth: 0.5))
                }
                .buttonStyle(MorseKeyInputPressStyle())
            }
            Spacer(minLength: 0)
        }
    }

    private func legend<Mark: View>(label: String, @ViewBuilder _ mark: () -> Mark) -> some View {
        HStack(spacing: 7) {
            mark()
            Text(label)
                .font(.system(size: 13, weight: .medium))
                .foregroundStyle(Self.faint)
                .lineLimit(1)
                .fixedSize()
        }
    }

    // MARK: Touch

    private func keyGesture(_ s: Art.Scene) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { _ in
                guard !keyHeld else { return }
                keyHeld = true
                let at = Date.now.timeIntervalSinceReferenceDate
                wake()
                engine.takeOver(at)
                engine.sim.keyDown(at)
                engine.lastTouch = at
                light.impactOccurred()
            }
            .onEnded { _ in
                keyHeld = false
                let at = Date.now.timeIntervalSinceReferenceDate
                engine.sim.keyUp(at)
                engine.lastTouch = at
                sleepLater()
            }
    }

    /// "Clear tape": tear it off now.
    private func clear() {
        let at = Date.now.timeIntervalSinceReferenceDate
        wake()
        engine.takeOver(at)
        let s = engine.sim
        s.held = false
        s.buffer = ""
        s.torn = M.Strip(len: s.base + s.run, run: s.run, marks: s.marks)
        s.backAt = at
        engine.mode = .back
        sleepLater()
    }

    /// "Send": finish the letter in progress at once.
    private func send() {
        let at = Date.now.timeIntervalSinceReferenceDate
        wake()
        engine.takeOver(at)
        engine.sim.upAt = at - M.letterGap - 0.01
        engine.lastTouch = at
        sleepLater()
    }

    private func wake() {
        idleTask?.cancel()
        handActive = true
    }

    /// Without autoPlay, the timeline stops again once the screen is back at rest.
    private func sleepLater() {
        idleTask?.cancel()
        idleTask = Task { @MainActor in
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(0.5))
                if !Task.isCancelled && !keyHeld && engine.mode == .auto {
                    handActive = false
                    return
                }
            }
        }
    }
}

/// The legend's "pause" mark: a bracket open at the top.
private struct MorseKeyInputSpaceMark: Shape {
    func path(in rect: CGRect) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: rect.minX + 0.75, y: rect.minY))
        p.addLine(to: CGPoint(x: rect.minX + 0.75, y: rect.maxY - 0.75))
        p.addLine(to: CGPoint(x: rect.maxX - 0.75, y: rect.maxY - 0.75))
        p.addLine(to: CGPoint(x: rect.maxX - 0.75, y: rect.minY))
        return p
    }
}

private struct MorseKeyInputPressStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
    }
}

/// The desk, the card and the key's fixed parts, drawn once.
private struct MorseKeyInputDesk: View, Equatable {
    let scene: MorseKeyInputArt.Scene
    var body: some View {
        Canvas { ctx, _ in MorseKeyInputArt.desk(ctx, scene) }
            .frame(width: scene.w, height: scene.h)
    }
}

/// The register, drawn once, over the tape.
private struct MorseKeyInputRegister: View, Equatable {
    let scene: MorseKeyInputArt.Scene
    var body: some View {
        Canvas { ctx, _ in MorseKeyInputArt.register(ctx, scene) }
            .frame(width: scene.w, height: scene.h)
    }
}
