import {
  LinearGradient,
  Path,
  usePathValue,
} from '@shopify/react-native-skia';
import type { SkPathBuilder } from '@shopify/react-native-skia';
import { useDerivedValue } from 'react-native-reanimated';
import type { SharedValue } from 'react-native-reanimated';

const HEAD_CENTER = 114.2705;
const HUMMING_START = 0.8;
const TRAIL_MAX_LIFE = 9;
const RETRACTION_START = TRAIL_MAX_LIFE * 0.55;
const RETRACTION_DURATION = 0.5;
const MAX_SAMPLE_ANGLE = 0.09;

const GRADIENT_POSITIONS = [0, 0.25, 0.5, 0.75, 1];

type TrailLayer = 'back' | 'front';

type TrailConfig = {
  arc: number;
  baseHue: number;
  baseWidth: number;
  follow: number;
  hueSpan: number;
  hueVelocity: number;
  lambdaOffset: number;
  lambdaVelocity: number;
  radius: number;
  radiusVelocity: number;
  roll: number;
  spawnDelay: number;
  tilt: number;
};

// The website creates three orbital planes and emits three ribbons on each
// plane. Its values are randomized per visit; these are stable samples from
// the same ranges so the native version remains reproducible.
const TRAILS: TrailConfig[] = [
  {
    arc: 2.72,
    baseHue: 79,
    baseWidth: 6.3,
    follow: 0.84,
    hueSpan: -74,
    hueVelocity: 24,
    lambdaOffset: 0.08,
    lambdaVelocity: 0.78,
    radius: 115.2,
    radiusVelocity: 1.4,
    roll: -0.31,
    spawnDelay: 0,
    tilt: 0.23,
  },
  {
    arc: 3.18,
    baseHue: 122,
    baseWidth: 6.8,
    follow: 0.76,
    hueSpan: 61,
    hueVelocity: -31,
    lambdaOffset: 0.14,
    lambdaVelocity: 1.02,
    radius: 116.6,
    radiusVelocity: 0.5,
    roll: 0.78,
    spawnDelay: 0.07,
    tilt: 0.4,
  },
  {
    arc: 2.38,
    baseHue: 160,
    baseWidth: 5.9,
    follow: 0.92,
    hueSpan: 89,
    hueVelocity: 38,
    lambdaOffset: 0.04,
    lambdaVelocity: 0.57,
    radius: 114.9,
    radiusVelocity: 2.1,
    roll: 1.86,
    spawnDelay: 0.15,
    tilt: 0.47,
  },
  {
    arc: 3.34,
    baseHue: 204,
    baseWidth: 7.1,
    follow: 0.8,
    hueSpan: -48,
    hueVelocity: -20,
    lambdaOffset: 0.16,
    lambdaVelocity: 0.91,
    radius: 134.2,
    radiusVelocity: 1.8,
    roll: -0.25,
    spawnDelay: 0.235,
    tilt: 0.2,
  },
  {
    arc: 2.9,
    baseHue: 244,
    baseWidth: 6.5,
    follow: 0.88,
    hueSpan: 82,
    hueVelocity: 29,
    lambdaOffset: 0.1,
    lambdaVelocity: 0.64,
    radius: 135.8,
    radiusVelocity: 0.9,
    roll: 0.83,
    spawnDelay: 0.315,
    tilt: 0.37,
  },
  {
    arc: 2.46,
    baseHue: 286,
    baseWidth: 5.7,
    follow: 0.74,
    hueSpan: -92,
    hueVelocity: -40,
    lambdaOffset: 0.06,
    lambdaVelocity: 1.08,
    radius: 135.1,
    radiusVelocity: 2.3,
    roll: 1.93,
    spawnDelay: 0.385,
    tilt: 0.44,
  },
  {
    arc: 3.06,
    baseHue: 322,
    baseWidth: 6.9,
    follow: 0.94,
    hueSpan: 58,
    hueVelocity: 21,
    lambdaOffset: 0.13,
    lambdaVelocity: 0.52,
    radius: 154.6,
    radiusVelocity: 0.3,
    roll: -0.36,
    spawnDelay: 0.475,
    tilt: 0.27,
  },
  {
    arc: 2.58,
    baseHue: 7,
    baseWidth: 6,
    follow: 0.78,
    hueSpan: -69,
    hueVelocity: -27,
    lambdaOffset: 0.02,
    lambdaVelocity: 0.87,
    radius: 153.4,
    radiusVelocity: 1.6,
    roll: 0.72,
    spawnDelay: 0.555,
    tilt: 0.34,
  },
  {
    arc: 3.26,
    baseHue: 45,
    baseWidth: 7.3,
    follow: 0.86,
    hueSpan: 94,
    hueVelocity: 35,
    lambdaOffset: 0.11,
    lambdaVelocity: 0.69,
    radius: 155.1,
    radiusVelocity: 2.4,
    roll: 1.8,
    spawnDelay: 0.64,
    tilt: 0.49,
  },
];

