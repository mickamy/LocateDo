import CoreGraphics
import CoreText
import Foundation
import ImageIO
import UniformTypeIdentifiers

// Draws caption images for the App Review video; ffmpeg overlays them because its build here has no drawtext.
//   swift caption.swift <output.png> <width> <height> <text> [--card]
// Without --card the image is transparent with a caption band near the bottom; with --card it is a
// full-frame dark card with the text centered.

func color(_ hex: UInt32, alpha: CGFloat = 1) -> CGColor {
    CGColor(
        srgbRed: CGFloat((hex >> 16) & 0xFF) / 255,
        green: CGFloat((hex >> 8) & 0xFF) / 255,
        blue: CGFloat(hex & 0xFF) / 255,
        alpha: alpha
    )
}

func frame(of text: String, fontSize: CGFloat, width: CGFloat) -> (CTFrame, CGSize) {
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
        kCTFontAttributeName as NSAttributedString.Key: CTFontCreateWithName("HelveticaNeue-Medium" as CFString, fontSize, nil),
        kCTForegroundColorAttributeName as NSAttributedString.Key: color(0xFFFFFF),
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
    context.setFillColor(color(0x101014))
    context.fill(CGRect(origin: .zero, size: size))
    let (textFrame, textSize) = frame(of: text, fontSize: size.width * 0.052, width: textWidth)
    context.translateBy(x: margin, y: (size.height - textSize.height) / 2)
    CTFrameDraw(textFrame, context)
} else {
    let (textFrame, textSize) = frame(of: text, fontSize: size.width * 0.045, width: textWidth - margin)
    let padding = size.width * 0.04
    let band = CGRect(
        x: margin,
        y: size.height * 0.14,
        width: textWidth,
        height: textSize.height + padding * 2
    )
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
