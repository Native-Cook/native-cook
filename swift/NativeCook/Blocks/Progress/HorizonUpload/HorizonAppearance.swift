import Foundation

/// Secondary cues all consume the same frame clock, including interrupted crossfades.
@MainActor final class HorizonAppearance {
  let shows = HorizonPhase.allCases.map { HorizonValue($0 == .idle ? 1 : 0) }
  let scales = HorizonPhase.allCases.map { HorizonValue($0 == .idle ? 1 : 0.85) }
  let labels = HorizonPhase.allCases.map { HorizonValue($0 == .idle ? 1 : 0) }
  let swap = HorizonValue(), shine = HorizonValue(-1), calm = HorizonValue()
  let paused = HorizonValue(), figure = HorizonValue(0.4)
  var rotation = 0.0
  private var phase = HorizonPhase.idle, retry = false, calming = false
  func advance(_ ms: Double, upload: HorizonUpload) {
    for v in shows { v.advance(ms) }; for v in scales { v.advance(ms) }; for v in labels { v.advance(ms) }
    swap.advance(ms); shine.advance(ms); calm.advance(ms); paused.advance(ms); figure.advance(ms)
    if upload.label != phase {
      phase = upload.label
      for (i, own) in HorizonPhase.allCases.enumerated() {
        let active = own == phase
        shows[i].timing(active ? 1 : 0, 220, .linear)
        labels[i].timing(active ? 1 : 0, upload.reduced ? 120 : 220)
        if upload.reduced { scales[i].set(1) }
        else if active && own == .done { scales[i].set(0.6); scales[i].spring(1, 420, 0.65) }
        else { scales[i].timing(active ? 1 : 0.85, 220, .linear) }
      }
      paused.timing(phase == .paused ? 1 : 0, 220)
      figure.timing(phase == .idle || phase == .stopped ? 0.4 : 1, 220)
      calming = false; shine.cancel(); calm.cancel()
      if phase == .done {
        shine.set(upload.reduced ? 1 : 0)
        if !upload.reduced { shine.timing(1, 300, .linear) }
      } else { shine.set(-1); calm.timing(0, 220) }
    }
    if shine.value == 1 && phase == .done && !calming { calming = true; calm.timing(1, 220) }
    if upload.retry != retry { retry = upload.retry; swap.timing(retry ? 1 : 0, 260, .linear) }
    if phase == .uploading && !upload.reduced { rotation = (rotation + ms / 900 * 360).truncatingRemainder(dividingBy: 360) }
  }
}
