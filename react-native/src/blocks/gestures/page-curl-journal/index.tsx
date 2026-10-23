import { QA_HOLD, QA_SPEED, QA_START } from './motion';
import { PageCurlJournal } from './PageCurlJournal';

export default function PageCurlJournalDemo() {
  // A QA setting remounts the block in that state.
  return <PageCurlJournal key={`${String(QA_HOLD)}-${QA_SPEED}-${QA_START}`} autoPlay />;
}
