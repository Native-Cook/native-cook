import SwiftUI
import UIKit

// Shadows, icons, buttons and small pieces shared by the screens.

/// One layer of a CSS `box-shadow`, as React Native's `boxShadow` takes it.
struct FolioFeatureLibraryShadow {
    var x: Double
    var y: Double
    var blur: Double
    var spread: Double
    var color: Color
}

enum FolioFeatureLibraryShadows {
    /// `SHADOW` (line 581): `0px 1px 2px rgba(0,0,0,0.04), 0px 10px 28px -8px rgba(0,0,0,0.12)`.
    static let card: [FolioFeatureLibraryShadow] = [
        FolioFeatureLibraryShadow(x: 0, y: 1, blur: 2, spread: 0, color: Color.black.opacity(0.04)),
        FolioFeatureLibraryShadow(x: 0, y: 10, blur: 28, spread: -8, color: Color.black.opacity(0.12)),
    ]

    static func single(y: Double, blur: Double, spread: Double = 0, opacity: Double) -> [FolioFeatureLibraryShadow] {
        [FolioFeatureLibraryShadow(x: 0, y: y, blur: blur, spread: spread, color: Color.black.opacity(opacity))]
    }
}

/// A rounded rectangle's outer shadows, drawn the way CSS does: blur radius b is a Gaussian of b / 2 (SwiftUI's
/// `radius`), the spread grows or shrinks the shadow's shape, and nothing is drawn inside the box when `clipInside`
/// is set (for a translucent surface). The shape that casts the shadow sits behind the surface.
struct FolioFeatureLibraryShadowLayers: View {
    let layers: [FolioFeatureLibraryShadow]
    let cornerRadius: Double
    var clipInside = false

    var body: some View {
        let stack = ZStack {
            ForEach(layers.indices, id: \.self) { index in
                let layer = layers[index]
                RoundedRectangle(cornerRadius: cornerRadius)
                    .inset(by: CGFloat(-layer.spread))
                    .fill(Color.white)
                    .shadow(color: layer.color, radius: CGFloat(layer.blur / 2), x: CGFloat(layer.x), y: CGFloat(layer.y))
            }
        }
        if clipInside {
            stack.mask {
                Color.white
                    .padding(-200)
                    .overlay {
                        RoundedRectangle(cornerRadius: cornerRadius)
                            .blendMode(.destinationOut)
                    }
                    .compositingGroup()
            }
        } else {
            stack
        }
    }
}

/// `<Ionicons name size color />` of `@expo/vector-icons`: one character of the bundled Ionicons font, at `size`, in
/// `color`. Like any React Native `Text` it follows the user's text size (`type`). The codepoints are the font's own,
/// read from its glyph names.
struct FolioFeatureLibraryIcon: View {
    let name: String
    let size: Double
    let color: Color
    let type: FolioFeatureLibraryType

    private var glyph: String {
        switch name {
        case "chevron-back": return "\u{F229}"
        case "chevron-forward": return "\u{F23B}"
        case "chevron-down": return "\u{F232}"
        case "add": return "\u{F103}"
        case "arrow-down": return "\u{F12D}"
        case "heart": return "\u{F36A}"
        case "heart-outline": return "\u{F377}"
        case "shuffle": return "\u{F583}"
        case "globe-outline": return "\u{F350}"
        case "share-outline": return "\u{F572}"
        case "chatbubble-outline": return "\u{F215}"
        case "checkmark-circle-outline": return "\u{F21F}"
        default: return ""
        }
    }

    var body: some View {
        let points = size * type.factor(maxScale: nil, scales: true)
        Text(verbatim: glyph)
            .font(.custom(FolioFeatureLibraryAssets.ionicons, fixedSize: CGFloat(points)))
            .foregroundColor(color)
            .lineLimit(1)
            .fixedSize()
    }
}

/// `PressScale` (shared/PressScale.tsx): the whole surface settles to a scale in 150 ms on a strong ease-out and
/// returns on the same curve; under Reduce Motion it dims to 0.72 instead.
struct FolioFeatureLibraryPressStyle: ButtonStyle {
    var scale = 0.96
    var reduced = false

    func makeBody(configuration: Configuration) -> some View {
        let pressed = configuration.isPressed
        configuration.label
            .scaleEffect(pressed && !reduced ? CGFloat(scale) : 1)
            .opacity(reduced && pressed ? 0.72 : 1)
            .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.15), value: pressed)
    }
}

