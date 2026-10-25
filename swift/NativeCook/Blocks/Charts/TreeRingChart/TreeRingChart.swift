import SwiftUI

/// "Heartwood", a savings app that shows seven years of saving as the cross-section of a cut tree.
/// Each yearly ring is as wide as that year's savings (2019 $3,120 … 2025 $9,380, $44,650 in all).
/// The slice is one Metal shader (TreeRingChartShader.metal); the guides, the table's pool of light
/// and the leader are Canvas drawing; the labels are views.
///
/// autoPlay (loop = 9 s, TreeRingChartMotion.swift):
///   0.0 – 1.4   rest: the full slice, 2021 selected.
///   1.4 – 2.5   the rings shrink back to the pith over a faint ghost, outermost first.
///   2.5 – 6.4   they regrow year by year with a back-out swell; the newest ring is selected.
///   6.4 – 7.4   the selection travels back from 2025 to 2021 and the glow returns.
///   7.4 – 9.0   rest.
/// Touch: drag from the pith outward to grow the slice to the finger; the year row scrubs too.
/// 2 s after the last touch it eases back to rest (800 ms) and the loop starts again.
struct TreeRingChart: View {
    var autoPlay = false

    static let loop = TreeRingChartData.loop

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var model = TreeRingChartModel()
    @State private var heroFrame: CGRect = .zero
    @State private var touchLive = false
    @State private var touchSerial = 0
    @State private var dragging = false

    private typealias D = TreeRingChartData
    static let ink = Color(red: 0xF4 / 255, green: 0xE7 / 255, blue: 0xD4 / 255)
    static let muted = Color(red: 0xF4 / 255, green: 0xE7 / 255, blue: 0xD4 / 255).opacity(0.58)
    static let amber = Color(red: 0xF2 / 255, green: 0xB8 / 255, blue: 0x6A / 255)
    static let background = Color(red: 0x1B / 255, green: 0x13 / 255, blue: 0x0E / 255)

