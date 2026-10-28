import SwiftUI
import UIKit

private typealias M = FighterProfileTourMotion
private typealias Roster = FighterProfileTourData
private typealias Fighter = FighterProfileTourFighter

private let yellow = Color(hex: 0xffdb35)
private let ink = Color(hex: 0x071f69)
// Card surfaces are translucent shades of the page, so a fighter's colour runs through all of them.
private let shade = Color.rgba(12, 16, 60, 0.35)
private let shadeSelected = (r: 12.0, g: 16.0, b: 60.0, a: 0.25)
private let haloFillRGBA = (r: 255.0, g: 255.0, b: 255.0, a: 0.055)
private let shadeSoft = Color.rgba(12, 16, 60, 0.31)
private let lift = Color.rgba(255, 255, 255, 0.12)
private let pressFill = Color.white.opacity(24.0 / 255.0)
private let pressCurve = Animation.timingCurve(0.23, 1, 0.32, 1, duration: 0.12)

// Roster geometry in stage points. Every card is 95 pt; the selection ring sits on top of one.
private let rosterTop = 164.0
private let rowX = 20.0, rowWidth = 353.0, rowHeight = 95.0, rowPitch = 107.0
private let artBoxX = 206.0, artBoxTop = -14.0
private let heroBox = CGRect(x: 45.8, y: 181.3, width: 287.3, height: 287.3)
private let pinnedBox = CGRect(x: 166, y: 38.4, width: 55, height: 55)
private let haloRect = CGRect(x: 74, y: 206, width: 246, height: 246)
private let statCard = FighterProfileTourModel.statCard
private let statGap = FighterProfileTourModel.statGap

private func rowTop(_ index: Double) -> Double { rosterTop + index * rowPitch }

/// Where a fighter's art sits on its own roster card, in stage points.
private func rowArtRect(_ fighter: Fighter, _ index: Int) -> CGRect {
    let fit = Roster.containIn(fighter, fighter.rowFit)
    return CGRect(x: artBoxX + fit.minX, y: rowTop(Double(index)) + artBoxTop + fit.minY, width: fit.width, height: fit.height)
}

private func mixRect(_ a: CGRect, _ b: CGRect, _ p: Double) -> CGRect {
    CGRect(x: M.mix(a.minX, b.minX, p), y: M.mix(a.minY, b.minY, p), width: M.mix(a.width, b.width, p), height: M.mix(a.height, b.height, p))
}

private extension View {
    /// Absolute placement in the stage, like `position: 'absolute'` with left and top.
    func at(_ x: Double, _ y: Double) -> some View {
        frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading).offset(x: x, y: y)
    }

    func text(_ size: CGFloat, _ weight: Font.Weight, _ color: Color, tracking: CGFloat = 0, digits: Bool = false) -> some View {
        font(digits ? Font.system(size: size, weight: weight).monospacedDigit() : Font.system(size: size, weight: weight))
            .tracking(tracking).foregroundStyle(color)
    }
}

/// A Pressable: the content gets `pressed`, and the tap fires on release inside.
private struct FighterProfileTourPressable<Content: View>: View {
    let action: () -> Void
    let content: (Bool) -> Content

    private struct Style: ButtonStyle {
        let content: (Bool) -> Content
        func makeBody(configuration: Configuration) -> some View { content(configuration.isPressed) }
    }

    var body: some View {
        Button(action: action) { Color.clear }.buttonStyle(Style(content: content))
    }
}

private struct PressHighlight: View {
    let pressed: Bool
    var radius: CGFloat = 10
    var body: some View {
        RoundedRectangle(cornerRadius: radius, style: .continuous).fill(pressFill)
            .opacity(pressed ? 1 : 0)
            .animation(pressCurve, value: pressed)
            .allowsHitTesting(false)
    }
}

struct FighterProfileTour: View {
    static let loop = FighterProfileTourMotion.loop

