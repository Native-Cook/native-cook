import SwiftUI
struct HourScrubSkyDemo:View {
 static let loop=12.0
 var body:some View {
  #if DEBUG
  HourScrubSky(autoPlay:!UserDefaults.standard.bool(forKey:"hourScrubSkyManual"))
  #else
  HourScrubSky(autoPlay:true)
  #endif
 }
}
#Preview { HourScrubSkyDemo() }
