import SwiftUI

private typealias M = HabitatSpecimenCarouselMotion

/// Erase-then-type timing over one crossing, sampled from the carousel position so a drag scrubs
/// it, a reversal is exact and the 2 to 0 wrap is seamless. Mirrors crossingFronts in
/// HabitatTextMotion.tsx.
enum HabitatSpecimenCarouselText {
    /// BlurEdgeTypeIn's numbers (text/blur-edge-type-in/blurEdgeMotion.ts).
    static let maxBlur = 0.16
    static let maxShift = 0.3

    static func crossingFronts(_ position: Double, _ count: Int) -> (from: Int, to: Int, erase: Double, type: Double, settled: Bool) {
        let n = Double(count)
        let wrapped = (position.truncatingRemainder(dividingBy: n) + n).truncatingRemainder(dividingBy: n)
        let from = Int(wrapped.rounded(.down)) % count
        let f = wrapped - wrapped.rounded(.down)
        let erase = 1 - max(0, min(1, f / 0.55))
        let type = max(0, min(1, (f - 0.38) / 0.62))
        return (from, (from + 1) % count, erase, type, f < 0.0005)
    }
}

/// Every value that belongs to a specimen swaps with the same letter motion, as one block: the
/// outgoing one blurs out drifting left, the next one sharpens in from the right, at BlurEdgeTypeIn's
/// blur and drift per em of the value's height.
struct HabitatSpecimenCarouselTextSwap<Item: View>: View {
    let model: HabitatSpecimenCarouselModel
    let count: Int
    let height: CGFloat
    let selectedIndex: Int
    var alignment: Alignment = .topLeading
    @ViewBuilder let item: (Int) -> Item

    var body: some View {
        TimelineView(.animation) { _ in
            let position = model.position
            let reduced = model.reduced
            ZStack(alignment: .topLeading) {
                ForEach(0..<count, id: \.self) { index in
                    let pose = Self.pose(index: index, count: count, height: height, position: position, reduced: reduced)
                    item(index)
                        .frame(maxWidth: .infinity, maxHeight: height, alignment: alignment)
                        .blur(radius: pose.blur)
                        .offset(x: pose.shift)
                        .opacity(pose.opacity)
                        .allowsHitTesting(index == selectedIndex)
                        .accessibilityHidden(index != selectedIndex)
                }
            }
            .frame(height: height, alignment: .top)
        }
    }

    static func pose(index: Int, count: Int, height: CGFloat, position: Double, reduced: Bool) -> (opacity: Double, blur: CGFloat, shift: CGFloat) {
        if reduced {
            let n = Double(count)
            let wrapped = (position.truncatingRemainder(dividingBy: n) + n).truncatingRemainder(dividingBy: n)
            let shown = Int(M.round(wrapped)) % count == index
            return (shown ? 1 : 0, 0, 0)
        }
        let fronts = HabitatSpecimenCarouselText.crossingFronts(position, count)
        var u = 0.0
        var direction = 0.0
        if index == fronts.from { u = fronts.erase; direction = -1 }
        else if index == fronts.to && !fronts.settled { u = fronts.type; direction = 1 }
        let eased = u * u * (3 - 2 * u)
        let k = (1 - eased) * (1 - eased)
        let em = min(Double(height) * 0.8, 18)
        return (eased, CGFloat(HabitatSpecimenCarouselText.maxBlur * em * k), CGFloat(direction * HabitatSpecimenCarouselText.maxShift * em * k))
    }
}
