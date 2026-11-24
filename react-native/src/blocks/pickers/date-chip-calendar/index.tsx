import { HarborBooking } from './HarborBooking';

export { DateChipCalendar, useDateChipMotion, type DateChipCalendarProps, type DateChipMotion, type DateRange } from './DateChipCalendar';
export { HarborBooking, type HarborBookingProps } from './HarborBooking';
export { LOOP } from './motion';

export default function DateChipCalendarDemo() {
  return <HarborBooking autoPlay />;
}
