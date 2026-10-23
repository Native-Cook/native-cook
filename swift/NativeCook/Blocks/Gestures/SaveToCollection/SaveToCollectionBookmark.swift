import SwiftUI

struct SaveToCollectionBookmarkView: View {
    @ObservedObject var model: SaveToCollectionModel
    let layout: SaveToCollectionLayout
    @Environment(\.displayScale) private var displayScale

    var body: some View {
        let rise = model.riseProgress()
        let bounce = model.bounceProgress()
        let open = model.openProgress
        let press = model.bookmarkPress
        let offsetY = (layout.riseY - layout.bookmark.y) * rise
        let centre = CGPoint(x: layout.bookmark.x, y: layout.bookmark.y + offsetY)
        let glyphScale = (model.reduceMotion ? 1 : 1 + SaveToCollectionTokens.Bookmark.openScale * open + SaveToCollectionTokens.Bookmark.bounceScale * bounce) *
            (1 + SaveToCollectionTokens.Bookmark.riseScale * rise) *
            (1 - SaveToCollectionTokens.Bookmark.pressScaleLoss * press)

        ZStack(alignment: .topLeading) {
            Circle()
                .fill(SaveToCollectionTokens.Palette.glow)
                .frame(width: SaveToCollectionTokens.Bookmark.halo, height: SaveToCollectionTokens.Bookmark.halo)
                .scaleEffect(
                    SaveToCollectionTokens.Bookmark.ringStartScale +
                        SaveToCollectionTokens.Bookmark.haloScaleTravel * open
                )
                .opacity(min(open + bounce, 1))
                .position(centre)

            if let ring = model.ringProgress() {
                Circle()
                    .stroke(SaveToCollectionTokens.Palette.ink, lineWidth: SaveToCollectionTokens.Bookmark.ringLineWidth)
                    .frame(width: SaveToCollectionTokens.Bookmark.ring, height: SaveToCollectionTokens.Bookmark.ring)
                    .scaleEffect(SaveToCollectionTokens.Bookmark.ringStartScale + SaveToCollectionTokens.Bookmark.ringScaleTravel * ring)
                    .opacity(SaveToCollectionTokens.Bookmark.ringOpacity * (1 - ring))
                    .position(centre)
            }

            if !SaveToCollectionTokens.replicaOnly {
                let entrance = model.countEntrance()
                Text("\(model.pileCount) saved")
                    .font(.system(size: SaveToCollectionTokens.Typography.countSize, weight: SaveToCollectionTokens.Typography.countWeight))
                    .tracking(SaveToCollectionTokens.Typography.countTracking)
                    .monospacedDigit()
                    .foregroundStyle(SaveToCollectionTokens.Palette.ink)
                    .frame(height: SaveToCollectionTokens.Space.xxl)
                    .padding(.horizontal, SaveToCollectionTokens.Space.l)
                    .background(.ultraThinMaterial)
                    .background(Color.white.opacity(SaveToCollectionTokens.Bookmark.pillOpacity))
                    .clipShape(Capsule())
                    .overlay(alignment: .top) {
                        Rectangle()
                            .fill(SaveToCollectionTokens.Palette.glassEdge)
                            .frame(height: SaveToCollectionTokens.Glass.edgePixels / displayScale)
                            .padding(.horizontal, SaveToCollectionTokens.Space.m)
                    }
                    .opacity(max(rise * SaveToCollectionTokens.Bookmark.countRevealMultiplier - 1, 0) * entrance)
                    .offset(y: (1 - rise) * SaveToCollectionTokens.Bookmark.countStartY)
                    .position(
                        x: centre.x,
                        y: centre.y + SaveToCollectionTokens.Layout.bookmarkHeight / 2 +
                            SaveToCollectionTokens.Space.l + SaveToCollectionTokens.Space.xxl / 2
                    )
            }

            SaveToCollectionBookmarkShape()
                .fill(SaveToCollectionTokens.Palette.ink)
                .frame(
                    width: SaveToCollectionTokens.Layout.bookmarkWidth,
                    height: SaveToCollectionTokens.Layout.bookmarkHeight
                )
                .scaleEffect(glyphScale)
                .position(centre)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("Saved, \(model.saved) items. Double tap to save every card")
                .accessibilityAddTraits(.isButton)
                .accessibilityAction {
                    model.accessibilitySaveAll()
                }
        }
    }
}

private struct SaveToCollectionBookmarkShape: Shape {
    func path(in rect: CGRect) -> Path {
        let width = rect.width
        let height = rect.height
        let radius = width * SaveToCollectionTokens.Bookmark.radiusFraction
        let notch = height * SaveToCollectionTokens.Bookmark.notchFraction
        let inset = SaveToCollectionTokens.Bookmark.sideInset
        let bottomRadius = SaveToCollectionTokens.Bookmark.bottomRadius
        var path = Path()
        path.move(to: CGPoint(x: radius, y: 0))
        path.addLine(to: CGPoint(x: width - radius, y: 0))
        path.addArc(
            center: CGPoint(x: width - radius, y: radius),
            radius: radius,
            startAngle: .degrees(-90),
            endAngle: .degrees(0),
            clockwise: false
        )
        path.addLine(to: CGPoint(x: width, y: height - bottomRadius))
        path.addQuadCurve(
            to: CGPoint(x: width - inset, y: height - SaveToCollectionTokens.Bookmark.bottomInset),
            control: CGPoint(x: width, y: height)
        )
        path.addLine(to: CGPoint(x: width / 2, y: height - notch))
        path.addLine(to: CGPoint(x: inset, y: height - SaveToCollectionTokens.Bookmark.bottomInset))
        path.addQuadCurve(
            to: CGPoint(x: 0, y: height - bottomRadius),
            control: CGPoint(x: 0, y: height)
        )
        path.addLine(to: CGPoint(x: 0, y: radius))
        path.addArc(
            center: CGPoint(x: radius, y: radius),
            radius: radius,
            startAngle: .degrees(180),
            endAngle: .degrees(270),
            clockwise: false
        )
        path.closeSubpath()
        return path
    }
}
