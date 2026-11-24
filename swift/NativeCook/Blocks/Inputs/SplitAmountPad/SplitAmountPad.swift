// Split Amount Pad — "Split the bill" in an invented payments app, Kindly.
//
// A custom number pad types an amount whose digits are tiles: each press drops one in on a spring,
// the amount re-centres, a thousands comma pops in, and the four shares under the avatars roll to
// their new values. Backspace flicks the last digit away (held, it clears), a key that would pass
// $2,500 shakes the amount instead, and tapping a friend takes them out of the split.
//
// autoPlay: loop = 9 s (the script in SplitAmountPadMotion). A ghost finger types 1 2 8 . 4 0, the
// shares settle on $32.10, Tobi is tapped out ($42.80 each), backspace is held until the amount
// clears, Tobi comes back, rest. Frame 9 s equals frame 0. A real touch takes over at once and keeps
// what was typed; 2 s after the last touch the amount clears and the loop restarts at t = 0.
// Reduced motion: the finished state ($128.40, $32.10 each) with no loop; keys still work, without
// the springs.
//
// Nothing typed is SwiftUI state: the model holds closed-form values and the timeline reads them.

import CoreText
import SwiftUI
import UIKit

struct SplitAmountPad: View {
    static let loop: Double = SplitAmountPadMotion.loop

    private let autoPlay: Bool
    @Environment(\.accessibilityReduceMotion) private var reduced
    @State private var model = SplitAmountPadModel()
    /// without the loop, the timeline only runs while a touch's motion plays out
    @State private var busy = false
    @State private var quiet: Task<Void, Never>?

    init(autoPlay: Bool = true) {
        self.autoPlay = autoPlay
    }

    private typealias M = SplitAmountPadMotion
    private typealias L = SplitAmountPadLayout

    // colours
    private static let paper = [Color(hex: 0xF7F3EB), Color(hex: 0xF2EDE3), Color(hex: 0xEAE4D7)]
    private static let ink = Color(hex: 0x1E1B16)
    private static let mute = Color(hex: 0x7B7568)
    private static let faint = Color(hex: 0xA8A294)
    private static let accent = Color(hex: 0x1F6B4E)
    private static let accentInk = Color(hex: 0xF4FBF6)
    private static let key = Color(hex: 0xFFFEFB)
    private static func shade(_ a: Double) -> Color { .rgba(40, 32, 20, a) }

