import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct LoanStepQuestionsDemo: View {
    var body: some View {
        LoanStepQuestions(autoPlay: true)
    }
}

#Preview {
    LoanStepQuestionsDemo()
}
