import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct AccordionLettersDemo: View {
    var body: some View {
        AccordionLetters(autoPlay: true)
    }
}

#Preview {
    AccordionLettersDemo()
}