    var autoPlay = true
    /// Deterministic preview position in seconds, from 0 to `loop`.
    var previewTime: Double? = nil
    var previewReducedMotion = false
    var onComplete: (() -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var systemReduced
    @Environment(\.scenePhase) private var scenePhase
    @State private var model = FighterProfileTourModel()
    @State private var visible = false
    @State private var dragMode = DragMode.undecided
    @State private var samples: [(x: Double, t: TimeInterval)] = []

    private enum DragMode { case undecided, pan, stats, failed }
    private static let space = "fighterProfileTourRoot"

    /// Everything one frame needs, derived from the model.
    private struct Snapshot {
        let t: Double
        let step: Int
        let you: Fighter
        let counters: [Fighter]
        let selected: Int
        let scale: Double
        let stageHeight: Double
        let stageWidth: Double
        let stageLeft: Double
        let top: Double
        let screen: CGSize
        let reduced: Bool
    }

    /// Stage scale and height: content fits compact phones without clipping its CTA, while the
    /// background always fills the device.
    private func metrics(_ screen: CGSize, _ insets: EdgeInsets) -> (scale: Double, available: Double) {
        let width = Double(screen.width), height = Double(screen.height)
        let available = max(1, height - Double(insets.top) - Double(insets.bottom))
        return (min(width / M.width, available / 770), available)
    }

    private func snapshot(screen: CGSize, insets: EdgeInsets) -> Snapshot {
        let reduced = systemReduced || previewReducedMotion
        let (scale, available) = metrics(screen, insets)
        let you = Roster.fighters[model.selected]
        let t = M.sceneTime(model.progress, reduced: reduced)
        return Snapshot(
            t: t, step: M.stepAt(t),
            you: you, counters: you.counters.map { Roster.fighters[Roster.index[$0] ?? 0] }, selected: model.selected,
            scale: scale, stageHeight: available / scale + 44, stageWidth: M.width * scale,
            stageLeft: (Double(screen.width) - M.width * scale) / 2, top: Double(insets.top) - 44 * scale, screen: screen, reduced: reduced
        )
    }

    private func prepare(_ date: Date, screen: CGSize, insets: EdgeInsets) {
        let reduced = systemReduced || previewReducedMotion
        model.reduced = reduced
        model.systemReduced = systemReduced
        model.stageWidth = M.width * metrics(screen, insets).scale
        model.onComplete = onComplete
        model.configure(autoPlay: autoPlay, previewTime: previewTime)
        model.advance(date)
    }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let screen = CGSize(width: proxy.size.width + insets.leading + insets.trailing, height: proxy.size.height + insets.top + insets.bottom)
            TimelineView(.animation(paused: !(visible && scenePhase == .active))) { timeline in
                let _ = prepare(timeline.date, screen: screen, insets: insets)
                root(snapshot(screen: screen, insets: insets))
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .ignoresSafeArea()
        }
        .background(Color(hex: Roster.fighters[0].page[1]).ignoresSafeArea())
        .preferredColorScheme(.dark)
        .onAppear {
            visible = true
            model.setFocused(true)
        }
        .onDisappear {
            model.setFocused(false)
            visible = false
        }
        .onChange(of: scenePhase) { _, phase in model.setAppActive(phase == .active) }
    }

    private func root(_ s: Snapshot) -> some View {
        ZStack(alignment: .topLeading) {
            ForEach(0..<Roster.fighters.count, id: \.self) { index in
                LinearGradient(colors: Roster.fighters[index].page.map { Color(hex: $0) }, startPoint: .top, endPoint: .bottom)
                    .opacity(index == 0 ? 1 : model.weight(index))
                    .allowsHitTesting(false)
            }
            stage(s)
        }
        .frame(width: s.screen.width, height: s.screen.height, alignment: .topLeading)
        .clipped()
        .contentShape(Rectangle())
        .coordinateSpace(name: Self.space)
        .simultaneousGesture(dragGesture(s))
    }

    // MARK: Stage

    private func stage(_ s: Snapshot) -> some View {
        ZStack(alignment: .topLeading) {
            Circle().fill(Color.white.opacity(0.04)).frame(width: 420, height: 420).at(-13, 55)
            morph(s)
            stepOne(s)
            stepTwo(s)
            stepThree(s)
            badge(s)
            actor(s)
            footer(s)
            topbar(s)
        }
        .frame(width: M.width, height: s.stageHeight, alignment: .topLeading)
        .scaleEffect(s.scale, anchor: .topLeading)
        .offset(x: s.stageLeft, y: s.top)
    }

    /// Fade, rise and scale between an arrival and a departure window of the scene time.
    private func layer<Content: View>(_ t: Double, enter: (Double, Double)? = nil, exit: (Double, Double)? = nil,
                                      shift: Double = 10, scaleFrom: Double = 1, @ViewBuilder _ content: () -> Content) -> some View {
        let arrival = enter.map { M.ramp(t, $0.0, $0.1) } ?? 1
        let departure = exit.map { M.ramp(t, $0.0, $0.1) } ?? 0
        return content()
            .opacity(arrival * (1 - departure))
            .scaleEffect(M.mix(scaleFrom, 1, arrival))
            .offset(y: shift * (1 - arrival) - shift * departure)
    }