    var body: some View {
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            screen(size: size, insets: insets)
                .frame(width: size.width, height: size.height)
                .offset(x: -insets.leading, y: -insets.top)
        }
    }

    private var auto: Bool { autoPlay && !reduceMotion }
    private var paused: Bool { !(auto || touchLive) }

    private func screen(size: CGSize, insets: EdgeInsets) -> some View {
        model.configure(auto: auto, reduced: reduceMotion)
        let rowW = max(1, size.width - 48)
        let step = rowW / Double(D.ringCount)
        let heroGeo = TreeRingChartGeo(w: heroFrame.width, h: heroFrame.height)
        return ZStack(alignment: .topLeading) {
            LinearGradient(
                stops: [
                    .init(color: Color(red: 0x2C / 255, green: 0x1E / 255, blue: 0x15 / 255), location: 0),
                    .init(color: Color(red: 0x21 / 255, green: 0x16 / 255, blue: 0x0F / 255), location: 0.55),
                    .init(color: Color(red: 0x15 / 255, green: 0x0E / 255, blue: 0x0A / 255), location: 1),
                ],
                startPoint: .top, endPoint: .bottom
            )
            TreeRingChartBackdrop(size: size, hero: heroFrame)
                .allowsHitTesting(false)

            VStack(spacing: 0) {
                header
                    .padding(.horizontal, 24)

                hero(geo: heroGeo)
                    .frame(maxWidth: .infinity, minHeight: 300, maxHeight: .infinity)
                    .onGeometryChange(for: CGRect.self) { $0.frame(in: .named("TreeRingChart")) } action: { heroFrame = $0 }

                VStack(spacing: 2) {
                    live { pose in
                        TreeRingChartRollStack(items: D.labels, index: pose.sel, height: 32) {
                            Text($0)
                                .font(.system(size: 26, weight: .bold))
                                .kerning(-0.4)
                                .monospacedDigit()
                                .foregroundStyle(Self.ink)
                        }
                    }
                    live { pose in
                        TreeRingChartRollStack(items: D.deltas, index: pose.sel, height: 18) {
                            Text($0)
                                .font(.system(size: 13, weight: .medium))
                                .monospacedDigit()
                                .foregroundStyle(Self.muted)
                        }
                    }
                }
                .padding(.top, 4)
                .padding(.horizontal, 24)
                .allowsHitTesting(false)

                yearRow(width: rowW, step: step)
                    .padding(.top, 16)

                card
                    .padding(.top, 16)
                    .padding(.horizontal, 24)
            }
            .padding(.top, insets.top + 10)
            .padding(.bottom, insets.bottom + 12)
            .frame(width: size.width, height: size.height, alignment: .top)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .coordinateSpace(name: "TreeRingChart")
        .background(Self.background)
        .clipped()
    }

    /// A view redrawn every frame from the model's pose.
    private func live<V: View>(@ViewBuilder _ content: @escaping (TreeRingChartPose) -> V) -> some View {
        TimelineView(.animation(paused: paused)) { tl in
            content(model.pose(at: tl.date))
        }
    }

    // MARK: Header

    private var header: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text("HEARTWOOD")
                    .font(.system(size: 12, weight: .bold))
                    .kerning(2.4)
                    .foregroundStyle(Self.amber)
                Spacer(minLength: 8)
                Text("Savings · 2019 – 2025")
                    .font(.system(size: 12, weight: .medium))
                    .monospacedDigit()
                    .foregroundStyle(Self.muted)
                    .padding(.horizontal, 12)
                    .frame(height: 28)
                    .background(
                        Capsule().fill(Color(red: 1, green: 236 / 255, blue: 210 / 255).opacity(0.06))
                    )
                    .overlay(
                        Capsule().strokeBorder(Color(red: 1, green: 236 / 255, blue: 210 / 255).opacity(0.18), lineWidth: 1 / 3)
                    )
            }
            Text("7 years of saving")
                .font(.system(size: 32, weight: .bold))
                .kerning(-0.6)
                .foregroundStyle(Self.ink)
                .padding(.top, 4)
            HStack(spacing: 8) {
                live { pose in
                    TreeRingChartOdometer(value: pose.total, digits: 5, prefix: "$", height: 24)
                }
                Text("put away, one ring a year")
                    .font(.system(size: 14))
                    .foregroundStyle(Self.muted)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    // MARK: Hero

    private func hero(geo: TreeRingChartGeo) -> some View {
        ZStack {
            if geo.w > 0 && geo.h > 0 {
                live { pose in
                    let radii = pose.radii(pith: geo.pith, wood: geo.wood)
                    let grown = treeRingChartClamp01((radii[D.ringCount - 1] - geo.pith) / (geo.wood - geo.pith))
                    ZStack {
                        Rectangle()
                            .colorEffect(ShaderLibrary.treeRingChartSlice(
                                .float2(geo.cx, geo.cy),
                                .float4(radii[0], radii[1], radii[2], radii[3]),
                                .float4(radii[4], radii[5], radii[6], geo.pith),
                                .float4(pose.sel, pose.glow, geo.bark * (0.55 + 0.45 * grown), geo.unit),
                                .float2(geo.wood, geo.bark)
                            ))
                        TreeRingChartLeader(geo: geo, mid: geo.selectedMid(radii, sel: pose.sel), glow: pose.glow)
                    }
                    .frame(width: geo.w, height: geo.h)
                }
                .allowsHitTesting(false)
            }
            Color.clear
                .contentShape(Rectangle())
                .gesture(
                    DragGesture(minimumDistance: 0)
                        .onChanged { v in
                            if !dragging {
                                beginTouch()
                                model.sliceBegan(v.location, geo: geo)
                            } else {
                                model.sliceMoved(v.location, geo: geo)
                            }
                        }
                        .onEnded { _ in
                            model.sliceEnded()
                            endTouch()
                        }
                )
                .accessibilityElement()
                .accessibilityLabel("Tree ring chart of yearly savings. Drag from the centre outward to pick a year.")
        }
    }

    // MARK: Year row

    private func yearRow(width: Double, step: Double) -> some View {
        let maxAmount = D.amounts.max() ?? 1
        return ZStack(alignment: .bottomLeading) {
            live { pose in
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .fill(Self.amber.opacity(0.13))
                    .overlay(
                        RoundedRectangle(cornerRadius: 14, style: .continuous)
                            .strokeBorder(Self.amber.opacity(0.4), lineWidth: 1)
                    )
                    .frame(width: step - 6, height: 76)
                    .offset(x: 3 + pose.sel * step)
                    .frame(width: width, height: 76, alignment: .leading)
            }
            HStack(spacing: 0) {
                ForEach(D.years.indices, id: \.self) { i in
                    VStack(spacing: 6) {
                        RoundedRectangle(cornerRadius: 5)
                            .fill(Color(red: 226 / 255, green: 178 / 255, blue: 118 / 255).opacity(0.42))
                            .frame(width: 14, height: 8 + 30 * D.amounts[i] / maxAmount)
                        Text(String(D.years[i]))
                            .font(.system(size: 11, weight: .semibold))
                            .monospacedDigit()
                            .foregroundStyle(Self.muted)
                    }
                    .padding(.bottom, 8)
                    .frame(width: step, height: 76, alignment: .bottom)
                }
            }
        }
        .frame(width: width, height: 76)
        .contentShape(Rectangle())
        .gesture(
            DragGesture(minimumDistance: 0)
                .onChanged { v in
                    if !dragging {
                        beginTouch()
                        model.rowBegan(v.location.x, step: step)
                    } else {
                        model.rowMoved(v.location.x, step: step)
                    }
                }
                .onEnded { _ in
                    model.rowEnded()
                    endTouch()
                }
        )
        .accessibilityLabel("Years")
    }

    // MARK: Card

    private var card: some View {
        let line = Color(red: 1, green: 238 / 255, blue: 214 / 255).opacity(0.14)
        let shape = RoundedRectangle(cornerRadius: 20, style: .continuous)
        return HStack(alignment: .top, spacing: 0) {
            stat("Best year", "2025 · $9,380")
            Rectangle().fill(line).frame(width: 1 / 3).padding(.horizontal, 12)
            stat("Average", "$6,379 a year")
            Rectangle().fill(line).frame(width: 1 / 3).padding(.horizontal, 12)
            stat("Next ring", "Goal $10,000")
        }
        .fixedSize(horizontal: false, vertical: true)
        .padding(.vertical, 14)
        .padding(.horizontal, 16)
        .background(
            ZStack {
                // The drop shadows sit outside the card only, as CSS box shadows do.
                shape.fill(Color.black)
                    .shadow(color: .black.opacity(0.3), radius: 1, y: 1)
                    .shadow(color: .black.opacity(0.25), radius: 16, y: 12)
                    .mask(
                        Rectangle().padding(-60)
                            .overlay(shape.blendMode(.destinationOut))
                            .compositingGroup()
                    )
                shape.fill(Color(red: 1, green: 238 / 255, blue: 214 / 255).opacity(0.05))
                shape.strokeBorder(line, lineWidth: 1 / 3)
            }
        )
    }

    private func stat(_ label: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label)
                .font(.system(size: 11, weight: .semibold))
                .kerning(0.4)
                .foregroundStyle(Self.muted)
            Text(value)
                .font(.system(size: 14, weight: .semibold))
                .monospacedDigit()
                .foregroundStyle(Self.ink)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    // MARK: Touch bookkeeping

    private func beginTouch() {
        dragging = true
        touchSerial += 1
        touchLive = true
    }

    /// Keeps the timeline running until the springs and the return to rest have finished.
    private func endTouch() {
        dragging = false
        touchSerial += 1
        let serial = touchSerial
        Task { @MainActor in
            try? await Task.sleep(for: .milliseconds(400))
            while model.moving && serial == touchSerial {
                try? await Task.sleep(for: .milliseconds(300))
            }
            if serial == touchSerial && !auto { touchLive = false }
        }
    }
}

