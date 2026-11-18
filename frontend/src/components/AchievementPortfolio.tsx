import { useEffect, useRef, useState, type FormEvent } from 'react';
import { api, message, request } from '../api/account';
import Feedback from './Feedback';

type Achievement = {
  id: number;
  title: string;
  date: string;
  description: string;
  certificateName: string | null;
};

const path = '/profile/achievements';

export default function AchievementPortfolio({
  onCount,
}: {
  onCount: (count: number | null) => void;
}) {
  const [achievements, setAchievements] = useState<Achievement[] | null>(null);
  const [loadError, setLoadError] = useState('');
  const [version, setVersion] = useState(0);
  const [adding, setAdding] = useState(false);
  const [deleting, setDeleting] = useState<number | null>(null);
  const [deleteError, setDeleteError] = useState('');
  const achievementCount = achievements?.length ?? null;

  useEffect(() => {
    onCount(achievementCount);
  }, [onCount, achievementCount]);

  useEffect(() => {
    const controller = new AbortController();
    api<Achievement[]>(path, 'GET', undefined, controller.signal)
      .then(setAchievements)
      .catch((error) => {
        if (!controller.signal.aborted) setLoadError(message(error));
      });
    return () => controller.abort();
  }, [version]);

  async function deleteAchievement(achievement: Achievement) {
    if (!window.confirm(`Удалить достижение «${achievement.title}»?`)) return;

    setDeleteError('');
    setDeleting(achievement.id);
    try {
      await api<void>(`${path}/${achievement.id}`, 'DELETE');
      setAchievements(
        (current) => current?.filter(({ id }) => id !== achievement.id) ?? [],
      );
    } catch (error) {
      setDeleteError(message(error));
    } finally {
      setDeleting(null);
    }
  }

  return (
    <details id="portfolio-achievements">
      <summary>Достижения</summary>
      <div className="portfolio-content">
        {adding ? (
          <AchievementForm
            onCancel={() => setAdding(false)}
            onSave={(achievement) => {
              setAchievements((current) =>
                [achievement, ...(current ?? [])].sort(
                  (a, b) => b.date.localeCompare(a.date) || b.id - a.id,
                ),
              );
              setAdding(false);
            }}
          />
        ) : loadError ? (
          <>
            <Feedback error={loadError} />
            <button
              onClick={() => {
                setLoadError('');
                setVersion((value) => value + 1);
              }}
            >
              Повторить загрузку
            </button>
          </>
        ) : achievements === null ? (
          <p role="status">Загрузка достижений…</p>
        ) : (
          <>
            <div className="portfolio-actions">
              <button
                className="outline"
                disabled={deleting !== null}
                onClick={() => {
                  setDeleteError('');
                  setAdding(true);
                }}
              >
                <span className="portfolio-accent" aria-hidden="true">
                  +{' '}
                </span>
                Добавить достижение
              </button>
            </div>
            {achievements.length === 0 ? (
              <p className="muted">У вас пока нет достижений.</p>
            ) : (
              <ul className="portfolio-projects">
                {achievements.map((achievement) => (
                  <li
                    className="project-row achievement-row"
                    key={achievement.id}
                  >
                    <div className="project-copy">
                      <strong>{achievement.title}</strong>
                      {achievement.description && (
                        <p className="about-text">{achievement.description}</p>
                      )}
                    </div>
                    <div className="achievement-meta">
                      <time dateTime={achievement.date} className="muted">
                        {achievement.date.split('-').reverse().join('.')}
                      </time>
                      {achievement.certificateName && (
                        <a
                          href={`/api${path}/${achievement.id}/certificate`}
                          download
                        >
                          {achievement.certificateName}
                          <span aria-hidden="true"> ↓</span>
                        </a>
                      )}
                    </div>
                    <button
                      type="button"
                      className="achievement-delete"
                      aria-label={`Удалить достижение «${achievement.title}»`}
                      aria-busy={deleting === achievement.id}
                      title="Удалить достижение"
                      disabled={deleting !== null}
                      onClick={() => void deleteAchievement(achievement)}
                    >
                      <svg
                        width="16"
                        height="16"
                        viewBox="0 0 24 24"
                        fill="none"
                        stroke="currentColor"
                        strokeWidth="1.75"
                        strokeLinecap="round"
                        strokeLinejoin="round"
                        aria-hidden="true"
                      >
                        <path d="M3 6h18M9 6V4h6v2M5 6l1 14h12l1-14M10 10v6m4-6v6" />
                      </svg>
                    </button>
                  </li>
                ))}
              </ul>
            )}
            <Feedback error={deleteError} />
          </>
        )}
      </div>
    </details>
  );
}

function AchievementForm({
  onCancel,
  onSave,
}: {
  onCancel: () => void;
  onSave: (achievement: Achievement) => void;
}) {
  const [file, setFile] = useState<File>();
  const fileInput = useRef<HTMLInputElement>(null);
  const [error, setError] = useState('');
  const [pending, setPending] = useState(false);

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const fields = new FormData(event.currentTarget);
    const title = String(fields.get('title')).trim();
    const description = String(fields.get('description')).trim();
    if (!title) {
      setError('Введите название достижения.');
      return;
    }
    if (!file?.size) {
      setError('Прикрепите сертификат.');
      fileInput.current?.focus();
      return;
    }
    if (file.size > 5 * 1024 * 1024) {
      setError('Размер сертификата не должен превышать 5 МБ.');
      return;
    }
    fields.set('title', title);
    fields.set('description', description);
    fields.set('certificate', file);

    setError('');
    setPending(true);
    try {
      const response = await request(path, { method: 'POST', body: fields });
      onSave((await response.json()) as Achievement);
    } catch (error) {
      setError(message(error));
    } finally {
      setPending(false);
    }
  }

  return (
    <form onSubmit={save}>
      <fieldset disabled={pending}>
        <legend>Новое достижение</legend>
        <label>
          Название достижения
          <input name="title" required maxLength={120} />
        </label>
        <label>
          Дата достижения
          <input
            name="date"
            type="date"
            min="1926-01-01"
            max="2026-12-31"
            aria-describedby="achievement-date-range"
            required
          />
        </label>
        <span className="hint" id="achievement-date-range">
          С 01.01.1926 по 31.12.2026
        </span>
        <label>
          Описание достижения (необязательно)
          <textarea name="description" maxLength={3000} />
        </label>
        <div className="file-zone achievement-certificate">
          <label className="file-choice">
            <span>
              <b className="portfolio-accent" aria-hidden="true">
                +{' '}
              </b>
              {file?.name || 'Выбрать сертификат'}
            </span>
            <input
              className="visually-hidden"
              ref={fileInput}
              aria-label="Выбрать сертификат"
              aria-describedby="achievement-certificate-limit"
              type="file"
              accept=".pdf,.png,.jpg,.jpeg,.docx"
              onChange={(event) => setFile(event.target.files?.[0])}
            />
          </label>
          <span className="hint" id="achievement-certificate-limit">
            Обязательно. PDF, PNG, JPEG или DOCX · до 5 МБ
          </span>
          {file && (
            <button
              type="button"
              onClick={() => {
                if (fileInput.current) fileInput.current.value = '';
                setFile(undefined);
              }}
            >
              Снять выбор
            </button>
          )}
        </div>
        <div className="actions">
          <button className="primary" type="submit">
            {pending ? 'Сохранение…' : 'Сохранить достижение'}
          </button>
          <button className="outline" type="button" onClick={onCancel}>
            Отмена
          </button>
        </div>
      </fieldset>
      <Feedback error={error} />
    </form>
  );
}
