import { useCallback, useEffect, useRef, useState } from 'react';
import { Linking } from 'react-native';
import { useCameraPermission } from 'react-native-vision-camera';

/** Requests camera access once and offers Settings when permission is blocked. */
export function useCameraAccess(enabled: boolean) {
  const { hasPermission, canRequestPermission, requestPermission } = useCameraPermission();
  const [requestingPermission, setRequestingPermission] = useState(false);
  const mountedRef = useRef(true);
  const permissionAttemptedRef = useRef(false);

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
    };
  }, []);

  const requestCameraPermission = useCallback(() => {
    if (!canRequestPermission) {
      void Linking.openSettings().catch(() => {
        // Settings can be unavailable under test runners.
      });
      return;
    }

    setRequestingPermission(true);
    void requestPermission()
      .catch(() => false)
      .finally(() => {
        if (mountedRef.current) {
          setRequestingPermission(false);
        }
      });
  }, [canRequestPermission, requestPermission]);

  useEffect(() => {
    if (!enabled || hasPermission || !canRequestPermission || permissionAttemptedRef.current) {
      return;
    }

    permissionAttemptedRef.current = true;
    requestCameraPermission();
  }, [canRequestPermission, enabled, hasPermission, requestCameraPermission]);

  return { hasPermission, canRequestPermission, requestingPermission, requestCameraPermission };
}
