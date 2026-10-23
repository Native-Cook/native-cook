import SwiftUI

struct HealthAgeDial: View {
    static let loop = HealthAgeDialMotion.loop
    var autoPlay = false

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var playback = HealthAgeDialPlayback()

    var body: some View {
        GeometryReader { proxy in
            let now = Date().timeIntervalSinceReferenceDate
            let tickEnd = scenePhase == .active
                ? playback.tickEnd(at: now).map(Date.init(timeIntervalSinceReferenceDate:))
                : Date()
            TimelineView(HealthAgeDialTimelineSchedule(end: tickEnd, interval: 1 / 120)) { timeline in
                let frameTime = timeline.date.timeIntervalSinceReferenceDate
                HealthAgeDialScreen(frame: playback.frame(at: frameTime),
                                    playback: playback, size: proxy.size, reduced: reduceMotion)
            }
        }
        .background(HealthAgeDialTheme.Colors.page.ignoresSafeArea())
        .ignoresSafeArea(.container, edges: .bottom)
        .onAppear {
            playback.start(autoPlay: autoPlay && !reduceMotion, reduced: reduceMotion,
                           at: Date().timeIntervalSinceReferenceDate)
            playback.setActive(scenePhase == .active, at: Date().timeIntervalSinceReferenceDate)
        }
        .onChange(of: autoPlay) { _, value in
            playback.setConfiguration(autoPlay: value && !reduceMotion, reduced: reduceMotion,
                                      at: Date().timeIntervalSinceReferenceDate)
        }
        .onChange(of: reduceMotion) { _, value in
            playback.setConfiguration(autoPlay: autoPlay && !value, reduced: value,
                                      at: Date().timeIntervalSinceReferenceDate)
        }
        .onChange(of: scenePhase) { _, phase in
            playback.setActive(phase == .active, at: Date().timeIntervalSinceReferenceDate)
        }
    }
}

private struct HealthAgeDialScreen: View {
    let frame: HealthAgeDialRenderFrame
    let playback: HealthAgeDialPlayback
    let size: CGSize
    let reduced: Bool

    private let y0 = -HealthAgeDialTheme.pinSafeTop

