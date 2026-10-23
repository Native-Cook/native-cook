import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { RaceResultStandings } from './RaceResultStandings';

export default function RaceResultStandingsDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#020002' }}>
      <StatusBar style="light" />
      <RaceResultStandings autoPlay />
    </View>
  );
}
