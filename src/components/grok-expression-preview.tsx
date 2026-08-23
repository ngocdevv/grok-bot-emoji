import { Canvas, Group, Path } from '@shopify/react-native-skia';

import {
  DEFAULT_GROK_ORB_EXPRESSION,
  GROK_ORB_EXPRESSION_SEQUENCES,
  GROK_ORB_POSES,
} from '@/components/grok-orb-expressions';
import type { GrokOrbExpression } from '@/components/grok-orb-expressions';
import {
  GROK_BODY_PATH,
  GROK_EYE_LOWER_LEFT_PATH,
  GROK_EYE_LOWER_RIGHT_PATH,
} from '@/components/grok-orb-paths';

const ORB_SIZE = 228.541;

type GrokExpressionPreviewProps = {
  expression: GrokOrbExpression;
  size: number;
};

function eyePathsFor(expression: GrokOrbExpression) {
  if (expression === DEFAULT_GROK_ORB_EXPRESSION) {
    return {
      left: GROK_EYE_LOWER_LEFT_PATH,
      right: GROK_EYE_LOWER_RIGHT_PATH,
    };
  }

  const poseIndex = GROK_ORB_EXPRESSION_SEQUENCES[expression][0];

  return GROK_ORB_POSES[poseIndex];
}

export function GrokExpressionPreview({
  expression,
  size,
}: GrokExpressionPreviewProps) {
  const eyePaths = eyePathsFor(expression);
  const scale = size / ORB_SIZE;

  return (
    <Canvas style={{ height: size, width: size }}>
      <Group transform={[{ scale }]}>
        <Path color="#ffffff" path={GROK_BODY_PATH} />
        <Path color="#1a1a1a" path={eyePaths.left} />
        <Path color="#1a1a1a" path={eyePaths.right} />
      </Group>
    </Canvas>
  );
}