    var body: some View {
        let dial = HealthAgeDialTheme.Layout.Dial.self
        let chip = HealthAgeDialTheme.Layout.Chip.self
        let center = size.width / 2
        let dialX = center + dial.cx - HealthAgeDialTheme.pinWidth / 2
        let dialY = y0 + dial.cy
        let chipWidths = chipWidths(for: size.width)
        let chipIconX = HealthAgeDialTheme.Space.chipInset + chipWidths.left
            + HealthAgeDialTheme.Space.chipGap + HealthAgeDialTheme.Space.chipPadLeading + chip.icon / 2
        let chipY = y0 + chip.top + chip.height / 2
        let flightProgress = HealthAgeDialMotion.clamp(frame.scroll / HealthAgeDialPlayback.landingOffset)
        let flight = HealthAgeDialMotion.smoothstep(flightProgress)
        let renderedX = reduced ? dialX : HealthAgeDialMotion.lerp(dialX, chipIconX, flight)
        let renderedY = reduced ? dialY - frame.scroll : HealthAgeDialMotion.lerp(dialY - frame.scroll, chipY, flight)
        let renderedScale = reduced ? 1 : HealthAgeDialMotion.lerp(
            1, chip.ring / (2 * HealthAgeDialRing.outer), flight
        )
        let dialOpacity = reduced
            ? 1 - HealthAgeDialMotion.segment(frame.scroll, 4, 30)
            : 1 - HealthAgeDialMotion.segment(flightProgress, HealthAgeDialMotion.Flight.crossfadeStart, 1)
        let numberOpacity = reduced ? 1 : 1 - HealthAgeDialMotion.segment(
            flightProgress, 0, HealthAgeDialMotion.Flight.numberFadeEnd
        )
        let contentHeight = y0 + HealthAgeDialTheme.Layout.contentBottom + 112

        ZStack(alignment: .topLeading) {
            HealthAgeDialTheme.Colors.page

            HealthAgeDialScrollableContent(frame: frame, size: size, y0: y0)
                .offset(y: -frame.scroll)

            LinearGradient(colors: [
                HealthAgeDialTheme.Colors.page,
                HealthAgeDialTheme.Colors.page.opacity(0.92),
                HealthAgeDialTheme.Colors.page.opacity(0.62),
                HealthAgeDialTheme.Colors.page.opacity(0),
            ], startPoint: .top, endPoint: .bottom)
            .frame(width: size.width, height: y0 + chip.top + chip.height + 10, alignment: .topLeading)
            .allowsHitTesting(false)
            .accessibilityHidden(true)

            HealthAgeDialChip(left: HealthAgeDialTheme.Space.chipInset, top: y0 + chip.top,
                              width: chipWidths.left, selected: false,
                              title: HealthAgeDialTheme.Copy.overviewTitle, value: HealthAgeDialTheme.Copy.overviewValue) {
                HealthAgeDialPetalMark(size: chip.icon, fills: HealthAgeDialPetalMark.overviewFills)
            }
            HealthAgeDialChip(left: HealthAgeDialTheme.Space.chipInset + chipWidths.left + HealthAgeDialTheme.Space.chipGap,
                              top: y0 + chip.top, width: chipWidths.right, selected: true,
                              scale: 1 - (1 - HealthAgeDialMotion.Press.scale) * frame.chipPress,
                              title: HealthAgeDialTheme.Copy.chipTitle, value: HealthAgeDialTheme.Copy.chipValue,
                              hint: "Scrolls back to the dial",
                              action: { playback.glideToDial(at: Date().timeIntervalSinceReferenceDate) }) {
                HealthAgeDialMiniDial(size: chip.icon + 2)
                    .scaleEffect(1 + HealthAgeDialMotion.Land.amplitude * frame.bump)
            }

            HealthAgeDialFullDial(years: frame.years, trail: frame.trail, lift: frame.lift,
                                  numberOpacity: numberOpacity)
                .frame(width: HealthAgeDialRing.size, height: HealthAgeDialRing.size, alignment: .topLeading)
                .scaleEffect(renderedScale)
                .opacity(dialOpacity)
                .position(x: renderedX, y: renderedY)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("Health age 26.1 years, 3.9 years younger than your actual age of 30")
                .accessibilityAddTraits(.isImage)

            HealthAgeDialTabBar(screenWidth: size.width, screenHeight: size.height)

            HealthAgeDialTouch(playback: playback,
                               dialCenter: CGPoint(x: dialX, y: dialY),
                               chipRect: CGRect(x: HealthAgeDialTheme.Space.chipInset + chipWidths.left + HealthAgeDialTheme.Space.chipGap,
                                                y: y0 + chip.top, width: chipWidths.right, height: chip.height),
                               screenHeight: size.height,
                               maximumScroll: max(0, contentHeight - size.height))
                .frame(width: size.width, height: size.height)
                .accessibilityHidden(true)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .clipped()
        .accessibilityScrollAction { edge in
            let delta = size.height * 0.72
            let target: Double
            switch edge {
            case .bottom: target = frame.scroll + delta
            case .top: target = frame.scroll - delta
            default: return
            }
            playback.accessibilityScroll(to: target, at: Date().timeIntervalSinceReferenceDate,
                                         screenHeight: size.height)
        }
    }

    private func chipWidths(for width: Double) -> (left: Double, right: Double) {
        let available = width - HealthAgeDialTheme.Space.chipInset
            - HealthAgeDialTheme.Space.chipInsetTrailing - HealthAgeDialTheme.Space.chipGap
        let left = available * HealthAgeDialTheme.Layout.Chip.leftShare
        return (left, available - left)
    }
}

private struct HealthAgeDialScrollableContent: View {
    let frame: HealthAgeDialRenderFrame
    let size: CGSize
    let y0: Double

