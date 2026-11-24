import SwiftUI

// Relay, a team inbox for the (invented) Fernway team. Names, channels and numbers are made up.

struct NotificationStackExpandNotice: Identifiable {
    enum Badge { case channel, file }
    enum App { case calendar, files }

    let id: String
    /// Who it is from; their initials draw the avatar.
    let from: String
    let body: String
    let time: String
    /// Three stops for the avatar disc, top-left to bottom-right.
    let tint: [UInt32]
    var badge: Badge? = nil
    var app: App? = nil
}

struct NotificationStackExpandGroup {
    let title: String
    let channel: String
    let notices: [NotificationStackExpandNotice]
}

enum NotificationStackExpandData {
    static let launch = NotificationStackExpandGroup(title: "Launch review", channel: "#launch-review", notices: [
        .init(id: "l1", from: "Priya Nair", body: "Signed off on the onboarding copy. Ship it with the new empty states?",
              time: "now", tint: [0xF2B48A, 0xE07A5F, 0xB5525C], badge: .channel),
        .init(id: "l2", from: "Tomás Reyes", body: "Build 4.12 is on TestFlight. Crash rate is down to 0.3% since Monday.",
              time: "4m", tint: [0x9CC9B8, 0x4E9A86, 0x2F6F66], badge: .channel),
        .init(id: "l3", from: "Ines Kowal", body: "Moved the review to 3:30 so design can join from the Lisbon office.",
              time: "12m", tint: [0xB9B4E8, 0x7F79C9, 0x5A5497], badge: .channel),
        .init(id: "l4", from: "Dev Shah", body: "The pricing page still shows last year’s annual price. Fixing it now.",
              time: "18m", tint: [0xF1D58E, 0xD9A74A, 0xA8772F], badge: .channel),
    ])

    static let standup = NotificationStackExpandNotice(
        id: "s1", from: "Calendar", body: "Standup in 10 min · Larch room, second floor", time: "9:50",
        tint: [0xF5A3A0, 0xE5605B, 0xC23F44], app: .calendar)

    static let crit = NotificationStackExpandGroup(title: "Design crit", channel: "#design-crit", notices: [
        .init(id: "c1", from: "Noor Amari", body: "Uploaded 6 frames of Checkout v3. Comments welcome before Thursday.",
              time: "9:12", tint: [0xA7C7EE, 0x5C8FD6, 0x3A64A8], badge: .file),
        .init(id: "c2", from: "Leo Brandt", body: "Left notes on the receipt screen.",
              time: "8:47", tint: [0xC9D8A2, 0x93AE5B, 0x64803A], badge: .channel),
    ])

    static let yesterday: [NotificationStackExpandNotice] = [
        .init(id: "y1", from: "Hana Ito", body: "Thanks for covering the release notes. The changelog reads great.",
              time: "Mon", tint: [0xF0B6CF, 0xD86F9A, 0xA84A74], badge: .channel),
        .init(id: "y2", from: "Files", body: "Q4 roadmap.key was shared with you by Ines Kowal.",
              time: "Mon", tint: [0xB7C2CC, 0x7D8B98, 0x56636F], app: .files),
    ]

    static let filters = ["All", "Mentions", "Threads"]
}

enum NotificationStackExpandColors {
    static let bg: [UInt32] = [0xF4F5F7, 0xEDEFF2, 0xE6E9EE]
    static let card = Color(hex: 0xFCFCFD)
    static let ink = Color(hex: 0x1B1F24)
    static let mute = Color(hex: 0x69707A)
    static let accent = Color(hex: 0x2D6F64)
    static let danger = Color(hex: 0xE2484D)
    static let bodyInk = Color(hex: 0x3C434C)
    static let backTint = Color(hex: 0xC9CFD7)
}

/// An SVG glyph stroked like react-native-svg with round caps and joins, in a box of `size`.
struct NotificationStackExpandGlyph: View {
    let d: String
    let size: CGSize
    var color: Color = .white
    var width: CGFloat = 1.6
    var fill = false

    var body: some View {
        let path = NotificationStackExpandSVG.path(d)
        Group {
            if fill {
                path.fill(color)
            } else {
                path.stroke(color, style: StrokeStyle(lineWidth: width, lineCap: .round, lineJoin: .round))
            }
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
    }
}

private struct NotificationStackExpandAppGlyph: View {
    let app: NotificationStackExpandNotice.App

    var body: some View {
        switch app {
        case .calendar:
            ZStack(alignment: .topLeading) {
                RoundedRectangle(cornerRadius: 3.2)
                    .stroke(.white, lineWidth: 1.7)
                    .frame(width: 15, height: 14)
                    .offset(x: 2.5, y: 3.5)
                NotificationStackExpandGlyph(d: "M2.8 8 H17.2 M6.5 1.8 V5 M13.5 1.8 V5", size: CGSize(width: 20, height: 20), width: 1.7)
                RoundedRectangle(cornerRadius: 0.8)
                    .fill(.white)
                    .frame(width: 3, height: 3)
                    .offset(x: 6, y: 10.6)
            }
            .frame(width: 20, height: 20, alignment: .topLeading)
        case .files:
            NotificationStackExpandGlyph(
                d: "M5 2.5 H11.5 L15.5 6.5 V16 A1.6 1.6 0 0 1 13.9 17.6 H5 A1.6 1.6 0 0 1 3.4 16 V4.1 A1.6 1.6 0 0 1 5 2.5 Z M11.2 2.8 V6.8 H15.2",
                size: CGSize(width: 20, height: 20), width: 1.6)
        }
    }
}

private struct NotificationStackExpandBadge: View {
    let kind: NotificationStackExpandNotice.Badge

