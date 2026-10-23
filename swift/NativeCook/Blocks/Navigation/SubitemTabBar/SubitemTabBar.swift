import SwiftUI

struct SubitemTabBarChip: Equatable {
    var label: String
    /// The name of a system symbol.
    var icon: String
}

struct SubitemTabBarTab: Equatable {
    var label: String
    /// The name of a system symbol.
    var icon: String
    /// The chips of the tab's panel, row by row. A tab without rows opens the sign-in form.
    var rows: [[SubitemTabBarChip]]? = nil

    static let samples = [
        SubitemTabBarTab(label: "Work", icon: "briefcase", rows: [
            [.init(label: "All", icon: "square.grid.2x2"), .init(label: "Design", icon: "paintbrush"), .init(label: "Dev", icon: "chevron.left.forwardslash.chevron.right")],
            [.init(label: "Motion", icon: "sparkles"), .init(label: "Research", icon: "magnifyingglass"), .init(label: "Branding", icon: "seal")],
            [.init(label: "3D", icon: "cube")],
        ]),
        SubitemTabBarTab(label: "About", icon: "person", rows: [
            [.init(label: "Story", icon: "text.book.closed"), .init(label: "Skills", icon: "star"), .init(label: "Contact", icon: "envelope")],
            [.init(label: "Resume", icon: "doc"), .init(label: "Awards", icon: "rosette"), .init(label: "Timeline", icon: "calendar")],
        ]),
        SubitemTabBarTab(label: "Journal", icon: "book", rows: [
            [.init(label: "All", icon: "square.grid.2x2"), .init(label: "Design", icon: "paintbrush"), .init(label: "Code", icon: "chevron.left.forwardslash.chevron.right"), .init(label: "Life", icon: "leaf")],
            [.init(label: "Travel", icon: "airplane"), .init(label: "Process", icon: "gearshape"), .init(label: "Insights", icon: "lightbulb")],
            [.init(label: "Tools", icon: "wrench.adjustable")],
        ]),
        SubitemTabBarTab(label: "Login", icon: "rectangle.portrait.and.arrow.right"),
    ]
}

