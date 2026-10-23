import SwiftUI

struct ParticleDispersion: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> ParticleDispersionCameraViewController {
        ParticleDispersionCameraViewController()
    }

    func updateUIViewController(_ controller: ParticleDispersionCameraViewController, context: Context) {}

    static func dismantleUIViewController(_ controller: ParticleDispersionCameraViewController, coordinator: ()) {
        controller.shutdown()
    }
}