    private func accent(_ hex: UInt32) -> Color { Color(hex: hex) }

    private func heading(_ word: String, _ rest: String, step: Int, _ s: Snapshot) -> some View {
        let enter: (Double, Double)? = step == 1 ? nil : step == 2 ? (0.18, 0.205) : (0.595, 0.62)
        let exit: (Double, Double)? = step == 1 ? (0.14, 0.168) : step == 2 ? (0.48, 0.505) : nil
        let top = step == 3 ? 109.0 : step == 2 ? 94.0 : 98.0
        return layer(s.t, enter: enter, exit: exit, shift: 8) {
            HStack(alignment: .lastTextBaseline, spacing: 7) {
                Text(word).text(step == 2 ? 35 : 39, .heavy, yellow, tracking: -1)
                    .frame(height: step == 2 ? 42 : 47)
                    .accessibilityAddTraits(.isHeader)
                Text(rest).text(26, .heavy, .white, tracking: -0.65)
            }
            .frame(width: 345, alignment: .leading)
        }
        .at(24, top)
    }

    // MARK: Chrome

    private func topbar(_ s: Snapshot) -> some View {
        let slop = max(0, (48 / s.scale - 44) / 2)
        return HStack(spacing: 0) {
            Text("\(s.step) of 3").text(15, .regular, Color.white.opacity(0.86))
            Spacer(minLength: 0)
            FighterProfileTourPressable(action: { if !model.suppressTap { model.commit(.skip) } }) { pressed in
                ZStack {
                    PressHighlight(pressed: pressed)
                    Text("Skip").text(15, .regular, .white)
                }
                .frame(minWidth: 60, minHeight: 44)
                .contentShape(Rectangle().inset(by: -slop))
            }
            .accessibilityLabel("Skip to matchups")
        }
        .frame(width: 353, height: 38)
        .at(24, 49)
    }

    private func morph(_ s: Snapshot) -> some View {
        let m = M.ramp(s.t, 0.12, 0.26, moving: true)
        let shape = mixRect(CGRect(x: rowX, y: rowTop(model.ringRow.value), width: rowWidth, height: rowHeight), haloRect, m)
        let radius = M.mix(12, haloRect.width / 2, m)
        let color = Color(.sRGB, red: M.mix(shadeSelected.r, haloFillRGBA.r, m) / 255, green: M.mix(shadeSelected.g, haloFillRGBA.g, m) / 255,
                          blue: M.mix(shadeSelected.b, haloFillRGBA.b, m) / 255, opacity: M.mix(shadeSelected.a, haloFillRGBA.a, m))
        return RoundedRectangle(cornerRadius: radius, style: .continuous).fill(color)
            .frame(width: shape.width, height: shape.height)
            .opacity(1 - M.ramp(s.t, 0.48, 0.535))
            .at(shape.minX, shape.minY)
            .allowsHitTesting(false)
    }

    private func badge(_ s: Snapshot) -> some View {
        let p = M.ramp(s.t, 0.6, 0.68)
        let size = pinnedBox.width + 16
        return ZStack {
            Circle().fill(Color.white.opacity(0.08))
            Circle().strokeBorder(yellow, lineWidth: 2)
        }
        .frame(width: size, height: size)
        .opacity(p)
        .scaleEffect(0.8 + 0.2 * p)
        .at(pinnedBox.minX - 8, pinnedBox.minY - 6)
        .allowsHitTesting(false)
    }

    /// The chosen fighter leaves its card: the clip grows from the card to the page, so the art
    /// breaks out of its frame as it lifts, then the same art becomes the hero and later the badge.
    private func actor(_ s: Snapshot) -> some View {
        let t = s.t
        let open = M.ramp(t, 0.12, 0.2, moving: true)
        let card = CGRect(x: rowX, y: rowTop(model.ringRow.value), width: rowWidth, height: rowHeight)
        let clip = mixRect(card, CGRect(x: 0, y: 0, width: M.width, height: s.stageHeight), open)
        let clipX = M.mix(rowX, 0, open)
        let clipY = M.mix(rowTop(model.ringRow.value), 0, open)
        let from = rowArtRect(s.you, s.selected)
        let hero = Roster.standIn(s.you, heroBox)
        let pinned = Roster.standIn(s.you, pinnedBox)
        let art = mixRect(mixRect(from, hero, M.ramp(t, 0.12, 0.32, moving: true)), pinned, M.ramp(t, 0.47, 0.6, moving: true))
        return ZStack(alignment: .topLeading) {
            Image(uiImage: s.you.image).resizable().interpolation(.high)
                .frame(width: art.width, height: art.height)
                .offset(x: art.minX - clipX, y: art.minY - clipY)
                .accessibilityHidden(true)
        }
        .frame(width: clip.width, height: clip.height, alignment: .topLeading)
        .clipShape(RoundedRectangle(cornerRadius: M.mix(12, 0, open), style: .continuous))
        .opacity(t >= 0.118 ? 1 : 0)
        .at(clip.minX, clip.minY)
        .allowsHitTesting(false)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(s.you.name), your fighter")
    }