/// A page with a black bar of four tabs docked at the bottom. Pressing a tab makes the bar grow
/// upward on a spring to hold that tab's panel (rows of filter chips, or a sign-in form) whose
/// pieces come in one after the other, while the page behind blurs and pales. Another tab swaps
/// the panel and the height; the open tab, or the page, closes the bar.
struct SubitemTabBar: View {
    var tabs = SubitemTabBarTab.samples
    var emailPlaceholder = "Email"
    var passwordPlaceholder = "Password"
    var signInLabel = "Sign in"
    var forgotLabel = "Forgot password?"
    var background = Color.white
    var barColor = Color(hex: 0x0D0D0D)
    /// The icon and the label of the tab whose panel is open.
    var activeColor = Color.white
    var inactiveColor = Color(hex: 0x8E8E93)
    /// Opens the panels one after the other by itself every `SubitemTabBarMotion.loop` seconds.
    var autoPlay = false
    /// Called with the tab whose panel opened, or nil when the bar closed.
    var onOpen: ((Int?) -> Void)? = nil
    /// Called with a tab and the place of the chip chosen in it, counted along its rows.
    var onChip: ((Int, Int) -> Void)? = nil
    var onSignIn: ((String, String) -> Void)? = nil
    var onForgot: (() -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// The tab whose panel is open; nil while the bar is closed.
    @State private var open: Int? = nil
    /// The chip chosen in each tab, by the tab's place; the first chip until one is pressed.
    @State private var chosen: [Int: Int] = [:]
    @State private var email = ""
    @State private var password = ""
    @State private var concealed = true
    /// Changes on every touch, which restarts the autoPlay loop behind a pause.
    @State private var touches = 0
    @State private var haptic = UISelectionFeedbackGenerator()
    @FocusState private var focus: Field?

    private enum Field {
        case email, password
    }

    var body: some View {
        let veiled = open != nil
        let fade = M.ease(reduceMotion ? M.stillFade : M.veil)

        ZStack {
            background.ignoresSafeArea()

            page
                .padding(.top, Metrics.titleTop)
                .padding(.horizontal, Metrics.margin)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
                .blur(radius: veiled && !reduceMotion ? Metrics.blur : 0)
                .animation(fade, value: veiled)
                .ignoresSafeArea(.keyboard)
                .accessibilityHidden(veiled)

            // The wash over the page, which also closes the bar.
            Color.white
                .opacity(veiled ? Metrics.wash : 0)
                .animation(fade, value: veiled)
                .ignoresSafeArea()
                .contentShape(Rectangle())
                .onTapGesture {
                    touched()
                    show(nil)
                }
                .allowsHitTesting(veiled)
                .accessibilityLabel("Close")

            // The home indicator's inset is ignored, the keyboard's is not: the bar rides above it.
            VStack(spacing: 0) {
                Spacer(minLength: 0)
                bar
            }
            .padding(.bottom, Metrics.barBottom)
            .ignoresSafeArea(.container, edges: .bottom)
        }
        .preferredColorScheme(.light)
        .onAppear { haptic.prepare() }
        .onChange(of: email) { if focus != nil { touched() } }
        .onChange(of: password) { if focus != nil { touched() } }
        .onChange(of: focus) { if focus != nil { touched() } }
        .task(id: LoopKey(on: autoPlay, touches: touches)) { await loop() }
    }

    // MARK: The bar

    private var bar: some View {
        let height = Metrics.tabRow + (open.map { Self.panelHeight(tabs[$0]) } ?? 0)

        return ZStack(alignment: .bottom) {
            // A panel keeps its place above the tabs: the bar's top edge uncovers it.
            ForEach(tabs.indices, id: \.self) { index in
                panel(index)
                    .padding(Metrics.pad)
                    .frame(width: Metrics.barWidth, height: Self.panelHeight(tabs[index]), alignment: .topLeading)
                    .padding(.bottom, Metrics.tabRow)
                    .allowsHitTesting(open == index)
                    .accessibilityHidden(open != index)
            }

            Rectangle()
                .fill(.white.opacity(0.08))
                .frame(height: 1)
                .padding(.horizontal, Metrics.pad)
                .padding(.bottom, Metrics.tabRow)
                .opacity(open != nil ? 1 : 0)
                .animation(M.ease(M.tabFade), value: open != nil)

            tabRow
        }
        .frame(width: Metrics.barWidth, height: height, alignment: .bottom)
        .clipShape(RoundedRectangle(cornerRadius: Metrics.barRadius, style: .continuous))
        .background(
            RoundedRectangle(cornerRadius: Metrics.barRadius, style: .continuous)
                .fill(barColor)
                .shadow(color: .black.opacity(0.25), radius: 12, y: 8)
        )
        // Without motion the height jumps and the contents cross-fade.
        .animation(reduceMotion ? nil : M.spring, value: open)
    }

    private var tabRow: some View {
        HStack(spacing: 0) {
            ForEach(tabs.indices, id: \.self) { index in
                let active = open == index
                Button {
                    touched()
                    press(index, byHand: true)
                } label: {
                    // The grey face and, over it while the panel is open, the white one with a heavier label.
                    ZStack {
                        face(tabs[index], color: inactiveColor, weight: .regular).opacity(active ? 0 : 1)
                        face(tabs[index], color: activeColor, weight: .semibold).opacity(active ? 1 : 0)
                    }
                    .animation(M.ease(M.tabFade), value: active)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .contentShape(Rectangle())
                }
                .buttonStyle(Flat())
                .accessibilityLabel(tabs[index].label)
                .accessibilityAddTraits(active ? [.isSelected] : [])
            }
        }
        .frame(height: Metrics.tabRow)
    }

    private func face(_ tab: SubitemTabBarTab, color: Color, weight: Font.Weight) -> some View {
        VStack(spacing: 4) {
            Image(systemName: tab.icon)
                .font(.system(size: Metrics.tabSymbol, weight: .regular))
                .frame(width: Metrics.tabIcon, height: Metrics.tabIcon)
            Text(tab.label)
                .font(.system(size: 11, weight: weight))
                .lineLimit(1)
                .frame(height: 14)
        }
        .foregroundStyle(color)
    }

    // MARK: The panels

    @ViewBuilder
    private func panel(_ index: Int) -> some View {
        let shown = open == index
        if let rows = tabs[index].rows {
            VStack(alignment: .leading, spacing: Metrics.gap) {
                ForEach(rows.indices, id: \.self) { row in
                    HStack(spacing: Metrics.gap) {
                        ForEach(rows[row].indices, id: \.self) { column in
                            let place = rows[..<row].reduce(0) { $0 + $1.count } + column
                            chip(rows[row][column], tab: index, place: place)
                                .modifier(SubitemTabBarReveal(shown: shown, index: place, still: reduceMotion))
                        }
                    }
                }
            }
        } else {
            form(shown: shown)
        }
    }

    private func chip(_ chip: SubitemTabBarChip, tab: Int, place: Int) -> some View {
        let selected = (chosen[tab] ?? 0) == place
        return Button {
            touched()
            haptic.selectionChanged()
            chosen[tab] = place
            onChip?(tab, place)
        } label: {
            HStack(spacing: 6) {
                Image(systemName: chip.icon)
                    .font(.system(size: Metrics.chipSymbol, weight: .medium))
                    .frame(width: Metrics.chipIcon, height: Metrics.chipIcon)
                Text(chip.label)
                    .font(.system(size: 13, weight: .medium))
                    .lineLimit(1)
            }
            .foregroundStyle(selected ? Palette.chipInkSelected : Palette.chipInk)
            .padding(.horizontal, Metrics.pad)
            .frame(height: Metrics.chipHeight)
            .background(Capsule().fill(selected ? Palette.chipSelected : Palette.chip))
            .animation(M.ease(M.chipFade), value: selected)
        }
        .buttonStyle(Flat())
        .fixedSize()
        .accessibilityAddTraits(selected ? [.isSelected] : [])
    }

    private func form(shown: Bool) -> some View {
        VStack(spacing: Metrics.gap) {
            field(icon: "envelope") {
                TextField("", text: $email, prompt: Text(emailPlaceholder).foregroundStyle(Palette.placeholder))
                    .keyboardType(.emailAddress)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .focused($focus, equals: .email)
            }
            .modifier(SubitemTabBarReveal(shown: shown, index: 0, still: reduceMotion))

            field(icon: "lock") {
                Group {
                    if concealed {
                        SecureField("", text: $password, prompt: Text(passwordPlaceholder).foregroundStyle(Palette.placeholder))
                    } else {
                        TextField("", text: $password, prompt: Text(passwordPlaceholder).foregroundStyle(Palette.placeholder))
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                    }
                }
                .focused($focus, equals: .password)

                Button {
                    touched()
                    concealed.toggle()
                } label: {
                    Image(systemName: concealed ? "eye" : "eye.slash")
                        .font(.system(size: Metrics.fieldSymbol))
                        .foregroundStyle(Palette.placeholder)
                        .frame(width: 28, height: Metrics.field)
                        .contentShape(Rectangle())
                }
                .buttonStyle(Flat())
                .accessibilityLabel(concealed ? "Show password" : "Hide password")
            }
            .modifier(SubitemTabBarReveal(shown: shown, index: 1, still: reduceMotion))

            Button {
                touched()
                haptic.selectionChanged()
                onSignIn?(email, password)
            } label: {
                Text(signInLabel)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(barColor)
                    .frame(maxWidth: .infinity)
                    .frame(height: Metrics.field)
                    .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(activeColor))
            }
            .buttonStyle(Dim())
            .modifier(SubitemTabBarReveal(shown: shown, index: 2, still: reduceMotion))

            Button {
                touched()
                onForgot?()
            } label: {
                Text(forgotLabel)
                    .font(.system(size: 12))
                    .foregroundStyle(Palette.placeholder)
                    .frame(height: Metrics.link)
                    .contentShape(Rectangle())
            }
            .buttonStyle(Flat())
            .modifier(SubitemTabBarReveal(shown: shown, index: 3, still: reduceMotion))
        }
    }

