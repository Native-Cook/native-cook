export type Task = {
  id: string;
  title: string;
  meta: string;
  priority?: boolean;
  done?: boolean;
};

export const TASKS: Task[] = [
  { id: 'rent', title: 'Pay the November rent', meta: 'Home · 9:00', done: true },
  { id: 'deck', title: 'Send the Q4 deck to Priya', meta: 'Work · 11:30', priority: true },
  { id: 'plants', title: 'Water the balcony plants', meta: 'Home · 12:00', done: true },
  { id: 'review', title: 'Review the onboarding copy', meta: 'Work · 14:00' },
  { id: 'parcel', title: 'Pick up the parcel', meta: 'Errands · 16:15' },
  { id: 'gym', title: 'Leg day at the gym', meta: 'Health · 18:00' },
  { id: 'groceries', title: 'Buy oat milk and eggs', meta: 'Errands · 18:45' },
  { id: 'call', title: 'Call Mum about Sunday', meta: 'Home · 20:00' },
];

export const LISTS = ['Inbox', 'Home', 'Work', 'Errands'];

/** What the autoPlay loop types and adds. */
export const SCRIPT = { title: 'Book the dentist', meta: 'Home · Today', priority: true };
