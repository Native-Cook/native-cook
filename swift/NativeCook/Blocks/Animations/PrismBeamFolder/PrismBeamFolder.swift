import SwiftUI
import UIKit

/*
 Prism Beam Folder — "Larder", a grocery list app saving the picks in your cart to a collection.

 Look: a near-black stage, a light-blue folder named "Weekend Snacks". Press it and a prism beam
 shoots up out of it and opens into a cone of soft pink, red, amber, cream, blue and violet bands
 (one Metal shader, PrismBeamFolderBeam.metal); the picks — peaches, cherries, aubergines, chilies,
 drawn in code (PrismBeamFolderArt.swift) — float down through the light into the folder, the
 folder bobs as each one lands and its badge ticks up. Then the beam narrows to a line and drops
 back into the folder.

 autoPlay: loop = 8 s, see PrismBeamFolderMotion.swift for the script.
 Touch: hold the folder to open the beam and pour (one pick every 0.26 s while held); release and
 the beam closes after the last landing. Light haptics when the beam opens and on each landing.
 2 s after the release, once the folder is back at rest, the loop restarts from t = 0.
 Reduced motion: a still of the beam fully open, no loop, no touch.
*/
struct PrismBeamFolder: View {
    /// Plays the 8 s press-pour-close loop until touched.
    var autoPlay = false

    typealias M = PrismBeamFolderMotion
    typealias A = PrismBeamFolderArt

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var sim = PrismBeamFolderSim()
    @State private var live = false
    @State private var pressed = false

