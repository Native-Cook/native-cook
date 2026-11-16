import Foundation

struct CardVacuumSealLayout {
    let width: Double, height: Double, top: Double, bottom: Double
    var scale: Double { width/402 }
    var dy: Double { max(top,20)-62*scale }
    func y(_ design: Double) -> Double { dy+design*scale }
    var contentHeight: Double { max(height,y(825)+bottom) }
}
