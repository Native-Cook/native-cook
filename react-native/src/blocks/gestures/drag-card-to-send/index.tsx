import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { DragCardToSend } from './DragCardToSend';
import { COLORS } from './theme';

export default function DragCardToSendDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: COLORS.page }}>
      {/* The pill sits around the Dynamic Island, so the clock and battery would collide with it. */}
      <StatusBar hidden />
      <DragCardToSend autoPlay />
    </View>
  );
}