function clamp(value: number, minimum: number, maximum: number) {
  'worklet';

  return Math.min(maximum, Math.max(minimum, value));
}

function smoothstep(value: number) {
  'worklet';

  const clamped = clamp(value, 0, 1);

  return clamped * clamped * (3 - 2 * clamped);
}

function integralEaseInOutCubic(value: number) {
  'worklet';

  return value <= 0.5
    ? Math.pow(value, 4)
    : value - 0.5 + Math.pow(1 - value, 4);
}

function hummingSpinAngle(time: number) {
  'worklet';

  if (time <= 0) {
    return 0;
  }

  if (time < 0.5) {
    return 3.5 * integralEaseInOutCubic(time / 0.5);
  }

  const firstStageAngle = 1.75;

  if (time < 1.3) {
    const progress = (time - 0.5) / 0.8;

    return (
      firstStageAngle +
      5.6 * progress -
      4.32 * integralEaseInOutCubic(progress)
    );
  }

  const secondStageAngle = firstStageAngle + 3.44;

  return (
    secondStageAngle +
    1.6 * (time - 1.3) +
    0.6 * (Math.cos(0.65) - Math.cos(0.5 * time))
  );
}

function projectOrbit(
  lambda: number,
  radius: number,
  tilt: number,
  roll: number
) {
  'worklet';

  const horizontal = radius * Math.sin(lambda);
  const vertical = -radius * Math.cos(lambda) * Math.sin(tilt);
  const cosRoll = Math.cos(roll);
  const sinRoll = Math.sin(roll);

  return {
    x: HEAD_CENTER + horizontal * cosRoll - vertical * sinRoll,
    y: HEAD_CENTER + horizontal * sinRoll + vertical * cosRoll,
    z: Math.cos(lambda) * Math.cos(tilt),
  };
}

function trailLifeAt(time: number, spawnDelay: number) {
  'worklet';

  return time - HUMMING_START - 0.3 - spawnDelay;
}

function trailRetractionAt(life: number) {
  'worklet';

  return clamp(
    (life - RETRACTION_START) / RETRACTION_DURATION,
    0,
    1
  );
}

function trailLambdaAt(life: number, config: TrailConfig) {
  'worklet';

  const spawnTime = 0.3 + config.spawnDelay;
  const currentTime = spawnTime + Math.max(life, 0);
  const spinAtSpawn = hummingSpinAngle(spawnTime);

  return (
    spinAtSpawn -
    config.lambdaOffset +
    (hummingSpinAngle(currentTime) - spinAtSpawn) * config.follow +
    config.lambdaVelocity * Math.max(life, 0)
  );
}

function trailVisibleArc(life: number, config: TrailConfig) {
  'worklet';

  if (life <= 0) {
    return 0;
  }

  const initialLambda = trailLambdaAt(0, config);
  const currentLambda = trailLambdaAt(life, config);
  const travelled = Math.abs(currentLambda - initialLambda);
  const retraction = trailRetractionAt(life);
  const retractionEase =
    retraction * retraction * (3 - 2 * retraction);

  return Math.min(
    travelled,
    config.arc * (1 - retractionEase)
  );
}

