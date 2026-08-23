import { WithSkiaWeb } from '@shopify/react-native-skia/lib/module/web';
import { View } from 'react-native';

import type { GrokOrbProps } from '@/components/grok-orb.types';

function GrokOrbFallback({ style }: GrokOrbProps) {
  return <View style={[{ backgroundColor: '#0b0b0b' }, style]} />;
}

export default function GrokOrb(props: GrokOrbProps) {
  const fallback = <GrokOrbFallback {...props} />;

  // Expo Router statically renders this route in Node, where CanvasKit has no
  // DOM surface. The same fallback is reused by Suspense during hydration.
  if (typeof window === 'undefined') {
    return fallback;
  }

  return (
    <WithSkiaWeb
      componentProps={props}
      fallback={fallback}
      getComponent={() => import('@/components/grok-orb-scene')}
    />
  );
}
