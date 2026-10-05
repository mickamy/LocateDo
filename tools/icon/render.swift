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

func makeContext(opaque: Bool, pixels: Int = Int(size)) -> CGContext {
    let alphaInfo: CGImageAlphaInfo = opaque ? .noneSkipLast : .premultipliedLast
    guard let context = CGContext(
        data: nil,
        width: pixels,
        height: pixels,
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

    context.saveGState()
    context.setShadow(offset: CGSize(width: 0, height: -14), blur: 44, color: color(0x000000, alpha: 0.22))
    drawPin(appearance.pin, in: context)
    context.restoreGState()
}

let pinRadius: CGFloat = 240
let pinCenter = CGPoint(x: size / 2, y: 590)
let pinTip = CGPoint(x: size / 2, y: pinCenter.y - 1.6 * pinRadius)
let pinOutline: CGFloat = 36
let checkWidth = 0.22 * pinRadius

func drawPin(_ fill: CGColor, in context: CGContext) {
    context.beginTransparencyLayer(auxiliaryInfo: nil)

    context.setFillColor(fill)
    context.setStrokeColor(fill)
    context.setLineJoin(.round)
    context.setLineWidth(pinOutline)
    context.addPath(pinPath(center: pinCenter, radius: pinRadius, tip: pinTip))
    context.drawPath(using: .fillStroke)

    context.setBlendMode(.clear)
    context.setLineWidth(checkWidth)
    context.setLineCap(.round)
    context.addPath(checkPath(center: pinCenter, radius: pinRadius))
    context.strokePath()
    context.setBlendMode(.normal)

    context.endTransparencyLayer()
}

func drawBadge(_ text: String, in context: CGContext) {
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
    let line = CTLineCreateWithAttributedString(NSAttributedString(string: text, attributes: attributes))
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

// A square around the pin, so the favicon is the pin alone at the largest size that fits.
var faviconBox: CGRect {
    let top = pinCenter.y + pinRadius + pinOutline / 2
    let bottom = pinTip.y - pinOutline / 2
    let side = (top - bottom) * 1.04
    return CGRect(x: pinCenter.x - side / 2, y: (top + bottom) / 2 - side / 2, width: side, height: side)
}

func scaled(pixels: Int, opaque: Bool, box: CGRect, drawing: (CGContext) -> Void) -> CGImage {
    let context = makeContext(opaque: opaque, pixels: pixels)
    context.interpolationQuality = .high
    let scale = CGFloat(pixels) / box.width
    context.scaleBy(x: scale, y: scale)
    context.translateBy(x: -box.minX, y: -box.minY)
    drawing(context)
    return context.makeImage()!
}

func pngData(_ image: CGImage) -> Data {
    let data = NSMutableData()
    guard let destination = CGImageDestinationCreateWithData(data, UTType.png.identifier as CFString, 1, nil) else {
        fatalError("could not encode a PNG")
    }
    CGImageDestinationAddImage(destination, image, nil)
    guard CGImageDestinationFinalize(destination) else {
        fatalError("could not encode a PNG")
    }
    return data as Data
}

// An ICO file holding a single PNG image.
func icoData(png: Data, pixels: Int) -> Data {
    var data = Data()
    func append(_ value: some FixedWidthInteger) {
        withUnsafeBytes(of: value.littleEndian) { data.append(contentsOf: $0) }
    }
    append(UInt16(0))
    append(UInt16(1))
    append(UInt16(1))
    append(UInt8(pixels))
    append(UInt8(pixels))
    append(UInt8(0))
    append(UInt8(0))
    append(UInt16(1))
    append(UInt16(32))
    append(UInt32(png.count))
    append(UInt32(6 + 16))
    data.append(png)
    return data
}

func number(_ value: CGFloat) -> String {
    String(format: "%.2f", Double(value))
}

// The same geometry as drawPin, flipped to SVG's y-down coordinates.
func faviconSVG(fill: String) -> String {
    let box = faviconBox
    func svg(_ point: CGPoint) -> String {
        "\(number(point.x)) \(number(box.minY + box.maxY - point.y))"
    }
    let phi = acos(pinRadius / (pinCenter.y - pinTip.y))
    let start = -CGFloat.pi / 2 + phi
    let end = 3 * CGFloat.pi / 2 - phi
    let right = CGPoint(x: pinCenter.x + pinRadius * cos(start), y: pinCenter.y + pinRadius * sin(start))
    let left = CGPoint(x: pinCenter.x + pinRadius * cos(end), y: pinCenter.y + pinRadius * sin(end))
    let pin = "M\(svg(pinTip)) L\(svg(right)) A\(number(pinRadius)) \(number(pinRadius)) 0 1 0 \(svg(left)) Z"
    let check = checkPath(center: pinCenter, radius: pinRadius)
    var points: [CGPoint] = []
    check.applyWithBlock { element in
        points.append(element.pointee.points[0])
    }
    let checkData = points.enumerated()
        .map { offset, point in "\(offset == 0 ? "M" : "L")\(svg(point))" }
        .joined(separator: " ")
    let view = "\(number(box.minX)) \(number(box.minY)) \(number(box.width)) \(number(box.height))"
    return """
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="\(view)">
      <mask id="check">
        <rect x="\(number(box.minX))" y="\(number(box.minY))" width="\(number(box.width))" height="\(number(box.height))" fill="white"/>
        <path d="\(checkData)" fill="none" stroke="black" stroke-width="\(number(checkWidth))" stroke-linecap="round" stroke-linejoin="round"/>
      </mask>
      <path d="\(pin)" mask="url(#check)" fill="\(fill)" stroke="\(fill)" stroke-width="\(number(pinOutline))" stroke-linejoin="round"/>
    </svg>

    """
}

func writeFavicons(to directory: URL) throws {
    let pinColor = appearances[0].pin
    let small = scaled(pixels: 32, opaque: false, box: faviconBox) { drawPin(pinColor, in: $0) }
    try icoData(png: pngData(small), pixels: 32).write(to: directory.appendingPathComponent("favicon.ico"))
    print("wrote favicon.ico")

    try faviconSVG(fill: "#0A84FF").write(to: directory.appendingPathComponent("favicon.svg"), atomically: true, encoding: .utf8)
    print("wrote favicon.svg")

    let full = CGRect(x: 0, y: 0, width: size, height: size)
    let touch = scaled(pixels: 180, opaque: true, box: full) { draw(appearances[0], in: $0) }
    write(touch, to: directory.appendingPathComponent("apple-touch-icon.png"))
    print("wrote apple-touch-icon.png")
}

let arguments = Array(CommandLine.arguments.dropFirst())
var outputPath: String?
var variant: String?
var writesPreview = false
var writesFavicons = false
var index = 0
while index < arguments.count {
    switch arguments[index] {
    case "--preview":
        writesPreview = true
    case "--favicon":
        writesFavicons = true
    case "--variant":
        index += 1
        variant = index < arguments.count ? arguments[index] : nil
    default:
        outputPath = arguments[index]
    }
    index += 1
}
guard let outputPath else {
    FileHandle.standardError.write(Data("usage: swift render.swift <output-dir> [--preview] [--variant Dev|Stg] [--favicon]\n".utf8))
    exit(2)
}
let outputDirectory = URL(fileURLWithPath: outputPath)
try FileManager.default.createDirectory(at: outputDirectory, withIntermediateDirectories: true)

if writesFavicons {
    try writeFavicons(to: outputDirectory)
    exit(0)
}

for appearance in appearances {
    let context = makeContext(opaque: true)
    draw(appearance, in: context)
    var fileName = appearance.fileName
    if let variant {
        drawBadge(variant.uppercased(), in: context)
        fileName = fileName.replacingOccurrences(of: "AppIcon", with: "AppIcon-\(variant)")
    }
    let image = context.makeImage()!
    if writesPreview {
        write(preview(of: image), to: outputDirectory.appendingPathComponent("\(fileName)-preview.png"))
    } else {
        write(image, to: outputDirectory.appendingPathComponent("\(fileName).png"))
    }
    print("wrote \(fileName)")
}
