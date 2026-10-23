import SwiftUI
import UIKit

struct GlassFolderBurstPose {
    var center: CGPoint
    var rx = 0.0
    var ry = 0.0
    var rz = 0.0
    var sx = 1.0
    var sy = 1.0
    var opacity = 1.0
    var glow = 0.12
    var face = 1.0
    var landing = 0.0

    init(row: GlassFolderBurstRowState, index: Int, layout: GlassFolderBurstLayout,
         folder: GlassFolderBurstFolder, reduced: Bool, press: Double) {
        if reduced {
            center = CGPoint(x: layout.width / 2, y: layout.slot(index).midY)
            opacity = row.fade
            sx = press
            sy = press
            return
        }
        let t = GlassFolderBurstMath.clamp(row.p)
        let smooth = GlassFolderBurstMath.smooth
        let radians = Double.pi / 180
        let turn = index == 2 ? 62.0 : index == 6 ? 78.0 : index == 4 ? -85.0 : -55 - Double(index % 4) * 7
        let unwind = t < 0.7 ? 1 - 0.64 * smooth((t - 0.32) / 0.38) : 0.36 * (1 - smooth((t - 0.7) / 0.10))
        let flash = t < 0.20 ? 78 * (1 - smooth(t / 0.20)) : t < 0.38 ? 0 : t < 0.58 ? -22 * smooth((t - 0.38) / 0.20) : -22 * (1 - smooth((t - 0.58) / 0.22))
        let fold = 1 - smooth(t / 0.20)
        rx = (flash + (78 * fold - flash) * row.mode) * radians
        ry = (index % 2 == 1 ? -35 : 35) * (1 - smooth((t - 0.35) / 0.45)) * radians
        rz = turn * unwind * radians
        let outScale = t < 0.60 ? 0.42 + 0.22 * smooth(t / 0.45) : 0.64 + 0.36 * smooth((t - 0.60) / 0.40)
        let stretch = row.mode * min(1, abs(row.v) / 8) * sin(.pi * t)
        sx = outScale * press * (1 + 0.06 * stretch)
        sy = outScale * press * (1 - 0.08 * stretch)
        center = GlassFolderBurstMath.rowCenter(row.p, row.mode, index, layout, folder)
        opacity = smooth((row.p - 0.015) / 0.055)
        glow = 0.12 + 0.88 * (1 - smooth((t - 0.55) / 0.45))
        landing = row.flash
        face = smooth((abs(cos(rx) * cos(ry)) - 0.12) / 0.65)
    }

    /// Same projective matrix as Skia, transposed for Core Animation's column convention.
    func projection(width: Double, padding: Double) -> ProjectionTransform {
        let ax = cos(rx)
        let bx = sin(rx)
        let ay = cos(ry)
        let by = sin(ry)
        let az = cos(rz)
        let bz = sin(rz)
        let r00 = az * ay * sx
        let r01 = (az * by * bx - bz * ax) * sy
        let r10 = bz * ay * sx
        let r11 = (bz * by * bx + az * ax) * sy
        let r20 = -by * sx
        let r21 = ay * bx * sy
        let q0 = -r20 / 900
        let q1 = -r21 / 900
        let a = r00 + center.x * q0
        let b = r01 + center.x * q1
        let c = r10 + center.y * q0
        let d = r11 + center.y * q1
        let originX = width / 2 + padding
        let originY = 26 + padding
        var matrix = CATransform3DIdentity
        matrix.m11 = a
        matrix.m21 = b
        matrix.m41 = center.x - a * originX - b * originY
        matrix.m12 = c
        matrix.m22 = d
        matrix.m42 = center.y - c * originX - d * originY
        matrix.m14 = q0
        matrix.m24 = q1
        matrix.m44 = 1 - q0 * originX - q1 * originY
        return ProjectionTransform(matrix)
    }
}

struct GlassFolderBurstTrack: View {
    let index: Int
    let width: Double
    let pose: GlassFolderBurstPose
    let active: Double
    let clock: Double
    let reduced: Bool

