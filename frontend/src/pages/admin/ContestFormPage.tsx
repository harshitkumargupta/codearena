import { useEffect, useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router';
import { FieldError } from '../../admin/FieldError';
import { CONTEST_LABELS, LIMITS, toContestRequest, validateContest, type ContestField, type ContestForm, type FormErrors } from '../../admin/validation';
import { fetchAllPublishedProblems } from '../../api/allProblems';
import { ApiError } from '../../api/client';
import { api } from '../../api/endpoints';
import type { ProblemSummary } from '../../api/types';
import ui from '../../components/ui.module.css';
import styles from './admin.module.css';

function toLocalInput(time: number): string {
  const d = new Date(time);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function initialForm(): ContestForm {
  const start = Math.ceil(Date.now() / 3_600_000) * 3_600_000 + 3_600_000;
  return { title: '', start: toLocalInput(start), end: toLocalInput(start + 2 * 3_600_000), penaltyMinutes: '20', problems: [{ label: 'A', problemId: '' }] };
}

function utcOffset(): string {
  const minutes = -new Date().getTimezoneOffset();
  const sign = minutes >= 0 ? '+' : '-';
  const abs = Math.abs(minutes);
  return `UTC${sign}${String(Math.floor(abs / 60)).padStart(2, '0')}:${String(abs % 60).padStart(2, '0')}`;
}

export function ContestFormPage() {
  const navigate = useNavigate();
  const [form, setForm] = useState<ContestForm>(initialForm);
  const [problems, setProblems] = useState<ProblemSummary[] | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [errors, setErrors] = useState<FormErrors<ContestField>>({});
  const [serverError, setServerError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    let cancelled = false;
    fetchAllPublishedProblems()
      .then((all) => {
        if (!cancelled) setProblems(all);
      })
      .catch(() => {
        if (!cancelled) setLoadError('Could not load published problems.');
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const set = <K extends keyof ContestForm>(field: K, value: ContestForm[K]) => setForm((f) => ({ ...f, [field]: value }));
  const setRow = (index: number, patch: Partial<ContestForm['problems'][number]>) =>
    set('problems', form.problems.map((row, i) => (i === index ? { ...row, ...patch } : row)));
  const addRow = () => {
    const used = new Set(form.problems.map((p) => p.label));
    const label = CONTEST_LABELS.find((l) => !used.has(l)) ?? 'A';
    set('problems', [...form.problems, { label, problemId: '' }]);
  };

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault();
    const found = validateContest(form);
    setErrors(found);
    setServerError(null);
    if (Object.keys(found).length > 0) return;
    setSaving(true);
    try {
      const created = await api.admin.createContest(toContestRequest(form));
      void navigate(`/contests/${created.id}`);
    } catch (e) {
      if (e instanceof ApiError && e.fieldErrors.length > 0) {
        const mapped: FormErrors<ContestField> = {};
        e.fieldErrors.forEach((f) => {
          const key = (f.field.startsWith('problems') ? 'problems' : f.field === 'startTime' ? 'start' : f.field === 'endTime' ? 'end' : f.field) as ContestField;
          mapped[key] = f.message;
        });
        setErrors(mapped);
      } else {
        setServerError(e instanceof ApiError ? e.message : 'Could not create the contest.');
      }
    } finally {
      setSaving(false);
    }
  };

  const field = (name: ContestField) => ({
    id: `contest-${name}`,
    'aria-invalid': errors[name] ? true : undefined,
    'aria-describedby': errors[name] ? `contest-${name}-error` : undefined,
  });

  return (
    <form className={ui.card} onSubmit={(e) => void onSubmit(e)} noValidate aria-labelledby="contest-form-title">
      <h1 id="contest-form-title">New contest</h1>
      <div className={ui.field}>
        <label htmlFor="contest-title">Title</label>
        <input {...field('title')} value={form.title} maxLength={LIMITS.title} onChange={(e) => set('title', e.target.value)} />
        <FieldError id="contest-title-error" message={errors.title} />
      </div>
      <div className={styles.grid}>
        <div className={ui.field}>
          <label htmlFor="contest-start">Start ({utcOffset()})</label>
          <input {...field('start')} type="datetime-local" step={1} value={form.start} onChange={(e) => set('start', e.target.value)} />
          <FieldError id="contest-start-error" message={errors.start} />
        </div>
        <div className={ui.field}>
          <label htmlFor="contest-end">End ({utcOffset()})</label>
          <input {...field('end')} type="datetime-local" step={1} value={form.end} onChange={(e) => set('end', e.target.value)} />
          <FieldError id="contest-end-error" message={errors.end} />
        </div>
      </div>
      <div className={ui.field}>
        <label htmlFor="contest-penaltyMinutes">Penalty per wrong attempt (minutes)</label>
        <input
          {...field('penaltyMinutes')}
          inputMode="numeric"
          value={form.penaltyMinutes}
          onChange={(e) => set('penaltyMinutes', e.target.value)}
        />
        <FieldError id="contest-penaltyMinutes-error" message={errors.penaltyMinutes} />
      </div>
      <fieldset className={styles.row} aria-describedby={errors.problems ? 'contest-problems-error' : undefined}>
        <legend>Problems</legend>
        {loadError && <p className={ui.error}>{loadError}</p>}
        {!loadError && problems === null && <p className={ui.hint}>Loading published problems…</p>}
        {problems &&
          form.problems.map((row, index) => (
            <div key={index} className={styles.problemRow}>
              <div className={ui.field} style={{ marginBottom: 0 }}>
                <label htmlFor={`contest-label-${index}`}>Label</label>
                <select id={`contest-label-${index}`} value={row.label} onChange={(e) => setRow(index, { label: e.target.value })}>
                  {CONTEST_LABELS.map((l) => (
                    <option key={l} value={l}>
                      {l}
                    </option>
                  ))}
                </select>
              </div>
              <div className={ui.field} style={{ marginBottom: 0 }}>
                <label htmlFor={`contest-problem-${index}`}>Problem {row.label}</label>
                <select id={`contest-problem-${index}`} value={row.problemId} onChange={(e) => setRow(index, { problemId: e.target.value })}>
                  <option value="">Choose a published problem…</option>
                  {problems.map((p) => (
                    <option key={p.id} value={String(p.id)}>
                      {p.title}
                    </option>
                  ))}
                </select>
              </div>
              <button
                type="button"
                className={`${ui.button} ${ui.secondary}`}
                disabled={form.problems.length === 1}
                aria-label={`Remove problem ${row.label}`}
                onClick={() => set('problems', form.problems.filter((_, i) => i !== index))}
              >
                Remove
              </button>
            </div>
          ))}
        <FieldError id="contest-problems-error" message={errors.problems} />
        <button
          type="button"
          className={`${ui.button} ${ui.secondary}`}
          disabled={form.problems.length >= LIMITS.contestProblems}
          onClick={addRow}
        >
          Add problem
        </button>
      </fieldset>
      {serverError && (
        <p className={ui.error} role="alert">
          {serverError}
        </p>
      )}
      <div className={styles.actions}>
        <button type="submit" className={ui.button} disabled={saving || problems === null}>
          {saving ? 'Creating…' : 'Create contest'}
        </button>
        <Link to="/admin">Back to admin</Link>
      </div>
    </form>
  );
}
