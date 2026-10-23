import Metal
import Observation

/// The main actor owns the cache and its loading state. Only the worker builds
/// resources; it hands back one immutable bundle after BOTH pipelines are ready.
@MainActor @Observable final class AuraPipelineStore {
  static let shared = AuraPipelineStore()
  private(set) var resources: AuraMetalResources?
  private var started = false
  private let compiler = DispatchQueue(label: "Aura.pipeline-compiler", qos: .userInitiated)

  private init() {}

  func prepare() {
    guard !started else { return }
    started = true
    compiler.async {
      do {
        let resources = try AuraMetalResources()
        DispatchQueue.main.async { self.resources = resources }
      } catch {
        // A failed build leaves the demo's plain page color visible.
        print("Aura material unavailable: \(error)")
      }
    }
  }
}
