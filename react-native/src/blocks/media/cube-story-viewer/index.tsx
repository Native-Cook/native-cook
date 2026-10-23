import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { CubeStoryViewer } from './CubeStoryViewer';

export default function CubeStoryViewerDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#000000' }}>
      <StatusBar hidden />
      <CubeStoryViewer autoPlay />
    </View>
  );
}