/// A `Pressable` row that dims to 0.6 while pressed (`AppRow`, line 136).
struct FolioFeatureLibraryRowStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .opacity(configuration.isPressed ? 0.6 : 1)
    }
}

/// Lays children out in a row the way Yoga does for `flexGrow: 1`: each takes its own width, and the space left over
/// is shared equally. Every child is as tall as the tallest.
struct FolioFeatureLibraryGrowRow: Layout {
    var spacing: CGFloat

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        var total: CGFloat = 0
        var tallest: CGFloat = 0
        for subview in subviews {
            let ideal = subview.sizeThatFits(.unspecified)
            total += ideal.width
            tallest = max(tallest, ideal.height)
        }
        total += spacing * CGFloat(max(0, subviews.count - 1))
        return CGSize(width: proposal.width ?? total, height: tallest)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        if subviews.isEmpty { return }
        let ideals = subviews.map { $0.sizeThatFits(.unspecified) }
        let used = ideals.reduce(CGFloat(0)) { $0 + $1.width } + spacing * CGFloat(subviews.count - 1)
        let extra = (bounds.width - used) / CGFloat(subviews.count)
        var x = bounds.minX
        for index in subviews.indices {
            let childWidth = max(0, ideals[index].width + extra)
            subviews[index].place(
                at: CGPoint(x: x, y: bounds.minY),
                anchor: .topLeading,
                proposal: ProposedViewSize(width: childWidth, height: bounds.height)
            )
            x += childWidth + spacing
        }
    }
}

/// The block's `Button` (lines 122-128).
struct FolioFeatureLibraryButton: View {
    enum Look {
        case primary
        case dark
        case secondary
    }

    let title: String
    var look: Look = .primary
    var pill = false
    var icon: String?
    var busy = false
    var disabled = false
    let engine: FolioFeatureLibraryEngine
    let frame: FolioFeatureLibraryFrame
    let action: () -> Void

    private var faded: Bool { disabled || busy }

    private var background: Color {
        if faded { return Color(folio: 0x7FBFAE) }
        switch look {
        case .primary: return Color(folio: 0x13775E)
        case .dark: return Color(folio: 0x2E2E2E)
        case .secondary: return Color(folio: 0xFFFFFF)
        }
    }

    private var ink: Color {
        look == .secondary ? Color(folio: 0x262626) : Color(folio: 0xFFFFFF)
    }

    private var labelColor: Color {
        faded ? Color(folio: 0xFFFFFF, alpha: Double(0xD9) / 255) : ink
    }

    private var minHeight: Double {
        switch look {
        case .primary: return 52
        case .dark, .secondary: return 48
        }
    }

    private var radius: Double { minHeight / 2 }

    private var shadows: [FolioFeatureLibraryShadow] {
        if faded { return [] }
        switch look {
        case .primary:
            return [FolioFeatureLibraryShadow(x: 0, y: 10, blur: 22, spread: -12, color: Color(folio: 0x105A46, alpha: 0.9))]
        case .dark:
            return []
        case .secondary:
            return FolioFeatureLibraryShadows.single(y: 1, blur: 3, opacity: 0.05)
        }
    }

    var body: some View {
        let t = frame.time
        let iconName = icon
        let content = HStack(spacing: 8) {
            if let iconName = iconName {
                iconView(iconName, time: t)
            }
            FolioFeatureLibraryLabel(text: title, style: FolioFeatureLibraryStyles.buttonLabel(color: labelColor), type: frame.type, alignment: .center)
        }
        .padding(.horizontal, pill ? 18 : 22)
        .modifier(SizeModifier(pill: pill, minHeight: CGFloat(minHeight)))
        .background(
            ZStack {
                FolioFeatureLibraryShadowLayers(layers: shadows, cornerRadius: radius)
                RoundedRectangle(cornerRadius: radius).fill(background)
            }
        )
        .overlay(highlight)
        .overlay(hairline)

        Button(action: action) {
            content
        }
        .buttonStyle(FolioFeatureLibraryPressStyle(scale: 0.96, reduced: frame.reduced))
        .disabled(disabled)
        .accessibilityLabel(title)
        .accessibilityAddTraits(.isButton)
    }

    /// `pill` is a fixed 252 pt wide (`buttonPill`); every other button fills the width it is given (`flexGrow: 1`).
    private struct SizeModifier: ViewModifier {
        let pill: Bool
        let minHeight: CGFloat