    private func field(icon: String, @ViewBuilder content: () -> some View) -> some View {
        HStack(spacing: 10) {
            Image(systemName: icon)
                .font(.system(size: Metrics.fieldSymbol))
                .foregroundStyle(Palette.placeholder)
                .frame(width: Metrics.fieldIcon, height: Metrics.fieldIcon)
            content()
        }
        .font(.system(size: 14))
        .foregroundStyle(.white)
        .tint(activeColor)
        .padding(.leading, 14)
        .padding(.trailing, 8)
        .frame(height: Metrics.field)
        .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(Palette.field))
    }

    // MARK: The page

    /// The page behind the bar: placeholders only.
    private var page: some View {
        VStack(alignment: .leading, spacing: 16) {
            RoundedRectangle(cornerRadius: 8, style: .continuous)
                .fill(Palette.shape)
                .frame(width: 68, height: 20)
                .padding(.bottom, 4)

            HStack(spacing: 12) {
                Circle().fill(Palette.shape).frame(width: 36, height: 36)
                VStack(alignment: .leading, spacing: 8) {
                    Capsule().fill(Palette.shape).frame(width: 96, height: 8)
                    Capsule().fill(Palette.shape).frame(width: 60, height: 6)
                }
                Spacer(minLength: 0)
                RoundedRectangle(cornerRadius: 5, style: .continuous).fill(Palette.shape).frame(width: 16, height: 16)
            }
            .padding(.horizontal, 14)
            .frame(height: 64)
            .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(Palette.card))

            group([72, 72, 100])
            group([72, 100, 100, 84])
        }
    }

    private func group(_ widths: [Double]) -> some View {
        VStack(spacing: 0) {
            ForEach(widths.indices, id: \.self) { row in
                HStack(spacing: 12) {
                    Circle().fill(Palette.shape).frame(width: 20, height: 20)
                    Capsule().fill(Palette.shape).frame(width: widths[row], height: 8)
                    Spacer(minLength: 0)
                    Capsule().fill(Palette.shape).frame(width: 24, height: 6)
                }
                .frame(height: 44)
                .overlay(alignment: .bottom) {
                    if row < widths.count - 1 { Palette.outline.frame(height: 1) }
                }
            }
        }
        .padding(.horizontal, 14)
        .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).strokeBorder(Palette.outline, lineWidth: 1))
    }

    // MARK: Behaviour

    private static func panelHeight(_ tab: SubitemTabBarTab) -> Double {
        guard let rows = tab.rows else { return Metrics.form }
        return Metrics.pad * 2 + Double(rows.count) * Metrics.chipHeight + Double(rows.count - 1) * Metrics.gap
    }

    private func show(_ next: Int?) {
        guard next != open else { return }
        focus = nil
        open = next
        onOpen?(next)
    }

    /// A press on a tab opens its panel, or closes the bar when that panel is the open one.
    private func press(_ index: Int, byHand: Bool) {
        // The loop that plays by itself is silent.
        if byHand { haptic.selectionChanged() }
        show(index == open ? nil : index)
    }

    private func rest() {
        focus = nil
        show(nil)
        chosen = [:]
        email = ""
        password = ""
        concealed = true
    }

    /// A touch takes over from the autoPlay loop, which starts again from rest a while later.
    private func touched() {
        touches += 1
    }

    private func loop() async {
        guard autoPlay else { return }
        let clock = ContinuousClock()
        if touches > 0 {
            try? await clock.sleep(until: clock.now + .seconds(M.userHold), tolerance: .zero)
            guard !Task.isCancelled else { return }
            rest()
        }
        // Every step is timed from the loop's origin, so a late wake does not push the next one back.
        var origin = clock.now
        while !Task.isCancelled {
            for step in M.steps {
                try? await clock.sleep(until: origin + .seconds(step.at), tolerance: .zero)
                guard !Task.isCancelled else { return }
                press(step.tab, byHand: false)
            }
            origin += .seconds(M.loop)
            try? await clock.sleep(until: origin, tolerance: .zero)
        }
    }

    private struct LoopKey: Equatable {
        let on: Bool
        let touches: Int
    }

    // MARK: Styles

    /// A button that shows its label as it is.
    private struct Flat: ButtonStyle {
        func makeBody(configuration: Configuration) -> some View {
            configuration.label
        }
    }

    private struct Dim: ButtonStyle {
        func makeBody(configuration: Configuration) -> some View {
            configuration.label.opacity(configuration.isPressed ? 0.8 : 1)
        }
    }
}