    var body: some View {
        // The block draws under the system bars and keeps its content clear of them itself.
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            stage(size: size, insets: insets)
                .frame(width: size.width, height: size.height)
                .offset(x: -insets.leading, y: -insets.top)
        }
        .background(A.hex(0x08070C).ignoresSafeArea())
        .preferredColorScheme(.dark)
    }

    private func stage(size: CGSize, insets: EdgeInsets) -> some View {
        let g = PrismBeamFolderGeo(w: max(1, size.width), h: max(1, size.height),
                                   top: insets.top, bottom: insets.bottom)
        let auto = autoPlay && !reduceMotion
        let reduced = reduceMotion
        let paused = reduced || (!auto && !live)
        return TimelineView(.animation(paused: paused)) { timeline in
            let f = reduced
                ? PrismBeamFolderSession.script(M.reducedAt, geo: g)
                : sim.frame(at: timeline.date, geo: g, auto: auto)
            ZStack(alignment: .topLeading) {
                RadialGradient(colors: [A.hex(0x14101C), A.hex(0x08070C)],
                               center: UnitPoint(x: 0.5, y: g.folderCY / g.h),
                               startRadius: 0, endRadius: g.h * 0.75)
                floorGlow(f, g)
                beam(f, g)
                folder(f, g)
                header(g)
                    .opacity(f.header)
                folderText(f, g)
                Text("Hold the folder to pour")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(A.hex(0x5F5A72))
                    .frame(width: g.w, height: 18)
                    .offset(y: g.h - g.bottom - 30 - 9)
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .contentShape(Rectangle())
            .gesture(touch(g, auto: auto), including: reduced ? .none : .all)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("Weekend Snacks folder. Hold it to pour your picks in.")
        }
        .clipped()
        .onAppear {
            sim.onFeel = {
                DispatchQueue.main.async { UIImpactFeedbackGenerator(style: .light).impactOccurred() }
            }
        }
    }

    // MARK: Layers

    private func floorGlow(_ f: PrismBeamFolderFrame, _ g: PrismBeamFolderGeo) -> some View {
        Canvas { c, _ in
            guard f.glow > 0.001 else { return }
            let center = CGPoint(x: g.cx, y: g.folderCY + 44)
            var e = c
            e.scaleBy(x: 1, y: 0.3)
            let cc = CGPoint(x: center.x, y: center.y / 0.3)
            e.fill(Path(ellipseIn: CGRect(x: cc.x - 190, y: cc.y - 190, width: 380, height: 380)),
                   with: .radialGradient(
                       Gradient(stops: [.init(color: A.hex(0xFF5FD2, 0.32 * f.glow), location: 0),
                                        .init(color: A.hex(0xB65CFF, 0.14 * f.glow), location: 0.5),
                                        .init(color: A.hex(0xB65CFF, 0), location: 1)]),
                       center: cc, startRadius: 0, endRadius: 190))
        }
        .frame(width: g.w, height: g.h)
        .allowsHitTesting(false)
    }

    private func beam(_ f: PrismBeamFolderFrame, _ g: PrismBeamFolderGeo) -> some View {
        let shader = ShaderLibrary.default.PrismBeamFolderBeam(
            .float4(Float(g.cx), Float(g.originY), Float(g.length), Float(f.tip)),
            .float4(Float(f.hb), Float(f.ht), Float(f.beamAlpha), Float(2 * .pi * f.phase / M.loop)),
            .float4(Float(g.top + 60), Float(g.top + 260), 0, 0)
        )
        return Rectangle()
            .fill(Color.black)
            .colorEffect(shader)
            .frame(width: g.w, height: g.h)
            .opacity(f.beamAlpha > 0.001 ? 1 : 0)
            .allowsHitTesting(false)
    }

    private func folder(_ f: PrismBeamFolderFrame, _ g: PrismBeamFolderGeo) -> some View {
        Canvas { c, _ in
            let base = CGPoint(x: g.cx, y: g.folderCY)
            // Contact shadow.
            var sh = c
            sh.addFilter(.blur(radius: 6))
            sh.fill(Path(ellipseIn: CGRect(x: base.x - 55 * f.folderSX, y: base.y + 38, width: 110 * f.folderSX, height: 12)),
                    with: .color(.black.opacity(0.7)))

            // The folder scales from the middle of its bottom edge.
            var fc = c
            fc.translateBy(x: base.x, y: base.y + 42)
            fc.scaleBy(x: f.folderSX, y: f.folderSY)
            fc.translateBy(x: 0, y: -42)
            A.drawBack(&fc)

            // Beam light spilling over the back panel at the mouth.
            if f.beamAlpha > 0.001 {
                var lc = fc
                lc.blendMode = .plusLighter
                let k = f.beamAlpha * (0.4 + 0.6 * f.glow)
                lc.fill(Path(ellipseIn: CGRect(x: -70, y: -34 - 70, width: 140, height: 140)),
                        with: .radialGradient(
                            Gradient(stops: [.init(color: A.hex(0xFFF3E6, 0.55 * k), location: 0),
                                             .init(color: A.hex(0xFF5FD2, 0.22 * k), location: 0.45),
                                             .init(color: A.hex(0xFF5FD2, 0), location: 1)]),
                            center: CGPoint(x: 0, y: -34), startRadius: 0, endRadius: 70))
            }

            // Items between the back and the front, so they drop into the mouth.
            for it in f.items {
                var ic = c
                ic.opacity = it.alpha
                ic.translateBy(x: it.x, y: it.y)
                ic.rotate(by: .degrees(it.angle))
                let s = it.scale * it.size / 48
                ic.scaleBy(x: s, y: s)
                A.drawItem(&ic, kind: it.kind)
            }

            A.drawFront(&fc, open: f.open, light: f.glow * f.beamAlpha)

            // Press ripple.
            if f.rippleA > 0.001 {
                let dot = 22.0
                c.fill(Path(ellipseIn: CGRect(x: base.x - dot, y: base.y - dot, width: 2 * dot, height: 2 * dot)),
                       with: .color(.white.opacity(f.rippleA * 0.45)))
                c.stroke(Path(ellipseIn: CGRect(x: base.x - f.rippleR, y: base.y - f.rippleR,
                                                width: 2 * f.rippleR, height: 2 * f.rippleR)),
                         with: .color(.white.opacity(f.rippleA)), lineWidth: 2)
            }

            // Badge.
            if f.badge > 0.001 {
                var bc = c
                bc.translateBy(x: base.x + 46, y: base.y - 38)
                bc.scaleBy(x: f.badge, y: f.badge)
                let r = 13.0
                bc.fill(Path(ellipseIn: CGRect(x: -r - 1.5, y: -r - 1.5, width: 2 * r + 3, height: 2 * r + 3)),
                        with: .color(A.hex(0x08070C)))
                bc.fill(Path(ellipseIn: CGRect(x: -r, y: -r, width: 2 * r, height: 2 * r)),
                        with: .linearGradient(Gradient(colors: [A.hex(0xFF4D9A), A.hex(0xE8317F)]),
                                              startPoint: CGPoint(x: 0, y: -r), endPoint: CGPoint(x: 0, y: r)))
                bc.draw(Text("\(f.count)").font(.system(size: 13, weight: .bold)).foregroundStyle(.white),
                        at: .zero, anchor: .center)
            }
        }
        .frame(width: g.w, height: g.h)
        .allowsHitTesting(false)
    }

    private func header(_ g: PrismBeamFolderGeo) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("LARDER · COLLECTIONS")
                .font(.system(size: 11, weight: .semibold))
                .kerning(2)
                .foregroundStyle(A.hex(0x6F6A82))
                .frame(height: 14, alignment: .leading)
            Text("Save your picks")
                .font(.system(size: 30, weight: .bold))
                .kerning(-0.5)
                .foregroundStyle(A.hex(0xF3F0F8))
                .lineLimit(1)
                .minimumScaleFactor(0.6)
                .frame(height: 36, alignment: .leading)
                .padding(.top, 6)
            Text("Hold a folder to pour them in.")
                .font(.system(size: 15))
                .foregroundStyle(A.hex(0x8D88A0))
                .lineLimit(1)
                .frame(height: 20, alignment: .leading)
                .padding(.top, 4)
        }
        .frame(width: g.w - 48, alignment: .leading)
        .offset(x: 24, y: g.top + 18)
        .allowsHitTesting(false)
    }

    private func folderText(_ f: PrismBeamFolderFrame, _ g: PrismBeamFolderGeo) -> some View {
        VStack(spacing: 4) {
            Text("Weekend Snacks")
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(A.hex(0xF3F0F8))
                .frame(height: 22)
            ZStack {
                Text("9 picks ready")
                    .foregroundStyle(A.hex(0x8D88A0))
                    .opacity(1 - f.saved)
                Text("+\(f.count) saved")
                    .foregroundStyle(A.hex(0xFF6FB5))
                    .opacity(f.saved)
            }
            .font(.system(size: 14, weight: .medium))
            .monospacedDigit()
            .frame(height: 18)
        }
        .frame(width: g.w)
        .offset(y: g.folderCY + 42 + 16)
        .allowsHitTesting(false)
    }

    // MARK: Touch

    private func touch(_ g: PrismBeamFolderGeo, auto: Bool) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { v in
                guard !pressed, g.onFolder(v.startLocation.x, v.startLocation.y) else { return }
                pressed = true
                live = true
                sim.press(at: Date(), geo: g, auto: auto)
            }
            .onEnded { _ in
                guard pressed else { return }
                pressed = false
                let now = Date()
                sim.release(at: now)
                if !auto {
                    let wait = sim.secondsToRest(from: now) + 0.2
                    DispatchQueue.main.asyncAfter(deadline: .now() + wait) {
                        if !pressed && !sim.inSession { live = false }
                    }
                }
            }
    }
}
