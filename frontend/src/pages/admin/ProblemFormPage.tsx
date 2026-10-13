import { useEffect, useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router';
import { FieldError } from '../../admin/FieldError';
import { LIMITS, slugify, toProblemRequest, validateProblem, type FormErrors, type ProblemField, type ProblemForm } from '../../admin/validation';
import { ApiError } from '../../api/client';
import { api } from '../../api/endpoints';
import { DIFFICULTIES, type AdminProblemResponse, type Difficulty } from '../../api/types';
import { Markdown } from '../../components/Markdown';
import ui from '../../components/ui.module.css';
import styles from './admin.module.css';

const EMPTY: ProblemForm = {
  title: '',
  slug: '',
  statementMd: '',
  difficulty: 'EASY',
  timeLimitMs: '2000',
  memoryLimitMb: '256',
  published: false,
};

interface LocationState {
  problem?: AdminProblemResponse;
  created?: boolean;
}

function toForm(p: Pick<AdminProblemResponse, keyof ProblemForm | 'timeLimitMs' | 'memoryLimitMb'>): ProblemForm {
  return {
    title: p.title,
    slug: p.slug,
    statementMd: p.statementMd,
    difficulty: p.difficulty,
    timeLimitMs: String(p.timeLimitMs),
    memoryLimitMb: String(p.memoryLimitMb),
    published: p.published,
  };
}

function ProblemFormContent() {
  const { slug } = useParams();
  const editing = slug !== undefined;
  const navigate = useNavigate();
  const state = (useLocation().state ?? {}) as LocationState;
  const fromState = editing && state.problem?.slug === slug ? state.problem : null;
  const [problemId, setProblemId] = useState<number | null>(fromState?.id ?? null);
  const [form, setForm] = useState<ProblemForm>(fromState ? toForm(fromState) : EMPTY);
  const [loading, setLoading] = useState(editing && !fromState);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [errors, setErrors] = useState<FormErrors<ProblemField>>({});
  const [serverError, setServerError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [savedMessage, setSavedMessage] = useState<string | null>(state.created ? 'Problem created.' : null);
  const [slugTouched, setSlugTouched] = useState(editing);

  useEffect(() => {
    if (!editing || fromState || !slug) return undefined;
    let cancelled = false;
    api.problems
      .get(slug)
      .then((p) => {
        if (cancelled) return;
        setProblemId(p.id);
        setForm(toForm({ ...p, published: true }));
        setLoading(false);
      })
      .catch((e: unknown) => {
        if (cancelled) return;
        setLoading(false);
        setLoadError(
          e instanceof ApiError && e.status === 404
            ? 'Problem not found. Unpublished problems cannot be loaded for editing (the api has no admin read endpoint).'
            : 'Could not load the problem.',
        );
      });
    return () => {
      cancelled = true;
    };
  }, [editing, fromState, slug]);

  const set = <K extends ProblemField>(field: K, value: ProblemForm[K]) => {
    setForm((f) => {
      const next = { ...f, [field]: value };
      if (field === 'title' && !slugTouched) next.slug = slugify(String(value));
      return next;
    });
    setSavedMessage(null);
  };

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault();
    const found = validateProblem(form);
    setErrors(found);
    setServerError(null);
    if (Object.keys(found).length > 0) return;
    setSaving(true);
    try {
      const request = toProblemRequest(form);
      if (editing && problemId !== null) {
        const saved = await api.admin.updateProblem(problemId, request);
        setSavedMessage('Saved.');
        if (saved.slug !== slug) void navigate(`/admin/problems/${saved.slug}/edit`, { replace: true, state: { problem: saved } });
      } else {
        const created = await api.admin.createProblem(request);
        void navigate(`/admin/problems/${created.slug}/edit`, { replace: true, state: { problem: created, created: true } });
      }
    } catch (e) {
      if (e instanceof ApiError && e.fieldErrors.length > 0) {
        setErrors(Object.fromEntries(e.fieldErrors.map((f) => [f.field, f.message])));
      } else {
        setServerError(e instanceof ApiError ? e.message : 'Could not save the problem.');
      }
    } finally {
      setSaving(false);
    }
  };

  if (loading) return <p className={ui.hint}>Loading problem…</p>;
  if (loadError) {
    return (
      <div className={ui.card}>
        <p className={ui.error}>{loadError}</p>
        <Link to="/admin">Back to admin</Link>
      </div>
    );
  }

  const field = (name: ProblemField) => ({
    id: `problem-${name}`,
    'aria-invalid': errors[name] ? true : undefined,
    'aria-describedby': errors[name] ? `problem-${name}-error` : undefined,
  });

  return (
    <form className={ui.card} onSubmit={(e) => void onSubmit(e)} noValidate aria-labelledby="problem-form-title">
      <h1 id="problem-form-title">{editing ? `Edit problem: ${form.title}` : 'New problem'}</h1>
      <div className={styles.grid}>
        <div className={ui.field}>
          <label htmlFor="problem-title">Title</label>
          <input {...field('title')} value={form.title} maxLength={LIMITS.title} onChange={(e) => set('title', e.target.value)} />
          <FieldError id="problem-title-error" message={errors.title} />
        </div>
        <div className={ui.field}>
          <label htmlFor="problem-slug">Slug</label>
          <input
            {...field('slug')}
            value={form.slug}
            onChange={(e) => {
              setSlugTouched(true);
              set('slug', e.target.value);
            }}
          />
          <span className={ui.hint}>Used in the URL, for example two-sum</span>
          <FieldError id="problem-slug-error" message={errors.slug} />
        </div>
        <div className={ui.field}>
          <label htmlFor="problem-difficulty">Difficulty</label>
          <select {...field('difficulty')} value={form.difficulty} onChange={(e) => set('difficulty', e.target.value as Difficulty)}>
            {DIFFICULTIES.map((d) => (
              <option key={d} value={d}>
                {d.charAt(0) + d.slice(1).toLowerCase()}
              </option>
            ))}
          </select>
        </div>
        <div className={styles.grid}>
          <div className={ui.field}>
            <label htmlFor="problem-timeLimitMs">Time limit (ms)</label>
            <input {...field('timeLimitMs')} inputMode="numeric" value={form.timeLimitMs} onChange={(e) => set('timeLimitMs', e.target.value)} />
            <FieldError id="problem-timeLimitMs-error" message={errors.timeLimitMs} />
          </div>
          <div className={ui.field}>
            <label htmlFor="problem-memoryLimitMb">Memory limit (MB)</label>
            <input
              {...field('memoryLimitMb')}
              inputMode="numeric"
              value={form.memoryLimitMb}
              onChange={(e) => set('memoryLimitMb', e.target.value)}
            />
            <FieldError id="problem-memoryLimitMb-error" message={errors.memoryLimitMb} />
          </div>
        </div>
      </div>
      <div className={styles.statement}>
        <div className={ui.field}>
          <label htmlFor="problem-statementMd">Statement (Markdown)</label>
          <textarea {...field('statementMd')} value={form.statementMd} onChange={(e) => set('statementMd', e.target.value)} />
          <FieldError id="problem-statementMd-error" message={errors.statementMd} />
        </div>
        <div className={ui.field}>
          <span id="preview-label">Preview</span>
          <div className={styles.preview} aria-labelledby="preview-label" data-testid="statement-preview">
            {form.statementMd.trim() ? <Markdown>{form.statementMd}</Markdown> : <p className={ui.hint}>Nothing to preview yet.</p>}
          </div>
        </div>
      </div>
      <label className={styles.checkbox}>
        <input type="checkbox" checked={form.published} onChange={(e) => set('published', e.target.checked)} />
        Published (visible to everyone and usable in contests)
      </label>
      {serverError && (
        <p className={ui.error} role="alert">
          {serverError}
        </p>
      )}
      <div className={styles.actions}>
        <button type="submit" className={ui.button} disabled={saving}>
          {saving ? 'Saving…' : editing ? 'Save changes' : 'Create problem'}
        </button>
        {savedMessage && (
          <span className={styles.success} role="status">
            {savedMessage}
          </span>
        )}
        {editing && (
          <Link to={`/admin/problems/${form.slug}/testcases`} state={problemId !== null ? { problemId, title: form.title } : undefined}>
            Add test cases
          </Link>
        )}
        <Link to="/admin">Back to admin</Link>
      </div>
    </form>
  );
}

export function ProblemFormPage() {
  const { pathname } = useLocation();
  return <ProblemFormContent key={pathname} />;
}