/// Grain, vignette, the dashed guides and the warm pool of light, drawn once per layout.
private struct TreeRingChartBackdrop: View {
    let size: CGSize
    let hero: CGRect

    var body: some View {
        Canvas { context, _ in
            let full = CGRect(origin: .zero, size: size)
            if let grain = TreeRingChartGrain.image {
                var g = context
                g.blendMode = .overlay
                g.opacity = 0.06
                g.fill(Path(full), with: .tiledImage(Image(decorative: grain, scale: 2)))
            }
            context.fill(Path(full), with: .radialGradient(
                Gradient(stops: [
                    .init(color: .clear, location: 0),
                    .init(color: Color(red: 8 / 255, green: 4 / 255, blue: 2 / 255).opacity(0.18), location: 0.6),
                    .init(color: Color(red: 8 / 255, green: 4 / 255, blue: 2 / 255).opacity(0.5), location: 1),
                ]),
                center: CGPoint(x: size.width / 2, y: size.height * 0.42),
                startRadius: 0, endRadius: max(size.width, size.height) * 0.72
            ))
            guard hero.width > 0 else { return }
            let geo = TreeRingChartGeo(w: hero.width, h: hero.height)
            let cx = hero.minX + geo.cx
            let cy = hero.minY + geo.cy
            let radii = geo.fullRadii
            for i in radii.indices {
                let r = radii[i] + (i == radii.count - 1 ? geo.bark : 0)
                context.stroke(
                    Path(ellipseIn: CGRect(x: cx - r, y: cy - r, width: 2 * r, height: 2 * r)),
                    with: .color(Color(red: 240 / 255, green: 220 / 255, blue: 190 / 255).opacity(0.2)),
                    style: StrokeStyle(lineWidth: 1, dash: [2, 5])
                )
            }
            context.fill(Path(full), with: .radialGradient(
                Gradient(stops: [
                    .init(color: Color(red: 150 / 255, green: 96 / 255, blue: 52 / 255).opacity(0.22), location: 0),
                    .init(color: Color(red: 110 / 255, green: 66 / 255, blue: 36 / 255).opacity(0.08), location: 0.55),
                    .init(color: Color(red: 60 / 255, green: 36 / 255, blue: 22 / 255).opacity(0), location: 1),
                ]),
                center: CGPoint(x: cx, y: cy + geo.slice * 0.08),
                startRadius: 0, endRadius: geo.slice * 1.45
            ))
        }
        .frame(width: size.width, height: size.height)
    }
}

