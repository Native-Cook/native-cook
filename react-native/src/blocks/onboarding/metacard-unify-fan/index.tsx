import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';
import { MetacardUnifyFan } from './MetacardUnifyFan';

export default function MetacardUnifyFanDemo() {
  return <View style={{ flex: 1, backgroundColor: '#000000' }}>
    <StatusBar hidden /><MetacardUnifyFan autoPlay />
  </View>;
}
