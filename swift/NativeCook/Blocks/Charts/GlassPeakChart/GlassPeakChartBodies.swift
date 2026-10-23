final class GlassPeakChartBodies {
  let orbU = GlassPeakChartBody()
  let orbR = GlassPeakChartBody()
  let orbGain = GlassPeakChartBody()
  let cardScale = GlassPeakChartBody()
  let tiltX = GlassPeakChartBody()
  let tiltY = GlassPeakChartBody()
  let poolLevel = GlassPeakChartBody()
  let poolHumpX = GlassPeakChartBody()
  let poolHump = GlassPeakChartBody()
  let poolSlope = GlassPeakChartBody()
  let poolGain = GlassPeakChartBody()
  let beam = GlassPeakChartBody()
  let numberScale = GlassPeakChartBody()
  let pillPress = GlassPeakChartBody()
  let refreshPress = GlassPeakChartBody()
  subscript(_ key: String) -> GlassPeakChartBody {
    switch key {
    case "orbU": return orbU
    case "orbR": return orbR
    case "orbGain": return orbGain
    case "cardScale": return cardScale
    case "tiltX": return tiltX
    case "tiltY": return tiltY
    case "poolLevel": return poolLevel
    case "poolHumpX": return poolHumpX
    case "poolHump": return poolHump
    case "poolSlope": return poolSlope
    case "poolGain": return poolGain
    case "beam": return beam
    case "numberScale": return numberScale
    case "pillPress": return pillPress
    case "refreshPress": return refreshPress
    default: preconditionFailure("Unknown spring")
    }
  }
}
