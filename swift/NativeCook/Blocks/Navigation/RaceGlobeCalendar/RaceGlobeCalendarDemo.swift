import SwiftUI
struct RaceGlobeCalendarDemo:View {
 static let loop=10.5
 var body:some View {
  #if DEBUG
  RaceGlobeCalendar(autoPlay:!UserDefaults.standard.bool(forKey:"raceGlobeCalendarManual"))
  #else
  RaceGlobeCalendar(autoPlay:true)
  #endif
 }
}
#Preview { RaceGlobeCalendarDemo() }