    var body: some View {
        Canvas { context, _ in
            var row = context
            row.translateBy(x: 50, y: 50)
            let shape = Path(roundedRect: CGRect(x: 0, y: 0, width: width, height: 52), cornerRadius: 16)
            var bloom = row
            bloom.opacity = pose.glow * (0.48 + 0.27 * (pose.glow - 0.12) / 0.88)
            bloom.blendMode = .plusLighter
            bloom.addFilter(.blur(radius: 18 + 4 * (pose.glow - 0.12) / 0.88))
            bloom.fill(shape, with: .color(GlassFolderBurstAssets.color(0x28E07C)))
            let dark = Color(.sRGB, red: channel(16, 27),
                green: channel(24, 58), blue: channel(20, 42))
            row.fill(shape, with: .linearGradient(Gradient(stops: [
                .init(color: dark, location: 0), .init(color: dark, location: 0.28),
                .init(color: GlassFolderBurstAssets.color(0x28E07C), location: 1)
            ]), startPoint: CGPoint(x: 0, y: 26), endPoint: CGPoint(x: width, y: 26)))
            row.fill(shape, with: .linearGradient(Gradient(colors: [
                .white.opacity(0.14 * (1 + 0.6 * pose.landing)), .white.opacity(0)
            ]), startPoint: .zero, endPoint: CGPoint(x: 0, y: 20.8)))
            let rim = Color(.sRGB, red: channel(255, 140),
                green: 1, blue: channel(255, 192), opacity: 0.1 + 0.9 * active)
            let inner = Path(roundedRect: CGRect(x: 0.75, y: 0.75, width: width - 1.5, height: 50.5), cornerRadius: 15.25)
            row.stroke(inner, with: .color(rim), lineWidth: 1 + active * 0.5)
            var flash = row
            flash.opacity = pose.landing * 0.6
            flash.blendMode = .plusLighter
            flash.stroke(inner, with: .color(rim), lineWidth: 1 + active * 0.5)
            row.opacity = pose.face
            if GlassFolderBurstAssets.covers.indices.contains(index) {
                var art = row
                art.clip(to: Path(roundedRect: CGRect(x: 8, y: 8, width: 36, height: 36), cornerRadius: 8))
                art.draw(GlassFolderBurstAssets.covers[index], in: CGRect(x: 8, y: 8, width: 36, height: 36))
                art.stroke(Path(roundedRect: CGRect(x: 8.5, y: 8.5, width: 35, height: 35), cornerRadius: 7.5), with: .color(.white.opacity(0.12)), lineWidth: 1)
            }
            let seconds = GlassFolderBurstAssets.seconds[index]
            let duration = String(format: "%d:%02d", seconds / 60, seconds % 60)
            let font = UIFont(name: GlassFolderBurstAssets.regular, size: 11) ?? .systemFont(ofSize: 11)
            let timeWidth = (duration as NSString).size(withAttributes: [.font: font]).width
            let timeX = width - 16 - timeWidth
            let title = fitted(GlassFolderBurstAssets.titles[index], size: 14, width: timeX - 68)
            let artist = fitted(GlassFolderBurstAssets.artists[index], size: 11, width: timeX - 68)
            GlassFolderBurstAssets.text(row, title, x: 56, top: 9, height: 18, size: 14, color: GlassFolderBurstAssets.color(0xF2FFF7))
            GlassFolderBurstAssets.text(row, artist, x: 56, top: 28, height: 14, size: 11, color: GlassFolderBurstAssets.color(0xF2FFF7, opacity: 0.58))
            var time = row
            time.opacity *= 1 - active
            GlassFolderBurstAssets.text(time, duration, x: timeX, top: 0, height: 52, size: 11, color: GlassFolderBurstAssets.color(0x02180D, opacity: 0.72))
            var equaliser = row
            equaliser.opacity *= active
            for phase in 0..<4 {
                let height = reduced ? 8 + Double(phase) * 2 : 6 + 10 * (0.5 + 0.5 * sin(clock * .pi * 2 / 5 + Double(phase) * 1.7))
                equaliser.fill(Path(roundedRect: CGRect(x: width - 37 + Double(phase) * 6, y: 26 - height / 2, width: 3, height: height), cornerRadius: 1.5), with: .color(GlassFolderBurstAssets.color(0x02180D, opacity: 0.72)))
            }
        }
    }

    // Reanimated's RGB interpolation linearizes with gamma 2.2, then rounds back to bytes.
    private func channel(_ from: Double, _ to: Double) -> Double {
        let start = pow(from / 255, 2.2)
        let end = pow(to / 255, 2.2)
        let linear = start + (end - start) * GlassFolderBurstMath.clamp(active)
        return (pow(linear, 1 / 2.2) * 255).rounded() / 255
    }

    private func fitted(_ text: String, size: Double, width: Double) -> String {
        let font = UIFont(name: GlassFolderBurstAssets.regular, size: size) ?? .systemFont(ofSize: size)
        if (text as NSString).size(withAttributes: [.font: font]).width <= width { return text }
        var result = text
        while !result.isEmpty && ((result + "…") as NSString).size(withAttributes: [.font: font]).width > width {
            result.removeLast()
        }
        return result + "…"
    }
}
