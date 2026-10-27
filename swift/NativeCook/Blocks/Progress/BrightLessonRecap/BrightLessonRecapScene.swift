import SwiftUI

/// The recap above the claim row: the tappable hero (bands, sparkles, mascot, titles) and the three
/// results. Everything is a function of the loop time `t`.
struct BrightLessonRecapScene: View {
    let t: Double
    let metrics: BrightLessonRecapMetrics
    let reduced: Bool
    let replayEnabled: Bool
    let onReplay: () -> Void

    private typealias M = BrightLessonRecapMotion
    private typealias P = BrightLessonRecapPalette

    var body: some View {
        VStack(spacing: 0) {
            hero
            if metrics.flow {
                flowCopy
                stackedStats
            } else {
                compactStats
            }
        }
        .opacity(1 - M.phase(t, M.sourceEnd, M.fadeEnd))
    }

    // MARK: Hero

    /// RN: a `Pressable` (replay) of `heroHeight` holding absolutely placed children. It clips only in
    /// the flow layout.
    private var hero: some View {
        Button(action: onReplay) {
            heroLabel
        }
        .buttonStyle(BrightLessonRecapPlainButtonStyle())
        .disabled(!replayEnabled)
        .accessibilityLabel("Replay lesson celebration")
        .accessibilityHidden(!replayEnabled)
    }

    @ViewBuilder private var heroLabel: some View {
        let frame = Color.clear
            .frame(width: metrics.contentWidth, height: metrics.heroHeight)
            .overlay(alignment: .topLeading) { heroArt.allowsHitTesting(false) }
        if metrics.flow {
            frame.clipShape(Rectangle()).contentShape(Rectangle())
        } else {
            frame.contentShape(Rectangle())
        }
    }

    private var heroArt: some View {
        ZStack(alignment: .topLeading) {
            bands
            ForEach(0..<6, id: \.self) { index in
                sparkle(index)
            }
            mascot
            if !metrics.flow {
                bigTitle
                finalTitle
                subtitle
            }
        }
        .frame(width: metrics.contentWidth, height: metrics.heroHeight, alignment: .topLeading)
    }

    /// Three broad diagonal green bands, swept in from the left and rotated -12 degrees. RN `bands`.
    private var bands: some View {
        let height: CGFloat = metrics.bandsHeight
        let width: CGFloat = metrics.width
        let opacity: Double = t < 1.333 ? M.enter(M.fraction(t, 0.333, 0.533)) : 0
        let sweep: Double = reduced ? 0 : -(1 - M.enter(M.fraction(t, 0.333, 0.55)))
        let slide: CGFloat = CGFloat(sweep) * width
        let left: CGFloat = -width * 0.25 + slide
        let edgeBand: CGFloat = height * 0.18
        let middleBand: CGFloat = height * 0.64
        return VStack(spacing: 0) {
            P.color(0xDCF5C6).frame(height: edgeBand)
            P.color(0xB4E892).frame(height: middleBand)
            P.color(0x92D577).frame(height: edgeBand)
        }
        .frame(width: width * 1.5, height: height)
        .clipped()
        .opacity(opacity)
        .rotationEffect(.degrees(-12))
        .offset(x: left, y: metrics.bandsTop)
        .accessibilityHidden(true)
    }

    /// One of six four-point sparkles round the compact mascot. RN `Sparkle`: the box is placed at
    /// `left: 50%`, margins -9, so a 14 pt sparkle sits 2 pt up and left of a 22 pt one.
    private func sparkle(_ index: Int) -> some View {
        let step: Double = Double(index)
        let angle: Double = step * Double.pi / 3
        let appear: Double = M.phase(t, 1.333, 1.5)
        let fade: Double = 1 - M.phase(t, M.sourceEnd, M.fadeEnd)
        let opacity: Double = reduced ? 0 : appear * fade
        // RN: sin(t * PI * 4 / LOOP * 1.5 + index * 1.7), evaluated left to right.
        let swing: Double = t * Double.pi * 4 / M.loop * 1.5
        let pulse: Double = 0.82 + 0.22 * sin(swing + step * 1.7)
        let side: CGFloat = index % 3 == 1 ? 14 : 22
        let colorHex: UInt32 = index % 2 == 1 ? 0x78D64B : 0x8FDF5F
        let dx: CGFloat = CGFloat(cos(angle) * 116)
        let dy: CGFloat = CGFloat(sin(angle) * 104)
        let originX: CGFloat = metrics.contentWidth / 2 - 9 + dx
        let originY: CGFloat = metrics.sparkleCenterY - 9 + dy
        return BrightLessonRecapSparkleShape()
            .fill(P.color(colorHex))
            .frame(width: side, height: side)
            .scaleEffect(CGFloat(pulse))
            .opacity(opacity)
            .offset(x: originX, y: originY)
            .accessibilityHidden(true)
    }

