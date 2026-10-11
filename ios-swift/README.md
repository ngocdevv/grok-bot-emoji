# Grok Bot Orb — Swift / SwiftUI port

Bản port thuần native (Swift 5, SwiftUI, iOS 17+) của component Orb trong dự án Expo ở thư mục
gốc. Không dùng thư viện ngoài: Skia được thay bằng SwiftUI `Canvas`, Reanimated được thay bằng
`TimelineView(.animation)`.

## Mở và chạy

```bash
open ios-swift/GrokBotOrb.xcodeproj
```

Chọn một iPhone simulator rồi ⌘R. Project dùng *file-system synchronized group* (Xcode 16+),
nên mọi file trong `GrokBotOrb/` được tự động đưa vào target.

Kiểm tra nhanh không cần simulator:

```bash
cd ios-swift/GrokBotOrb
xcrun -sdk iphonesimulator swiftc -typecheck -target arm64-apple-ios17.0-simulator *.swift
```

## Đối chiếu với bản Expo

| Expo (TypeScript)                       | Swift                              | Ghi chú                                                            |
| --------------------------------------- | ---------------------------------- | ------------------------------------------------------------------ |
| `app/_layout.tsx`, `app/index.tsx`      | `GrokBotOrbApp.swift`, `ContentView.swift` | ScrollView nền `#0b0b0b`, status bar ẩn, padding 16/8/36.    |
| `grok-orb-paths.ts`, `GROK_ORB_POSES`   | `GrokOrbPathData.swift`            | Sinh tự động từ source TS, giữ nguyên 5 path + 25 pose.             |
| `grok-orb-expressions.ts`               | `GrokOrbExpressions.swift`         | 12 biểu cảm, nhãn tiếng Việt, accent, sequence, timing range.       |
| `Skia.Path.MakeFromSVGString`           | `GrokOrbSVGPath.swift`             | Parser M/L/C/Z; mắt lưu thành 48 đỉnh để nội suy.                   |
| `usePathInterpolation`, `path.interpolate` | `GrokOrbMath.lerp`              | Nội suy từng đỉnh, đúng công thức `SkPath::interpolate`.            |
| `Easing.bezier(0.77, 0, 0.175, 1)`      | `CubicBezierEasing`                | Transition 320 ms khi đổi biểu cảm.                                 |
| `grok-orb-trails.tsx`                   | `GrokOrbTrails.swift`              | 9 ribbon, 2 layer back/front, gradient HSL 5 stop, retraction.      |
| `grok-orb-scene.tsx`                    | `GrokOrbScene.swift` (`GrokOrb`)   | Quỹ đạo hạt, morph mắt chu kỳ 13.6 s, blink, lắc thân, mirror X.    |
| `grok-expression-preview.tsx`           | `GrokExpressionPreview.swift`      | Thumbnail tĩnh, pose đầu của sequence.                              |
| `grok-orb-expression-picker.tsx`        | `GrokOrbExpressionPicker.swift`    | Lưới 3/4/5/6 cột theo bề rộng, viền accent, scale 0.98 khi nhấn, replay trail khi chọn "Tự nhiên". |
| `useReducedMotion`                      | `accessibilityReduceMotion`        | Đứng yên ở pose tham chiếu khi bật Reduce Motion hoặc `paused`.     |

## Dùng component

```swift
// Tự quản lý state
GrokOrbExpressionPicker()

// Controlled
@State private var expression: GrokOrbExpression = .natural
GrokOrbExpressionPicker(expression: $expression, onExpressionChange: { print($0) })

// Chỉ Orb, không có picker
GrokOrb(expression: .happy)
    .frame(height: 420)
```
