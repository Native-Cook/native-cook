import SwiftUI

/// The four section pages of Northline (static content; the parent slides them in and out). Each
/// is a full section sized to the page area: a main card, and a second block when there is room
/// (`room` = the page area's height). Copy and names are invented.
enum RailwaySwitchNavStyle {
    static let ink = railwaySwitchNavHex(0xEEF1EA)
    static let muted = railwaySwitchNavHex(0xEEF1EA).opacity(0.58)
    static let gold = railwaySwitchNavHex(0xF2C14E)
    static let good = railwaySwitchNavHex(0x7BE39A)
    static let warn = railwaySwitchNavHex(0xF2A65A)
    static let dark = railwaySwitchNavHex(0x1A2420)
    static let line = Color(red: 236 / 255, green: 244 / 255, blue: 236 / 255)
    /// Below this page height the second block is left out.
    static let roomForMore = 290.0
}

private typealias S = RailwaySwitchNavStyle

/// A card with outer drop shadows only (as CSS box shadows), so its translucent face stays clear.
private struct RailwaySwitchNavCard<Content: View>: View {
    var vertical = 16.0
    @ViewBuilder let content: Content

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 20, style: .continuous)
        VStack(alignment: .leading, spacing: 6) { content }
            .padding(.horizontal, 16)
            .padding(.vertical, vertical)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                ZStack {
                    shape.fill(Color.black)
                        .shadow(color: .black.opacity(0.3), radius: 1, y: 1)
                        .shadow(color: .black.opacity(0.25), radius: 15, y: 12)
                        .mask(Rectangle().padding(-60).overlay(shape.blendMode(.destinationOut)).compositingGroup())
                    shape.fill(S.line.opacity(0.06))
                    shape.strokeBorder(S.line.opacity(0.16), lineWidth: 1 / 3)
                }
            )
    }
}

private struct RailwaySwitchNavHeading: View {
    let kicker: String
    let title: String

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(kicker)
                .font(.system(size: 12, weight: .bold))
                .kerning(2)
                .foregroundStyle(S.gold)
            Text(title)
                .font(.system(size: 28, weight: .bold))
                .kerning(-0.5)
                .foregroundStyle(S.ink)
                .lineLimit(1)
        }
    }
}

private func railwaySwitchNavRule() -> some View {
    Rectangle().fill(S.line.opacity(0.14)).frame(height: 1 / 3)
}