    private struct Person {
        let name: String
        let initials: String
        let tint: Color
        let ink: Color
    }
    private static let people = [
        Person(name: "You", initials: "AK", tint: Color(hex: 0xDCE8DD), ink: Color(hex: 0x2F6B4A)),
        Person(name: "Maren", initials: "MH", tint: Color(hex: 0xF6DFD2), ink: Color(hex: 0xA0502C)),
        Person(name: "Tobi", initials: "TO", tint: Color(hex: 0xD9E4F1), ink: Color(hex: 0x355C88)),
        Person(name: "Ines", initials: "IV", tint: Color(hex: 0xF2E6BF), ink: Color(hex: 0x7A5E14)),
    ]
    private static let keyLabels = ["1", "2", "3", "4", "5", "6", "7", "8", "9", ".", "0", ""]
    /// the code each pad cell sends (10 the dot, 11 backspace)
    private static let keyCodes = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 0, 11]
    private static let glyphs = ["0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "."]
    private static let dollarScale = 0.56
    private static let ghost = 44.0
    /// room above and below the amount's line for a digit dropping in or flicked away
    private static let fly = 44.0

    var body: some View {
        GeometryReader { proxy in
            let top = proxy.safeAreaInsets.top, bottom = proxy.safeAreaInsets.bottom
            let l = L(width: proxy.size.width, height: proxy.size.height + top + bottom, top: top, bottom: bottom)
            let fonts = Fonts(amount: l.amount)
            let auto = autoPlay && !reduced
            TimelineView(.animation(paused: !auto && !busy)) { timeline in
                let _ = model.configure(layout: l, metrics: fonts.metrics, auto: auto, reduced: reduced)
                let _ = model.advance(to: timeline.date)
                screen(l, fonts)
            }
            .frame(width: l.width, height: l.height, alignment: .topLeading)
            .offset(y: -top)
        }
        .preferredColorScheme(.light)
    }

    // MARK: fonts

    private struct Fonts {
        let digit: UIFont
        let dollar: UIFont
        let metrics: SplitAmountPadMetrics

        init(amount: Double) {
            digit = Self.rounded(amount, .bold, tabular: true)
            dollar = Self.rounded(amount * SplitAmountPad.dollarScale, .semibold, tabular: false)
            metrics = SplitAmountPadMetrics(
                digit: Self.width("0", digit), dot: Self.width(".", digit), comma: Self.width(",", digit), dollar: Self.width("$", dollar))
        }

        static func rounded(_ size: Double, _ weight: UIFont.Weight, tabular: Bool) -> UIFont {
            var descriptor = UIFont.systemFont(ofSize: size, weight: weight).fontDescriptor
            if let round = descriptor.withDesign(.rounded) { descriptor = round }
            if tabular {
                descriptor = descriptor.addingAttributes([
                    .featureSettings: [[UIFontDescriptor.FeatureKey.type: kNumberSpacingType, UIFontDescriptor.FeatureKey.selector: kMonospacedNumbersSelector]],
                ])
            }
            return UIFont(descriptor: descriptor, size: size)
        }

        static func width(_ text: String, _ font: UIFont) -> Double {
            Double((text as NSString).size(withAttributes: [.font: font]).width)
        }
    }

    // MARK: the screen

    private func screen(_ l: L, _ fonts: Fonts) -> some View {
        let side = L.side
        return ZStack(alignment: .topLeading) {
            LinearGradient(colors: Self.paper, startPoint: .top, endPoint: .bottom)
            SplitAmountPadGrain.image
                .resizable(resizingMode: .tile)
                .interpolation(.none)
                .allowsHitTesting(false)
                .accessibilityHidden(true)

            nav(l)

            amountBlock(l, fonts)
                .frame(width: l.width, height: l.betweenY - (l.top + 44))
                .offset(y: l.top + 44)

            Text("Split evenly between")
                .font(.system(size: 13, weight: .medium))
                .foregroundStyle(Self.mute)
                .frame(height: L.label)
                .offset(x: side, y: l.betweenY)

            ForEach(0..<4, id: \.self) { i in
                friend(i, l)
                    .offset(x: side + Double(i) * l.column, y: l.seatsY)
            }

            ForEach(0..<12, id: \.self) { cell in
                padKey(cell, l)
                    .offset(x: side + Double(cell % 3) * (l.keyW + l.gap), y: l.padY + Double(cell / 3) * (l.keyH + l.gap))
            }

            cta(l)
                .offset(x: side, y: l.ctaY)

            Circle()
                .fill(Color.rgba(30, 27, 22, 0.14))
                .overlay(Circle().strokeBorder(Color.rgba(30, 27, 22, 0.26), lineWidth: 1))
                .frame(width: Self.ghost, height: Self.ghost)
                .scaleEffect(1 - 0.14 * model.autoPress)
                .opacity(model.ghostAlpha)
                .offset(x: model.ghostX - Self.ghost / 2, y: model.ghostY - Self.ghost / 2)
                .allowsHitTesting(false)
                .accessibilityHidden(true)
        }
        .frame(width: l.width, height: l.height, alignment: .topLeading)
        .background(Self.paper[1])
    }

    private func nav(_ l: L) -> some View {
        ZStack {
            Text("Split the bill")
                .font(.system(size: 17, weight: .semibold))
                .tracking(-0.2)
                .foregroundStyle(Self.ink)
                .accessibilityAddTraits(.isHeader)
            HStack {
                Circle()
                    .fill(Self.key)
                    .shadow(color: Self.shade(0.14), radius: 0.75, y: 1)
                    .shadow(color: Self.shade(0.07), radius: 5, y: 4)
                    .overlay(SplitAmountPadInset(shape: Circle(), dy: 1).fill(Color.white.opacity(0.9)))
                    .overlay(
                        Path { p in
                            p.move(to: CGPoint(x: 11, y: 3.5))
                            p.addLine(to: CGPoint(x: 5.5, y: 9))
                            p.addLine(to: CGPoint(x: 11, y: 14.5))
                        }
                        .stroke(Self.ink, style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round))
                        .frame(width: 18, height: 18)
                    )
                    .frame(width: 36, height: 36)
                    .accessibilityLabel("Back")
                    .accessibilityAddTraits(.isButton)
                Spacer(minLength: 0)
            }
            .padding(.horizontal, L.side)
        }
        .frame(width: l.width, height: 44)
        .offset(y: l.top)
    }

    private func amountBlock(_ l: L, _ fonts: Fonts) -> some View {
        let now = model.now
        let flash = model.limit.value(at: now)
        return VStack(spacing: 0) {
            Text("Dinner at Marlow's · Friday")
                .font(.system(size: 14, weight: .medium))
                .foregroundStyle(Self.mute)
                .frame(height: 17)
            amount(l, fonts)
                .frame(width: l.width, height: l.line + Self.fly * 2)
                .padding(.vertical, -Self.fly)
                .offset(x: model.shake.value(at: now))
                .padding(.top, l.compact ? 4 : 8)
                .accessibilityHidden(true)
            Text("Up to $2,500 a bill")
                .font(.system(size: 13, weight: .medium).monospacedDigit())
                .foregroundStyle(Self.ink)
                .opacity(0.62 + 0.38 * flash)
                .scaleEffect(1 + 0.04 * flash)
                .frame(height: L.label)
                .padding(.top, l.compact ? 2 : 6)
        }
    }

    /// the amount: the dollar sign, the placeholder zero, the comma and twelve pooled digit tiles
    private func amount(_ l: L, _ fonts: Fonts) -> some View {
        let m = fonts.metrics
        return Canvas { context, size in
            let now = model.now
            let mid = size.width / 2
            let y = size.height / 2
            let digit = Font(fonts.digit as CTFont)

            context.draw(
                Text("$").font(Font(fonts.dollar as CTFont)).foregroundStyle(Self.mute),
                at: CGPoint(x: mid + model.dollarX.value(at: now), y: y - l.amount * 0.14), anchor: .leading)

            let empty = model.empty.value(at: now)
            if empty > 0.001 {
                var zero = context
                zero.opacity = empty * 0.24
                zero.draw(Text("0").font(digit).foregroundStyle(Self.ink), at: CGPoint(x: mid + (m.dollar + SplitAmountPadModel.dollarGap) / 2, y: y))
            }

            let comma = model.commaOn.value(at: now)
            if comma > 0.001 {
                var c = context
                c.opacity = min(1, comma)
                c.translateBy(x: mid + model.commaX.value(at: now), y: y + 6 * (1 - comma))
                c.scaleBy(x: 0.6 + 0.4 * comma, y: 0.6 + 0.4 * comma)
                c.draw(Text(",").font(digit).foregroundStyle(Self.ink), at: .zero)
            }

            for tile in model.tiles where tile.state != .hidden {
                let e = tile.enter.value(at: now)
                let leave = tile.leave.value(at: now)
                let alpha = min(1, max(0, e * 3)) * (1 - leave * leave)
                guard alpha > 0.001 else { continue }
                var c = context
                c.opacity = alpha
                c.translateBy(
                    x: mid + tile.x.value(at: now) + M.flickX * leave,
                    y: y + M.dropRise * (1 - e) + M.flickUp * leave + M.flickFall * leave * leave)
                c.rotate(by: .degrees(tile.turn * (1 - e) + M.flickTurn * leave))
                let scale = (M.dropScale + (1 - M.dropScale) * e) * (1 - M.flickShrink * leave)
                c.scaleBy(x: scale, y: scale)
                c.draw(Text(Self.glyphs[tile.glyph]).font(digit).foregroundStyle(Self.ink), at: .zero)
            }
        }
    }

    // MARK: the people

    private func friend(_ i: Int, _ l: L) -> some View {
        let now = model.now
        let person = Self.people[i]
        let on = model.friendOn[i].value(at: now)
        let press = max(model.friendPress[i].value(at: now), model.autoFriend == i ? model.autoPress : 0)
        let seated = min(1, max(0, on))
        return VStack(spacing: 0) {
            Circle()
                .fill(person.tint)
                .shadow(color: Self.shade(0.14), radius: 1, y: 1)
                .shadow(color: Self.shade(0.08), radius: 7, y: 6)
                .overlay(SplitAmountPadInset(shape: Circle(), dy: 1).fill(Color.white.opacity(0.55)))
                .overlay(
                    Text(person.initials)
                        .font(.system(size: l.disc * 0.34, weight: .bold, design: .rounded))
                        .tracking(0.2)
                        .foregroundStyle(person.ink)
                )
                .overlay(alignment: .bottomTrailing) {
                    if i > 0 {
                        Circle()
                            .fill(Self.accent)
                            .overlay(Circle().strokeBorder(Self.paper[1], lineWidth: 2))
                            .overlay(
                                Path { p in
                                    p.move(to: CGPoint(x: 1.8, y: 5.3))
                                    p.addLine(to: CGPoint(x: 4.1, y: 7.5))
                                    p.addLine(to: CGPoint(x: 8.3, y: 2.8))
                                }
                                .stroke(Self.accentInk, style: StrokeStyle(lineWidth: 1.7, lineCap: .round, lineJoin: .round))
                                .frame(width: 10, height: 10)
                            )
                            .frame(width: 20, height: 20)
                            .scaleEffect(max(0, on))
                            .opacity(min(1, max(0, on * 2)))
                            .offset(x: 3, y: 3)
                    }
                }
                .frame(width: l.disc, height: l.disc)
                .compositingGroup()
                .scaleEffect((0.9 + 0.1 * on) * (1 - 0.06 * press))
                .opacity(0.4 + 0.6 * seated)
            Text(person.name)
                .font(.system(size: 13, weight: .medium))
                .foregroundStyle(Self.ink)
                .lineLimit(1)
                .frame(height: L.label)
                .padding(.top, 8)
            ZStack {
                share(i, l).opacity(seated)
                Text("Sits out")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(Self.faint)
                    .opacity(1 - seated)
                    .accessibilityHidden(true) // the person's value below says it, only when true
            }
            .frame(width: l.column, height: L.shareLine)
            .padding(.top, 2)
        }
        .frame(width: l.column)
        .contentShape(Rectangle())
        .gesture(
            DragGesture(minimumDistance: 0)
                .onChanged { _ in
                    touched()
                    model.touchDown(friend: i, at: .now)
                }
                .onEnded { _ in
                    touched()
                    model.touchUp(friend: i, at: .now)
                }
        )
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(person.name)
        .accessibilityValue(on > 0.5 ? "In the split" : "Sits out")
        .accessibilityAddTraits(.isButton)
    }

    /// a share: six digit columns ("$0,000.00" at most), each a strip of 0…9 rolled to its digit;
    /// leading columns fade out and the group re-centres
    private func share(_ friend: Int, _ l: L) -> some View {
        Canvas { context, size in
            let now = model.now
            let dw = SplitAmountPadModel.shareDigit, mw = SplitAmountPadModel.shareMark
            let line = L.shareLine
            let font = Font.system(size: 15, weight: .semibold)
            let digits = font.monospacedDigit()
            let hidden = model.hidden[friend].value(at: now)
            let y = line / 2
            var x = (size.width - (dw * 7 + mw * 2)) / 2 - hidden / 2

            func mark(_ text: String, _ width: Double, _ shift: Double = 0, _ alpha: Double = 1) {
                if alpha > 0.001 {
                    var c = context
                    c.opacity = alpha
                    c.draw(Text(text).font(font).foregroundStyle(Self.ink), at: CGPoint(x: x + shift + width / 2, y: y))
                }
                x += width
            }
            func column(_ place: Int) {
                let alpha = min(1, max(0, model.shown[friend][place].value(at: now)))
                if alpha > 0.001 {
                    let d = model.digit[friend][place].value(at: now)
                    var c = context
                    c.opacity = alpha
                    let base = Int(d.rounded(.down))
                    for g in min(9, max(0, base - 1))...min(9, max(0, base + 1)) {
                        let dy = (Double(g) - d) * line
                        guard abs(dy) < line else { continue }
                        c.draw(Text(Self.glyphs[g]).font(digits).foregroundStyle(Self.ink), at: CGPoint(x: x + dw / 2, y: y + dy))
                    }
                }
                x += dw
            }

            mark("$", dw, hidden)
            column(5)
            mark(",", mw, 0, model.thousands[friend].value(at: now))
            column(4)
            column(3)
            column(2)
            mark(".", mw)
            column(1)
            column(0)
        }
        .frame(width: l.column, height: L.shareLine)
    }

    // MARK: the pad

    private func padKey(_ cell: Int, _ l: L) -> some View {
        let code = Self.keyCodes[cell]
        let p = max(model.keyPress[cell].value(at: model.now), model.autoKey == cell ? model.autoPress : 0)
        let shape = RoundedRectangle(cornerRadius: 18, style: .continuous)
        return ZStack {
            // lifted, the key throws a soft shadow; pressed, only the tight contact shadow is left
            shape.fill(Self.key)
                .shadow(color: Self.shade(0.10), radius: 8, y: 7)
                .opacity(1 - 0.8 * p)
                .scaleEffect(1 - 0.03 * p)
            ZStack {
                shape.fill(Self.key)
                    .shadow(color: Self.shade(0.18), radius: 0.75, y: 1)
                SplitAmountPadInset(shape: shape, dy: 1).fill(Color.white.opacity(0.95))
                SplitAmountPadInset(shape: shape, dy: -1).fill(Self.shade(0.05))
                if code == M.keyBack {
                    SplitAmountPadBackspace()
                        .stroke(Self.ink, style: StrokeStyle(lineWidth: 1.8, lineCap: .round, lineJoin: .round))
                        .frame(width: 30, height: 30)
                } else {
                    Text(Self.keyLabels[cell])
                        .font(.system(size: 26, weight: .medium, design: .rounded).monospacedDigit())
                        .foregroundStyle(Self.ink)
                }
                shape.fill(Self.shade(0.07)).opacity(p)
            }
            .scaleEffect(x: 1 + 0.012 * p, y: 1 - 0.05 * p)
            .offset(y: 2 * p)
        }
        .frame(width: l.keyW, height: l.keyH)
        .contentShape(Rectangle())
        .gesture(
            DragGesture(minimumDistance: 0)
                .onChanged { _ in
                    touched()
                    model.touchDown(cell: cell, code: code, at: .now)
                }
                .onEnded { _ in
                    touched()
                    model.touchUp(cell: cell, code: code, at: .now)
                }
        )
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(code == M.keyBack ? "Delete" : code == M.keyDot ? "Decimal point" : Self.keyLabels[cell])
        .accessibilityAddTraits(.isButton)
    }

    private func cta(_ l: L) -> some View {
        let empty = model.empty.value(at: model.now)
        return Capsule()
            .fill(Self.accent)
            .shadow(color: Color.rgba(18, 60, 44, 0.30), radius: 1, y: 1)
            .shadow(color: Color.rgba(18, 60, 44, 0.18), radius: 9, y: 8)
            .overlay(SplitAmountPadInset(shape: Capsule(), dy: 1).fill(Color.white.opacity(0.18)))
            .overlay(
                Text("Send requests")
                    .font(.system(size: 17, weight: .semibold))
                    .tracking(-0.2)
                    .foregroundStyle(Self.accentInk)
            )
            .frame(width: l.width - L.side * 2, height: l.cta)
            // one layer, so the shadow never shows through the dimmed button
            .compositingGroup()
            .opacity(0.42 + 0.58 * (1 - empty))
            .accessibilityAddTraits(.isButton)
    }

    /// keep the timeline running while a touch's motion plays out (it always runs in autoPlay)
    private func touched() {
        guard !(autoPlay && !reduced) else { return }
        if !busy { busy = true }
        quiet?.cancel()
        quiet = Task { @MainActor in
            try? await Task.sleep(for: .seconds(2.5))
            if !Task.isCancelled { busy = false }
        }
    }
}

