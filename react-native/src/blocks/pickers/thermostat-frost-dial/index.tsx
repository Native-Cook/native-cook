import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { ThermostatFrostDial } from './ThermostatFrostDial';

export default function ThermostatFrostDialDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#3A3530' }}>
      <StatusBar hidden />
      <ThermostatFrostDial autoPlay />
    </View>
  );
}
