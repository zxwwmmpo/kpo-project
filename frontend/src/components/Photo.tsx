import { useEffect, useState, useRef } from 'react';
import { ApiError, message, request } from '../api/account';
import Feedback from './Feedback';

export default function Photo() {
  const [photo, setPhoto] = useState<Blob | null>(null);
  const [file, setFile] = useState<File | null>(null);
  const [error, setError] = useState('');
  const [pending, setPending] = useState(false);
  const [loading, setLoading] = useState(true);
  const [version, setVersion] = useState(0);
  const input = useRef<HTMLInputElement>(null);
  useEffect(() => {
    const controller = new AbortController();
    request('/profile/photo', { signal: controller.signal })
      .then((r) => r.blob())
      .then((blob) => {
        if (!controller.signal.aborted) {
          setPhoto(blob);
          setError('');
        }
      })
      .catch((e) => {
        if (controller.signal.aborted) return;
        if (e instanceof ApiError && e.status === 404) {
          setPhoto(null);
          setError('');
        } else setError(message(e));
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [version]);
  async function save() {
    if (!file || pending) return;
    setPending(true);
    setError('');
    try {
      const body = new FormData();
      body.append('file', file);
      await request('/profile/photo', { method: 'POST', body });
      setFile(null);
      setVersion((v) => v + 1);
      setLoading(true);
    } catch (e) {
      setError(message(e));
    } finally {
      setPending(false);
    }
  }
  async function remove() {
    if (!window.confirm('Удалить фотографию профиля?')) return;
    setPending(true);
    setError('');
    try {
      await request('/profile/photo', { method: 'DELETE' });
      setPhoto(null);
      setFile(null);
    } catch (e) {
      setError(message(e));
    } finally {
      setPending(false);
    }
  }
  return (
    <div className="photo-block">
      <div className="avatar">
        {file || photo ? (
          <BlobImage
            source={(file || photo)!}
            alt={file ? 'Предпросмотр фотографии' : 'Фотография профиля'}
          />
        ) : (
          <svg viewBox="0 0 64 64" fill="none" aria-hidden="true">
            <circle cx="32" cy="23" r="10" />
            <path d="M12 55c0-12 8-19 20-19s20 7 20 19" />
          </svg>
        )}
      </div>
      <input
        ref={input}
        className="visually-hidden"
        tabIndex={-1}
        type="file"
        accept="image/png,image/jpeg"
        aria-label="Выбрать фотографию"
        onChange={(e) => {
          const selected = e.target.files?.[0];
          e.target.value = '';
          if (!selected) return;
          setError('');
          if (!['image/png', 'image/jpeg'].includes(selected.type)) {
            setError('Выберите JPEG или PNG.');
            return;
          }
          if (selected.size > 5 * 1024 * 1024) {
            setError('Размер фотографии не должен превышать 5 МБ.');
            return;
          }
          setFile(selected);
        }}
      />
      {file ? (
        <div className="actions">
          <button
            className="primary"
            disabled={pending || loading}
            onClick={save}
          >
            Сохранить фото
          </button>
          <button disabled={pending} onClick={() => setFile(null)}>
            Отмена
          </button>
        </div>
      ) : (
        <>
          <button
            disabled={pending || loading}
            onClick={() => input.current?.click()}
          >
            {loading ? 'Загрузка…' : photo ? 'Изменить фото' : 'Загрузить фото'}
          </button>
          {photo && (
            <button disabled={pending} onClick={remove}>
              Удалить фото
            </button>
          )}
        </>
      )}
      <p className="hint">
        JPEG / PNG · до 5 МБ
        <br />
        До 4096 × 4096 px
      </p>
      <Feedback error={error} />
      {error && !file && (
        <button
          disabled={pending}
          onClick={() => {
            setLoading(true);
            setVersion((v) => v + 1);
          }}
        >
          Повторить загрузку
        </button>
      )}
    </div>
  );
}

function BlobImage({ source, alt }: { source: Blob; alt: string }) {
  const element = useRef<HTMLImageElement>(null);
  useEffect(() => {
    const url = URL.createObjectURL(source);
    if (element.current) element.current.src = url;
    return () => URL.revokeObjectURL(url);
  }, [source]);
  return <img ref={element} alt={alt} />;
}
