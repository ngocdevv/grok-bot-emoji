import { useCallback, useState } from 'react';
import type {
  LayoutChangeEvent,
  StyleProp,
  ViewStyle,
} from 'react-native';
import {
  Pressable,
  StyleSheet,
  useWindowDimensions,
  View,
} from 'react-native';
import Animated, {
  Easing,
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';

import { GrokExpressionPreview } from '@/components/grok-expression-preview';
import GrokOrb from '@/components/grok-orb';
import {
  DEFAULT_GROK_ORB_EXPRESSION,
  GROK_ORB_EXPRESSION_OPTIONS,
} from '@/components/grok-orb-expressions';
import type {
  GrokOrbExpression,
  GrokOrbExpressionOption,
} from '@/components/grok-orb-expressions';

const CONTENT_MAX_WIDTH = 820;
const GRID_GAP = 10;
const PAGE_HORIZONTAL_PADDING = 16;
const PRESS_EASING = Easing.bezier(0.23, 1, 0.32, 1);

export type GrokOrbExpressionPickerProps = {
  accessibilityLabel?: string;
  defaultExpression?: GrokOrbExpression;
  expression?: GrokOrbExpression;
  onExpressionChange?: (expression: GrokOrbExpression) => void;
  paused?: boolean;
  style?: StyleProp<ViewStyle>;
};

type GrokExpressionItemProps = {
  onSelect: (expression: GrokOrbExpression) => void;
  option: GrokOrbExpressionOption;
  selected: boolean;
  size: number;
};

function GrokExpressionItem({
  onSelect,
  option,
  selected,
  size,
}: GrokExpressionItemProps) {
  const reduceMotion = useReducedMotion();
  const pressScale = useSharedValue(1);
  const animatedStyle = useAnimatedStyle(() => ({
    transform: [{ scale: pressScale.get() }],
  }));

  const animatePress = (scale: number, duration: number) => {
    pressScale.set(
      reduceMotion
        ? 1
        : withTiming(scale, {
            duration,
            easing: PRESS_EASING,
          })
    );
  };

  return (
    <Pressable
      aria-checked={selected}
      accessibilityHint={`Chuyển Orb sang biểu cảm ${option.label}`}
      accessibilityLabel={`${option.label}. ${option.description}`}
      accessibilityRole="radio"
      accessibilityState={{ checked: selected }}
      hitSlop={4}
      onPress={() => onSelect(option.id)}
      onPressIn={() => animatePress(0.98, 100)}
      onPressOut={() => animatePress(1, 130)}
      pressRetentionOffset={12}
      style={[styles.pressable, { height: size, width: size }]}>
      <Animated.View
        style={[
          styles.card,
          selected && { borderColor: option.accent },
          animatedStyle,
        ]}>
        <GrokExpressionPreview
          expression={option.id}
          size={Math.max(1, size - 20)}
        />
      </Animated.View>
    </Pressable>
  );
}

export function GrokOrbExpressionPicker({
  accessibilityLabel,
  defaultExpression = DEFAULT_GROK_ORB_EXPRESSION,
  expression,
  onExpressionChange,
  paused = false,
  style,
}: GrokOrbExpressionPickerProps) {
  const { width: windowWidth } = useWindowDimensions();
  const [internalExpression, setInternalExpression] =
    useState<GrokOrbExpression>(defaultExpression);
  const [measuredWidth, setMeasuredWidth] = useState(0);
  const [trailReplayKey, setTrailReplayKey] = useState(0);
  const selectedExpression = expression ?? internalExpression;
  const selectedOption = GROK_ORB_EXPRESSION_OPTIONS.find(
    (option) => option.id === selectedExpression
  )!;
  const fallbackWidth = Math.min(
    CONTENT_MAX_WIDTH,
    Math.max(1, windowWidth - PAGE_HORIZONTAL_PADDING * 2)
  );
  const contentWidth = measuredWidth || fallbackWidth;
  const columnCount =
    contentWidth >= 728
      ? 6
      : contentWidth >= 448
        ? 5
        : contentWidth < 328
          ? 3
          : 4;
  const itemSize = Math.max(
    1,
    (contentWidth - GRID_GAP * (columnCount - 1)) / columnCount
  );
  const orbHeight = Math.min(430, Math.max(320, contentWidth));

  const handleLayout = useCallback((event: LayoutChangeEvent) => {
    const nextWidth = event.nativeEvent.layout.width;

    setMeasuredWidth((currentWidth) =>
      currentWidth === nextWidth ? currentWidth : nextWidth
    );
  }, []);

  const handleSelectExpression = (nextExpression: GrokOrbExpression) => {
    if (expression === undefined) {
      setInternalExpression(nextExpression);
    }

    onExpressionChange?.(nextExpression);

    if (nextExpression === DEFAULT_GROK_ORB_EXPRESSION) {
      setTrailReplayKey((currentKey) => currentKey + 1);
    }
  };

  return (
    <View onLayout={handleLayout} style={[styles.container, style]}>
      <View style={[styles.orbStage, { height: orbHeight }]}>
        <GrokOrb
          accessibilityLabel={
            accessibilityLabel ??
            `Orb đang thể hiện trạng thái ${selectedOption.label}`
          }
          expression={selectedExpression}
          paused={paused}
          style={StyleSheet.absoluteFill}
          trailReplayKey={trailReplayKey}
        />
      </View>

      <View accessibilityRole="radiogroup" style={styles.expressionGrid}>
        {GROK_ORB_EXPRESSION_OPTIONS.map((option) => (
          <GrokExpressionItem
            key={option.id}
            onSelect={handleSelectExpression}
            option={option}
            selected={option.id === selectedExpression}
            size={itemSize}
          />
        ))}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    alignItems: 'center',
    backgroundColor: '#181818',
    borderColor: 'transparent',
    borderCurve: 'continuous',
    borderRadius: 22,
    borderWidth: 2,
    flex: 1,
    justifyContent: 'center',
    overflow: 'hidden',
    padding: 10,
  },
  container: {
    alignSelf: 'center',
    gap: 8,
    maxWidth: CONTENT_MAX_WIDTH,
    width: '100%',
  },
  expressionGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: GRID_GAP,
  },
  orbStage: {
    overflow: 'hidden',
    width: '100%',
  },
  pressable: {
    borderCurve: 'continuous',
    borderRadius: 22,
  },
});
