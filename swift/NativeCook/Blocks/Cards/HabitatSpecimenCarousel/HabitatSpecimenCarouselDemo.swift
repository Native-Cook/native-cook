import SwiftUI

struct HabitatSpecimenCarouselDemo: View {
    /// `-habitatTime <seconds>` freezes the screen at that second of the loop; `-habitatReduced YES`
    /// shows the Reduced Motion state; `-habitatManual YES` leaves the demo to the finger.
    private var previewTime: Double? {
        guard let value = UserDefaults.standard.object(forKey: "habitatTime") else { return nil }
        if let number = value as? NSNumber { return number.doubleValue }
        return Double(String(describing: value))
    }

    var body: some View {
        HabitatSpecimenCarousel(
            autoPlay: !UserDefaults.standard.bool(forKey: "habitatManual"),
            previewTime: previewTime,
            previewReducedMotion: UserDefaults.standard.bool(forKey: "habitatReduced")
        )
    }
}

#Preview {
    HabitatSpecimenCarouselDemo()
}
