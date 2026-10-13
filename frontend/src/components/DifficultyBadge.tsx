import type { Difficulty } from '../api/types';
import ui from './ui.module.css';

const LABELS: Record<Difficulty, string> = { EASY: 'Easy', MEDIUM: 'Medium', HARD: 'Hard' };

export function DifficultyBadge({ difficulty }: { difficulty: Difficulty }) {
  return <span className={`${ui.badge} ${ui[difficulty]}`}>{LABELS[difficulty]}</span>;
}