    private func footer(_ s: Snapshot) -> some View {
        let t = s.t
        let slop = max(0, (48 / s.scale - 52) / 2)
        let dot = M.mix(M.mix(0, 62, M.ramp(t, 0.12, 0.32, moving: true)), 124, M.ramp(t, 0.47, 0.68, moving: true))
        let ctaOpacity = t < 0.575 ? 1 - M.ramp(t, 0.56, 0.573) : M.ramp(t, 0.58, 0.595)
        let label = s.step == 3 ? (model.completed ? "Ready to train" : "Start training") : "Continue"
        let a11y = s.step == 3 ? (model.completed ? "\(s.you.name) is ready to train" : "Finish onboarding and start training") : "Continue to step \(s.step + 1)"
        return VStack(spacing: 16) {
            ZStack(alignment: .leading) {
                HStack(spacing: 8) {
                    ForEach(0..<3, id: \.self) { _ in Capsule().fill(Color.white.opacity(0.13)).frame(width: 54, height: 6) }
                }
                Capsule().fill(yellow).frame(width: 54, height: 6).offset(x: dot)
            }
            .frame(width: 178, height: 6)
            .frame(width: 353)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("Step \(s.step) of 3")
            FighterProfileTourPressable(action: { if !model.suppressTap { model.commit(.proceed) } }) { pressed in
                ZStack {
                    RoundedRectangle(cornerRadius: 14, style: .continuous).fill(yellow)
                    HStack(spacing: 10) {
                        Text(label).text(18, .heavy, ink, tracking: -0.4)
                        Image(systemName: "chevron.forward").font(.system(size: 22, weight: .bold)).foregroundStyle(ink)
                    }
                    .opacity(ctaOpacity)
                    FighterProfileTourShimmerView(sweep: model.sweep.value, enabled: model.shimmerEnabled)
                }
                .frame(width: 353, height: 52)
                .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                .scaleEffect(pressed && !s.reduced ? 0.97 : 1)
                .opacity(pressed && s.reduced ? 0.9 : 1)
                .animation(pressCurve, value: pressed)
                .contentShape(Rectangle().inset(by: -slop))
            }
            .frame(width: 353, height: 52)
            .accessibilityLabel(a11y)
        }
        .frame(width: 353, alignment: .top)
        .at(20, s.stageHeight - 88)
    }

    // MARK: Step 1 — pick

    private func stepOne(_ s: Snapshot) -> some View {
        ZStack(alignment: .topLeading) {
            heading("Pick", "your fighter", step: 1, s)
            ForEach(0..<Roster.fighters.count, id: \.self) { index in row(index, s) }
            ring(s)
        }
        .accessibilityHidden(s.step != 1)
        .allowsHitTesting(s.step == 1)
    }

    /// A roster card. Every one can be picked; picking the chosen one again starts the tour.
    private func row(_ index: Int, _ s: Snapshot) -> some View {
        let fighter = Roster.fighters[index]
        let selected = index == s.selected
        let exit: (Double, Double) = selected ? (0.12, 0.15) : (0.125 + Double(index) * 0.005, 0.185 + Double(index) * 0.005)
        let fit = Roster.containIn(fighter, fighter.rowFit)
        // The chosen card's art is handed to the travelling actor the moment the tour begins.
        let artOpacity = selected ? 1 - (s.t >= 0.118 ? 1.0 : 0.0) : 1
        let rgb = model.tierAccent
        let tierColor = Color(.sRGB, red: rgb[0] / 255, green: rgb[1] / 255, blue: rgb[2] / 255)
        return layer(s.t, exit: exit, shift: selected ? 0 : -6) {
            FighterProfileTourPressable(action: { if !model.suppressTap { model.pick(index) } }) { pressed in
                ZStack(alignment: .topLeading) {
                    // The chosen card's surface is drawn by the morph, which later becomes the hero halo.
                    if !selected { RoundedRectangle(cornerRadius: 12, style: .continuous).fill(shade) }
                    PressHighlight(pressed: pressed, radius: 12)
                    VStack(alignment: .leading, spacing: 2) {
                        Text("\(fighter.tier) TIER").text(11, .semibold, tierColor, tracking: 0.2)
                        Text(fighter.name).text(21, .heavy, .white, tracking: -0.4)
                    }
                    .offset(x: 27, y: 30)
                    Image(uiImage: fighter.image).resizable().interpolation(.high)
                        .frame(width: fit.width, height: fit.height)
                        .offset(x: artBoxX - rowX + fit.minX, y: artBoxTop + fit.minY)
                        .opacity(artOpacity)
                        .allowsHitTesting(false)
                        .accessibilityHidden(true)
                }
                .frame(width: rowWidth, height: rowHeight, alignment: .topLeading)
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                .contentShape(Rectangle())
            }
            .frame(width: rowWidth, height: rowHeight)
            .disabled(s.step != 1)
            .accessibilityLabel(selected ? "\(fighter.name), \(fighter.tier) tier, selected. Explore \(fighter.name)" : "\(fighter.name), \(fighter.tier) tier")
            .accessibilityAddTraits(selected ? .isSelected : [])
        }
        .at(rowX, rowTop(Double(index)))
    }

