import Foundation
import XCTest
@testable import NoScrollCore

func profile(_ disposition: ScreenDisposition = .block, name: String = "Reels") -> ScreenProfile {
    ScreenProfile(name: name, disposition: disposition, aspectRatio: 0.462,
        landmarks: [
            Landmark(rect: UnitRect(x: 0.05, y: 0.06, width: 0.20, height: 0.05), featurePrint: Data([1])),
            Landmark(rect: UnitRect(x: 0.87, y: 0.45, width: 0.08, height: 0.07), featurePrint: Data([2])),
            Landmark(rect: UnitRect(x: 0.46, y: 0.92, width: 0.08, height: 0.05), featurePrint: Data([3]))
        ])
}

final class MappingTests: XCTestCase {
    func testCompleteLandmarkMatchBlocks() {
        let p = profile()
        XCTAssertEqual(LandmarkClassifier.classify(profiles: [p], distances: [.init(profileID: p.id, values: [0.01, 0.02, 0.03])], aspectRatio: p.aspectRatio), .blocked(p.id))
    }
    func testAllowedProfileVetoesEvenPerfectBlockedMatch() {
        let blocked = profile(), allowed = profile(.allow, name: "Messages")
        XCTAssertEqual(LandmarkClassifier.classify(profiles: [blocked, allowed], distances: [
            .init(profileID: blocked.id, values: [0, 0, 0]), .init(profileID: allowed.id, values: [0.03, 0.03, 0.03])
        ], aspectRatio: 0.462), .allowed(allowed.id))
    }
    func testPartialOrWeakLandmarksAbstain() {
        let p = profile()
        for values in [[0.01], [0.01, 0.01], [0, 0, 0.121], [0, 0, 0, 0]] {
            XCTAssertEqual(LandmarkClassifier.classify(profiles: [p], distances: [.init(profileID: p.id, values: values)], aspectRatio: 0.462), .unknown)
        }
    }
    func testNonfiniteAndNegativeDistancesAbstain() {
        let p = profile()
        for invalid in [Double.nan, .infinity, -.infinity, -0.001] {
            XCTAssertEqual(LandmarkClassifier.classify(profiles: [p], distances: [.init(profileID: p.id, values: [0, invalid, 0])], aspectRatio: 0.462), .unknown)
        }
    }
    func testWrongAspectOrLandscapeAbstains() {
        let p = profile()
        for aspect in [1.8, 0.60, 0, Double.nan] {
            XCTAssertEqual(LandmarkClassifier.classify(profiles: [p], distances: [.init(profileID: p.id, values: [0, 0, 0])], aspectRatio: aspect), .unknown)
        }
    }
    func testTwoBlockedProfilesMatchingIsAmbiguous() {
        let a = profile(), b = profile()
        XCTAssertEqual(LandmarkClassifier.classify(profiles: [a,b], distances: [
            .init(profileID: a.id, values: [0,0,0]), .init(profileID: b.id, values: [0,0,0])
        ], aspectRatio: 0.462), .unknown)
    }
    func testDuplicateDistanceEntriesAbstain() {
        let p = profile(), d = ProfileDistances(profileID: UUID(), values: [0,0,0])
        XCTAssertEqual(LandmarkClassifier.classify(profiles: [p], distances: [d,d], aspectRatio: 0.462), .unknown)
        let own = ProfileDistances(profileID: p.id, values: [0,0,0])
        XCTAssertEqual(LandmarkClassifier.classify(profiles: [p], distances: [own,own], aspectRatio: 0.462), .unknown)
    }
    func testMappingRequiresThreeSeparateSmallRegions() {
        var p = profile()
        XCTAssertTrue(p.isValid)
        p.landmarks[1].rect = p.landmarks[0].rect
        XCTAssertFalse(p.isValid)
        p = profile(); p.landmarks[0].rect = .init(x: 0, y: 0, width: 1, height: 1)
        XCTAssertFalse(p.isValid)
        p = profile(); p.landmarks.removeLast(); XCTAssertFalse(p.isValid)
    }
    func testBadGeometryDescriptorOrThresholdRejected() {
        for r in [UnitRect(x: -0.1, y: 0.1, width: 0.1, height: 0.1),
                  UnitRect(x: 0.9, y: 0.1, width: 0.2, height: 0.1),
                  UnitRect(x: .nan, y: 0.1, width: 0.1, height: 0.1)] {
            var p = profile(); p.landmarks[0].rect = r; XCTAssertFalse(p.isValid)
        }
        var p = profile(); p.landmarks[0].featurePrint = Data(); XCTAssertFalse(p.isValid)
        p = profile(); p.landmarks[0].maximumDistance = 0.99; XCTAssertFalse(p.isValid)
        p = profile(); p.landmarks[0].featurePrint = Data(repeating: 0, count: 65_537); XCTAssertFalse(p.isValid)
    }
    func testProfileRoundTripContainsDescriptorsNotSourceImage() throws {
        let p = profile()
        let encoded = try JSONEncoder().encode(p)
        XCTAssertEqual(try JSONDecoder().decode(ScreenProfile.self, from: encoded), p)
    }
    func testInterventionReadinessRequiresPositiveAndNegativeMaps() {
        XCTAssertFalse(ProfileArchive(profiles: [profile()]).readyForIntervention)
        XCTAssertTrue(ProfileArchive(profiles: [profile(), profile(.allow)]).readyForIntervention)
        var invalid = profile(.allow); invalid.landmarks = []
        XCTAssertFalse(ProfileArchive(profiles: [profile(), invalid]).readyForIntervention)
    }
}
