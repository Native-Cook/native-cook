import SwiftUI
import UIKit

/*
 Elevator Floor Nav — "Merrow & Hale", a department store whose section navigation is its lift.

 Look: a green marble lobby; above the doorway an enamel floor dial (1 – 5) on a brass half-moon
 with a sunburst and two hall lanterns; the doorway itself in a brass surround, showing the floor
 the car is on (each a poster drawn in code: "Shoes", "Fashion", "Beauty", "Home", "Café", with
 three arched display windows and prices); two brushed-steel doors with vision panels; below, a
 brass panel of five glass buttons with the sections' names.

 Tap a floor: its button dips and lights amber, the doors close (0.6 s, a small bump as they
 meet), the car rides 0.4 s a floor (smootherstep) while the store slides by behind the vision
 panels, blurred with speed, and the needle follows; on arrival the lantern of the direction
 glows, a ring of light leaves the dial, the button goes out, the doors open on the new section
 and the header rolls to its name. A floor called during a trip is queued. 2 s after the last
 touch the car goes home to floor 1 at a quick pace and the loop starts again from t = 0
 (ElevatorFloorNavMotion.swift, loop = 9 s). Reduced motion: no loop; a floor opens at once.
*/
struct ElevatorFloorNav: View {
    /// Plays the 9 s loop (to Home and back to Shoes) until touched.
    var autoPlay = false

    static let loop = ElevatorFloorNavMotion.loop
    private typealias M = ElevatorFloorNavMotion
    private typealias Art = ElevatorFloorNavArt

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var engine = ElevatorFloorNavEngine()
    @State private var handActive = false
    /// Under reduced motion the timeline is paused; this redraws the floor that was called.
    @State private var stillFloor = 1
    @State private var idleTask: Task<Void, Never>? = nil
    @State private var light = UIImpactFeedbackGenerator(style: .light)
    @State private var select = UISelectionFeedbackGenerator()