    private func ring(_ s: Snapshot) -> some View {
        ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: 13, style: .continuous).strokeBorder(yellow, lineWidth: 2.5)
            Circle().fill(yellow).frame(width: 24, height: 24)
                .overlay(Image(systemName: "checkmark").font(.system(size: 18, weight: .bold)).foregroundStyle(Color(hex: 0x15318c)))
                .offset(x: rowWidth - 2.5 - 11.5 - 24, y: 2.5 + 35)
        }
        .frame(width: rowWidth, height: rowHeight + 3, alignment: .topLeading)
        .opacity(1 - M.ramp(s.t, 0.12, 0.15))
        .at(rowX, rowTop(model.ringRow.value) - 1.5)
        .allowsHitTesting(false)
    }

    // MARK: Step 2 — strengths

    private func sectionHeading(_ title: String) -> some View {
        HStack(spacing: 12) {
            RoundedRectangle(cornerRadius: 2).fill(Color(hex: 0xffa900)).frame(width: 22, height: 4)
            Text(title).text(23, .bold, .white, tracking: -0.5)
        }
        .frame(height: 29)
    }

    private func stepTwo(_ s: Snapshot) -> some View {
        let you = s.you
        let t = s.t
        let accentColor = accent(you.accent)
        return ZStack(alignment: .topLeading) {
            heading("Know", "your strengths", step: 2, s)
            layer(t, enter: (0.225, 0.25), exit: (0.48, 0.51)) {
                Text(you.name).text(55, .heavy, yellow, tracking: -1.6)
                    .lineLimit(1).minimumScaleFactor(0.5)
                    .frame(width: 353, height: 55)
            }
            .at(20, 143)
            layer(t, enter: (0.265, 0.29), exit: (0.49, 0.52)) {
                VStack(alignment: .leading, spacing: 10) {
                    sectionHeading("Overview")
                    HStack(spacing: 10) {
                        HStack(spacing: 9) {
                            Image(systemName: you.seriesIcon).font(.system(size: 19)).foregroundStyle(accentColor)
                            Text(you.series.uppercased()).text(10, .bold, .white).lineLimit(1)
                        }
                        .padding(.horizontal, 14)
                        .frame(minWidth: 181, minHeight: 30, maxHeight: 30)
                        .background(RoundedRectangle(cornerRadius: 8, style: .continuous).fill(shadeSoft))
                        HStack(spacing: 9) {
                            Text(you.tier).text(19, .heavy, accentColor)
                            Text("TIER").text(10, .bold, .white)
                        }
                        .frame(width: 100, height: 30)
                        .background(RoundedRectangle(cornerRadius: 8, style: .continuous).fill(shadeSoft))
                    }
                }
                .frame(width: 345, alignment: .leading)
            }
            .at(24, 454)
            layer(t, enter: (0.270, 0.295), exit: (0.495, 0.525)) {
                Text(Roster.summary(you)).text(14, .regular, Color.white.opacity(0.86))
                    .lineSpacing(4.2)
                    .fixedSize(horizontal: false, vertical: true)
                    .frame(width: 345, alignment: .leading)
            }
            .at(24, 536)
            HStack(alignment: .top, spacing: statGap) {
                ForEach(0..<you.stats.count, id: \.self) { index in stat(index, s) }
            }
            .fixedSize(horizontal: true, vertical: false)
            .offset(x: -model.stripOffset)
            .frame(width: 353, height: 100, alignment: .topLeading)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(you.stats.enumerated().map { "\(Roster.statLabels[$0.offset]) \($0.element.value)" }.joined(separator: ", "))
            .accessibilityHint("Swipe sideways for more stats")
            .at(20, 612)
        }
        .accessibilityHidden(s.step != 2)
        .allowsHitTesting(false)
    }

    private func stat(_ index: Int, _ s: Snapshot) -> some View {
        let stat = s.you.stats[index]
        let i = Double(index)
        let fill = stat.amount * M.ramp(s.t, 0.30 + i * 0.005, 0.335 + i * 0.005)
        return layer(s.t, enter: (0.278 + i * 0.005, 0.306 + i * 0.005), exit: (0.50 + i * 0.005, 0.53 + i * 0.005), scaleFrom: 0.97) {
            VStack(alignment: .leading, spacing: 0) {
                HStack {
                    Image(systemName: Roster.statIcons[index]).font(.system(size: 24)).foregroundStyle(accent(s.you.accent))
                        .frame(width: 27, height: 27, alignment: .leading)
                    Spacer(minLength: 0)
                    Text(stat.value).text(17, .heavy, .white, tracking: -0.3, digits: true)
                }
                .frame(height: 27)
                Text(Roster.statLabels[index]).text(12, .regular, .white).padding(.top, 8)
                ZStack(alignment: .leading) {
                    Capsule().fill(lift)
                    RoundedRectangle(cornerRadius: 3).fill(yellow)
                        .scaleEffect(x: fill, y: 1, anchor: .leading)
                }
                .frame(height: 6)
                .clipShape(RoundedRectangle(cornerRadius: 3))
                .padding(.top, 9)
            }
            .padding(.horizontal, 14)
            .padding(.top, 11)
            .frame(width: statCard, height: 100, alignment: .topLeading)
            .background(RoundedRectangle(cornerRadius: 9, style: .continuous).fill(shade))
        }
    }

    // MARK: Step 3 — matchups

    private func stepThree(_ s: Snapshot) -> some View {
        let t = s.t
        let you = s.you
        return ZStack(alignment: .topLeading) {
            heading("Find", "your matchup", step: 3, s)
            layer(t, enter: (0.598, 0.623)) {
                VStack(alignment: .leading, spacing: 17) {
                    HStack(spacing: 12) {
                        RoundedRectangle(cornerRadius: 2).fill(Color(hex: 0xffa900)).frame(width: 22, height: 4)
                        Text("Counter Matchups").text(23, .bold, .white, tracking: -0.5)
                        Spacer(minLength: 0)
                        HStack(spacing: 5) {
                            Circle().fill(yellow).frame(width: 8, height: 8)
                            Text("You").text(11, .semibold, Color.white.opacity(0.78))
                            Circle().fill(accent(you.accent)).frame(width: 8, height: 8).padding(.leading, 8)
                            Text("Them").text(11, .semibold, Color.white.opacity(0.78))
                        }
                        .accessibilityElement(children: .ignore)
                        .accessibilityLabel("Yellow bars are \(you.name), coloured bars are the counter")
                    }
                    .frame(height: 29)
                    layer(t, enter: (0.603, 0.628), shift: 6) {
                        Text("These are the fighters \(you.name) struggles against. Learn their strengths before your next match.")
                            .text(16, .regular, Color.white.opacity(0.86))
                            .lineSpacing(4.9)
                            .fixedSize(horizontal: false, vertical: true)
                            .frame(width: 345, alignment: .leading)
                    }
                }
                .frame(width: 345, alignment: .leading)
            }
            .at(24, 186)
            ForEach(0..<2, id: \.self) { index in matchCard(index, s) }
            versusSlash(s)
        }
        .accessibilityHidden(s.step != 3)
        .allowsHitTesting(s.step == 3)
    }

    private func matchCard(_ index: Int, _ s: Snapshot) -> some View {
        let t = s.t
        let i = Double(index)
        let fighter = s.counters[index]
        let you = s.you
        let expanded = index == 0 ? model.firstExpanded.value : model.secondExpanded.value
        let isOpen = (index == 0 ? model.firstTarget : model.secondTarget) > 0.5
        let opening = M.ramp(t, 0.632 + i * 0.005, 0.657 + i * 0.005) * expanded
        let enter = M.ramp(t, 0.6 + i * 0.006, 0.628 + i * 0.006)
        let lungeEnter = M.ramp(t, 0.6 + i * 0.006, 0.638 + i * 0.006, moving: true)
        let bodyOpacity = M.ramp(t, 0.642 + i * 0.005, 0.667 + i * 0.005)
        let head = Roster.containIn(fighter, CGRect(x: 12, y: 9, width: 87, height: 101))
        let accentColor = accent(you.accent)
        let rise = 12 * (1 - enter) - (index == 1 ? 128 * (1 - model.firstExpanded.value) : 0)
        return ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: 11, style: .continuous).fill(shade)
                .frame(width: 353, height: 192)
                .scaleEffect(x: 1, y: (64 + 128 * opening) / 192, anchor: .topLeading)
            // Body: the lower half of the art and the four metric bars, sliding out from under the header.
            ZStack(alignment: .topLeading) {
                Image(uiImage: fighter.image).resizable().interpolation(.high)
                    .frame(width: head.width, height: head.height)
                    .offset(x: head.minX, y: head.minY - 64)
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 5) {
                    ForEach(0..<4, id: \.self) { metric in metricRow(metric, index: index, fighter: fighter, you: you, s) }
                }
                .frame(width: 219, alignment: .leading)
                .offset(x: 111)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(Roster.metricLabels.enumerated().map { "\($0.element): \(fighter.name) \(Int((fighter.metrics[$0.offset] * 100).rounded())), \(you.name) \(Int((you.metrics[$0.offset] * 100).rounded()))" }.joined(separator: ". "))
            }
            .frame(width: 353, height: 128, alignment: .topLeading)
            .offset(y: -128 * (1 - opening))
            .opacity(bodyOpacity)
            .frame(width: 353, height: 128, alignment: .topLeading)
            .clipped()
            .offset(y: 64)
            .allowsHitTesting(false)
            .accessibilityHidden(!isOpen)
            FighterProfileTourPressable(action: { if !model.suppressTap { model.toggle(card: index) } }) { pressed in
                ZStack(alignment: .topLeading) {
                    Image(uiImage: fighter.image).resizable().interpolation(.high)
                        .frame(width: head.width, height: head.height)
                        .scaleEffect(1.12 - 0.12 * lungeEnter)
                        .offset(x: 46 * (1 - lungeEnter))
                        .offset(x: head.minX, y: head.minY)
                        .frame(width: 353, height: 64, alignment: .topLeading)
                        .clipShape(UnevenRoundedRectangle(topLeadingRadius: 11, topTrailingRadius: 11, style: .continuous))
                        .allowsHitTesting(false)
                        .accessibilityHidden(true)
                    PressHighlight(pressed: pressed, radius: 11)
                    VStack(alignment: .leading, spacing: 4) {
                        Text(fighter.name).text(20, .heavy, .white, tracking: -0.3)
                        Text("\(fighter.tier) Tier").text(13, .regular, accentColor)
                    }
                    .offset(x: 111, y: 13)
                    Image(systemName: "chevron.up").font(.system(size: 17, weight: .bold)).foregroundStyle(accentColor)
                        .rotationEffect(.degrees(180 * (1 - expanded)))
                        .offset(x: 353 - 16 - 17, y: 23)
                }
                .frame(width: 353, height: 64, alignment: .topLeading)
                .clipShape(RoundedRectangle(cornerRadius: 11, style: .continuous))
                .contentShape(Rectangle())
            }
            .frame(width: 353, height: 64)
            .disabled(s.step != 3)
            .accessibilityLabel("\(fighter.name) matchup statistics, \(fighter.tier) tier")
            .accessibilityValue(isOpen ? "expanded" : "collapsed")
        }
        .frame(width: 353, height: 192, alignment: .topLeading)
        .opacity(enter)
        .scaleEffect(0.975 + 0.025 * enter, anchor: .top)
        .offset(y: rise)
        .at(20, 321 + Double(index) * 205)
    }

    private func metricRow(_ metric: Int, index: Int, fighter: Fighter, you: Fighter, _ s: Snapshot) -> some View {
        let grow = M.ramp(s.t, 0.655 + Double(index) * 0.005 + Double(metric) * 0.002, 0.69 + Double(index) * 0.005 + Double(metric) * 0.002)
        return VStack(alignment: .leading, spacing: 0) {
            Text(Roster.metricLabels[metric]).text(12, .regular, .white).frame(height: 15).padding(.bottom, 3)
            rail(fighter.metrics[metric] * grow, accent(s.you.accent))
            rail(you.metrics[metric] * grow, yellow).padding(.top, 2)
        }
    }

    private func rail(_ amount: Double, _ color: Color) -> some View {
        ZStack(alignment: .leading) {
            RoundedRectangle(cornerRadius: 1.5).fill(lift)
            RoundedRectangle(cornerRadius: 1.5).fill(color).scaleEffect(x: amount, y: 1, anchor: .leading)
        }
        .frame(height: 3)
        .clipShape(RoundedRectangle(cornerRadius: 1.5))
    }

    /// A Smash-style VS beat between learning the fighter and meeting its counters.
    private func versusSlash(_ s: Snapshot) -> some View {
        let t = s.t
        let p = M.ramp(t, 0.552, 0.59, moving: true)
        let slashOpacity = t > 0.552 && t < 0.6 ? sin(Double.pi * M.ramp(t, 0.552, 0.6)) : 0
        let arrive = M.ramp(t, 0.556, 0.574)
        let leave = M.ramp(t, 0.588, 0.606)
        let flash = t > 0.566 && t < 0.586 ? 0.16 * sin(Double.pi * M.ramp(t, 0.566, 0.586)) : 0
        let accentColor = accent(s.you.accent)
        return ZStack(alignment: .topLeading) {
            Rectangle().fill(Color.white).frame(width: M.width + 80, height: s.stageHeight + 120)
                .opacity(flash).at(-40, -60)
            ZStack {
                RoundedRectangle(cornerRadius: 7).fill(accentColor).opacity(0.45).frame(width: 520, height: 14)
                RoundedRectangle(cornerRadius: 1.5).fill(Color.white).frame(width: 520, height: 3)
            }
            .frame(width: 520, height: 14)
            .offset(x: M.mix(-430, 430, p))
            .rotationEffect(.degrees(-22))
            .opacity(slashOpacity)
            .at(-60, 452)
            Text("VS").font(.system(size: 78, weight: .black)).italic().tracking(-4).foregroundStyle(yellow)
                .shadow(color: Color.rgba(7, 31, 105, 0.55), radius: 0, x: 0, y: 4)
                .frame(width: M.width, height: 86)
                .scaleEffect(M.mix(1.6, 1, arrive) + leave * 0.18)
                .rotationEffect(.degrees(-8))
                .opacity(arrive * (1 - leave))
                .at(0, 414)
        }
        .frame(width: M.width, height: s.stageHeight, alignment: .topLeading)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    // MARK: Gestures

    private func inStats(_ point: CGPoint, _ s: Snapshot) -> Bool {
        let x = (point.x - s.stageLeft) / s.scale
        let y = (point.y - s.top) / s.scale
        return s.step == 2 && x >= 20 && x <= 373 && y >= 612 && y <= 712
    }

    private func velocity(endingAt end: TimeInterval, translation: Double) -> Double {
        var recent = samples.filter { $0.t >= end - 0.1 }
        recent.append((x: translation, t: end))
        guard let first = recent.first, let last = recent.last, last.t - first.t > 0.001 else { return 0 }
        return (last.x - first.x) / (last.t - first.t)
    }

    /// Two pans, as in the React Native version: the stat strip's (starts after 8 pt sideways, gives
    /// up after 14 pt vertical) wins inside the strip, and the scene scrub (14 pt / 18 pt) everywhere else.
    private func dragGesture(_ s: Snapshot) -> some Gesture {
        DragGesture(minimumDistance: 8, coordinateSpace: .named(Self.space))
            .onChanged { value in
                let tx = value.translation.width, ty = value.translation.height
                model.suppressTap = true
                samples.append((x: tx, t: value.time.timeIntervalSinceReferenceDate))
                if samples.count > 12 { samples.removeFirst(samples.count - 12) }
                if dragMode == .undecided {
                    if inStats(value.startLocation, s) {
                        if abs(ty) > 14 { dragMode = .failed } else if abs(tx) > 8 { dragMode = .stats; model.statsStart() }
                    } else {
                        if abs(ty) > 18 { dragMode = .failed } else if abs(tx) > 14 { dragMode = .pan; model.panStart(translationX: tx) }
                    }
                }
                switch dragMode {
                case .pan: model.panUpdate(translationX: tx)
                case .stats: model.statsUpdate(translationX: tx)
                default: break
                }
            }
            .onEnded { value in
                let v = velocity(endingAt: value.time.timeIntervalSinceReferenceDate, translation: value.translation.width)
                switch dragMode {
                case .pan: model.panEnd(velocityX: v)
                case .stats: model.statsEnd(velocityX: v)
                default: break
                }
                dragMode = .undecided
                samples = []
                // A tap that the drag also produced arrives with the release; let it pass first.
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.05) { model.suppressTap = false }
            }
    }
}

#Preview {
    FighterProfileTour(autoPlay: true)
}