        @ViewBuilder
        func body(content: Content) -> some View {
            if pill {
                content
                    .frame(width: 252)
                    .frame(minHeight: minHeight)
            } else {
                content
                    .frame(maxWidth: .infinity)
                    .frame(minHeight: minHeight)
            }
        }
    }

    /// `0px 1px 0px rgba(255,255,255,0.16) inset` of the primary button: a one-point light line at the top edge.
    @ViewBuilder
    private var highlight: some View {
        if look == .primary && !faded {
            RoundedRectangle(cornerRadius: radius)
                .fill(Color.white.opacity(0.16))
                .mask {
                    Color.white
                        .overlay {
                            RoundedRectangle(cornerRadius: radius)
                                .offset(y: 1)
                                .blendMode(.destinationOut)
                        }
                        .compositingGroup()
                }
                .allowsHitTesting(false)
        }
    }

    /// `borderWidth: StyleSheet.hairlineWidth, borderColor: '#DADADA'` of the secondary button.
    @ViewBuilder
    private var hairline: some View {
        if look == .secondary {
            RoundedRectangle(cornerRadius: radius)
                .strokeBorder(Color(folio: 0xDADADA), lineWidth: 1.0 / CGFloat(max(1, engine.pixelScale)))
                .allowsHitTesting(false)
        }
    }

    /// The icon; the heart of a saved book pops in with `ZoomIn.springify().damping(11).stiffness(300)`.
    private func iconView(_ name: String, time: Double) -> some View {
        let isHeart = name == "heart"
        let popped = isHeart ? engine.zoom("heart", spring: FolioFeatureLibrarySpring(damping: 11, stiffness: 300), at: time) : 1
        return FolioFeatureLibraryIcon(
            name: name,
            size: name == "add" ? 18 : 17,
            color: isHeart ? Color(folio: 0xD9534F) : ink,
            type: frame.type
        )
        .scaleEffect(CGFloat(popped))
    }
}

/// A tiny "working" mark: three soft dots (lines 131-133).
struct FolioFeatureLibraryWorking: View {
    var body: some View {
        HStack(spacing: -2) {
            ForEach(0..<3, id: \.self) { index in
                Circle()
                    .fill(Color(folio: 0x4A4A4A))
                    .frame(width: 9, height: 9)
                    .opacity(1 - Double(index) * 0.22)
            }
        }
        .padding(.leading, -2)
    }
}

/// A UIView that reports its position to the engine, for `measureInWindow`.
struct FolioFeatureLibraryAnchor: UIViewRepresentable {
    let engine: FolioFeatureLibraryEngine
    let key: String

    func makeUIView(context: Context) -> UIView {
        let view = UIView()
        view.isUserInteractionEnabled = false
        view.backgroundColor = .clear
        engine.anchors[key] = FolioFeatureLibraryWeakView(view)
        return view
    }

    func updateUIView(_ view: UIView, context: Context) {
        engine.anchors[key] = FolioFeatureLibraryWeakView(view)
    }
}

/// Book covers (lines 69-84). Small Rituals is typographic, set in code; the tour uses an illustrated plate with its
/// title set in code. None of the cover text follows the user's text size (`allowFontScaling={false}`).
struct FolioFeatureLibraryCover: View {
    let book: FolioFeatureLibraryBookID
    let large: Bool

    private var corner: Double { large ? 8 : 4 }

    var body: some View {
        let cover = Group {
            if book == .noticing {
                tour
            } else {
                rituals
            }
        }
        .frame(width: large ? 190 : 74, height: large ? 268 : 105)
        .clipShape(RoundedRectangle(cornerRadius: corner))
        if large {
            cover.background(FolioFeatureLibraryShadowLayers(layers: [FolioFeatureLibraryShadow(x: 0, y: 14, blur: 26, spread: -10, color: Color.black.opacity(0.45))], cornerRadius: corner))
        } else {
            cover
        }
    }

    private func style(_ size: Double, _ largeSize: Double, weight: UIFont.Weight = .regular, face: FolioFeatureLibraryTextStyle.Face = .system, lineHeight: Double? = nil, largeLineHeight: Double? = nil, tracking: Double = 0, color: UInt32) -> FolioFeatureLibraryTextStyle {
        FolioFeatureLibraryTextStyle(
            size: large ? largeSize : size,
            weight: weight,
            face: face,
            lineHeight: large ? largeLineHeight : lineHeight,
            tracking: tracking,
            color: Color(folio: color),
            scales: false
        )
    }