/// A small tile of seeded grey noise, tiled over the screen as grain.
enum TreeRingChartGrain {
    static let image: CGImage? = {
        let n = 128
        var bytes = [UInt8](repeating: 0, count: n * n)
        var seed: UInt32 = 0x9E3779B9
        for i in 0..<(n * n) {
            seed = seed &* 1_664_525 &+ 1_013_904_223
            bytes[i] = UInt8(truncatingIfNeeded: seed >> 24)
        }
        guard let provider = CGDataProvider(data: Data(bytes) as CFData) else { return nil }
        return CGImage(
            width: n, height: n, bitsPerComponent: 8, bitsPerPixel: 8, bytesPerRow: n,
            space: CGColorSpaceCreateDeviceGray(), bitmapInfo: CGBitmapInfo(rawValue: 0),
            provider: provider, decode: nil, shouldInterpolate: false, intent: .defaultIntent
        )
    }()
}

/// The annotation: a ringed dot on the selected year straight below the pith, and a thin leader
/// that drops from it to the label under the slice.
private struct TreeRingChartLeader: View {
    let geo: TreeRingChartGeo
    let mid: Double
    let glow: Double

    var body: some View {
        Canvas { context, _ in
            let x = geo.cx
            let y = geo.cy + mid
            let end = geo.h - 4
            let a = 0.35 + 0.65 * glow
            var line = Path()
            line.move(to: CGPoint(x: x, y: y + 6))
            line.addLine(to: CGPoint(x: x, y: end))
            context.stroke(line, with: .linearGradient(
                Gradient(stops: [
                    .init(color: Color(red: 1, green: 214 / 255, blue: 150 / 255).opacity(0.95 * a), location: 0),
                    .init(color: Color(red: 1, green: 196 / 255, blue: 120 / 255).opacity(0.55 * a), location: 0.6),
                    .init(color: Color(red: 1, green: 190 / 255, blue: 110 / 255).opacity(0.15), location: 1),
                ]),
                startPoint: CGPoint(x: x, y: y), endPoint: CGPoint(x: x, y: end)
            ), style: StrokeStyle(lineWidth: 1.25, lineCap: .round))
            var halo = context
            halo.blendMode = .plusLighter
            halo.fill(Path(ellipseIn: CGRect(x: x - 18, y: y - 18, width: 36, height: 36)), with: .radialGradient(
                Gradient(colors: [
                    Color(red: 1, green: 180 / 255, blue: 90 / 255).opacity(0.45 * a),
                    Color(red: 1, green: 150 / 255, blue: 60 / 255).opacity(0),
                ]),
                center: CGPoint(x: x, y: y), startRadius: 0, endRadius: 18
            ))
            context.stroke(
                Path(ellipseIn: CGRect(x: x - 5.5, y: y - 5.5, width: 11, height: 11)),
                with: .color(Color(red: 1, green: 232 / 255, blue: 196 / 255).opacity(a)),
                lineWidth: 1.5
            )
            context.fill(
                Path(ellipseIn: CGRect(x: x - 2.6, y: y - 2.6, width: 5.2, height: 5.2)),
                with: .color(Color(red: 1, green: 246 / 255, blue: 228 / 255).opacity(a))
            )
        }
    }
}