    var body: some View {
        let textWidth = size.width - HealthAgeDialTheme.Space.textInset * 2
        let halo = HealthAgeDialTheme.Layout.halo
        let chart = HealthAgeDialTheme.Layout.Chart.self
        ZStack(alignment: .topLeading) {
            RadialGradient(colors: [
                Color(red: 185 / 255, green: 219 / 255, blue: 250 / 255).opacity(0.78),
                Color(red: 196 / 255, green: 224 / 255, blue: 250 / 255).opacity(0.55),
                Color(red: 214 / 255, green: 230 / 255, blue: 248 / 255).opacity(0),
            ], center: .center, startRadius: 0, endRadius: halo.radius)
            .frame(width: halo.radius * 2, height: halo.radius * 2)
            .position(x: size.width / 2 + halo.cx - HealthAgeDialTheme.pinWidth / 2,
                      y: y0 + halo.cy)
            .opacity(1 - HealthAgeDialMotion.segment(frame.scroll, 0, HealthAgeDialPlayback.landingOffset * 0.7))
            .accessibilityHidden(true)

            HealthAgeDialInfoGlyph(size: HealthAgeDialTheme.Layout.info.diameter)
                .position(x: size.width - (HealthAgeDialTheme.pinWidth - HealthAgeDialTheme.Layout.info.cx),
                          y: y0 + HealthAgeDialTheme.Layout.info.cy)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("About Health Age")

            label(HealthAgeDialTheme.Copy.title, style: HealthAgeDialTheme.TypeStyle.title,
                  left: HealthAgeDialTheme.Space.textInset,
                  top: y0 + topFor(HealthAgeDialTheme.Baseline.title, HealthAgeDialTheme.TypeStyle.title),
                  width: textWidth + 8, height: 82, lines: 2, header: true)
            label(HealthAgeDialTheme.Copy.body,
                  style: HealthAgeDialTheme.TypeStyle.body,
                  left: HealthAgeDialTheme.Space.textInset,
                  top: y0 + topFor(HealthAgeDialTheme.Baseline.body, HealthAgeDialTheme.TypeStyle.body),
                  width: textWidth + 8, height: 116, lines: 4)

            label(HealthAgeDialTheme.Copy.healthAgeLabel, style: HealthAgeDialTheme.TypeStyle.statLabel,
                  left: HealthAgeDialTheme.Space.textInset,
                  top: y0 + topFor(HealthAgeDialTheme.Baseline.statLabel, HealthAgeDialTheme.TypeStyle.statLabel),
                  width: 150, height: 24)
            label(String(format: "%.1f years", HealthAgeDialTheme.ages.actual + frame.years),
                  style: HealthAgeDialTheme.TypeStyle.statValue,
                  left: HealthAgeDialTheme.Space.textInset,
                  top: y0 + topFor(HealthAgeDialTheme.Baseline.statValue, HealthAgeDialTheme.TypeStyle.statValue),
                  width: 150, height: 28, accessibility: "Health Age 26.1 years")
            let secondColumn = size.width / 2 + HealthAgeDialTheme.Space.statColumn - HealthAgeDialTheme.pinWidth / 2
            label(HealthAgeDialTheme.Copy.actualAgeLabel, style: HealthAgeDialTheme.TypeStyle.statLabel, left: secondColumn,
                  top: y0 + topFor(HealthAgeDialTheme.Baseline.statLabel, HealthAgeDialTheme.TypeStyle.statLabel),
                  width: 150, height: 24)
            label(HealthAgeDialTheme.Copy.actualAgeValue, style: HealthAgeDialTheme.TypeStyle.statValue, left: secondColumn,
                  top: y0 + topFor(HealthAgeDialTheme.Baseline.statValue, HealthAgeDialTheme.TypeStyle.statValue),
                  width: 150, height: 28)

            label(HealthAgeDialTheme.Copy.history, style: HealthAgeDialTheme.TypeStyle.heading,
                  left: HealthAgeDialTheme.Space.textInset,
                  top: y0 + topFor(HealthAgeDialTheme.Layout.historyHeading, HealthAgeDialTheme.TypeStyle.heading),
                  width: 260, height: 30, header: true)
            HealthAgeDialHistoryChart(progress: frame.lineProgress, opacity: frame.revealOpacity)
                .frame(width: HealthAgeDialHistoryChart.width, height: HealthAgeDialHistoryChart.height)
                .position(x: chart.left + HealthAgeDialHistoryChart.width / 2,
                          y: y0 + chart.top + HealthAgeDialHistoryChart.height / 2)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("Health age history, November to March: younger than your actual age, trending younger")
            ForEach(HealthAgeDialTheme.months, id: \.self) { month in
                let index = HealthAgeDialTheme.months.firstIndex(of: month) ?? 0
                label(month, style: HealthAgeDialTheme.TypeStyle.axis,
                      left: chart.left + (chart.right - chart.left) * Double(index) / Double(chart.columns) + chart.labelOffset,
                      top: y0 + topFor(chart.labelBaseline, HealthAgeDialTheme.TypeStyle.axis),
                      width: 40, height: 22)
            }
            label(HealthAgeDialTheme.Copy.older, style: HealthAgeDialTheme.TypeStyle.guide, left: chart.guideX,
                  top: y0 + topFor(chart.olderBaseline, HealthAgeDialTheme.TypeStyle.guide),
                  width: 55, height: 20, opacity: frame.guides * frame.revealOpacity)
            label(HealthAgeDialTheme.Copy.younger, style: HealthAgeDialTheme.TypeStyle.guide, left: chart.guideX,
                  top: y0 + topFor(chart.youngerBaseline, HealthAgeDialTheme.TypeStyle.guide),
                  width: 64, height: 20, opacity: frame.guides * frame.revealOpacity)

            label(HealthAgeDialTheme.Copy.raising, style: HealthAgeDialTheme.TypeStyle.heading,
                  left: HealthAgeDialTheme.Space.textInset,
                  top: y0 + topFor(HealthAgeDialTheme.Layout.raisingHeading, HealthAgeDialTheme.TypeStyle.heading),
                  width: 300, height: 30, header: true)
            HealthAgeDialFactorCard(index: 0, frame: frame, width: size.width - 2 * HealthAgeDialTheme.Space.cardInset)
                .position(x: size.width / 2, y: y0 + HealthAgeDialTheme.Layout.cards[0].top + HealthAgeDialTheme.Layout.cards[0].height / 2)
            HealthAgeDialFactorCard(index: 1, frame: frame, width: size.width - 2 * HealthAgeDialTheme.Space.cardInset)
                .position(x: size.width / 2, y: y0 + HealthAgeDialTheme.Layout.cards[1].top + HealthAgeDialTheme.Layout.cards[1].height / 2)
        }
        .frame(width: size.width, height: y0 + HealthAgeDialTheme.Layout.contentBottom + 112, alignment: .topLeading)
    }

