# Grok Bot Orb — Kotlin / Jetpack Compose port

Bản port thuần native (Kotlin 2.2, Jetpack Compose, minSdk 26) của component Orb trong dự án Expo
ở thư mục gốc. Không dùng thư viện ngoài: Skia được thay bằng Compose `Canvas`, Reanimated được
thay bằng `withInfiniteAnimationFrameNanos`.

## Mở và chạy

Mở thư mục `android-kotlin/` bằng Android Studio (Ladybug trở lên) và nhấn Run, hoặc:

```bash
cd android-kotlin
./gradlew :app:installDebug
```

Kiểm tra nhanh không cần emulator:

```bash
./gradlew :app:compileDebugKotlin
```

Phiên bản: AGP 8.13.2, Kotlin 2.2.21, Compose BOM 2025.10.01, Gradle 8.14.6 (wrapper đi kèm).

## Đối chiếu với bản Expo

| Expo (TypeScript)                       | Kotlin                             | Ghi chú                                                            |
| --------------------------------------- | ---------------------------------- | ------------------------------------------------------------------ |
| `app/_layout.tsx`, `app/index.tsx`      | `MainActivity.kt` (`HomeScreen`)   | Column cuộn dọc nền `#0b0b0b`, status bar ẩn, padding 16/8/36.      |
| `grok-orb-paths.ts`, `GROK_ORB_POSES`   | `orb/GrokOrbPathData.kt`           | Sinh tự động từ source TS, giữ nguyên 5 path + 25 pose.             |
| `grok-orb-expressions.ts`               | `orb/GrokOrbExpressions.kt`        | 12 biểu cảm, nhãn tiếng Việt, accent, sequence, timing range.       |
| `Skia.Path.MakeFromSVGString`           | `orb/SvgPath.kt`                   | Parser M/L/C/Z; mắt lưu thành `FloatArray` 48 đỉnh để nội suy.      |
| `usePathInterpolation`, `path.interpolate` | `GrokOrbMath.lerpInto`          | Nội suy từng đỉnh vào buffer dùng lại, không cấp phát mỗi frame.    |
| `Easing.bezier(0.77, 0, 0.175, 1)`      | `CubicBezierEasing` (Compose)      | Transition 320 ms khi đổi biểu cảm.                                 |
| `grok-orb-trails.tsx`                   | `orb/GrokOrbTrails.kt`             | 9 ribbon, 2 layer back/front, gradient HSL 5 stop, retraction.      |
| `grok-orb-scene.tsx`                    | `orb/GrokOrb.kt`                   | Quỹ đạo hạt, morph mắt chu kỳ 13.6 s, blink, lắc thân, mirror X.    |
| `grok-expression-preview.tsx`           | `orb/GrokExpressionPreview.kt`     | Thumbnail tĩnh, pose đầu của sequence.                              |
| `grok-orb-expression-picker.tsx`        | `orb/GrokOrbExpressionPicker.kt`   | Lưới 3/4/5/6 cột theo bề rộng, viền accent, scale 0.98 khi nhấn, replay trail khi chọn "Tự nhiên". |
| `useReducedMotion`                      | `rememberReduceMotion()`           | Android không có Reduce Motion; dùng animator duration scale = 0.   |

## Dùng component

```kotlin
// Tự quản lý state
GrokOrbExpressionPicker()

// Controlled
var expression by remember { mutableStateOf(GrokOrbExpression.Natural) }
GrokOrbExpressionPicker(expression = expression, onExpressionChange = { expression = it })

// Chỉ Orb, không có picker
GrokOrb(modifier = Modifier.fillMaxWidth().height(420.dp), expression = GrokOrbExpression.Happy)
```
