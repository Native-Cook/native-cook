import SwiftUI

/// An 11 pt square with a left and a bottom border of 2, turned 45 degrees, nudged 4 pt right of
/// centre (the back button).
struct MoodOrbJarChevron: View {
    var body: some View {
        Path { path in
            // The borders sit inside the square, so the strokes run along its inset edges.
            path.move(to: CGPoint(x: 1, y: 0))
            path.addLine(to: CGPoint(x: 1, y: 10))
            path.addLine(to: CGPoint(x: 11, y: 10))
        }
        .stroke(
            MoodOrbJarTokens.Palette.ink,
            style: StrokeStyle(lineWidth: 2, lineCap: .butt, lineJoin: .miter)
        )
        .frame(width: 11, height: 11)
        .rotationEffect(.degrees(45))
        .padding(.leading, 4)
    }
}
