import SwiftUI

/// Every block in this app, by category. A row opens the block on its own screen.
struct GalleryScreen: View {
    let onOpen: (Block) -> Void

    private struct Category: Identifiable {
        let title: String
        var blocks: [Block]
        var id: String { title }
    }

    // The registry is sorted by category, so each category's blocks are already together.
    private static let categories: [Category] = blockRegistry.reduce(into: []) { list, block in
        if let last = list.indices.last, list[last].title == block.category {
            list[last].blocks.append(block)
        } else {
            list.append(Category(title: block.category, blocks: [block]))
        }
    }

    private static let count = "\(blockRegistry.count) \(blockRegistry.count == 1 ? "block" : "blocks")"

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 0) {
                header

                if blockRegistry.isEmpty {
                    empty
                }

                ForEach(Self.categories) { category in
                    Text(category.title)
                        .font(.system(size: 12, design: .monospaced))
                        .textCase(.uppercase)
                        .foregroundStyle(GalleryColors.accent)
                        .padding(.horizontal, 8)
                        .padding(.top, 24)
                        .padding(.bottom, 8)

                    ForEach(category.blocks) { block in
                        Button {
                            onOpen(block)
                        } label: {
                            row(block)
                        }
                        .buttonStyle(RowStyle())
                        .padding(.bottom, 8)
                    }
                }
            }
            .padding(.horizontal, 16)
            .padding(.top, 24)
            .padding(.bottom, 32)
        }
        .background(GalleryColors.background.ignoresSafeArea())
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Native Cook")
                .font(.system(size: 32, weight: .bold))
                .tracking(-0.5)
                .foregroundStyle(GalleryColors.text)
                .accessibilityAddTraits(.isHeader)
            Text("\(galleryTier) · SwiftUI · \(Self.count)")
                .font(.system(size: 12, design: .monospaced))
                .textCase(.uppercase)
                .foregroundStyle(GalleryColors.textMuted)
        }
        .padding(.horizontal, 8)
        .padding(.bottom, 8)
    }

    private var empty: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("No blocks yet")
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(GalleryColors.text)
            Text("Add the first one from the repository root, then build again:")
                .font(.system(size: 15))
                .foregroundStyle(GalleryColors.textMuted)
            Text("npm run new -- <category> <slug>")
                .font(.system(size: 13, design: .monospaced))
                .foregroundStyle(GalleryColors.accent)
                .padding(.top, 4)
        }
        .padding(.horizontal, 8)
        .padding(.top, 32)
    }

    private func row(_ block: Block) -> some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                Text(block.name)
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(GalleryColors.text)
                if !block.spec.isEmpty {
                    Text(block.spec)
                        .font(.system(size: 14))
                        .lineSpacing(3)
                        .lineLimit(2)
                        .multilineTextAlignment(.leading)
                        .foregroundStyle(GalleryColors.textMuted)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            Image(systemName: "chevron.right")
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(GalleryColors.textMuted)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .frame(minHeight: 56)
    }
}

private struct RowStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .background(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .fill(configuration.isPressed ? GalleryColors.surfacePressed : GalleryColors.surface)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .strokeBorder(GalleryColors.border, lineWidth: 0.5)
            )
            .contentShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
    }
}

#Preview {
    GalleryScreen { _ in }
}