    /// The otter: enters from the left, drifts right, then compacts (`dock`) to 61% of its size.
    private var mascot: some View {
        let build: Double = M.enter(M.fraction(t, 0.333, 0.533))
        let dock: Double = M.dock(t)
        let size: CGFloat = metrics.mascotSize
        let boxHeight: CGFloat = size * 31 / 30
        let drift: CGFloat = CGFloat(M.mascotX(t)) * metrics.width
        let shrink: CGFloat = CGFloat(1 - 0.39 * dock)
        var rise: CGFloat = CGFloat(-10 * dock)
        if !metrics.flow {
            // RN: the mascot's centre sits at `height * center - sectionTop` in the hero.
            let centre: CGFloat = metrics.height * CGFloat(M.mascotCenter(t))
            rise = centre - metrics.sectionTop - size * 31 / 60
        }
        let left: CGFloat = (metrics.contentWidth - size) / 2 + drift
        return Image(uiImage: BrightLessonRecapAssets.mascot)
            .resizable()
            .interpolation(.high)
            .scaledToFit()
            .frame(width: size, height: boxHeight)
            .opacity(build)
            .scaleEffect(shrink)
            .offset(x: left, y: rise)
            .accessibilityHidden(true)
    }

    /// "Brilliant / work!" in two heavy lines, shrinking away as the mascot docks. RN `title` + `bigTitle`.
    private var bigTitle: some View {
        let dock: Double = M.dock(t)
        let opacity: Double = t < 1.333 ? M.enter(M.fraction(t, 0.35, 0.55)) : 0
        let shrink: CGFloat = CGFloat(1 - 0.18 * dock)
        let lift: CGFloat = metrics.height * 0.06 * CGFloat(dock)
        let top: CGFloat = metrics.height * 0.769 - metrics.sectionTop - lift
        return metrics.label("Brilliant\nwork!", face: .black, size: 49, lineHeight: 62, kern: -1.2, color: 0xFFB100, cap: 1.4)
            .frame(width: metrics.contentWidth)
            .opacity(opacity)
            .scaleEffect(shrink)
            .offset(y: top)
    }

    /// The small headline that rises into place at 1.333 s. RN `finalTitle`.
    private var finalTitle: some View {
        let remaining: Double = 1 - M.enter(M.fraction(t, 1.333, 1.5))
        let rise: CGFloat = reduced ? 0 : metrics.height * 0.147 * CGFloat(remaining)
        let rest: CGFloat = metrics.height * 344 / 586 - metrics.sectionTop - 4
        return metrics.label("Brilliant work!", face: .black, size: 28, lineHeight: 34, kern: -0.8, color: 0xFFB100, cap: 1.6)
            .frame(width: metrics.contentWidth)
            .opacity(t >= 1.333 ? 1 : 0)
            .offset(y: rest + rise)
    }

    /// "Take a bow!" in the system face. RN `subtitle`.
    private var subtitle: some View {
        metrics.label("Take a bow!", face: .system, size: 15, lineHeight: 23, color: 0x555555)
            .frame(width: metrics.contentWidth)
            .opacity(M.enter(M.fraction(t, 1.367, 1.5)))
            .offset(y: metrics.height * 380 / 586 - metrics.sectionTop)
    }

    // MARK: Flow layout copy

    /// RN `flowCopy`: the headline and line below the hero when large text stacks the recap.
    private var flowCopy: some View {
        VStack(spacing: 16) {
            metrics.label("Brilliant work!", face: .black, size: 30, lineHeight: 37, kern: -0.8, color: 0xF0A500, cap: 1.6)
                .frame(maxWidth: .infinity)
                .opacity(M.enter(M.fraction(t, 0.35, 0.55)))
                .accessibilityAddTraits(.isHeader)
            metrics.label("Take a bow!", face: .extraBold, size: 16, lineHeight: 23, color: 0x4B4B4B)
                .frame(maxWidth: .infinity)
                .opacity(M.enter(M.fraction(t, 1.367, 1.5)))
        }
        .padding(.bottom, 28)
    }

