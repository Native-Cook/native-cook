import SwiftUI

/// The foot of the screen: at the reference layout the step dots and the round arrow that applies
/// the profile in the gate; at large text sizes (or a narrow window) previous / next arrows with
/// tappable dots, a wide apply button and a line of status.
struct SilverScreenCarouselFooter: View {
    let model: SilverScreenCarouselModel
    let layout: SilverScreenCarouselLayout
    let reduced: Bool

    private var fontScale: CGFloat {
        return layout.fontScale
    }

    var body: some View {
        let selected = model.selected
        let applied = model.applied
        VStack(spacing: 0) {
            if layout.referenceLayout {
                referencePager(selected: selected, applied: applied)
            } else {
                pager(selected: selected)
                applyButton(selected: selected, applied: applied)
            }
            if applied != nil || !layout.referenceLayout {
                status(applied: applied)
            }
        }
        .padding(.horizontal, 34)
        .padding(.top, layout.footerMargin)
        .frame(maxWidth: .infinity)
    }

    // MARK: Reference layout

    /// `sourcePager`: the dots count onboarding steps (this is the first), not film profiles.
    private func referencePager(selected: Int, applied: String?) -> some View {
        let name = SilverScreenCarouselProfiles.all[selected].name
        let label = applied != nil ? "Try another film profile" : "Use \(name)"
        let glyph = applied != nil ? "✓" : "→"
        let glyphStyle = applied != nil
            ? SilverScreenCarouselTextStyle.arrowGlyph.with(color: SilverScreenCarouselPalette.arrowGlyphApplied)
            : SilverScreenCarouselTextStyle.arrowGlyph
        return HStack(spacing: 0) {
            // `sourceDotsTarget`: 48 x 48, the dots start at its left edge.
            HStack(spacing: 6) {
                ForEach(0..<4, id: \.self) { index in
                    SilverScreenCarouselDot(active: index == 0, animated: false)
                }
            }
            .frame(width: 48, height: 48, alignment: .leading)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("Step 1 of 4")
            .accessibilityAddTraits(.isStaticText)
            Spacer(minLength: 0)
            Button {
                if applied != nil {
                    model.tryAnother()
                } else {
                    model.apply()
                }
            } label: {
                SilverScreenCarouselSwapText(text: glyph, style: glyphStyle, fontScale: fontScale, reduced: reduced)
                    .frame(width: 58, height: 58)
                    .background(
                        Circle().fill(applied != nil ? SilverScreenCarouselPalette.arrowApplied : SilverScreenCarouselPalette.arrow)
                    )
            }
            .buttonStyle(SilverScreenCarouselPressStyle(reduced: reduced))
            .accessibilityLabel(label)
        }
    }

    // MARK: Large text layout

    /// `pager`: previous arrow, four dots, next arrow.
    private func pager(selected: Int) -> some View {
        let profiles = SilverScreenCarouselProfiles.all
        return HStack(spacing: 0) {
            arrowButton(glyph: "←", label: "Previous film profile", dimmed: selected == 0) {
                model.select(selected - 1)
            }
            Spacer(minLength: 0)
            HStack(spacing: 0) {
                ForEach(profiles) { profile in
                    Button {
                        model.select(profile.index)
                    } label: {
                        SilverScreenCarouselDot(active: selected == profile.index, animated: !reduced)
                            .frame(width: 48, height: 48)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(SilverScreenCarouselBareStyle())
                    .accessibilityLabel("Select \(profile.name)")
                    .accessibilityAddTraits(selected == profile.index ? .isSelected : [])
                }
            }
            Spacer(minLength: 0)
            arrowButton(glyph: "→", label: "Next film profile", dimmed: selected == 3) {
                model.select(selected + 1)
            }
        }
        .padding(.bottom, 10)
    }

    /// An arrow of the pager: `disabled` at the ends, 0.32 opaque then, 0.6 while pressed.
    private func arrowButton(glyph: String, label: String, dimmed: Bool, action: @escaping () -> Void) -> some View {
        return Button(action: action) {
            SilverScreenCarouselLabel(text: glyph, style: .arrowGlyphFixed, fontScale: fontScale, alignment: .center)
                .frame(width: 58, height: 58)
                .background(Circle().fill(SilverScreenCarouselPalette.arrow))
        }
        .buttonStyle(SilverScreenCarouselArrowStyle(dimmed: dimmed))
        .disabled(dimmed)
        .accessibilityLabel(label)
    }

    /// `apply`: the wide button under the pager.
    private func applyButton(selected: Int, applied: String?) -> some View {
        let name = SilverScreenCarouselProfiles.all[selected].name
        let text = applied != nil ? "Try another profile" : "Use \(name)"
        return Button {
            if applied != nil {
                model.tryAnother()
            } else {
                model.apply()
            }
        } label: {
            SilverScreenCarouselSwapText(text: text, style: .applyLabel, fontScale: fontScale, reduced: reduced)
                .padding(.vertical, 12)
                .padding(.horizontal, 20)
                .frame(maxWidth: .infinity, minHeight: 48)
                .background(
                    RoundedRectangle(cornerRadius: 25)
                        .fill(SilverScreenCarouselPalette.apply)
                )
        }
        .buttonStyle(SilverScreenCarouselPressStyle(reduced: reduced))
        .accessibilityLabel(text)
    }

    /// `styles.status`.
    private func status(applied: String?) -> some View {
        let text = applied.map { "\($0) applied to your preview." } ?? "Swipe to find your point of view."
        return SilverScreenCarouselLabel(text: text, style: .status, fontScale: fontScale, alignment: .center)
            .padding(.top, 10)
    }
}

// MARK: - Dot

/// `styles.dot` / `styles.activeDot`: 5 pt grey, or 6 pt and light when active. At large text sizes
/// the change runs 240 ms on cubic-bezier(0.23, 1, 0.32, 1).
struct SilverScreenCarouselDot: View {
    let active: Bool
    let animated: Bool

