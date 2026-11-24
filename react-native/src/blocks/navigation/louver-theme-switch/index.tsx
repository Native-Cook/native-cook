import { useCallback, useMemo, useState } from 'react';

import { AlmanacSettings, type AlmanacExtras, type AlmanacPrefs } from './AlmanacSettings';
import { LouverThemeSwitch } from './LouverThemeSwitch';

export default function LouverThemeSwitchDemo() {
  const [prefs, setPrefs] = useState<AlmanacPrefs>({ weekNumbers: true, declined: false, travel: true });
  const setPref = useCallback((key: keyof AlmanacPrefs, value: boolean) => setPrefs((p) => ({ ...p, [key]: value })), []);
  const screenProps = useMemo<AlmanacExtras>(() => ({ prefs, setPref }), [prefs, setPref]);
  return <LouverThemeSwitch autoPlay Screen={AlmanacSettings} screenProps={screenProps} contentKey={`${prefs.weekNumbers}-${prefs.declined}-${prefs.travel}`} />;
}
