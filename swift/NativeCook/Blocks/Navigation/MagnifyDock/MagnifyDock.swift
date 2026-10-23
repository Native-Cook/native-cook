import SwiftUI
import UIKit

/// A desktop dock over a blue-hour mountain wallpaper. Sliding a finger along it swells the icons
/// near the finger on a smooth falloff, pushing their neighbours apart, with the name of the one
/// under the finger floating above it. A tap launches an app with two bounces and a running dot; a
/// long press sets the icons wiggling, and an icon can then be dragged to a new place in the row.
struct MagnifyDock: View {
    /// Sweeps a virtual finger along the dock, launches an app and rearranges the icons, on a loop.
    var autoPlay = false
    /// Called with an app's name when it is launched.
    var onLaunch: ((String) -> Void)? = nil

    private let apps = MagnifyDockArt.apps

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var engine = MagnifyDockEngine()

    var body: some View {
        // The block draws under the system bars and keeps its content clear of them itself.
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            stage(MagnifyDockStage(width: size.width, height: size.height, top: insets.top, bottom: insets.bottom))
                .frame(width: size.width, height: size.height)
                .offset(x: -insets.leading, y: -insets.top)
        }
    }

    private func stage(_ stage: MagnifyDockStage) -> some View {
        let names = apps.map(\.name)
        let launch = onLaunch
        engine.onLaunch = launch.map { callback in { id in if names.indices.contains(id) { callback(names[id]) } } }
        return ZStack(alignment: .topLeading) {
            // The wallpaper never changes, so it is drawn once, outside the per-frame timeline.
            Canvas { context, size in
                MagnifyDockWallpaper.draw(&context, size: size)
            }
            .frame(width: stage.width, height: stage.height)
            MagnifyDockDesk(top: stage.top)
            TimelineView(.animation) { timeline in
                let frame = engine.step(to: timeline.date, stage: stage, names: apps.map(\.name), autoPlay: autoPlay && !reduceMotion, reducedMotion: reduceMotion)
                MagnifyDockScene(frame: frame, stage: stage, apps: apps, reduced: reduceMotion)
            }
        }
        .frame(width: stage.width, height: stage.height, alignment: .topLeading)
        .background(Color(hex: 0x1d3150))
        .clipped()
        .contentShape(Rectangle())
        .gesture(
            DragGesture(minimumDistance: 0)
                .onChanged { engine.touchChanged($0) }
                .onEnded { engine.touchEnded($0) }
        )
        .accessibilityElement()
        .accessibilityLabel("Dock: \(apps.map(\.name).joined(separator: ", "))")
    }
}

/// The date and the time over the sky.
private struct MagnifyDockDesk: View {
    let top: Double

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("Tuesday 14 October")
                .font(.system(size: 17, weight: .semibold))
                .tracking(0.1)
                .foregroundStyle(.white.opacity(0.88))
            Text("9:41")
                .font(.system(size: 84, weight: .bold))
                .tracking(-3)
                .monospacedDigit()
                .foregroundStyle(.white)
                .padding(.top, -4)
        }
        .padding(.leading, 26)
        .padding(.top, top + 22)
        .allowsHitTesting(false)
    }
}

private struct MagnifyDockScene: View {
    let frame: MagnifyDockFrame
    let stage: MagnifyDockStage
    let apps: [MagnifyDockApp]
    let reduced: Bool

    private typealias M = MagnifyDockMetrics
    private typealias X = MagnifyDockMath

    var body: some View {
        let W = stage.width
        ZStack(alignment: .topLeading) {
            Canvas { context, size in
                drawDock(&context, size: size)
            }
            .frame(width: W, height: stage.height)
            done
            popover(width: W)
            Circle()
                .fill(Color.white.opacity(0.28))
                .overlay(Circle().strokeBorder(Color.white.opacity(0.7), lineWidth: 1.5))
                .frame(width: 42, height: 42)
                .scaleEffect(1 - 0.2 * frame.ghostPress)
                .opacity(frame.ghostAlpha * 0.85)
                .offset(x: frame.ghostX - 21, y: frame.ghostY - 21)
        }
        .frame(width: W, height: stage.height, alignment: .topLeading)
        .allowsHitTesting(false)
    }

