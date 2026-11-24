import SwiftUI

/// Ledger's activity history: day sections whose headers stick, get pushed by the next and shrink into a frosted pill
/// with a rolling running total; a month index on the right follows and can be dragged to jump. One scroll value
/// drives it all; the loop scripts a real scroll of the list.
struct StickySectionHeaders: View {
    static let loop: Double = StickySectionHeadersMotion.loop

    typealias P = StickySectionHeadersPalette
    private static let topBarH: CGFloat = 44
    private static let listTopH: Double = 212

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var scroll: StickySectionHeadersScroll
    @State private var driver: StickySectionHeadersDriver
    private let autoPlay: Bool

    init(autoPlay: Bool = true) {
        let scroll = StickySectionHeadersScroll()
        let geo = StickySectionHeadersMotion.Geometry(sections: StickySectionHeadersData.sections, months: StickySectionHeadersData.months.count, listTopH: Self.listTopH)
        _scroll = State(initialValue: scroll)
        _driver = State(initialValue: StickySectionHeadersDriver(geo: geo, scroll: scroll))
        self.autoPlay = autoPlay
    }

    var body: some View {
        VStack(spacing: 0) {
            StickySectionHeadersTopBar(scroll: scroll)
                .frame(height: Self.topBarH)
                .zIndex(2)
            StickySectionHeadersList(
                sections: StickySectionHeadersData.sections,
                months: StickySectionHeadersData.months,
                monthNames: StickySectionHeadersData.monthNames,
                listTopHeight: Self.listTopH,
                listTop: StickySectionHeadersSummary(),
                footer: StickySectionHeadersFooter(),
                scroll: scroll,
                driver: driver)
                .ignoresSafeArea(edges: .bottom)
        }
        .background(P.bg.ignoresSafeArea())
        .preferredColorScheme(.light)
        .onAppear {
            driver.autoPlay = autoPlay
            driver.reducedMotion = reduceMotion
            driver.start()
        }
        .onDisappear { driver.stop() }
        .onChange(of: reduceMotion) { _, v in driver.reducedMotion = v }
    }
}

private struct StickySectionHeadersTopBar: View {
    typealias P = StickySectionHeadersPalette
    let scroll: StickySectionHeadersScroll

    var body: some View {
        HStack(spacing: 0) {
            Text("Accounts")
                .font(.system(size: 17, weight: .medium))
                .foregroundStyle(P.ink)
                .frame(maxWidth: .infinity, alignment: .leading)
            StickySectionHeadersBarTitle(scroll: scroll)
            Text("Export")
                .font(.system(size: 17, weight: .medium))
                .foregroundStyle(P.ink)
                .frame(maxWidth: .infinity, alignment: .trailing)
        }
        .padding(.horizontal, P.side)
        .background(P.bg)
    }
}

/// "Activity" in the bar fades in as the large title scrolls away (36 → 64 pt, rising 6 pt).
private struct StickySectionHeadersBarTitle: View {
    let scroll: StickySectionHeadersScroll
    var body: some View {
        let u = StickySectionHeadersMotion.clamp01((scroll.s - 36) / 28)
        Text("Activity")
            .font(.system(size: 17, weight: .semibold))
            .foregroundStyle(StickySectionHeadersPalette.ink)
            .opacity(u)
            .offset(y: 6 * (1 - u))
    }
}

/// The list header: the large title and November's summary card (exactly 212 pt tall).
private struct StickySectionHeadersSummary: View {
    typealias P = StickySectionHeadersPalette
    private static let novSpent = StickySectionHeadersData.sections.filter { $0.month == 0 }.reduce(0) { $0 + $1.spent }
    private static let bars: [Double] = (0..<18).map { i in
        let d = Double(i)
        return 0.18 + 0.82 * abs(sin(d * 1.71 + 0.4) * cos(d * 0.53))
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("Activity")
                .font(.system(size: 34, weight: .heavy))
                .tracking(-0.6)
                .foregroundStyle(P.ink)
                .frame(height: 41)
                .padding(.leading, P.side)
            card
                .padding(.horizontal, P.side)
                .padding(.top, 12)
            Spacer(minLength: 0)
        }
        .padding(.top, 2)
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var card: some View {
        HStack(alignment: .bottom, spacing: 0) {
            VStack(alignment: .leading, spacing: 0) {
                Text("Spent in November")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(P.mute)
                Text(StickySectionHeadersData.formatMoney(Self.novSpent, signed: false))
                    .font(.system(size: 32, weight: .bold))
                    .tracking(-0.6)
                    .monospacedDigit()
                    .foregroundStyle(P.ink)
                    .frame(height: 38)
                    .padding(.top, 4)
                (Text("18% less").foregroundColor(P.income).fontWeight(.semibold) + Text(" than October").foregroundColor(P.mute))
                    .font(.system(size: 13))
                    .padding(.top, 6)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            HStack(alignment: .bottom, spacing: 3) {
                ForEach(0..<Self.bars.count, id: \.self) { i in
                    RoundedRectangle(cornerRadius: 2)
                        .fill(i == Self.bars.count - 1 ? P.ink : Color(.sRGB, red: 28 / 255, green: 27 / 255, blue: 25 / 255, opacity: 0.16))
                        .frame(width: 4, height: 8 + Self.bars[i] * 46)
                }
            }
            .frame(height: 56, alignment: .bottom)
            .padding(.leading, 12)
        }
        .padding(18)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomLeading)
        .frame(height: 136)
        .background {
            let shape = RoundedRectangle(cornerRadius: 20, style: .continuous)
            ZStack(alignment: .top) {
                shape.fill(P.hex(0xfbfaf6)).shadow(color: P.shadow.opacity(0.1), radius: 22, y: 10)
                shape.fill(LinearGradient(stops: [.init(color: P.hex(0xfffefb), location: 0), .init(color: P.hex(0xfbfaf6), location: 0.6), .init(color: P.hex(0xf6f4ef), location: 1)], startPoint: .top, endPoint: .bottom))
                shape.strokeBorder(P.hair, lineWidth: P.hairline)
                Rectangle().fill(Color.white.opacity(0.95)).frame(height: 1).padding(.horizontal, 16)
            }
        }
    }
}

private struct StickySectionHeadersFooter: View {
    typealias P = StickySectionHeadersPalette
    var body: some View {
        VStack(spacing: 4) {
            Text("That’s everything since June")
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(P.ink)
            Text("Older activity lives in Statements.")
                .font(.system(size: 13))
                .foregroundStyle(P.mute)
        }
        .padding(.top, 36)
        .padding(.trailing, P.railW - P.side)
        .frame(maxWidth: .infinity)
    }
}
