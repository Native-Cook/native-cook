import QuartzCore
import SwiftUI
import UIKit

/// The folding paper: four panels of the text area, each a layer turned about its own centre line under
/// perspective. Panel i maps content (u, v) (measured from its centre) to u / (1 - k v), cos(th) v / (1 - k v),
/// which is motion.ts `panelMatrix` conjugated to the panel's centre: m22 = cos(th), m24 = -k.
final class PastePlacesImportPaperView: UIView {
    private let flat = CALayer()
    private let panels = (0..<PastePlacesImportMotion.foldPanels).map { _ in CALayer() }
    private let shades = (0..<PastePlacesImportMotion.foldPanels).map { _ in CALayer() }

    override init(frame: CGRect) {
        super.init(frame: frame)
        isUserInteractionEnabled = false
        backgroundColor = .clear
        layer.addSublayer(flat)
        for (panel, shade) in zip(panels, shades) {
            panel.allowsEdgeAntialiasing = true
            panel.addSublayer(shade)
            layer.addSublayer(panel)
        }
        flat.isHidden = true
        panels.forEach { $0.isHidden = true }
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("init(coder:) is not used") }

    func update(time: Double, layout: PastePlacesImportLayout, faces: PastePlacesImportFaces?) {
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        defer { CATransaction.commit() }

        guard PastePlacesImportMotion.panels(at: time) else {
            flat.isHidden = true
            panels.forEach { $0.isHidden = true }
            return
        }
        let card = PastePlacesImportTheme.card
        let cardWidth = layout.px(card.width)
        let cardHeight = layout.px(card.height)
        let margin = PastePlacesImportMotion.foldMargin * layout.pointScale
        let count = PastePlacesImportMotion.foldPanels
        let panelHeight = cardHeight / Double(count)
        let perspective = PastePlacesImportMotion.foldPerspective * layout.pointScale
        let deg = PastePlacesImportMotion.fold(at: time)
        let scale = faces?.scale ?? 1

        let image: CGImage?
        if PastePlacesImportMotion.mapFace(at: time) {
            image = faces?.map ?? faces?.white
        } else {
            image = (time < 6 ? faces?.note : faces?.rest) ?? faces?.white
        }

        if deg == 0 {
            panels.forEach { $0.isHidden = true }
            flat.isHidden = false
            flat.bounds = CGRect(x: 0, y: 0, width: cardWidth, height: cardHeight)
            flat.position = CGPoint(x: margin + cardWidth / 2, y: margin + cardHeight / 2)
            flat.transform = CATransform3DIdentity
            flat.contents = image
            flat.contentsScale = scale
            flat.contentsRect = CGRect(x: 0, y: 0, width: 1, height: 1)
            return
        }

        flat.isHidden = true
        for index in 0..<count {
            let panel = panels[index]
            let shade = shades[index]
            let geometry = PastePlacesImportMotion.panel(
                index,
                deg: deg,
                panelHeight: panelHeight,
                perspective: perspective
            )
            let projection = PastePlacesImportMotion.panelProjection(index, deg: deg, perspective: perspective)
            panel.isHidden = false
            panel.bounds = CGRect(x: 0, y: 0, width: cardWidth, height: panelHeight)
            panel.position = CGPoint(
                x: margin + cardWidth / 2,
                y: margin + geometry.ty + (Double(index) + 0.5) * panelHeight
            )
            var transform = CATransform3DIdentity
            transform.m22 = projection.cos
            transform.m24 = -projection.k
            panel.transform = transform
            let strip = CGRect(x: 0, y: Double(index) / Double(count), width: 1, height: 1 / Double(count))
            panel.contents = image
            panel.contentsScale = scale
            panel.contentsRect = strip
            shade.frame = panel.bounds
            shade.contents = faces?.black
            shade.contentsScale = scale
            shade.contentsRect = strip
            shade.opacity = geometry.shade > 0.002 ? Float(geometry.shade) : 0
        }
    }
}

struct PastePlacesImportPaperRepresentable: UIViewRepresentable {
    let time: Double
    let layout: PastePlacesImportLayout
    let faces: PastePlacesImportFaces?

    func makeUIView(context: Context) -> PastePlacesImportPaperView { PastePlacesImportPaperView() }

    func updateUIView(_ view: PastePlacesImportPaperView, context: Context) {
        view.update(time: time, layout: layout, faces: faces)
    }
}
