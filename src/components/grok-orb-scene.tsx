import {
  Canvas,
  Circle,
  Group,
  Path,
  Skia,
  useClock,
  usePathInterpolation,
  vec,
} from '@shopify/react-native-skia';
import { useCallback, useLayoutEffect, useState } from 'react';
import type { LayoutChangeEvent } from 'react-native';
import { StyleSheet, View } from 'react-native';
import {
  Easing,
  useDerivedValue,
  useReducedMotion,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';

import {
  DEFAULT_GROK_ORB_EXPRESSION,
  GROK_ORB_EXPRESSION_SEQUENCES,
  GROK_ORB_EXPRESSION_TIMING_RANGES,
  GROK_ORB_POSES,
} from '@/components/grok-orb-expressions';
import type {
  GrokOrbExpression,
  GrokOrbPresetExpression,
} from '@/components/grok-orb-expressions';
import {
  GROK_BODY_PATH,
  GROK_EYE_LOWER_LEFT_PATH,
  GROK_EYE_LOWER_RIGHT_PATH,
  GROK_EYE_UPPER_LEFT_PATH,
  GROK_EYE_UPPER_RIGHT_PATH,
} from '@/components/grok-orb-paths';
import { GrokOrbTrails } from '@/components/grok-orb-trails';
import type { GrokOrbProps } from '@/components/grok-orb.types';

const BODY_CENTER = 114.2705;
const ORBIT_CENTER_X = 114.25;
const ORBIT_CENTER_Y = 106.25;
const ORBIT_RADIUS_X = 148.45;
const ORBIT_RADIUS_Y = 56.45;
const ORBIT_SPEED = 1.55096587;
const PARTICLE_MIN_RADIUS = 4.13;
const PARTICLE_RADIUS_RANGE = 3.37;
const PARTICLE_MIN_OPACITY = 0.685;
const PARTICLE_OPACITY_RANGE = 0.315;

const EYE_CYCLE_DURATION = 13.6;
const MORPH_UP_START = 3.35;
const MORPH_UP_END = 4.15;
const MORPH_DOWN_START = 9.25;
const MORPH_DOWN_END = 10.05;
const EXPRESSION_MORPH_DURATION = 320;
const EXPRESSION_EASING = Easing.bezier(0.77, 0, 0.175, 1);
const EXPRESSION_POSE_MORPH_DURATION = 0.72;
const CADENCE_VARIATIONS = [0.18, 0.72, 0.42, 0.9, 0.58] as const;

const bodyOrigin = vec(BODY_CENTER, BODY_CENTER);
const eyeOrigin = vec(BODY_CENTER, 92);

function makePath(svg: string, label: string) {
  const path = Skia.Path.MakeFromSVGString(svg);

  if (path === null) {
    throw new Error(`Unable to parse the ${label} SVG path.`);
  }

  return path;
}

const bodyPath = makePath(GROK_BODY_PATH, 'Grok Orb body');
const lowerLeftEyePath = makePath(
  GROK_EYE_LOWER_LEFT_PATH,
  'lower-left Grok Orb eye'
);
const upperLeftEyePath = makePath(
  GROK_EYE_UPPER_LEFT_PATH,
  'upper-left Grok Orb eye'
);
const lowerRightEyePath = makePath(
  GROK_EYE_LOWER_RIGHT_PATH,
  'lower-right Grok Orb eye'
);
const upperRightEyePath = makePath(
  GROK_EYE_UPPER_RIGHT_PATH,
  'upper-right Grok Orb eye'
);

type OrbEyePath = typeof lowerLeftEyePath;

type OrbExpressionMotion = {
  durations: number[];
  inputOffset: number;
  totalDuration: number;
};

const orbPosePaths = GROK_ORB_POSES.map((pose, index) => ({
  left: makePath(pose.left, `pose ${index} left Grok Orb eye`),
  right: makePath(pose.right, `pose ${index} right Grok Orb eye`),
}));

const expressionPoseInputRange: number[] = [];
const expressionLeftEyePaths: OrbEyePath[] = [];
const expressionRightEyePaths: OrbEyePath[] = [];

const expressionMotions = Object.fromEntries(
  (
    Object.entries(GROK_ORB_EXPRESSION_SEQUENCES) as [
      GrokOrbPresetExpression,
      readonly number[],
    ][]
  ).map(([expression, sequence], expressionIndex) => {
    const [minimumDuration, maximumDuration] =
      GROK_ORB_EXPRESSION_TIMING_RANGES[expression];
    const durations = sequence.map((_, poseIndex) => {
      const variation =
        CADENCE_VARIATIONS[
          (expressionIndex + poseIndex) % CADENCE_VARIATIONS.length
        ];

      return (
        minimumDuration +
        (maximumDuration - minimumDuration) * variation
      );
    });
    const inputOffset = expressionPoseInputRange.length;

    [...sequence, sequence[0]].forEach((poseIndex) => {
      expressionPoseInputRange.push(expressionPoseInputRange.length);
      expressionLeftEyePaths.push(orbPosePaths[poseIndex].left);
      expressionRightEyePaths.push(orbPosePaths[poseIndex].right);
    });

    return [
      expression,
      {
        durations,
        inputOffset,
        totalDuration: durations.reduce((total, duration) => total + duration, 0),
      },
    ];
  })
) as Record<GrokOrbPresetExpression, OrbExpressionMotion>;

function smoothstep(value: number) {
  'worklet';
  const clamped = Math.max(0, Math.min(1, value));

  return clamped * clamped * (3 - 2 * clamped);
}

function animatedExpressionValueAt(
  time: number,
  inputOffset: number,
  durations: number[],
  totalDuration: number
) {
  'worklet';
  let segmentTime = ((time % totalDuration) + totalDuration) % totalDuration;
  let poseIndex = 0;

  for (let index = 0; index < durations.length; index += 1) {
    if (segmentTime < durations[index]) {
      poseIndex = index;
      break;
    }

    segmentTime -= durations[index];
  }

  const segmentDuration = durations[poseIndex];
  const morphDuration = Math.min(
    EXPRESSION_POSE_MORPH_DURATION,
    segmentDuration * 0.42
  );
  const holdDuration = segmentDuration - morphDuration;
  const morphProgress = smoothstep(
    (segmentTime - holdDuration) / morphDuration
  );
  return inputOffset + poseIndex + morphProgress;
}

function eyeMorphAt(time: number) {
  'worklet';
  const localTime = time % EYE_CYCLE_DURATION;

  if (localTime < MORPH_UP_START) {
    return 0;
  }

  if (localTime < MORPH_UP_END) {
    return smoothstep(
      (localTime - MORPH_UP_START) / (MORPH_UP_END - MORPH_UP_START)
    );
  }

  if (localTime < MORPH_DOWN_START) {
    return 1;
  }

  if (localTime < MORPH_DOWN_END) {
    return (
      1 -
      smoothstep(
        (localTime - MORPH_DOWN_START) /
          (MORPH_DOWN_END - MORPH_DOWN_START)
      )
    );
  }

  return 0;
}

function transitionAt(time: number) {
  'worklet';
  const localTime = time % EYE_CYCLE_DURATION;

  if (localTime >= MORPH_UP_START && localTime < MORPH_UP_END) {
    return {
      isMorphingUp: true,
      progress: (localTime - MORPH_UP_START) / (MORPH_UP_END - MORPH_UP_START),
    };
  }

  if (localTime >= MORPH_DOWN_START && localTime < MORPH_DOWN_END) {
    return {
      isMorphingUp: false,
      progress:
        (localTime - MORPH_DOWN_START) /
        (MORPH_DOWN_END - MORPH_DOWN_START),
    };
  }

  return { isMorphingUp: false, progress: -1 };
}

function particleDepth(angle: number) {
  'worklet';

  return (1 - Math.sin(angle)) * 0.5;
}

export default function GrokOrbScene({
  accessibilityLabel = 'Animated Grok Bot Orb',
  expression = DEFAULT_GROK_ORB_EXPRESSION,
  paused = false,
  style,
  trailReplayKey = 0,
}: GrokOrbProps) {
  const [size, setSize] = useState({ height: 1, width: 1 });
  const [expressionPair, setExpressionPair] = useState<{
    from: GrokOrbExpression;
    to: GrokOrbExpression;
  }>(() => ({ from: expression, to: expression }));
  const clock = useClock();
  const reduceMotion = useReducedMotion();
  const isStatic = paused || reduceMotion;
  const expressionTransition = useSharedValue(1);
  const trailStartTime = useSharedValue(0);

  if (expressionPair.to !== expression) {
    setExpressionPair({ from: expressionPair.to, to: expression });
  }

  const previousExpression = expressionPair.from;
  const selectedExpression = expressionPair.to;
  const previousIsNatural =
    previousExpression === DEFAULT_GROK_ORB_EXPRESSION;
  const expressionIsNatural =
    selectedExpression === DEFAULT_GROK_ORB_EXPRESSION;

  useLayoutEffect(() => {
    if (previousExpression === selectedExpression) {
      expressionTransition.set(1);
      return;
    }

    expressionTransition.set(0);
    expressionTransition.set(
      isStatic
        ? 1
        : withTiming(1, {
            duration: EXPRESSION_MORPH_DURATION,
            easing: EXPRESSION_EASING,
          })
    );
  }, [
    expressionTransition,
    isStatic,
    previousExpression,
    selectedExpression,
  ]);

  useLayoutEffect(() => {
    trailStartTime.set(clock.get() / 1000);
  }, [clock, trailReplayKey, trailStartTime]);

  const animationTime = useDerivedValue(
    () => (isStatic ? 0 : clock.get() / 1000),
    [isStatic]
  );
  const trailTime = useDerivedValue(
    () =>
      isStatic
        ? 0
        : Math.max(0, clock.get() / 1000 - trailStartTime.get()),
    [isStatic]
  );
  const naturalEyeMorph = useDerivedValue(() =>
    eyeMorphAt(animationTime.get())
  );

  const naturalLeftEyePath = usePathInterpolation(
    naturalEyeMorph,
    [0, 1],
    [lowerLeftEyePath, upperLeftEyePath]
  );
  const naturalRightEyePath = usePathInterpolation(
    naturalEyeMorph,
    [0, 1],
    [lowerRightEyePath, upperRightEyePath]
  );

  const previousMotion = previousIsNatural
    ? expressionMotions.happy
    : expressionMotions[previousExpression];
  const selectedMotion = expressionIsNatural
    ? expressionMotions.happy
    : expressionMotions[selectedExpression];

  const previousExpressionValue = useDerivedValue(() =>
    animatedExpressionValueAt(
      animationTime.get(),
      previousMotion.inputOffset,
      previousMotion.durations,
      previousMotion.totalDuration
    )
  );
  const selectedExpressionValue = useDerivedValue(() =>
    animatedExpressionValueAt(
      animationTime.get(),
      selectedMotion.inputOffset,
      selectedMotion.durations,
      selectedMotion.totalDuration
    )
  );

  const previousLeftEyePath = usePathInterpolation(
    previousExpressionValue,
    expressionPoseInputRange,
    expressionLeftEyePaths
  );
  const previousRightEyePath = usePathInterpolation(
    previousExpressionValue,
    expressionPoseInputRange,
    expressionRightEyePaths
  );
  const selectedLeftEyePath = usePathInterpolation(
    selectedExpressionValue,
    expressionPoseInputRange,
    expressionLeftEyePaths
  );
  const selectedRightEyePath = usePathInterpolation(
    selectedExpressionValue,
    expressionPoseInputRange,
    expressionRightEyePaths
  );

  const leftEyePath = useDerivedValue(() => {
    const progress = expressionTransition.get();
    const fromPath = previousIsNatural
      ? naturalLeftEyePath.get()
      : previousLeftEyePath.get();
    const toPath = expressionIsNatural
      ? naturalLeftEyePath.get()
      : selectedLeftEyePath.get();

    return toPath.interpolate(fromPath, progress) ?? toPath;
  }, [previousExpression, selectedExpression]);

  const rightEyePath = useDerivedValue(() => {
    const progress = expressionTransition.get();
    const fromPath = previousIsNatural
      ? naturalRightEyePath.get()
      : previousRightEyePath.get();
    const toPath = expressionIsNatural
      ? naturalRightEyePath.get()
      : selectedRightEyePath.get();

    return toPath.interpolate(fromPath, progress) ?? toPath;
  }, [previousExpression, selectedExpression]);

  const naturalExpressionWeight = useDerivedValue(() => {
    const progress = expressionTransition.get();

    if (previousIsNatural && expressionIsNatural) {
      return 1;
    }

    if (previousIsNatural) {
      return 1 - progress;
    }

    if (expressionIsNatural) {
      return progress;
    }

    return 0;
  }, [previousExpression, selectedExpression]);

  const bodyTransform = useDerivedValue(() => {
    const time = animationTime.get();
    const translateX =
      1.4878 * Math.sin((Math.PI * 2 * time) / 18.29 - 3.0698);
    const translateY =
      1.4539 * Math.sin((Math.PI * 2 * time) / 7.835 - 0.828);
    const rotation =
      ((1.9847 * Math.sin((Math.PI * 2 * time) / 13.71 + 2.2731)) *
        Math.PI) /
      180;

    return [{ translateX }, { translateY }, { rotate: rotation }];
  });

  const firstParticleCenter = useDerivedValue(() => {
    const angle = animationTime.get() * ORBIT_SPEED + 0.42;

    return {
      x: ORBIT_CENTER_X + ORBIT_RADIUS_X * Math.cos(angle),
      y: ORBIT_CENTER_Y + ORBIT_RADIUS_Y * Math.sin(angle),
    };
  });
  const firstParticleRadius = useDerivedValue(() => {
    const angle = animationTime.get() * ORBIT_SPEED + 0.42;

    return PARTICLE_MIN_RADIUS + PARTICLE_RADIUS_RANGE * particleDepth(angle);
  });
  const firstParticleOpacity = useDerivedValue(() => {
    const angle = animationTime.get() * ORBIT_SPEED + 0.42;

    return (
      PARTICLE_MIN_OPACITY +
      PARTICLE_OPACITY_RANGE * particleDepth(angle)
    );
  });

  const secondParticleCenter = useDerivedValue(() => {
    const angle = animationTime.get() * ORBIT_SPEED + 0.42 + Math.PI;

    return {
      x: ORBIT_CENTER_X + ORBIT_RADIUS_X * Math.cos(angle),
      y: ORBIT_CENTER_Y + ORBIT_RADIUS_Y * Math.sin(angle),
    };
  });
  const secondParticleRadius = useDerivedValue(() => {
    const angle = animationTime.get() * ORBIT_SPEED + 0.42 + Math.PI;

    return PARTICLE_MIN_RADIUS + PARTICLE_RADIUS_RANGE * particleDepth(angle);
  });
  const secondParticleOpacity = useDerivedValue(() => {
    const angle = animationTime.get() * ORBIT_SPEED + 0.42 + Math.PI;

    return (
      PARTICLE_MIN_OPACITY +
      PARTICLE_OPACITY_RANGE * particleDepth(angle)
    );
  });

  const eyeTransitionTransform = useDerivedValue(() => {
    const transition = transitionAt(animationTime.get());
    const expressionProgress = expressionTransition.get();
    const selectionPulse = Math.sin(Math.PI * expressionProgress);

    if (transition.progress < 0) {
      const selectionScale = 1 + 0.045 * selectionPulse;

      return [{ scaleX: selectionScale }, { scaleY: selectionScale }];
    }

    const naturalPulse = Math.sin(Math.PI * transition.progress);
    const overshoot = 1 + 0.052 * naturalPulse + 0.045 * selectionPulse;
    const blink = transition.isMorphingUp
      ? Math.pow(naturalPulse, 10) * 0.58
      : 0;

    return [
      { scaleX: overshoot },
      { scaleY: overshoot * (1 - blink) },
    ];
  });

  const leftEyeTransform = useDerivedValue(() => {
    const time = animationTime.get();
    const morph = naturalEyeMorph.get();
    const naturalWeight = naturalExpressionWeight.get();
    const presetWeight = 1 - naturalWeight;

    return [
      {
        translateX:
          naturalWeight *
            (-4.05 + 0.22 * morph + 0.85 * Math.sin(time * 0.71)) +
          presetWeight *
            (0.68 * Math.sin(time * 0.71) +
              0.2 * Math.sin(time * 1.31 + 0.4)),
      },
      {
        translateY:
          naturalWeight *
            (-2.9 + 4.25 * morph + 0.7 * Math.sin(time * 0.93 + 1.1)) +
          presetWeight * 0.5 * Math.sin(time * 0.93 + 1.1),
      },
    ];
  });

  const rightEyeTransform = useDerivedValue(() => {
    const time = animationTime.get();
    const morph = naturalEyeMorph.get();
    const naturalWeight = naturalExpressionWeight.get();
    const presetWeight = 1 - naturalWeight;

    return [
      {
        translateX:
          naturalWeight *
            (-5.6 + 1.1 * morph + 0.8 * Math.sin(time * 0.66 + 2.1)) +
          presetWeight *
            (0.64 * Math.sin(time * 0.66 + 2.1) +
              0.18 * Math.sin(time * 1.23 + 1.7)),
      },
      {
        translateY:
          naturalWeight *
            (-2.1 + 2.6 * morph + 0.65 * Math.sin(time * 0.87 + 2.4)) +
          presetWeight * 0.46 * Math.sin(time * 0.87 + 2.4),
      },
    ];
  });

  const handleLayout = useCallback((event: LayoutChangeEvent) => {
    const { height, width } = event.nativeEvent.layout;

    setSize((currentSize) => {
      if (currentSize.height === height && currentSize.width === width) {
        return currentSize;
      }

      return { height, width };
    });
  }, []);

  const orbScale = (Math.min(size.width, size.height) * 0.36) / BODY_CENTER;
  const layoutTransform = [
    { translateX: size.width / 2 },
    { translateY: size.height / 2 },
    { scaleX: -orbScale },
    { scaleY: orbScale },
    { translateX: -BODY_CENTER },
    { translateY: -BODY_CENTER },
  ];

  return (
    <View
      accessibilityLabel={accessibilityLabel}
      accessibilityRole="image"
      onLayout={handleLayout}
      style={[styles.container, style]}>
      <Canvas style={StyleSheet.absoluteFill}>
        <Group transform={layoutTransform}>
          <GrokOrbTrails layer="back" time={trailTime} />

          <Circle
            c={firstParticleCenter}
            color="#ffffff"
            opacity={firstParticleOpacity}
            r={firstParticleRadius}
          />
          <Circle
            c={secondParticleCenter}
            color="#ffffff"
            opacity={secondParticleOpacity}
            r={secondParticleRadius}
          />

          <Group origin={bodyOrigin} transform={bodyTransform}>
            <Path color="#ffffff" path={bodyPath} />

            <Group clip={bodyPath}>
              <Group origin={eyeOrigin} transform={eyeTransitionTransform}>
                <Group transform={leftEyeTransform}>
                  <Path color="#1a1a1a" path={leftEyePath} />
                </Group>
                <Group transform={rightEyeTransform}>
                  <Path color="#1a1a1a" path={rightEyePath} />
                </Group>
              </Group>
            </Group>
          </Group>

          <GrokOrbTrails layer="front" time={trailTime} />
        </Group>
      </Canvas>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    overflow: 'hidden',
    pointerEvents: 'none',
  },
});
