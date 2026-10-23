import { ComponentStage } from '@/shared/component-stage';

import { TripAlbumFolder } from './TripAlbumFolder';

export default function TripAlbumFolderDemo() {
  return (
    <ComponentStage background="#f5f5f6" height={440}>
      <TripAlbumFolder autoPlay />
    </ComponentStage>
  );
}
