import { NotificationStackExpand } from './NotificationStackExpand';

export { LOOP } from './motion';
export { NoticeCard, NoticeFace } from './NoticeCard';
export {
  adoptPose,
  clearAll,
  clearCard,
  NotificationStack,
  restoreStack,
  setOpen,
  useStackMotion,
  type NotificationStackProps,
  type StackMotion,
} from './NotificationStack';
export { NotificationStackExpand, type NotificationStackExpandProps } from './NotificationStackExpand';

export default function NotificationStackExpandDemo() {
  return <NotificationStackExpand autoPlay />;
}
