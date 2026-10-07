import CoreGraphics
import CoreText
import Foundation
import ImageIO
import UniformTypeIdentifiers

// Draws the Google Play feature graphic (1024x500): the app icon beside the app name and a tagline,
// over the same soft blue background as the screenshots.
//   swift feature_graphic.swift <icon.png> <output.png> <name> <tagline> [--language <code>]

func color(_ hex: UInt32, alpha: CGFloat = 1) -> CGColor {
    CGColor(
        srgbRed: CGFloat((hex >> 16) & 0xFF) / 255,
        green: CGFloat((hex >> 8) & 0xFF) / 255,
        blue: CGFloat(hex & 0xFF) / 255,
        alpha: alpha
    )
}

func line(_ text: String, language: String, style: CTFontUIFontType, fontSize: CGFloat, hex: UInt32) -> CTLine {
    let font = CTFontCreateUIFontForLanguage(style, fontSize, language as CFString)!
    let attributes: [NSAttributedString.Key: Any] = [
        kCTFontAttributeName as NSAttributedString.Key: font,
        kCTForegroundColorAttributeName as NSAttributedString.Key: color(hex),
    ]
    return CTLineCreateWithAttributedString(NSAttributedString(string: text, attributes: attributes))
}

func metrics(_ line: CTLine) -> (width: CGFloat, ascent: CGFloat, descent: CGFloat) {
    var ascent: CGFloat = 0
    var descent: CGFloat = 0
    let width = CGFloat(CTLineGetTypographicBounds(line, &ascent, &descent, nil))
    return (width, ascent, descent)
}

let arguments = Array(CommandLine.arguments.dropFirst())
guard arguments.count >= 4 else {
    FileHandle.standardError.write(Data(
        "usage: swift feature_graphic.swift <icon.png> <output.png> <name> <tagline> [--language <code>]\n".utf8
    ))
    exit(2)
}
var language = "en"
if let index = arguments.firstIndex(of: "--language"), index + 1 < arguments.count {
    language = arguments[index + 1]
}
guard let source = CGImageSourceCreateWithURL(URL(fileURLWithPath: arguments[0]) as CFURL, nil),
      let icon = CGImageSourceCreateImageAtIndex(source, 0, nil) else {
    fatalError("could not read \(arguments[0])")
}

let size = CGSize(width: 1024, height: 500)
guard let context = CGContext(
    data: nil,
    width: Int(size.width),
    height: Int(size.height),
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
context.drawLinearGradient(gradient, start: CGPoint(x: 0, y: size.height), end: .zero, options: [])

let name = line(arguments[2], language: language, style: .emphasizedSystem, fontSize: 84, hex: 0x0B1B33)
let tagline = line(arguments[3], language: language, style: .system, fontSize: 36, hex: 0x3A4A63)
let nameMetrics = metrics(name)
let taglineMetrics = metrics(tagline)

// The icon and the text are centered together as one group, so a short name does not leave the right side empty.
let iconSide: CGFloat = 220
let gap: CGFloat = 56
let textWidth = max(nameMetrics.width, taglineMetrics.width)
let groupWidth = iconSide + gap + textWidth
guard groupWidth <= size.width - 96 else {
    fatalError("\"\(arguments[2])\" / \"\(arguments[3])\" is too wide for the feature graphic")
}
let left = (size.width - groupWidth) / 2

let iconRect = CGRect(x: left, y: (size.height - iconSide) / 2, width: iconSide, height: iconSide)
let corner = iconSide * 0.22
let shape = CGPath(roundedRect: iconRect, cornerWidth: corner, cornerHeight: corner, transform: nil)
context.saveGState()
context.setShadow(offset: CGSize(width: 0, height: -8), blur: 36, color: color(0x0B1B33, alpha: 0.22))
context.addPath(shape)
context.setFillColor(color(0xFFFFFF))
context.fillPath()
context.restoreGState()
context.saveGState()
context.addPath(shape)
context.clip()
context.draw(icon, in: iconRect)
context.restoreGState()

let lineGap: CGFloat = 18
let nameHeight = nameMetrics.ascent + nameMetrics.descent
let taglineHeight = taglineMetrics.ascent + taglineMetrics.descent
let blockBottom = (size.height - (nameHeight + lineGap + taglineHeight)) / 2
let textLeft = left + iconSide + gap
context.textPosition = CGPoint(x: textLeft, y: blockBottom + taglineMetrics.descent)
CTLineDraw(tagline, context)
context.textPosition = CGPoint(x: textLeft, y: blockBottom + taglineHeight + lineGap + nameMetrics.descent)
CTLineDraw(name, context)

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
