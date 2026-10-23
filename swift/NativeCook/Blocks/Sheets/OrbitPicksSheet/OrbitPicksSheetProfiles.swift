import SwiftUI

struct OrbitPicksSheetProfile {
    var name: String
    var rating: String
    var details: String
    var likes: String
    var match: Int
    var skin: Color
    var hair: Color
    var shirt: Color
    /// Round glasses on the face.
    var glasses = false
    /// A portrait; without one, a flat drawn face stands in.
    var photo: String? = nil
    /// A few more interests, shown as chips.
    var tags: [String] = []
}

private enum P {
    static let mute = Color(hex: 0x6b6b7b)
    static let ink = Color(hex: 0x14141f)
    static let green = Color(hex: 0x22c55e)
    static let well = Color(hex: 0xf4f4f6)
    static let avatar = 108.0
    static let ring = avatar + 12
    /// React Native's hairline width doubled, on a 3x screen.
    static let hairline = 2.0 / 3
}

/// A flat face: shoulders, a head, hair and, for some, round glasses, on a pale disc.
private struct OrbitPicksSheetAvatar: View {
    let profile: OrbitPicksSheetProfile

    var body: some View {
        Canvas { context, _ in
            // Drawn in a 96-point box, scaled to the avatar's size.
            context.scaleBy(x: P.avatar / 96, y: P.avatar / 96)
            context.fill(Path(ellipseIn: CGRect(x: 0, y: 0, width: 96, height: 96)), with: .color(Color(hex: 0xeeeef6)))

            // Shoulders: two curves and the minor arc of a 48-point circle across the bottom.
            var shirt = Path()
            shirt.move(to: CGPoint(x: 14, y: 90))
            shirt.addCurve(to: CGPoint(x: 48, y: 64), control1: CGPoint(x: 16, y: 70), control2: CGPoint(x: 30, y: 64))
            shirt.addCurve(to: CGPoint(x: 82, y: 90), control1: CGPoint(x: 66, y: 64), control2: CGPoint(x: 80, y: 70))
            let centreY = 90 - (48.0 * 48.0 - 34.0 * 34.0).squareRoot()
            let start = atan2(90 - centreY, 34.0)
            let end = atan2(90 - centreY, -34.0)
            for step in 1...24 {
                let angle = start + (end - start) * Double(step) / 24
                shirt.addLine(to: CGPoint(x: 48 + cos(angle) * 48, y: centreY + sin(angle) * 48))
            }
            shirt.closeSubpath()
            context.fill(shirt, with: .color(profile.shirt))

            var neck = Path()
            neck.move(to: CGPoint(x: 42, y: 56))
            neck.addLine(to: CGPoint(x: 54, y: 56))
            neck.addLine(to: CGPoint(x: 54, y: 68))
            neck.addCurve(to: CGPoint(x: 42, y: 68), control1: CGPoint(x: 54, y: 70), control2: CGPoint(x: 42, y: 70))
            neck.closeSubpath()
            context.fill(neck, with: .color(profile.skin))

            context.fill(Path(ellipseIn: CGRect(x: 29, y: 23, width: 38, height: 38)), with: .color(profile.skin))

            var hair = Path()
            hair.move(to: CGPoint(x: 28, y: 40))
            hair.addCurve(to: CGPoint(x: 50, y: 17), control1: CGPoint(x: 26, y: 24), control2: CGPoint(x: 38, y: 16))
            hair.addCurve(to: CGPoint(x: 67, y: 40), control1: CGPoint(x: 62, y: 18), control2: CGPoint(x: 70, y: 26))
            hair.addCurve(to: CGPoint(x: 52, y: 31), control1: CGPoint(x: 63, y: 33), control2: CGPoint(x: 58, y: 30))
            hair.addCurve(to: CGPoint(x: 28, y: 40), control1: CGPoint(x: 44, y: 33), control2: CGPoint(x: 36, y: 31))
            hair.closeSubpath()
            context.fill(hair, with: .color(profile.hair))

            for x in [41.0, 55.0] {
                context.fill(Path(ellipseIn: CGRect(x: x - 2, y: 42, width: 4, height: 4)), with: .color(P.ink))
                if profile.glasses {
                    context.stroke(Path(ellipseIn: CGRect(x: x - 6, y: 38, width: 12, height: 12)), with: .color(P.ink), lineWidth: 1.5)
                }
            }

            var mouth = Path()
            mouth.move(to: CGPoint(x: 43, y: 53))
            mouth.addCurve(to: CGPoint(x: 53, y: 53), control1: CGPoint(x: 46, y: 56), control2: CGPoint(x: 50, y: 56))
            context.stroke(mouth, with: .color(P.ink), style: StrokeStyle(lineWidth: 1.5, lineCap: .round))
        }
        .frame(width: P.avatar, height: P.avatar)
    }
}

/// One profile card: the person, what they like, and the Chat button.
struct OrbitPicksSheetProfileCard: View {
    let profile: OrbitPicksSheetProfile
    let chatLabel: String
    var onChat: (() -> Void)? = nil

