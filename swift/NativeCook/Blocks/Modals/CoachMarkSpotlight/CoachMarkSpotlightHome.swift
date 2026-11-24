import SwiftUI

// Penny, a budgeting app, on its first run: the home screen under the tour. Names, places and amounts are
// invented. Everything sits at positions from `CoachMarkSpotlightLayout`, so the cutouts land on the real
// elements.

struct CoachMarkSpotlightStep {
    let title: String
    let body: String
}

enum CoachMarkSpotlightData {
    static let steps: [CoachMarkSpotlightStep] = [
        .init(title: "Log a spend in seconds", body: "Tap + after you pay. Penny picks the category, you just check the amount."),
        .init(title: "Your month at a glance", body: "See what’s left before payday. It updates every time you log a spend."),
        .init(title: "Find where it goes", body: "Insights breaks your spending down by week, so surprises show up early."),
    ]

    struct Category { let name: String; let spent: Double; let color: UInt32 }
    static let categories: [Category] = [
        .init(name: "Groceries", spent: 412.3, color: 0x3E8E6A),
        .init(name: "Eating out", spent: 286.9, color: 0xE58A5B),
        .init(name: "Transport", spent: 164.0, color: 0x5B8BD9),
        .init(name: "Home", spent: 421.4, color: 0xC9A23E),
    ]
    static let budget: Double = 2000

    enum Glyph { case cart, cup, train, leaf }
    struct Spend { let place: String; let category: String; let amount: Double; let time: String; let color: UInt32; let glyph: Glyph }
    static let recent: [Spend] = [
        .init(place: "Greenway Market", category: "Groceries", amount: 38.45, time: "Today, 6:12 PM", color: 0x3E8E6A, glyph: .cart),
        .init(place: "Little Fern Café", category: "Eating out", amount: 6.8, time: "Today, 8:41 AM", color: 0xE58A5B, glyph: .cup),
        .init(place: "Metro card top-up", category: "Transport", amount: 25.0, time: "Yesterday", color: 0x5B8BD9, glyph: .train),
        .init(place: "Hollis Plant Co.", category: "Home", amount: 19.9, time: "Sun, Nov 22", color: 0xC9A23E, glyph: .leaf),
    ]

    static let tabs = ["Home", "Budgets", "Insights", "Profile"]
}

enum CoachMarkSpotlightColors {
    static let paper: [UInt32] = [0xF8F5F0, 0xF3EFE8, 0xECE7DE]
    static let card = Color(hex: 0xFFFDF9)
    static let ink = Color(hex: 0x1E2420)
    static let mute = Color(hex: 0x6E746F)
    static let green = Color(hex: 0x2F7D5B)
}

struct CoachMarkSpotlightLayout {
    static let side: Double = 16
    static let brandH: Double = 28
    static let cardH: Double = 164
    static let rowH: Double = 58
    static let tabH: Double = 50
    static let fabSize: Double = 58

    let width: Double
    let top: Double
    let card: CoachMarkSpotlightBox
    let fab: CoachMarkSpotlightBox
    /// The Insights tab item (icon and label).
    let tab: CoachMarkSpotlightBox
    let tabTop: Double
    let tabBottom: Double
    let recentTop: Double
    let rows: Int

    init(width: Double, height: Double, insetTop: Double, insetBottom: Double) {
        self.width = width
        top = insetTop + 8
        card = CoachMarkSpotlightBox(x: Self.side, y: top + 146, w: width - Self.side * 2, h: Self.cardH, r: 22)
        tabBottom = max(insetBottom, 8)
        tabTop = height - tabBottom - Self.tabH
        fab = CoachMarkSpotlightBox(x: width - 20 - Self.fabSize, y: tabTop - 18 - Self.fabSize,
                                    w: Self.fabSize, h: Self.fabSize, r: Self.fabSize / 2)
        let tabW = width / Double(CoachMarkSpotlightData.tabs.count)
        tab = CoachMarkSpotlightBox(x: tabW * 2 + (tabW - 80) / 2, y: tabTop + 3, w: 80, h: 44, r: 14)
        recentTop = card.y + card.h + 22
        rows = max(2, min(CoachMarkSpotlightData.recent.count, Int(floor((tabTop - recentTop - 30) / Self.rowH))))
    }
}

private func coachMarkSpotlightMoney(_ n: Double) -> String {
    let f = NumberFormatter()
    f.locale = Locale(identifier: "en_US")
    f.numberStyle = .decimal
    f.minimumFractionDigits = 2
    f.maximumFractionDigits = 2
    return "$" + (f.string(from: NSNumber(value: n)) ?? String(format: "%.2f", n))
}

private typealias C = CoachMarkSpotlightColors