/// The one-point lip of an inset highlight: the shape minus itself moved by `dy`.
private struct SplitAmountPadInset<S: Shape>: Shape {
    let shape: S
    let dy: Double

    func path(in rect: CGRect) -> Path {
        let whole = shape.path(in: rect)
        return whole.subtracting(whole.offsetBy(dx: 0, dy: dy))
    }
}

/// The backspace key's mark, 30 × 30.
private struct SplitAmountPadBackspace: Shape {
    func path(in rect: CGRect) -> Path {
        var p = Path()
        // M11.2 7.5h11.3a3 3 0 0 1 3 3v9a3 3 0 0 1-3 3H11.2a3 3 0 0 1-2.3-1.1L4 15l4.9-6.4a3 3 0 0 1 2.3-1.1Z
        p.move(to: CGPoint(x: 11.2, y: 7.5))
        p.addLine(to: CGPoint(x: 22.5, y: 7.5))
        p.addArc(center: CGPoint(x: 22.5, y: 10.5), radius: 3, startAngle: .degrees(-90), endAngle: .degrees(0), clockwise: false)
        p.addLine(to: CGPoint(x: 25.5, y: 19.5))
        p.addArc(center: CGPoint(x: 22.5, y: 19.5), radius: 3, startAngle: .degrees(0), endAngle: .degrees(90), clockwise: false)
        p.addLine(to: CGPoint(x: 11.2, y: 22.5))
        p.addQuadCurve(to: CGPoint(x: 8.9, y: 21.4), control: CGPoint(x: 9.8, y: 22.5))
        p.addLine(to: CGPoint(x: 4, y: 15))
        p.addLine(to: CGPoint(x: 8.9, y: 8.6))
        p.addQuadCurve(to: CGPoint(x: 11.2, y: 7.5), control: CGPoint(x: 9.8, y: 7.5))
        p.closeSubpath()
        // m14 12 5.6 6M19.6 12 14 18
        p.move(to: CGPoint(x: 14, y: 12))
        p.addLine(to: CGPoint(x: 19.6, y: 18))
        p.move(to: CGPoint(x: 19.6, y: 12))
        p.addLine(to: CGPoint(x: 14, y: 18))
        return p
    }
}