/// One piece of a panel: it fades in, sharpens and rises when its panel opens, `index` steps after
/// the first, and the pieces leave together, faster.
struct SubitemTabBarReveal: ViewModifier {
    let shown: Bool
    let index: Int
    let still: Bool

    func body(content: Content) -> some View {
        content
            .opacity(shown ? 1 : 0)
            .blur(radius: shown || still ? 0 : SubitemTabBarMotion.revealBlur)
            .offset(y: shown || still ? 0 : SubitemTabBarMotion.rise)
            .animation(animation, value: shown)
    }

    private var animation: Animation {
        typealias M = SubitemTabBarMotion
        if still { return M.ease(M.stillFade) }
        return shown ? M.ease(M.revealIn).delay(M.revealDelay + Double(index) * M.stagger) : M.ease(M.revealOut)
    }
}

/// The timeline of the block, shared by its three stacks.
enum SubitemTabBarMotion {
    /// The period of the autoPlay loop, in seconds, and the tab it presses at each moment of it.
    /// The last press is on the tab that is open, which closes the bar.
    static let loop = 10.0
    static let steps: [(at: Double, tab: Int)] = [(0.8, 0), (2.6, 1), (4.2, 2), (5.8, 3), (7.4, 0), (8.8, 0)]
    /// After a touch the loop waits this long, closes the bar and starts over.
    static let userHold = 3.0

