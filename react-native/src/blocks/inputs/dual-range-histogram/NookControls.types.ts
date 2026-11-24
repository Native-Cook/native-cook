export type NookControlsProps = {
  width: number;
  placeType: number;
  bedrooms: number;
  amenities: readonly boolean[];
  /** How many amenity rows fit on this screen. */
  amenityRows: number;
  onPlaceType: (index: number) => void;
  onBedrooms: (index: number) => void;
  onAmenity: (index: number, on: boolean) => void;
};

export type NookPlacePickerProps = Pick<NookControlsProps, 'width' | 'placeType' | 'onPlaceType'>;