/// Paper grain over the page: neutral warm specks at up to 5 % opacity in half-point cells, made once.
private enum SplitAmountPadGrain {
    static let image: Image = {
        let side = 256
        var pixels = [UInt8](repeating: 0, count: side * side * 4)
        var seed: UInt32 = 0x9E37_79B9
        for i in 0..<(side * side) {
            seed = seed &* 1_664_525 &+ 1_013_904_223
            let n = Double(seed >> 8) / Double(1 << 24)
            let a = n * n * 0.05
            // premultiplied
            pixels[i * 4] = UInt8((0.16 * a * 255).rounded())
            pixels[i * 4 + 1] = UInt8((0.13 * a * 255).rounded())
            pixels[i * 4 + 2] = UInt8((0.08 * a * 255).rounded())
            pixels[i * 4 + 3] = UInt8((a * 255).rounded())
        }
        let info = CGBitmapInfo(rawValue: CGImageAlphaInfo.premultipliedLast.rawValue)
        guard let provider = CGDataProvider(data: Data(pixels) as CFData),
              let cg = CGImage(
                width: side, height: side, bitsPerComponent: 8, bitsPerPixel: 32, bytesPerRow: side * 4,
                space: CGColorSpace(name: CGColorSpace.sRGB) ?? CGColorSpaceCreateDeviceRGB(), bitmapInfo: info,
                provider: provider, decode: nil, shouldInterpolate: false, intent: .defaultIntent)
        else { return Image(systemName: "square") }
        // two pixels a point: half-point cells
        return Image(decorative: cg, scale: 2)
    }()
}
