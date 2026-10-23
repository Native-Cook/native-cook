// Plain frame values. Both the shader and native transforms read this snapshot.
struct AuraDrive {
  var phase = AuraPhase.rest
  var chosen = -1
  var formed = 0.0
  var leaving = -1
  var unformed = 0.0
  var pullX = 0.0
  var pullY = 0.0
  var amount = AuraFlow.pull.amountDefault
  var clock = 0.0
  var fromX = 0.0
  var fromY = 0.0
  var wide = 0.0
  var nudge = -1.0
  var weight = AuraFlow.pull.amountDefault / AuraFlow.pull.amountMax
  var entered = 0.0
  var press = SIMD4<Double>.zero
}
