// Heat Haze Weather — the hour slider.
//
// Drawn rather than native, as in React Native: its thumb follows the hour every frame (the film and
// the finger's momentum both move it). A track that warms and cools along the afternoon, hour ticks,
// a thumb with a sun in it and a bubble with the time, rounded to the half hour. The autoplay's ghost
// finger sits on the thumb.

import SwiftUI

struct HeatHazeWeatherSlider: View {
    private typealias M = HeatHazeWeatherMotion

    let frame: HeatHazeWeatherClock.Frame
    let trackW: Double

    private static let mute = Color.rgba(251, 246, 238, 0.66)
    private static let bubble = 76.0
    private static let ghost = 44.0
    private static let steps: [String] = (0...Int((M.hMax - M.hMin) * 2)).map { i in
        let h = M.hMin + Double(i) / 2
        let whole = Int(floor(h))
        return "\(whole % 12 == 0 ? 12 : whole % 12):\(h - Double(whole) >= 0.5 ? "30" : "00") \(whole < 12 ? "AM" : "PM")"
    }

    var body: some View {
        let u = (frame.hour - M.hMin) / (M.hMax - M.hMin)
        VStack(spacing: 0) {
            bubbleRow(u)
            hit(u)
        }
    }

    private func bubbleRow(_ u: Double) -> some View {
        // the nearest half hour, rolling to the next one over the last part of each step
        // (held to the track's ends, so the bubble keeps 12 PM or 9 PM while the thumb is rubber-banded past them)
        let at = M.rolled((min(max(frame.hour, M.hMin), M.hMax) - M.hMin) * 2 + 0.5, 0.65)
        let x = min(max(u * trackW - Self.bubble / 2, -10), trackW - Self.bubble + 10)
        return ZStack {
            ForEach(Array(Self.steps.enumerated()), id: \.offset) { i, label in
                let d = Double(i) - at
                if abs(d) < 1 {
                    Text(label)
                        .font(.system(size: 13, weight: .bold).monospacedDigit())
                        .foregroundStyle(Color(hex: 0x2A1F1A))
                        .fixedSize()
                        .opacity(M.clamp01(1 - abs(d)))
                        .offset(y: 9 * d)
                        .accessibilityHidden(abs(d) >= 0.5)
                }
            }
        }
        .frame(width: Self.bubble, height: 26)
        .background(Capsule().fill(Color.rgba(251, 246, 238, 0.94)))
        .clipShape(Capsule())
        .shadow(color: .rgba(20, 12, 8, 0.25), radius: 1, y: 1)
        .shadow(color: .rgba(20, 12, 8, 0.18), radius: 8, y: 6)
        .offset(x: x)
        .frame(width: trackW, height: 30, alignment: .topLeading)
    }

    private func hit(_ u: Double) -> some View {
        let tx = u * trackW
        let sun: Color = M.clamp01(u) < 0.55 ? Color(hex: 0xFFC85A) : M.clamp01(u) < 0.8 ? Color(hex: 0xFF9A57) : Color(hex: 0xE8708A)
        return VStack(spacing: 2) {
            ZStack(alignment: .topLeading) {
                Capsule()
                    .fill(LinearGradient(stops: [
                        .init(color: Color(hex: 0xFFD36B), location: 0),
                        .init(color: Color(hex: 0xFF9F5A), location: 0.38),
                        .init(color: Color(hex: 0xE8708A), location: 0.72),
                        .init(color: Color(hex: 0x5D5A9E), location: 1),
                    ], startPoint: .leading, endPoint: .trailing))
                    .frame(width: trackW, height: 6)
                    .offset(y: 15)
                ForEach(0...Int(M.hMax - M.hMin), id: \.self) { i in
                    let major = i % 3 == 0
                    Rectangle()
                        .fill(Color.rgba(251, 246, 238, major ? 0.6 : 0.35))
                        .frame(width: 1, height: major ? 6 : 4)
                        .offset(x: Double(i) * (trackW - 1) / (M.hMax - M.hMin), y: 24)
                }
                Circle()
                    .fill(Color(hex: 0xFBF6EE))
                    .overlay(Circle().fill(sun).frame(width: 12, height: 12))
                    .frame(width: M.thumb, height: M.thumb)
                    .shadow(color: .rgba(20, 12, 8, 0.35), radius: 1, y: 1)
                    .shadow(color: .rgba(20, 12, 8, 0.28), radius: 9, y: 8)
                    .offset(x: tx - M.thumb / 2, y: (36 - M.thumb) / 2)
                ghostView
                    .scaleEffect(1 - 0.14 * frame.sliderGhostP)
                    .opacity(frame.sliderGhostA * 0.9)
                    .offset(x: tx - Self.ghost / 2, y: (36 - Self.ghost) / 2)
            }
            .frame(width: trackW, height: 36, alignment: .topLeading)
            HStack(spacing: 0) {
                ForEach([12, 15, 18, 21], id: \.self) { h in
                    Text("\(h % 12 == 0 ? 12 : h % 12) \(h < 12 ? "AM" : "PM")")
                        .font(.system(size: 12, weight: .medium).monospacedDigit())
                        .foregroundStyle(Self.mute)
                        .fixedSize()
                    if h != 21 { Spacer(minLength: 0) }
                }
            }
            .frame(width: trackW)
        }
    }

    private var ghostView: some View {
        Circle()
            .fill(Color.rgba(251, 246, 238, 0.16))
            .overlay(Circle().strokeBorder(Color.rgba(251, 246, 238, 0.62), lineWidth: 1.5))
            .overlay(Circle().fill(Color.rgba(251, 246, 238, 0.5)).frame(width: 14, height: 14))
            .frame(width: Self.ghost, height: Self.ghost)
    }
}