private struct CoachMarkSpotlightPennyMark: View {
    var body: some View {
        ZStack(alignment: .topLeading) {
            Circle().fill(C.green).frame(width: 22, height: 22).offset(x: 1, y: 1)
            Circle().stroke(Color(hex: 0x9ED2B6), lineWidth: 1.2).frame(width: 15.2, height: 15.2).offset(x: 4.4, y: 4.4)
            CoachMarkSpotlightGlyph(d: "M10 16.5 V7.8 H13.1 A2.6 2.6 0 0 1 13.1 13 H10", size: CGSize(width: 24, height: 24),
                                    color: Color(hex: 0xF4FBF7), width: 1.8)
        }
        .frame(width: 24, height: 24, alignment: .topLeading)
    }
}

private struct CoachMarkSpotlightSpendGlyph: View {
    let kind: CoachMarkSpotlightData.Glyph
    let color: Color

    var body: some View {
        let size = CGSize(width: 20, height: 20)
        Group {
            switch kind {
            case .cart:
                ZStack(alignment: .topLeading) {
                    CoachMarkSpotlightGlyph(d: "M2.5 3.5 H4.6 L6.4 12.6 H15.3 L16.9 6.2 H5.3", size: size, color: color)
                    Circle().stroke(color, lineWidth: 1.7).frame(width: 2.4, height: 2.4).offset(x: 6.2, y: 14.8)
                    Circle().stroke(color, lineWidth: 1.7).frame(width: 2.4, height: 2.4).offset(x: 13, y: 14.8)
                }
            case .cup:
                CoachMarkSpotlightGlyph(d: "M4 7 H14 V11.5 A4 4 0 0 1 10 15.5 H8 A4 4 0 0 1 4 11.5 Z M14 8.4 H15.4 A2 2 0 0 1 15.4 12.4 H14 M6.5 2.8 V4.6 M9.5 2.8 V4.6",
                                        size: size, color: color)
            case .train:
                ZStack(alignment: .topLeading) {
                    RoundedRectangle(cornerRadius: 3).stroke(color, lineWidth: 1.7).frame(width: 11, height: 11.5).offset(x: 4.5, y: 2.8)
                    CoachMarkSpotlightGlyph(d: "M4.8 9.2 H15.2 M7 17.2 L8.2 14.4 M13 17.2 L11.8 14.4", size: size, color: color)
                }
            case .leaf:
                CoachMarkSpotlightGlyph(d: "M4 16 C4 9 8.5 4.5 16 4 C15.6 11.5 11 16 4 16 Z M4 16 L10.5 9.5", size: size, color: color)
            }
        }
        .frame(width: 20, height: 20, alignment: .topLeading)
    }
}

struct CoachMarkSpotlightTabGlyph: View {
    let kind: String
    let color: Color

    var body: some View {
        let size = CGSize(width: 26, height: 24)
        Group {
            switch kind {
            case "Home":
                CoachMarkSpotlightGlyph(d: "M4.5 11 L13 4 L21.5 11 V19 A1.5 1.5 0 0 1 20 20.5 H15.5 V14.5 H10.5 V20.5 H6 A1.5 1.5 0 0 1 4.5 19 Z",
                                        size: size, color: color, width: 1.8)
            case "Budgets":
                ZStack(alignment: .topLeading) {
                    RoundedRectangle(cornerRadius: 3.2).stroke(color, lineWidth: 1.8).frame(width: 18, height: 14.5).offset(x: 4, y: 5)
                    CoachMarkSpotlightGlyph(d: "M4 9.5 H22 M16.5 14.5 H18.5", size: size, color: color, width: 1.8)
                }
            case "Insights":
                CoachMarkSpotlightGlyph(d: "M5 20 V13 M10.3 20 V8.5 M15.6 20 V11.5 M20.9 20 V4.5", size: size, color: color, width: 1.8)
            default:
                ZStack(alignment: .topLeading) {
                    Circle().stroke(color, lineWidth: 1.8).frame(width: 8.4, height: 8.4).offset(x: 8.8, y: 4.3)
                    CoachMarkSpotlightGlyph(d: "M5 20.5 C6.3 16.4 9.3 14.6 13 14.6 C16.7 14.6 19.7 16.4 21 20.5",
                                            size: size, color: color, width: 1.8)
                }
            }
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
    }
}

private struct CoachMarkSpotlightMonthCard: View {
    let box: CoachMarkSpotlightBox

