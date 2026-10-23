import SwiftUI

/// Back chevron on the left; calendar and more on the right (the screen's header row).
struct MoodOrbJarHeader: View {
    typealias Layout = MoodOrbJarTokens.Layout
    typealias Palette = MoodOrbJarTokens.Palette
    typealias Copy = MoodOrbJarTokens.Copy

    let geometry: MoodOrbJarGeometry

    var body: some View {
        ZStack(alignment: .topLeading) {
            Text(Copy.title)
                .font(.system(size: 17, weight: .semibold))
                .tracking(-0.3)
                .foregroundStyle(Palette.ink)
                .frame(width: geometry.width, height: Layout.header * geometry.scale)

            HStack(spacing: 0) {
                iconButton(Copy.back) {
                    MoodOrbJarChevron()
                }
                Spacer(minLength: 0)
                HStack(spacing: 0) {
                    iconButton(Copy.calendar) {
                        ZStack {
                            RoundedRectangle(cornerRadius: 5)
                                .strokeBorder(Palette.ink, lineWidth: 1.75)
                            Text("14")
                                .font(.system(size: 9, weight: .bold))
                                .monospacedDigit()
                                .foregroundStyle(Palette.ink)
                                .padding(.top, 1)
                        }
                        .frame(width: 20, height: 20)
                    }
                    iconButton(Copy.more) {
                        Text("•••")
                            .font(.system(size: 12, weight: .bold))
                            .tracking(1)
                            .foregroundStyle(Palette.ink)
                    }
                }
            }
            .frame(width: geometry.width - 16, height: Layout.header * geometry.scale)
            .offset(x: 8)
        }
        .frame(width: geometry.width, height: Layout.header * geometry.scale, alignment: .topLeading)
        .offset(y: geometry.safeTop)
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
