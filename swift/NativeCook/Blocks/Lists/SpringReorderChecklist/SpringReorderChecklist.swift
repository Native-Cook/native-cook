import SwiftUI
import UIKit

struct SpringReorderChecklistItem: Identifiable {
    let id: String
    let title: String
    let note: String
    var packed = false
}

enum SpringReorderChecklistPalette {
    static let page = Color(hex: 0xf5f2ea)
    static let card = Color(hex: 0xfffdf8)
    static let ink = Color(hex: 0x26302b)
    static let mute = Color(hex: 0x7b8078)
    static let hair = Color.rgba(38, 48, 43, 0.1)
    static let green = Color(hex: 0x2f7d5b)
}

/// The packing list of Stowe, an invented travel app, around the checklist. Long-press lifts a row, its neighbours
/// part on springs as it moves, the list scrolls itself near the edges, the row settles with a small overshoot and its
/// check draws. Long-press to drag, tap to check; the film returns 2 s after the last touch.
struct SpringReorderChecklist: View {
    var autoPlay = true

    static let loop = SpringReorderChecklistMotion.loop

    static let items: [SpringReorderChecklistItem] = [
        .init(id: "passport", title: "Passport", note: "Front pocket", packed: true),
        .init(id: "jacket", title: "Rain jacket", note: "Carry-on · 420 g"),
        .init(id: "charger", title: "Charger and cable", note: "Carry-on · 90 g", packed: true),
        .init(id: "shoes", title: "Walking shoes", note: "Suitcase · 1 pair"),
        .init(id: "shirts", title: "Linen shirts", note: "Suitcase · 3"),
        .init(id: "adapter", title: "Plug adapter", note: "Carry-on · Type F", packed: true),
        .init(id: "toiletries", title: "Toiletries bag", note: "Suitcase · under 100 ml"),
        .init(id: "sunscreen", title: "Sunscreen", note: "Suitcase · SPF 50"),
        .init(id: "book", title: "Paperback", note: "Carry-on · for the flight"),
        .init(id: "bottle", title: "Water bottle", note: "Carry-on · empty at security"),
        .init(id: "socks", title: "Socks", note: "Suitcase · 5 pairs", packed: true),
        .init(id: "hat", title: "Sun hat", note: "Suitcase · packable"),
        .init(id: "meds", title: "Travel meds", note: "Carry-on · 1 pouch", packed: true),
        .init(id: "tote", title: "Folding tote", note: "Suitcase · for the market"),
    ]

    private typealias P = SpringReorderChecklistPalette

    @State private var packed = SpringReorderChecklist.items.filter(\.packed).count

    init(autoPlay: Bool = true) {
        self.autoPlay = autoPlay
    }

    var body: some View {
        let total = Self.items.count
        VStack(alignment: .leading, spacing: 0) {
            VStack(alignment: .leading, spacing: 0) {
                Text("Leaving Friday, 7:40 am")
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(P.mute)
                Text("Porto, 6 days")
                    .font(.system(size: 32, weight: .bold))
                    .kerning(-0.5)
                    .foregroundStyle(P.ink)
                    .padding(.top, 2)
                    .accessibilityAddTraits(.isHeader)
                HStack(spacing: 12) {
                    RoundedRectangle(cornerRadius: 3)
                        .fill(Color.rgba(38, 48, 43, 0.12))
                        .overlay(alignment: .leading) {
                            RoundedRectangle(cornerRadius: 3)
                                .fill(P.green)
                                .scaleEffect(x: Double(packed) / Double(total), anchor: .leading)
                        }
                        .clipShape(RoundedRectangle(cornerRadius: 3))
                        .frame(height: 5)
                        .frame(maxWidth: .infinity)
                    Text("\(packed) of \(total) packed")
                        .font(.system(size: 13, weight: .semibold))
                        .monospacedDigit()
                        .foregroundStyle(P.ink)
                }
                .padding(.top, 12)
            }
            .padding(.horizontal, 20)
            .padding(.top, 8)
            .padding(.bottom, 12)

            SpringReorderChecklistList(items: Self.items, autoPlay: autoPlay) { n in
                withAnimation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.32)) { packed = n }
            }
        }
        .background(P.page.ignoresSafeArea())
        .preferredColorScheme(.light)
    }
}

