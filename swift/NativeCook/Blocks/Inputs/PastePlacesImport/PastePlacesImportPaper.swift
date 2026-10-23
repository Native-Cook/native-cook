import SwiftUI

/// The folding paper (four panels lit by their angle, over the paper's shadow on the page), then the pins'
/// contact shadows on the map, the route and the dots. Card-local geometry lives in a frame with a margin
/// around the text area for the paper's perspective.
struct PastePlacesImportPaper: View {
    let time: Double
    let layout: PastePlacesImportLayout
    let faces: PastePlacesImportFaces?

    var body: some View {
        let card = PastePlacesImportTheme.card
        let cardWidth = layout.px(card.width)
        let cardHeight = layout.px(card.height)
        let margin = PastePlacesImportMotion.foldMargin * layout.pointScale
        let frameWidth = cardWidth + 2 * margin
        let frameHeight = cardHeight + 2 * margin

        ZStack(alignment: .topLeading) {
            Canvas { context, _ in drawShadow(&context, margin: margin, cardWidth: cardWidth, cardHeight: cardHeight) }
            PastePlacesImportPaperRepresentable(time: time, layout: layout, faces: faces)
            Canvas { context, _ in drawPins(&context, margin: margin) }
        }
        .frame(width: frameWidth, height: frameHeight, alignment: .topLeading)
        .position(
            x: layout.px(card.x) - margin + frameWidth / 2,
            y: layout.y(card.y) - margin + frameHeight / 2
        )
        .allowsHitTesting(false)
    }

    /// The paper's shadow on the page, under the folded stack.
    private func drawShadow(_ context: inout GraphicsContext, margin: Double, cardWidth: Double, cardHeight: Double) {
        guard PastePlacesImportMotion.panels(at: time) else { return }
        let deg = PastePlacesImportMotion.fold(at: time)
        guard deg != 0 else { return }
        let k = layout.pointScale
        let th = abs(deg) * .pi / 180
        let stack = Double(PastePlacesImportMotion.foldPanels) * (cardHeight / Double(PastePlacesImportMotion.foldPanels)) * cos(th)
        let shadow = PastePlacesImportMotion.foldShadow
        let rect = CGRect(
            x: margin + 6 * k,
            y: margin + shadow.y * k,
            width: cardWidth - 12 * k,
            height: max(stack, 4 * k)
        )
        context.drawLayer { layer in
            layer.addFilter(.blur(radius: shadow.blur * k))
            layer.fill(
                Path(roundedRect: rect, cornerRadius: 12 * k, style: .circular),
                with: .color(PastePlacesImportTheme.shade.opacity(shadow.opacity * sin(th)))
            )
        }
    }

    /// Contact shadows, the route and the dots, only while the map lies flat.
    private func drawPins(_ context: inout GraphicsContext, margin: Double) {
        guard PastePlacesImportMotion.onFlatMap(at: time) else { return }
        let k = layout.pointScale
        let u = layout.unit
        let pin = PastePlacesImportTheme.Pin.self
        let colors = PastePlacesImportTheme.MapColors.self
        context.translateBy(x: margin, y: margin)

        for (index, spot) in PastePlacesImportTheme.spots.enumerated() {
            let alpha = PastePlacesImportMotion.contact(at: time, index: index)
            if alpha <= 0 { continue }
            let h = PastePlacesImportMotion.dropAt(time, index: index).h
            let sx = spot.x * u
            let sy = spot.y * u
            let rx = pin.shadowRx * k * (1 + 0.5 * h)
            let ry = pin.shadowRy * k * (1 + 0.5 * h)
            let rect = CGRect(x: sx - rx, y: sy - ry + pin.dotR * 0.4 * k, width: 2 * rx, height: 2 * ry)
            context.drawLayer { layer in
                layer.addFilter(.blur(radius: pin.shadowBlur * k))
                layer.fill(Path(ellipseIn: rect), with: .color(colors.shadow.opacity(alpha)))
            }
        }

        let end = PastePlacesImportMotion.route(at: time)
        if end > 0.001 {
            let route = PastePlacesImportMap.route(unit: u).trimmedPath(from: 0, to: end)
            context.stroke(
                route,
                with: .color(colors.routeCasing),
                style: StrokeStyle(lineWidth: pin.routeCasing * k, lineCap: .round, lineJoin: .round)
            )
            context.stroke(
                route,
                with: .color(colors.route),
                style: StrokeStyle(lineWidth: pin.route * k, lineCap: .round, lineJoin: .round)
            )
        }

        for (index, spot) in PastePlacesImportTheme.spots.enumerated() {
            let d = PastePlacesImportMotion.dot(at: time, index: index)
            if d <= 0 { continue }
            let g = 0.6 + 0.4 * d
            let center = CGPoint(x: spot.x * u, y: spot.y * u)
            func circle(_ radius: Double) -> Path {
                Path(ellipseIn: CGRect(x: center.x - radius, y: center.y - radius, width: 2 * radius, height: 2 * radius))
            }
            context.fill(circle(pin.dotR * k * g), with: .color(colors.dotRing.opacity(d)))
            context.fill(circle(pin.coreR * k * g), with: .color(colors.dot.opacity(d)))
        }
    }
}
