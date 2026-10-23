import { StatusBar } from 'expo-status-bar';
import type { ReactNode } from 'react';
import { StyleSheet, View } from 'react-native';

type Props = {
  /** Backdrop colour the component was designed against. */
  background: string;
  /** Height in points of the box the component is laid out in; taller for a large glow. */
  height?: number;
  children: ReactNode;
};

/** One component alone on its backdrop, with nothing else on screen — made for screen recording. */
export function ComponentStage({ background, height = 220, children }: Props) {
  return (
    <View style={[styles.screen, { backgroundColor: background }]}>
      <StatusBar hidden />
      {/* The box the component was designed in, so it lays out the same wherever it is shown. */}
      <View style={[styles.stage, { height }]}>{children}</View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    justifyContent: 'center',
  },
  stage: {
    alignItems: 'center',
    justifyContent: 'center',
  },
});
