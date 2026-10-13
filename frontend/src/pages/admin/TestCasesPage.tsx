import { useEffect, useState, type ChangeEvent, type FormEvent } from 'react';
import { Link, useLocation, useParams } from 'react-router';
import { LIMITS, utf8Bytes, validateTestCases } from '../../admin/validation';
import { ApiError } from '../../api/client';
import { api } from '../../api/endpoints';
import type { TestCaseRequest, TestCaseSummary } from '../../api/types';
import ui from '../../components/ui.module.css';
import styles from './admin.module.css';

const EMPTY_ROW: TestCaseRequest = { input: '', expectedOutput: '', sample: false };

interface LocationState {
  problemId?: number;
  title?: string;
}

export function TestCasesPage() {
  const { slug = '' } = useParams();
  const state = (useLocation().state ?? {}) as LocationState;
  const [problem, setProblem] = useState<{ id: number; title: string } | null>(
    state.problemId !== undefined ? { id: state.problemId, title: state.title ?? slug } : null,
  );
  const [loadError, setLoadError] = useState<string | null>(null);
  const [rows, setRows] = useState<TestCaseRequest[]>([{ ...EMPTY_ROW }]);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [added, setAdded] = useState<TestCaseSummary[]>([]);

  useEffect(() => {
    if (problem) return undefined;
    let cancelled = false;
    api.problems
      .get(slug)
      .then((p) => {
        if (!cancelled) setProblem({ id: p.id, title: p.title });
      })
      .catch((e: unknown) => {
        if (!cancelled) {
          setLoadError(e instanceof ApiError && e.status === 404 ? 'Problem not found (unpublished problems cannot be loaded here).' : 'Could not load the problem.');
        }
      });
    return () => {
      cancelled = true;
    };
  }, [problem, slug]);

  const update = (index: number, patch: Partial<TestCaseRequest>) =>
    setRows((all) => all.map((row, i) => (i === index ? { ...row, ...patch } : row)));

  const loadFile = (index: number, field: 'input' | 'expectedOutput') => async (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    event.target.value = '';
    if (!file) return;
    if (file.size > LIMITS.testCaseBytes) {
      setError(`${file.name} is larger than 1 MB`);
      return;
    }
    const text = await file.text();
    if (utf8Bytes(text) > LIMITS.testCaseBytes) {
      setError(`${file.name} is larger than 1 MB`);
      return;
    }
    setError(null);
    update(index, { [field]: text });
  };

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault();
    if (!problem) return;
    const problemFound = validateTestCases(rows);
    setError(problemFound);
    if (problemFound) return;
    setSaving(true);
    try {
      const created = await api.admin.addTestCases(problem.id, rows);
      setAdded((all) => [...all, ...created]);
      setRows([{ ...EMPTY_ROW }]);
    } catch (e) {
      setError(
        e instanceof ApiError
          ? e.fieldErrors.length > 0
            ? e.fieldErrors.map((f) => `${f.field}: ${f.message}`).join('; ')
            : e.message
          : 'Could not add the test cases.',
      );
    } finally {
      setSaving(false);
    }
  };

  if (loadError) {
    return (
      <div className={ui.card}>
        <p className={ui.error}>{loadError}</p>
        <Link to="/admin">Back to admin</Link>
      </div>
    );
  }
  if (!problem) return <p className={ui.hint}>Loading problem…</p>;

  return (
    <div className={ui.card}>
      <h1>Test cases: {problem.title}</h1>
      <p className={ui.hint}>
        Ordinals are assigned by the server after the existing test cases. Hidden test data is never returned, so the
        list below shows only what was added in this session.
      </p>
      <form onSubmit={(e) => void onSubmit(e)} noValidate>
        {rows.map((row, index) => (
          <fieldset key={index} className={styles.row}>
            <legend className="visually-hidden">Test case {index + 1}</legend>
            <div className={styles.rowHeader}>
              <strong>New test case {index + 1}</strong>
              {rows.length > 1 && (
                <button type="button" className={`${ui.button} ${ui.secondary}`} onClick={() => setRows((all) => all.filter((_, i) => i !== index))}>
                  Remove
                </button>
              )}
            </div>
            <div className={styles.grid}>
              <div className={ui.field}>
                <label htmlFor={`tc-input-${index}`}>Input</label>
                <textarea id={`tc-input-${index}`} className={styles.textarea} value={row.input} onChange={(e) => update(index, { input: e.target.value })} />
                <label className={ui.hint}>
                  Load input from a file{' '}
                  <input type="file" accept=".txt,.in,text/plain" aria-label={`Input file for test case ${index + 1}`} onChange={(e) => void loadFile(index, 'input')(e)} />
                </label>
              </div>
              <div className={ui.field}>
                <label htmlFor={`tc-output-${index}`}>Expected output</label>
                <textarea
                  id={`tc-output-${index}`}
                  className={styles.textarea}
                  value={row.expectedOutput}
                  onChange={(e) => update(index, { expectedOutput: e.target.value })}
                />
                <label className={ui.hint}>
                  Load expected output from a file{' '}
                  <input
                    type="file"
                    accept=".txt,.out,.ans,text/plain"
                    aria-label={`Expected output file for test case ${index + 1}`}
                    onChange={(e) => void loadFile(index, 'expectedOutput')(e)}
                  />
                </label>
              </div>
            </div>
            <label className={styles.checkbox}>
              <input type="checkbox" checked={row.sample} onChange={(e) => update(index, { sample: e.target.checked })} />
              Sample (shown to everyone on the problem page)
            </label>
          </fieldset>
        ))}
        {error && (
          <p className={ui.error} role="alert">
            {error}
          </p>
        )}
        <div className={styles.actions}>
          <button type="button" className={`${ui.button} ${ui.secondary}`} onClick={() => setRows((all) => [...all, { ...EMPTY_ROW }])}>
            Add another
          </button>
          <button type="submit" className={ui.button} disabled={saving}>
            {saving ? 'Uploading…' : `Upload ${rows.length} test case${rows.length === 1 ? '' : 's'}`}
          </button>
          <Link to="/admin">Back to admin</Link>
        </div>
      </form>
      <h2>Added in this session</h2>
      {added.length === 0 ? (
        <p className={ui.hint}>Nothing added yet.</p>
      ) : (
        <table className={styles.table} data-testid="added-test-cases">
          <thead>
            <tr>
              <th scope="col">Ordinal</th>
              <th scope="col">Id</th>
              <th scope="col">Sample</th>
            </tr>
          </thead>
          <tbody>
            {added.map((t) => (
              <tr key={t.id}>
                <td>{t.ordinal}</td>
                <td>{t.id}</td>
                <td>{t.sample ? 'Yes' : 'No (hidden)'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
