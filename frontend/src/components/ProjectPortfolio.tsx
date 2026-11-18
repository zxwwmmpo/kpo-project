import { useEffect, useState, type FormEvent } from 'react';
import { api, message } from '../api/account';
import Feedback from './Feedback';
import AchievementPortfolio from './AchievementPortfolio';
import ProjectMaterials from './ProjectMaterials';

type Project = {
  id: number;
  title: string;
  description: string;
  role: string;
  fileCount?: number;
  linkCount?: number;
};

const plural = new Intl.PluralRules('ru');
function countLabel(count: number, one: string, few: string, many: string) {
  const form = plural.select(count);
  return count + ' ' + (form === 'one' ? one : form === 'few' ? few : many);
}

const path = '/profile/projects';

export default function ProjectPortfolio({
  onProjectCount,
  onAchievementCount,
}: {
  onProjectCount: (count: number | null) => void;
  onAchievementCount: (count: number | null) => void;
}) {
  const [projects, setProjects] = useState<Project[] | null>(null);
  const [loadError, setLoadError] = useState('');
  const [version, setVersion] = useState(0);
  const [selected, setSelected] = useState<Project | null>(null);
  const [adding, setAdding] = useState(false);
  const [editing, setEditing] = useState(false);
  const [deleteError, setDeleteError] = useState('');
  const [deleting, setDeleting] = useState(false);
  const [savingMaterials, setSavingMaterials] = useState(false);
  const projectCount = projects?.length ?? null;

  useEffect(() => {
    onProjectCount(projectCount);
  }, [onProjectCount, projectCount]);

  useEffect(() => {
    const controller = new AbortController();
    api<Project[]>(path, 'GET', undefined, controller.signal)
      .then(setProjects)
      .catch((error) => {
        if (!controller.signal.aborted) setLoadError(message(error));
      });
    return () => controller.abort();
  }, [version]);

  async function deleteProject(project: Project) {
    if (
      !window.confirm(
        `Удалить проект «${project.title}» вместе с файлами и ссылками?`,
      )
    )
      return;
    setDeleting(true);
    setDeleteError('');
    try {
      await api<void>(`${path}/${project.id}`, 'DELETE');
      setProjects(
        (current) => current?.filter(({ id }) => id !== project.id) ?? [],
      );
      setSelected(null);
    } catch (error) {
      setDeleteError(message(error));
    } finally {
      setDeleting(false);
    }
  }

  return (
    <section className="card portfolio" aria-labelledby="portfolio-title">
      <h2 id="portfolio-title">Портфолио</h2>
      <details id="portfolio-projects" open>
        <summary>Проекты</summary>
        <div className="portfolio-content">
          {adding || editing ? (
            <ProjectForm
              project={editing ? selected : null}
              onCancel={() => {
                setAdding(false);
                setEditing(false);
              }}
              onSave={(project) => {
                setProjects((current) =>
                  editing
                    ? (current ?? []).map((entry) =>
                        entry.id === project.id ? project : entry,
                      )
                    : [project, ...(current ?? [])],
                );
                setSelected(project);
                setAdding(false);
                setEditing(false);
              }}
            />
          ) : selected ? (
            <div>
              <button
                disabled={deleting || savingMaterials}
                onClick={() => {
                  setSelected(null);
                  setProjects(null);
                  setVersion((v) => v + 1);
                }}
              >
                ← К списку проектов
              </button>
              <div className="project-heading">
                <h3>{selected.title}</h3>
                <button
                  className="outline"
                  disabled={deleting || savingMaterials}
                  onClick={() => setEditing(true)}
                >
                  Редактировать проект
                </button>
              </div>
              {selected.role && (
                <p className="muted">Роль в проекте — {selected.role}</p>
              )}
              <p className="about-text">{selected.description}</p>
              <ProjectMaterials
                projectPath={`${path}/${selected.id}`}
                disableChanges={deleting}
                onBusyChange={setSavingMaterials}
              />
              <button
                disabled={deleting || savingMaterials}
                onClick={() => void deleteProject(selected)}
              >
                {deleting ? 'Удаление…' : 'Удалить проект'}
              </button>
              <Feedback error={deleteError} />
            </div>
          ) : loadError ? (
            <div>
              <Feedback error={loadError} />
              <button
                onClick={() => {
                  setLoadError('');
                  setVersion((value) => value + 1);
                }}
              >
                Повторить загрузку
              </button>
            </div>
          ) : projects === null ? (
            <p role="status">Загрузка проектов…</p>
          ) : (
            <>
              <div className="portfolio-actions">
                <button className="outline" onClick={() => setAdding(true)}>
                  <span className="portfolio-accent" aria-hidden="true">
                    +{' '}
                  </span>
                  Добавить проект
                </button>
              </div>
              {projects.length === 0 ? (
                <p className="muted">У вас пока нет проектов.</p>
              ) : (
                <ul className="portfolio-projects">
                  {projects.map((project) => (
                    <li key={project.id}>
                      <button
                        className="project-row"
                        onClick={() => {
                          setDeleteError('');
                          setSelected(project);
                        }}
                      >
                        <span className="project-copy">
                          <strong>{project.title}</strong>
                          <span className="portfolio-preview">
                            {project.description}
                          </span>
                        </span>
                        <span className="project-count">
                          {countLabel(
                            project.fileCount ?? 0,
                            'файл',
                            'файла',
                            'файлов',
                          )}
                        </span>
                        <span className="project-count">
                          {countLabel(
                            project.linkCount ?? 0,
                            'ссылка',
                            'ссылки',
                            'ссылок',
                          )}
                        </span>
                        <span className="row-arrow" aria-hidden="true">
                          →
                        </span>
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </>
          )}
        </div>
      </details>
      <AchievementPortfolio onCount={onAchievementCount} />
    </section>
  );
}

function ProjectForm({
  project,
  onCancel,
  onSave,
}: {
  project: Project | null;
  onCancel: () => void;
  onSave: (project: Project) => void;
}) {
  const [error, setError] = useState('');
  const [pending, setPending] = useState(false);
  const [description, setDescription] = useState(project?.description ?? '');

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const fields = new FormData(event.currentTarget);
    const title = String(fields.get('title')).trim();
    const role = String(fields.get('role')).trim();

    if (!title || !description.trim()) {
      setError('Введите название и описание проекта.');
      return;
    }

    setError('');
    setPending(true);
    try {
      onSave(
        await api<Project>(
          project ? `${path}/${project.id}` : path,
          project ? 'PUT' : 'POST',
          {
            title,
            description: description.trim(),
            role,
          },
        ),
      );
    } catch (error) {
      setError(message(error));
    } finally {
      setPending(false);
    }
  }

  return (
    <form onSubmit={save}>
      <fieldset disabled={pending}>
        <legend>{project ? 'Редактирование проекта' : 'Новый проект'}</legend>
        <label>
          Название
          <input
            name="title"
            required
            maxLength={120}
            defaultValue={project?.title}
          />
        </label>
        <label>
          Описание
          <textarea
            name="description"
            required
            maxLength={3000}
            value={description}
            onChange={(event) => setDescription(event.target.value)}
            aria-describedby="project-description-count"
          />
        </label>
        <span id="project-description-count" className="hint counter">
          {description.length} / 3000
        </span>
        <label>
          Роль в проекте (необязательно)
          <input name="role" maxLength={100} defaultValue={project?.role} />
        </label>
        <div className="actions">
          <button className="primary" type="submit">
            Сохранить проект
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