    private func label(_ text: String, style: HealthAgeDialTheme.TextStyle, left: Double, top: Double,
                       width: Double, height: Double, lines: Int = 1, opacity: Double = 1,
                       accessibility: String? = nil, header: Bool = false) -> some View {
        HealthAgeDialLabel(text: text, style: style, lines: lines)
            .equatable()
            .frame(width: width, height: height, alignment: .topLeading)
            .position(x: left + width / 2, y: top + height / 2)
            .opacity(opacity)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(accessibility ?? text)
            .accessibilityAddTraits(header ? .isHeader : [])
    }

    private func topFor(_ baseline: Double, _ style: HealthAgeDialTheme.TextStyle) -> Double {
        if let line = style.line { return baseline - (line + 0.712 * style.size) / 2 }
        return baseline - 0.953 * style.size
    }
}

private struct HealthAgeDialFullDial: View {
    let years: Double
    let trail: Double
    let lift: Double
    let numberOpacity: Double

    var body: some View {
        let size = HealthAgeDialRing.size
        let numberStyle = HealthAgeDialTheme.TypeStyle.number
        let numberTop = size / 2 + HealthAgeDialTheme.Baseline.number - HealthAgeDialTheme.Layout.Dial.cy
            - 0.953 * numberStyle.size
        ZStack(alignment: .topLeading) {
            HealthAgeDialRing(years: years, trail: trail, lift: lift)
            HealthAgeDialOdometer(years: years)
                .position(x: size / 2 + HealthAgeDialTheme.Layout.numberCenterX - HealthAgeDialTheme.Layout.Dial.cx,
                          y: numberTop + round(numberStyle.size * 1.2) / 2)
                .opacity(numberOpacity)
            HealthAgeDialLabel(text: HealthAgeDialTheme.Copy.unit, style: HealthAgeDialTheme.TypeStyle.unit,
                               alignment: .center)
                .frame(width: 90, height: 24)
                .position(x: size / 2 + HealthAgeDialTheme.Layout.unitCenterX - HealthAgeDialTheme.Layout.Dial.cx,
                          y: size / 2 + HealthAgeDialTheme.Baseline.unit - HealthAgeDialTheme.Layout.Dial.cy
                              - 0.953 * HealthAgeDialTheme.TypeStyle.unit.size + 12)
                .opacity(numberOpacity)
                .accessibilityHidden(true)
        }
        .frame(width: size, height: size, alignment: .topLeading)
    }
}

private struct HealthAgeDialChip<Icon: View>: View {
    let left: Double
    let top: Double
    let width: Double
    let selected: Bool
    var scale = 1.0
    let title: String
    let value: String
    var hint: String? = nil
    var action: (() -> Void)? = nil
    @ViewBuilder let icon: () -> Icon