function hslColor(hue: number, saturation: number, lightness: number) {
  'worklet';

  const normalizedHue = ((hue % 360) + 360) % 360;
  const chroma = (1 - Math.abs(2 * lightness - 1)) * saturation;
  const sector = normalizedHue / 60;
  const secondary = chroma * (1 - Math.abs((sector % 2) - 1));
  let red = 0;
  let green = 0;
  let blue = 0;

  if (sector < 1) {
    red = chroma;
    green = secondary;
  } else if (sector < 2) {
    red = secondary;
    green = chroma;
  } else if (sector < 3) {
    green = chroma;
    blue = secondary;
  } else if (sector < 4) {
    green = secondary;
    blue = chroma;
  } else if (sector < 5) {
    red = secondary;
    blue = chroma;
  } else {
    red = chroma;
    blue = secondary;
  }

  const lightnessOffset = lightness - chroma / 2;

  return [
    red + lightnessOffset,
    green + lightnessOffset,
    blue + lightnessOffset,
    1,
  ];
}

function addRibbonContour(
  builder: SkPathBuilder,
  start: number,
  end: number,
  pointCount: number,
  xs: number[],
  ys: number[],
  leftXs: number[],
  leftYs: number[],
  rightXs: number[],
  rightYs: number[]
) {
  'worklet';

  builder.moveTo(leftXs[start], leftYs[start]);

  for (let index = start + 1; index <= end; index += 1) {
    builder.lineTo(leftXs[index], leftYs[index]);
  }

  if (end === pointCount - 1) {
    const previous = Math.max(end - 1, 0);
    const dx = xs[end] - xs[previous];
    const dy = ys[end] - ys[previous];
    const length = Math.hypot(dx, dy) || 1;
    const tangentX = dx / length;
    const tangentY = dy / length;
    const capRadius =
      Math.hypot(leftXs[end] - xs[end], leftYs[end] - ys[end]) || 0.2;

    builder.quadTo(
      xs[end] + tangentX * capRadius * 2,
      ys[end] + tangentY * capRadius * 2,
      rightXs[end],
      rightYs[end]
    );
  } else {
    builder.lineTo(rightXs[end], rightYs[end]);
  }

  for (let index = end - 1; index >= start; index -= 1) {
    builder.lineTo(rightXs[index], rightYs[index]);
  }

  if (start === 0) {
    const next = Math.min(start + 1, pointCount - 1);
    const dx = xs[next] - xs[start];
    const dy = ys[next] - ys[start];
    const length = Math.hypot(dx, dy) || 1;
    const tangentX = dx / length;
    const tangentY = dy / length;
    const capRadius =
      Math.hypot(leftXs[start] - xs[start], leftYs[start] - ys[start]) ||
      0.2;

    builder.quadTo(
      xs[start] - tangentX * capRadius * 2,
      ys[start] - tangentY * capRadius * 2,
      leftXs[start],
      leftYs[start]
    );
  } else {
    builder.lineTo(leftXs[start], leftYs[start]);
  }

  builder.close();
}

type GrokOrbTrailProps = {
  config: TrailConfig;
  layer: TrailLayer;
  time: SharedValue<number>;
};