    /// The illustrated plate with the title across its top band.
    private var tour: some View {
        let still = FolioFeatureLibraryType()
        return ZStack(alignment: .top) {
            Image(uiImage: FolioFeatureLibraryAssets.image("cover-everything"))
                .resizable()
                .scaledToFill()
                .frame(width: large ? 190 : 74, height: large ? 268 : 105)
                .clipped()
            VStack(spacing: 0) {
                FolioFeatureLibraryLabel(
                    text: "THEO MARSH",
                    style: style(6, 13, weight: .heavy, tracking: 0.6, color: 0xF2C94C),
                    type: still
                )
                FolioFeatureLibraryLabel(
                    text: "A BRIEF TOUR OF\nALMOST\nEVERYTHING",
                    style: style(8.5, 22, face: .georgiaBold, lineHeight: 9.5, largeLineHeight: 24, color: 0xF5E9C8),
                    type: still,
                    alignment: .center
                )
                .padding(.top, 2)
            }
            .padding(.horizontal, 6)
            .padding(.top, 6)
        }
    }

    /// The typographic cover: a dark frame around a cream page.
    private var rituals: some View {
        let still = FolioFeatureLibraryType()
        let page = ZStack {
            Color(folio: 0xF4EEDF)
            VStack(spacing: 0) {
                FolioFeatureLibraryLabel(
                    text: "Small Steps,\nLasting Change",
                    style: style(5, 11, weight: .bold, lineHeight: 6.5, largeLineHeight: 14, color: 0x2A2A2A),
                    type: still,
                    alignment: .center
                )
                Spacer(minLength: 0)
                FolioFeatureLibraryLabel(
                    text: "Small\nRituals",
                    style: style(17, 44, face: .georgia, lineHeight: 17, largeLineHeight: 44, tracking: -0.5, color: 0xD18A55),
                    type: still,
                    alignment: .center
                )
                Spacer(minLength: 0)
                FolioFeatureLibraryLabel(
                    text: "A gentle way to build good days",
                    style: style(3.5, 7, color: 0x3A3A3A),
                    type: still,
                    alignment: .center
                )
                Spacer(minLength: 0)
                FolioFeatureLibraryLabel(
                    text: "Mira Wells",
                    style: style(7, 15, weight: .bold, tracking: 0.3, color: 0x1F1F1F),
                    type: still
                )
            }
            .padding(.vertical, 6)
            .padding(.horizontal, 3)
        }
        .overlay(alignment: .topTrailing) {
            if large {
                badge(still)
                    .offset(x: 6, y: -6)
            }
        }
        return ZStack {
            Color(folio: 0x161616)
            page.padding(3)
        }
    }

    private func badge(_ still: FolioFeatureLibraryType) -> some View {
        ZStack {
            Circle().fill(Color(folio: 0xF3B53F))
            Circle().strokeBorder(Color(folio: 0xF7D27A), lineWidth: 2)
            FolioFeatureLibraryLabel(
                text: "NEW\nSAMPLE\nREAD",
                style: FolioFeatureLibraryTextStyle(size: 6.5, weight: .heavy, lineHeight: 7.5, color: Color(folio: 0x3A2A10), scales: false),
                type: still,
                alignment: .center
            )
        }
        .frame(width: 46, height: 46)
    }
}

/// A rounded surface with its outer shadows: a `View` of React Native with `backgroundColor`, `borderRadius` and
/// `boxShadow`. Use it as a `background`.
struct FolioFeatureLibrarySurface: View {
    let radius: Double
    var fill: Color = Color(folio: 0xFFFFFF)
    var shadows: [FolioFeatureLibraryShadow] = []

    var body: some View {
        ZStack {
            if !shadows.isEmpty {
                FolioFeatureLibraryShadowLayers(layers: shadows, cornerRadius: radius)
            }
            RoundedRectangle(cornerRadius: radius).fill(fill)
        }
    }
}

/// The round white buttons of the app bars (`circleButton`, line 620) and the chevron circles (`chevronCircle`).
struct FolioFeatureLibraryCircle: View {
    let size: Double
    let shadow: [FolioFeatureLibraryShadow]
    let icon: String
    let iconSize: Double
    let iconColor: Color
    let type: FolioFeatureLibraryType

    var body: some View {
        FolioFeatureLibraryIcon(name: icon, size: iconSize, color: iconColor, type: type)
            .frame(width: CGFloat(size), height: CGFloat(size))
            .background(FolioFeatureLibrarySurface(radius: size / 2, shadows: shadow))
    }
}
