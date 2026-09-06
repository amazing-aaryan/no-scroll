import Foundation

/// Rectangles use a top-left origin after image orientation has been applied.
public struct UnitRect: Codable, Equatable, Sendable {
    public var x, y, width, height: Double
    public init(x: Double, y: Double, width: Double, height: Double) {
        self.x = x; self.y = y; self.width = width; self.height = height
    }
    public var isValidLandmark: Bool {
        [x, y, width, height].allSatisfy(\.isFinite) &&
        x >= 0 && y >= 0 && width >= 0.02 && height >= 0.02 &&
        x + width <= 1.000001 && y + height <= 1.000001 && width * height <= 0.12
    }
    public func overlaps(_ other: UnitRect) -> Bool {
        min(x + width, other.x + other.width) > max(x, other.x) &&
        min(y + height, other.y + other.height) > max(y, other.y)
    }
}

public enum ScreenDisposition: String, Codable, Sendable { case block, allow }

public struct Landmark: Codable, Equatable, Sendable {
    public var rect: UnitRect
    /// A secure-coded VNFeaturePrintObservation, never a screenshot or OCR text.
    public var featurePrint: Data
    public var maximumDistance: Double
    public init(rect: UnitRect, featurePrint: Data, maximumDistance: Double = 0.12) {
        self.rect = rect; self.featurePrint = featurePrint; self.maximumDistance = maximumDistance
    }
    public var isValid: Bool {
        rect.isValidLandmark && !featurePrint.isEmpty && featurePrint.count <= 65_536 &&
        maximumDistance.isFinite && maximumDistance > 0 && maximumDistance <= 0.30
    }
}

public struct ScreenProfile: Codable, Equatable, Identifiable, Sendable {
    public var id: UUID
    public var name: String
    public var disposition: ScreenDisposition
    public var aspectRatio: Double
    public var landmarks: [Landmark]
    public init(id: UUID = UUID(), name: String, disposition: ScreenDisposition,
                aspectRatio: Double, landmarks: [Landmark]) {
        self.id = id; self.name = name; self.disposition = disposition
        self.aspectRatio = aspectRatio; self.landmarks = landmarks
    }
    public var isValid: Bool {
        guard !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, name.count <= 40,
              aspectRatio.isFinite, (0.35...0.8).contains(aspectRatio), landmarks.count == 3,
              landmarks.allSatisfy(\.isValid) else { return false }
        for i in landmarks.indices {
            for j in landmarks.indices where j > i {
                if landmarks[i].rect.overlaps(landmarks[j].rect) { return false }
            }
        }
        return true
    }
}

public struct ProfileArchive: Codable, Equatable, Sendable {
    public var schemaVersion = 1
    public var visionRevision = 2
    public var revision: UUID
    public var profiles: [ScreenProfile]
    public init(revision: UUID = UUID(), profiles: [ScreenProfile] = []) {
        self.revision = revision; self.profiles = profiles
    }
    public var isValid: Bool {
        schemaVersion == 1 && visionRevision == 2 && profiles.count <= 8 &&
        Set(profiles.map(\.id)).count == profiles.count && profiles.allSatisfy(\.isValid)
    }
    public var readyForIntervention: Bool {
        isValid && profiles.contains { $0.disposition == .block } && profiles.contains { $0.disposition == .allow }
    }
}

public struct ProfileDistances: Equatable, Sendable {
    public let profileID: UUID
    public let values: [Double]
    public init(profileID: UUID, values: [Double]) { self.profileID = profileID; self.values = values }
}

public enum ScreenMatch: Equatable, Sendable {
    case unknown, allowed(UUID), blocked(UUID)
    public var summary: String {
        switch self { case .unknown: return "unknown"; case .allowed: return "allowed"; case .blocked: return "blocked" }
    }
}

public enum LandmarkClassifier {
    public static func classify(profiles: [ScreenProfile], distances: [ProfileDistances], aspectRatio: Double) -> ScreenMatch {
        guard aspectRatio.isFinite, (0.35...0.8).contains(aspectRatio),
              Set(distances.map(\.profileID)).count == distances.count,
              ProfileArchive(profiles: profiles).isValid else { return .unknown }
        let measurements = Dictionary(uniqueKeysWithValues: distances.map { ($0.profileID, $0.values) })
        let matches = profiles.filter { profile in
            guard abs(profile.aspectRatio - aspectRatio) <= 0.025,
                  let values = measurements[profile.id], values.count == profile.landmarks.count else { return false }
            return zip(values, profile.landmarks).allSatisfy { value, landmark in
                value.isFinite && value >= 0 && value <= landmark.maximumDistance
            }
        }
        // An allowed mapping wins regardless of which profile has the smaller distance.
        if let allowed = matches.first(where: { $0.disposition == .allow }) { return .allowed(allowed.id) }
        let blocked = matches.filter { $0.disposition == .block }
        return blocked.count == 1 ? .blocked(blocked[0].id) : .unknown
    }
}
