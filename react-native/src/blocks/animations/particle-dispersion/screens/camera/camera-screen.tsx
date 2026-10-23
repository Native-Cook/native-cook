import { PlatformMessage } from '../../components/platform-message';

/** Metro selects the native implementation on Android and iOS. */
export function CameraScreen() {
  return (
    <PlatformMessage
      body="The camera and particle renderer require an Android or iOS development build."
      title="Native app required"
    />
  );
}
