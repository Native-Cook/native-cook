import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';
import { GlassPeakChart } from './GlassPeakChart';

// Present the self-playing glass object on a completely black, full-screen page.
export default function GlassPeakChartDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#000000' }}>
      <StatusBar hidden />
      <GlassPeakChart autoPlay />
    </View>
  );
}
