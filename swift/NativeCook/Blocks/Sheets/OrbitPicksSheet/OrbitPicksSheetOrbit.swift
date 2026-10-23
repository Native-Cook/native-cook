import SwiftUI

struct OrbitPicksSheetDiscSpec {
    /// An SF Symbol name.
    var icon: String
    var color: Color
    var size: Double
    /// Distance from the badge, in points.
    var radius: Double
    /// Turns per period.
    var speed: Double
    /// Where on its orbit it starts, in radians.
    var phase: Double
}

private typealias C = OrbitPicksSheetClock

// Twelve discs, small and large, on orbits between 90 and 170 points.
let orbitPicksSheetDiscs = [
    OrbitPicksSheetDiscSpec(icon: "tennisball.fill", color: Color(hex: 0x1fc760), size: 40, radius: 150, speed: 1, phase: 0.4),
    OrbitPicksSheetDiscSpec(icon: "cpu", color: Color(hex: 0xf2994a), size: 32, radius: 170, speed: 0.8, phase: 1.5),
    OrbitPicksSheetDiscSpec(icon: "car.fill", color: Color(hex: 0xe5384a), size: 44, radius: 110, speed: 1.2, phase: 2.6),
    OrbitPicksSheetDiscSpec(icon: "pawprint.fill", color: Color(hex: 0x1fc760), size: 26, radius: 100, speed: 1.1, phase: 3.7),
    OrbitPicksSheetDiscSpec(icon: "gamecontroller.fill", color: Color(hex: 0x3b82f6), size: 22, radius: 135, speed: 0.9, phase: 4.8),
    OrbitPicksSheetDiscSpec(icon: "music.note", color: Color(hex: 0xe5384a), size: 24, radius: 160, speed: 1.3, phase: 5.6),
    OrbitPicksSheetDiscSpec(icon: "sparkles", color: Color(hex: 0xf5c518), size: 38, radius: 120, speed: 1, phase: 1.0),
    OrbitPicksSheetDiscSpec(icon: "bicycle", color: Color(hex: 0x26c6da), size: 24, radius: 170, speed: 0.85, phase: 2.2),
    OrbitPicksSheetDiscSpec(icon: "fork.knife", color: Color(hex: 0xf2994a), size: 30, radius: 95, speed: 1.25, phase: 3.1),
    OrbitPicksSheetDiscSpec(icon: "heart.fill", color: Color(hex: 0xf2547d), size: 26, radius: 145, speed: 1.15, phase: 4.2),
    OrbitPicksSheetDiscSpec(icon: "globe.europe.africa.fill", color: Color(hex: 0x26c6da), size: 34, radius: 165, speed: 0.95, phase: 5.2),
    OrbitPicksSheetDiscSpec(icon: "football.fill", color: Color(hex: 0xf5c518), size: 28, radius: 125, speed: 1.05, phase: 0.1),
]

// The orbits are ellipses, wider than tall.
private let squash = 0.85
private let turn = Double.pi * 2
private let pulse = 0.08
private let spiralIn = 1.4
private let far = 2.4

/// One icon disc: it flies in from outside along a spiral, circles the badge, and is pulled into it.
struct OrbitPicksSheetDisc: View {
    let spec: OrbitPicksSheetDiscSpec
    let index: Int
    /// Seconds since the tap.
    let time: Double
    let cx: Double
    let cy: Double

    var body: some View {
        let inAt = C.discIn + Double(index) * C.discStagger
        let fly = C.easeOut(C.ramp(time, inAt, inAt + C.discFly))
        let suckAt = C.suckAt + Double(index) * C.suckStagger
        let suck = C.easeIn(C.ramp(time, suckAt, suckAt + C.suckFor))
        let orbit = max(0, time - inAt)
        let angle = spec.phase + spec.speed * turn * orbit / C.period + (1 - fly) * spiralIn + suck * .pi
        let radius = spec.radius * (1 + far * (1 - fly)) * (1 - suck)
        let scale = (0.3 + 0.7 * fly) * (1 + pulse * sin(orbit * 2.4 + spec.phase)) * (1 - suck)
        Image(systemName: spec.icon)
            .font(.system(size: spec.size * 0.46))
            .foregroundStyle(.white)
            .frame(width: spec.size, height: spec.size)
            .background(spec.color, in: Circle())
            .overlay(Circle().strokeBorder(.white.opacity(0.85), lineWidth: 2.5))
            .compositingGroup()
            .shadow(color: spec.color.opacity(0.35), radius: 6, y: 3)
            .scaleEffect(max(scale, 0.001))
            .opacity(C.ramp(time, inAt, inAt + 0.12) * (suck < 1 ? 1 : 0))
            .position(x: cx + cos(angle) * radius, y: cy + sin(angle) * radius * squash)
    }
}

/// The arc around the badge: it draws itself, keeps turning, and fades when the discs are pulled in.
struct OrbitPicksSheetRing: View {
    let time: Double
    let cx: Double
    let cy: Double

    private static let size = 104.0
    private static let stroke = 5.0
    /// Three quarters of the circle are drawn.
    private static let arc = 0.75
    /// Degrees a second.
    private static let spin = 360 / C.period

    var body: some View {
        let draw = C.easeOut(C.ramp(time, C.ringIn, C.ringIn + C.ringDraw))
        Circle()
            .trim(from: 0, to: Self.arc * draw)
            .stroke(OrbitPicksSheetPalette.indigo, style: StrokeStyle(lineWidth: Self.stroke, lineCap: .round))
            .frame(width: Self.size - Self.stroke, height: Self.size - Self.stroke)
            .rotationEffect(.degrees(max(0, time - C.ringIn) * Self.spin))
            .opacity(C.ramp(time, C.ringIn, C.ringIn + 0.2) * (1 - C.ramp(time, C.ringOut, C.ringOut + 0.5)))
            .position(x: cx, y: cy)
            .allowsHitTesting(false)
    }
}
