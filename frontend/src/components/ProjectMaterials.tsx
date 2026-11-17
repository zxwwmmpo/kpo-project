import { useEffect, useState, type FormEvent } from 'react';
import { api, message, request } from '../api/account';
import Feedback from './Feedback';
type ProjectFile = { id: number; name: string; size: number };
type ProjectLink = { label: string; url: string };
type Materials = { files: ProjectFile[]; links: ProjectLink[] };
const maxFiles = 5;
const maxFileSize = 5 * 1024 * 1024;
export default function ProjectMaterials({
  projectPath,
  disableChanges,
  onBusyChange,
}: {
  projectPath: string;
  disableChanges: boolean;
  onBusyChange: (busy: boolean) => void;
}) {
  const [materials, setMaterials] = useState<Materials | null>(null);
  const [loadError, setLoadError] = useState('');
  const [version, setVersion] = useState(0);
  const [error, setError] = useState('');
  const [fileName, setFileName] = useState('');
  const [linkForm, setLinkForm] = useState(false);
  const [pending, setPending] = useState(false);
  useEffect(() => {
    const controller = new AbortController();
    api<Materials>(projectPath, 'GET', undefined, controller.signal)
      .then(setMaterials)
      .catch((error) => {
        if (!controller.signal.aborted) setLoadError(message(error));
      });
    return () => controller.abort();
  }, [projectPath, version]);
  async function change(save: () => Promise<void>) {
    setError('');
    setPending(true);
    onBusyChange(true);
    try {
      await save();
    } catch (error) {
      setError(message(error));
    } finally {
      setPending(false);
      onBusyChange(false);
    }
  }
  function upload(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const input = form.elements.namedItem('file') as HTMLInputElement;
    const file = input.files?.[0];
    if (!file?.size) {
      setError('Выберите непустой файл.');
      return;
    }
    if (file.size > maxFileSize) {
      setError('Размер файла не должен превышать 5 МБ.');
      return;
    }
    void change(async () => {
      const body = new FormData();
      body.append('file', file);
      const response = await request(projectPath + '/files', {
        method: 'POST',
        body,
      });
      const uploaded: ProjectFile = await response.json();
      setMaterials(
        (current) =>
          current && { ...current, files: [...current.files, uploaded] },
      );
      setFileName('');
      form.reset();
    });
  }
  function addLink(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const fields = new FormData(form);
    void change(async () => {
      const link = await api<ProjectLink>(projectPath + '/links', 'POST', {
        url: String(fields.get('url')).trim(),
        label: String(fields.get('label')).trim(),
      });
      setMaterials(
        (current) => current && { ...current, links: [...current.links, link] },
      );
      setLinkForm(false);
    });
  }
  function removeFile(file: ProjectFile) {
    if (!window.confirm(`Удалить файл «${file.name}»?`)) return;
    void change(async () => {
      await api<void>(`${projectPath}/files/${file.id}`, 'DELETE');
      setMaterials(
        (current) =>
          current && {
            ...current,
            files: current.files.filter(({ id }) => id !== file.id),
          },
      );
    });
  }
  if (loadError)
    return (
      <>
        <Feedback error={loadError} />
        <button
          onClick={() => {
            setLoadError('');
            setVersion((current) => current + 1);
          }}
        >
          Повторить загрузку материалов
        </button>
      </>
    );
  if (!materials) return <p role="status">Загрузка файлов и ссылок…</p>;
  const disabled = pending || disableChanges;
  return (
    <div className="project-materials">
      <section aria-labelledby="project-files-title">
        <h4 id="project-files-title">
          Файлы <span className="material-count">{materials.files.length}</span>
        </h4>
        {materials.files.length ? (
          <ul className="portfolio-projects">
            {materials.files.map((file) => (
              <li className="actions" key={file.id}>
                <a
                  className="project-download"
                  title={`Скачать ${file.name}`}
                  href={`/api${projectPath}/files/${file.id}`}
                  download
                >
                  {file.name}
                  <span aria-hidden="true"> ↓</span>
                </a>
                <span className="muted">
                  {Math.max(1, Math.ceil(file.size / 1024))} КБ
                </span>
                <button
                  aria-label={`Удалить файл ${file.name}`}
                  disabled={disabled}
                  onClick={() => removeFile(file)}
                >
                  Удалить
                </button>
              </li>
            ))}
          </ul>
        ) : (
          <p className="muted">Файлов пока нет.</p>
        )}
        <form className="file-zone" onSubmit={upload}>
          <fieldset disabled={disabled || materials.files.length >= maxFiles}>
            <label className="file-choice">
              <span>
                <b className="portfolio-accent" aria-hidden="true">
                  +{' '}
                </b>
                {fileName || 'Выбрать файл'}
              </span>
              <input
                className="visually-hidden"
                aria-label="Файл проекта"
                name="file"
                type="file"
                accept=".pdf,.png,.jpg,.jpeg,.docx"
                aria-describedby="project-files-limit"
                onChange={(e) => setFileName(e.target.files?.[0]?.name ?? '')}
              />
            </label>
            <span className="hint" id="project-files-limit">
              PDF, PNG, JPEG, DOCX · до 5 файлов по 5 МБ
            </span>
            {fileName && (
              <button className="outline" type="submit">
                Прикрепить файл
              </button>
            )}
          </fieldset>
        </form>
      </section>
      <section aria-labelledby="project-links-title">
        <h4 id="project-links-title">
          Ссылки{' '}
          <span className="material-count">{materials.links.length}</span>
        </h4>
        {materials.links.length ? (
          <ul className="portfolio-projects">
            {materials.links.map((link, index) => (
              <li key={index}>
                <a href={link.url} target="_blank" rel="noopener noreferrer">
                  {link.label ||
                    (URL.canParse(link.url)
                      ? new URL(link.url).hostname
                      : link.url)}{' '}
                  ↗
                </a>
                {link.label && URL.canParse(link.url) && (
                  <div className="hint">{new URL(link.url).hostname}</div>
                )}
              </li>
            ))}
          </ul>
        ) : (
          <p className="muted">Ссылок пока нет.</p>
        )}
        {!linkForm ? (
          <button
            className="outline link-toggle"
            disabled={disabled}
            onClick={() => setLinkForm(true)}
          >
            <b className="portfolio-accent" aria-hidden="true">
              +{' '}
            </b>
            Добавить ссылку
          </button>
        ) : (
          <form onSubmit={addLink}>
            <fieldset disabled={disabled}>
              <label>
                Адрес ссылки
                <input name="url" type="url" maxLength={2048} required />
              </label>
              <label>
                Подпись ссылки (необязательно)
                <input name="label" maxLength={100} />
              </label>
              <div className="actions">
                <button className="outline" type="submit">
                  Добавить ссылку
                </button>
              </div>
            </fieldset>
            <button
              type="button"
              disabled={disabled}
              onClick={() => setLinkForm(false)}
            >
              Отмена
            </button>
          </form>
        )}
      </section>
      <Feedback error={error} />
    </div>
  );
}
