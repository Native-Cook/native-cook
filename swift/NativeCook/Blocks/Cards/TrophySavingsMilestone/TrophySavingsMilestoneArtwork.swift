import SwiftUI

/// The reference's flat, faceted trophy, in its original 176 × 214 artwork frame.
struct TrophySavingsMilestoneArtwork: View {
    enum Part: Hashable {
        case all, bowl, lip, stem, base
    }

    var part: Part = .all

    var body: some View {
        Canvas { context, _ in
            drawArtwork(in: &context, part: part)
        }
        .frame(width: 176, height: 214)
        .accessibilityHidden(true)
    }

    private func drawArtwork(in context: inout GraphicsContext, part: Part) {
        let handleGold = Color(red: 1, green: 179.0 / 255, blue: 28.0 / 255)
        let bodyGold = Color(red: 1, green: 226.0 / 255, blue: 102.0 / 255)
        let sideGold = Color(red: 1, green: 214.0 / 255, blue: 50.0 / 255)
        let paleGold = Color(red: 1, green: 240.0 / 255, blue: 171.0 / 255)
        let stemGold = Color(red: 1, green: 204.0 / 255, blue: 69.0 / 255)

        if part == .all || part == .bowl {
            var leftHandle = Path()
            leftHandle.move(to: CGPoint(x: 29.5, y: 27))
            leftHandle.addLine(to: CGPoint(x: 3.5, y: 27))
            leftHandle.addCurve(to: CGPoint(x: 7.3, y: 53), control1: CGPoint(x: 3.3, y: 35), control2: CGPoint(x: 4, y: 44))
            leftHandle.addCurve(to: CGPoint(x: 48.5, y: 88), control1: CGPoint(x: 14.5, y: 70), control2: CGPoint(x: 31.5, y: 81))
            context.stroke(leftHandle, with: .color(handleGold), style: StrokeStyle(lineWidth: 6.5, lineCap: .round, lineJoin: .round))

            var rightHandle = Path()
            rightHandle.move(to: CGPoint(x: 146.5, y: 27))
            rightHandle.addLine(to: CGPoint(x: 172.5, y: 27))
            rightHandle.addCurve(to: CGPoint(x: 168.7, y: 53), control1: CGPoint(x: 172.7, y: 35), control2: CGPoint(x: 172, y: 44))
            rightHandle.addCurve(to: CGPoint(x: 127.5, y: 88), control1: CGPoint(x: 161.5, y: 70), control2: CGPoint(x: 144.5, y: 81))
            context.stroke(rightHandle, with: .color(handleGold), style: StrokeStyle(lineWidth: 6.5, lineCap: .round, lineJoin: .round))

            // This same outline is used by the streamer's bowl-contact clip.
            var bowl = Path()
            bowl.move(to: CGPoint(x: 31, y: 24))
            bowl.addCurve(to: CGPoint(x: 144, y: 24), control1: CGPoint(x: 58, y: 9), control2: CGPoint(x: 118, y: 9))
            bowl.addCurve(to: CGPoint(x: 106, y: 112), control1: CGPoint(x: 144, y: 61), control2: CGPoint(x: 132, y: 90))
            bowl.addCurve(to: CGPoint(x: 87.5, y: 123), control1: CGPoint(x: 100, y: 119), control2: CGPoint(x: 94, y: 122))
            bowl.addCurve(to: CGPoint(x: 70, y: 115), control1: CGPoint(x: 81, y: 122), control2: CGPoint(x: 75, y: 120))
            bowl.addCurve(to: CGPoint(x: 31, y: 24), control1: CGPoint(x: 42, y: 94), control2: CGPoint(x: 32, y: 60))
            bowl.closeSubpath()
            context.fill(bowl, with: .color(bodyGold))

            var facets = context
            facets.clip(to: bowl)
            var leftFacet = Path()
            leftFacet.move(to: CGPoint(x: 51, y: 15))
            leftFacet.addCurve(to: CGPoint(x: 64, y: 96), control1: CGPoint(x: 49, y: 55), control2: CGPoint(x: 57, y: 82))
            leftFacet.addCurve(to: CGPoint(x: 77, y: 118), control1: CGPoint(x: 68, y: 105), control2: CGPoint(x: 73, y: 113))
            leftFacet.addCurve(to: CGPoint(x: 66, y: 14), control1: CGPoint(x: 68, y: 99), control2: CGPoint(x: 64, y: 58))
            leftFacet.closeSubpath()
            facets.fill(leftFacet, with: .color(paleGold))

            var rightFacet = Path()
            rightFacet.move(to: CGPoint(x: 135.5, y: 20.5))
            rightFacet.addLine(to: CGPoint(x: 144, y: 24))
            rightFacet.addCurve(to: CGPoint(x: 106, y: 112), control1: CGPoint(x: 144, y: 61), control2: CGPoint(x: 132, y: 90))
            rightFacet.addCurve(to: CGPoint(x: 87.5, y: 123), control1: CGPoint(x: 100, y: 119), control2: CGPoint(x: 94, y: 122))
            rightFacet.addCurve(to: CGPoint(x: 128, y: 70), control1: CGPoint(x: 101, y: 114), control2: CGPoint(x: 119, y: 94))
            rightFacet.addCurve(to: CGPoint(x: 135.5, y: 20.5), control1: CGPoint(x: 133, y: 51), control2: CGPoint(x: 135, y: 35))
            rightFacet.closeSubpath()
            facets.fill(rightFacet, with: .color(sideGold))
        }

        if part == .all || part == .lip {
            var lip = Path()
            lip.move(to: CGPoint(x: 29.5, y: 12.5))
            lip.addCurve(to: CGPoint(x: 87.5, y: 0.2), control1: CGPoint(x: 29.5, y: 5.7), control2: CGPoint(x: 55.5, y: 0.2))
            lip.addCurve(to: CGPoint(x: 146.5, y: 12.5), control1: CGPoint(x: 120.1, y: 0.2), control2: CGPoint(x: 146.5, y: 5.7))
            lip.addLine(to: CGPoint(x: 145, y: 24))
            lip.addCurve(to: CGPoint(x: 30.5, y: 24), control1: CGPoint(x: 118, y: 9), control2: CGPoint(x: 58, y: 9))
            lip.closeSubpath()
            context.fill(lip, with: .linearGradient(Gradient(stops: [
                .init(color: Color(red: 1, green: 211.0 / 255, blue: 69.0 / 255), location: 0),
                .init(color: Color(red: 1, green: 205.0 / 255, blue: 65.0 / 255), location: 0.55),
                .init(color: Color(red: 1, green: 207.0 / 255, blue: 67.0 / 255), location: 1),
            ]), startPoint: CGPoint(x: 29.5, y: 8), endPoint: CGPoint(x: 146.5, y: 8)))

            var lipFacet = Path()
            lipFacet.move(to: CGPoint(x: 51, y: 0))
            lipFacet.addLine(to: CGPoint(x: 67, y: 0))
            lipFacet.addLine(to: CGPoint(x: 66, y: 23))
            lipFacet.addLine(to: CGPoint(x: 50, y: 24))
            lipFacet.closeSubpath()
            var lipHighlight = context
            lipHighlight.clip(to: lip)
            lipHighlight.fill(lipFacet, with: .color(.white.opacity(0.45)))

            var lowerEdge = Path()
            lowerEdge.move(to: CGPoint(x: 30.5, y: 24))
            lowerEdge.addCurve(to: CGPoint(x: 145, y: 24), control1: CGPoint(x: 58, y: 9), control2: CGPoint(x: 118, y: 9))
            context.stroke(lowerEdge, with: .color(Color(red: 242.0 / 255, green: 182.0 / 255, blue: 47.0 / 255)), lineWidth: 1.1)
        }

        if part == .all || part == .bowl {
            var sparkle = Path()
            sparkle.move(to: CGPoint(x: 66, y: 26))
            sparkle.addLine(to: CGPoint(x: 68.5, y: 31.5))
            sparkle.addLine(to: CGPoint(x: 75.5, y: 34))
            sparkle.addLine(to: CGPoint(x: 68.5, y: 36.5))
            sparkle.addLine(to: CGPoint(x: 66, y: 42))
            sparkle.addLine(to: CGPoint(x: 63.5, y: 36.5))
            sparkle.addLine(to: CGPoint(x: 56.5, y: 34))
            sparkle.addLine(to: CGPoint(x: 63.5, y: 31.5))
            sparkle.closeSubpath()
            context.fill(sparkle, with: .color(.white.opacity(0.88)))
        }

        if part == .all || part == .stem {
            var stem = Path()
            stem.move(to: CGPoint(x: 81, y: 126))
            stem.addCurve(to: CGPoint(x: 95.5, y: 126), control1: CGPoint(x: 84, y: 127), control2: CGPoint(x: 92, y: 127))
            stem.addLine(to: CGPoint(x: 95.5, y: 141))
            stem.addCurve(to: CGPoint(x: 118.5, y: 161), control1: CGPoint(x: 96.5, y: 154), control2: CGPoint(x: 108, y: 159))
            stem.addLine(to: CGPoint(x: 118.5, y: 165))
            stem.addCurve(to: CGPoint(x: 57, y: 165), control1: CGPoint(x: 103, y: 160), control2: CGPoint(x: 72, y: 160))
            stem.addLine(to: CGPoint(x: 57, y: 161))
            stem.addCurve(to: CGPoint(x: 81, y: 141), control1: CGPoint(x: 68, y: 159), control2: CGPoint(x: 81, y: 153))
            stem.closeSubpath()
            context.fill(stem, with: .linearGradient(Gradient(colors: [
                Color(red: 1, green: 207.0 / 255, blue: 75.0 / 255), stemGold,
            ]), startPoint: CGPoint(x: 88, y: 126), endPoint: CGPoint(x: 88, y: 165)))

            let collar = Path(roundedRect: CGRect(x: 71.5, y: 118.5, width: 32, height: 9.5), cornerSize: CGSize(width: 3, height: 3))
            context.fill(collar, with: .color(Color(red: 243.0 / 255, green: 172.0 / 255, blue: 44.0 / 255)))
            let collarFace = Path(roundedRect: CGRect(x: 73, y: 119.5, width: 29, height: 6.5), cornerSize: CGSize(width: 2, height: 2))
            context.fill(collarFace, with: .color(Color(red: 1, green: 210.0 / 255, blue: 75.0 / 255)))
            let collarHighlight = Path(roundedRect: CGRect(x: 73.5, y: 120, width: 28, height: 2), cornerSize: CGSize(width: 1, height: 1))
            context.fill(collarHighlight, with: .color(Color(red: 1, green: 228.0 / 255, blue: 119.0 / 255)))

            var foot = Path()
            foot.move(to: CGPoint(x: 50.5, y: 166))
            foot.addCurve(to: CGPoint(x: 58.5, y: 160.5), control1: CGPoint(x: 52.5, y: 163), control2: CGPoint(x: 54.5, y: 161.5))
            foot.addCurve(to: CGPoint(x: 117, y: 160.5), control1: CGPoint(x: 75, y: 158.5), control2: CGPoint(x: 100, y: 158.5))
            foot.addCurve(to: CGPoint(x: 124.5, y: 166), control1: CGPoint(x: 121, y: 161.5), control2: CGPoint(x: 123.5, y: 163))
            foot.addLine(to: CGPoint(x: 124, y: 171))
            foot.addLine(to: CGPoint(x: 50.5, y: 171))
            foot.closeSubpath()
            context.fill(foot, with: .linearGradient(Gradient(colors: [
                Color(red: 1, green: 210.0 / 255, blue: 77.0 / 255), stemGold,
            ]), startPoint: CGPoint(x: 88, y: 159), endPoint: CGPoint(x: 88, y: 171)))
        }

        if part == .all || part == .base {
            let base = Path(roundedRect: CGRect(x: 43, y: 170, width: 91, height: 44), cornerSize: CGSize(width: 5, height: 5))
            context.fill(base, with: .linearGradient(Gradient(stops: [
                .init(color: Color(red: 111.0 / 255, green: 87.0 / 255, blue: 80.0 / 255), location: 0),
                .init(color: Color(red: 108.0 / 255, green: 85.0 / 255, blue: 79.0 / 255), location: 0.25),
                .init(color: Color(red: 108.0 / 255, green: 85.0 / 255, blue: 79.0 / 255), location: 0.8),
                .init(color: Color(red: 105.0 / 255, green: 83.0 / 255, blue: 77.0 / 255), location: 1),
            ]), startPoint: CGPoint(x: 88, y: 170), endPoint: CGPoint(x: 88, y: 214)))
            var baseEdge = context
            baseEdge.clip(to: base)
            baseEdge.fill(Path(CGRect(x: 43, y: 170, width: 1.5, height: 44)), with: .color(Color(red: 99.0 / 255, green: 81.0 / 255, blue: 73.0 / 255)))

            var broadReflection = Path()
            broadReflection.move(to: CGPoint(x: 85, y: 170))
            broadReflection.addLine(to: CGPoint(x: 100, y: 170))
            broadReflection.addLine(to: CGPoint(x: 66, y: 214))
            broadReflection.addLine(to: CGPoint(x: 51, y: 214))
            broadReflection.closeSubpath()
            var narrowReflection = Path()
            narrowReflection.move(to: CGPoint(x: 114, y: 170))
            narrowReflection.addLine(to: CGPoint(x: 121, y: 170))
            narrowReflection.addLine(to: CGPoint(x: 87, y: 214))
            narrowReflection.addLine(to: CGPoint(x: 80, y: 214))
            narrowReflection.closeSubpath()
            var baseReflection = context
            baseReflection.clip(to: base)
            baseReflection.fill(broadReflection, with: .color(Color(red: 1, green: 246.0 / 255, blue: 238.0 / 255).opacity(0.35)))
            baseReflection.fill(narrowReflection, with: .color(Color(red: 222.0 / 255, green: 209.0 / 255, blue: 207.0 / 255).opacity(0.45)))

            // The plaque remains blank; the same two reflections cross its gold face.
            let plaque = Path(roundedRect: CGRect(x: 59, y: 177.5, width: 58, height: 23), cornerSize: CGSize(width: 2, height: 2))
            context.fill(plaque, with: .linearGradient(Gradient(colors: [
                Color(red: 1, green: 175.0 / 255, blue: 40.0 / 255),
                Color(red: 1, green: 173.0 / 255, blue: 39.0 / 255),
            ]), startPoint: CGPoint(x: 88, y: 177.5), endPoint: CGPoint(x: 88, y: 200.5)))
            var plaqueReflection = context
            plaqueReflection.clip(to: plaque)
            plaqueReflection.fill(broadReflection, with: .color(Color(red: 1, green: 1, blue: 155.0 / 255).opacity(0.7)))
            plaqueReflection.fill(narrowReflection, with: .color(Color(red: 1, green: 233.0 / 255, blue: 196.0 / 255).opacity(0.45)))
        }
    }
}
