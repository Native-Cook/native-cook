import SwiftUI

/// What the scroll view holds: the nav capsule, the violet hero panel with the card and its
/// copy, the partner logos and the domain pill. The content of `<ScrollView>` in the React Native
/// file, laid out in points of the screen it is given.
struct CipherSeamPage: View {
    let layout: CipherSeamLayout
    let playback: CipherSeamPlayback
    let reduced: Bool
    /// The hero's clock stops while this is true.
    let timelinePaused: Bool
    /// Whether the hero answers a drag.
    let touchEnabled: Bool
    let onTalk: () -> Void
    let onReplay: () -> Void
    let onDragStart: () -> Void
    let onDragMove: (CGFloat) -> Void
    let onDragEnd: (CGFloat) -> Void
    let onDragFinish: () -> Void
    /// The height of everything, padding included (`onContentSizeChange`).
    let onContentHeight: (CGFloat) -> Void

    /// `panelHeight`: measured, because the copy's height depends on the text size; 760 until then.
    @State private var panelHeight: CGFloat = 760

    private static let ink = CipherSeamPalette.buttonInk
    private static let logoInk = CipherSeamPalette.rgb(0xE9E6F2)

    var body: some View {
        VStack(spacing: 0) {
            nav
                .zIndex(2)
            panel
                // `marginTop: -60`: the panel reaches 60 pt under the nav.
                .padding(.top, -60)
            logos
            domain
        }
        .padding(.top, layout.top)
        .padding(.bottom, layout.bottom + 90)
        .frame(width: layout.size.width, alignment: .top)
        .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { height in
            onContentHeight(height)
        }
    }

    // MARK: - Nav

    /// `styles.nav`: a frosted capsule, 44 pt tall, 10 pt in from the sides.
    private var nav: some View {
        HStack(spacing: 0) {
            CipherSeamLogoMark()
            Spacer(minLength: 0)
            HStack(spacing: 10) {
                CipherSeamLabel(
                    text: "Log in", size: 13, weight: .medium,
                    color: CipherSeamPalette.rgb(0xE8E4F2), maxScale: 1.3
                )
                talkButton
                CipherSeamIcon(glyph: .menu, size: 18, color: CipherSeamPalette.rgb(0xC9C3D6))
                    .frame(width: 40, height: 30)
                    .background(RoundedRectangle(cornerRadius: 15, style: .circular).fill(CipherSeamPalette.rgb(0x2A2735)))
                    .accessibilityHidden(true)
            }
        }
        // Border 1 + paddingLeft 14 and paddingRight 6.
        .padding(.leading, 15)
        .padding(.trailing, 7)
        .frame(height: 44)
        .background(RoundedRectangle(cornerRadius: 22, style: .circular).fill(CipherSeamPalette.rgb(0x0B0718)))
        .overlay {
            RoundedRectangle(cornerRadius: 22, style: .circular)
                .strokeBorder(CipherSeamPalette.rgb(0xFFFFFF, alpha: 0x1F), lineWidth: 1)
        }
        .padding(.horizontal, 10)
        .padding(.top, 6)
    }

    /// `navPill`: the white "Talk to an Expert" capsule.
    private var talkButton: some View {
        Button(action: onTalk) {
            CipherSeamLabel(
                text: "Talk to an Expert", size: 13, weight: .semibold,
                color: CipherSeamPalette.rgb(0x110A20), maxScale: 1.3
            )
            .padding(.horizontal, 11)
            .frame(height: 28)
            .background(RoundedRectangle(cornerRadius: 14, style: .circular).fill(Color.white))
        }
        .buttonStyle(CipherSeamPressStyle(reduced: reduced))
        .accessibilityLabel("Talk to an expert about the tokenization demo")
    }

    // MARK: - Hero panel

    /// `heroPanel`: the violet gradient with its dark bowl, 61 pt of padding (60 under the nav and
    /// the hero's 1 pt margin), the hero, and the copy; rounded at the bottom.
    private var panel: some View {
        VStack(spacing: 0) {
            Color.clear.frame(height: 61)
            hero
            copy
                .padding(.top, 22)
        }
        .frame(width: layout.size.width)
        .background { panelBackground }
        .clipShape(UnevenRoundedRectangle(bottomLeadingRadius: 32, bottomTrailingRadius: 32, style: .circular))
        .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { height in
            if abs(height - panelHeight) > 0.5 { panelHeight = height }
        }
    }

    private var panelBackground: some View {
        ZStack {
            LinearGradient(stops: gradientStops, startPoint: .top, endPoint: .bottom)
            Canvas { context, _ in
                CipherSeamDrawing.bowl(
                    context, heroWidth: Double(layout.heroWidth), centerY: Double(layout.cardBottom) - 92
                )
            }
        }
    }

    /// The panel's colours and where they sit: dark down to the lane, then up through violet.
    private var gradientStops: [Gradient.Stop] {
        let height = max(panelHeight, 1)
        let colors: [UInt32] = [0x06040F, 0x06040F, 0x2C1478, 0x4B2FD6, 0x6A45F5, 0xA48CFF]
        let locations: [CGFloat] = [
            0,
            layout.laneTop / height,
            (layout.cardBottom + 20) / height,
            (layout.heroBottom + 90) / height,
            0.88,
            1,
        ]
        var previous: CGFloat = 0
        var stops: [Gradient.Stop] = []
        for index in 0..<colors.count {
            let location = min(1, max(previous, locations[index]))
            previous = location
            stops.append(Gradient.Stop(color: CipherSeamPalette.rgb(colors[index]), location: location))
        }
        return stops
    }

