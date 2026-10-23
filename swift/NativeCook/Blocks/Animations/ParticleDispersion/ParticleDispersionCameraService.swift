import AVFoundation
import UIKit

final class ParticleDispersionCameraService: NSObject, AVCapturePhotoCaptureDelegate {
  let session = AVCaptureSession()
  private let queue = DispatchQueue(label: "particle.camera", qos: .userInitiated)
  private let output = AVCapturePhotoOutput()
  private var completion: ((Result<URL, Error>) -> Void)?
  private var configured = false
  private var device: AVCaptureDevice?
  private var wantsRunning = false

  static var isAvailable: Bool {
    AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back) != nil
  }

  func start(completion: @escaping (Result<Void, Error>) -> Void) {
    queue.async {
      self.wantsRunning = true
      do {
        if !self.configured {
          guard let camera = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back) else {
            throw self.error("No rear camera is available.")
          }
          let input = try AVCaptureDeviceInput(device: camera)
          self.session.beginConfiguration()
          self.session.sessionPreset = .photo
          guard self.session.canAddInput(input), self.session.canAddOutput(self.output) else {
            self.session.commitConfiguration()
            throw self.error("The camera could not be configured.")
          }
          self.session.addInput(input)
          self.session.addOutput(self.output)
          self.session.commitConfiguration()
          self.device = camera
          self.configured = true
        }
        if self.wantsRunning && !self.session.isRunning { self.session.startRunning() }
        DispatchQueue.main.async { completion(.success(())) }
      } catch { DispatchQueue.main.async { completion(.failure(error)) } }
    }
  }

  func stop() {
    queue.async {
      self.wantsRunning = false
      if self.session.isRunning { self.session.stopRunning() }
    }
  }

  func capture(completion: @escaping (Result<URL, Error>) -> Void) {
    queue.async {
      guard self.configured, self.session.isRunning, self.completion == nil else {
        DispatchQueue.main.async { completion(.failure(self.error("Wait for the camera to be ready."))) }
        return
      }
      self.completion = completion
      if let connection = self.output.connection(with: .video), connection.isVideoRotationAngleSupported(90) {
        connection.videoRotationAngle = 90
      }
      let settings = AVCapturePhotoSettings()
      settings.flashMode = .off
      self.output.capturePhoto(with: settings, delegate: self)
    }
  }

  func focus(at point: CGPoint) {
    queue.async {
      guard let device = self.device else { return }
      do {
        try device.lockForConfiguration()
        defer { device.unlockForConfiguration() }
        if device.isFocusPointOfInterestSupported && device.isFocusModeSupported(.autoFocus) {
          device.focusPointOfInterest = point
          device.focusMode = .autoFocus
        }
        if device.isExposurePointOfInterestSupported && device.isExposureModeSupported(.continuousAutoExposure) {
          device.exposurePointOfInterest = point
          device.exposureMode = .continuousAutoExposure
        }
      } catch { NSLog("[ParticleCamera] Focus unavailable: %@", error.localizedDescription) }
    }
  }

  func zoom(to factor: CGFloat) {
    queue.async {
      guard let device = self.device else { return }
      do {
        try device.lockForConfiguration()
        defer { device.unlockForConfiguration() }
        device.videoZoomFactor = min(max(factor, device.minAvailableVideoZoomFactor), min(device.maxAvailableVideoZoomFactor, 8))
      } catch { NSLog("[ParticleCamera] Zoom unavailable: %@", error.localizedDescription) }
    }
  }

  func photoOutput(_ output: AVCapturePhotoOutput, didFinishProcessingPhoto photo: AVCapturePhoto, error: Error?) {
    queue.async {
      let callback = self.completion
      self.completion = nil
      let result: Result<URL, Error>
      do {
        if let error { throw error }
        guard let data = photo.fileDataRepresentation() else { throw self.error("The camera returned an empty photo.") }
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString + ".jpg")
        try data.write(to: url)
        result = .success(url)
      } catch { result = .failure(error) }
      DispatchQueue.main.async { callback?(result) }
    }
  }

  private func error(_ message: String) -> NSError {
    NSError(domain: "Camera", code: 1, userInfo: [NSLocalizedDescriptionKey: message])
  }
}