    var body: some View {
        let chip = HealthAgeDialTheme.Layout.Chip.self
        ZStack(alignment: .topLeading) {
            Capsule()
                .fill(.ultraThinMaterial)
                .overlay(Capsule().fill(selected ? HealthAgeDialTheme.Colors.chipSelected.opacity(0.86) : HealthAgeDialTheme.Colors.chipFill))
                .overlay(Capsule().stroke(selected ? Color.clear : HealthAgeDialTheme.Colors.chipBorder, lineWidth: 1))
                .shadow(color: selected ? Color(red: 74 / 255, green: 72 / 255, blue: 128 / 255).opacity(0.06) : .clear,
                        radius: 10, y: 3)
            icon()
                .frame(width: chip.icon + 2, height: chip.height)
                .position(x: HealthAgeDialTheme.Space.chipPadLeading - 1 + (chip.icon + 2) / 2, y: chip.height / 2)
            let textLeft = HealthAgeDialTheme.Space.chipPadLeading + chip.icon + HealthAgeDialTheme.Space.chipIconGap
            HealthAgeDialLabel(text: title, style: HealthAgeDialTheme.TypeStyle.chipTitle)
                .frame(width: width - textLeft - 4, height: 22)
                .position(x: textLeft + (width - textLeft - 4) / 2,
                          y: topFor(HealthAgeDialTheme.Baseline.chipTitle, HealthAgeDialTheme.TypeStyle.chipTitle) - chip.top + 11)
                .accessibilityHidden(true)
            HealthAgeDialLabel(text: value, style: HealthAgeDialTheme.TypeStyle.chipValue)
                .frame(width: width - textLeft - 4, height: 22)
                .position(x: textLeft + (width - textLeft - 4) / 2,
                          y: topFor(HealthAgeDialTheme.Baseline.chipValue, HealthAgeDialTheme.TypeStyle.chipValue) - chip.top + 11)
                .accessibilityHidden(true)
        }
        .frame(width: width, height: chip.height, alignment: .topLeading)
        .scaleEffect(scale)
        .position(x: left + width / 2, y: top + chip.height / 2)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(title), \(value)")
        .accessibilityHint(hint ?? "")
        .accessibilityAddTraits(selected ? [.isButton, .isSelected] : [])
        .healthAgeDialAccessibilityAction(action)
    }

