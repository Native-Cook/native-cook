import SwiftUI
import UIKit

/// A "formulating" waiting screen: a clear glass capsule turns end over end on near black. When a
/// cap swings towards the viewer it throws a band of spectrum, then a warm caustic, then shows as
/// a disc with the body's rings stacked behind it. Drag to turn it by hand.
struct SpectralCapsuleSpin: View {
    var brand = "ORRIN"
    var batch = "Batch 0427"
    var label = "FORMULATING"
    var title = "Your daily blend"
    var detail = "Sealing four actives into one clear capsule."
    /// Turns by itself, one full turn every `loop` seconds.
    var autoPlay = false

    static let loop = 10.0

    /// Capsule radius and half the seam-to-seam length, as fractions of the screen width.
    private static let radiusRatio = 0.172
    private static let halfLengthRatio = 0.268
    private static let centreRatio = 0.44
    /// Points of drag along the capsule's diagonal for one radian of turn.
    private static let dragScale = 90.0
    private static let coastTau = 0.35
    private static let holdAfterTouch = 2.0
    private static let settle = 0.9
    private static let reducedPhi = 0.80

    private typealias P = SpectralCapsuleSpinPalette
    private typealias T = SpectralCapsuleSpinTimeline

    private struct Release {
        let phi: Double
        let velocity: Double
        let at: Date
    }

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var origin = Date()
    @State private var dragBase: Double? = nil
    @State private var dragPhi: Double? = nil
    @State private var release: Release? = nil
    @State private var detent = 0
    @State private var haptic = UIImpactFeedbackGenerator(style: .light)

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let full = CGSize(width: proxy.size.width + insets.leading + insets.trailing,
                              height: proxy.size.height + insets.top + insets.bottom)
            screen(full: full, insets: insets)
                .frame(width: full.width, height: full.height, alignment: .topLeading)
                .offset(x: -insets.leading, y: -insets.top)
        }
        .background(P.rgb(P.background).ignoresSafeArea())
        .preferredColorScheme(.dark)
    }

    private var idle: Bool {
        !autoPlay && !reduceMotion && dragPhi == nil && release == nil
    }

    @ViewBuilder
    private func screen(full: CGSize, insets: EdgeInsets) -> some View {
        let width = full.width
        let centre = CGPoint(x: width / 2, y: full.height * Self.centreRatio)
        let bottom = full.height - insets.bottom
        TimelineView(.animation(minimumInterval: reduceMotion ? 1.0 / 20 : nil, paused: idle)) { timeline in
            let pose = pose(at: timeline.date)
            let light = T.Light(facing: abs(sin(pose.phi)))
            ZStack(alignment: .topLeading) {
                RadialGradient(colors: [P.rgb(P.vignette), P.rgb(P.background)],
                               center: UnitPoint(x: 0.5, y: Self.centreRatio),
                               startRadius: 0, endRadius: full.height * 0.62)
                RadialGradient(colors: [P.rgb(P.halo, 0.10 + 0.16 * light.warm * pose.warmGain), P.rgb(P.halo, 0)],
                               center: UnitPoint(x: 0.5, y: Self.centreRatio),
                               startRadius: 0, endRadius: 260)
                Canvas { context, size in
                    SpectralCapsuleSpinRenderer(phi: pose.phi, hueShift: pose.hueShift, warmGain: pose.warmGain,
                                                radius: width * Self.radiusRatio,
                                                halfLength: width * Self.halfLengthRatio)
                        .draw(in: &context, size: size)
                }
                .frame(width: width, height: 440)
                .position(x: centre.x, y: centre.y)
                .accessibilityElement()
                .accessibilityLabel(Text("A clear capsule turning end over end"))
                .accessibilityHint(Text("Drag to turn it"))

                indicator(phi: pose.phi)
                    .position(x: width / 2, y: bottom - 40)
            }
            .frame(width: full.width, height: full.height)
            .contentShape(Rectangle())
            .gesture(drag(at: timeline.date))
        }
        .overlay(alignment: .topLeading) {
            HStack {
                Text(brand)
                    .font(.system(size: 13, weight: .semibold))
                    .tracking(3)
                    .foregroundStyle(P.rgb(0xC9CFDB))
                Spacer()
                Text(batch)
                    .font(.system(size: 13, weight: .regular).monospacedDigit())
                    .foregroundStyle(P.rgb(0x6E7587))
            }
            .padding(.horizontal, 24)
            .frame(width: width, height: 20)
            .position(x: width / 2, y: insets.top + 24)
            .allowsHitTesting(false)
        }
        .overlay(alignment: .topLeading) {
            ZStack {
                Text(label)
                    .font(.system(size: 12, weight: .semibold))
                    .tracking(2.4)
                    .foregroundStyle(P.rgb(0x7C8496))
                    .position(x: width / 2, y: bottom - 148)
                Text(title)
                    .font(.system(size: 28, weight: .semibold))
                    .foregroundStyle(P.rgb(0xF1F3F8))
                    .position(x: width / 2, y: bottom - 114)
                Text(detail)
                    .font(.system(size: 15, weight: .regular))
                    .foregroundStyle(P.rgb(0x8C93A4))
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: 320)
                    .fixedSize(horizontal: false, vertical: true)
                    .position(x: width / 2, y: bottom - 80)
            }
            .frame(width: width, height: full.height)
            .allowsHitTesting(false)
        }
    }

    /// Two small capsules: each lights when its end of the big one faces the viewer.
    private func indicator(phi: Double) -> some View {
        let a = Self.endOn(phi, .pi / 2)
        let b = Self.endOn(phi, 3 * .pi / 2)
        return HStack(spacing: 10) {
            pip(a)
            pip(b)
        }
        .accessibilityHidden(true)
    }

    private func pip(_ lit: Double) -> some View {
        Capsule()
            .fill(P.rgb(0x232836))
            .overlay(Capsule().fill(P.rgb(0xDCE6FF, lit)))
            .frame(width: 22, height: 8)
            .shadow(color: P.rgb(0x4D7BFF, 0.9 * lit), radius: 6)
    }

    /// 1 when `phi` is at `target` (mod 2π), falling off over about a quarter of a radian.
    static func endOn(_ phi: Double, _ target: Double) -> Double {
        var d = (phi - target).truncatingRemainder(dividingBy: 2 * .pi)
        if d < -Double.pi { d += 2 * .pi }
        if d > Double.pi { d -= 2 * .pi }
        return T.bell(d, 0, 0.45)
    }

    private struct Pose {
        var phi: Double
        var hueShift = 0.0
        var warmGain = 1.0
    }

    private func loopTime(_ seconds: Double) -> Double {
        let t = seconds.truncatingRemainder(dividingBy: Self.loop)
        return t < 0 ? t + Self.loop : t
    }

    private func pose(at date: Date) -> Pose {
        if reduceMotion {
            let w = 2 * Double.pi * loopTime(date.timeIntervalSince(origin)) / Self.loop
            return Pose(phi: Self.reducedPhi, hueShift: 0.6 * sin(w), warmGain: 0.85 + 0.15 * sin(w))
        }
        if let dragPhi { return Pose(phi: dragPhi) }
        if let release {
            let e = date.timeIntervalSince(release.at)
            func coast(_ x: Double) -> Double {
                release.phi + release.velocity * Self.coastTau * (1 - exp(-x / Self.coastTau))
            }
            if e < Self.holdAfterTouch { return Pose(phi: coast(e)) }
            let held = coast(Self.holdAfterTouch)
            let rest = (held / .pi).rounded() * .pi
            let k = (e - Self.holdAfterTouch) / Self.settle
            if k < 1 { return Pose(phi: held + (rest - held) * T.easeInOutCubic(k)) }
            guard autoPlay else { return Pose(phi: rest) }
            return Pose(phi: rest + T.phi(loopTime(e - Self.holdAfterTouch - Self.settle), loop: Self.loop))
        }
        guard autoPlay else { return Pose(phi: 0) }
        return Pose(phi: T.phi(loopTime(date.timeIntervalSince(origin)), loop: Self.loop))
    }

    private func drag(at date: Date) -> some Gesture {
        DragGesture(minimumDistance: 4)
            .onChanged { value in
                if reduceMotion { return }
                let u = SpectralCapsuleSpinGeometry.u
                if dragBase == nil {
                    dragBase = pose(at: Date()).phi
                    detent = Int(floor((dragBase! - .pi / 2) / .pi))
                    haptic.prepare()
                }
                let along = value.translation.width * u.x + value.translation.height * u.y
                let phi = (dragBase ?? 0) - along / Self.dragScale
                dragPhi = phi
                release = nil
                let now = Int(floor((phi - .pi / 2) / .pi))
                if now != detent {
                    detent = now
                    haptic.impactOccurred(intensity: 0.7)
                }
            }
            .onEnded { value in
                guard let phi = dragPhi else { return }
                let u = SpectralCapsuleSpinGeometry.u
                let along = value.velocity.width * u.x + value.velocity.height * u.y
                let velocity = min(max(-along / Self.dragScale, -14), 14)
                release = Release(phi: phi, velocity: velocity, at: Date())
                dragPhi = nil
                dragBase = nil
            }
    }
}
