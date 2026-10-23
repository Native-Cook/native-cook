import SwiftUI

/// Root screen. Mirrors `App.tsx`: Header → Queued → (CircleTime + TimeRange) → Footer → BottomTab.
@available(iOS 18.0, *)
struct TimeTargetContentView: View {
    /// Shared value between the vertical picker (`TimeRangeView`) and the circular ruler (`CircleTimeView`).
    @State private var selectedDuration: Double = 5
    @State private var scrollPosition = ScrollPosition(y: 4 * TimeRangeView.itemHeight)
    @State private var dialDragTranslation: CGFloat?
    @State private var dialDragTarget: Double = 5
    @GestureState private var isDialDragging = false

    var body: some View {
        VStack(spacing: 0) {
            HeaderView()
            QueuedView()

            // `container` style: flexDirection row, alignItems center, flex 1
            GeometryReader { geometry in
                ZStack(alignment: .trailing) {
                    CircleTimeView(selectedDuration: selectedDuration)

                    // The dial and picker control the same scroll offset. Geometry updates
                    // selectedDuration, so the ruler and rolling number cannot drift apart.
                    Color.clear
                        .frame(width: max(0, geometry.size.width - TimeRangeView.listWidth))
                        .frame(maxHeight: .infinity)
                        .contentShape(Rectangle())
                        .gesture(dialDrag)
                        .accessibilityElement(children: .ignore)
                        .accessibilityLabel("Time target dial")
                        .accessibilityValue("\(Int(selectedDuration.rounded())) hr")
                        .accessibilityIdentifier("time-target-dial")
                        .accessibilityAdjustableAction { direction in
                            switch direction {
                            case .increment: scroll(to: selectedDuration.rounded() + 1, animated: true)
                            case .decrement: scroll(to: selectedDuration.rounded() - 1, animated: true)
                            @unknown default: break
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)

                    TimeRangeView(
                        selectedDuration: $selectedDuration,
                        scrollPosition: $scrollPosition,
                        isDialDragging: isDialDragging
                    )
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .fadeInUp(duration: 0.6)
            .onChange(of: isDialDragging) { _, active in
                // GestureState also resets when a touch is interrupted or cancelled.
                if !active && dialDragTranslation != nil {
                    scroll(to: dialDragTarget.rounded(), animated: true)
                    dialDragTranslation = nil
                }
            }

            FooterView()
            BottomTabView()
        }
        .background(Color.black.ignoresSafeArea())
    }

    private var dialDrag: some Gesture {
        DragGesture(minimumDistance: 5)
            .updating($isDialDragging) { _, active, _ in active = true }
            .onChanged { value in
                if dialDragTranslation == nil { dialDragTarget = selectedDuration }
                let delta = value.translation.height - (dialDragTranslation ?? 0)
                dialDragTarget = min(10, max(1, dialDragTarget - delta / TimeRangeView.itemHeight))
                dialDragTranslation = value.translation.height
                scroll(to: dialDragTarget, animated: false)
            }
            .onEnded { _ in
                scroll(to: dialDragTarget.rounded(), animated: true)
                dialDragTranslation = nil
            }
    }

    private func scroll(to duration: Double, animated: Bool) {
        let offset = (min(10, max(1, duration)) - 1) * TimeRangeView.itemHeight
        if animated {
            withAnimation(.easeOut(duration: 0.25)) { scrollPosition.scrollTo(y: offset) }
        } else {
            scrollPosition.scrollTo(y: offset)
        }
    }
}

@available(iOS 18.0, *)
#Preview {
    TimeTargetContentView()
        .preferredColorScheme(.dark)
}