    static let ink = Color(hex: 0xF3EAD7)
    static let brass = Color(hex: 0xD9B66E)
    static let labelH = 18.0

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let s = Art.Scene(w: proxy.size.width + insets.leading + insets.trailing,
                              h: proxy.size.height + insets.top + insets.bottom,
                              top: insets.top, bottom: insets.bottom)
            stage(s, top: insets.top)
                .frame(width: s.w, height: s.h, alignment: .topLeading)
                .offset(x: -insets.leading, y: -insets.top)
        }
        .background(Color(hex: 0x10221C).ignoresSafeArea())
        .preferredColorScheme(.dark)
        .onAppear {
            light.prepare()
            select.prepare()
        }
        .onDisappear { idleTask?.cancel() }
    }

    private func stage(_ s: Art.Scene, top: Double) -> some View {
        let auto = autoPlay && !reduceMotion
        let reduced = reduceMotion
        let step = s.doorH + 30
        let pad = 44.0
        return ZStack(alignment: .topLeading) {
            ElevatorFloorNavLobby(scene: s).equatable()

            TimelineView(.animation(paused: !auto && !handActive)) { timeline in
                let now = timeline.date.timeIntervalSinceReferenceDate
                let pose = framePose(now, reduced: reduced, auto: auto, still: stillFloor)
                let half = s.doorW / 2
                let travel = (1 - pose.doors) * (half + 6)
                let blur = min(16, pose.speed * 9)
                ZStack(alignment: .topLeading) {
                    // The store behind the doorway: each floor a poster stacked by height, moving
                    // with the car, blurred along y with its speed.
                    ZStack(alignment: .topLeading) {
                        ForEach(0..<M.floors, id: \.self) { f in
                            // Every floor stays in the tree (drawn once), clipped away when out of sight.
                            let y = pad + (pose.car - Double(f + 1)) * step
                            ElevatorFloorNavPoster(scene: s, floor: f).equatable()
                                .offset(y: y)
                            if f < M.floors - 1 {
                                ElevatorFloorNavGap(scene: s).equatable()
                                    .offset(y: y - 30)
                            }
                        }
                    }
                    .frame(width: s.doorW, height: s.doorH + pad * 2, alignment: .topLeading)
                    .layerEffect(ShaderLibrary.elevatorFloorNavMotionBlur(.float(blur)),
                                 maxSampleOffset: CGSize(width: 0, height: pad),
                                 isEnabled: blur > 0.3)
                    .frame(width: s.doorW, height: s.doorH)
                    .clipped()
                    .offset(x: s.doorX, y: s.doorY)

                    // The doors: open, each hides behind its jamb; closed, they meet in the middle.
                    if pose.doors > 0.002 {
                        ZStack(alignment: .topLeading) {
                            Canvas { ctx, _ in Art.doorShadow(ctx, s, pose) }
                            ElevatorFloorNavDoor(scene: s, left: true).equatable()
                                .offset(x: s.doorX - travel, y: s.doorY)
                            ElevatorFloorNavDoor(scene: s, left: false).equatable()
                                .offset(x: s.doorX + half + travel, y: s.doorY)
                            Canvas { ctx, _ in Art.doorGlass(ctx, s, pose) }
                        }
                        .frame(width: s.w, height: s.h, alignment: .topLeading)
                        .mask(alignment: .topLeading) {
                            Rectangle()
                                .frame(width: s.doorW, height: s.doorH)
                                .offset(x: s.doorX, y: s.doorY)
                        }
                    }

                    ElevatorFloorNavFrame(scene: s).equatable()
                    Canvas { ctx, _ in Art.instruments(ctx, s, pose) }

                    header(s, top: top, pose: pose)
                }
                .frame(width: s.w, height: s.h, alignment: .topLeading)
            }
            .allowsHitTesting(false)

            ForEach(0..<M.floors, id: \.self) { i in
                Button {
                    call(i + 1, auto: auto, reduced: reduced)
                } label: {
                    Color.clear
                        .frame(width: s.buttonR * 2 + 20, height: s.buttonR * 2 + 20)
                        .contentShape(Circle())
                }
                .buttonStyle(.plain)
                .offset(x: s.buttonX[i] - s.buttonR - 10, y: s.buttonY - s.buttonR - 10)
                .accessibilityLabel("Floor \(i + 1), \(Art.store[i].name)")
            }
        }
        .frame(width: s.w, height: s.h, alignment: .topLeading)
        .clipped()
    }

    private func framePose(_ now: Double, reduced: Bool, auto: Bool, still: Int) -> M.Pose {
        engine.still = still
        let p = engine.pose(at: now, auto: auto, reduced: reduced)
        if engine.arrived {
            engine.arrived = false
            DispatchQueue.main.async { select.selectionChanged() }
        }
        return p
    }

    private func header(_ s: Art.Scene, top: Double, pose: M.Pose) -> some View {
        VStack(spacing: 4) {
            Text("MERROW & HALE")
                .font(.custom("Georgia", size: 17))
                .kerning(4.5)
                .foregroundStyle(Self.ink)
            ZStack {
                ForEach(1...M.floors, id: \.self) { f in
                    floorLabel(f, pose, width: s.w - 40)
                }
            }
            .frame(width: s.w - 40, height: Self.labelH + 2, alignment: .top)
            .clipped()
        }
        .frame(width: s.w - 40)
        .offset(x: 20, y: top + 8)
    }

    /// One floor's name in the header: rolls up out, or in from below, when the car arrives.
    private func floorLabel(_ floor: Int, _ p: M.Pose, width: Double) -> some View {
        var y = Self.labelH
        var a = 0.0
        if p.labelTo == floor {
            y = (1 - p.labelK) * Self.labelH
            a = p.labelK
        } else if p.labelFrom == floor && p.labelFrom != p.labelTo {
            y = -p.labelK * Self.labelH
            a = 1 - p.labelK
        }
        return Text("Floor \(floor) · \(Art.store[floor - 1].name)")
            .font(.system(size: 13, weight: .semibold))
            .kerning(0.4)
            .foregroundStyle(Self.brass)
            .lineLimit(1)
            .frame(width: width, height: Self.labelH)
            .opacity(a)
            .offset(y: y)
    }

    private func call(_ floor: Int, auto: Bool, reduced: Bool) {
        light.impactOccurred()
        let at = Date.now.timeIntervalSinceReferenceDate
        if reduced {
            stillFloor = floor
        } else {
            handActive = true
        }
        engine.call(floor, at: at, auto: auto, reduced: reduced)
        idleTask?.cancel()
        guard !reduced else { return }
        // Without autoPlay, the timeline stops again once the car is home and at rest.
        idleTask = Task { @MainActor in
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(0.5))
                if !Task.isCancelled && engine.mode == .auto {
                    handActive = false
                    return
                }
            }
        }
    }
}

/// The marble lobby, the dial's face and the button panel, drawn once.
private struct ElevatorFloorNavLobby: View, Equatable {
    let scene: ElevatorFloorNavArt.Scene
    var body: some View {
        Canvas { ctx, _ in ElevatorFloorNavArt.lobby(ctx, scene) }
            .frame(width: scene.w, height: scene.h)
    }
}

/// One floor of the store, drawn once.
private struct ElevatorFloorNavPoster: View, Equatable {
    let scene: ElevatorFloorNavArt.Scene
    let floor: Int
    var body: some View {
        Canvas { ctx, _ in ElevatorFloorNavArt.poster(ctx, scene, floor) }
            .frame(width: scene.doorW, height: scene.doorH)
    }
}

/// The shaft between two floors, drawn once.
private struct ElevatorFloorNavGap: View, Equatable {
    let scene: ElevatorFloorNavArt.Scene
    var body: some View {
        Canvas { ctx, _ in ElevatorFloorNavArt.gap(ctx, scene) }
            .frame(width: scene.doorW, height: 30)
    }
}

/// One brushed-steel door, drawn once.
private struct ElevatorFloorNavDoor: View, Equatable {
    let scene: ElevatorFloorNavArt.Scene
    let left: Bool
    var body: some View {
        Canvas { ctx, _ in ElevatorFloorNavArt.door(ctx, scene, left: left) }
            .frame(width: scene.doorW / 2, height: scene.doorH)
    }
}

/// The doorway's brass surround, drawn once.
private struct ElevatorFloorNavFrame: View, Equatable {
    let scene: ElevatorFloorNavArt.Scene
    var body: some View {
        Canvas { ctx, _ in ElevatorFloorNavArt.frame(ctx, scene) }
            .frame(width: scene.w, height: scene.h)
    }
}