    /// The dock's shadow and frosted glass, the running dots and the icons, the carried one on top.
    private func drawDock(_ context: inout GraphicsContext, size: CGSize) {
        let W = stage.width
        let L = frame.layout
        let height = stage.dockBottom - stage.dockTop
        let rect = CGRect(x: W / 2 - L.dockWidth / 2, y: stage.dockTop, width: max(1, L.dockWidth), height: height)
        let dock = Path(roundedRect: rect, cornerRadius: M.dockRadius)

        var shadow = context
        shadow.addFilter(.blur(radius: 12))
        shadow.drawLayer { layer in
            layer.fill(
                Path(roundedRect: CGRect(x: rect.minX + 6, y: rect.minY + 10, width: max(1, rect.width - 12), height: height), cornerRadius: M.dockRadius),
                with: .color(Color.rgba(4, 8, 18, 0.45))
            )
        }
        // Frosted glass: the wallpaper under the dock, blurred, tinted, with a lit rim.
        var glass = context
        glass.clip(to: dock)
        glass.addFilter(.blur(radius: 18))
        glass.drawLayer { layer in
            MagnifyDockWallpaper.draw(&layer, size: size)
        }
        context.fill(dock, with: .color(Color.rgba(214, 224, 240, 0.2)))
        context.stroke(
            dock,
            with: .linearGradient(
                Gradient(colors: [.white.opacity(0.55), .white.opacity(0.12)]),
                startPoint: CGPoint(x: 0, y: stage.dockTop),
                endPoint: CGPoint(x: 0, y: stage.dockBottom)
            ),
            lineWidth: 1
        )
        for id in apps.indices where id < L.cx.count {
            let alpha = frame.running[id]
            guard alpha > 0.001 else { continue }
            context.fill(
                Path(ellipseIn: CGRect(x: L.cx[id] - 2.2, y: stage.dockBottom - 3.5 - 2.2, width: 4.4, height: 4.4)),
                with: .color(.white.opacity(0.92 * alpha))
            )
        }
        for top in [false, true] {
            for id in apps.indices where (frame.dragId == id) == top && id < L.cx.count {
                let p = pose(id, L)
                let s = max(0.01, p.size / 100)
                var icon = context
                icon.translateBy(x: p.cx, y: stage.baseline - p.size / 2 - p.rise)
                icon.rotate(by: .degrees(p.turn))
                icon.scaleBy(x: s, y: s)
                icon.translateBy(x: -50, y: -50)
                MagnifyDockArt.draw(apps[id], in: icon, scale: s)
            }
        }
    }

    /// Where icon `id` is drawn: its centre x, its size, how far it is lifted and turned.
    private func pose(_ id: Int, _ L: MagnifyDockLayout) -> (cx: Double, size: Double, rise: Double, turn: Double) {
        let W = stage.width
        let dragging = frame.dragId == id
        let half = X.restingWidth(L.cx.count) / 2 - M.pad - M.icon / 2
        let cx = dragging ? X.clamp(frame.fingerX, W / 2 - half, W / 2 + half) : L.cx[id]
        let size = M.icon * (dragging ? M.liftScale * L.fit : L.scale[id])
        let t = frame.time
        let hop = reduced ? 0 : X.bounceAt(t - frame.launchedAt[id])
        let wob = dragging || reduced ? 0 : frame.wiggle
        let phase = t * M.wiggleHz * .pi * 2 + Double(id) * 1.7
        let lift = dragging ? frame.dragY - 8 : 0
        return (cx, size, hop - lift - wob * 0.7 * sin(phase * 0.5), wob * M.wiggleDeg * sin(phase))
    }

    private var done: some View {
        Text("Done")
            .font(.system(size: 15, weight: .semibold))
            .foregroundStyle(.white)
            .padding(.horizontal, 16)
            .frame(height: 34)
            .background(
                Capsule()
                    .fill(.white.opacity(0.22))
                    .overlay(Capsule().strokeBorder(.white.opacity(0.5), lineWidth: 1 / 3))
            )
            .opacity(frame.wiggle)
            .offset(y: (1 - frame.wiggle) * -8)
            .frame(width: stage.width - 20, alignment: .trailing)
            .padding(.top, stage.top + 14)
    }

    /// The name popover: a single dark pill with a tip, gliding from icon to icon and resizing to
    /// the name it shows while the names cross-fade, so moving along the dock never makes it blink.
    private func popover(width W: Double) -> some View {
        let l = frame.label
        let w = max(M.labelPad * 2, l.w)
        // The pill stays on the screen; its tip keeps pointing at the icon.
        let cx = X.clamp(l.x, w / 2 + 8, W - w / 2 - 8)
        let tip = X.clamp(l.x - cx, -w / 2 + 10, w / 2 - 10)
        let fill = Color.rgba(22, 26, 36, 0.86)
        return VStack(spacing: 0) {
            ZStack {
                RoundedRectangle(cornerRadius: 8, style: .continuous)
                    .fill(fill)
                    .overlay(RoundedRectangle(cornerRadius: 8, style: .continuous).strokeBorder(.white.opacity(0.22), lineWidth: 1 / 3))
                ForEach(apps.indices, id: \.self) { id in
                    let a = l.names.indices.contains(id) ? l.names[id] : 0
                    Text(apps[id].name)
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(.white)
                        .lineLimit(1)
                        .fixedSize()
                        .opacity(a)
                        .offset(y: (1 - a) * 3)
                }
            }
            .frame(width: w, height: M.labelHeight)
            .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
            Path { p in
                p.move(to: .zero)
                p.addLine(to: CGPoint(x: 12, y: 0))
                p.addLine(to: CGPoint(x: 6, y: 6))
                p.closeSubpath()
            }
            .fill(fill)
            .frame(width: 12, height: 6)
            .offset(x: tip)
        }
        .frame(width: w, height: M.labelHeight + 6, alignment: .top)
        .scaleEffect(0.94 + 0.06 * l.show)
        .opacity(l.show)
        .offset(x: cx - w / 2, y: l.y + (1 - l.show) * 4)
    }
}
