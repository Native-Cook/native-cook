import SwiftUI

/// A 12 pt square with a left and a bottom border of 2.2, turned 45 degrees, nudged 4 pt right of
/// centre (the back button).
struct MoodOrbJarChevron: View {
    var body: some View {
        Path { path in
            // The borders sit inside the square, so the strokes run along its inset edges.
            path.move(to: CGPoint(x: 1.1, y: 0))
            path.addLine(to: CGPoint(x: 1.1, y: 10.9))
            path.addLine(to: CGPoint(x: 12, y: 10.9))
        }
        .stroke(
            MoodOrbJarTokens.Palette.ink,
            style: StrokeStyle(lineWidth: 2.2, lineCap: .butt, lineJoin: .miter)
        )
        .frame(width: 12, height: 12)
        .rotationEffect(.degrees(45))
        .padding(.leading, 4)
    }
}
