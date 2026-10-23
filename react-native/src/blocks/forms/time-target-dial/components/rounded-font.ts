import { Platform, type TextStyle } from 'react-native';

// The system's rounded bold face: SF Pro Rounded on iOS, the default bold face elsewhere.
export const roundedBold: TextStyle =
  Platform.OS === 'ios' ? { fontFamily: 'ui-rounded', fontWeight: '700' } : { fontWeight: '700' };
