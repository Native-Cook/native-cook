import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';
import { GrowthOdometerTimeline } from './GrowthOdometerTimeline';
export default function GrowthOdometerTimelineDemo() {
  return <View style={{
    flex: 1,
    backgroundColor: '#000000'
  }}>
    <StatusBar hidden />
    <GrowthOdometerTimeline autoPlay />
  </View>;
}