struct RailwaySwitchNavPage: View {
    let k: Int
    let room: Double

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            switch k {
            case 0: home
            case 1: trips
            case 2: tickets
            default: account
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var more: Bool { room >= S.roomForMore }

    // MARK: Home

    @ViewBuilder private var home: some View {
        RailwaySwitchNavHeading(kicker: "GOOD MORNING, MAREN", title: "Your 08:14 is on time")
        RailwaySwitchNavCard {
            HStack {
                Text("08:14")
                    .font(.system(size: 34, weight: .bold))
                    .monospacedDigit()
                    .kerning(-0.8)
                    .foregroundStyle(S.ink)
                Spacer()
                Text("Platform 3")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(S.gold)
                    .padding(.horizontal, 10)
                    .frame(height: 26)
                    .background(Capsule().fill(S.gold.opacity(0.16)))
            }
            Text("Elm Cross to Harbour Row")
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(S.ink)
            Text("22 min · 4 stops · leave home in 6 min")
                .font(.system(size: 13))
                .monospacedDigit()
                .foregroundStyle(S.muted)
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Capsule().fill(S.line.opacity(0.12))
                    Capsule().fill(S.gold).frame(width: geo.size.width * 0.62)
                }
            }
            .frame(height: 4)
            .padding(.top, 6)
        }
        if more {
            RailwaySwitchNavCard(vertical: 10) {
                ForEach([
                    ("Harbour line", "Good service", S.good),
                    ("Millbank loop", "Minor delays, 4 min", S.warn),
                    ("Fenwick branch", "Good service", S.good),
                ], id: \.0) { row in
                    HStack(spacing: 10) {
                        Circle().fill(row.2).frame(width: 8, height: 8)
                        Text(row.0)
                            .font(.system(size: 14, weight: .medium))
                            .foregroundStyle(S.ink)
                        Spacer(minLength: 0)
                        Text(row.1)
                            .font(.system(size: 13))
                            .foregroundStyle(S.muted)
                    }
                    .frame(height: 22)
                }
            }
        }
    }

    // MARK: Trips

    @ViewBuilder private var trips: some View {
        RailwaySwitchNavHeading(kicker: "TRIPS", title: "Three trips this week")
        RailwaySwitchNavCard(vertical: 10) {
            let rows = [
                ("Today", "08:14", "Elm Cross → Harbour Row", "22 min"),
                ("Fri", "17:42", "Harbour Row → Millbank", "35 min"),
                ("Sun", "10:05", "Elm Cross → Fenwick Bay", "1 h 12"),
            ]
            VStack(spacing: 0) {
                ForEach(rows.indices, id: \.self) { i in
                    VStack(spacing: 0) {
                        if i > 0 { railwaySwitchNavRule() }
                        HStack(spacing: 12) {
                            VStack(alignment: .leading, spacing: 0) {
                                Text(rows[i].0)
                                    .font(.system(size: 11, weight: .semibold))
                                    .foregroundStyle(S.muted)
                                Text(rows[i].1)
                                    .font(.system(size: 16, weight: .bold))
                                    .monospacedDigit()
                                    .foregroundStyle(S.ink)
                            }
                            .frame(width: 48, alignment: .leading)
                            Text(rows[i].2)
                                .font(.system(size: 14, weight: .medium))
                                .foregroundStyle(S.ink)
                                .lineLimit(1)
                                .frame(maxWidth: .infinity, alignment: .leading)
                            Text(rows[i].3)
                                .font(.system(size: 13))
                                .monospacedDigit()
                                .foregroundStyle(S.muted)
                        }
                        .padding(.vertical, 7)
                    }
                }
            }
        }
        if more {
            let shape = RoundedRectangle(cornerRadius: 18, style: .continuous)
            ZStack(alignment: .bottom) {
                Canvas { ctx, size in
                    // The SVG's 320 × 64 view box, scaled to cover (xMidYMid slice).
                    let s = max(size.width / 320, size.height / 64)
                    ctx.translateBy(x: (size.width - 320 * s) / 2, y: (size.height - 64 * s) / 2)
                    ctx.scaleBy(x: s, y: s)
                    var bed = Path()
                    bed.move(to: CGPoint(x: 0, y: 50))
                    bed.addCurve(to: CGPoint(x: 120, y: 22), control1: CGPoint(x: 60, y: 50), control2: CGPoint(x: 70, y: 22))
                    bed.addCurve(to: CGPoint(x: 240, y: 30), control1: CGPoint(x: 170, y: 22), control2: CGPoint(x: 190, y: 44))
                    bed.addCurve(to: CGPoint(x: 320, y: 14), control1: CGPoint(x: 290, y: 16), control2: CGPoint(x: 300, y: 12))
                    ctx.stroke(bed, with: .color(Color(red: 232 / 255, green: 224 / 255, blue: 200 / 255).opacity(0.18)), lineWidth: 6)
                    var route = Path()
                    route.move(to: CGPoint(x: 24, y: 48))
                    route.addCurve(to: CGPoint(x: 120, y: 22), control1: CGPoint(x: 60, y: 48), control2: CGPoint(x: 76, y: 22))
                    route.addCurve(to: CGPoint(x: 236, y: 31), control1: CGPoint(x: 164, y: 22), control2: CGPoint(x: 190, y: 44))
                    ctx.stroke(route, with: .color(S.gold), style: StrokeStyle(lineWidth: 3, lineCap: .round))
                    let start = Path(ellipseIn: CGRect(x: 19, y: 43, width: 10, height: 10))
                    ctx.fill(start, with: .color(S.dark))
                    ctx.stroke(start, with: .color(S.gold), lineWidth: 2)
                    ctx.fill(Path(ellipseIn: CGRect(x: 116.5, y: 18.5, width: 7, height: 7)), with: .color(S.gold))
                    ctx.fill(Path(ellipseIn: CGRect(x: 176.5, y: 32.5, width: 7, height: 7)), with: .color(S.gold))
                    ctx.fill(Path(ellipseIn: CGRect(x: 231, y: 26, width: 10, height: 10)), with: .color(S.gold))
                }
                HStack {
                    Text("Elm Cross")
                    Spacer()
                    Text("Harbour Row · next trip")
                }
                .font(.system(size: 10, weight: .semibold))
                .foregroundStyle(S.muted)
                .padding(.horizontal, 14)
                .padding(.bottom, 6)
            }
            .frame(height: 72)
            .background(railwaySwitchNavHex(0x1D2B27))
            .clipShape(shape)
            .overlay(shape.strokeBorder(S.line.opacity(0.16), lineWidth: 1 / 3))
        }
    }

    // MARK: Tickets

    private static let bars: [Double] = [3, 1, 2, 1, 1, 3, 2, 1, 3, 1, 2, 2, 1, 3, 1, 1, 2, 3, 1, 2, 1, 3, 2, 1, 1, 2, 3, 1, 2, 1, 3, 1, 2, 1, 1, 3]

    @ViewBuilder private var tickets: some View {
        let sub = S.dark.opacity(0.62)
        RailwaySwitchNavHeading(kicker: "TICKETS", title: "Monthly pass")
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text("Zones 1 – 3")
                    .font(.system(size: 20, weight: .bold))
                    .foregroundStyle(S.dark)
                Spacer()
                Text("£142.50")
                    .font(.system(size: 16, weight: .semibold))
                    .monospacedDigit()
                    .foregroundStyle(S.dark)
            }
            Text("Peak and off-peak · Maren Holt")
                .font(.system(size: 12))
                .monospacedDigit()
                .foregroundStyle(sub)
            HStack(alignment: .bottom, spacing: 2) {
                ForEach(Self.bars.indices, id: \.self) { i in
                    RoundedRectangle(cornerRadius: 0.5).fill(S.dark).frame(width: Self.bars[i], height: 34)
                }
            }
            .padding(.top, 6)
            Text("NL · 4471 2093 8810")
                .font(.system(size: 11, weight: .semibold))
                .kerning(1.2)
                .monospacedDigit()
                .foregroundStyle(S.dark.opacity(0.7))
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Capsule().fill(S.dark.opacity(0.14))
                    Capsule().fill(railwaySwitchNavHex(0x2E6B4E)).frame(width: geo.size.width * 0.4)
                }
            }
            .frame(height: 6)
            .padding(.top, 4)
            HStack {
                Text("Valid 1 – 30 Nov")
                    .font(.system(size: 12))
                    .monospacedDigit()
                    .foregroundStyle(sub)
                Spacer()
                Text("18 days left")
                    .font(.system(size: 12, weight: .bold))
                    .monospacedDigit()
                    .foregroundStyle(S.dark)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .fill(railwaySwitchNavHex(0xEFE6CC))
                .shadow(color: .black.opacity(0.35), radius: 1, y: 1)
                .shadow(color: .black.opacity(0.3), radius: 15, y: 14)
        )
        if more {
            HStack(spacing: 8) {
                chip(dot: true, "Auto-renews 1 Dec")
                chip(dot: false, "Saved £38 vs daily fares")
            }
        }
    }

    private func chip(dot: Bool, _ text: String) -> some View {
        HStack(spacing: 6) {
            if dot { Circle().fill(S.good).frame(width: 8, height: 8) }
            Text(text)
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(S.ink)
                .lineLimit(1)
        }
        .padding(.horizontal, 12)
        .frame(height: 30)
        .background(Capsule().fill(S.line.opacity(0.06)))
        .overlay(Capsule().strokeBorder(S.line.opacity(0.16), lineWidth: 1 / 3))
    }

    // MARK: Account

    @ViewBuilder private var account: some View {
        RailwaySwitchNavHeading(kicker: "ACCOUNT", title: "Maren Holt")
        if more {
            HStack(spacing: 12) {
                Text("MH")
                    .font(.system(size: 17, weight: .heavy))
                    .foregroundStyle(S.dark)
                    .frame(width: 48, height: 48)
                    .background(Circle().fill(S.gold))
                VStack(alignment: .leading, spacing: 2) {
                    Text("Member since 2021")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(S.ink)
                    Text("312 trips · 4,860 km this year")
                        .font(.system(size: 13))
                        .monospacedDigit()
                        .foregroundStyle(S.muted)
                }
            }
        }
        RailwaySwitchNavCard(vertical: 10) {
            let rows = [("Card", "ending 4421"), ("Commute alerts", "On, 07:45"), ("Home station", "Elm Cross")]
            VStack(spacing: 0) {
                ForEach(rows.indices, id: \.self) { i in
                    VStack(spacing: 0) {
                        if i > 0 { railwaySwitchNavRule() }
                        HStack {
                            Text(rows[i].0)
                                .font(.system(size: 14))
                                .foregroundStyle(S.muted)
                            Spacer()
                            Text(rows[i].1)
                                .font(.system(size: 14, weight: .semibold))
                                .foregroundStyle(S.ink)
                        }
                        .padding(.vertical, 9)
                    }
                }
            }
        }
        if more {
            Text("Signed in on this phone · Sign out")
                .font(.system(size: 13))
                .foregroundStyle(S.muted)
                .frame(maxWidth: .infinity)
        }
    }
}
