import SwiftUI
import UIKit

/// A signature pad on cream paper. The finger writes in fountain-pen ink whose width follows its
/// speed, drawn through as smooth curves, glossy while wet and drying to blue-black. Clear sweeps a
/// felt wiper across it, Replay redraws the signature at the speed it was written, and Done shrinks
/// the page onto the rest of the lease and presses a wax seal on it.
struct InkSignaturePad: View {
    /// Signs the name, replays it, seals the page, opens it again and wipes it, over and over.
    var autoPlay = false
    /// Printed under the signature line.
    var signer = "Mara Ellison"
    var role = "Tenant"
    var date = "13 Oct 2026"
    /// The letters pressed into the wax.
    var monogram = "ME"
    /// Called with the ink (x, y, ms, width, stroke per sample) when the page is sealed by hand.
    var onDone: (([Double]) -> Void)? = nil

    fileprivate typealias M = InkSignaturePadMetrics

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.displayScale) private var displayScale
    @State private var model = InkSignaturePadModel()
    @State private var wake = 0
    // Bumped once the pad has come to rest, so the frame clock can stop.
    @State private var rest = 0
    @State private var paper: UIImage?
    @State private var dragging = false

    var body: some View {
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            let l = InkSignaturePadLayout(size: size, insets: insets)
            let _ = configure(l)
            let _ = (wake, rest)
            TimelineView(.animation(minimumInterval: nil, paused: !model.live)) { timeline in
                let _ = model.advance(to: timeline.date)
                screen(l)
            }
            .frame(width: size.width, height: size.height)
            .offset(x: -insets.leading, y: -insets.top)
        }
        .background(M.background.ignoresSafeArea())
        .task(id: wake) {
            // Once nothing moves any more the body is drawn again, and the paused timeline stops.
            while !Task.isCancelled {
                try? await Task.sleep(for: .milliseconds(300))
                if !model.live {
                    rest &+= 1
                    break
                }
            }
        }
        .task {
            paper = await InkSignaturePadPhotos.load(InkSignaturePadPhotos.address(M.padPaper, width: 900))
        }
    }

    private func configure(_ l: InkSignaturePadLayout) {
        model.reduced = reduceMotion
        model.onDone = onDone
        model.thud = { UINotificationFeedbackGenerator().notificationOccurred(.success) }
        model.tap = { UIImpactFeedbackGenerator(style: .light).impactOccurred() }
        model.configure(autoPlay: autoPlay && !reduceMotion, padWidth: l.padW, lineY: l.lineY, signLeft: M.signLeft, signRight: M.signRight, lift: M.lineLift)
    }

    private func poke() {
        wake &+= 1
    }

    private func screen(_ l: InkSignaturePadLayout) -> some View {
        let t = model.clock
        let e = model.seal.value(t)
        let chrome = min(1, max(0, 1 - e * 2.2))
        let sealedK = min(1, max(0, (e - 0.55) / 0.45))
        let fade = model.fade.value(t)
        let scale = 1 - (1 - M.cardScale) * e
        return ZStack(alignment: .topLeading) {
            header("STUDIO LEASE · PAGE 4 OF 4", "Sign to accept")
                .opacity(chrome)
                .offset(x: M.side + 4, y: l.insets.top + 12)
            header("STUDIO LEASE · COMPLETE", "Lease sealed")
                .opacity(sealedK)
                .offset(x: M.side + 4, y: l.insets.top + 12)
                .allowsHitTesting(false)

            ForEach(0..<M.backPages.count, id: \.self) { i in
                let page = M.backPages[i]
                InkSignaturePadBackPage()
                    .frame(width: l.padW, height: l.padH)
                    .rotationEffect(.degrees(page.turn * e))
                    .offset(x: page.x * e, y: page.y * e)
                    .scaleEffect(scale)
                    .offset(y: l.shiftY * e)
                    .opacity(min(1, max(0, e * 3)) * fade)
                    .offset(x: M.side, y: l.padTop)
                    .allowsHitTesting(false)
                    .accessibilityHidden(true)
            }

            pad(l)
                .compositingGroup()
                .scaleEffect(scale)
                .offset(y: l.shiftY * e)
                .opacity(fade)
                .offset(x: M.side, y: l.padTop)

            // The wax seal and the line under the sealed card.
            seal(t)
                .offset(x: l.sealX, y: l.sealY)
                .allowsHitTesting(false)
                .accessibilityHidden(true)
            VStack(spacing: 6) {
                Text("Signed by \(signer) · \(date)")
                    .font(.custom("Baskerville-SemiBoldItalic", size: 17))
                    .foregroundStyle(M.cream)
                Text("Tap the page to sign again")
                    .font(.system(size: 12))
                    .kerning(0.3)
                    .foregroundStyle(M.muted)
            }
            .frame(width: l.size.width)
            .offset(y: l.cardBottom + 40)
            .opacity(sealedK)
            .allowsHitTesting(false)
            if model.sealed {
                Color.clear
                    .contentShape(Rectangle())
                    .frame(width: l.padW * M.cardScale, height: l.padH * M.cardScale)
                    .offset(x: (l.size.width - l.padW * M.cardScale) / 2, y: l.cardCentreY - l.padH * M.cardScale / 2)
                    .onTapGesture {
                        model.touched()
                        model.sealDown()
                        poke()
                    }
                    .accessibilityElement()
                    .accessibilityLabel("Open the page to sign again")
                    .accessibilityAddTraits(.isButton)
            }

            buttons(t)
                .frame(width: l.padW, height: M.buttonHeight)
                .opacity(chrome)
                .offset(x: M.side, y: l.buttonsTop)
                .allowsHitTesting(!model.sealed)
        }
        .frame(width: l.size.width, height: l.size.height, alignment: .topLeading)
        .clipped()
    }

    private func header(_ eyebrow: String, _ title: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(eyebrow)
                .font(.system(size: 11, weight: .semibold))
                .kerning(1.8)
                .foregroundStyle(M.muted)
            Text(title)
                .font(.custom("Baskerville-SemiBold", size: 33))
                .kerning(-0.3)
                .foregroundStyle(M.cream)
        }
    }

    // MARK: - The page

    private func pad(_ l: InkSignaturePadLayout) -> some View {
        let t = model.clock
        let frame = model.frame(width: l.padW, height: l.padH)
        let paper = paper
        return ZStack(alignment: .topLeading) {
            Canvas { context, size in
                let rect = CGRect(origin: .zero, size: size)
                context.fill(Path(rect), with: .color(Color(hex: 0xefe7d6)))
                if let paper {
                    context.draw(Image(uiImage: paper), in: InkSignaturePadPhotos.cover(paper.size, in: rect))
                }
                InkSignaturePadInk.paint(context, frame)
            }
            VStack(alignment: .leading, spacing: 8) {
                Text("ACCEPTANCE")
                    .font(.system(size: 10, weight: .bold))
                    .kerning(2)
                    .foregroundStyle(Color(hex: 0x8f826b))
                Text("I have read the lease for Atelier 3B, 14 Rue Vasse, and take the studio from 1 November 2026 for twelve months.")
                    .font(.custom("Baskerville-SemiBold", size: 16))
                    .lineSpacing(M.bodyLineSpacing)
                    .foregroundStyle(M.print)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .frame(width: max(0, l.padW - 56), alignment: .leading)
            .offset(x: 26, y: 26)
            Text("Sign above the line")
                .font(.custom("Baskerville-SemiBoldItalic", size: 16))
                .foregroundStyle(Color(hex: 0xa39679))
                .frame(width: l.padW)
                .offset(y: l.lineY - 64)
                .opacity(model.hint.value(t))
            signLine(l)
        }
        .frame(width: l.padW, height: l.padH, alignment: .topLeading)
        .clipShape(RoundedRectangle(cornerRadius: M.padRadius, style: .continuous))
        .contentShape(Rectangle())
        .gesture(
            DragGesture(minimumDistance: 0, coordinateSpace: .local)
                .onChanged { value in
                    if !dragging {
                        dragging = true
                        model.begin(x: value.startLocation.x, y: value.startLocation.y)
                        if value.location != value.startLocation {
                            model.move(x: value.location.x, y: value.location.y, speed: hypot(value.velocity.width, value.velocity.height))
                        }
                        poke()
                    } else {
                        model.move(x: value.location.x, y: value.location.y, speed: hypot(value.velocity.width, value.velocity.height))
                    }
                }
                .onEnded { _ in
                    dragging = false
                    model.end()
                    poke()
                }
        )
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Signature pad for \(signer)")
        .accessibilityHint("Draw your signature above the line")
    }

    private func signLine(_ l: InkSignaturePadLayout) -> some View {
        let width = max(0, l.padW - 52)
        return ZStack(alignment: .topLeading) {
            Text("×")
                .font(.custom("Baskerville-SemiBold", size: 24))
                .foregroundStyle(M.rule)
                .offset(y: -30)
            VStack(spacing: 9) {
                Rectangle()
                    .fill(M.rule)
                    .frame(height: 2 / displayScale)
                HStack {
                    Text("\(signer) · \(role)".uppercased())
                    Spacer()
                    Text(date.uppercased())
                }
                .font(.system(size: 10, weight: .semibold))
                .kerning(1.6)
                .foregroundStyle(Color(hex: 0x857862))
            }
        }
        .frame(width: width, alignment: .topLeading)
        .offset(x: 26, y: l.lineY)
    }

    private func seal(_ t: Double) -> some View {
        let s = model.stamp.value(t)
        let scale = model.reduced ? 1 : s <= 1 ? M.stampFrom - (M.stampFrom - 1) * s : 1 - (s - 1) * 1.6
        return InkSignaturePadSeal(monogram: monogram)
            .scaleEffect(scale)
            .rotationEffect(.degrees(-16 + 10 * min(1, s)))
            .opacity(min(1, max(0, s * 1.6)))
    }

    // MARK: - Buttons

    private func buttons(_ t: Double) -> some View {
        let disabled = !model.hasInk
        return HStack(spacing: 10) {
            InkSignaturePadButton(label: "Clear", icon: "eraser", primary: false, disabled: disabled, press: model.pressClear.value(t)) {
                model.touched()
                model.startSweep(user: true)
                poke()
            }
            InkSignaturePadButton(label: "Replay", icon: "arrow.counterclockwise", primary: false, disabled: disabled, press: model.pressReplay.value(t)) {
                model.touched()
                model.startReplay()
                poke()
            }
            InkSignaturePadButton(label: "Done", icon: "checkmark", primary: true, disabled: disabled, press: model.pressDone.value(t)) {
                model.touched()
                model.sealUp(user: true)
                poke()
            }
        }
    }
}

