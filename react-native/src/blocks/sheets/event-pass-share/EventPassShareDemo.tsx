import { setStringAsync } from 'expo-clipboard';
import { Directory, File, Paths } from 'expo-file-system';
import * as Sharing from 'expo-sharing';
import { Platform, Share } from 'react-native';

import { EventPassShare, EVENT_PASS_SHARE_DETAILS } from './EventPassShare';

let exportSequence = 0;

async function copyEventDetails(): Promise<void> {
  const copied = await setStringAsync(EVENT_PASS_SHARE_DETAILS);
  if (!copied) throw new Error('The clipboard did not accept the event details.');
}

async function shareEventDetails(): Promise<void> {
  await Share.share({
    title: 'Paws, People & Purpose',
    message: EVENT_PASS_SHARE_DETAILS,
  });
}

async function shareEventPassImage(pngBase64: string): Promise<void> {
  if (!(await Sharing.isAvailableAsync())) throw new Error('Image sharing is unavailable on this device.');
  const base64 = pngBase64.replace(/^data:image\/png;base64,/i, '').replace(/\s/g, '');
  if (!base64) throw new Error('The event pass image data is empty.');
  const directory = new Directory(Paths.cache, 'event-pass-share');
  directory.create({ idempotent: true });
  const now = Date.now();
  // Android resolves when the chooser returns, before a recipient necessarily reads the URI.
  // Retain recent exports and prune only this block's older cache files on the next export.
  for (const entry of directory.list()) {
    if (!(entry instanceof File)) continue;
    const timestamp = /^event-pass-(\d+)-\d+\.png$/.exec(entry.name)?.[1];
    if (timestamp && now - Number(timestamp) > 24 * 60 * 60 * 1000) {
      try { entry.delete(); } catch { /* Cache cleanup must not prevent sharing. */ }
    }
  }
  const filename = `event-pass-${now}-${exportSequence++}.png`;
  const file = new File(directory, filename);
  file.create();
  try {
    file.write(base64, { encoding: 'base64' });
    await Sharing.shareAsync(file.uri, {
      dialogTitle: 'Share event pass',
      mimeType: 'image/png',
      UTI: 'public.png',
    });
  } finally {
    if (Platform.OS !== 'android' && file.exists) {
      try {
        file.delete();
      } catch {
        // The cache is temporary; preserve the share result if cleanup itself fails.
      }
    }
  }
}

export function EventPassShareDemo() {
  return (
    <EventPassShare
      autoPlay
      onCopyDetails={copyEventDetails}
      onShareEvent={shareEventDetails}
      onExportImage={shareEventPassImage}
    />
  );
}
