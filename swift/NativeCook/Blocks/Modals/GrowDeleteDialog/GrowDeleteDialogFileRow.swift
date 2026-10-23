import SwiftUI

struct GrowDeleteDialogFile: Equatable {
    var name: String
    var size: String
    /// Written under the name, after the size.
    var detail: String? = nil
    /// Tile colour, the symbol on it and the symbol's colour.
    var tint: Color
    var icon: String
    var ink: Color? = nil
    /// A picture shown in place of the tile.
    var photo: URL? = nil

    static let samples = [
        GrowDeleteDialogFile(
            name: "Project brief.pdf", size: "2.4 MB", detail: "Edited 2h ago",
            tint: Color(hex: 0xfde8e8), icon: "doc.text.fill", ink: Color(hex: 0xe5484d)
        ),
        GrowDeleteDialogFile(
            name: "Team offsite.png", size: "5.1 MB", detail: "Added yesterday",
            tint: Color(hex: 0xe3ecfd), icon: "photo.fill", ink: Color(hex: 0x3b6fe0),
            photo: URL(string: "https://images.unsplash.com/photo-1522202176988-66273c2fd55f?auto=format&fit=crop&w=140&h=140&q=80")
        ),
        GrowDeleteDialogFile(
            name: "Budget 2026.xlsx", size: "860 KB", detail: "Shared with 4",
            tint: Color(hex: 0xe3f6ea), icon: "tablecells.fill", ink: Color(hex: 0x22a35a)
        ),
        GrowDeleteDialogFile(
            name: "Meeting notes.txt", size: "12 KB", detail: "Edited Monday",
            tint: Color(hex: 0xfff2d9), icon: "doc.plaintext.fill", ink: Color(hex: 0xd98a00)
        ),
    ]
}

/// One file in the list; its trash button is where the dialog grows from.
struct GrowDeleteDialogFileRow: View {
    static let rowHeight = 72.0
    static let rowGap = 12.0
    static let trashSize = 36.0
    /// The trash button's distance from the row's right edge.
    static let trashInset = 16.0
    static let trashInk = Color(hex: 0xd14b5a)
    private static let collapseFor = 0.35
    private static let returnFor = 0.5
    private static let ink = Color(hex: 0x111318)
    private static let mute = Color(hex: 0x7c808a)
    private static let shadow = Color.rgba(17, 19, 24, 1)

    let date: Date
    let file: GrowDeleteDialogFile
    /// True once the file is deleted: the row fades and closes up, and comes back when it is false.
    let removed: Bool
    /// The trash button is pressed down (by a touch or by the loop).
    let pressed: Bool
    let onTrash: () -> Void

    @State private var present = GrowDeleteDialogChannel(1)

    var body: some View {
        let shown = present.value(at: date)
        let tile = RoundedRectangle(cornerRadius: 13, style: .continuous)
        HStack(spacing: 14) {
            Group {
                if let photo = file.photo {
                    Color.clear.overlay {
                        AsyncImage(url: photo) { phase in
                            if let image = phase.image { image.resizable().scaledToFill() }
                        }
                    }
                } else {
                    Image(systemName: file.icon)
                        .font(.system(size: 18))
                        .foregroundStyle(file.ink ?? .black.opacity(0.55))
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .background(file.tint)
                }
            }
            .frame(width: 46, height: 46)
            .clipShape(tile)
            VStack(alignment: .leading, spacing: 2) {
                Text(file.name)
                    .font(.system(size: 16, weight: .semibold))
                    .tracking(-0.2)
                    .foregroundStyle(Self.ink)
                    .lineLimit(1)
                    .frame(height: 21)
                Text(file.detail.map { "\(file.size) · \($0)" } ?? file.size)
                    .font(.system(size: 13))
                    .foregroundStyle(Self.mute)
                    .lineLimit(1)
                    .frame(height: 17)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            Button(action: onTrash) {
                Image(systemName: "trash")
                    .font(.system(size: 16))
                    .foregroundStyle(Self.trashInk)
                    .frame(width: Self.trashSize, height: Self.trashSize)
                    .background(RoundedRectangle(cornerRadius: 11, style: .continuous).fill(pressed ? Color(hex: 0xf7d6da) : Color(hex: 0xfdf0f1)))
                    .scaleEffect(pressed ? 0.9 : 1)
                    .animation(.easeOut(duration: 0.12), value: pressed)
                    .contentShape(Rectangle().inset(by: -8))
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Delete \(file.name)")
        }
        .padding(.leading, 14)
        .padding(.trailing, Self.trashInset)
        .frame(height: Self.rowHeight)
        .background(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .fill(.white)
                .shadow(color: Self.shadow.opacity(0.04), radius: 1, y: 1)
                .shadow(color: Self.shadow.opacity(0.05), radius: 9, y: 6)
        )
        // The row fades and shrinks a little faster than its slot closes, so it never overlaps the next one.
        .frame(height: (Self.rowHeight + Self.rowGap) * shown, alignment: .top)
        .scaleEffect(0.94 + 0.06 * shown)
        .opacity(shown * shown * shown)
        .allowsHitTesting(!removed)
        .onChange(of: removed) { _, now in
            present.go(to: now ? 0 : 1, .timing(now ? Self.collapseFor : Self.returnFor, { t in t < 0.5 ? 2 * t * t : 1 - 2 * (1 - t) * (1 - t) }))
        }
    }
}
