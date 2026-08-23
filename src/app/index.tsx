import { ScrollView, StyleSheet } from 'react-native';

import { GrokOrbExpressionPicker } from '@/components/grok-orb-expression-picker';

export default function HomeScreen() {
  return (
    <ScrollView
      contentContainerStyle={styles.scrollContent}
      contentInsetAdjustmentBehavior="automatic"
      showsVerticalScrollIndicator={false}
      style={styles.screen}>
      <GrokOrbExpressionPicker />
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  screen: {
    backgroundColor: '#0b0b0b',
    flex: 1,
  },
  scrollContent: {
    paddingBottom: 36,
    paddingHorizontal: 16,
    paddingTop: 8,
  },
});
