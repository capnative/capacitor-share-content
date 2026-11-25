// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "CapnativeCapacitorShareContent",
    platforms: [.iOS(.v15)],
    products: [
        .library(
            name: "CapnativeCapacitorShareContent",
            targets: ["ShareContentPlugin"])
    ],
    dependencies: [
        .package(url: "https://github.com/ionic-team/capacitor-swift-pm.git", from: "8.0.0")
    ],
    targets: [
        .target(
            name: "ShareContentPlugin",
            dependencies: [
                .product(name: "Capacitor", package: "capacitor-swift-pm"),
                .product(name: "Cordova", package: "capacitor-swift-pm")
            ],
            path: "ios/Sources/ShareContentPlugin"),
        .testTarget(
            name: "ShareContentPluginTests",
            dependencies: ["ShareContentPlugin"],
            path: "ios/Tests/ShareContentPluginTests")
    ]
)
