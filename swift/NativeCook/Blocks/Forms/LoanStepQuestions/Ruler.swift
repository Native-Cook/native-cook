import SwiftUI

private enum LoanStepQuestionsRulerStyle {
    static let tallEvery = 5
    static let tick = 14.0, tickTall = 22.0, tickWidth = 1.2
    static let markerWidth = 5.0, markerHeight = 40.0, fade = 44.0
}

/// A repeating strip needs only its remainder; the marker stays fixed over it.
enum LoanStepQuestionsRuler {
    static let tickStep = 10.0

    static func strip(left: Double, right: Double, centre: Double) -> Path {
        let count = Int(ceil(max(centre - left, right - centre) / tickStep)) + LoanStepQuestionsRulerStyle.tallEvery
        var path = Path()
        for index in -count...count {
            let half = (index.isMultiple(of: LoanStepQuestionsRulerStyle.tallEvery) ? LoanStepQuestionsRulerStyle.tickTall : LoanStepQuestionsRulerStyle.tick) / 2
            path.move(to: CGPoint(x: Double(index) * tickStep, y: -half))
            path.addLine(to: CGPoint(x: Double(index) * tickStep, y: half))
        }
        return path
    }

    static func draw(_ context: inout GraphicsContext, strip: Path, offset: Double, left: Double, right: Double, centre: Double, y: Double, tickColor: Color, markerColor: Color, background: Color) {
        let period = tickStep * Double(LoanStepQuestionsRulerStyle.tallEvery)
        let pulled = (offset.truncatingRemainder(dividingBy: period) + period).truncatingRemainder(dividingBy: period)
        var ticks = context
        ticks.clip(to: Path(CGRect(x: left, y: y - LoanStepQuestionsRulerStyle.tickTall, width: right - left, height: LoanStepQuestionsRulerStyle.tickTall * 2)))
        ticks.translateBy(x: centre - pulled, y: y)
        ticks.stroke(strip, with: .color(tickColor), style: StrokeStyle(lineWidth: LoanStepQuestionsRulerStyle.tickWidth, lineCap: .round))
        let fade = LoanStepQuestionsRulerStyle.fade
        let clear = background.opacity(0)
        context.fill(Path(CGRect(x: left, y: y - LoanStepQuestionsRulerStyle.tickTall, width: fade, height: LoanStepQuestionsRulerStyle.tickTall * 2)), with: .linearGradient(Gradient(colors: [background, clear]), startPoint: CGPoint(x: left, y: 0), endPoint: CGPoint(x: left + fade, y: 0)))
        context.fill(Path(CGRect(x: right - fade, y: y - LoanStepQuestionsRulerStyle.tickTall, width: fade, height: LoanStepQuestionsRulerStyle.tickTall * 2)), with: .linearGradient(Gradient(colors: [clear, background]), startPoint: CGPoint(x: right - fade, y: 0), endPoint: CGPoint(x: right, y: 0)))
        let marker = CGRect(x: centre - LoanStepQuestionsRulerStyle.markerWidth / 2, y: y - LoanStepQuestionsRulerStyle.markerHeight / 2, width: LoanStepQuestionsRulerStyle.markerWidth, height: LoanStepQuestionsRulerStyle.markerHeight)
        context.fill(Path(roundedRect: marker, cornerRadius: LoanStepQuestionsRulerStyle.markerWidth / 2), with: .color(markerColor))
    }
}