    private static let curve = Animation.timingCurve(0.23, 1, 0.32, 1, duration: 0.24)

    var body: some View {
        let side: CGFloat = active ? 6 : 5
        Circle()
            .fill(active ? SilverScreenCarouselPalette.dotActive : SilverScreenCarouselPalette.dot)
            .frame(width: side, height: side)
            .animation(animated ? Self.curve : nil, value: active)
    }
}

// MARK: - Button styles

/// `PressScale`: the whole surface settles to 0.96 in 150 ms on a strong ease-out and returns on the
/// same curve; under Reduce Motion it dims to 0.72 instead. A disabled button never moves.
struct SilverScreenCarouselPressStyle: ButtonStyle {
    let reduced: Bool

    private static let curve = Animation.timingCurve(0.23, 1, 0.32, 1, duration: 0.15)

    func makeBody(configuration: Configuration) -> some View {
        let down = configuration.isPressed
        return configuration.label
            .scaleEffect(down && !reduced ? 0.96 : 1)
            .opacity(reduced && down ? 0.72 : 1)
            .animation(Self.curve, value: down)
    }
}

/// The pager's arrows: 0.32 opaque at an end, 0.6 while pressed.
struct SilverScreenCarouselArrowStyle: ButtonStyle {
    let dimmed: Bool

    func makeBody(configuration: Configuration) -> some View {
        return configuration.label
            .opacity(dimmed ? 0.32 : (configuration.isPressed ? 0.6 : 1))
    }
}

/// A button with no pressed look of its own (the dots' 48 pt targets).
struct SilverScreenCarouselBareStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        return configuration.label
    }
}

// MARK: - SwapText

/// `SwapText`: text that changes in place. The old words leave fast (140 ms) and the new ones settle
/// a beat later from 10 pt below (260 ms after 60 ms), so a label reads as one sentence being
/// rewritten rather than a cut. Under Reduce Motion it is a plain cross-fade (180 ms in, 120 ms out).
/// The first render does not animate.
///
/// An invisible copy of the new text sizes the box; the new words sit on top of it and the old ones
/// on top of that while they fade out, in the colour they had.
struct SilverScreenCarouselSwapText: View {
    let text: String
    let style: SilverScreenCarouselTextStyle
    let fontScale: CGFloat
    let reduced: Bool

    @State private var shown: String
    @State private var shownColor: Color
    @State private var previous: String?
    @State private var previousColor: Color
    /// When the text last changed; nil until it does.
    @State private var changedAt: Date?

    init(text: String, style: SilverScreenCarouselTextStyle, fontScale: CGFloat, reduced: Bool) {
        self.text = text
        self.style = style
        self.fontScale = fontScale
        self.reduced = reduced
        _shown = State(initialValue: text)
        _shownColor = State(initialValue: style.color)
        _previousColor = State(initialValue: style.color)
    }

    var body: some View {
        TimelineView(.animation(paused: changedAt == nil)) { timeline in
            let elapsed = changedAt.map { timeline.date.timeIntervalSince($0) }
            let layers = Self.layers(elapsed: elapsed, reduced: reduced)
            face(shown, color: shownColor)
                .opacity(0)
                .accessibilityHidden(true)
                .overlay(alignment: .top) {
                    face(shown, color: shownColor)
                        .opacity(layers.incoming)
                        .offset(y: layers.lift)
                        .accessibilityHidden(true)
                }
                .overlay(alignment: .top) {
                    if layers.outgoing > 0, let old = previous {
                        face(old, color: previousColor)
                            .opacity(layers.outgoing)
                            .accessibilityHidden(true)
                            .allowsHitTesting(false)
                    }
                }
        }
        .onChange(of: text) { _, next in
            previous = shown
            previousColor = shownColor
            shown = next
            shownColor = style.color
            changedAt = Date()
        }
        .task(id: changedAt) {
            guard let stamp = changedAt else { return }
            try? await Task.sleep(for: .seconds(0.5))
            guard !Task.isCancelled, changedAt == stamp else { return }
            changedAt = nil
            previous = nil
        }
    }

    private func face(_ words: String, color: Color) -> some View {
        return SilverScreenCarouselLabel(
            text: words, style: style.with(color: color), fontScale: fontScale, alignment: .center)
    }

    /// The opacity of the new and the old text and the new text's lift, `elapsed` seconds after the change.
    private static func layers(elapsed: Double?, reduced: Bool) -> (incoming: Double, lift: CGFloat, outgoing: Double) {
        guard let elapsed else { return (1, 0, 0) }
        let time = max(0, elapsed)
        if reduced {
            let enter = SilverScreenCarouselMotion.quadInOut(time / 0.18)
            let exit = SilverScreenCarouselMotion.quadInOut(time / 0.12)
            return (enter, 0, 1 - exit)
        }
        let enter = SilverScreenCarouselMotion.settle((time - 0.06) / 0.26)
        let exit = SilverScreenCarouselMotion.settle(time / 0.14)
        return (enter, CGFloat(10 * (1 - enter)), 1 - exit)
    }
}