    // MARK: Results

    private var compactStats: some View {
        HStack(spacing: metrics.statGap) {
            ForEach(0..<3, id: \.self) { index in
                BrightLessonRecapStatPill(index: index, t: t, metrics: metrics, reduced: reduced)
            }
        }
        .frame(width: metrics.statsWidth)
    }

    private var stackedStats: some View {
        VStack(spacing: 16) {
            ForEach(0..<3, id: \.self) { index in
                BrightLessonRecapStatCard(index: index, t: t, metrics: metrics)
            }
        }
        .frame(maxWidth: .infinity)
    }
}

/// A button with no pressed look: RN's hero `Pressable` has none.
struct BrightLessonRecapPlainButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
    }
}

// MARK: - Compact pill

/// One result as a thin outlined pill: the outline grows, then the coloured cap rises, and the counter
/// lands with a small kick. RN `StatCard` (not stacked).
struct BrightLessonRecapStatPill: View {
    let index: Int
    let t: Double
    let metrics: BrightLessonRecapMetrics
    let reduced: Bool

    private typealias M = BrightLessonRecapMotion
    private typealias P = BrightLessonRecapPalette

    private var stat: BrightLessonRecapStat { BrightLessonRecapStat.all[index] }
    private var pillHeight: CGFloat { metrics.compactHeight }
    private var bodyHeight: CGFloat { metrics.compactHeight * 0.76 }
    private var capHeight: CGFloat { metrics.compactHeight * 0.27 }
    /// RN measures the final text and adds 8 pt; the counter then keeps that width while it counts.
    private var counterWidth: CGFloat {
        ceil(metrics.measure(stat.finalCounterText, face: .black, size: 18)) + 8
    }

    var body: some View {
        ZStack(alignment: .topLeading) {
            ForEach(0..<8, id: \.self) { spark in
                sparkView(spark)
            }
            outline
            cap
            value
        }
        .frame(width: metrics.pillWidth, height: pillHeight, alignment: .topLeading)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(stat.accessibilityText)
    }

    /// Eight 4 pt squares round the pill before its cap rises. RN `StatSpark`.
    private func sparkView(_ spark: Int) -> some View {
        let step: Double = Double(spark)
        let angle: Double = step * Double.pi / 4
        let arrive: Double = M.phase(t, stat.sparkle, stat.sparkle + 0.08)
        let leave: Double = 1 - M.phase(t, stat.cap + 0.1, stat.cap + 0.5)
        let show: Double = arrive * leave
        let wave: Double = sin(t * 18 + step)
        let flicker: Double = 0.55 + 0.45 * wave * wave
        let left: CGFloat = metrics.pillWidth * CGFloat(0.5 + cos(angle) * 0.47)
        let top: CGFloat = pillHeight * CGFloat(0.62 + sin(angle) * 0.29)
        return RoundedRectangle(cornerRadius: 0.7)
            .fill(P.color(stat.border))
            .frame(width: 4, height: 4)
            .rotationEffect(.degrees(45))
            .opacity(reduced ? 0 : show * flicker)
            .offset(x: left, y: top)
            .accessibilityHidden(true)
    }

    /// The white pill with a 2.5 pt coloured border, popping in from 56% by 50% of its size.
    private var outline: some View {
        let p: Double = M.enter(M.fraction(t, stat.body, stat.body + 0.2))
        let scaleX: CGFloat = CGFloat(0.56 + 0.44 * p)
        let scaleY: CGFloat = CGFloat(0.5 + 0.5 * p)
        let appear: Double = M.fraction(t, stat.body - 1.0 / 60, stat.body)
        return RoundedRectangle(cornerRadius: 12)
            .fill(Color.white)
            .overlay { RoundedRectangle(cornerRadius: 12).strokeBorder(P.color(stat.border), lineWidth: 2.5) }
            .frame(width: metrics.pillWidth, height: bodyHeight)
            .scaleEffect(x: scaleX, y: scaleY)
            .opacity(appear)
            .offset(y: pillHeight - bodyHeight)
            .accessibilityHidden(true)
    }

