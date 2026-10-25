import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { CardCatalogList } from './CardCatalogList';

export default function CardCatalogListDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#1C130D' }}>
      <StatusBar hidden />
      <CardCatalogList autoPlay />
    </View>
  );
}
