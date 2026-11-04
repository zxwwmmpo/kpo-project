import { useEffect, useState, type FormEvent } from 'react';
import { api, message, type Contact } from '../api/account';
import Feedback from './Feedback';

const labels = { PHONE: 'Телефон', URL: 'URL', GITHUB: 'GitHub' };

function link(contact: Contact) {
  if (contact.type === 'PHONE')
    return /^\+[1-9][0-9]{7,14}$/.test(contact.value)
      ? 'tel:' + contact.value
      : undefined;
  try {
    const url = new URL(contact.value);
    return ['https:', 'http:'].includes(url.protocol) ? url.href : undefined;
  } catch {
    return undefined;
  }
}

export default function Contacts() {
  const [contacts, setContacts] = useState<Contact[]>([]);
  const [editing, setEditing] = useState<Contact | 'new' | null>(null);
  const [type, setType] = useState<Contact['type']>('PHONE');
  const [value, setValue] = useState('');
  const [error, setError] = useState('');
  const [pending, setPending] = useState(false);
  const [loaded, setLoaded] = useState(false);
  const [version, setVersion] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    api<Contact[]>('/profile/contacts', 'GET', undefined, controller.signal)
      .then((data) => {
        setContacts(data);
        setLoaded(true);
        setError('');
      })
      .catch((e) => {
        if (!controller.signal.aborted) setError(message(e));
      });
    return () => controller.abort();
  }, [version]);
  function edit(contact: Contact | 'new') {
    setEditing(contact);
    setType(contact === 'new' ? 'PHONE' : contact.type);
    setValue(contact === 'new' ? '' : contact.value);
    setError('');
  }
  async function save(event: FormEvent) {
    event.preventDefault();
    if (pending || !editing) return;
    let normalized = value.trim();
    if (type === 'PHONE') {
      normalized = normalized.replace(/[() -]/g, '');
      if (!/^\+[1-9][0-9]{7,14}$/.test(normalized)) {
        setError('Введите телефон с кодом страны: + и от 8 до 15 цифр.');
        return;
      }
    } else {
      try {
        const url = new URL(normalized);
        if (
          !['http:', 'https:'].includes(url.protocol) ||
          url.username ||
          url.password
        )
          throw new Error();
        if (
          type === 'GITHUB' &&
          (url.protocol !== 'https:' ||
            url.hostname !== 'github.com' ||
            url.port ||
            url.pathname === '/')
        )
          throw new Error();
      } catch {
        setError(
          type === 'GITHUB'
            ? 'Введите ссылку https://github.com/ на профиль или репозиторий.'
            : 'Введите корректную ссылку с http:// или https://.',
        );
        return;
      }
    }
    setPending(true);
    setError('');
    try {
      const saved = await api<Contact>(
        '/profile/contacts' + (editing === 'new' ? '' : '/' + editing.id),
        editing === 'new' ? 'POST' : 'PUT',
        { type, value: normalized },
      );
      setContacts((items) =>
        editing === 'new'
          ? [...items, saved]
          : items.map((c) => (c.id === saved.id ? saved : c)),
      );
      setEditing(null);
    } catch (e) {
      setError(message(e));
    } finally {
      setPending(false);
    }
  }
  async function remove(contact: Contact) {
    if (!window.confirm(`Удалить контакт «${contact.value}»?`)) return;
    setPending(true);
    setError('');
    try {
      await api('/profile/contacts/' + contact.id, 'DELETE');
      setContacts((items) => items.filter((c) => c.id !== contact.id));
    } catch (e) {
      setError(message(e));
    } finally {
      setPending(false);
    }
  }
  return (
    <section className="card" aria-labelledby="contacts-title">
      <div className="section-heading">
        <h2 id="contacts-title">Контакты</h2>
        <button
          disabled={pending || !loaded || editing !== null}
          onClick={() => edit('new')}
        >
          + Добавить
        </button>
      </div>
      {!loaded && !error && <p className="muted">Загрузка контактов…</p>}
      {loaded && !contacts.length && !editing && (
        <p className="muted">
          Добавьте телефон, ссылки на сайты или GitHub. Можно указать несколько
          контактов каждого типа.
        </p>
      )}
      <ul className="contact-list">
        {contacts.map((c) => (
          <li key={c.id}>
            <div>
              <span className="eyebrow">{labels[c.type]}</span>
              <a
                href={link(c)}
                target={c.type === 'PHONE' ? undefined : '_blank'}
                rel="noopener noreferrer"
              >
                {c.value}
              </a>
            </div>
            <div className="actions">
              <button
                aria-label={`Изменить контакт ${c.value}`}
                disabled={pending || editing !== null}
                onClick={() => edit(c)}
              >
                Изменить
              </button>
              <button
                aria-label={`Удалить контакт ${c.value}`}
                disabled={pending || editing !== null}
                onClick={() => remove(c)}
              >
                Удалить
              </button>
            </div>
          </li>
        ))}
      </ul>
      {editing && (
        <form onSubmit={save}>
          <fieldset disabled={pending}>
            <legend>
              {editing === 'new' ? 'Новый контакт' : 'Редактирование контакта'}
            </legend>
            <label>
              Тип контакта
              <select
                value={type}
                onChange={(e) => setType(e.target.value as Contact['type'])}
              >
                {Object.entries(labels).map(([key, label]) => (
                  <option key={key} value={key}>
                    {label}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Значение
              <input
                required
                maxLength={2048}
                type={type === 'PHONE' ? 'tel' : 'url'}
                value={value}
                placeholder={
                  type === 'PHONE'
                    ? '+7 (999) 123-45-67'
                    : type === 'GITHUB'
                      ? 'https://github.com/username'
                      : 'https://example.com'
                }
                onChange={(e) => setValue(e.target.value)}
              />
            </label>
            <div className="actions">
              <button className="primary">
                {pending ? 'Сохранение…' : 'Сохранить контакт'}
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
          </fieldset>
        </form>
      )}
      <Feedback error={error} />
      {!loaded && error && (
        <button onClick={() => setVersion((v) => v + 1)}>Повторить</button>
      )}
    </section>
  );
}
