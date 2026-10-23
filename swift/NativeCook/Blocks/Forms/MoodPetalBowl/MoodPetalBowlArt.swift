import SwiftUI

/// Two bounded canvases sample the same opaque, depth-ordered seven-body pile.
struct MoodPetalBowlArt: View {
    let scene: MoodPetalBowlScene
    let logicalHeight: Double
    private let resources = MoodPetalBowlResources.shared

    var body: some View {
        ZStack(alignment: .topLeading) {
            MoodPetalBowlResources.color(0xF4F1EA)
            Canvas { context, _ in
                context.draw(resources.paperImage, in: CGRect(x: 0, y: 0, width: 393, height: 1024))
                context.draw(resources.headerImage, in: CGRect(x: 0, y: 0, width: 393, height: 128))
                var guide = Path(); guide.move(to: CGPoint(x: 0, y: 682)); guide.addLine(to: CGPoint(x: 393, y: 682))
                context.stroke(guide, with: .color(MoodPetalBowlResources.color(0x827F77, alpha: 0.18)),
                               style: StrokeStyle(lineWidth: 0.7, dash: [3, 4]))
                var trend = Path(); trend.move(to: CGPoint(x: 0, y: 686))
                trend.addCurve(to: CGPoint(x: 43, y: 660), control1: CGPoint(x: 18, y: 666), control2: CGPoint(x: 28, y: 644))
                trend.addCurve(to: CGPoint(x: 104, y: 712), control1: CGPoint(x: 58, y: 676), control2: CGPoint(x: 80, y: 712))
                trend.addLine(to: CGPoint(x: 393, y: 712))
                context.stroke(trend, with: .color(MoodPetalBowlResources.color(0x191A17)), lineWidth: 1.2)
            }
            .frame(width: 393, height: logicalHeight, alignment: .topLeading)

            bowlTint
                .frame(width: 375, height: 345, alignment: .topLeading)
                .offset(x: 9, y: 111)

            ZStack(alignment: .topLeading) {
                pile
                    .mask(MoodPetalBowlMaterialMask(soft: false, resources: resources))
                pile
                    .blur(radius: 8.5, opaque: false)
                    .saturation(0.95)
                    .distortionEffect(ShaderLibrary.moodPetalBowlRefraction(.float(111)),
                                      maxSampleOffset: CGSize(width: 1.6, height: 0))
                    .mask(MoodPetalBowlMaterialMask(soft: true, resources: resources))
                    .opacity(0.86)
                    .blendMode(.plusLighter)
            }
            // Add the premultiplied weighted passes in isolation; cream and tint
            // sit beneath that sum and are never included in its additive group.
            .compositingGroup()
            .frame(width: 375, height: 345, alignment: .topLeading)
            .offset(x: -26, y: -26)
            .frame(width: 323, height: 293, alignment: .topLeading)
            .clipped()
            .offset(x: 35, y: 137)

            bowlHighlights
                .frame(width: 375, height: 345, alignment: .topLeading)
                .offset(x: 9, y: 111)
        }
        .frame(width: 393, height: logicalHeight, alignment: .topLeading)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    private var pile: some View {
        Canvas(opaque: false, colorMode: .nonLinear, rendersAsynchronously: false) { context, _ in
            for id in MoodPetalBowlGeometry.painterOrder {
                let token = scene.tokens[id]
                if token.alpha <= 0 { continue }
                var body = context
                body.opacity = token.alpha
                body.translateBy(x: token.center.x - 9, y: token.center.y - 111)
                body.rotate(by: .degrees(token.angle))
                let side = MoodPetalBowlResources.spriteSide * MoodPetalBowlGeometry.radius(id: id, mood: token.mood) / MoodPetalBowlResources.spriteRadius
                body.draw(resources.tokenImages[token.mood.rawValue],
                          in: CGRect(x: -side / 2, y: -side / 2, width: side, height: side))
            }
        }
        .frame(width: 375, height: 345, alignment: .topLeading)
    }

    private var bowlTint: some View {
        MoodPetalBowlResources.color(0xE9E0D4, alpha: 0.44)
            .mask(MoodPetalBowlMaterialMask(soft: true, resources: resources))
    }

    private var bowlHighlights: some View {
        Canvas { context, _ in
            var lower = Path(); lower.move(to: CGPoint(x: 34.5, y: 162))
            lower.addCurve(to: CGPoint(x: 187.5, y: 315), control1: CGPoint(x: 34.5, y: 246.499), control2: CGPoint(x: 103.001, y: 315))
            lower.addCurve(to: CGPoint(x: 340.5, y: 162), control1: CGPoint(x: 271.999, y: 315), control2: CGPoint(x: 340.5, y: 246.499))
            context.stroke(lower, with: .color(MoodPetalBowlResources.color(0xFCF8F0, alpha: 0.30)), lineWidth: 0.8)
            var lip = Path(); lip.move(to: CGPoint(x: 34.5, y: 162)); lip.addLine(to: CGPoint(x: 340.5, y: 162))
            var diffused = context; diffused.addFilter(.blur(radius: 0.6))
            diffused.stroke(lip, with: .color(MoodPetalBowlResources.color(0xFCF8F0, alpha: 0.40)), lineWidth: 1.2)
        }
    }
}

private struct MoodPetalBowlMaterialMask: View {
    let soft: Bool
    let resources: MoodPetalBowlResources
    var body: some View {
        Canvas { context, _ in
            context.clip(to: soft ? resources.bowlDisk : resources.sharpVisibility)
            context.draw(soft ? resources.softStripImage : resources.sharpStripImage,
                         in: CGRect(x: 0, y: 0, width: 375, height: 345))
        }
        .frame(width: 375, height: 345, alignment: .topLeading)
    }
}
