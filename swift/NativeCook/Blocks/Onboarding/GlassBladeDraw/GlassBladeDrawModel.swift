import Foundation
import simd

struct GlassBladeDrawLayout {
    let width: Double
    let height: Double
    let scale: Double
    let cardW: Double
    let cardH: Double
    let cardT: Double
    let cornerR: Double
    let heroBottomR: Double
    let sideBottomR: Double
    let pivotY: Double
    let ctaY: Double
    var ctaW: Double { width - 32 }

    init(width: Double, height: Double, bottomInset: Double) {
        self.width = width
        self.height = height
        scale = min(width / 402, height / 874)
        cardW = (132 * scale).rounded()
        cardH = (320 * scale).rounded()
        cardT = 7 * scale
        cornerR = 14 * scale
        heroBottomR = (388 * scale).rounded()
        sideBottomR = (306 * scale).rounded()
        ctaY = height - max(bottomInset, 8) - 8 - 56
        pivotY = ctaY - (48 * scale).rounded() + heroBottomR
    }
}

enum GlassBladeDrawModel {
    static let values = [60, 47, 49, 71, 38, 55, 62, 44, 53]
    static let hues = ["lime", "ember", "teal"]
    static let colours: [SIMD3<Double>] = [
        SIMD3(216.0 / 255, 242.0 / 255, 26.0 / 255),
        SIMD3(232.0 / 255, 72.0 / 255, 12.0 / 255),
        SIMD3(15.0 / 255, 224.0 / 255, 210.0 / 255)
    ]

    static func wrapped(_ index: Int) -> Int { (index % 9 + 9) % 9 }
    static func rounded(_ value: Double) -> Double { floor(value + 0.5) }

    static func smooth(_ low: Double, _ high: Double, _ value: Double) -> Double {
        let t = GlassBladeDrawTimeline.clamp((value - low) / (high - low))
        return t * t * (3 - 2 * t)
    }

    struct Pose {
        let phi: Double
        let yaw: Double
        let lift: Double
        let bob: Double
        let depth: Double
        let light: Double
        let opacity: Double
    }

    static func pose(_ u: Double, _ frame: GlassBladeDrawFrame, seconds: Double) -> Pose {
        let a = abs(u)
        let near = 1 - smooth(0.18, 0.85, a)
        let raised = 1 - smooth(0, 1.08, a)
        let opening = GlassBladeDrawTimeline.clamp(frame.intro)
        var yawMagnitude = 77.0
        if a > 1 && a <= 2 { yawMagnitude = 77 - 22 * smooth(1, 2, a) }
        else if a > 2 && a <= 3 { yawMagnitude = 55 - 13 * smooth(2, 3, a) }
        else if a > 3 { yawMagnitude = 42 - 10 * smooth(3, 4, a) }
        let turn = smooth(0, 0.48, a)
        let side = u < 0 ? 1.0 : -1.0
        let yaw = -42 * (1 - turn) + side * yawMagnitude * turn
        let idle = (1 - GlassBladeDrawTimeline.clamp(frame.still)) * opening
        let breath = sin(seconds * .pi * 2 / 5 + u * 1.13)
        return Pose(phi: u * 9.5 * .pi / 180 * opening,
                    yaw: (yaw * opening + 1.2 * breath * idle) * .pi / 180,
                    lift: raised * opening, bob: 1.5 * raised * breath * idle,
                    depth: (26 * raised + (24 - 52 * a) * (1 - raised)) * opening,
                    light: GlassBladeDrawTimeline.clamp(near * frame.charge, 0, 1.25) * opening,
                    opacity: 1 - smooth(4, 5, a))
    }

