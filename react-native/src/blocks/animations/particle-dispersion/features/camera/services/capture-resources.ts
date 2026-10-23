import type { SubjectSegmentationModule } from '../../../native/subject-segmentation';

type CaptureResourceClient = Pick<SubjectSegmentationModule, 'cancel' | 'release'>;

/** Owns temporary files for one mounted camera session, grouped by capture. */
export function createCaptureResources(client: CaptureResourceClient) {
  const requests = new Map<string, Set<string>>();

  async function releaseUris(uris: string[]) {
    if (uris.length === 0) {
      return;
    }

    try {
      await client.release(uris);
    } catch {
      // The OS also removes temporary files; cleanup must never block the UI.
    }
  }

  async function release(requestId: string) {
    const resources = requests.get(requestId);
    requests.delete(requestId);
    await releaseUris(resources ? [...resources] : []);
  }

  async function cancel(requestId: string, releaseImmediately: boolean) {
    try {
      await client.cancel(requestId);
    } catch {
      // Vision may already have completed the request.
    }

    if (releaseImmediately) {
      await release(requestId);
    }
  }

  return {
    begin(requestId: string) {
      requests.set(requestId, new Set());
    },
    register(requestId: string, uri: string) {
      const resources = requests.get(requestId);
      if (!resources) {
        return false;
      }

      resources.add(uri);
      return true;
    },
    release,
    cancel,
    releaseLateUri(uri: string) {
      return releaseUris([uri]);
    },
    dispose() {
      for (const requestId of requests.keys()) {
        void cancel(requestId, true);
      }
    },
  };
}
