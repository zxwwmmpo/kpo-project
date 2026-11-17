import { useEffect, useState, type FormEvent } from 'react';
import {
  api,
  loadCatalog,
  message,
  type Catalog,
  type Entry,
} from '../api/account';
import Feedback from './Feedback';

type Kind = keyof Catalog;
type Editor = { kind: Kind; entry: Entry | null; parentId: number | null };

export default function AdminPage() {
  const [catalog, setCatalog] = useState<Catalog | null>(null);
  const [faculty, setFaculty] = useState<number | null>(null);
  const [direction, setDirection] = useState<number | null>(null);
  const [editor, setEditor] = useState<Editor | null>(null);
  const [name, setName] = useState('');
  const [error, setError] = useState('');
  const [pending, setPending] = useState(false);
  const [version, setVersion] = useState(0);
  const [notice, setNotice] = useState('');
  useEffect(() => {
    const controller = new AbortController();
    loadCatalog(controller.signal)
      .then((data) => {
        setCatalog(data);
        setError('');
      })
      .catch((e) => {
        if (!controller.signal.aborted) setError(message(e));
      });
    return () => controller.abort();
  }, [version]);
  function edit(kind: Kind, entry: Entry | null, parentId: number | null) {
    setEditor({ kind, entry, parentId });
    setName(entry?.name || '');
    setError('');
    setNotice('');
  }
  async function save(event: FormEvent) {
    event.preventDefault();
    if (!editor || !catalog || pending) return;
    if (!name.trim()) {
      setError('Введите название.');
      return;
    }
    setPending(true);
    setError('');
    try {
      const saved = await api<Entry>(
        '/catalog/' + editor.kind + (editor.entry ? '/' + editor.entry.id : ''),
        editor.entry ? 'PATCH' : 'POST',
        editor.entry
          ? { name: name.trim() }
          : { name: name.trim(), parentId: editor.parentId },
      );
      setCatalog({
        ...catalog,
        [editor.kind]: [
          ...catalog[editor.kind].filter((e) => e.id !== saved.id),
          saved,
        ].sort((a, b) => a.name.localeCompare(b.name, 'ru')),
      });
      setEditor(null);
      setNotice('Запись сохранена.');
    } catch (e) {
      setError(message(e));
    } finally {
      setPending(false);
    }
  }
  async function remove(kind: Kind, entry: Entry) {
    if (!catalog) return;
    const warning =
      kind === 'faculties'
        ? 'Будут удалены факультет, его направления и группы.'
        : kind === 'directions'
          ? 'Будут удалены направление и его группы.'
          : 'Будет удалена группа.';
    if (
      !window.confirm(
        `Удалить «${entry.name}»?\n${warning}\nЕсли в группах есть студенты, удаление будет запрещено.`,
      )
    )
      return;
    setPending(true);
    setError('');
    setNotice('');
    try {
      await api('/catalog/' + kind + '/' + entry.id, 'DELETE');
      const removedDirections = new Set(
        kind === 'faculties'
          ? catalog.directions
              .filter((d) => d.parentId === entry.id)
              .map((d) => d.id)
          : kind === 'directions'
            ? [entry.id]
            : [],
      );
      setCatalog({
        faculties: catalog.faculties.filter(
          (f) => kind !== 'faculties' || f.id !== entry.id,
        ),
        directions: catalog.directions.filter(
          (d) => !removedDirections.has(d.id),
        ),
        groups: catalog.groups.filter(
          (g) =>
            !removedDirections.has(g.parentId!) &&
            (kind !== 'groups' || g.id !== entry.id),
        ),
      });
      if (kind === 'faculties' && faculty === entry.id) {
        setFaculty(null);
        setDirection(null);
      }
      if (direction !== null && removedDirections.has(direction))
        setDirection(null);
      setNotice('Запись удалена.');
    } catch (e) {
      setError(message(e));
    } finally {
      setPending(false);
    }
  }
  if (!catalog)
    return (
      <section className="card">
        <Feedback error={error} />
        {error ? (
          <button onClick={() => setVersion((v) => v + 1)}>
            Повторить загрузку
          </button>
        ) : (
          <p role="status">Загрузка справочников…</p>
        )}
      </section>
    );
  const columns: {
    kind: Kind;
    title: string;
    parent: number | null;
    enabled: boolean;
    selected: number | null;
    entries: Entry[];
    hint: string;
  }[] = [
    {
      kind: 'faculties',
      title: 'Факультеты',
      parent: null,
      enabled: true,
      selected: faculty,
      entries: catalog.faculties,
      hint: 'Добавьте первый факультет.',
    },
    {
      kind: 'directions',
      title: 'Направления',
      parent: faculty,
      enabled: faculty !== null,
      selected: direction,
      entries: catalog.directions.filter((d) => d.parentId === faculty),
      hint:
        faculty === null
          ? 'Выберите факультет слева.'
          : 'В этом факультете пока нет направлений.',
    },
    {
      kind: 'groups',
      title: 'Группы',
      parent: direction,
      enabled: direction !== null,
      selected: null,
      entries: catalog.groups.filter((g) => g.parentId === direction),
      hint:
        direction === null
          ? 'Выберите направление слева.'
          : 'В этом направлении пока нет групп.',
    },
  ];
  return (
    <>
      <div className="page-heading">
        <div>
          <span className="eyebrow">Администрирование</span>
          <h1>Управление справочниками</h1>
        </div>
        <p className="muted">Факультет → направление → группа</p>
      </div>
      <Feedback error={error} />
      <p className="save-notice" role="status">
        {notice}
      </p>
      <div className="catalog-columns">
        {columns.map((column) => (
          <section
            className="card catalog-column"
            key={column.kind}
            aria-label={column.title}
          >
            <div className="section-heading">
              <h2>{column.title}</h2>
              <span className="count">{column.entries.length}</span>
            </div>
            <button
              className="outline add-entry"
              disabled={!column.enabled || pending || editor !== null}
              onClick={() => edit(column.kind, null, column.parent)}
            >
              + Добавить
            </button>
            {!column.entries.length && <p className="muted">{column.hint}</p>}
            <ul className="catalog-list">
              {column.entries.map((entry) => (
                <li
                  key={entry.id}
                  className={column.selected === entry.id ? 'selected' : ''}
                >
                  {column.kind === 'groups' ? (
                    <p className="entry-name">{entry.name}</p>
                  ) : (
                    <button
                      className="entry-name"
                      aria-pressed={column.selected === entry.id}
                      disabled={pending || editor !== null}
                      onClick={() => {
                        if (column.kind === 'faculties') {
                          setFaculty(entry.id);
                          setDirection(null);
                        } else setDirection(entry.id);
                      }}
                    >
                      {entry.name}
                      <span aria-hidden="true">→</span>
                    </button>
                  )}
                  <div className="actions">
                    <button
                      aria-label={`Переименовать ${entry.name}`}
                      disabled={pending || editor !== null}
                      onClick={() => edit(column.kind, entry, column.parent)}
                    >
                      Переименовать
                    </button>
                    <button
                      aria-label={`Удалить ${entry.name}`}
                      disabled={pending || editor !== null}
                      onClick={() => remove(column.kind, entry)}
                    >
                      Удалить
                    </button>
                  </div>
                </li>
              ))}
            </ul>
            {editor?.kind === column.kind && (
              <form className="catalog-editor" onSubmit={save}>
                <fieldset disabled={pending}>
                  <legend>
                    {editor.entry ? 'Переименование' : 'Новая запись'}
                  </legend>
                  <label>
                    Название
                    <input
                      autoFocus
                      required
                      maxLength={150}
                      value={name}
                      onChange={(e) => setName(e.target.value)}
                    />
                  </label>
                  <div className="actions">
                    <button className="primary">
                      {pending ? 'Сохранение…' : 'Сохранить'}
                    </button>
                    <button
                      type="button"
                      onClick={() => {
                        setEditor(null);
                        setError('');
                      }}
                    >
                      Отмена
                    </button>
                  </div>
                </fieldset>
              </form>
            )}
          </section>
        ))}
      </div>
    </>
  );
}
