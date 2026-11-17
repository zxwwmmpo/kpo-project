import { useEffect, useState, type FormEvent } from 'react';
import {
  api,
  loadCatalog,
  message,
  type Catalog,
  type Profile,
} from '../api/account';
import Contacts from './Contacts';
import Feedback from './Feedback';
import Photo from './Photo';

export default function StudentPage() {
  const [data, setData] = useState<{
    profile: Profile;
    catalog: Catalog;
  } | null>(null);
  const [error, setError] = useState('');
  const [version, setVersion] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    Promise.all([
      api<Profile>('/profile', 'GET', undefined, controller.signal),
      loadCatalog(controller.signal),
    ])
      .then(([profile, catalog]) => {
        setData({ profile, catalog });
        setError('');
      })
      .catch((e) => {
        if (!controller.signal.aborted) setError(message(e));
      });
    return () => controller.abort();
  }, [version]);
  if (!data)
    return (
      <section className="card">
        <Feedback error={error} />
        {error ? (
          <button onClick={() => setVersion((v) => v + 1)}>
            Повторить загрузку
          </button>
        ) : (
          <p role="status">Загрузка профиля…</p>
        )}
      </section>
    );
  return (
    <ProfileContent
      profile={data.profile}
      catalog={data.catalog}
      onChange={(profile) => setData({ ...data, profile })}
    />
  );
}

function ProfileContent({
  profile,
  catalog,
  onChange,
}: {
  profile: Profile;
  catalog: Catalog;
  onChange: (profile: Profile) => void;
}) {
  const [editing, setEditing] = useState<'profile' | 'about' | null>(null);
  const [name, setName] = useState('');
  const [faculty, setFaculty] = useState('');
  const [direction, setDirection] = useState('');
  const [group, setGroup] = useState('');
  const [about, setAbout] = useState('');
  const [error, setError] = useState('');
  const [pending, setPending] = useState(false);
  function edit(mode: 'profile' | 'about') {
    setEditing(mode);
    setName(profile.fullName || '');
    setFaculty(String(profile.faculty?.id || ''));
    setDirection(String(profile.direction?.id || ''));
    setGroup(String(profile.studyGroup?.id || ''));
    setAbout(profile.about || '');
    setError('');
  }
  async function save(event: FormEvent) {
    event.preventDefault();
    if (pending) return;
    if (!name.trim()) {
      setError('Введите ФИО.');
      return;
    }
    setPending(true);
    setError('');
    try {
      onChange(
        await api<Profile>('/profile', 'PUT', {
          fullName: name.trim(),
          groupId: Number(group),
          about: about.trim() || null,
        }),
      );
      setEditing(null);
    } catch (e) {
      setError(message(e));
    } finally {
      setPending(false);
    }
  }
  const actions = (
    <div className="actions">
      <button className="primary">
        {pending ? 'Сохранение…' : 'Сохранить'}
      </button>
      <button
        type="button"
        onClick={() => {
          setEditing(null);
          setError('');
        }}
      >
        Отмена
      </button>
    </div>
  );
  return (
    <>
      <section className="card profile-card" aria-label="Профиль студента">
        <Photo />
        <div className="profile-info">
          {editing === 'profile' ? (
            <form onSubmit={save}>
              <fieldset disabled={pending}>
                <legend>Редактирование профиля</legend>
                <label>
                  ФИО
                  <input
                    required
                    maxLength={150}
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                    autoComplete="name"
                  />
                </label>
                <div className="study-fields">
                  <label>
                    Факультет
                    <select
                      required
                      value={faculty}
                      onChange={(e) => {
                        setFaculty(e.target.value);
                        setDirection('');
                        setGroup('');
                      }}
                    >
                      <option value="">Выберите факультет</option>
                      {catalog.faculties.map((f) => (
                        <option key={f.id} value={f.id}>
                          {f.name}
                        </option>
                      ))}
                    </select>
                  </label>
                  <label>
                    Направление обучения
                    <select
                      required
                      disabled={!faculty}
                      value={direction}
                      onChange={(e) => {
                        setDirection(e.target.value);
                        setGroup('');
                      }}
                    >
                      <option value="">Выберите направление</option>
                      {catalog.directions
                        .filter((d) => d.parentId === Number(faculty))
                        .map((d) => (
                          <option key={d.id} value={d.id}>
                            {d.name}
                          </option>
                        ))}
                    </select>
                  </label>
                  <label>
                    Группа
                    <select
                      required
                      disabled={!direction}
                      value={group}
                      onChange={(e) => setGroup(e.target.value)}
                    >
                      <option value="">Выберите группу</option>
                      {catalog.groups
                        .filter((g) => g.parentId === Number(direction))
                        .map((g) => (
                          <option key={g.id} value={g.id}>
                            {g.name}
                          </option>
                        ))}
                    </select>
                  </label>
                </div>
                {!catalog.faculties.length && (
                  <p className="muted">
                    Администратор ещё не добавил факультеты.
                  </p>
                )}
                {actions}
              </fieldset>
              <Feedback error={error} />
            </form>
          ) : (
            <>
              <span className="eyebrow">Профиль студента</span>
              <h1 className={!profile.fullName ? 'empty-title' : ''}>
                {profile.fullName || 'Заполните профиль'}
              </h1>
              <p className="study-summary">
                {profile.faculty
                  ? `${profile.faculty.name} · ${profile.direction?.name}`
                  : 'Укажите ФИО и учебную группу.'}
              </p>
              {profile.studyGroup && (
                <p className="group-name">{profile.studyGroup.name}</p>
              )}
              <p className="identity">{profile.email}</p>
              <button
                className="outline"
                disabled={editing !== null}
                onClick={() => edit('profile')}
              >
                Редактировать профиль
              </button>
            </>
          )}
        </div>
      </section>
      <div className="profile-columns">
        <section className="card" aria-labelledby="about-title">
          <div className="section-heading">
            <h2 id="about-title">О себе</h2>
            <button
              disabled={editing !== null || !profile.studyGroup}
              onClick={() => edit('about')}
            >
              Редактировать
            </button>
          </div>
          {editing === 'about' ? (
            <form onSubmit={save}>
              <fieldset disabled={pending}>
                <label>
                  Расскажите о себе
                  <textarea
                    rows={7}
                    maxLength={500}
                    value={about}
                    onChange={(e) => setAbout(e.target.value)}
                    aria-describedby="about-count"
                  />
                </label>
                <span id="about-count" className="hint counter">
                  {about.length} / 500
                </span>
                {actions}
              </fieldset>
              <Feedback error={error} />
            </form>
          ) : (
            <p className={profile.about ? 'about-text' : 'muted'}>
              {profile.about ||
                (profile.studyGroup
                  ? 'Расскажите об интересах, навыках и целях.'
                  : 'Сначала заполните основные данные профиля.')}
            </p>
          )}
        </section>
        <Contacts />
      </div>
    </>
  );
}
