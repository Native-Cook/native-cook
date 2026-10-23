import SwiftUI

/// The three below-hero screens from Pages.tsx. Every coordinate remains in the
/// 1206 px reference grid; the parent supplies the safe-area origin and places
/// this 1206 × 2622*k canvas at its top-leading corner.
struct VitalsChipMorphPages: View {
    let lo: Int
    let hi: Int
    let u: Double
    let quiet: Bool
    let k: Double

    private typealias T = VitalsChipMorphTheme
    private typealias M = VitalsChipMorphMotion
    private var s: Double { 3 * k }

    var body: some View {
        ZStack(alignment: .topLeading) {
            layer(0, 0) { readinessChecks }
            layer(0, 1) { text("Ready", x: T.space.screenInset, capTop: T.layout.pageHeadingCapTop, spec: T.type.heading) }
            ForEach(Array(Self.readyBody.enumerated()), id: \.offset) { index, value in
                layer(0, 2 + index) { text(value, x: T.space.screenInset, capTop: T.layout.readyBodyTop + Double(index) * T.space.bodyLine, spec: T.type.body) }
            }
            layer(0, 6) { text("Raising Your Score", x: T.space.screenInset, capTop: T.layout.raisingCapTop, spec: T.type.section) }
            layer(0, 7) { readinessVitals }
            layer(0, 8) { readinessSleep }
            ForEach(Array(Self.fitHeading.enumerated()), id: \.offset) { index, value in
                layer(1, 1 + index) { text(value, x: T.space.screenInset, capTop: T.layout.pageHeadingCapTop + Double(index) * T.space.headingLine, spec: T.type.heading) }
            }
            ForEach(Array(Self.fitBody.enumerated()), id: \.offset) { index, value in
                layer(1, 3 + index) { text(value, x: T.space.screenInset, capTop: T.layout.fitBodyTop + Double(index) * T.space.bodyLine, spec: T.type.body) }
            }
            layer(1, 5) { fitnessChart }
            layer(1, 6) { fitnessMetrics }
            ForEach(Array(Self.heartHeading.enumerated()), id: \.offset) { index, value in
                layer(2, 1 + index) { text(value, x: T.space.screenInset, capTop: T.layout.pageHeadingCapTop + Double(index) * T.space.headingLine, spec: T.type.heading) }
            }
            ForEach(Array(Self.heartBody.enumerated()), id: \.offset) { index, value in
                layer(2, 3 + index) { text(value, x: T.space.screenInset, capTop: T.layout.heartBodyTop + Double(index) * T.space.bodyLine, spec: T.type.body) }
            }
            layer(2, 6) { heartChart }
            layer(2, 7) { heartMetrics }
        }
        .frame(width: 1206 * k, height: 2622 * k, alignment: .topLeading)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    private func layer<Content: View>(_ screen: Int, _ index: Int, @ViewBuilder content: () -> Content) -> some View {
        let state = pageState(screen: screen, index: index)
        return content()
            .frame(width: 1206 * k, height: 2622 * k, alignment: .topLeading)
            .opacity(state.opacity)
            .offset(x: state.x * k)
    }

    private func pageState(screen: Int, index: Int) -> (opacity: Double, x: Double) {
        guard lo != hi else { return (lo == screen ? 1 : 0, 0) }
        if screen == hi {
            let a = quiet ? M.seg(u, 0.5, 1) : M.easeOut(M.seg(u, 0.38 + 0.03 * Double(index), 0.86 + 0.03 * Double(index)))
            return (a, quiet ? 0 : T.space.pageSlide * (1 - a))
        }
        if screen == lo {
            let d = quiet ? M.seg(u, 0, 0.5) : M.easeInOut(M.seg(u, 0.02 * Double(index), 0.4 + 0.02 * Double(index)))
            return (1 - d, quiet ? 0 : -T.space.pageSlide * d)
        }
        return (0, 0)
    }

    private func text(_ value: String, x: Double, capTop: Double, spec: T.TextSpec, color: Color? = nil) -> some View {
        VitalsChipMorphText(value: value, spec: spec, x: x, capTop: capTop, k: k, color: color)
    }

    private func at<Content: View>(_ content: Content, x: Double, y: Double) -> some View {
        content.position(x: x * k, y: y * k)
    }

    private var readinessChecks: some View {
        let checks = T.layout.checks
        return ZStack(alignment: .topLeading) {
            at(VitalsChipMorphTargetIcon(s: s), x: checks.groups[0].icon, y: checks.cy)
            at(VitalsChipMorphDotsIcon(s: s), x: checks.groups[1].icon, y: checks.cy)
            at(VitalsChipMorphBedIcon(s: s), x: checks.groups[2].icon, y: checks.cy)
            ForEach(checks.groups.indices, id: \.self) { index in at(VitalsChipMorphCheckCircle(size: checks.checkD / 3 * s), x: checks.groups[index].check, y: checks.cy) }
            Canvas { context, _ in
                context.scaleBy(x: k, y: k)
                context.fill(Path(CGRect(x: checks.dividers[0], y: checks.cy - checks.dividerH / 2, width: 2, height: checks.dividerH)), with: .color(T.colors.hairline))
                var dashed = Path(); dashed.move(to: CGPoint(x: checks.dividers[1] + 1 / k, y: checks.cy - checks.dividerH / 2)); dashed.addLine(to: CGPoint(x: checks.dividers[1] + 1 / k, y: checks.cy + checks.dividerH / 2))
                context.stroke(dashed, with: .color(T.colors.hairline), style: StrokeStyle(lineWidth: 1.4 / k, dash: [2 / k, 2.6 / k]))
            }.frame(width: 1206 * k, height: 2622 * k)
        }
    }

    private func linkRow(_ label: String, x: Double, capTop: Double, chevronX: Double) -> some View {
        let check = 15.0 * s
        return ZStack(alignment: .topLeading) {
            at(VitalsChipMorphCheckCircle(size: check), x: x + check / (2 * k), y: capTop + 0.35 * T.type.link.size * s / k)
            text(label, x: x + T.space.linkLabel, capTop: capTop, spec: T.type.link)
            VitalsChipMorphChevron(s: s).offset(x: chevronX * k, y: (capTop - 1 * s / k) * k)
        }
    }

    private var readinessVitals: some View {
        ZStack(alignment: .topLeading) {
            linkRow("Vitals, Overnight", x: T.space.screenInset + 3, capTop: T.layout.vitalsLinkCapTop, chevronX: T.layout.vitalsChevronX)
            text("Typical", x: T.space.screenInset, capTop: T.layout.typicalCapTop, spec: T.type.value)
            miniChart
        }
    }

    private var miniChart: some View {
        let chart = T.layout.miniChart
        let kinds: [VitalsChipMorphMiniChartIcon.Kind] = [.heart, .lungs, .temp, .oxygen, .wave]
        return ZStack(alignment: .topLeading) {
            Canvas { context, _ in
                context.scaleBy(x: k, y: k)
                context.fill(Path(roundedRect: CGRect(x: chart.x, y: chart.lineTop, width: chart.w, height: 9), cornerRadius: 4.5), with: .color(T.colors.bandLine))
                context.fill(Path(CGRect(x: chart.x, y: chart.bandTop, width: chart.w, height: chart.bandH)), with: .color(T.colors.band))
                context.fill(Path(roundedRect: CGRect(x: chart.x, y: chart.lineBottom, width: chart.w, height: 9), cornerRadius: 4.5), with: .color(T.colors.bandLine))
                for point in chart.dots {
                    context.fill(Path(ellipseIn: CGRect(x: point.x - chart.dotD / 2, y: point.y - chart.dotD / 2, width: chart.dotD, height: chart.dotD)), with: .color(.white))
                    context.stroke(Path(ellipseIn: CGRect(x: point.x - chart.dotD / 2, y: point.y - chart.dotD / 2, width: chart.dotD, height: chart.dotD)), with: .color(T.colors.link), lineWidth: 6)
                }
            }.frame(width: 1206 * k, height: 2622 * k)
            ForEach(chart.dots.indices, id: \.self) { index in at(VitalsChipMorphMiniChartIcon(kind: kinds[index], s: s), x: chart.dots[index].x, y: chart.iconsCy) }
        }
    }

    private var readinessSleep: some View {
        let ring = T.layout.sleepRing
        return ZStack(alignment: .topLeading) {
            linkRow("Sleep Score", x: T.space.screenInset + 3, capTop: T.layout.sleepLinkCapTop, chevronX: T.layout.sleepChevronX)
            text("High", x: T.space.screenInset, capTop: T.layout.highCapTop, spec: T.type.value).blur(radius: T.layout.underGlassBlur / 2 * k).opacity(0.72)
            Canvas { context, _ in
                context.scaleBy(x: k, y: k); context.opacity = 0.8
                context.drawLayer { soft in
                    soft.addFilter(.blur(radius: T.layout.underGlassBlur / 2))
                    for (start, end, color) in [(268.0, 358.0, T.colors.sleepTeal), (4.0, 58.0, T.colors.sleepCoral), (64.0, 262.0, T.colors.sleepBlue)] {
                        soft.stroke(VitalsChipMorphIcons.arc(CGPoint(x: ring.cx, y: ring.cy), radius: ring.r, from: start, to: end), with: .color(color), lineWidth: ring.stroke)
                    }
                    VitalsChipMorphIcons.baselineText(&soft, "84", centerX: ring.cx, baseline: ring.cy + 7.5 * s / k, size: 21 * s / k, weight: .bold, color: T.colors.muted)
                }
            }.frame(width: 1206 * k, height: 2622 * k)
        }
    }

    private var fitnessChart: some View {
        let chart = T.layout.fitChart
        let move = [22.0, 36, 70, 44, 58, 30, 24, 22, 20]
        let exercise = [14.0, 26, 66, 60, 18, 22, 30, 16, 14]
        let stats = ["375/500 CAL", "19/30 MIN", "4/12 HRS"]
        let colors = [T.colors.movePink, T.colors.exerciseText, T.colors.standCyan]
        return ZStack(alignment: .topLeading) {
            Canvas { context, _ in
                context.scaleBy(x: k, y: k)
                for y in [chart.rowsCy[0] + chart.base, chart.rowsCy[1] + chart.base] { context.fill(Path(CGRect(x: chart.x, y: y, width: chart.w * chart.gridW, height: 1.5)), with: .color(T.colors.hairline.opacity(0.6))) }
                func bars(_ values: [Double], row: Double, color: Color) { for (index, value) in values.enumerated() { context.fill(Path(roundedRect: CGRect(x: chart.x + chart.barX + Double(index) * chart.barPitch, y: row + chart.base - value, width: chart.barW, height: value), cornerRadius: chart.barW / 2), with: .color(color)) } }
                bars(move, row: chart.rowsCy[0], color: T.colors.movePink); bars(exercise, row: chart.rowsCy[1], color: T.colors.exerciseText)
                for index in 0..<5 { context.fill(Path(ellipseIn: CGRect(x: chart.x + chart.dotX + Double(index) * chart.dotPitch - chart.dotR, y: chart.rowsCy[2] - chart.dotR, width: 2 * chart.dotR, height: 2 * chart.dotR)), with: .color(T.colors.standCyan.opacity(index == 4 ? 0.3 : 1))) }
            }.frame(width: 1206 * k, height: 2622 * k)
            ForEach(["12 AM", "6 AM", "12 PM", "6 PM"].indices, id: \.self) { index in text(["12 AM", "6 AM", "12 PM", "6 PM"][index], x: chart.x + Double(index) * chart.labelPitch, capTop: chart.labelsTop + VitalsChipMorphText.CAP_OFFSET * T.type.axis.size * s / k, spec: T.type.axis) }
            ForEach(stats.indices, id: \.self) { index in text(stats[index], x: T.layout.fitStats.x, capTop: T.layout.fitStats.capTops[index], spec: T.type.stat, color: colors[index]) }
        }
    }

    private func metric(title: String, value: String, sub: String, x: Double, titleColor: Color) -> some View {
        ZStack(alignment: .topLeading) {
            text(title, x: x, capTop: T.layout.fitMetricsCapTop, spec: T.type.metricLink, color: titleColor)
            metricChevron(title, x: x, capTop: T.layout.fitMetricsCapTop)
            text(value, x: x, capTop: T.layout.fitMetricsCapTop + T.space.metricValue, spec: T.type.metricValue)
            text(sub, x: x, capTop: T.layout.fitMetricsCapTop + T.space.metricSub, spec: T.type.metricSub)
        }
    }

    private func metricChevron(_ title: String, x: Double, capTop: Double) -> some View {
        let width = VitalsChipMorphText.measuredWidth(title, spec: T.type.metricLink, k: k)
        let size = T.type.metricLink.size * s
        let lineHeight = VitalsChipMorphText.lineHeight(spec: T.type.metricLink, k: k)
        return VitalsChipMorphChevron(s: s)
            .offset(x: x * k + width + 2 * s, y: capTop * k - VitalsChipMorphText.CAP_OFFSET * size + (lineHeight - 16 * s) / 2)
    }

    private var fitnessMetrics: some View {
        ZStack(alignment: .topLeading) {
            metric(title: "Steps", value: "5,253", sub: "Avg: 5,053", x: T.space.screenInset, titleColor: T.colors.heartDark)
            metric(title: "Cardio Fitness", value: "38 VO₂ max", sub: "High", x: T.layout.fitStats.x, titleColor: T.colors.heartDark)
        }
    }

    private var heartChart: some View {
        let bars: [(Double, Double, Double)] = [(34,678,697),(41.25,670,688),(48.5,670,694),(55.75,665,695),(63,670,697),(70.25,677,698),(77.5,685,699),(84.75,676,699),(92,687,699),(99.25,686,703),(106.5,687,703),(113.75,696,703),(121,696,703),(128.25,660,698),(135.5,670,694),(142.75,681,695),(150,680,701),(157.25,687,700),(164.5,680,700),(171.75,681,703)]
        return ZStack(alignment: .topLeading) {
            text("43–120 BPM", x: T.space.screenInset, capTop: T.layout.heartChart.titleTop, spec: T.type.chartTitle)
            Canvas { context, _ in
                context.scaleBy(x: s, y: s)
                for (x, y0, y1) in bars { context.fill(Path(roundedRect: CGRect(x: x - 32, y: y0 - 650, width: 3.8, height: y1 - y0), cornerRadius: 1.9), with: .color(T.colors.chartPink.opacity(0.82))) }
                for index in 0..<33 { context.fill(Path(ellipseIn: CGRect(x: 2.7 + Double(index) * 10.35 - 0.7, y: 711 - 650 - 0.7, width: 1.4, height: 1.4)), with: .color(T.colors.faint.opacity(0.55))) }
                context.fill(Path(CGRect(x: 2, y: 707 - 650, width: 1, height: 7)), with: .color(T.colors.faint.opacity(0.7))); context.fill(Path(CGRect(x: 334, y: 707 - 650, width: 1, height: 7)), with: .color(T.colors.faint.opacity(0.7)))
                context.fill(Path(ellipseIn: CGRect(x: 171.75 + 1.9 - 32 - 3.3, y: 681 - 3 - 650 - 3.3, width: 6.6, height: 6.6)), with: .color(.white)); context.stroke(Path(ellipseIn: CGRect(x: 171.75 + 1.9 - 32 - 3.3, y: 681 - 3 - 650 - 3.3, width: 6.6, height: 6.6)), with: .color(T.colors.heart), lineWidth: 2.4)
            }.frame(width: 338 * s, height: 80 * s).offset(x: 32 * s, y: 650 * s)
            text("12AM", x: T.space.screenInset, capTop: T.layout.heartChart.axisCapTop, spec: T.type.axis)
            text("12AM", x: T.layout.heartChart.axisRightX, capTop: T.layout.heartChart.axisCapTop, spec: T.type.axis)
        }
    }

    private var heartMetrics: some View {
        ZStack(alignment: .topLeading) {
            text("Resting Heart Rate", x: T.space.screenInset, capTop: T.layout.heartMetricsTop, spec: T.type.metricLink)
            metricChevron("Resting Heart Rate", x: T.space.screenInset, capTop: T.layout.heartMetricsTop)
            text("55 BPM", x: T.space.screenInset, capTop: T.layout.heartMetricsTop + T.space.metricValue, spec: T.type.metricValue, color: T.colors.supportValue)
            text("Recovery HRV", x: T.layout.heartRightColumn, capTop: T.layout.heartMetricsTop, spec: T.type.metricLink)
            metricChevron("Recovery HRV", x: T.layout.heartRightColumn, capTop: T.layout.heartMetricsTop)
            text("54 MS", x: T.layout.heartRightColumn, capTop: T.layout.heartMetricsTop + T.space.metricValue, spec: T.type.metricValue, color: T.colors.supportValue)
        }
    }

    private static let readyBody = ["You had a good night’s sleep last", "night and all your other metrics are", "typical. It looks like you’re rested", "and ready for today."]
    private static let fitHeading = ["Your Exercise Ring", "Looks Good"]
    private static let fitBody = ["Your Exercise ring is ahead of", "where it usually is by now."]
    private static let heartHeading = ["Cardio Fitness", "Trending Up"]
    private static let heartBody = ["On average, your cardio fitness", "went up over the last month. This is", "great for your long-term health."]
}
