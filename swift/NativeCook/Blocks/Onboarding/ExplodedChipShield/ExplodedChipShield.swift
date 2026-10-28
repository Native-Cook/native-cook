import SwiftUI
import UIKit

/*
 Exploded Chip Shield — the security page of the onboarding of Ferrin, an invented password vault.

 Look: a white, light-grey premium screen. An isometric brushed-metal chip with an embossed shield
 and keyhole sits above "Every layer, encrypted.", page dots and a Continue button.
 Hero: the chip explodes into six floating layers (ExplodedChipShieldArt.swift): base plate,
 mirror-rim tray, glowing teal core with a raised disc, frosted glass ring, clear glass sheet printed
 with a circuit, and the lid. They separate with a stagger, hover with a slow bob while the view
 sways a few degrees (the highlights travel), and three callouts draw out of their layers. Then the
 stack collapses, the lid lands last with a small settle bump.

 autoPlay: loop = 7 s (ExplodedChipShieldModel.timeline); frame 7 s equals frame 0.
 Touch: drag up on the chip to open it (the layers follow the finger), sideways to tilt the view;
 release springs to the nearest state, a tap toggles. 2 s after the last touch it springs closed and
 the loop starts again.
 Reduced motion: no loop; the open stack with its callouts shows still, a tap crossfades it closed.
*/
struct ExplodedChipShield: View {
    var autoPlay = false

    typealias M = ExplodedChipShieldModel
    typealias A = ExplodedChipShieldArt

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var driver = ExplodedChipShieldDriver()
    @State private var live = false
    @State private var token = 0
    @State private var grabbing = false