    var body: some View {
        let cats = CoachMarkSpotlightData.categories
        let spent = cats.reduce(0) { $0 + $1.spent }
        let share = Int((spent / CoachMarkSpotlightData.budget * 100).rounded())
        let barW = box.w - 32 - Double(cats.count - 1) * 2
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 0) {
                Text("Spent in November")
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(C.mute)
                Spacer(minLength: 0)
                Text("\(share)%")
                    .font(.system(size: 12.5, weight: .bold))
                    .monospacedDigit()
                    .foregroundStyle(C.green)
                    .padding(.horizontal, 8)
                    .frame(height: 22)
                    .background(Capsule().fill(C.green.opacity(0.12)))
            }
            .frame(height: 18)
            Text(coachMarkSpotlightMoney(spent))
                .font(.system(size: 28, weight: .bold))
                .monospacedDigit()
                .foregroundStyle(C.ink)
                .frame(height: 34)
                .padding(.top, 2)
            HStack(spacing: 2) {
                ForEach(Array(cats.enumerated()), id: \.offset) { _, c in
                    Rectangle().fill(Color(hex: c.color)).frame(width: barW * c.spent / CoachMarkSpotlightData.budget)
                }
                Spacer(minLength: 0)
            }
            .frame(height: 10)
            .background(Color.rgba(30, 36, 32, 0.07))
            .clipShape(Capsule())
            .padding(.top, 12)
            let columns = [GridItem(.flexible(), spacing: 0, alignment: .leading), GridItem(.flexible(), spacing: 0, alignment: .leading)]
            LazyVGrid(columns: columns, alignment: .leading, spacing: 4) {
                ForEach(Array(cats.enumerated()), id: \.offset) { _, c in
                    HStack(spacing: 0) {
                        Circle().fill(Color(hex: c.color)).frame(width: 8, height: 8).padding(.trailing, 7)
                        Text(c.name).font(.system(size: 13)).foregroundStyle(C.ink).lineLimit(1)
                        Spacer(minLength: 0)
                        Text(coachMarkSpotlightMoney(c.spent)).font(.system(size: 13)).monospacedDigit().foregroundStyle(C.mute)
                    }
                    .padding(.trailing, 10)
                    .frame(height: 20)
                }
            }
            .padding(.top, 14)
        }
        .padding(16)
        .frame(width: box.w, height: box.h, alignment: .topLeading)
        .background(
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .fill(C.card)
                .shadow(color: Color.rgba(52, 44, 30, 0.08), radius: 1, y: 1)
                .shadow(color: Color.rgba(52, 44, 30, 0.07), radius: 14, y: 12)
                .overlay(
                    RoundedRectangle(cornerRadius: 22, style: .continuous).inset(by: 0.5)
                        .stroke(LinearGradient(stops: [.init(color: .white.opacity(0.9), location: 0),
                                                       .init(color: .white.opacity(0), location: 0.04)],
                                               startPoint: .top, endPoint: .bottom), lineWidth: 1)
                )
        )
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Spent this month \(coachMarkSpotlightMoney(spent)), \(share) percent of budget")
    }
}

private struct CoachMarkSpotlightSpendRow: View {
    let spend: CoachMarkSpotlightData.Spend

    var body: some View {
        HStack(spacing: 0) {
            CoachMarkSpotlightSpendGlyph(kind: spend.glyph, color: Color(hex: spend.color))
                .frame(width: 40, height: 40)
                .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(Color(hex: spend.color, opacity: 0x1F / 255)))
            VStack(alignment: .leading, spacing: 1) {
                Text(spend.place).font(.system(size: 16, weight: .semibold)).foregroundStyle(C.ink).lineLimit(1)
                Text("\(spend.category) · \(spend.time)").font(.system(size: 13)).foregroundStyle(C.mute).lineLimit(1)
            }
            .padding(.leading, 12)
            .frame(minWidth: 0, maxWidth: .infinity, alignment: .leading)
            Text("−\(coachMarkSpotlightMoney(spend.amount))")
                .font(.system(size: 16, weight: .semibold))
                .monospacedDigit()
                .foregroundStyle(C.ink)
                .padding(.leading, 10)
        }
        .frame(height: CoachMarkSpotlightLayout.rowH)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(spend.place), \(coachMarkSpotlightMoney(spend.amount)), \(spend.time)")
    }
}

/// The app screen. Static: the tour moves over it.
struct CoachMarkSpotlightHome: View {
    let layout: CoachMarkSpotlightLayout
    let height: Double
    var onAdd: () -> Void = {}