/// One line of a list chosen by a continuous index (2.4 sits 40 % of the way from line 2 to 3):
/// the old line rolls up and fades, the new one comes in from below, inside a clipped box.
struct TreeRingChartRollStack<Row: View>: View {
    let items: [String]
    let index: Double
    let height: Double
    @ViewBuilder let row: (String) -> Row

    var body: some View {
        ZStack {
            ForEach(items.indices, id: \.self) { i in
                let d = Double(i) - index
                if abs(d) < 1 {
                    row(items[i])
                        .lineLimit(1)
                        .frame(maxWidth: .infinity, minHeight: height, maxHeight: height)
                        .offset(y: d * height)
                        .opacity(max(0, 1 - abs(d) * 1.15))
                }
            }
        }
        .frame(maxWidth: .infinity, minHeight: height, maxHeight: height)
        .clipped()
    }
}

/// A whole-number odometer with thousands separators: `digits` columns after `prefix`.
struct TreeRingChartOdometer: View {
    let value: Double
    let digits: Int
    let prefix: String
    let height: Double

    private func glyph(_ s: String) -> some View {
        Text(s)
            .font(.system(size: 20, weight: .semibold))
            .monospacedDigit()
            .foregroundStyle(TreeRingChart.ink)
            .frame(height: height)
    }

    var body: some View {
        HStack(spacing: 0) {
            glyph(prefix)
            ForEach((0..<digits).reversed(), id: \.self) { k in
                column(place: k)
                if k % 3 == 0 && k > 0 { glyph(",") }
            }
        }
    }

    private func column(place: Int) -> some View {
        // This column turns with the lower digits' carry and rests on a whole digit whenever the
        // value is whole.
        let v = max(0, value)
        let scaled = v / pow(10, Double(place))
        let whole = floor(scaled).truncatingRemainder(dividingBy: 10)
        let frac = scaled - floor(scaled)
        let lower = place == 0 ? frac : max(0, frac * 10 - 9)
        return VStack(spacing: 0) {
            ForEach(0..<11, id: \.self) { i in glyph(String(i % 10)) }
        }
        .offset(y: -(whole + lower) * height)
        .frame(height: height, alignment: .top)
        .clipped()
    }
}
