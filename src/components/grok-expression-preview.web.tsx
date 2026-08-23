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

  return (
    <svg
      aria-hidden="true"
      height={size}
      style={{ display: 'block' }}
      viewBox="0 0 228.541 228.541"
      width={size}>
      <path d={GROK_BODY_PATH} fill="#ffffff" />
      <path d={eyePaths.left} fill="#1a1a1a" />
      <path d={eyePaths.right} fill="#1a1a1a" />
    </svg>
  );
}
