import SwiftUI

/// One to-do. Its blue band grows out of a selected neighbour (scaleY from the shared edge) and rounds
/// only the corners at the ends of a run, so contiguous rows read as one block that stretches and shrinks.
struct DragMultiSelectRowView: View {
    let row: DragMultiSelectRow
    let engine: DragMultiSelectEngine

    private typealias Layout = DragMultiSelectLayout
    private typealias Palette = DragMultiSelectColor
    private typealias Space = DragMultiSelectSpace
    private typealias Component = DragMultiSelectTokens.Component
    private static let glossWidth = Component.glossWidth
    /// The width the gloss travels across, as measured on the source's rows.
    private static let glossTravel = Component.rowWidth

    var body: some View {
        let state = engine.state
        let width = state.width
        // Anticipation: while a finger holds the row, a band grows in from the left.
        let held = state.pressRow == row.liveIndex ? engine.pressP.value : 0
        ZStack(alignment: .topLeading) {
            if held > 0 { preview(width: width, held: held) }
            band(width: width)
            checkbox
            title(width: width, selecting: state.isSelecting)
            circle(width: width)
        }
        .frame(width: width, height: Layout.row, alignment: .topLeading)
        .scaleEffect(1 - Component.leanScale * held)
        .opacity(row.appear.value)
        .offset(x: row.x.value, y: row.y.value + row.lift.value)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(row.task.title)
        .accessibilityAddTraits(row.isSelected ? .isSelected : [])
        .accessibilityHint(state.isSelecting ? "Double tap to toggle" : "Double tap to select")
        .accessibilityAction { engine.selectFromAccessibility(row) }
    }

    // MARK: Pieces

    private func preview(width: Double, held: Double) -> some View {
        RoundedRectangle(cornerRadius: Layout.bandRadius, style: .circular)
            .fill(Palette.band)
            .frame(width: width - 2 * Layout.bandInset, height: Layout.row)
            .scaleEffect(x: Component.previewScale + (1 - Component.previewScale) * held, y: 1, anchor: .leading)
            .opacity(Component.previewOpacity * held)
            .offset(x: Layout.bandInset)
    }

    @ViewBuilder
    private func band(width: Double) -> some View {
        let progress = row.band.value
        if progress > 0.0001 {
            let reduced = engine.reduceMotion
            let top = row.topRadius.value
            let bottom = row.bottomRadius.value
            // Never from scale(0).
            let scale = reduced ? 1 : Component.bandMinimumScale + Component.bandProgressScale * min(max(progress, 0), Component.bandOvershootLimit)
            let shape = UnevenRoundedRectangle(
                topLeadingRadius: top,
                bottomLeadingRadius: bottom,
                bottomTrailingRadius: bottom,
                topTrailingRadius: top,
                style: .circular
            )
            shape
                .fill(Palette.band)
                .overlay(alignment: .top) {
                    if row.seam {
                        Rectangle().fill(Palette.bandSeam).frame(height: Component.seamHeight)
                    }
                }
                .overlay { gloss(bandWidth: width - 2 * Layout.bandInset) }
                .clipShape(shape)
                .frame(width: width - 2 * Layout.bandInset, height: Layout.row)
                .scaleEffect(x: 1, y: scale)
                .offset(y: row.origin * -(1 - scale) * (Layout.row / 2))
                .compositingGroup()
                .opacity(reduced ? progress : min(1, progress / Component.bandMinimumScale / 2))
                .offset(x: Layout.bandInset)
        }
    }

    /// A skewed light sweeps across the band as its row joins.
    @ViewBuilder
    private func gloss(bandWidth: Double) -> some View {
        let sweep = row.gloss.value
        if sweep < 1 && !engine.reduceMotion {
            Canvas { context, _ in
                let skew = tan(Component.glossSkew * Double.pi / 180)
                let shift = -Self.glossWidth + (Self.glossTravel + Self.glossWidth) * sweep
                let middle = Layout.row / 2
                // The gloss box skewed about its centre, then moved along.
                func place(_ x: Double, _ y: Double) -> CGPoint {
                    CGPoint(x: x + skew * (y - middle) + shift, y: y)
                }
                var path = Path()
                path.move(to: place(0, 0))
                path.addLine(to: place(Self.glossWidth, 0))
                path.addLine(to: place(Self.glossWidth, Layout.row))
                path.addLine(to: place(0, Layout.row))
                path.closeSubpath()
                // The gradient runs along the box's own x axis, which the skew slants.
                let reach = Self.glossWidth / (1 + skew * skew)
                let start = CGPoint(x: shift, y: middle)
                let end = CGPoint(x: shift + reach, y: middle - skew * reach)
                context.fill(
                    path,
                    with: .linearGradient(
                        Gradient(colors: Palette.gloss),
                        startPoint: start,
                        endPoint: end
                    )
                )
            }
            .frame(width: bandWidth, height: Layout.row)
            .opacity(sin(Double.pi * sweep))
            .allowsHitTesting(false)
        }
    }

    private var checkbox: some View {
        RoundedRectangle(cornerRadius: Layout.checkRadius, style: .circular)
            .strokeBorder(Palette.check, style: StrokeStyle(lineWidth: Component.checkBorder, dash: [Component.checkDash, Component.checkDash]))
            .frame(width: Layout.check, height: Layout.check)
            .offset(x: Layout.gutter, y: (Layout.row - Layout.check) / 2)
    }

    private func title(width: Double, selecting: Bool) -> some View {
        HStack(spacing: 0) {
            Text(row.task.title)
                .font(DragMultiSelectType.row)
                .foregroundStyle(Palette.ink)
                .lineLimit(1)
                .frame(height: DragMultiSelectType.rowLineHeight)
            if row.task.note {
                DragMultiSelectIcon(kind: .fileText, size: Component.noteIcon, color: Palette.note)
                    .padding(.leading, Component.noteGap)
            }
        }
        .padding(.trailing, selecting ? Layout.circleColumn - Space.sm : Layout.gutter)
        .frame(width: width - Layout.textLeft, height: Layout.row, alignment: .leading)
        .offset(x: Layout.textLeft)
    }

    private func circle(width: Double) -> some View {
        let appearing = row.circleIn.value
        let ring = row.ring.value
        let picked = row.band.value
        let reduced = engine.reduceMotion
        return ZStack {
            Circle().fill(Palette.white)
            Circle().strokeBorder(Palette.circle, lineWidth: Component.circleBorder)
            if ring < 1 {
                Circle()
                    .strokeBorder(Palette.blue, lineWidth: Component.rippleBorder)
                    .opacity(Component.rippleOpacity * (1 - ring))
                    .scaleEffect(1 + Component.ringScale * ring)
            }
            if picked > 0 {
                // Selected: blue with a white ring inside, as in the source.
                ZStack {
                    Circle().fill(Palette.blue)
                    Circle().strokeBorder(Palette.white, lineWidth: Component.selectedCircleBorder).padding(Component.selectedCircleInset)
                }
                .opacity(min(max(picked, 0), 1))
                .scaleEffect(reduced ? 1 : Component.selectedCircleMinimumScale + Component.selectedCircleProgressScale * min(picked, 1))
            }
        }
        .frame(width: Layout.circle, height: Layout.circle)
        .opacity(min(appearing, 1))
        .offset(x: reduced ? 0 : (1 - appearing) * DragMultiSelectTiming.circleSlide)
        .offset(x: width - Layout.circleRight - Layout.bandInset - Layout.circle, y: (Layout.row - Layout.circle) / 2)
        .accessibilityHidden(true)
    }
}