function GrokOrbTrail({ config, layer, time }: GrokOrbTrailProps) {
  const path = usePathValue((builder) => {
    'worklet';

    const life = trailLifeAt(time.get(), config.spawnDelay);
    const retraction = trailRetractionAt(life);
    const visibleArc = trailVisibleArc(life, config);

    if (life <= 0 || retraction >= 1 || visibleArc < 0.025) {
      return;
    }

    const currentLambda = trailLambdaAt(life, config);
    const radius = config.radius + config.radiusVelocity * life;
    const pointCount = Math.min(
      48,
      Math.max(2, Math.ceil(visibleArc / MAX_SAMPLE_ANGLE) + 1)
    );
    const xs: number[] = [];
    const ys: number[] = [];
    const zs: number[] = [];
    const leftXs: number[] = [];
    const leftYs: number[] = [];
    const rightXs: number[] = [];
    const rightYs: number[] = [];
    let pathLength = 0;

    for (let index = 0; index < pointCount; index += 1) {
      const progress = index / (pointCount - 1);
      const lambda = currentLambda - visibleArc * (1 - progress);
      const point = projectOrbit(lambda, radius, config.tilt, config.roll);

      xs.push(point.x);
      ys.push(point.y);
      zs.push(point.z);

      if (index > 0) {
        pathLength += Math.hypot(
          point.x - xs[index - 1],
          point.y - ys[index - 1]
        );
      }
    }

    const headDepth = 0.72 + 0.28 * clamp(zs[pointCount - 1], 0, 1);
    const growthProgress = clamp(life / 0.34, 0, 1);
    const growth = smoothstep(growthProgress);
    const rawWidth = Math.max(
      config.baseWidth *
        headDepth *
        1.7 *
        growth *
        (1 - 0.72 * retraction * retraction),
      0.5
    );
    const width = Math.min(rawWidth, pathLength * 0.34);

    for (let index = 0; index < pointCount; index += 1) {
      const previous = Math.max(index - 1, 0);
      const next = Math.min(index + 1, pointCount - 1);
      const dx = xs[next] - xs[previous];
      const dy = ys[next] - ys[previous];
      const length = Math.hypot(dx, dy) || 1;
      const halfWidth =
        (width * (0.5 + 0.5 * (index / (pointCount - 1)))) / 2;
      const normalX = (-dy / length) * halfWidth;
      const normalY = (dx / length) * halfWidth;

      leftXs.push(xs[index] + normalX);
      leftYs.push(ys[index] + normalY);
      rightXs.push(xs[index] - normalX);
      rightYs.push(ys[index] - normalY);
    }

    let groupStart = 0;

    while (groupStart < pointCount) {
      const groupIsFront = zs[groupStart] >= 0;
      let groupEnd = groupStart;

      while (
        groupEnd + 1 < pointCount &&
        (zs[groupEnd + 1] >= 0) === groupIsFront
      ) {
        groupEnd += 1;
      }

      if (groupIsFront === (layer === 'front')) {
        const expandedStart = Math.max(groupStart - 1, 0);
        const expandedEnd = Math.min(groupEnd + 1, pointCount - 1);

        if (expandedEnd > expandedStart) {
          addRibbonContour(
            builder,
            expandedStart,
            expandedEnd,
            pointCount,
            xs,
            ys,
            leftXs,
            leftYs,
            rightXs,
            rightYs
          );
        }
      }

      groupStart = groupEnd + 1;
    }
  });

  const opacity = useDerivedValue(() => {
    const life = trailLifeAt(time.get(), config.spawnDelay);

    if (life <= 0 || trailRetractionAt(life) >= 1) {
      return 0;
    }

    return Math.min(1, life / 0.26);
  });

  const gradientStart = useDerivedValue(() => {
    const life = trailLifeAt(time.get(), config.spawnDelay);
    const visibleArc = trailVisibleArc(life, config);

    if (life <= 0 || visibleArc <= 0) {
      return { x: HEAD_CENTER, y: HEAD_CENTER };
    }

    const radius = config.radius + config.radiusVelocity * life;
    const point = projectOrbit(
      trailLambdaAt(life, config) - visibleArc,
      radius,
      config.tilt,
      config.roll
    );

    return { x: point.x, y: point.y };
  });

  const gradientEnd = useDerivedValue(() => {
    const life = trailLifeAt(time.get(), config.spawnDelay);

    if (life <= 0) {
      return { x: HEAD_CENTER, y: HEAD_CENTER };
    }

    const radius = config.radius + config.radiusVelocity * life;
    const point = projectOrbit(
      trailLambdaAt(life, config),
      radius,
      config.tilt,
      config.roll
    );

    return { x: point.x, y: point.y };
  });

  const colors = useDerivedValue(() => {
    const life = Math.max(trailLifeAt(time.get(), config.spawnDelay), 0);
    const movingHue = config.baseHue + config.hueVelocity * life;

    return [
      hslColor(movingHue, 0.56, 0.56),
      hslColor(movingHue + config.hueSpan * 0.25, 0.56, 0.59),
      hslColor(movingHue + config.hueSpan * 0.5, 0.56, 0.62),
      hslColor(movingHue + config.hueSpan * 0.75, 0.56, 0.64),
      hslColor(movingHue + config.hueSpan, 0.56, 0.67),
    ];
  });

  return (
    <Path opacity={opacity} path={path}>
      <LinearGradient
        colors={colors}
        end={gradientEnd}
        positions={GRADIENT_POSITIONS}
        start={gradientStart}
      />
    </Path>
  );
}

type GrokOrbTrailsProps = {
  layer: TrailLayer;
  time: SharedValue<number>;
};

export function GrokOrbTrails({ layer, time }: GrokOrbTrailsProps) {
  return TRAILS.map((config, index) => (
    <GrokOrbTrail
      config={config}
      key={`${layer}-${index}`}
      layer={layer}
      time={time}
    />
  ));
}
