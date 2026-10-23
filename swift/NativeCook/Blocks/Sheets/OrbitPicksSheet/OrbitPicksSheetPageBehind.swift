import SwiftUI

private enum B {
    static let page = Color.white
    static let ink = Color(hex: 0x14141f)
    static let mute = Color(hex: 0x6b6b7b)
    static let well = Color(hex: 0xf6f6f8)
    static let pink = Color(hex: 0xf2547d)
    static let margin = 20.0
    static let me = "https://images.unsplash.com/photo-1544005313-94ddf0286df2?auto=format&fit=crop&w=120&h=120&q=80"
    /// React Native's hairline width doubled, on a 3x screen.
    static let hairline = 2.0 / 3

    struct Interest {
        let label: String
        let icon: String
        let fill: Color
        let ink: Color
    }

    static let interests = [
        [
            Interest(label: "Gaming", icon: "gamecontroller.fill", fill: Color(hex: 0xe1ecff), ink: Color(hex: 0x2f6fe0)),
            Interest(label: "Skincare", icon: "sparkles", fill: Color(hex: 0xfff4c4), ink: Color(hex: 0xa67c00)),
            Interest(label: "Hiking", icon: "signpost.right.fill", fill: Color(hex: 0xffeede), ink: Color(hex: 0xd0661c)),
            Interest(label: "Pizza", icon: "fork.knife", fill: Color(hex: 0xffe1ec), ink: Color(hex: 0xd6366b)),
        ],
        [
            Interest(label: "Pop", icon: "music.note.list", fill: Color(hex: 0xffe1ec), ink: Color(hex: 0xd6366b)),
            Interest(label: "Dogs", icon: "pawprint.fill", fill: Color(hex: 0xdcf7e4), ink: Color(hex: 0x1a9a4a)),
            Interest(label: "Country", icon: "headphones", fill: Color(hex: 0xffe0e0), ink: Color(hex: 0xd63636)),
            Interest(label: "Space", icon: "globe.americas.fill", fill: Color(hex: 0xdff6fa), ink: Color(hex: 0x1695a8)),
        ],
    ]
}

/// The screen behind the sheet: a heading, the banner that opens the picks, interests and an empty chat list.
struct OrbitPicksSheetPageBehind: View {
    let size: CGSize
    let top: Double
    let bottom: Double
    let title: String
    let banner: String
    let bannerCaption: String
    let bannerScale: Double
    let onBanner: () -> Void
    let onBannerPress: (Bool) -> Void

