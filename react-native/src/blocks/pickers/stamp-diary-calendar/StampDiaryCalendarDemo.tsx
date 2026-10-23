import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { StampDiaryCalendar } from './StampDiaryCalendar';

export default function StampDiaryCalendarDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#0B0B0B' }}>
      <StatusBar style="light" />
      <StampDiaryCalendar autoPlay />
    </View>
  );
}
