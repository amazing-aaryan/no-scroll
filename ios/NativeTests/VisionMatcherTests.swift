import XCTest
import UIKit
import NoScrollCore
@testable import NoScrollIOS

final class VisionMatcherTests: XCTestCase {
    private let rects = [UnitRect(x: 0.05, y: 0.05, width: 0.2, height: 0.1),
                         UnitRect(x: 0.75, y: 0.4, width: 0.2, height: 0.1),
                         UnitRect(x: 0.4, y: 0.8, width: 0.2, height: 0.1)]
    private func image() -> CGImage {
        let format = UIGraphicsImageRendererFormat(); format.scale = 1
        return UIGraphicsImageRenderer(size: CGSize(width: 400, height: 860), format: format).image { ctx in
            UIColor.white.setFill(); ctx.fill(CGRect(x: 0, y: 0, width: 400, height: 860))
            for y in stride(from: 0, to: 860, by: 20) {
                for x in stride(from: 0, to: 400, by: 20) where (x + y) % 40 == 0 {
                    UIColor.black.setFill(); ctx.fill(CGRect(x: x, y: y, width: 12, height: 16))
                }
            }
        }.cgImage!
    }
    func testFeaturePrintRoundTripMatchesIdenticalImage() throws {
        let image = image()
        let p = try VisionMatcher.makeProfile(image: image, name: "Pattern", disposition: .block, rectangles: rects)
        let restored = try JSONDecoder().decode(ScreenProfile.self, from: JSONEncoder().encode(p))
        let matcher = try VisionMatcher(profiles: [restored])
        let distances = try matcher.distances(image: image)
        XCTAssertEqual(LandmarkClassifier.classify(profiles: [restored], distances: distances, aspectRatio: 400.0/860.0), .blocked(p.id))
        XCTAssertTrue(distances[0].values.allSatisfy { $0 < 0.001 })
    }
    func testInvalidCalibrationRejectedBeforeVisionRuns() {
        XCTAssertThrowsError(try VisionMatcher.makeProfile(image: image(), name: "Invalid", disposition: .block, rectangles: []))
        XCTAssertThrowsError(try VisionMatcher.makeProfile(image: image(), name: "Invalid", disposition: .block, rectangles: [rects[0], rects[0], rects[0]]))
    }
    func testCorruptFeaturePrintFailsClosed() {
        let p = ScreenProfile(name: "Bad", disposition: .block, aspectRatio: 0.46,
                              landmarks: rects.map { Landmark(rect: $0, featurePrint: Data([1,2,3])) })
        XCTAssertThrowsError(try VisionMatcher(profiles: [p]))
    }
}
