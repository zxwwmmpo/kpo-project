import { useEffect, useRef, useState } from 'react';
import { message } from '../api/account';
import { contactLabels, contactLink, webLink } from '../api/contactLinks';
import {
  loadProfileExport,
  type ProfileExportData,
} from '../api/profileExport';
import Feedback from './Feedback';
import './ProfileExport.css';

export default function ProfileExport({ onClose }: { onClose: () => void }) {
  const [result, setResult] = useState<
    { data: ProfileExportData } | { error: string } | null
  >(null);
  const [version, setVersion] = useState(0);
  const [photoStatus, setPhotoStatus] = useState<'loading' | 'ready' | 'error'>(
    'loading',
  );
  const heading = useRef<HTMLHeadingElement>(null);
  const image = useRef<HTMLImageElement>(null);
  const data = result && 'data' in result ? result.data : null;
  const photo = data?.photo;

  useEffect(() => heading.current?.focus(), []);

  useEffect(() => {
    const controller = new AbortController();
    loadProfileExport(controller.signal)
      .then((data) => {
        if (!controller.signal.aborted) setResult({ data });
      })
      .catch((error) => {
        if (!controller.signal.aborted) {
          controller.abort();
          setResult({ error: message(error) });
        }
      });
    return () => controller.abort();
  }, [version]);

  useEffect(() => {
    if (!photo || !image.current) return;
    const url = URL.createObjectURL(photo);
    image.current.src = url;
    return () => URL.revokeObjectURL(url);
  }, [photo]);

  const photoLoading = Boolean(photo) && photoStatus === 'loading';
  return (
    <section className="profile-export" aria-labelledby="profile-export-title">
      <header className="profile-export-toolbar">
        <button className="outline" onClick={onClose}>
          ← К профилю
        </button>
        <h1 id="profile-export-title" ref={heading} tabIndex={-1}>
          Предпросмотр профиля
        </h1>
        <button
          className="primary"
          disabled={!data || photoLoading}
          onClick={() => window.print()}
        >
          Печать / PDF
        </button>
      </header>

      {result === null && <p role="status">Собираем данные профиля…</p>}
      {result && 'error' in result && (
        <div className="card">
          <Feedback error={result.error} />
          <button
            onClick={() => {
              setResult(null);
              setVersion((current) => current + 1);
            }}
          >
            Повторить
          </button>
        </div>
      )}
      {photoLoading && (
        <p className="profile-export-note" role="status">
          Загружаем фотографию…
        </p>
      )}
      {photoStatus === 'error' && (
        <p className="profile-export-note" role="status">
          Фотография не отобразилась. В документе будут инициалы или силуэт.
        </p>
      )}
      {data && (
        <article className="profile-export-sheet" aria-label="Документ профиля">
          <header className="export-student">
            {photo && photoStatus !== 'error' ? (
              <img
                className="export-avatar"
                ref={image}
                alt="Фото студента"
                onLoad={() => setPhotoStatus('ready')}
                onError={() => setPhotoStatus('error')}
              />
            ) : (
              <AvatarPlaceholder fullName={data.profile.fullName} />
            )}
            <div>
              <h2>{data.profile.fullName || 'Профиль студента'}</h2>
              {data.profile.faculty && <p>{data.profile.faculty.name}</p>}
              {data.profile.direction && <p>{data.profile.direction.name}</p>}
              {data.profile.studyGroup && (
                <p>Группа {data.profile.studyGroup.name}</p>
              )}
            </div>
          </header>

          {data.profile.about && (
            <section>
              <h3>О себе</h3>
              <p className="export-paragraph">{data.profile.about}</p>
            </section>
          )}

          <section>
            <h3>Контакты</h3>
            <ul className="export-contacts">
              <li>
                Электронная почта —{' '}
                <a href={`mailto:${data.profile.email}`}>
                  {data.profile.email}
                </a>
              </li>
              {data.contacts.map((contact) => (
                <li key={contact.id}>
                  {contactLabels[contact.type]} —{' '}
                  <a href={contactLink(contact)}>{contact.value}</a>
                </li>
              ))}
            </ul>
          </section>

          <section>
            <h3>Проекты · {data.projects.length}</h3>
            {data.projects.length === 0 && <p>Проектов пока нет.</p>}
            {data.projects.map((project) => (
              <article className="export-entry" key={project.id}>
                <h4>{project.title}</h4>
                <p className="export-paragraph">{project.description}</p>
                {project.role && <p>Роль — {project.role}</p>}
                {project.links.length > 0 && (
                  <ul className="export-links">
                    {project.links.map(({ label, url }, index) => (
                      <li key={index}>
                        {label && label !== url && <>{label} — </>}
                        <a href={webLink(url)}>{url}</a>
                      </li>
                    ))}
                  </ul>
                )}
              </article>
            ))}
          </section>

          <section>
            <h3>Достижения · {data.achievements.length}</h3>
            {data.achievements.length === 0 && <p>Достижений пока нет.</p>}
            {data.achievements.map((achievement) => (
              <article className="export-entry" key={achievement.id}>
                <h4>
                  {achievement.title} ·{' '}
                  <time dateTime={achievement.date}>
                    {achievement.date.split('-').reverse().join('.')}
                  </time>
                </h4>
                {achievement.description && (
                  <p className="export-paragraph">{achievement.description}</p>
                )}
                {achievement.certificateName && (
                  <p>Сертификат — {achievement.certificateName}</p>
                )}
              </article>
            ))}
          </section>
        </article>
      )}
    </section>
  );
}

function AvatarPlaceholder({ fullName }: { fullName: string | null }) {
  const initials = fullName
    ?.trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => Array.from(part)[0])
    .join('')
    .toLocaleUpperCase('ru');
  return (
    <div
      className="export-avatar export-avatar-placeholder"
      role="img"
      aria-label="Заглушка фотографии"
    >
      {initials || (
        <svg
          width="44"
          height="44"
          viewBox="0 0 64 64"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          aria-hidden="true"
        >
          <circle cx="32" cy="23" r="10" />
          <path d="M12 55c0-12 8-19 20-19s20 7 20 19" />
        </svg>
      )}
    </div>
  );
}
