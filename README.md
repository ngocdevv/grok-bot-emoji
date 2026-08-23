# Grok Bot Orb — Expo recreation

An Expo SDK 57 and React Native recreation of the animated Orb featured in the
**“Message Bots like teammates”** section of `x.ai/bot`.

## Architecture

- `src/components/grok-orb-expression-picker.tsx`: a complete interactive
  component containing the main Orb, expression state, responsive picker grid,
  and color-trail replay behavior.
- `src/components/grok-orb-paths.ts` and `grok-orb-expressions.ts`: vector paths,
  poses, and animation cadence derived by comparing against the
  `.grok-bot-mark` SVG on `x.ai/bot`.
- `src/components/grok-orb-scene.tsx`: renders the Orb with React Native Skia,
  morphs the two eye paths, builds elliptical trail paths, and drives motion
  with Reanimated. It also respects the user's Reduce Motion preference.
- `src/components/grok-expression-preview.tsx` and its `.web.tsx` counterpart:
  lightweight expression thumbnails rendered with Skia on native and SVG on
  the web.
- `src/components/grok-orb.web.tsx`: loads CanvasKit asynchronously so Expo
  Router can statically render the web page without importing Skia too early.
- `src/components/grok-orb.tsx`: the lower-level Orb component shared by iOS
  and Android.

Redraw was evaluated but not selected because it is still a technical preview
and requires React Native WebGPU. Skia is a better fit for the original SVG
structure, supports path interpolation, and can consume Reanimated shared
values directly.

## Using the component

```tsx
import { GrokOrbExpressionPicker } from '@/components/grok-orb-expression-picker';

<GrokOrbExpressionPicker />
```

The component manages its own state by default. It can also be controlled by a
parent component:

```tsx
const [expression, setExpression] = useState<GrokOrbExpression>('natural');

<GrokOrbExpressionPicker
  expression={expression}
  onExpressionChange={setExpression}
/>;
```

Main props:

- `defaultExpression`: the initial expression in uncontrolled mode.
- `expression` and `onExpressionChange`: the controlled-state API.
- `paused`: freezes the animation on its reference pose.
- `style`: styles the outer container.

To render the Orb without the expression picker:

```tsx
import GrokOrb from '@/components/grok-orb';

<GrokOrb expression="happy" style={{ height: 420, width: '100%' }} />;
```

## Running and validating

```bash
bun install
bunx expo start
bunx expo lint
bunx tsc --noEmit
bunx expo export --platform all
```

The `postinstall` script copies `canvaskit.wasm` into `public/` so the web build
can run with Metro. There is no need to create or edit the `ios/` and `android/`
directories directly.