    private var hero: some View {
        CipherSeamHero(layout: layout, playback: playback, reduced: reduced, paused: timelinePaused)
            .overlay {
                CipherSeamTouch(
                    enabled: touchEnabled,
                    onStart: onDragStart,
                    onUpdate: onDragMove,
                    onEnd: onDragEnd,
                    onFinalize: onDragFinish
                )
            }
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("A sample payment card crosses a violet seam and becomes a token pattern. Drag across to scrub or use Replay below.")
            .accessibilityAddTraits(.isImage)
    }

    // MARK: - Copy

    /// `styles.copy`: title, subtitle, description and the Replay button, centred.
    private var copy: some View {
        VStack(spacing: 0) {
            CipherSeamLabel(
                text: "Flexible Payments Security", size: 28, weight: .medium, color: .white,
                lineHeight: 34, kerning: -0.5, maxScale: 1.5, alignment: .center
            )
            .accessibilityAddTraits(.isHeader)
            CipherSeamLabel(
                text: "Maximum protection, minimum compliance\nburden", size: 15,
                color: CipherSeamPalette.rgb(0xE3DAFF), lineHeight: 18, alignment: .center
            )
            .padding(.top, 6)
            CipherSeamLabel(
                text: "Take control of your payments stack \u{2014} easily tokenize cards, optimize margins, comply with PCI, avoid gateway lock-in, or spin-up card issuing programs.",
                size: 11.4, color: CipherSeamPalette.rgb(0xE6DEFF), lineHeight: 20, alignment: .center
            )
            .frame(maxWidth: 380)
            .padding(.top, 12)
            replayButton
                .padding(.top, 14)
        }
        .padding(.horizontal, 10)
        .padding(.bottom, 36)
        .frame(maxWidth: .infinity)
    }

    /// `styles.cta`: the white pill that replays the pass.
    private var replayButton: some View {
        Button(action: onReplay) {
            HStack(spacing: 6) {
                CipherSeamLabel(text: "Talk to an Expert", size: 14, weight: .medium, color: Self.ink, alignment: .center)
                CipherSeamIcon(glyph: .chevronForward, size: 15, color: Self.ink)
            }
            .padding(.horizontal, 14)
            .frame(height: 34)
            .background(RoundedRectangle(cornerRadius: 17, style: .circular).fill(Color.white))
        }
        .buttonStyle(CipherSeamPressStyle(reduced: reduced))
        .accessibilityLabel("Replay the card transformation")
    }

    // MARK: - Below the panel

    /// `styles.logos`: three invented partner marks spread over the width, 67 pt under the panel.
    private var logos: some View {
        HStack(spacing: 0) {
            HStack(spacing: 5) {
                CipherSeamIcon(glyph: .reorderThree, size: 18, color: Self.logoInk)
                CipherSeamLabel(text: "LEDGERLY", size: 13, weight: .heavy, color: Self.logoInk, kerning: 1.6)
            }
            Spacer(minLength: 0)
            HStack(spacing: 5) {
                CipherSeamIcon(glyph: .sparkles, size: 14, color: Self.logoInk)
                CipherSeamLabel(text: "Tripwell", size: 16, weight: .bold, color: Self.logoInk, kerning: -0.3)
            }
            Spacer(minLength: 0)
            HStack(spacing: 5) {
                CipherSeamIcon(glyph: .radioButtonOn, size: 15, color: Self.logoInk)
                CipherSeamLabel(text: "orbita", size: 15, weight: .regular, color: Self.logoInk)
            }
        }
        .padding(.horizontal, 22)
        .padding(.top, 67)
        .accessibilityHidden(true)
    }

    /// `styles.domain`: the "cipher.dev" pill.
    private var domain: some View {
        CipherSeamLabel(text: "cipher.dev", size: 12, color: CipherSeamPalette.rgb(0xBDB6CC))
            .padding(.horizontal, 14)
            .frame(height: 28)
            .background(RoundedRectangle(cornerRadius: 14, style: .circular).fill(CipherSeamPalette.rgb(0x1B1724)))
            .padding(.top, 4)
    }
}

/// The layers mark of the nav (the 26 x 22 `Svg`): a diamond over two chevrons.
struct CipherSeamLogoMark: View {
    var body: some View {
        Canvas { context, _ in
            let ink = CipherSeamPalette.rgb(0xF4F1FF)
            var diamond = Path()
            diamond.move(to: CGPoint(x: 13, y: 1))
            diamond.addLine(to: CGPoint(x: 25, y: 7))
            diamond.addLine(to: CGPoint(x: 13, y: 13))
            diamond.addLine(to: CGPoint(x: 1, y: 7))
            diamond.closeSubpath()
            context.fill(diamond, with: .color(ink))

            let style = StrokeStyle(lineWidth: 2.2, lineCap: .butt, lineJoin: .round)
            for y in [11.5, 15.5] {
                var chevron = Path()
                chevron.move(to: CGPoint(x: 1, y: y))
                chevron.addLine(to: CGPoint(x: 13, y: y + 6))
                chevron.addLine(to: CGPoint(x: 25, y: y))
                context.stroke(chevron, with: .color(ink), style: style)
            }
        }
        .frame(width: 26, height: 22)
    }
}