// MARK: - Parts

/// One of the lease's earlier pages, waiting under the signed one.
private struct InkSignaturePadBackPage: View {
    @State private var image: UIImage?

    var body: some View {
        let address = InkSignaturePadPhotos.address(InkSignaturePadMetrics.pagePaper, width: 600)
        Color(hex: 0xf2eee6)
            .overlay {
                if let image = image ?? InkSignaturePadPhotos.cached(address) {
                    Image(uiImage: image)
                        .resizable()
                        .aspectRatio(contentMode: .fill)
                }
            }
            .overlay(Color(.sRGB, red: 40 / 255, green: 30 / 255, blue: 18 / 255, opacity: 0.12))
            .clipShape(RoundedRectangle(cornerRadius: InkSignaturePadMetrics.padRadius, style: .continuous))
            .task {
                guard InkSignaturePadPhotos.cached(address) == nil else { return }
                image = await InkSignaturePadPhotos.load(address)
            }
    }
}

/// A pill button that sinks while held; autoPlay "presses" it through `press`.
private struct InkSignaturePadButton: View {
    let label: String
    let icon: String
    let primary: Bool
    let disabled: Bool
    let press: Double
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            EmptyView()
        }
        .buttonStyle(InkSignaturePadButtonStyle(label: label, icon: icon, primary: primary, disabled: disabled, press: press))
        .disabled(disabled)
        .accessibilityLabel(label)
    }
}

