import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { OrigamiFoldList } from './OrigamiFoldList';

export default function OrigamiFoldListDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#071519' }}>
      <StatusBar hidden />
      <OrigamiFoldList autoPlay />
    </View>
  );
}
