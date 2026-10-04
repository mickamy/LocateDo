import CoreGraphics
import CoreText
import Foundation
import ImageIO
import UniformTypeIdentifiers

struct Appearance {
    let fileName: String
    let backgroundTop: CGColor
    let backgroundBottom: CGColor
    let pin: CGColor
}

let size: CGFloat = 1024
let colorSpace = CGColorSpace(name: CGColorSpace.sRGB)!

func color(_ hex: UInt32, alpha: CGFloat = 1) -> CGColor {
    let red = CGFloat((hex >> 16) & 0xFF) / 255
    let green = CGFloat((hex >> 8) & 0xFF) / 255
    let blue = CGFloat(hex & 0xFF) / 255
    return CGColor(srgbRed: red, green: green, blue: blue, alpha: alpha)
}

let appearances = [
    Appearance(
        fileName: "AppIcon",
        backgroundTop: color(0xFFFFFF),
        backgroundBottom: color(0xEEF3FA),
        pin: color(0x0A84FF)
    ),
    Appearance(
        fileName: "AppIcon-Dark",
        backgroundTop: color(0x3B9BFF),
        backgroundBottom: color(0x0062D6),
        pin: color(0xFFFFFF)
    ),
    Appearance(
        fileName: "AppIcon-Tinted",
        backgroundTop: color(0x3A3A3E),
        backgroundBottom: color(0x1A1A1D),
        pin: color(0xECECEC)
    ),
]

func pinPath(center: CGPoint, radius: CGFloat, tip: CGPoint) -> CGPath {
    let path = CGMutablePath()
    let phi = acos(radius / (center.y - tip.y))
    let start = -CGFloat.pi / 2 + phi
    let end = 3 * CGFloat.pi / 2 - phi
    path.move(to: tip)
    path.addLine(to: CGPoint(x: center.x + radius * cos(start), y: center.y + radius * sin(start)))
    path.addArc(center: center, radius: radius, startAngle: start, endAngle: end, clockwise: false)
    path.closeSubpath()
    return path
}

func checkPath(center: CGPoint, radius: CGFloat) -> CGPath {
    let path = CGMutablePath()
    path.move(to: CGPoint(x: center.x - 0.44 * radius, y: center.y + 0.02 * radius))
    path.addLine(to: CGPoint(x: center.x - 0.12 * radius, y: center.y - 0.3 * radius))
    path.addLine(to: CGPoint(x: center.x + 0.48 * radius, y: center.y + 0.38 * radius))
    return path
}

func makeContext(opaque: Bool) -> CGContext {
    let alphaInfo: CGImageAlphaInfo = opaque ? .noneSkipLast : .premultipliedLast
    guard let context = CGContext(
        data: nil,
        width: Int(size),
        height: Int(size),
        bitsPerComponent: 8,
        bytesPerRow: 0,
        space: colorSpace,
        bitmapInfo: alphaInfo.rawValue
    ) else {
        fatalError("could not create a bitmap context")
    }
    return context
}

func draw(_ appearance: Appearance, in context: CGContext) {
    let gradient = CGGradient(
        colorsSpace: colorSpace,
        colors: [appearance.backgroundTop, appearance.backgroundBottom] as CFArray,
        locations: [0, 1]
    )!
    context.drawLinearGradient(
        gradient,
        start: CGPoint(x: size / 2, y: size),
        end: CGPoint(x: size / 2, y: 0),
        options: []
    )

    let radius: CGFloat = 240
    let center = CGPoint(x: size / 2, y: 590)
    let tip = CGPoint(x: size / 2, y: center.y - 1.6 * radius)
    let pin = pinPath(center: center, radius: radius, tip: tip)

    context.saveGState()
    context.setShadow(offset: CGSize(width: 0, height: -14), blur: 44, color: color(0x000000, alpha: 0.22))
    context.beginTransparencyLayer(auxiliaryInfo: nil)

    context.setFillColor(appearance.pin)
    context.setStrokeColor(appearance.pin)
    context.setLineJoin(.round)
    context.setLineWidth(36)
    context.addPath(pin)
    context.drawPath(using: .fillStroke)

    context.setBlendMode(.clear)
    context.setLineWidth(0.22 * radius)
    context.setLineCap(.round)
    context.addPath(checkPath(center: center, radius: radius))
    context.strokePath()
    context.setBlendMode(.normal)

    context.endTransparencyLayer()
    context.restoreGState()
}

