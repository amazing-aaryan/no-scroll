import Foundation
import CoreGraphics
import CoreVideo
import ImageIO
import Vision
import NoScrollCore

/// Feature prints remain on-device. No image, text, network, or logging side effects.
final class VisionMatcher {
    private let profiles: [ScreenProfile]
    private let referencePrints: [UUID: [VNFeaturePrintObservation]]
    init(profiles: [ScreenProfile]) throws {
        guard ProfileArchive(profiles: profiles).isValid else { throw NativeError.invalidMapping }
        self.profiles = profiles
        var decoded: [UUID: [VNFeaturePrintObservation]] = [:]
        for profile in profiles {
            decoded[profile.id] = try profile.landmarks.map { landmark in
                guard let value = try NSKeyedUnarchiver.unarchivedObject(ofClass: VNFeaturePrintObservation.self,
                                                                        from: landmark.featurePrint) else {
                    throw NativeError.invalidMapping
                }
                guard value.requestRevision == 2 else { throw NativeError.invalidMapping }
                return value
            }
        }
        referencePrints = decoded
    }

    static func makeProfile(image: CGImage, name: String, disposition: ScreenDisposition,
                            rectangles: [UnitRect]) throws -> ScreenProfile {
        var profile = ScreenProfile(name: name, disposition: disposition,
                                    aspectRatio: Double(image.width) / Double(image.height),
                                    landmarks: rectangles.map { Landmark(rect: $0, featurePrint: Data([0])) })
        guard profile.isValid else { throw NativeError.invalidMapping }
        let handler = VNImageRequestHandler(cgImage: image, orientation: .up, options: [:])
        profile.landmarks = try rectangles.map { rect in
            let observation = try featurePrint(handler: handler, rect: rect)
            return Landmark(rect: rect, featurePrint: try NSKeyedArchiver.archivedData(withRootObject: observation,
                                                                                      requiringSecureCoding: true))
        }
        guard profile.isValid else { throw NativeError.invalidMapping }
        return profile
    }

    func distances(image: CGImage) throws -> [ProfileDistances] {
        try evaluate(handler: VNImageRequestHandler(cgImage: image, orientation: .up, options: [:]),
                     aspectRatio: Double(image.width) / Double(image.height))
    }

    func distances(pixelBuffer: CVPixelBuffer, orientation: CGImagePropertyOrientation,
                   aspectRatio: Double) throws -> [ProfileDistances] {
        try evaluate(handler: VNImageRequestHandler(cvPixelBuffer: pixelBuffer, orientation: orientation, options: [:]),
                     aspectRatio: aspectRatio)
    }

    private func evaluate(handler: VNImageRequestHandler, aspectRatio: Double) throws -> [ProfileDistances] {
        var result: [ProfileDistances] = []
        for profile in profiles where abs(profile.aspectRatio - aspectRatio) <= 0.025 {
            guard let references = referencePrints[profile.id] else { throw NativeError.invalidMapping }
            var values: [Double] = []
            for (landmark, reference) in zip(profile.landmarks, references) {
                let distance: Double = try autoreleasepool {
                    let actual = try Self.featurePrint(handler: handler, rect: landmark.rect)
                    var value: Float = 0
                    try actual.computeDistance(&value, to: reference)
                    return Double(value)
                }
                values.append(distance)
                // Still evaluate other profiles: an allowed profile must be able to veto a block.
            }
            result.append(ProfileDistances(profileID: profile.id, values: values))
        }
        return result
    }

    private static func featurePrint(handler: VNImageRequestHandler, rect: UnitRect) throws -> VNFeaturePrintObservation {
        let request = VNGenerateImageFeaturePrintRequest()
        request.revision = VNGenerateImageFeaturePrintRequestRevision2
        request.imageCropAndScaleOption = .scaleFit
        request.preferBackgroundProcessing = true
        // Vision uses a bottom-left origin; calibration UI uses a top-left origin.
        request.regionOfInterest = CGRect(x: rect.x, y: 1 - rect.y - rect.height, width: rect.width, height: rect.height)
        try handler.perform([request])
        guard let result = request.results?.first else { throw NativeError.visionUnavailable }
        return result
    }
}