/// A checklist reordered by long-press. The held row lifts, its neighbours part on springs while it moves, it settles
/// with a small overshoot, and the check draws its stroke. Rows are transforms over a fixed order table: nothing is laid
/// out while dragging, and the list scrolls itself when the row nears an edge.
struct SpringReorderChecklistList: View {
    let items: [SpringReorderChecklistItem]
    var autoPlay = true
    var onPackedChange: ((Int) -> Void)?
    var onReorder: (([String]) -> Void)?

    private typealias M = SpringReorderChecklistMotion
    private typealias P = SpringReorderChecklistPalette

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var engine: SpringReorderChecklistEngine
    @State private var awake = true
    @State private var touching = false

    init(items: [SpringReorderChecklistItem], autoPlay: Bool = true, onPackedChange: ((Int) -> Void)? = nil, onReorder: (([String]) -> Void)? = nil) {
        self.items = items
        self.autoPlay = autoPlay
        self.onPackedChange = onPackedChange
        self.onReorder = onReorder
        _engine = State(initialValue: SpringReorderChecklistEngine(items: items))
    }

    var body: some View {
        GeometryReader { proxy in
            let bottomInset = proxy.safeAreaInsets.bottom
            let bottomPad = max(bottomInset, 12) + 28
            let width = proxy.size.width
            let height = proxy.size.height + bottomInset
            TimelineView(.animation(paused: !awake)) { timeline in
                let frame = step(timeline.date, width: width, height: height, bottomPad: bottomPad)
                ZStack(alignment: .topLeading) {
                    ForEach(Array(items.enumerated()), id: \.element.id) { i, item in
                        let row = frame.rows.indices.contains(i) ? frame.rows[i] : SpringReorderChecklistRowFrame(y: Double(i) * M.rowH, lift: 0, draw: item.packed ? 1 : 0)
                        SpringReorderChecklistRow(item: item, lift: row.lift, draw: row.draw)
                            .frame(width: width, height: M.rowH)
                            .scaleEffect(1 + (M.liftScale - 1) * row.lift)
                            .offset(y: row.y)
                            .zIndex(row.lift > 0.01 ? 10 : 1)
                            .accessibilityElement(children: .ignore)
                            .accessibilityLabel("\(item.title), \(item.note)")
                            .accessibilityValue(engine.checked[i] == 1 ? "Packed" : "Not packed")
                            .accessibilityAddTraits(.isToggle)
                            .accessibilityAction { engine.toggle(i); awake = true }
                    }
                    Circle()
                        .fill(Color.rgba(38, 48, 43, 0.2))
                        .frame(width: 44, height: 44)
                        .scaleEffect(1 - 0.14 * frame.ghostTap)
                        .offset(x: frame.ghostX - 22, y: frame.ghostY - 22)
                        .opacity(frame.ghost * 0.9)
                        .zIndex(20)
                        .allowsHitTesting(false)
                        .accessibilityHidden(true)
                }
                .frame(width: width, height: height, alignment: .topLeading)
            }
            .overlay(alignment: .bottom) {
                LinearGradient(
                    stops: [
                        .init(color: P.page.opacity(0), location: 0),
                        .init(color: P.page.opacity(0.86), location: 0.5),
                        .init(color: P.page, location: 1),
                    ],
                    startPoint: .top, endPoint: .bottom
                )
                .frame(height: 44 + bottomInset * 0.5)
                .allowsHitTesting(false)
            }
            .frame(width: width, height: height, alignment: .topLeading)
            .clipped()
            .contentShape(Rectangle())
            .gesture(drag)
            .ignoresSafeArea(edges: .bottom)
        }
        .background(P.page)
        .onChange(of: reduceMotion) { _, _ in awake = true }
    }

    private func step(_ date: Date, width: Double, height: Double, bottomPad: Double) -> SpringReorderChecklistFrame {
        let e = engine
        if e.onHaptic == nil { wire() }
        e.viewW = width
        e.viewH = height
        e.contentH = Double(items.count) * M.rowH + bottomPad
        e.configure(film: autoPlay && !reduceMotion)
        let f = e.frame(at: date.timeIntervalSinceReferenceDate)
        if e.asleep && awake && !touching { DispatchQueue.main.async { if e.asleep { awake = false } } }
        return f
    }

