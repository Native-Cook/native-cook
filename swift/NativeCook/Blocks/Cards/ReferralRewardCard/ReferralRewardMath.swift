import Foundation

/// Pure point-space arithmetic shared by the renderer and choreography.
enum ReferralRewardMath {
  static func clamp(_ x: Double, _ lo: Double = 0, _ hi: Double = 1) -> Double {
    min(max(x, lo), hi)
  }
  static func wrap(_ x: Double, _ n: Double = 7) -> Double {
    (x.truncatingRemainder(dividingBy: n) + n).truncatingRemainder(dividingBy: n)
  }
  static func index(_ x: Double) -> Int { Int(wrap(floor(x + 0.5))) }
  static func offset(_ i: Int, _ scroll: Double) -> Double { wrap(Double(i) - scroll + 3.5) - 3.5 }
  static func smooth(_ x: Double) -> Double {
    let c = clamp(x)
    return c * c * (3 - 2 * c)
  }
  static func ramp(_ x: Double, _ values: [Double]) -> Double {
    let a = clamp(x, 0, Double(values.count - 1))
    let i = min(Int(a), values.count - 2)
    return values[i] + (values[i + 1] - values[i]) * smooth(a - Double(i))
  }
  static func out(_ x: Double) -> Double {
    let u = 1 - clamp(x)
    return 1 - u * u * u * u
  }
  static func inOut(_ x: Double) -> Double {
    let c = clamp(x)
    return c < 0.5 ? 4 * c * c * c : 1 - pow(-2 * c + 2, 3) / 2
  }
  static func tier(_ count: Int) -> Int {
    (count >= 3 ? 1 : 0) + (count >= 5 ? 1 : 0) + (count >= 7 ? 1 : 0)
  }
  static func arc(_ count: Int) -> Double {
    count >= 7 ? 1 : count < 3 ? Double(count) / 3 : Double(count - (count < 5 ? 3 : 5)) / 2
  }
}

struct ReferralRewardPoint: Equatable {
  var x: Double
  var y: Double
}

struct ReferralRewardPath {
  struct Sample {
    var x: Double
    var y: Double
    var length: Double
  }
  let samples: [Sample]
  let launchLength: Double
  let orbitEnd: Double
  let length: Double
  let launch: Double
  let orbit: Double
  let sling: Double
  var total: Double { launch + orbit + sling }

  init(origin: ReferralRewardPoint, centre: ReferralRewardPoint) {
    var table = [Sample(x: origin.x, y: origin.y, length: 0)]
    table.reserveCapacity(361)
    func add(_ x: Double, _ y: Double) {
      let last = table[table.count - 1]
      table.append(Sample(x: x, y: y, length: last.length + hypot(x - last.x, y - last.y)))
    }
    func curve(_ a: ReferralRewardPoint, _ c: ReferralRewardPoint, _ b: ReferralRewardPoint) {
      for i in 1...120 {
        let u = Double(i) / 120
        let m = 1 - u
        add(
          m * m * a.x + 2 * m * u * c.x + u * u * b.x, m * m * a.y + 2 * m * u * c.y + u * u * b.y)
      }
    }
    let join = ReferralRewardPoint(x: centre.x - 42, y: centre.y)
    curve(origin, .init(x: join.x, y: join.y + max((origin.y - centre.y) * 0.5, 50.4)), join)
    launchLength = table.last!.length
    for i in 1...120 {
      let a = Double.pi + Double(i) / 120 * Double.pi * 2
      add(i == 120 ? join.x : centre.x + 42 * cos(a), i == 120 ? join.y : centre.y + 42 * sin(a))
    }
    orbitEnd = table.last!.length
    curve(join, .init(x: join.x, y: centre.y - 63), .init(x: centre.x, y: centre.y - 69))
    length = table.last!.length
    launch = 2 * launchLength / 1.75
    orbit = 2 * (orbitEnd - launchLength) / 1.55
    sling = 2 * (length - orbitEnd) / 1.1
    samples = table
  }
  func distance(at time: Double) -> Double {
    let t = ReferralRewardMath.clamp(time, 0, total)
    if t < launch { return 1.3 * t - 0.85 * t * t / (2 * launch) }
    if t < launch + orbit {
      let u = t - launch
      return launchLength + 0.45 * u + 0.65 * u * u / (2 * orbit)
    }
    let u = t - launch - orbit
    return orbitEnd + 1.1 * u - 1.1 * u * u / (2 * sling)
  }
  func at(_ u: Double) -> ReferralRewardPoint {
    let d = distance(at: u * total)
    var lo = 0
    var hi = samples.count - 1
    while hi - lo > 1 {
      let mid = (lo + hi) / 2
      if samples[mid].length < d { lo = mid } else { hi = mid }
    }
    let a = samples[lo]
    let b = samples[hi]
    let f = (d - a.length) / (b.length - a.length)
    return .init(x: a.x + (b.x - a.x) * f, y: a.y + (b.y - a.y) * f)
  }
}