    static func uniforms(_ layout: GlassBladeDrawLayout, _ frame: GlassBladeDrawFrame,
                         seconds: Double, displayScale: Double) -> [SIMD4<Float>] {
        func lane(_ x: Double, _ y: Double, _ z: Double = 0, _ w: Double = 0) -> SIMD4<Float> {
            SIMD4(Float(x), Float(y), Float(z), Float(w))
        }
        func vector(_ v: SIMD3<Double>) -> SIMD4<Float> { lane(v.x, v.y, v.z) }
        var centres: [SIMD4<Float>] = []
        var axesX: [SIMD4<Float>] = []
        var axesY: [SIMD4<Float>] = []
        var normals: [SIMD4<Float>] = []
        var bounds: [SIMD4<Float>] = []
        var cards: [SIMD4<Float>] = []
        var colours: [SIMD4<Float>] = []
        let pivot = Int(rounded(frame.pos))
        // Tie-break by original index, matching the stable insertion sort in RN.
        let indices = Array((pivot - 5)...(pivot + 5)).sorted {
            let a = abs(frame.pos - Double($0))
            let b = abs(frame.pos - Double($1))
            return a == b ? $0 < $1 : a < b
        }
        let focal = 420 * layout.scale
        let eyeX = layout.width / 2
        let eyeY = layout.ctaY - layout.cardH * 0.44
        var fanTop = layout.height
        var heroColour = SIMD3<Double>(repeating: 0)
        var heroPower = 0.0
        for index in indices {
            let slot = frame.pos - Double(index)
            let pose = pose(slot, frame, seconds: seconds)
            let cardIndex = wrapped(index)
            let colour = Self.colours[cardIndex % 3]
            let radius = layout.sideBottomR + (layout.heroBottomR - layout.sideBottomR) * pose.lift
                + layout.cardH / 2 + pose.bob * layout.scale
            let cx = layout.width / 2 + sin(pose.phi) * radius
            let cy = layout.pivotY - cos(pose.phi) * radius
            let cz = pose.depth * layout.scale
            let yaw = pose.yaw - atan((cx - eyeX) / (focal - cz)) * (1 - pose.lift)
            let lean = pose.phi + 3 * .pi / 180 * pose.lift
            let ax = SIMD3(cos(lean) * cos(yaw), sin(lean) * cos(yaw), -sin(yaw))
            let ay = SIMD3(-sin(lean), cos(lean), 0)
            let az = SIMD3(cos(lean) * sin(yaw), sin(lean) * sin(yaw), cos(yaw))
            centres.append(lane(cx, cy, cz))
            axesX.append(vector(ax))
            axesY.append(vector(ay))
            normals.append(vector(az))
            var minX = layout.width * 10
            var minY = layout.height * 10
            var maxX = -minX
            var maxY = -minY
            for corner in 0..<8 {
                let x = (corner & 1 != 0 ? 0.5 : -0.5) * layout.cardW
                let y = (corner & 2 != 0 ? 0.5 : -0.5) * layout.cardH
                let z = (corner & 4 != 0 ? 0.5 : -0.5) * layout.cardT
                let persp = focal / (focal - cz - ax.z * x - az.z * z)
                let px = eyeX + (cx + ax.x * x + ay.x * y + az.x * z - eyeX) * persp
                let py = eyeY + (cy + ax.y * x + ay.y * y + az.y * z - eyeY) * persp
                minX = min(minX, px)
                minY = min(minY, py)
                maxX = max(maxX, px)
                maxY = max(maxY, py)
            }
            let margin = (pose.light > 0.01 ? 42.0 : 8.0) * layout.scale
            bounds.append(lane(minX - margin, minY - margin, maxX + margin, maxY + margin))
            fanTop = min(fanTop, minY - 8)
            cards.append(lane(Double(values[cardIndex]), pose.light, pose.opacity, slot))
            colours.append(vector(colour))
            let energy = pose.light * pose.light
            heroColour += colour * energy
            heroPower += energy
        }
        heroColour /= max(heroPower, 0.001)
        var result = [lane(layout.width, layout.height, displayScale), lane(eyeX, eyeY, focal),
                      lane(layout.cardW / 2, layout.cardH / 2, layout.cardT / 2, layout.cornerR)]
        result += centres + axesX + axesY + normals + bounds + cards + colours
        result.append(lane(heroColour.x, heroColour.y, heroColour.z, min(heroPower, 1.5)))
        result.append(lane(layout.width / 2, layout.ctaY + 28, layout.ctaW / 2, 28))
        result.append(lane(220.0 / 255, 1, 31.0 / 255))
        result.append(lane(frame.flare, frame.press, frame.ctaEnergy, frame.intro))
        result.append(lane(16, 1 / displayScale, layout.scale, fanTop))
        return result
    }
}
