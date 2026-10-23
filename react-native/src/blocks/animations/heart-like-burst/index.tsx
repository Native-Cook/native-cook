import React from 'react';
import { StyleSheet, View } from 'react-native';
import { XHeartAnimation } from './components/x-heart-animation';

export default function HeartLikeBurstDemo() {
  return (
    <View style={styles.fill}>
      <View style={styles.fill}>
        <XHeartAnimation
          initialLiked={false}
          onToggle={(liked) => {
            console.log('Liked:', liked);
          }}
        />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  fill: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
    backgroundColor:"black"
  },
});
