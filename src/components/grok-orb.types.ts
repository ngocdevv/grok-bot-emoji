import type { StyleProp, ViewStyle } from 'react-native';

import type { GrokOrbExpression } from '@/components/grok-orb-expressions';

export type GrokOrbProps = {
  accessibilityLabel?: string;
  /** Selects the eye pose while preserving the Orb's ambient movement. */
  expression?: GrokOrbExpression;
  /** Stops the animation on its reference pose. */
  paused?: boolean;
  style?: StyleProp<ViewStyle>;
  /** Restarts the one-shot color trails whenever this value changes. */
  trailReplayKey?: number;
};

export type { GrokOrbExpression } from '@/components/grok-orb-expressions';