    var body: some View {
        VStack(spacing: 0) {
            ZStack {
                Circle().fill(.white).frame(width: P.avatar + 6, height: P.avatar + 6)
                if let photo = profile.photo {
                    AsyncImage(url: URL(string: photo), transaction: Transaction(animation: .easeOut(duration: 0.2))) { phase in
                        if let image = phase.image { image.resizable().scaledToFill().transition(.opacity) }
                    }
                    .frame(width: P.avatar, height: P.avatar)
                    .background(Color(hex: 0xeeeef6))
                    .clipShape(Circle())
                } else {
                    OrbitPicksSheetAvatar(profile: profile)
                }
            }
            .frame(width: P.ring, height: P.ring)
            .background(
                LinearGradient(colors: [Color(hex: 0x8f7bef), OrbitPicksSheetPalette.indigo, Color(hex: 0xf2547d)], startPoint: .topLeading, endPoint: .bottomTrailing),
                in: Circle()
            )

            HStack(spacing: 8) {
                Text(profile.name)
                    .font(.system(size: 26, weight: .heavy))
                    .tracking(-0.6)
                    .foregroundStyle(P.ink)
                HStack(spacing: 3) {
                    Image(systemName: "star.fill").font(.system(size: 8))
                    Text(profile.rating).font(.system(size: 12, weight: .heavy))
                }
                .foregroundStyle(.white)
                .padding(.horizontal, 6)
                .frame(height: 20)
                .background(P.green, in: RoundedRectangle(cornerRadius: 6))
            }
            .frame(height: 32)
            .padding(.top, 14)

            HStack(spacing: 4) {
                Text(profile.details).padding(.trailing, 4)
                Image(systemName: "mappin").font(.system(size: 12))
                Text("3 km away")
            }
            .font(.system(size: 15, weight: .medium))
            .foregroundStyle(P.mute)
            .frame(height: 20)
            .padding(.top, 2)

            HStack(spacing: 12) {
                Image(systemName: "football.fill")
                    .font(.system(size: 14))
                    .foregroundStyle(OrbitPicksSheetPalette.indigo)
                    .frame(width: 36, height: 36)
                    .background(Color(hex: 0xece8fb), in: Circle())
                VStack(alignment: .leading, spacing: 2) {
                    Text("Likes ").font(.system(size: 15)).foregroundStyle(P.mute)
                        + Text(profile.likes).font(.system(size: 15, weight: .heavy)).foregroundStyle(P.ink)
                    Text("You're ").font(.system(size: 13)).foregroundStyle(P.mute)
                        + Text("\(profile.match)%").font(.system(size: 13, weight: .heavy)).foregroundStyle(P.green)
                        + Text(" compatible").font(.system(size: 13)).foregroundStyle(P.mute)
                }
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 14)
            .frame(height: 64)
            .background(P.well, in: RoundedRectangle(cornerRadius: 18))
            .padding(.horizontal, 20)
            .padding(.top, 18)

            if !profile.tags.isEmpty {
                HStack(spacing: 8) {
                    ForEach(profile.tags, id: \.self) { tag in
                        Text(tag)
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(Color(hex: 0x45455a))
                            .padding(.horizontal, 12)
                            .frame(height: 30)
                            .overlay(Capsule().strokeBorder(Color(hex: 0xe4e4ec), lineWidth: P.hairline))
                    }
                }
                .padding(.top, 12)
            }

            Button { onChat?() } label: {
                Text(chatLabel)
                    .font(.system(size: 17, weight: .heavy))
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 54)
                    .background(OrbitPicksSheetPalette.indigo, in: Capsule())
                    .compositingGroup()
                    .shadow(color: OrbitPicksSheetPalette.indigo.opacity(0.35), radius: 12, y: 6)
            }
            .buttonStyle(.plain)
            .padding(.horizontal, 20)
            .padding(.top, 18)
            Spacer(minLength: 0)
        }
        .padding(.top, 24)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .overlay(alignment: .top) {
            HStack {
                corner("ellipsis", size: 14)
                Spacer(minLength: 0)
                corner("xmark", size: 13)
            }
            .padding(.horizontal, 12)
            .padding(.top, 12)
        }
        .background(
            RoundedRectangle(cornerRadius: 28)
                .fill(.white)
                .shadow(color: Color(hex: 0x2a1d7a).opacity(0.1), radius: 24, y: 12)
        )
        .overlay(RoundedRectangle(cornerRadius: 28).strokeBorder(Color(hex: 0xececf2), lineWidth: P.hairline))
        .padding(.horizontal, 20)
    }

    private func corner(_ symbol: String, size: Double) -> some View {
        Image(systemName: symbol)
            .font(.system(size: size, weight: .medium))
            .foregroundStyle(P.mute)
            .frame(width: 32, height: 32)
            .background(P.well, in: Circle())
    }
}
