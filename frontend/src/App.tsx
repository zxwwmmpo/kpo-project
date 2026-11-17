import { useEffect, useState, type FormEvent } from 'react';
import { api, ApiError, message, type User } from './api/account';
import AdminPage from './components/AdminPage';
import StudentPage from './components/StudentPage';
import Feedback from './components/Feedback';
import './App.css';

function App() {
  const [user, setUser] = useState<User | null>(null);
  const [screen, setScreen] = useState<'login' | 'register'>('login');
  const [checking, setChecking] = useState(true);
  const [error, setError] = useState('');
  const [pending, setPending] = useState(false);
  const [version, setVersion] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    api<User>('/auth/me', 'GET', undefined, controller.signal)
      .then((current) => {
        if (!controller.signal.aborted) {
          setUser(current);
          setError('');
        }
      })
      .catch((e) => {
        if (
          !controller.signal.aborted &&
          !(e instanceof ApiError && e.status === 401)
        )
          setError(message(e));
      })
      .finally(() => {
        if (!controller.signal.aborted) setChecking(false);
      });
    return () => controller.abort();
  }, [version]);
  useEffect(() => {
    const expire = () => {
      setUser(null);
      setScreen('login');
      setError('Сессия завершилась. Войдите снова.');
    };
    window.addEventListener('session-expired', expire);
    return () => window.removeEventListener('session-expired', expire);
  }, []);
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (pending) return;
    const data = new FormData(event.currentTarget);
    setPending(true);
    setError('');
    try {
      await api('/auth/' + screen, 'POST', {
        email: data.get('email'),
        password: data.get('password'),
      });
      setUser(await api<User>('/auth/me'));
    } catch (e) {
      setError(
        e instanceof ApiError && e.status === 401
          ? 'Проверьте email и пароль.'
          : e instanceof ApiError && e.status === 409
            ? 'Этот email уже зарегистрирован.'
            : message(e),
      );
    } finally {
      setPending(false);
    }
  }
  async function logout() {
    setPending(true);
    setError('');
    try {
      await api('/auth/logout', 'POST');
      setUser(null);
      setScreen('login');
    } catch (e) {
      setError(message(e));
    } finally {
      setPending(false);
    }
  }
  if (checking)
    return (
      <main className="auth">
        <p role="status">Проверка входа…</p>
      </main>
    );
  if (user)
    return (
      <div className="workspace">
        <header className="topbar">
          <a className="brand" href="/">
            Электронное портфолио
            <span className="brand-dot" aria-hidden="true">
              .
            </span>
          </a>
          <div className="account-menu">
            <span className="muted">{user.email}</span>
            <button className="outline" disabled={pending} onClick={logout}>
              {pending ? 'Подождите…' : 'Выйти'}
            </button>
          </div>
        </header>
        <main className="workspace-main">
          <Feedback error={error} />
          {user.role === 'ADMIN' ? <AdminPage /> : <StudentPage />}
        </main>
      </div>
    );
  const registering = screen === 'register';
  return (
    <main className="auth">
      <section className="panel" aria-labelledby="title">
        <h1 id="title">{registering ? 'Регистрация' : 'Вход'}</h1>
        <form key={screen} onSubmit={submit}>
          <label>
            Email
            <input
              type="email"
              name="email"
              autoComplete="username"
              maxLength={254}
              required
              disabled={pending}
            />
          </label>
          <label>
            Пароль
            <input
              type="password"
              name="password"
              autoComplete={registering ? 'new-password' : 'current-password'}
              minLength={registering ? 8 : undefined}
              maxLength={128}
              required
              disabled={pending}
              aria-describedby={registering ? 'password-hint' : undefined}
            />
          </label>
          {registering && (
            <small id="password-hint">От 8 до 128 символов</small>
          )}
          <Feedback error={error} />
          <button className="primary" disabled={pending}>
            {pending
              ? 'Подождите…'
              : registering
                ? 'Зарегистрироваться'
                : 'Войти'}
          </button>
        </form>
        <button
          type="button"
          className="switch"
          disabled={pending}
          onClick={() => {
            setError('');
            setScreen(registering ? 'login' : 'register');
          }}
        >
          {registering ? 'Уже есть аккаунт? Войти' : 'Регистрация'}
        </button>
        {error && (
          <button
            className="switch"
            disabled={pending}
            onClick={() => {
              setChecking(true);
              setError('');
              setVersion((v) => v + 1);
            }}
          >
            Проверить сохранённый вход
          </button>
        )}
      </section>
    </main>
  );
}
export default App;
