import { useState, type FormEvent } from 'react';
import './App.css';

type Screen = 'login' | 'register' | 'account';

function App() {
  const [screen, setScreen] = useState<Screen>('login');
  const [email, setEmail] = useState('');
  const [error, setError] = useState('');
  const [pending, setPending] = useState(false);
  const registering = screen === 'register';
  const account = screen === 'account';

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (pending) return;
    const data = new FormData(event.currentTarget);
    setError('');
    setPending(true);
    try {
      const response = await fetch(
        '/api/auth/' + (account ? 'logout' : screen),
        {
          method: 'POST',
          headers: account
            ? { 'X-Requested-With': 'XMLHttpRequest' }
            : { 'Content-Type': 'application/json' },
          body: account
            ? undefined
            : JSON.stringify({
                email: data.get('email'),
                password: data.get('password'),
              }),
        },
      );
      if (!response.ok) {
        const messages: Record<number, string> = {
          400: 'Проверьте введённые данные.',
          401: 'Проверьте email и пароль.',
          409: 'Этот email уже зарегистрирован.',
        };
        setError(
          messages[response.status] ?? 'Сервис недоступен. Попробуйте позже.',
        );
        return;
      }
      if (account) {
        setEmail('');
        setScreen('login');
      } else {
        const user = await response.json();
        setEmail(user.email);
        setScreen('account');
      }
    } catch {
      setError('Не удалось получить ответ сервера. Попробуйте ещё раз.');
    } finally {
      setPending(false);
    }
  }

  return (
    <main className="auth">
      <section className="panel" aria-labelledby="title">
        <h1 id="title">
          {account ? 'Вы вошли' : registering ? 'Регистрация' : 'Вход'}
        </h1>
        {account && <p className="identity">{email}</p>}
        <form key={screen} onSubmit={submit}>
          {!account && (
            <>
              <label>
                Email
                <input
                  type="email"
                  name="email"
                  autoComplete="username"
                  maxLength={254}
                  required
                />
              </label>
              <label>
                Пароль
                <input
                  type="password"
                  name="password"
                  autoComplete={
                    registering ? 'new-password' : 'current-password'
                  }
                  minLength={registering ? 8 : undefined}
                  maxLength={128}
                  required
                  aria-describedby={registering ? 'password-hint' : undefined}
                />
              </label>
              {registering && (
                <small id="password-hint">От 8 до 128 символов</small>
              )}
            </>
          )}
          {error && <p role="alert">{error}</p>}
          <button className="primary" disabled={pending}>
            {pending
              ? 'Подождите…'
              : account
                ? 'Выйти'
                : registering
                  ? 'Зарегистрироваться'
                  : 'Войти'}
          </button>
        </form>
        {!account && (
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
        )}
      </section>
    </main>
  );
}

export default App;