    private func wire() {
        engine.onPackedChange = onPackedChange
        engine.onHaptic = { kind in
            switch kind {
            case 0: UIImpactFeedbackGenerator(style: .medium).impactOccurred()
            case 2: UIImpactFeedbackGenerator(style: .light).impactOccurred()
            default: UISelectionFeedbackGenerator().selectionChanged()
            }
        }
        let ids = items.map(\.id)
        let cb = onReorder
        engine.onReorder = { order in cb?(order.map { ids[$0] }) }
    }

    private var drag: some Gesture {
        DragGesture(minimumDistance: 0, coordinateSpace: .local)
            .onChanged { v in
                let now = Date().timeIntervalSinceReferenceDate
                if !touching {
                    touching = true
                    awake = true
                    engine.touchBegan(x: v.startLocation.x, y: v.startLocation.y, at: now)
                }
                engine.touchMoved(y: v.location.y, at: now)
            }
            .onEnded { v in
                engine.touchEnded(velocityY: v.velocity.height, at: Date().timeIntervalSinceReferenceDate)
                touching = false
                awake = true
            }
    }
}

/// One row: the ring and its drawn check, the title struck through as it is packed, the note and the grip. A plate
/// with a soft shadow fades in under it while it is held.
struct SpringReorderChecklistRow: View {
    let item: SpringReorderChecklistItem
    let lift: Double
    let draw: Double

    private typealias P = SpringReorderChecklistPalette
    private static let checkSize = 26.0

    var body: some View {
        let hairOpacity = 1 - min(max(lift * 2, 0), 1)
        ZStack(alignment: .leading) {
            Rectangle().fill(P.page).opacity(hairOpacity)
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(P.card)
                .shadow(color: Color.rgba(32, 40, 34, 0.2), radius: 1.5, y: 1)
                .shadow(color: Color.rgba(32, 40, 34, 0.2), radius: 15, y: 14)
                .padding(.horizontal, 6)
                .opacity(min(max(lift, 0), 1))
            HStack(spacing: 0) {
                check
                VStack(alignment: .leading, spacing: 2) {
                    Text(item.title)
                        .font(.system(size: 17, weight: .medium))
                        .foregroundStyle(P.ink)
                        .lineLimit(1)
                        .opacity(1 - 0.48 * draw)
                        .overlay(alignment: .leading) {
                            RoundedRectangle(cornerRadius: 1)
                                .fill(Color.rgba(38, 48, 43, 0.55))
                                .frame(height: 1.5)
                                .scaleEffect(x: max(draw, 0.0001), anchor: .leading)
                                .opacity(draw > 0.001 ? 1 : 0)
                        }
                    Text(item.note)
                        .font(.system(size: 13))
                        .monospacedDigit()
                        .foregroundStyle(P.mute)
                        .lineLimit(1)
                }
                .padding(.leading, 14)
                .padding(.trailing, 12)
                .frame(maxWidth: .infinity, alignment: .leading)
                VStack(spacing: 3) {
                    ForEach(0..<3, id: \.self) { _ in
                        RoundedRectangle(cornerRadius: 1)
                            .fill(Color.rgba(38, 48, 43, 0.3))
                            .frame(width: 16, height: 1.75)
                    }
                }
                .frame(width: 28, height: 28)
            }
            .padding(.leading, 20)
            .padding(.trailing, 20)
        }
        .overlay(alignment: .bottom) {
            Rectangle()
                .fill(P.hair)
                .frame(height: 1 / 3)
                .padding(.leading, 60)
                .opacity(hairOpacity)
        }
    }

    private var check: some View {
        let s = Self.checkSize
        return ZStack {
            Circle().strokeBorder(Color.rgba(38, 48, 43, 0.28), lineWidth: 1.75)
            Circle()
                .fill(P.green)
                .scaleEffect(0.72 + 0.28 * draw)
                .opacity(min(max(draw * 3, 0), 1))
            Path { p in
                let k = s / 24
                p.move(to: CGPoint(x: 6.4 * k, y: 12.6 * k))
                p.addLine(to: CGPoint(x: 10.5 * k, y: 16.5 * k))
                p.addLine(to: CGPoint(x: 17.8 * k, y: 8.2 * k))
            }
            .trim(from: 0, to: min(max(draw, 0), 1))
            .stroke(Color(hex: 0xfbfff9), style: StrokeStyle(lineWidth: 2.4 * s / 24, lineCap: .round, lineJoin: .round))
            .opacity(draw > 0.02 ? 1 : 0)
        }
        .frame(width: s, height: s)
    }
}