    private func topFor(_ baseline: Double, _ style: HealthAgeDialTheme.TextStyle) -> Double {
        baseline - 0.953 * style.size
    }
}

private extension View {
    @ViewBuilder
    func healthAgeDialAccessibilityAction(_ action: (() -> Void)?) -> some View {
        if let action { self.accessibilityAction { action() } } else { self }
    }
}

private struct HealthAgeDialFactorCard: View {
    let index: Int
    let frame: HealthAgeDialRenderFrame
    let width: Double

    private var title: String { HealthAgeDialTheme.Copy.factorTitles[index] }
    private var years: Double { HealthAgeDialTheme.Copy.factorYears[index] }
    private var bodyCopy: String { HealthAgeDialTheme.Copy.factorBodies[index] }

    var body: some View {
        let height = HealthAgeDialTheme.Layout.cards[index].height
        let badge = HealthAgeDialMotion.pop(index == 0 ? frame.badge1 : frame.badge2)
        let count = index == 0 ? frame.count1 : frame.count2
        let tint = index == 0 ? HealthAgeDialTheme.Colors.sleepTint : HealthAgeDialTheme.Colors.hrvTint
        let titleColor = index == 0 ? HealthAgeDialTheme.Colors.sleep : HealthAgeDialTheme.Colors.hrv
        ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: HealthAgeDialTheme.Radius.card, style: .continuous)
                .fill(HealthAgeDialTheme.Colors.card)
                .shadow(color: Color(red: 77 / 255, green: 74 / 255, blue: 136 / 255).opacity(0.04), radius: 14, y: 4)
            Circle().fill(tint)
                .frame(width: HealthAgeDialTheme.Layout.cardIcon.diameter, height: HealthAgeDialTheme.Layout.cardIcon.diameter)
                .position(x: HealthAgeDialTheme.Space.cardIconLeading + HealthAgeDialTheme.Layout.cardIcon.diameter / 2,
                          y: HealthAgeDialTheme.Layout.cardIcon.cy)
            Group {
                if index == 0 {
                    HealthAgeDialBedGlyph(size: HealthAgeDialTheme.Layout.cardIcon.glyph, color: HealthAgeDialTheme.Colors.sleepGlyph)
                } else {
                    HealthAgeDialPulseGlyph(size: HealthAgeDialTheme.Layout.cardIcon.glyph, color: HealthAgeDialTheme.Colors.hrvGlyph)
                }
            }
            .scaleEffect(badge.scale)
            .opacity(badge.opacity)
            .position(x: HealthAgeDialTheme.Space.cardIconLeading + HealthAgeDialTheme.Layout.cardIcon.diameter / 2,
                      y: HealthAgeDialTheme.Layout.cardIcon.cy)
            cardLabel(title, style: HealthAgeDialTheme.TypeStyle.cardTitle, color: titleColor,
                      top: topFor(HealthAgeDialTheme.Layout.cardTitleBaseline, HealthAgeDialTheme.TypeStyle.cardTitle), height: 22)
            cardLabel(String(format: "+%.1f years", years * count), style: HealthAgeDialTheme.TypeStyle.cardValue,
                      top: topFor(HealthAgeDialTheme.Layout.cardValueBaseline, HealthAgeDialTheme.TypeStyle.cardValue), height: 38)
            cardLabel(bodyCopy, style: HealthAgeDialTheme.TypeStyle.cardBody,
                      top: topFor(HealthAgeDialTheme.Layout.cardBodyBaseline, HealthAgeDialTheme.TypeStyle.cardBody),
                      height: 80, lines: 4)
        }
        .frame(width: width, height: height, alignment: .topLeading)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(title), plus \(years) years. \(bodyCopy)")
    }

    private func cardLabel(_ text: String, style: HealthAgeDialTheme.TextStyle, color: Color? = nil,
                           top: Double, height: Double, lines: Int = 1) -> some View {
        HealthAgeDialLabel(text: text, style: style, color: color, lines: lines)
            .frame(width: width - HealthAgeDialTheme.Space.cardTextLeading - 12, height: height)
            .position(x: HealthAgeDialTheme.Space.cardTextLeading + (width - HealthAgeDialTheme.Space.cardTextLeading - 12) / 2,
                      y: top + height / 2)
            .accessibilityHidden(true)
    }

    private func topFor(_ baseline: Double, _ style: HealthAgeDialTheme.TextStyle) -> Double {
        if let line = style.line { return baseline - (line + 0.712 * style.size) / 2 }
        return baseline - 0.953 * style.size
    }
}

