export type OwnedGalleryItem = {
  cardId: string;
  count: number;
  id: string;
  image: number;
  kind: 'owned';
  name: string;
};

export type LockedGalleryItem = {
  id: string;
  kind: 'locked';
  lockCount: number;
};

export type GalleryItem = OwnedGalleryItem | LockedGalleryItem;