private struct InkSignaturePadButtonStyle: ButtonStyle {
    let label: String
    let icon: String
    let primary: Bool
    let disabled: Bool
    let press: Double

    func makeBody(configuration: Configuration) -> some View {
        let tint = primary ? Color(hex: 0x1b1610) : InkSignaturePadMetrics.cream
        let shape = Capsule()
        HStack(spacing: 7) {
            Image(systemName: icon)
                .resizable()
                .fontWeight(.semibold)
                .scaledToFit()
                .frame(width: 15, height: 15)
                .accessibilityHidden(true)
            Text(label)
                .font(.system(size: 15, weight: .semibold))
        }
        .foregroundStyle(tint)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background {
            if primary {
                shape.fill(InkSignaturePadMetrics.cream)
            } else {
                shape.fill(Color(.sRGB, red: 239 / 255, green: 230 / 255, blue: 213 / 255, opacity: 0.07))
                    .overlay(shape.strokeBorder(Color(.sRGB, red: 239 / 255, green: 230 / 255, blue: 213 / 255, opacity: 0.16), lineWidth: 2 / 3))
            }
        }
        .contentShape(shape)
        .scaleEffect(1 - 0.05 * max(press, configuration.isPressed ? 1 : 0))
        .opacity(disabled ? 0.38 : 1)
    }
}

