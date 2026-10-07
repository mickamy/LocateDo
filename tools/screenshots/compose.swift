import CoreGraphics
import CoreText
import Foundation
import ImageIO
import UniformTypeIdentifiers

// Turns a raw simulator screenshot into an App Store image: a headline over a soft blue background,
// with the screenshot below it, rounded and shadowed, running off the bottom edge.
//   swift compose.swift <raw.png> <output.png> <headline> [--language <code>]

func color(_ hex: UInt32, alpha: CGFloat = 1) -> CGColor {
    CGColor(
        srgbRed: CGFloat((hex >> 16) & 0xFF) / 255,
        green: CGFloat((hex >> 8) & 0xFF) / 255,
        blue: CGFloat(hex & 0xFF) / 255,
        alpha: alpha
    )
}

func headline(_ text: String, language: String, fontSize: CGFloat, width: CGFloat) -> (CTFrame, CGSize) {
    let font = CTFontCreateUIFontForLanguage(.emphasizedSystem, fontSize, language as CFString)!
    var alignment = CTTextAlignment.center
    let paragraph = withUnsafeBytes(of: &alignment) { bytes in
        var setting = CTParagraphStyleSetting(
            spec: .alignment,
            valueSize: MemoryLayout<CTTextAlignment>.size,
            value: bytes.baseAddress!
        )
        return CTParagraphStyleCreate(&setting, 1)
    }
    let attributes: [NSAttributedString.Key: Any] = [
        kCTFontAttributeName as NSAttributedString.Key: font,
        kCTForegroundColorAttributeName as NSAttributedString.Key: color(0x0B1B33),
        kCTParagraphStyleAttributeName as NSAttributedString.Key: paragraph,
    ]
    let setter = CTFramesetterCreateWithAttributedString(NSAttributedString(string: text, attributes: attributes))
    let constraint = CGSize(width: width, height: .greatestFiniteMagnitude)
    let size = CTFramesetterSuggestFrameSizeWithConstraints(setter, CFRange(location: 0, length: 0), nil, constraint, nil)
    let path = CGPath(rect: CGRect(origin: .zero, size: CGSize(width: width, height: ceil(size.height))), transform: nil)
    let frame = CTFramesetterCreateFrame(setter, CFRange(location: 0, length: 0), path, nil)
    let lines = CFArrayGetCount(CTFrameGetLines(frame))
    if lines != text.split(separator: "\n").count {
        fatalError("\"\(text)\" does not fit; break it by hand with \\n")
    }
    return (frame, size)
}

let arguments = Array(CommandLine.arguments.dropFirst())
guard arguments.count >= 3 else {
    FileHandle.standardError.write(Data("usage: swift compose.swift <raw.png> <output.png> <headline> [--language <code>]\n".utf8))
    exit(2)
}
var language = "en"
if let index = arguments.firstIndex(of: "--language"), index + 1 < arguments.count {
    language = arguments[index + 1]
}
guard let source = CGImageSourceCreateWithURL(URL(fileURLWithPath: arguments[0]) as CFURL, nil),
      let shot = CGImageSourceCreateImageAtIndex(source, 0, nil) else {
    fatalError("could not read \(arguments[0])")
}

// The output keeps the screenshot's own size, which is what App Store Connect expects for the device.
let size = CGSize(width: shot.width, height: shot.height)
guard let context = CGContext(
    data: nil,
    width: shot.width,
    height: shot.height,
    bitsPerComponent: 8,
    bytesPerRow: 0,
    space: CGColorSpace(name: CGColorSpace.sRGB)!,
    bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue
) else {
    fatalError("could not create a bitmap context")
}

let gradient = CGGradient(
    colorsSpace: CGColorSpace(name: CGColorSpace.sRGB),
    colors: [color(0xD6E8FF), color(0xF7FAFF)] as CFArray,
    locations: [0, 1]
)!
context.drawLinearGradient(
    gradient,
    start: CGPoint(x: 0, y: size.height),
    end: CGPoint(x: 0, y: size.height * 0.35),
    options: [.drawsAfterEndLocation]
)

// Core Graphics counts up from the bottom. The screenshot always sits at the same height so a set lines up
// in the store; the headline is centered in the space above it, whether it takes one line or two.
let margin = size.width * 0.08
let shotWidth = size.width * 0.8
let shotHeight = shotWidth * size.height / size.width
let shotTop = size.height * 0.8
let shotRect = CGRect(x: (size.width - shotWidth) / 2, y: shotTop - shotHeight, width: shotWidth, height: shotHeight)
let (textFrame, textSize) = headline(arguments[2], language: language, fontSize: size.width * 0.072, width: size.width - margin * 2)
let bandBottom = shotTop + size.height * 0.02
let bandTop = size.height - size.height * 0.04
context.saveGState()
context.translateBy(x: margin, y: bandBottom + (bandTop - bandBottom - textSize.height) / 2)
CTFrameDraw(textFrame, context)
context.restoreGState()

let corner = shotWidth * 0.12
let shape = CGPath(roundedRect: shotRect, cornerWidth: corner, cornerHeight: corner, transform: nil)
context.saveGState()
context.setShadow(offset: CGSize(width: 0, height: -size.width * 0.012), blur: size.width * 0.05, color: color(0x0B1B33, alpha: 0.22))
context.addPath(shape)
context.setFillColor(color(0xFFFFFF))
context.fillPath()
context.restoreGState()
context.saveGState()
context.addPath(shape)
context.clip()
context.draw(shot, in: shotRect)
context.restoreGState()
context.addPath(shape)
context.setStrokeColor(color(0x0B1B33, alpha: 0.08))
context.setLineWidth(size.width * 0.002)
context.strokePath()

guard let image = context.makeImage(),
      let destination = CGImageDestinationCreateWithURL(
          URL(fileURLWithPath: arguments[1]) as CFURL, UTType.png.identifier as CFString, 1, nil
      ) else {
    fatalError("could not write \(arguments[1])")
}
CGImageDestinationAddImage(destination, image, nil)
guard CGImageDestinationFinalize(destination) else {
    fatalError("could not write \(arguments[1])")
}
print("wrote \(arguments[1])")
