import SwiftUI

/// Back chevron on the left; calendar and more on the right (the screen's header row).
struct MoodOrbJarHeader: View {
    typealias Layout = MoodOrbJarTokens.Layout
    typealias Palette = MoodOrbJarTokens.Palette
    typealias Copy = MoodOrbJarTokens.Copy

    let geometry: MoodOrbJarGeometry

    var body: some View {
        HStack(spacing: 0) {
            iconButton(Copy.back) {
                MoodOrbJarChevron()
            }
            Spacer(minLength: 0)
            HStack(spacing: 0) {
                iconButton(Copy.calendar) {
                    ZStack {
                        RoundedRectangle(cornerRadius: 5)
                            .strokeBorder(Palette.ink, lineWidth: 2)
                        Text("14")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundStyle(Palette.ink)
                            .padding(.top, 1)
                    }
                    .frame(width: 22, height: 22)
                }
                iconButton(Copy.more) {
                    Text("•••")
                        .font(.system(size: 13, weight: .bold))
                        .tracking(1)
                        .foregroundStyle(Palette.ink)
                }
            }
        }
        .frame(width: geometry.width - 16, height: Layout.header * geometry.scale)
        .offset(x: 8, y: geometry.safeTop)
    }

    private func iconButton<Content: View>(_ label: String, @ViewBuilder content: () -> Content) -> some View {
        Button(action: {}) {
            content()
                .frame(width: Layout.icon, height: Layout.icon)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }
}