    var body: some View {
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(width: geo.size.width + insets.leading + insets.trailing,
                              height: geo.size.height + insets.top + insets.bottom)
            stage(size: size, insets: insets)
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                .offset(x: -insets.leading, y: -insets.top)
        }
        .background(A.color(M.bgBottom).ignoresSafeArea())
        .preferredColorScheme(.light)
    }

    private func stage(size: CGSize, insets: EdgeInsets) -> some View {
        let w = max(1, size.width), h = max(1, size.height)
        let g = A.Geo(w: w, h: h, top: insets.top, textTop: h - insets.bottom - M.textBlock)
        let auto = autoPlay && !reduceMotion
        let reduced = reduceMotion
        return ZStack(alignment: .topLeading) {
            TimelineView(.animation(paused: !auto && !live)) { timeline in
                let now = timeline.date.timeIntervalSinceReferenceDate
                let frame = driver.frame(at: now, auto: auto, reduced: reduced)
                let shown = reduced ? driver.shownAmount(at: now) : 1
                Canvas { c, _ in
                    if reduced {
                        let closed = M.manual(0, yawOffset: 0, t: 0)
                        A.background(&c, g, closed)
                        var a = c
                        a.opacity = 1 - shown
                        A.stack(&a, g, closed)
                        var b = c
                        b.opacity = shown
                        A.background(&b, g, frame)
                        A.stack(&b, g, frame)
                        A.callouts(&b, g, frame)
                    } else {
                        A.background(&c, g, frame)
                        A.stack(&c, g, frame)
                        A.callouts(&c, g, frame)
                    }
                }
                .frame(width: w, height: h)
            }
            .contentShape(Rectangle())
            .gesture(touch(g: g, auto: auto, reduced: reduced))
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("Ferrin security chip: alloy shell, cipher mesh and key enclave.")
            .accessibilityAddTraits(.isButton)
            .accessibilityHint("Opens or closes the layers.")
            .accessibilityAction {
                let now = Date.timeIntervalSinceReferenceDate
                if reduced { driver.toggleReduced(at: now) } else {
                    driver.begin(at: now, auto: auto)
                    _ = driver.end(vy: 0, tap: true, at: now)
                }
                wake()
            }

            header(w: w)
                .offset(y: insets.top + 10)
            copy(w: w)
                .offset(y: g.textTop)
            footer(w: w)
                .offset(y: h - insets.bottom - 16 - 54 - 34)
        }
        .onChange(of: auto) { driver.reset() }
        .onChange(of: reduced) { driver.reset() }
    }

    // MARK: Chrome

    private func header(w: CGFloat) -> some View {
        HStack(spacing: 8) {
            ZStack {
                ExplodedChipShieldMark()
                    .fill(A.color(M.ink), style: FillStyle(eoFill: true))
            }
            .frame(width: 18, height: 21)
            Text(M.brand)
                .font(.system(size: 17, weight: .semibold))
                .tracking(-0.2)
                .foregroundStyle(A.color(M.ink))
            Spacer()
            Text(M.skip)
                .font(.system(size: 16))
                .foregroundStyle(A.color(M.grey))
        }
        .padding(.horizontal, 24)
        .frame(width: w, height: 32)
    }

    private func copy(w: CGFloat) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(M.eyebrow)
                .font(.system(size: 12, weight: .semibold))
                .tracking(1.4)
                .foregroundStyle(A.color(M.teal))
            Text(M.title)
                .font(.system(size: 34, weight: .bold))
                .tracking(-0.6)
                .lineSpacing(-2)
                .foregroundStyle(A.color(M.ink))
                .padding(.top, 10)
                .fixedSize(horizontal: false, vertical: true)
            Text(M.body)
                .font(.system(size: 15))
                .lineSpacing(3)
                .foregroundStyle(A.color(M.grey))
                .padding(.top, 10)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(.horizontal, 24)
        .frame(width: w, alignment: .leading)
        .allowsHitTesting(false)
    }

    private func footer(w: CGFloat) -> some View {
        VStack(spacing: 28) {
            HStack(spacing: 8) {
                Capsule().fill(A.color(M.dotOff)).frame(width: 6, height: 6)
                Capsule().fill(A.color(M.ink)).frame(width: 20, height: 6)
                Capsule().fill(A.color(M.dotOff)).frame(width: 6, height: 6)
            }
            Button {
                UIImpactFeedbackGenerator(style: .light).impactOccurred()
            } label: {
                Text(M.cta)
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 54)
                    .background(Capsule().fill(A.color(M.ink)))
                    .shadow(color: A.color(0x14171B, 0.18), radius: 12, y: 6)
            }
            .buttonStyle(ExplodedChipShieldPress())
            .padding(.horizontal, 24)
        }
        .frame(width: w)
    }

    // MARK: Touch

    private func wake() {
        live = true
        token += 1
        let mine = token
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: 4_000_000_000)
            if token == mine && !driver.dragging { live = false }
        }
    }

    private func touch(g: A.Geo, auto: Bool, reduced: Bool) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { v in
                let now = Date.timeIntervalSinceReferenceDate
                if reduced { return }
                if !grabbing {
                    guard v.startLocation.y > g.top + 50 && v.startLocation.y < g.textTop else { return }
                    grabbing = true
                    driver.begin(at: now, auto: auto)
                }
                if hypot(v.translation.width, v.translation.height) > 6 {
                    driver.drag(dx: v.translation.width, dy: v.translation.height, at: now)
                }
                wake()
            }
            .onEnded { v in
                let now = Date.timeIntervalSinceReferenceDate
                let moved = hypot(v.translation.width, v.translation.height)
                if reduced {
                    if moved < 10 && v.startLocation.y < g.textTop {
                        driver.toggleReduced(at: now)
                        Self.impact()
                        wake()
                    }
                    return
                }
                guard grabbing else { return }
                grabbing = false
                if driver.end(vy: v.velocity.height, tap: moved < 8, at: now) { Self.impact() }
                wake()
            }
    }

    static func impact() { UIImpactFeedbackGenerator(style: .light).impactOccurred() }
}

/// The small shield-and-keyhole mark of the header (even-odd: the keyhole is cut out).
struct ExplodedChipShieldMark: Shape {
    func path(in rect: CGRect) -> Path {
        var p = ExplodedChipShieldArt.shield(rect.width, rect.height)
        p.addPath(ExplodedChipShieldArt.keyhole(rect.width, rect.height))
        return p.applying(CGAffineTransform(translationX: rect.midX, y: rect.midY))
    }
}

struct ExplodedChipShieldPress: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .animation(.spring(response: 0.3, dampingFraction: 0.7), value: configuration.isPressed)
    }
}
