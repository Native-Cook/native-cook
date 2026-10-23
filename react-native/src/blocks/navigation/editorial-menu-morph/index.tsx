import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { EditorialMenuMorph } from './EditorialMenuMorph';

export default function EditorialMenuMorphDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#efe9df' }}>
      <StatusBar hidden />
      <EditorialMenuMorph autoPlay />
    </View>
  );
}
