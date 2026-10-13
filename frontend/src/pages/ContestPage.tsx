import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router';
import { ApiError } from '../api/client';
import { api } from '../api/endpoints';
import type { ContestDetail, ProblemDetail } from '../api/types';
import { useAuth } from '../auth/useAuth';
import { ContestStatusBadge } from '../components/ContestStatusBadge';
import { Countdown } from '../components/Countdown';
import { LiveLeaderboard } from '../components/Leaderboard';
import { ProblemWorkspace } from '../components/ProblemWorkspace';
import ui from '../components/ui.module.css';
import { formatDateTime } from '../time/contestClock';
import styles from './ContestPage.module.css';

const STATUS_RECHECK_MS = 3000;

export function ContestPage() {
  const { id = '' } = useParams();
  const contestId = Number(id);
  const { session } = useAuth();
  const [contest, setContest] = useState<ContestDetail | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [reload, setReload] = useState(0);
  const [joined, setJoined] = useState(false);
  const [joinError, setJoinError] = useState<string | null>(null);
  const [joining, setJoining] = useState(false);
  const [selected, setSelected] = useState<string | null>(null);
  const [problems, setProblems] = useState<Record<string, ProblemDetail | 'error'>>({});

  useEffect(() => {
    let cancelled = false;
    if (!Number.isInteger(contestId) || contestId <= 0) return undefined;
    api.contests
      .get(contestId)
      .then((c) => {
        if (!cancelled) {
          setContest(c);
          setLoadError(null);
        }
      })
      .catch((e: unknown) => {
        if (!cancelled) setLoadError(e instanceof ApiError && e.status === 404 ? 'Contest not found.' : 'Could not load the contest.');
      });
    return () => {
      cancelled = true;
    };
  }, [contestId, reload]);

  const refresh = useCallback(() => setReload((r) => r + 1), []);

  useEffect(() => {
    if (!contest) return undefined;
    const clientSaysStarted = Date.now() >= Date.parse(contest.startTime);
    const clientSaysEnded = Date.now() >= Date.parse(contest.endTime);
    const behind = (contest.status === 'UPCOMING' && clientSaysStarted) || (contest.status === 'RUNNING' && clientSaysEnded);
    if (!behind) return undefined;
    const timer = window.setTimeout(refresh, STATUS_RECHECK_MS);
    return () => window.clearTimeout(timer);
  }, [contest, refresh]);

  const activeLabel = contest?.problemsVisible ? (selected ?? contest.problems[0]?.label ?? null) : null;
  const activeProblem = contest?.problems.find((p) => p.label === activeLabel) ?? null;
  const activeSlug = activeProblem?.slug ?? null;
  const activeLoaded = activeSlug ? problems[activeSlug] : undefined;

  useEffect(() => {
    if (!activeSlug || activeLoaded !== undefined || !session) return undefined;
    let cancelled = false;
    api.problems
      .get(activeSlug)
      .then((p) => {
        if (!cancelled) setProblems((all) => ({ ...all, [activeSlug]: p }));
      })
      .catch(() => {
        if (!cancelled) setProblems((all) => ({ ...all, [activeSlug]: 'error' }));
      });
    return () => {
      cancelled = true;
    };
  }, [activeSlug, activeLoaded, session]);

  const markJoined = useCallback((member: boolean) => {
    if (member) setJoined(true);
  }, []);

  const join = async () => {
    setJoining(true);
    setJoinError(null);
    try {
      await api.contests.join(contestId);
      setJoined(true);
    } catch (e) {
      setJoinError(e instanceof ApiError ? e.message : 'Could not join the contest.');
    } finally {
      setJoining(false);
    }
  };

  if (loadError) {
    return (
      <div className={ui.card}>
        <p className={ui.error}>{loadError}</p>
        <Link to="/contests">Back to contests</Link>
      </div>
    );
  }
  if (!contest) return <p className={ui.hint}>Loading contest…</p>;

  const { status } = contest;
  const ended = status === 'ENDED';

  return (
    <>
      <section className={ui.card} aria-labelledby="contest-title" style={{ marginBottom: '1.25rem' }}>
        <div className={styles.header}>
          <h1 id="contest-title">{contest.title}</h1>
          <ContestStatusBadge status={status} />
          <span className={ui.hint}>
            {formatDateTime(contest.startTime)} – {formatDateTime(contest.endTime)} · penalty {contest.penaltyMinutes} min per wrong
            attempt
          </span>
          {!ended && session && joined && <span className={styles.joined}>Joined</span>}
          {!ended && session && !joined && (
            <button type="button" className={`${ui.button} ${styles.join}`} disabled={joining} onClick={() => void join()}>
              {joining ? 'Joining…' : 'Join contest'}
            </button>
          )}
          {!ended && !session && (
            <Link className={styles.join} to="/login" state={{ from: `/contests/${contest.id}` }}>
              Log in to join
            </Link>
          )}
        </div>
        {status === 'UPCOMING' && <Countdown target={contest.startTime} label="Starts in" onElapsed={refresh} />}
        {status === 'RUNNING' && <Countdown target={contest.endTime} label="Ends in" onElapsed={refresh} />}
        {ended && <p>This contest has ended. Results are final and submissions are closed.</p>}
        {joinError && (
          <p className={ui.error} role="alert">
            {joinError}
          </p>
        )}
      </section>

      <div className={styles.grid}>
        <div>
          {status === 'UPCOMING' && (
            <div className={ui.card}>
              <p>Problems will appear here when the contest starts{joined ? '' : '. Join now to take part'}.</p>
            </div>
          )}
          {contest.problemsVisible && contest.problems.length > 0 && (
            <>
              <div role="tablist" aria-label="Contest problems" className={styles.tabs}>
                {contest.problems.map((p) => (
                  <button
                    key={p.label}
                    type="button"
                    role="tab"
                    id={`tab-${p.label}`}
                    aria-selected={p.label === activeLabel}
                    aria-controls="contest-problem"
                    className={styles.tab}
                    onClick={() => setSelected(p.label)}
                  >
                    {p.label}. {p.title}
                  </button>
                ))}
              </div>
              <div role="tabpanel" id="contest-problem" aria-labelledby={`tab-${activeLabel ?? ''}`}>
                {!session && (
                  <div className={ui.card}>
                    <Link to="/login" state={{ from: `/contests/${contest.id}` }}>
                      Log in
                    </Link>{' '}
                    to see and solve the problems.
                  </div>
                )}
                {session && activeLoaded === 'error' && <p className={ui.error}>Could not load this problem.</p>}
                {session && activeLoaded === undefined && <p className={ui.hint}>Loading problem…</p>}
                {session && activeLoaded && activeLoaded !== 'error' && activeProblem && (
                  <ProblemWorkspace
                    key={`${contest.id}:${activeProblem.problemId}:${ended}`}
                    problem={activeLoaded}
                    contestId={contest.id}
                    label={activeProblem.label}
                    readOnly={ended}
                    notice={
                      ended ? (
                        <p className={ui.notice}>
                          Read-only: this contest has ended. You can still practise it from the{' '}
                          <Link to={`/problems/${activeProblem.slug}`}>problem page</Link>.
                        </p>
                      ) : !joined ? (
                        <p className={ui.notice} role="status">
                          You have not joined this contest yet. Join it to have your submissions count.
                        </p>
                      ) : undefined
                    }
                  />
                )}
              </div>
            </>
          )}
        </div>
        <LiveLeaderboard contestId={contest.id} currentUserId={session?.user.id ?? null} onMembership={markJoined} />
      </div>
    </>
  );
}
