import SwiftUI

/// What every skeleton in a group shares: the two clocks and the width the shimmer has to cross.
struct SkeletonContentResolveGroup: Equatable {
    static let space = "skeleton-content-resolve-group"
    static let bar = SkeletonContentResolveData.color(0xf5e8d6, 0.11)
    static let shine = SkeletonContentResolveData.color(0xfff0de, 0.22)
    static let shineClear = SkeletonContentResolveData.color(0xfff0de, 0)
    static let block = SkeletonContentResolveData.color(0x2e2821)

    var sinceHide: Double
    var sinceShow: Double
    var span: Double
}

/// Wraps one line of content and measures it. While loading it shows a bar of `placeholder` width
/// with the group's shimmer running through it; to resolve, the bar stretches to the child's exact
/// width, the child fades in inside it, and the bar dissolves.
struct SkeletonContentResolveLine<Content: View>: View {
    private typealias M = SkeletonContentResolveMotion

    let group: SkeletonContentResolveGroup
    let order: Double
    let placeholder: CGFloat
    let barHeight: CGFloat
    var radius: CGFloat?
    var trailing = false
    @ViewBuilder let content: Content

    @State private var measured: CGRect = .zero

    var body: some View {
        let k = M.kAt(order, group.sinceHide, group.sinceShow)
        content
            .onGeometryChange(for: CGRect.self) { $0.frame(in: .named(SkeletonContentResolveGroup.space)) } action: { measured = $0 }
            .opacity(M.contentAlpha(k))
            .background(alignment: trailing ? .trailing : .leading) {
                if k < 1 { bar(k) }
            }
    }

    private func bar(_ k: Double) -> some View {
        let contentW = measured.width > 0 ? measured.width : placeholder
        let width = max(barHeight, placeholder + (contentW - placeholder) * M.barMorph(k))
        let barLeft = trailing ? measured.minX + contentW - width : measured.minX
        let bandX = M.shimmerX(group.sinceHide, group.span) - M.slant * measured.midY - barLeft - M.band / 2
        let shape = RoundedRectangle(cornerRadius: radius ?? barHeight / 2)
        return shape
            .fill(SkeletonContentResolveGroup.bar)
            .overlay(alignment: .leading) {
                LinearGradient(colors: [SkeletonContentResolveGroup.shineClear, SkeletonContentResolveGroup.shine, SkeletonContentResolveGroup.shineClear],
                               startPoint: .leading, endPoint: .trailing)
                    .frame(width: M.band)
                    .offset(x: bandX)
                    .opacity(M.shimmerAlpha(k, group.sinceHide))
            }
            .clipShape(shape)
            .frame(width: width, height: barHeight)
            .opacity(M.barAlpha(k))
            .scaleEffect(x: 1, y: M.barBloom(k))
            .allowsHitTesting(false)
    }
}

/// A drawn cover under a placeholder that carries the same band and opens with a soft radial wipe.
struct SkeletonContentResolveCover: View {
    private typealias M = SkeletonContentResolveMotion

    let group: SkeletonContentResolveGroup
    let order: Double
    let size: CGFloat
    let radius: CGFloat
    let spec: SkeletonContentResolveData.Cover

    @State private var origin: CGPoint = .zero

    var body: some View {
        let k = M.kAt(order, group.sinceHide, group.sinceShow)
        ZStack {
            // The art is masked rather than covered, so none of it fringes the placeholder's edge.
            SkeletonContentResolveCoverArt(spec: spec, size: size)
                .equatable()
                .mask { wipeMask(k) }
            if M.shimmerAlpha(k, group.sinceHide) > 0 {
                Canvas { context, _ in drawBand(k, in: &context) }
                    .frame(width: size, height: size)
            }
        }
        .frame(width: size, height: size)
        .background(SkeletonContentResolveGroup.block)
        .clipShape(RoundedRectangle(cornerRadius: radius, style: .continuous))
        .onGeometryChange(for: CGPoint.self) { $0.frame(in: .named(SkeletonContentResolveGroup.space)).origin } action: { origin = $0 }
    }

    /// Open inside the first stop, covered outside the second.
    private func wipeMask(_ k: Double) -> some View {
        let p = M.wipe(k) * (1 + M.wipeSoft)
        return RadialGradient(stops: [
            .init(color: .white, location: 0),
            .init(color: .white, location: M.clamp01(p - M.wipeSoft)),
            .init(color: .white.opacity(0), location: M.clamp01(p)),
            .init(color: .white.opacity(0), location: 1),
        ], center: .center, startRadius: 0, endRadius: size * 0.75)
    }

    private func drawBand(_ k: Double, in context: inout GraphicsContext) {
        let alpha = M.shimmerAlpha(k, group.sinceHide)
        var band = context
        band.opacity = alpha
        band.concatenate(CGAffineTransform(a: 1, b: 0, c: -tan(M.slant), d: 1,
                                           tx: M.shimmerX(group.sinceHide, group.span) - M.slant * origin.y - origin.x, ty: 0))
        let half = M.band / 2
        let shine = Gradient(colors: [SkeletonContentResolveGroup.shineClear, SkeletonContentResolveGroup.shine, SkeletonContentResolveGroup.shineClear])
        band.fill(Path(CGRect(x: -half, y: 0, width: M.band, height: size)),
                  with: .linearGradient(shine, startPoint: CGPoint(x: -half, y: 0), endPoint: CGPoint(x: half, y: 0)))
    }
}
