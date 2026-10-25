import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { IcingNameForm } from './IcingNameForm';

export default function IcingNameFormDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#F1E9DF' }}>
      <StatusBar hidden />
      <IcingNameForm autoPlay />
    </View>
  );
}
