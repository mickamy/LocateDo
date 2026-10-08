import CoreGraphics
import CoreText
import Foundation
import ImageIO
import UniformTypeIdentifiers

// Draws caption images for the App Review video; ffmpeg overlays them because its build here has no drawtext.
//   swift caption.swift <output.png> <width> <height> <text> [--top] [--card [--title <title>] [--icon <png>]]
// Without --card the image is transparent with a caption band near the bottom, or with --top just below where the
// lock screen shows its clock; with --card it is a full-frame light card, in the app's colors, with the icon,
// title, and text centered.

func color(_ hex: UInt32, alpha: CGFloat = 1) -> CGColor {
    CGColor(
        srgbRed: CGFloat((hex >> 16) & 0xFF) / 255,
        green: CGFloat((hex >> 8) & 0xFF) / 255,
        blue: CGFloat(hex & 0xFF) / 255,
        alpha: alpha
    )
}

func frame(
    of text: String,
    fontSize: CGFloat,
    width: CGFloat,
    font: String = "HelveticaNeue-Medium",
    textColor: CGColor = color(0xFFFFFF)
) -> (CTFrame, CGSize) {
    var alignment = CTTextAlignment.center
    var spacing = fontSize * 0.25
    let paragraph = withUnsafeBytes(of: &alignment) { alignmentBytes in
        withUnsafeBytes(of: &spacing) { spacingBytes in
            let settings = [
                CTParagraphStyleSetting(
                    spec: .alignment,
                    valueSize: MemoryLayout<CTTextAlignment>.size,
                    value: alignmentBytes.baseAddress!
                ),
                CTParagraphStyleSetting(
                    spec: .lineSpacingAdjustment,
                    valueSize: MemoryLayout<CGFloat>.size,
                    value: spacingBytes.baseAddress!
                ),
            ]
            return CTParagraphStyleCreate(settings, settings.count)
        }
    }
    let attributes: [NSAttributedString.Key: Any] = [
        kCTFontAttributeName as NSAttributedString.Key: CTFontCreateWithName(font as CFString, fontSize, nil),
        kCTForegroundColorAttributeName as NSAttributedString.Key: textColor,
        kCTParagraphStyleAttributeName as NSAttributedString.Key: paragraph,
    ]
    let string = NSAttributedString(string: text, attributes: attributes)
    let setter = CTFramesetterCreateWithAttributedString(string)
    let constraint = CGSize(width: width, height: .greatestFiniteMagnitude)
    let size = CTFramesetterSuggestFrameSizeWithConstraints(setter, CFRange(location: 0, length: 0), nil, constraint, nil)
    let path = CGPath(rect: CGRect(origin: .zero, size: CGSize(width: width, height: ceil(size.height))), transform: nil)
    return (CTFramesetterCreateFrame(setter, CFRange(location: 0, length: 0), path, nil), size)
}

let arguments = Array(CommandLine.arguments.dropFirst())
guard arguments.count >= 4, let width = Double(arguments[1]), let height = Double(arguments[2]) else {
    FileHandle.standardError.write(Data("usage: swift caption.swift <output.png> <width> <height> <text> [--card]\n".utf8))
    exit(2)
}
let output = URL(fileURLWithPath: arguments[0])
let text = arguments[3]
let isCard = arguments.contains("--card")
let isTop = arguments.contains("--top")

func option(_ name: String) -> String? {
    guard let index = arguments.firstIndex(of: name), index + 1 < arguments.count else {
        return nil
    }
    return arguments[index + 1]
}
let size = CGSize(width: width, height: height)

guard let context = CGContext(
    data: nil,
    width: Int(size.width),
    height: Int(size.height),
    bitsPerComponent: 8,
    bytesPerRow: 0,
    space: CGColorSpace(name: CGColorSpace.sRGB)!,
    bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
) else {
    fatalError("could not create a bitmap context")
}

let margin = size.width * 0.08
let textWidth = size.width - margin * 2
if isCard {
    context.setFillColor(color(0xF2F2F7))
    context.fill(CGRect(origin: .zero, size: size))
    let gap = size.width * 0.05
    var icon: CGImage?
    if let path = option("--icon"),
       let source = CGImageSourceCreateWithURL(URL(fileURLWithPath: path) as CFURL, nil) {
        icon = CGImageSourceCreateImageAtIndex(source, 0, nil)
    }
    let iconSide = size.width * 0.24
    let title = option("--title").map {
        frame(of: $0, fontSize: size.width * 0.058, width: textWidth, font: "HelveticaNeue-Bold", textColor: color(0x0A84FF))
    }
    let body = frame(of: text, fontSize: size.width * 0.046, width: textWidth, font: "HelveticaNeue", textColor: color(0x3C3C43))

    var total = body.1.height
    if icon != nil {
        total += iconSide + gap
    }
    if let title {
        total += title.1.height + gap * 0.6
    }
    // Core Graphics counts up from the bottom, so the stack is laid out from its top edge downwards.
    var top = (size.height + total) / 2 + size.height * 0.04
    if let icon {
        let rect = CGRect(x: (size.width - iconSide) / 2, y: top - iconSide, width: iconSide, height: iconSide)
        context.saveGState()
        context.addPath(CGPath(roundedRect: rect, cornerWidth: iconSide * 0.2237, cornerHeight: iconSide * 0.2237, transform: nil))
        context.clip()
        context.draw(icon, in: rect)
        context.restoreGState()
        top -= iconSide + gap
    }
    if let title {
        context.saveGState()
        context.translateBy(x: margin, y: top - title.1.height)
        CTFrameDraw(title.0, context)
        context.restoreGState()
        top -= title.1.height + gap * 0.6
    }
    context.translateBy(x: margin, y: top - body.1.height)
    CTFrameDraw(body.0, context)
} else {
    let (textFrame, textSize) = frame(of: text, fontSize: size.width * 0.045, width: textWidth - margin)
    let padding = size.width * 0.04
    let bandHeight = textSize.height + padding * 2
    // The context counts y from the bottom.
    var bandY = size.height * 0.14
    if isTop {
        bandY = size.height * 0.73 - bandHeight
    }
    let band = CGRect(x: margin, y: bandY, width: textWidth, height: bandHeight)
    context.setFillColor(color(0x000000, alpha: 0.72))
    context.addPath(CGPath(roundedRect: band, cornerWidth: padding, cornerHeight: padding, transform: nil))
    context.fillPath()
    context.translateBy(x: band.minX + margin / 2, y: band.minY + padding)
    CTFrameDraw(textFrame, context)
}

guard let image = context.makeImage(),
      let destination = CGImageDestinationCreateWithURL(output as CFURL, UTType.png.identifier as CFString, 1, nil) else {
    fatalError("could not write \(output.path)")
}
CGImageDestinationAddImage(destination, image, nil)
guard CGImageDestinationFinalize(destination) else {
    fatalError("could not write \(output.path)")
}
print("wrote \(output.lastPathComponent)")