    /// The coloured label cap sliding up from below inside a mask with rounded top corners.
    private var cap: some View {
        let p: Double = M.enter(M.fraction(t, stat.cap, stat.capEnd))
        let width: CGFloat = metrics.pillWidth
        let slide: CGFloat = CGFloat(1 - p) * capHeight
        return Color.clear
            .frame(width: width, height: capHeight)
            .overlay(alignment: .top) {
                P.color(stat.border)
                    .frame(width: width, height: capHeight)
                    .overlay {
                        metrics.label(stat.label, face: .black, size: 9, lineHeight: 12, kern: 0.6, color: 0xFFFFFF)
                            .fixedSize()
                    }
                    .opacity(p)
                    .offset(y: slide)
            }
            .clipShape(UnevenRoundedRectangle(topLeadingRadius: 11, bottomLeadingRadius: 0, bottomTrailingRadius: 0, topTrailingRadius: 11))
            .accessibilityHidden(true)
    }

    /// The glyph and the counter, centred in the pill. The number lands with a +10% kick for 90 ms.
    private var value: some View {
        let p: Double = M.enter(M.fraction(t, stat.body, stat.body + 0.2))
        // RN: +10% for 90 ms, then settling over 220 ms.
        let rise: Double = M.fraction(t, stat.land, stat.land + 0.09)
        let settle: Double = 1 - M.enter(M.fraction(t, stat.land + 0.09, stat.land + 0.31))
        let kick: Double = reduced ? 0 : rise * settle
        let grow: CGFloat = CGFloat((0.8 + 0.2 * p) * (1 + 0.1 * kick))
        let appear: Double = M.fraction(t, stat.body - 1.0 / 60, stat.body)
        return HStack(spacing: 4) {
            BrightLessonRecapStatIcon(kind: stat.kind, color: P.color(stat.color), size: 18)
            metrics.label(
                M.counterText(index: index, t: t),
                face: .black, size: 18, lineHeight: 24, color: stat.color, tabular: true
            )
            .aligned(.leading)
            .lineLimit(1)
            .fixedSize()
            .frame(width: counterWidth, alignment: .leading)
        }
        .frame(width: metrics.pillWidth, height: bodyHeight)
        .scaleEffect(grow)
        .opacity(appear)
        .offset(y: pillHeight - bodyHeight)
        .accessibilityHidden(true)
    }
}

// MARK: - Stacked card (flow layout)

/// One result as a full-width card with a tinted header, the glyph and a large counter. RN `StatCard`, `stacked`.
struct BrightLessonRecapStatCard: View {
    let index: Int
    let t: Double
    let metrics: BrightLessonRecapMetrics

    private typealias M = BrightLessonRecapMotion
    private typealias P = BrightLessonRecapPalette

    private var stat: BrightLessonRecapStat { BrightLessonRecapStat.all[index] }

    var body: some View {
        let scale = metrics.fontScale
        VStack(spacing: 0) {
            metrics.label(stat.label, face: .black, size: 10, kern: 0.3, color: stat.color)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 10)
                .padding(.horizontal, 3)
                .background(P.color(stat.light))
            BrightLessonRecapStatIcon(kind: stat.kind, color: P.color(stat.color))
                .padding(.top, 10)
            // RN: a multiline input with padding 6 / 12 / 10, 5 pt of margin and a 34 pt line.
            metrics.label(
                M.counterText(index: index, t: t),
                face: .black, size: 27, lineHeight: 34, color: stat.color, tabular: true
            )
            .frame(maxWidth: .infinity, alignment: .top)
            .padding(.horizontal, 12)
            .padding(.top, 6)
            .frame(minHeight: ceil(34 * scale) + 16, alignment: .top)
            .padding(.top, 5)
        }
        .frame(maxWidth: .infinity, minHeight: 128, alignment: .top)
        .background(P.color(0xFFFDF9))
        .clipShape(RoundedRectangle(cornerRadius: 17))
        .overlay { RoundedRectangle(cornerRadius: 17).strokeBorder(P.color(stat.border), lineWidth: 1.5) }
        .opacity(M.fraction(t, stat.body - 1.0 / 60, stat.body))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(stat.accessibilityText)
    }
}
