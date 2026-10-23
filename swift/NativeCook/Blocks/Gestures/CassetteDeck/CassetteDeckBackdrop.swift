import SwiftUI

/// What the backdrop takes from the simulation: the lights and the room's tint.
struct CassetteDeckBackdropState: Equatable {
    private typealias Motion = CassetteDeckMotion

    var lift: CassetteDeckRGBA
    var led: Double
    var spill: Double
    var lit: [Double]

    init(sim: CassetteDeckSim) {
        // The LED flickers with a level while the tape plays (two incommensurate sines, deterministic per loop).
        let n = 0.5 + 0.25 * sin(sim.clock * 23.1) + 0.25 * sin(sim.clock * 37.7 + 1.3)
        let flicker = sim.reduced ? 0 : Motion.Material.ledFlicker * n * min(1, abs(sim.v))
        led = sim.led * (1 - flicker)
        spill = (sim.reduced ? 0.6 : 1) * Motion.Material.keyGlow * min(1, sim.lit[0])
        lit = sim.lit

        // The lift above the cassette takes the hue of the cassettes in view, weighted by how close each is to the deck.
        var r = 0.0
        var g = 0.0
        var b = 0.0
        var a = 0.0
        var total = 0.0
        for j in 0..<CassetteDeckSim.slotCount {
            let weight = max(0, 1 - abs(sim.slotX(j)))
            let tint = CassetteDeckTheme.Palette.tints[j]
            r += tint.r * weight
            g += tint.g * weight
            b += tint.b * weight
            a += tint.a * weight
            total += weight
        }
        let k = total > 0 ? 1 / total : 0
        lift = CassetteDeckRGBA(
            CassetteDeckSim.jsRound(r * k),
            CassetteDeckSim.jsRound(g * k),
            CassetteDeckSim.jsRound(b * k),
            (a * k * 1000).rounded() / 1000
        )
    }
}

/// The layers behind the cassettes and keys: the room's lift, the speaker grille, the LED, the key glyphs
/// and the music button. Blurred glows are static shapes whose opacity moves, so nothing animates a blur.
struct CassetteDeckBackdrop: View {
    let state: CassetteDeckBackdropState
    let metrics: CassetteDeckMetrics

    private typealias Palette = CassetteDeckTheme.Palette
    private typealias Layout = CassetteDeckTheme.Layout
    private typealias Theme = CassetteDeckTheme

    var body: some View {
        let s = metrics.scale
        let oy = metrics.offsetY
        ZStack(alignment: .topLeading) {
            lift
            grille

            // Red spill under the latched play key.
            let keys = Layout.keys
            Circle()
                .fill(Palette.redSpill)
                .frame(width: Theme.Space.xxl * 2 * s, height: Theme.Space.xxl * 2 * s)
                .blur(radius: 22 * s)
                .opacity(state.spill)
                .position(x: (keys.x0 + keys.w / 2) * s, y: oy + (keys.y + keys.h * 0.62) * s)

            led
            glyphs
            pill
        }
        .frame(width: metrics.width, height: metrics.height, alignment: .topLeading)
    }

    private var lift: some View {
        let s = metrics.scale
        let centre = UnitPoint(
            x: (Layout.cassette.x + Layout.cassette.w / 2) * s / metrics.width,
            y: (metrics.offsetY + 205 * s) / metrics.height
        )
        return Rectangle()
            .fill(RadialGradient(
                colors: [state.lift.color, Color.rgba(255, 255, 255, 0)],
                center: centre,
                startRadius: 0,
                endRadius: 150 * s
            ))
            .frame(width: metrics.width, height: metrics.height)
    }

    private var grille: some View {
        let s = metrics.scale
        let g = Layout.grille
        let side = (g.radius + g.dot + 1) * 2 * s
        return CassetteDeckGrille(scale: s)
            .fill(Palette.grille)
            .frame(width: side, height: side)
            .position(x: g.cx * s, y: metrics.offsetY + g.cy * s)
    }

    private var led: some View {
        let s = metrics.scale
        let spot = Layout.led
        let at = CGPoint(x: spot.cx * s, y: metrics.offsetY + spot.cy * s)
        let colour = CassetteDeckRGBA.mix(Palette.ledOff, Palette.ledOn, state.led).color
        return ZStack {
            Circle()
                .fill(Palette.ledSpill)
                .frame(width: 44 * s, height: 44 * s)
                .blur(radius: 16 * s)
                .opacity(state.led)
                .position(at)
            Circle()
                .fill(Palette.ledGlow)
                .frame(width: spot.r * 6 * s, height: spot.r * 6 * s)
                .blur(radius: 3 * s)
                .opacity(state.led)
                .position(at)
            Circle()
                .fill(colour)
                .frame(width: spot.r * 2 * s, height: spot.r * 2 * s)
                .position(at)
        }
    }

    private var glyphs: some View {
        let s = metrics.scale
        let spot = Layout.glyphs
        return ZStack {
            ForEach(0..<CassetteDeckSim.keyCount, id: \.self) { i in
                let lit = state.lit[i]
                let centre = CGPoint(x: (spot.x0 + Double(i) * Layout.keys.pitch) * s, y: metrics.offsetY + spot.y * s)
                ZStack {
                    CassetteDeckGlyph(index: i, scale: s)
                        .fill(Palette.glyphGlow)
                        .blur(radius: 2.5 * s)
                        .opacity(lit)
                    CassetteDeckGlyph(index: i, scale: s)
                        .fill(CassetteDeckRGBA.mix(Palette.glyphOff, Palette.glyphOn, lit).color)
                }
                .frame(width: 24 * s, height: 20 * s)
                .position(centre)
            }
        }
    }

    /// The small music button at the bottom left: a light grey pill with a note.
    private var pill: some View {
        let s = metrics.scale
        let box = Layout.pill
        return ZStack {
            Capsule(style: .circular)
                .fill(LinearGradient(colors: [Palette.pillTop, Palette.pillBottom], startPoint: .top, endPoint: .bottom))
            ForEach(0..<3, id: \.self) { part in
                CassetteDeckNote(part: part, scale: s).fill(Palette.pillInk)
            }
        }
        .frame(width: box.w * s, height: box.h * s)
        .position(x: (box.x + box.w / 2) * s, y: metrics.offsetY + (box.y + box.h / 2) * s)
    }
}
