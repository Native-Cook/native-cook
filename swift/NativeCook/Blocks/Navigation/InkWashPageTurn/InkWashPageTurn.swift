// Ink Wash Page Turn — a poetry reader whose pages turn by ink bleeding through wet paper.
//
// Inkwell shows one short poem to a page. The three pages are baked once (InkWashPageTurnPages) and
// one Metal shader draws the turn: from the point the finger touched, a noisy, fibrous front spreads
// across the paper, dark with ink at its edge, and the next page shows through behind it. A swipe
// scrubs the turn, a release completes it or lets it draw back, and a tap turns the page by itself.
//
// autoPlay: loop = 10 s. A ghost finger swipes page 1 to 2 from the lower right, 2 to 3 from the upper
// left, then taps the middle and page 1 blooms back; frame 10 s equals frame 0. A touch takes over at
// once; 2 s after the last touch the reader bleeds back to page 1 and the loop restarts.
// Reduced motion: pages change at once, with no bleed and no loop.

import SwiftUI

struct InkWashPageTurn: View {
    static let loop: Double = InkWashPageTurnMotion.loop

    private let autoPlay: Bool
    @Environment(\.accessibilityReduceMotion) private var reduced
    @State private var reader = InkWashPageTurnReader()
    @State private var touching = false
    @State private var live = true
    /// redraws the labels after a page change while nothing is animating
    @State private var revision = 0
    @GestureState private var held = false

    init(autoPlay: Bool = true) {
        self.autoPlay = autoPlay
    }

    private static let paper = Color(hex: 0xF3ECDD)
    private static let text = Color(hex: 0x26231E)
    private static let mute = Color(hex: 0x8C8576)
    private static let ghost = 46.0
    private static let dot = 6.0
    private static let pitch = 16.0
    private static let pill = 18.0

    var body: some View {
        GeometryReader { proxy in
            let top = proxy.safeAreaInsets.top, bottom = proxy.safeAreaInsets.bottom
            let w = proxy.size.width, h = proxy.size.height + top + bottom
            page(w, h, top, bottom)
                .frame(width: w, height: h, alignment: .topLeading)
                .clipped()
                .contentShape(Rectangle())
                .gesture(touch)
                .offset(y: -top)
        }
        .background(Self.paper.ignoresSafeArea())
        .preferredColorScheme(.light)
        .onChange(of: held) { _, now in
            // a touch the system took away never reaches onEnded
            if !now, touching {
                touching = false
                reader.touchCancelled()
                revision += 1
            }
        }
        .onAppear {
            reader.onLive = { value in live = value }
        }
    }

    private var touch: some Gesture {
        DragGesture(minimumDistance: 0)
            .updating($held) { _, state, _ in state = true }
            .onChanged { value in
                if !touching {
                    touching = true
                    reader.touchBegan(x: value.startLocation.x, y: value.startLocation.y)
                }
                reader.touchMoved(dx: value.translation.width, dy: value.translation.height)
                if reduced { revision += 1 }
            }
            .onEnded { value in
                guard touching else { return }
                touching = false
                reader.touchEnded(x: value.location.x, dx: value.translation.width, velocityX: value.velocity.width)
                revision += 1
            }
    }

    private func page(_ w: Double, _ h: Double, _ top: Double, _ bottom: Double) -> some View {
        let compact = h < 740
        let _ = reader.auto = autoPlay && !reduced
        let _ = reader.reduced = reduced
        let _ = revision
        return ZStack(alignment: .topLeading) {
            InkWashPageTurnPaper(reader: reader, layout: .init(width: w, height: h, top: top, bottom: bottom))
                .frame(width: w, height: h)
                .accessibilityLabel("A poem on warm paper")

            TimelineView(.animation(paused: reduced || !live)) { _ in
                ZStack(alignment: .topLeading) {
                    header
                        .frame(width: w - 64, alignment: .leading)
                        .offset(x: 32, y: top + (compact ? 8 : 14))
                    foot
                        .frame(width: w, height: h - max(bottom, 12) - (compact ? 10 : 18), alignment: .bottom)
                    finger
                        .scaleEffect(1 - 0.16 * reader.ghostP)
                        .opacity(reader.ghostAlpha)
                        .offset(x: reader.ghostPX - Self.ghost / 2, y: reader.ghostPY - Self.ghost / 2)
                        .accessibilityHidden(true)
                }
                .frame(width: w, height: h, alignment: .topLeading)
            }
        }
        .allowsHitTesting(false)
    }

    private var header: some View {
        HStack(alignment: .firstTextBaseline, spacing: 0) {
            Text("Inkwell")
                .font(.system(size: 15, weight: .semibold))
                .tracking(0.1)
                .foregroundStyle(Self.text)
            Text("Small Hours")
                .font(.system(size: 15))
                .foregroundStyle(Self.mute)
                .padding(.leading, 8)
            Spacer(minLength: 0)
            ZStack(alignment: .topTrailing) {
                ForEach(0..<InkWashPageTurnMotion.pages, id: \.self) { index in
                    Text("\(index + 1) of 3")
                        .font(.system(size: 13, weight: .medium).monospacedDigit())
                        .foregroundStyle(Self.mute)
                        .opacity(reader.weight(index))
                        .accessibilityHidden(true)
                }
            }
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("Page \(reader.from + 1) of 3")
        }
    }

    private var foot: some View {
        VStack(spacing: 12) {
            ZStack(alignment: .topLeading) {
                HStack(spacing: Self.pitch - Self.dot) {
                    ForEach(0..<3, id: \.self) { _ in
                        Circle().fill(Color.rgba(38, 35, 30, 0.2)).frame(width: Self.dot, height: Self.dot)
                    }
                }
                Capsule()
                    .fill(Self.text)
                    .frame(width: Self.pill, height: Self.dot)
                    .scaleEffect(x: reader.pillStretch, y: 1)
                    .offset(x: reader.pillAt * Self.pitch - (Self.pill - Self.dot) / 2)
            }
            .frame(width: Self.pitch * 2 + Self.dot, height: Self.dot, alignment: .topLeading)
            .accessibilityHidden(true)
            Text("Swipe to turn the page")
                .font(.system(size: 13, weight: .medium))
                .foregroundStyle(Self.mute)
                .opacity(reader.hint)
        }
    }

    /// a soft ink-dark touch on the paper: a faint halo, a thin ring and a darker core
    private var finger: some View {
        ZStack {
            Circle().fill(Color.rgba(38, 35, 30, 0.08))
            Circle()
                .stroke(Color.rgba(38, 35, 30, 0.1), lineWidth: 10)
                .blur(radius: 5)
                .clipShape(Circle())
            Circle().strokeBorder(Color.rgba(38, 35, 30, 0.34), lineWidth: 1.5)
            Circle().fill(Color.rgba(38, 35, 30, 0.3)).frame(width: 14, height: 14)
        }
        .frame(width: Self.ghost, height: Self.ghost)
        .background(
            Circle()
                .fill(Color.rgba(38, 35, 30, 0.16))
                .blur(radius: 8)
                .mask(
                    Path { path in
                        path.addRect(CGRect(x: -40, y: -40, width: Self.ghost + 80, height: Self.ghost + 80))
                        path.addEllipse(in: CGRect(x: 0, y: 0, width: Self.ghost, height: Self.ghost))
                    }
                    .fill(style: FillStyle(eoFill: true))
                )
        )
    }
}
