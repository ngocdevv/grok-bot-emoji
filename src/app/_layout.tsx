import { DarkTheme, Stack, ThemeProvider } from 'expo-router';
import { StatusBar } from 'expo-status-bar';

const orbTheme = {
  ...DarkTheme,
  colors: {
    ...DarkTheme.colors,
    background: '#0b0b0b',
    border: '#242424',
    card: '#181818',
  },
};

export default function RootLayout() {
  return (
    <ThemeProvider value={orbTheme}>
      <Stack
        screenOptions={{
          animation: 'none',
          contentStyle: { backgroundColor: '#0b0b0b' },
          headerShown: false,
        }}
      />
      <StatusBar hidden />
    </ThemeProvider>
  );
}
