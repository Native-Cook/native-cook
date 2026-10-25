import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { EtchSketchSignature } from './EtchSketchSignature';

export default function EtchSketchSignatureDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#ECEAE5' }}>
      <StatusBar hidden />
      <EtchSketchSignature autoPlay />
    </View>
  );
}