    var body: some View {
        let width = layout.width
        let left = CoachMarkSpotlightData.budget - CoachMarkSpotlightData.categories.reduce(0) { $0 + $1.spent }
        ZStack(alignment: .topLeading) {
            LinearGradient(stops: [.init(color: Color(hex: C.paper[0]), location: 0),
                                   .init(color: Color(hex: C.paper[1]), location: 0.55),
                                   .init(color: Color(hex: C.paper[2]), location: 1)],
                           startPoint: .top, endPoint: .bottom)
                .frame(width: width, height: height)

            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 0) {
                    CoachMarkSpotlightPennyMark()
                    Text("Penny").font(.system(size: 17, weight: .bold)).foregroundStyle(C.ink).padding(.leading, 8)
                    Spacer(minLength: 0)
                    HStack(spacing: 5) {
                        Text("November").font(.system(size: 14, weight: .semibold)).foregroundStyle(C.ink)
                        CoachMarkSpotlightGlyph(d: "M2 3.8 L5 6.8 L8 3.8", size: CGSize(width: 10, height: 10), color: C.ink, width: 1.5)
                    }
                    .padding(.horizontal, 12)
                    .frame(height: 30)
                    .background(Capsule().fill(Color.rgba(30, 36, 32, 0.06)))
                }
                .frame(height: CoachMarkSpotlightLayout.brandH)
                Text("Good evening, Sam")
                    .font(.system(size: 15)).foregroundStyle(C.mute)
                    .frame(height: 20)
                    .padding(.top, 12)
                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    Text(coachMarkSpotlightMoney(left))
                        .font(.system(size: 40, weight: .bold)).monospacedDigit().tracking(-0.5).foregroundStyle(C.ink)
                    Text("left").font(.system(size: 20, weight: .medium)).foregroundStyle(C.mute)
                }
                .frame(height: 48)
                .padding(.top, 2)
                Text("of \(coachMarkSpotlightMoney(CoachMarkSpotlightData.budget)) · 6 days to payday")
                    .font(.system(size: 14)).foregroundStyle(C.mute)
                    .frame(height: 18)
                    .padding(.top, 2)
            }
            .frame(width: width - CoachMarkSpotlightLayout.side * 2, alignment: .leading)
            .offset(x: CoachMarkSpotlightLayout.side, y: layout.top)

            CoachMarkSpotlightMonthCard(box: layout.card)
                .offset(x: layout.card.x, y: layout.card.y)

            Text("Recent")
                .font(.system(size: 17, weight: .bold)).foregroundStyle(C.ink)
                .frame(height: 20)
                .offset(x: CoachMarkSpotlightLayout.side, y: layout.recentTop)

            VStack(spacing: 0) {
                ForEach(Array(CoachMarkSpotlightData.recent.prefix(layout.rows).enumerated()), id: \.offset) { _, s in
                    CoachMarkSpotlightSpendRow(spend: s)
                }
            }
            .frame(width: width - CoachMarkSpotlightLayout.side * 2)
            .offset(x: CoachMarkSpotlightLayout.side, y: layout.recentTop + 30)

            Button(action: onAdd) {
                ZStack {
                    Circle().fill(LinearGradient(stops: [.init(color: Color(hex: 0x4FA27C), location: 0),
                                                         .init(color: C.green, location: 0.5),
                                                         .init(color: Color(hex: 0x22604A), location: 1)],
                                                 startPoint: UnitPoint(x: 0.2, y: 0), endPoint: UnitPoint(x: 0.8, y: 1)))
                    Circle().inset(by: 0.5).stroke(LinearGradient(stops: [.init(color: .white.opacity(0.3), location: 0),
                                                                         .init(color: .white.opacity(0), location: 0.06)],
                                                                  startPoint: .top, endPoint: .bottom), lineWidth: 1)
                    CoachMarkSpotlightGlyph(d: "M12 5 V19 M5 12 H19", size: CGSize(width: 24, height: 24),
                                            color: Color(hex: 0xF4FBF7), width: 2.4)
                }
                .frame(width: CoachMarkSpotlightLayout.fabSize, height: CoachMarkSpotlightLayout.fabSize)
                .background(
                    Circle().fill(C.green)
                        .shadow(color: Color.rgba(20, 60, 42, 0.22), radius: 2, y: 2)
                        .shadow(color: Color.rgba(20, 60, 42, 0.2), radius: 13, y: 12)
                )
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Add a spend")
            .offset(x: layout.fab.x, y: layout.fab.y)

            HStack(spacing: 0) {
                ForEach(Array(CoachMarkSpotlightData.tabs.enumerated()), id: \.offset) { i, tab in
                    let on = i == 0
                    VStack(spacing: 2) {
                        CoachMarkSpotlightTabGlyph(kind: tab, color: on ? C.green : Color(hex: 0x8C918C))
                        Text(tab).font(.system(size: 10.5, weight: on ? .semibold : .medium))
                            .foregroundStyle(on ? C.green : Color(hex: 0x8C918C))
                    }
                    .padding(.top, 6)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .accessibilityElement(children: .combine)
                    .accessibilityAddTraits(on ? [.isSelected] : [])
                }
            }
            .frame(width: width, height: CoachMarkSpotlightLayout.tabH)
            .padding(.bottom, layout.tabBottom)
            .background(Color.rgba(251, 249, 245, 0.96))
            .overlay(alignment: .top) {
                Rectangle().fill(Color.rgba(30, 36, 32, 0.14)).frame(height: 1 / UIScreen.main.scale)
            }
            .offset(y: layout.tabTop)
        }
        .frame(width: width, height: height, alignment: .topLeading)
    }
}
