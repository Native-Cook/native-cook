import { StyleSheet, View } from 'react-native';
import { ComponentStage } from '@/shared/component-stage';

import { FlightProgress } from './FlightProgress';

export default function FlightProgressDemo() {
  return (
    <ComponentStage background="#000000" height={440}>
      <View style={styles.stack}>
        <FlightProgress autoPlay />
        <FlightProgress autoPlay tint="orange" />
      </View>
    </ComponentStage>
  );
}

const styles = StyleSheet.create({
  stack: {
    gap: 22,
  },
});