// MARK: - Layout

enum InkSignaturePadMetrics {
    static let padPaper = "photo-1629968417850-3505f5180761"
    static let pagePaper = "photo-1601662528567-526cd06f6582"

    static let background = Color(hex: 0x15120e)
    static let cream = Color(hex: 0xefe6d5)
    static let muted = Color(hex: 0x8c8170)
    static let print = Color(hex: 0x463d31)
    static let rule = Color(hex: 0x9c8f78)

    static let side = 18.0
    static let padTop = 96.0
    static let padRadius = 22.0
    static let buttonHeight = 54.0
    static let buttonsGap = 22.0
    // The signature line sits this share of the pad's height down, the signature's baseline lineLift above it.
    static let lineAt = 0.7
    static let lineLift = 5.0
    // The signature runs from signLeft (clear of the × mark) to signRight pt from the pad's right edge.
    static let signLeft = 50.0
    static let signRight = 26.0
    static let cardScale = 0.56
    static let stampFrom = 1.75
    // The two pages behind the signed one, as offsets (pt, before scaling) and turns (°).
    static let backPages: [(x: Double, y: Double, turn: Double)] = [(-26, -18, -5.5), (24, -8, 4)]

    /// The printed text's lines are 22 pt apart.
    static let bodyLineSpacing: Double = {
        let font = UIFont(name: "Baskerville-SemiBold", size: 16) ?? .systemFont(ofSize: 16)
        return max(0, 22 - font.lineHeight)
    }()
}

struct InkSignaturePadLayout {
    let size: CGSize
    let insets: EdgeInsets
    let padW: Double
    let padTop: Double
    let buttonsTop: Double
    let padH: Double
    let lineY: Double
    let cardCentreY: Double
    let shiftY: Double
    let cardBottom: Double
    let sealX: Double
    let sealY: Double

    init(size: CGSize, insets: EdgeInsets) {
        typealias M = InkSignaturePadMetrics
        self.size = size
        self.insets = insets
        // Never negative: the first layout pass can report a zero size.
        padW = max(0, size.width - M.side * 2)
        padTop = insets.top + M.padTop
        buttonsTop = size.height - insets.bottom - 14 - M.buttonHeight
        padH = max(300, buttonsTop - M.buttonsGap - padTop)
        lineY = (padH * M.lineAt).rounded()
        // Where the sealed card sits, and the seal on it, over its lower right corner.
        cardCentreY = insets.top + (size.height - insets.top - insets.bottom) * 0.45
        shiftY = cardCentreY - (padTop + padH / 2)
        let cardRight = size.width / 2 + padW * M.cardScale / 2
        cardBottom = cardCentreY + padH * M.cardScale / 2
        sealX = cardRight - InkSignaturePadSeal.size / 2 - 22
        sealY = cardBottom - InkSignaturePadSeal.size / 2 - 24
    }
}
