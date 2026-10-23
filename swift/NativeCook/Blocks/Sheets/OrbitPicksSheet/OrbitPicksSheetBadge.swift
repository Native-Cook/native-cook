import SwiftUI

enum OrbitPicksSheetPalette {
    static let indigo = Color(hex: 0x5b3fe0)
}

/// An eight-point rosette in a 64 × 64 box: the outer and inner radius alternate.
private struct OrbitPicksSheetRosette: Shape {
    func path(in rect: CGRect) -> Path {
        let points = 16
        var path = Path()
        for i in 0..<points {
            let radius = i % 2 == 0 ? 31.0 : 26.0
            let angle = Double(i) / Double(points) * .pi * 2 - .pi / 2
            let point = CGPoint(x: 32 + cos(angle) * radius, y: 32 + sin(angle) * radius)
            if i == 0 { path.move(to: point) } else { path.addLine(to: point) }
        }
        path.closeSubpath()
        return path.applying(CGAffineTransform(scaleX: rect.width / 64, y: rect.height / 64))
    }
}

/// The indigo rosette with a white bolt that stands for the picks.
struct OrbitPicksSheetBadge: View {
    let size: Double

    var body: some View {
        ZStack {
            OrbitPicksSheetRosette()
                .fill(OrbitPicksSheetPalette.indigo)
                .overlay(OrbitPicksSheetRosette().stroke(OrbitPicksSheetPalette.indigo, style: StrokeStyle(lineWidth: 2 * size / 64, lineJoin: .round)))
            Image(systemName: "bolt.fill")
                .font(.system(size: size * 0.36))
                .foregroundStyle(.white)
        }
        .frame(width: size, height: size)
    }
}
