import {
  SensorType,
  type SharedValue,
  useAnimatedReaction,
  useAnimatedSensor,
  useSharedValue,
} from "react-native-reanimated";
import type { Tilt } from "./device-art";

// Mounted only while the tilt effect is on and the screen is focused, so the
// rotation sensor is not running otherwise. Writes a normalized tilt (-1…1)
// relative to a slowly following baseline: highlights answer a movement of
// the phone, then drift back to rest for however the phone is being held.
export function TiltSource({ tilt }: { tilt: SharedValue<Tilt> }) {
  const sensor = useAnimatedSensor(SensorType.ROTATION, { interval: 16 });
  const base = useSharedValue({ roll: 0, pitch: 0, ready: false });
  useAnimatedReaction(
    () => sensor.sensor.get(),
    (r) => {
      const b = base.get();
      const roll = b.ready ? b.roll + (r.roll - b.roll) * 0.02 : r.roll;
      const pitch = b.ready ? b.pitch + (r.pitch - b.pitch) * 0.02 : r.pitch;
      base.set({ roll, pitch, ready: true });
      tilt.set({
        x: Math.max(-1, Math.min(1, (r.roll - roll) / 0.35)),
        y: Math.max(-1, Math.min(1, (r.pitch - pitch) / 0.35)),
      });
    },
  );
  return null;
}