    /// The bar's height follows a spring that overshoots by about a twentieth.
    static let spring = Animation.interpolatingSpring(mass: 1, stiffness: 250, damping: 23)
    // Seconds. The page blurs; a tab and a chip cross-fade to their other look.
    static let veil = 0.3
    static let tabFade = 0.2
    static let chipFade = 0.2
    // A panel's pieces come in one after the other in reading order and leave together, faster.
    static let revealDelay = 0.08
    static let stagger = 0.025
    static let revealIn = 0.3
    static let revealOut = 0.15
    static let rise = 6.0
    static let revealBlur = 6.0
    /// Without motion everything cross-fades in this time and the height jumps.
    static let stillFade = 0.2

    /// A cubic ease-out.
    static func ease(_ seconds: Double) -> Animation {
        .timingCurve(0.33, 1, 0.68, 1, duration: seconds)
    }
}

private typealias M = SubitemTabBarMotion

private enum Palette {
    static let chip = Color(hex: 0x1A1A1C)
    static let chipSelected = Color(hex: 0x2E2E30)
    static let chipInk = Color(hex: 0xA1A1A6)
    static let chipInkSelected = Color.white
    static let field = Color(hex: 0x1A1A1C)
    static let placeholder = Color(hex: 0x6E6E73)
    /// The page's placeholders: shapes, the card under the title, the outline of a group.
    static let shape = Color(hex: 0xE4E4E8)
    static let card = Color(hex: 0xF6F6F8)
    static let outline = Color(hex: 0xEEEEF1)
}

private enum Metrics {
    static let margin = 20.0
    static let titleTop = 14.0
    static let barWidth = 345.0
    static let barRadius = 20.0
    /// The bar's bottom edge above the bottom of the screen, or above the keyboard.
    static let barBottom = 24.0
    static let tabRow = 66.0
    static let tabIcon = 22.0
    static let pad = 12.0
    static let gap = 8.0
    static let chipHeight = 30.0
    static let chipIcon = 13.0
    static let field = 44.0
    static let fieldIcon = 16.0
    static let link = 16.0
    static let form = pad * 2 + field * 3 + gap * 3 + link
    // System symbols draw larger than an icon font at the same size: these fill the boxes above.
    static let tabSymbol = 16.0
    static let chipSymbol = 10.0
    static let fieldSymbol = 13.0
    /// The page behind an open panel: blurred and washed with white.
    static let blur = 12.0
    static let wash = 0.4
}

#Preview {
    SubitemTabBar(autoPlay: true)
}
