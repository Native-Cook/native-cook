import UIKit

struct QuestMapUnrollGeometry {
    let size: CGSize
    let top: Double
    let bottom: Double
    let rect: CGRect
    let nodes: [CGPoint]
    init(_ size: CGSize, _ safe: UIEdgeInsets) {
        self.size = size
        top = safe.top
        bottom = safe.bottom
        let mw = size.width - 40
        let mh = (mw * 1300 / 900).rounded()
        let sc = min(1, (size.height - safe.top - 96 - safe.bottom - 120) / mh)
        let w = (mw * sc).rounded()
        let h = (mh * sc).rounded()
        let x = ((size.width - w) / 2).rounded()
        let y = safe.top + 96
        rect = CGRect(x: x, y: y, width: w, height: h)
        nodes = [(316.0,1160.0), (599,945), (319,750), (606,595), (318,412), (555,261)].map {
            CGPoint(x: x + $0.0 / 900 * w, y: y + $0.1 / 1300 * h)
        }
    }
    func along(_ f: Double) -> CGPoint {
        let i = max(0, min(nodes.count - 2, Int(floor(f))))
        let k = QuestMapUnrollMotion.clamp(f - Double(i))
        let a = nodes[i], b = nodes[i + 1]
        let bend = sin(k * .pi) * 18 * (i % 2 == 1 ? 1.0 : -1.0)
        return CGPoint(x: a.x + (b.x - a.x) * k + bend, y: a.y + (b.y - a.y) * k)
    }
}
