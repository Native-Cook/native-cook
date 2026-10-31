import Foundation

// Values and cubic segments translated directly from the RN worklet script.
enum StormCloudWeatherMotion {
    static let loop = 9.0
    static let morph = 0.8
    struct Weather {
        var cloud: [Double]
        var moon, sun, bolt, rain, puff: Double
    }
    struct Copy {
        let label: String
        let temp, wind, humidity: Int
        let sky: String
    }
    static let states: [Weather] = [
        .init(cloud: [0.21, 0.22, 0.26], moon: 1, sun: 0, bolt: 1, rain: 1, puff: 0),
        .init(cloud: [0.86, 0.89, 0.95], moon: 0, sun: 0, bolt: 0, rain: 1, puff: 0.6),
        .init(cloud: [0.95, 0.96, 0.99], moon: 0, sun: 1, bolt: 0, rain: 0, puff: 1)
    ]
    static let copy: [Copy] = [
        .init(label: "Now", temp: 26, wind: 14, humidity: 82, sky: "Thunderstorm"),
        .init(label: "2:00", temp: 28, wind: 9, humidity: 74, sky: "Showers"),
        .init(label: "4:00", temp: 34, wind: 6, humidity: 52, sky: "Partly sunny")
    ]
    static func mix(_ a: Double, _ b: Double, _ k: Double) -> Double { a + (b-a)*k }
    static func ease(_ k: Double) -> Double {
        let x = min(1, max(0, k))
        return x < 0.5 ? 4*x*x*x : 1-pow(-2*x+2, 3)/2
    }
    struct Frame {
        var from = 0, to = 0
        var m = 1.0, yaw = 0.0, time = 0.0, phase = 0.0, flash = 0.0
        var weather: Weather { StormCloudWeatherMotion.blend(from, to, m) }
        func number(_ key: KeyPath<Copy, Int>) -> Int {
            Int(floor(StormCloudWeatherMotion.mix(Double(copy[from][keyPath: key]), Double(copy[to][keyPath: key]), m)+0.5))
        }
    }
    static func blend(_ from: Int, _ to: Int, _ k: Double) -> Weather {
        let a = states[from], b = states[to]
        return .init(cloud: zip(a.cloud,b.cloud).map { mix($0,$1,k) },
            moon: mix(a.moon,b.moon,k), sun: mix(a.sun,b.sun,k), bolt: mix(a.bolt,b.bolt,k),
            rain: mix(a.rain,b.rain,k), puff: mix(a.puff,b.puff,k))
    }
    static func at(_ t: Double, _ reduced: Bool = false) -> Frame {
        var f = Frame()
        if t >= 8 { f.from = 2; f.to = 0; f.m = ease((t-8)/morph) }
        else if t >= 5.6 { f.from = 1; f.to = 2; f.m = ease((t-5.6)/morph) }
        else if t >= 2.6 { f.from = 0; f.to = 1; f.m = ease((t-2.6)/morph) }
        f.time = t; f.phase = t
        f.yaw = reduced ? 0 : 0.32*sin(2 * .pi*t/loop)
        f.flash = reduced ? 0 : exp(-pow((t-1.2)/0.06,2))+0.6*exp(-pow((t-1.38)/0.05,2))
        return f
    }
}
