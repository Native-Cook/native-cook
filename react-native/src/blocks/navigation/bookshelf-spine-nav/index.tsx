import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { BookshelfSpineNav } from './BookshelfSpineNav';

export default function BookshelfSpineNavDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#17120F' }}>
      <StatusBar hidden />
      <BookshelfSpineNav autoPlay />
    </View>
  );
}