    var body: some View {
        ZStack(alignment: .topLeading) {
            B.page

            HStack(spacing: 8) {
                AsyncImage(url: URL(string: B.me), transaction: Transaction(animation: .easeOut(duration: 0.2))) { phase in
                    if let image = phase.image { image.resizable().scaledToFill().transition(.opacity) }
                }
                .frame(width: 36, height: 36)
                .background(Color(hex: 0xe8c9a8))
                .clipShape(Circle())
                .padding(.trailing, 4)
                Text(title)
                    .font(.system(size: 30, weight: .heavy))
                    .tracking(-0.8)
                    .foregroundStyle(B.ink)
                    .frame(maxWidth: .infinity, alignment: .leading)
                round("checkmark.shield")
                round("slider.horizontal.3")
            }
            .padding(.horizontal, B.margin)
            .frame(width: size.width, height: 40)
            .offset(y: top + 6)

            Button(action: onBanner) {
                HStack(spacing: 14) {
                    OrbitPicksSheetBadge(size: 48)
                        .frame(width: 58, height: 58)
                        .background(.white, in: Circle())
                        .compositingGroup()
                        .shadow(color: OrbitPicksSheetPalette.indigo.opacity(0.2), radius: 8, y: 3)
                    VStack(alignment: .leading, spacing: 3) {
                        Text(banner)
                            .font(.system(size: 17, weight: .heavy))
                            .tracking(-0.3)
                            .foregroundStyle(B.ink)
                        Text(bannerCaption)
                            .font(.system(size: 13))
                            .foregroundStyle(B.mute)
                            .lineLimit(2)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    Image(systemName: "chevron.right")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(OrbitPicksSheetPalette.indigo)
                }
                .padding(.horizontal, 14)
                .frame(width: size.width - B.margin * 2, height: 88)
                .background(
                    LinearGradient(colors: [Color(hex: 0xf1edff), Color(hex: 0xe4ddfd)], startPoint: .topLeading, endPoint: .bottomTrailing),
                    in: RoundedRectangle(cornerRadius: 22)
                )
                .overlay(RoundedRectangle(cornerRadius: 22).strokeBorder(Color(hex: 0xddd5fb), lineWidth: B.hairline))
                .overlay(alignment: .topTrailing) {
                    Text("5")
                        .font(.system(size: 11, weight: .heavy))
                        .foregroundStyle(.white)
                        .frame(minWidth: 18, minHeight: 18)
                        .background(B.pink, in: Capsule())
                        .padding(2)
                        .background(.white, in: Capsule())
                        .offset(x: 4, y: -6)
                }
            }
            .buttonStyle(OrbitPicksSheetPress(onChange: onBannerPress))
            .accessibilityLabel(banner)
            .scaleEffect(bannerScale)
            .offset(x: B.margin, y: top + 62)

            HStack(spacing: 5) {
                Capsule().fill(OrbitPicksSheetPalette.indigo).frame(width: 16, height: 6)
                Circle().fill(Color(hex: 0xdcdce4)).frame(width: 6, height: 6)
                Circle().fill(Color(hex: 0xdcdce4)).frame(width: 6, height: 6)
            }
            .frame(width: size.width)
            .offset(y: top + 162)

            sectionRow("Explore Interests") {
                Text("See All").font(.system(size: 14, weight: .bold)).foregroundStyle(OrbitPicksSheetPalette.indigo)
            }
            .offset(y: top + 190)

            ForEach(Array(B.interests.enumerated()), id: \.offset) { index, row in
                HStack(spacing: 8) {
                    ForEach(row, id: \.label) { interest in
                        HStack(spacing: 6) {
                            Image(systemName: interest.icon).font(.system(size: 13))
                            Text(interest.label).font(.system(size: 14, weight: .bold))
                        }
                        .foregroundStyle(interest.ink)
                        .padding(.horizontal, 13)
                        .frame(height: 36)
                        .background(interest.fill, in: Capsule())
                        .fixedSize()
                    }
                }
                .offset(x: B.margin, y: top + 228 + Double(index) * 44)
            }

            sectionRow("Chats") {
                Text("0 Requests").font(.system(size: 14, weight: .semibold)).foregroundStyle(Color(hex: 0xa9a9b6))
            }
            .offset(y: top + 334)

            emptyChats
                .offset(x: B.margin, y: top + 374)

            tabs
                .frame(width: size.width, height: size.height, alignment: .bottom)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .clipped()
    }

    private func round(_ symbol: String) -> some View {
        Image(systemName: symbol)
            .font(.system(size: 17))
            .foregroundStyle(B.ink)
            .frame(width: 40, height: 40)
            .background(B.well, in: Circle())
    }

    private func sectionRow<Trailing: View>(_ text: String, @ViewBuilder trailing: () -> Trailing) -> some View {
        HStack(alignment: .firstTextBaseline) {
            Text(text)
                .font(.system(size: 20, weight: .heavy))
                .tracking(-0.4)
                .foregroundStyle(B.ink)
            Spacer(minLength: 0)
            trailing()
        }
        .padding(.horizontal, B.margin)
        .frame(width: size.width, height: 26)
    }

    private var emptyChats: some View {
        VStack(spacing: 8) {
            ZStack(alignment: .topLeading) {
                Image(systemName: "bubble.left.fill")
                    .font(.system(size: 19))
                    .foregroundStyle(Color(hex: 0xc9bff7))
                    .frame(width: 48, height: 48)
                    .background(Color(hex: 0xece8fb), in: Circle())
                Image(systemName: "bubble.left.and.bubble.right.fill")
                    .font(.system(size: 18))
                    .foregroundStyle(OrbitPicksSheetPalette.indigo)
                    .frame(width: 48, height: 48)
                    .background(.white, in: Circle())
                    .compositingGroup()
                    .shadow(color: OrbitPicksSheetPalette.indigo.opacity(0.18), radius: 8, y: 3)
                    .offset(x: 28, y: 8)
            }
            .frame(width: 76, height: 56, alignment: .topLeading)
            .padding(.bottom, 6)
            Text("No chats yet!")
                .font(.system(size: 18, weight: .heavy))
                .tracking(-0.3)
                .foregroundStyle(B.ink)
            Text("When you chat with new people on \(title), they'll show up here")
                .font(.system(size: 14))
                .foregroundStyle(B.mute)
                .multilineTextAlignment(.center)
                .lineSpacing(2)
                .padding(.horizontal, 8)
            HStack(spacing: 8) {
                Image(systemName: "sparkles").font(.system(size: 15, weight: .semibold))
                Text("Find New People").font(.system(size: 16, weight: .heavy))
            }
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity)
            .frame(height: 52)
            .background(OrbitPicksSheetPalette.indigo, in: Capsule())
            .compositingGroup()
            .shadow(color: OrbitPicksSheetPalette.indigo.opacity(0.3), radius: 10, y: 5)
            .padding(.top, 14)
        }
        .padding(.horizontal, 20)
        .padding(.top, 30)
        .padding(.bottom, 24)
        .frame(width: size.width - B.margin * 2)
        .background(B.well, in: RoundedRectangle(cornerRadius: 24))
    }

    private var tabs: some View {
        HStack(spacing: 0) {
            tab("face.smiling", size: 22, color: B.mute)
            tab("safari.fill", size: 23, color: OrbitPicksSheetPalette.indigo)
                .overlay(alignment: .bottom) {
                    Circle().fill(OrbitPicksSheetPalette.indigo).frame(width: 5, height: 5).offset(y: 2)
                }
            tab("bubble.left.and.bubble.right", size: 18, color: B.mute)
        }
        .padding(.top, 10)
        .padding(.bottom, bottom + 2)
        .frame(width: size.width)
        .background(B.page)
        .overlay(alignment: .top) { Rectangle().fill(Color(hex: 0xe6e6ec)).frame(height: 1.0 / 3) }
    }

    /// One of three tabs spread like React Native's space-around.
    private func tab(_ symbol: String, size: Double, color: Color) -> some View {
        Image(systemName: symbol)
            .font(.system(size: size))
            .foregroundStyle(color)
            .frame(width: 64, height: 40)
            .frame(maxWidth: .infinity)
    }
}

/// Reports a press and its release, so the banner can squash like the rest of the page.
struct OrbitPicksSheetPress: ButtonStyle {
    let onChange: (Bool) -> Void

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .onChange(of: configuration.isPressed) { _, pressed in onChange(pressed) }
    }
}