private struct HealthAgeDialTabBar: View {
    let screenWidth: Double
    let screenHeight: Double

    var body: some View {
        let tab = HealthAgeDialTheme.Layout.Tab.self
        let inset = (tab.width - 3 * tab.item) / 2
        ZStack(alignment: .topLeading) {
            Capsule()
                .fill(.ultraThinMaterial)
                .overlay(Capsule().fill(Color.white.opacity(0.22)))
                .overlay(Capsule().stroke(Color.white.opacity(0.85), lineWidth: 1))
                .shadow(color: .black.opacity(0.1), radius: 16, y: 5)
            Capsule()
                .fill(HealthAgeDialTheme.Colors.tabSelected)
                .frame(width: tab.pillWidth, height: tab.pillHeight)
                .position(x: inset + tab.item + tab.item / 2, y: tab.height / 2)
            ForEach(0..<3, id: \.self) { index in
                VStack(spacing: tab.labelGap) {
                    tabIcon(index)
                    Text(HealthAgeDialTheme.Copy.tabs[index])
                        .font(.system(size: HealthAgeDialTheme.TypeStyle.tab.size, weight: .semibold))
                        .foregroundStyle(index == 1 ? HealthAgeDialTheme.Colors.blue : HealthAgeDialTheme.Colors.tabText)
                }
                .frame(width: tab.item, height: tab.height)
                .position(x: inset + Double(index) * tab.item + tab.item / 2, y: tab.height / 2)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(HealthAgeDialTheme.Copy.tabs[index])
                .accessibilityAddTraits(index == 1 ? .isSelected : [])
            }
        }
        .frame(width: tab.width, height: tab.height, alignment: .topLeading)
        .position(x: screenWidth / 2, y: screenHeight - HealthAgeDialTheme.Space.tabBottom - tab.height / 2)
        .accessibilityElement(children: .contain)
        .accessibilityLabel("Tab bar")
    }

    @ViewBuilder
    private func tabIcon(_ index: Int) -> some View {
        if index == 0 {
            HealthAgeDialHeartGlyph(size: HealthAgeDialTheme.Layout.Tab.heart, color: HealthAgeDialTheme.Colors.tabText)
        } else if index == 1 {
            HealthAgeDialPetalMark(size: HealthAgeDialTheme.Layout.Tab.petal,
                                   fills: Array(repeating: HealthAgeDialTheme.Colors.tabBlue, count: 8), tabShape: true)
        } else {
            HealthAgeDialGridGlyph(size: HealthAgeDialTheme.Layout.Tab.grid, color: HealthAgeDialTheme.Colors.tabText)
        }
    }
}
