// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "NoScrollCore",
    platforms: [.iOS(.v17), .macOS(.v13)],
    products: [.library(name: "NoScrollCore", targets: ["NoScrollCore"])],
    targets: [
        .target(name: "NoScrollCore"),
        .testTarget(name: "NoScrollCoreTests", dependencies: ["NoScrollCore"])
    ]
)
