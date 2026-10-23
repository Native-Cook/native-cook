import SwiftUI

/// `src/components/time-range/index.tsx`
///
/// A vertical, one-item-per-page picker (1 hr … 10 hr). The continuous content offset drives
/// `selectedDuration`, exactly like the Reanimated `useAnimatedScrollHandler` in the RN version.
@available(iOS 18.0, *)
struct TimeRangeView: View {
    @Binding var selectedDuration: Double
    @Binding var scrollPosition: ScrollPosition
    var isDialDragging = false

    private static let scaleFactor: CGFloat = 2.5
    static let itemHeight: CGFloat = 36 * scaleFactor            // 90
    static let arrowWrapperHeight: CGFloat = 24 * scaleFactor    // 60
    static let arrowIconSize: CGFloat = 10 * scaleFactor         // 25
    static let labelFontSize: CGFloat = 28 * scaleFactor         // 70
    static let listWidth: CGFloat = 100 * scaleFactor - 66       // 184
    static let containerHeight: CGFloat = itemHeight + arrowWrapperHeight * 2 // 210

    private let durationOptions = Array(1...10)

    var body: some View {
        VStack(spacing: 0) {
            arrowButton("chevron.up") { step(-1) }

            ScrollView(.vertical) {
                LazyVStack(spacing: 0) {
                    ForEach(durationOptions, id: \.self) { hours in
                        Text("\(hours) hr")
                            .font(.rounded(Self.labelFontSize))
                            .foregroundStyle(Color(timeTargetHex: 0xD8D8D8))
                            .lineLimit(1)
                            .fixedSize()
                            .frame(maxWidth: .infinity)
                            .frame(height: Self.itemHeight)
                            .id(hours)
                    }
                }
                .scrollTargetLayout()
            }
            .frame(width: Self.listWidth, height: Self.itemHeight)
            .scrollTargetBehavior(HourPagingBehavior(isDialDragging: isDialDragging))
            .scrollPosition($scrollPosition)
            .scrollIndicators(.hidden)
            .onScrollGeometryChange(for: CGFloat.self) { geometry in
                geometry.contentOffset.y
            } action: { _, offsetY in
                // interpolate(contentOffset.y, snapOffsets, options) → 1 + y / itemHeight, clamped 1…10
                let interpolated = 1 + offsetY / Self.itemHeight
                let clamped = min(Double(durationOptions.last!), max(Double(durationOptions.first!), interpolated))
                if abs(selectedDuration - clamped) > 0.01 {
                    selectedDuration = clamped
                }
            }
            .overlay(alignment: .top) {
                LinearGradient(colors: [.black, .clear], startPoint: .top, endPoint: .bottom)
                    .frame(height: 16)
                    .allowsHitTesting(false)
            }
            .overlay(alignment: .bottom) {
                LinearGradient(colors: [.clear, .black], startPoint: .top, endPoint: .bottom)
                    .frame(height: 16)
                    .allowsHitTesting(false)
            }

            arrowButton("chevron.down") { step(1) }
        }
        .frame(width: Self.listWidth, height: Self.containerHeight)
    }

    private func arrowButton(_ systemImage: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: systemImage)
                .font(.system(size: Self.arrowIconSize, weight: .black))
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity)
                .frame(height: Self.arrowWrapperHeight)
                .contentShape(Rectangle())
        }
        .buttonStyle(TouchableOpacity())
    }

    private func step(_ delta: Int) {
        let target = min(10, max(1, Int(selectedDuration.rounded()) + delta))
        withAnimation(.easeOut(duration: 0.25)) {
            scrollPosition.scrollTo(y: CGFloat(target - 1) * Self.itemHeight)
        }
    }
}

/// Paging also adjusts programmatic point-based scrolling. Bypass it while
/// scrubbing the dial, then let the dial snap once when the finger is released.
private struct HourPagingBehavior: ScrollTargetBehavior {
    var isDialDragging: Bool

    func updateTarget(_ target: inout ScrollTarget, context: TargetContext) {
        guard !isDialDragging else { return }
        PagingScrollTargetBehavior().updateTarget(&target, context: context)
    }

    @available(iOS 18.4, *)
    func properties(context: PropertiesContext) -> Properties {
        isDialDragging ? Properties() : PagingScrollTargetBehavior().properties(context: context)
    }
}

@available(iOS 18.0, *)
#Preview {
    struct Preview: View {
        @State private var duration: Double = 5
        @State private var position = ScrollPosition(y: 4 * TimeRangeView.itemHeight)
        var body: some View {
            TimeRangeView(selectedDuration: $duration, scrollPosition: $position)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .trailing)
                .background(Color.black)
        }
    }
    return Preview().preferredColorScheme(.dark)
}
