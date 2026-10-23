import SwiftUI

struct HabitBubbleClusterChrome: View {
    let model: HabitBubbleClusterModel

    var body: some View {
        ZStack(alignment: .topLeading) {
            header
            dateBlock
            bottomBar
        }
        .frame(width: model.size.width, height: model.size.height, alignment: .topLeading)
    }

    private var header: some View {
        HStack(spacing: 0) {
            HabitBubbleClusterGlass(shape: Capsule()) {
                HStack(spacing: 0) {
                    ForEach(Array(HabitBubbleClusterTokens.copy.tabs.enumerated()), id: \.offset) { index, label in
                        Button {
                            model.selectTab(index)
                        } label: {
                            Text(label)
                                .font(HabitBubbleClusterAssets.font(HabitBubbleClusterTokens.FontName.semibold, size: HabitBubbleClusterTokens.type.segment))
                                .foregroundStyle(HabitBubbleClusterColor(index == model.tab ? HabitBubbleClusterTokens.Color.selectedInk : HabitBubbleClusterTokens.Color.inkSoft))
                                .frame(maxWidth: .infinity, maxHeight: .infinity)
                                .background(index == model.tab ? HabitBubbleClusterColor(HabitBubbleClusterTokens.Color.selected) : .clear)
                                .clipShape(Capsule())
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("\(label) habits")
                        .accessibilityAddTraits(index == model.tab ? .isSelected : [])
                    }
                }
                .padding(HabitBubbleClusterTokens.space.xs)
            }
            .frame(width: HabitBubbleClusterTokens.layout.segmentWidth, height: HabitBubbleClusterTokens.layout.control)

            Spacer(minLength: 0)

            HabitBubbleClusterGlass(shape: Circle()) {
                Text(HabitBubbleClusterTokens.copy.monogram)
                    .font(HabitBubbleClusterAssets.font(HabitBubbleClusterTokens.FontName.semibold, size: HabitBubbleClusterTokens.type.monogram))
                    .foregroundStyle(HabitBubbleClusterColor(HabitBubbleClusterTokens.Color.ink))
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .accessibilityLabel("Profile, Ana Morales")
            }
            .frame(width: HabitBubbleClusterTokens.layout.control, height: HabitBubbleClusterTokens.layout.control)
        }
        .frame(width: max(0, model.size.width - HabitBubbleClusterTokens.layout.gutter * 2), height: HabitBubbleClusterTokens.layout.control)
        .offset(x: HabitBubbleClusterTokens.layout.gutter, y: model.safeTop + HabitBubbleClusterTokens.space.xs)
    }

    private var dateBlock: some View {
        VStack(spacing: HabitBubbleClusterTokens.space.xs) {
            Text(HabitBubbleClusterTokens.copy.date)
                .font(HabitBubbleClusterAssets.font(HabitBubbleClusterTokens.FontName.semibold, size: HabitBubbleClusterTokens.type.date))
                .tracking(-0.3)
                .foregroundStyle(HabitBubbleClusterColor(HabitBubbleClusterTokens.Color.ink))
                .accessibilityAddTraits(.isHeader)
            Text(HabitBubbleClusterTokens.copy.now)
                .font(HabitBubbleClusterAssets.font(HabitBubbleClusterTokens.FontName.regular, size: HabitBubbleClusterTokens.type.now))
                .foregroundStyle(HabitBubbleClusterColor(HabitBubbleClusterTokens.Color.inkSoft))
        }
        .frame(width: model.size.width, alignment: .top)
        .offset(y: model.origin.y + HabitBubbleClusterTokens.layout.dateDrop)
        .allowsHitTesting(false)
    }

    private var bottomBar: some View {
        HStack(spacing: 0) {
            HabitBubbleClusterGlass(shape: Circle()) {
                Image(systemName: "calendar")
                    .font(.system(size: 19, weight: .regular))
                    .foregroundStyle(HabitBubbleClusterColor(HabitBubbleClusterTokens.Color.ink))
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .accessibilityLabel("Calendar")
            }
            .frame(width: HabitBubbleClusterTokens.layout.pill, height: HabitBubbleClusterTokens.layout.pill)

            Spacer(minLength: 0)

            HabitBubbleClusterGlass(shape: Capsule()) {
                HStack(spacing: HabitBubbleClusterTokens.space.s) {
                    HabitBubbleClusterProgressRing(progress: progress)
                    Text("\(model.count.done) of \(model.count.total)")
                        .font(HabitBubbleClusterAssets.font(HabitBubbleClusterTokens.FontName.semibold, size: HabitBubbleClusterTokens.type.count))
                        .monospacedDigit()
                        .foregroundStyle(HabitBubbleClusterColor(HabitBubbleClusterTokens.Color.ink))
                    Text("done today")
                        .font(HabitBubbleClusterAssets.font(HabitBubbleClusterTokens.FontName.regular, size: HabitBubbleClusterTokens.type.count))
                        .foregroundStyle(HabitBubbleClusterColor(HabitBubbleClusterTokens.Color.inkSoft))
                }
                .padding(.horizontal, HabitBubbleClusterTokens.space.l)
                .frame(height: HabitBubbleClusterTokens.layout.pill)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("\(model.count.done) of \(model.count.total) habits done today")
            }
            .fixedSize(horizontal: true, vertical: false)

            Spacer(minLength: 0)

            HabitBubbleClusterGlass(shape: Circle()) {
                Image(systemName: "plus")
                    .font(.system(size: 22, weight: .regular))
                    .foregroundStyle(HabitBubbleClusterColor(HabitBubbleClusterTokens.Color.ink))
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .accessibilityLabel("New habit")
            }
            .frame(width: HabitBubbleClusterTokens.layout.pill, height: HabitBubbleClusterTokens.layout.pill)
        }
        .frame(width: max(0, model.size.width - HabitBubbleClusterTokens.layout.gutter * 2), height: HabitBubbleClusterTokens.layout.pill)
        .offset(x: HabitBubbleClusterTokens.layout.gutter, y: bottomY)
    }

    private var bottomY: Double {
        model.size.height - max(model.safeBottom, HabitBubbleClusterTokens.layout.gutter) - HabitBubbleClusterTokens.space.s - HabitBubbleClusterTokens.layout.pill
    }

    private var progress: Double {
        model.count.total > 0 ? Double(model.count.done) / Double(model.count.total) : 0
    }
}

private struct HabitBubbleClusterProgressRing: View {
    let progress: Double

    var body: some View {
        ZStack {
            Circle()
                .stroke(HabitBubbleClusterColor(HabitBubbleClusterTokens.Color.hairline), lineWidth: 2.5)
            Circle()
                .trim(from: 0, to: progress)
                .stroke(HabitBubbleClusterColor(HabitBubbleClusterTokens.Color.ink), style: StrokeStyle(lineWidth: 2.5, lineCap: .round))
                .rotationEffect(.degrees(-90))
        }
        .frame(width: 20, height: 20)
    }
}

private struct HabitBubbleClusterGlass<ShapeType: InsettableShape, Content: View>: View {
    let shape: ShapeType
    @ViewBuilder let content: Content

    var body: some View {
        ZStack {
            shape.fill(.ultraThinMaterial)
            shape.fill(Color.black.opacity(0.68))
            shape.fill(Color.white.opacity(0.04))
            content
        }
            .overlay(shape.stroke(Color.white.opacity(0.16), lineWidth: 0.5))
            .clipShape(shape)
    }
}