func drawDevBadge(in context: CGContext) {
    let pill = CGRect(x: (size - 300) / 2, y: 868, width: 300, height: 84)
    context.saveGState()
    context.setFillColor(color(0x1C1C1E, alpha: 0.78))
    context.addPath(CGPath(roundedRect: pill, cornerWidth: pill.height / 2, cornerHeight: pill.height / 2, transform: nil))
    context.fillPath()

    let kern: CGFloat = 8
    let attributes: [NSAttributedString.Key: Any] = [
        kCTFontAttributeName as NSAttributedString.Key: CTFontCreateWithName("HelveticaNeue-Bold" as CFString, 56, nil),
        kCTForegroundColorAttributeName as NSAttributedString.Key: color(0xFFFFFF),
        kCTKernAttributeName as NSAttributedString.Key: kern,
    ]
    let line = CTLineCreateWithAttributedString(NSAttributedString(string: "DEV", attributes: attributes))
    var ascent: CGFloat = 0
    var descent: CGFloat = 0
    let width = CGFloat(CTLineGetTypographicBounds(line, &ascent, &descent, nil)) - kern
    context.textPosition = CGPoint(x: pill.midX - width / 2, y: pill.midY - (ascent - descent) / 2)
    CTLineDraw(line, context)
    context.restoreGState()
}

func preview(of image: CGImage) -> CGImage {
    let context = makeContext(opaque: false)
    let rect = CGRect(x: 0, y: 0, width: size, height: size)
    let corner = size * 0.2237
    context.addPath(CGPath(roundedRect: rect, cornerWidth: corner, cornerHeight: corner, transform: nil))
    context.clip()
    context.draw(image, in: rect)
    return context.makeImage()!
}

func write(_ image: CGImage, to url: URL) {
    guard let destination = CGImageDestinationCreateWithURL(url as CFURL, UTType.png.identifier as CFString, 1, nil) else {
        fatalError("could not create \(url.path)")
    }
    CGImageDestinationAddImage(destination, image, nil)
    guard CGImageDestinationFinalize(destination) else {
        fatalError("could not write \(url.path)")
    }
}

let arguments = Array(CommandLine.arguments.dropFirst())
let flags = Set(arguments.filter { $0.hasPrefix("--") })
guard let outputPath = arguments.first(where: { !$0.hasPrefix("--") }) else {
    FileHandle.standardError.write(Data("usage: swift render.swift <output-dir> [--preview] [--dev]\n".utf8))
    exit(2)
}
let outputDirectory = URL(fileURLWithPath: outputPath)
let writesPreview = flags.contains("--preview")
let isDev = flags.contains("--dev")
try FileManager.default.createDirectory(at: outputDirectory, withIntermediateDirectories: true)

for appearance in appearances {
    let context = makeContext(opaque: true)
    draw(appearance, in: context)
    if isDev {
        drawDevBadge(in: context)
    }
    let image = context.makeImage()!
    var fileName = appearance.fileName
    if isDev {
        fileName = fileName.replacingOccurrences(of: "AppIcon", with: "AppIcon-Dev")
    }
    if writesPreview {
        write(preview(of: image), to: outputDirectory.appendingPathComponent("\(fileName)-preview.png"))
    } else {
        write(image, to: outputDirectory.appendingPathComponent("\(fileName).png"))
    }
    print("wrote \(fileName)")
}
