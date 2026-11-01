import SwiftUI
import UIKit

struct PopUpQuestCardTouchSurface: UIViewRepresentable {
  let clock: PopUpQuestCardClock
  let layout: PopUpQuestCardMotion.Layout
  let pose: PopUpQuestCardMotion.Pose
  let quest: PopUpQuestCardResources.Quest
  func makeUIView(context: Context) -> PopUpQuestCardTouchView { PopUpQuestCardTouchView() }
  func updateUIView(_ view: PopUpQuestCardTouchView, context: Context) {
    view.clock = clock; view.layout = layout; view.open = pose.o > 0.5; view.opening = pose.o; view.quest = quest
    view.updateAccessibility()
  }
}
