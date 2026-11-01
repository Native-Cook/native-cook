import UIKit

extension PopUpQuestCardResources {
  func shadows(_ active: Int) -> UIImage {
    cached("shadow-\(active)", CGSize(width: 1200, height: 900)) { cv in
      cv.setFillColor(Self.color("#1D2A3A").cgColor)
      for h in quests[active].hinges.dropFirst(2) where h < 700 {
        cv.fillEllipse(in: CGRect(x: 250, y: h + 74, width: 700, height: 34))
      }
    }
  }
}
