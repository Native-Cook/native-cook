import CoreGraphics
import simd

enum PullCordThemeLight {
    struct Range {
        let low: Double
        let high: Double
    }

    static func fieldRange(_ geometry: PullCordThemeSimulation.Geometry) -> Range {
        let bulb = PullCordThemeSimulation.lampPoint(
            geometry,
            angle: 0,
            localX: 0,
            localY: PullCordThemeTokens.Lamp.bulbY
        )
        let depth = PullCordThemeTokens.Lamp.depth * geometry.scale
        var low = Double.infinity
        var high = -Double.infinity
        for column in 0...40 {
            for row in 0...80 {
                let value = log(illuminance(
                    x: geometry.width * Double(column) / 40,
                    y: geometry.height * Double(row) / 80,
                    bulbX: bulb.x,
                    bulbY: bulb.y,
                    axisX: 0,
                    axisY: 1,
                    depth: depth
                ))
                low = min(low, value)
                high = max(high, value)
            }
        }
        return Range(low: low, high: high - 0.04 * (high - low))
    }

    static func lampColor(heat: Double) -> SIMD3<Float> {
        let heatStops = PullCordThemeTokens.Light.blackbodyHeat
        let colors = PullCordThemeTokens.Light.blackbody.map(rgb)
        let value = min(max(heat, 0), 1)
        for index in 0..<(heatStops.count - 1) where value <= heatStops[index + 1] {
            let amount = Float((value - heatStops[index]) / (heatStops[index + 1] - heatStops[index]))
            return simd_mix(colors[index], colors[index + 1], SIMD3(repeating: amount))
        }
        return colors[colors.count - 1]
    }

    static func brightness(_ heat: Double) -> Double {
        pow(min(max(heat, 0), 1), PullCordThemeTokens.Motion.Filament.power)
    }

    private static func smooth(_ low: Double, _ high: Double, _ value: Double) -> Double {
        let t = min(max((value - low) / (high - low), 0), 1)
        return t * t * (3 - 2 * t)
    }

    private static func directLight(
        x: Double,
        y: Double,
        bulbX: Double,
        bulbY: Double,
        axisX: Double,
        axisY: Double,
        depth: Double
    ) -> Double {
        let dx = x - bulbX
        let dy = y - bulbY
        let radius = sqrt(dx * dx + dy * dy + depth * depth)
        let cosine = (dx * axisX + dy * axisY) / radius
        let incidence = depth / radius
        let radians = Double.pi / 180
        let outer = cos((PullCordThemeTokens.Lamp.coneDegrees + PullCordThemeTokens.Lamp.penumbraDegrees) * radians)
        let inner = cos((PullCordThemeTokens.Lamp.coneDegrees - PullCordThemeTokens.Lamp.penumbraDegrees) * radians)
        return smooth(outer, inner, cosine) * incidence * incidence * incidence
    }

    private static func illuminance(
        x: Double,
        y: Double,
        bulbX: Double,
        bulbY: Double,
        axisX: Double,
        axisY: Double,
        depth: Double
    ) -> Double {
        let dx = x - bulbX
        let dy = y - bulbY
        let distance = 4 * depth * depth
        return directLight(
            x: x, y: y, bulbX: bulbX, bulbY: bulbY,
            axisX: axisX, axisY: axisY, depth: depth
        ) + PullCordThemeTokens.Light.bounce * distance / (dx * dx + dy * dy + distance)
    }

    private static func rgb(_ hex: UInt32) -> SIMD3<Float> {
        SIMD3(
            Float((hex >> 16) & 0xFF) / 255,
            Float((hex >> 8) & 0xFF) / 255,
            Float(hex & 0xFF) / 255
        )
    }
}