    var body: some View {
        ZStack {
            Circle().fill(kind == .file ? Color(hex: 0x5C8FD6) : NotificationStackExpandColors.accent)
            Circle().strokeBorder(NotificationStackExpandColors.card, lineWidth: 2)
            if kind == .channel {
                NotificationStackExpandGlyph(d: "M3.2 0.8 L2.4 8.2 M6.6 0.8 L5.8 8.2 M0.9 3 H8.2 M0.6 6 H7.9",
                                             size: CGSize(width: 9, height: 9), width: 1.25)
            } else {
                NotificationStackExpandGlyph(d: "M2 0.9 H5.4 L7.4 2.9 V8.1 H2 Z", size: CGSize(width: 9, height: 9), fill: true)
            }
        }
        .frame(width: 17, height: 17)
    }
}

struct NotificationStackExpandAvatar: View {
    let notice: NotificationStackExpandNotice
    static let size: CGFloat = 38

    private func initials(_ name: String) -> String {
        let parts = name.split(separator: " ")
        let first = parts.first?.first.map(String.init) ?? ""
        let second = parts.count > 1 ? parts[1].first.map(String.init) ?? "" : ""
        return (first + second).uppercased()
    }

    var body: some View {
        let shape = notice.app != nil
            ? AnyShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
            : AnyShape(Circle())
        ZStack(alignment: .bottomTrailing) {
            ZStack {
                shape.fill(LinearGradient(
                    stops: [
                        .init(color: Color(hex: notice.tint[0]), location: 0),
                        .init(color: Color(hex: notice.tint[1]), location: 0.55),
                        .init(color: Color(hex: notice.tint[2]), location: 1),
                    ],
                    startPoint: .topLeading, endPoint: .bottomTrailing))
                if let app = notice.app {
                    NotificationStackExpandAppGlyph(app: app)
                } else {
                    Text(initials(notice.from))
                        .font(.system(size: 14, weight: .semibold))
                        .tracking(0.2)
                        .foregroundStyle(.white)
                }
                // The sheen: a light top edge and a faint dark bottom edge.
                shape.stroke(LinearGradient(
                    stops: [
                        .init(color: .white.opacity(0.35), location: 0),
                        .init(color: .white.opacity(0), location: 0.12),
                        .init(color: .black.opacity(0), location: 0.88),
                        .init(color: .black.opacity(0.08), location: 1),
                    ],
                    startPoint: .top, endPoint: .bottom), lineWidth: 2)
                    .clipShape(shape)
            }
            .frame(width: Self.size, height: Self.size)
            if let badge = notice.badge {
                NotificationStackExpandBadge(kind: badge)
                    .offset(x: 3, y: 3)
            }
        }
        .frame(width: Self.size, height: Self.size)
    }
}

/// The card's content only. The caller gives it a background, a shadow and a transform.
struct NotificationStackExpandFace: View {
    let notice: NotificationStackExpandNotice

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            NotificationStackExpandAvatar(notice: notice)
                .padding(.top, 2)
            VStack(alignment: .leading, spacing: 3) {
                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    Text(notice.from)
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(NotificationStackExpandColors.ink)
                        .lineLimit(1)
                    Spacer(minLength: 0)
                    Text(notice.time)
                        .font(.system(size: 13))
                        .monospacedDigit()
                        .foregroundStyle(NotificationStackExpandColors.mute)
                }
                .frame(height: 20)
                Text(notice.body)
                    .font(.system(size: 14))
                    .lineSpacing(18 - 16.71)
                    .foregroundStyle(NotificationStackExpandColors.bodyInk)
                    .lineLimit(2)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .frame(minWidth: 0, maxWidth: .infinity, alignment: .leading)
        }
        .padding(.horizontal, 14)
        .padding(.top, 13)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }
}

/// The card surface: radius 20 continuous, two soft shadows and a 1 pt light top edge.
struct NotificationStackExpandSurface: View {
    var body: some View {
        let shape = RoundedRectangle(cornerRadius: NotificationStackExpandMotion.radius, style: .continuous)
        shape.fill(NotificationStackExpandColors.card)
            .shadow(color: Color.rgba(30, 38, 48, 0.08), radius: 1, y: 1)
            .shadow(color: Color.rgba(30, 38, 48, 0.07), radius: 12, y: 10)
            .overlay(
                shape.inset(by: 0.5).stroke(LinearGradient(
                    stops: [.init(color: .white.opacity(0.9), location: 0), .init(color: .white.opacity(0), location: 0.05)],
                    startPoint: .top, endPoint: .bottom), lineWidth: 1)
            )
    }
}

/// A plain, still notification card for the rest of the feed.
struct NotificationStackExpandNoticeCard: View {
    let notice: NotificationStackExpandNotice

    var body: some View {
        NotificationStackExpandFace(notice: notice)
            .frame(height: NotificationStackExpandMotion.cardH)
            .background(NotificationStackExpandSurface())
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("\(notice.from), \(notice.time). \(notice.body)")
    }
}
