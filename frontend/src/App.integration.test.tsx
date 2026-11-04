// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import App from './App';

const EMAIL = 'student@example.com';
const PASSWORD = 'Student123!';

const fetchMock = vi.fn<typeof fetch>();

beforeEach(() => {
  fetchMock.mockReset();
  vi.stubGlobal('fetch', fetchMock);
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

async function fillCredentials(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText('Email'), EMAIL);
  await user.type(screen.getByLabelText('Пароль'), PASSWORD);
}

function successfulResponse(status = 200) {
  return Response.json({ id: 1, email: EMAIL }, { status });
}

describe('Формы авторизации', () => {
  it('открывает вход и переключается между формами', async () => {
    const user = userEvent.setup();
    render(<App />);

    expect(screen.getByRole('heading', { name: 'Вход' })).toBeTruthy();

    await user.click(screen.getByRole('button', { name: 'Регистрация' }));

    expect(screen.getByRole('heading', { name: 'Регистрация' })).toBeTruthy();
    expect(
      screen.getByRole('button', { name: 'Зарегистрироваться' }),
    ).toBeTruthy();

    await user.click(
      screen.getByRole('button', { name: 'Уже есть аккаунт? Войти' }),
    );

    expect(screen.getByRole('heading', { name: 'Вход' })).toBeTruthy();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('отправляет данные входа и показывает пользователя', async () => {
    fetchMock.mockResolvedValueOnce(successfulResponse());
    const user = userEvent.setup();
    render(<App />);

    await fillCredentials(user);
    await user.click(screen.getByRole('button', { name: 'Войти' }));

    expect(
      await screen.findByRole('heading', { name: 'Вы вошли' }),
    ).toBeTruthy();
    expect(screen.getByText(EMAIL)).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Выйти' })).toBeTruthy();

    expect(fetchMock).toHaveBeenCalledExactlyOnceWith('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: EMAIL, password: PASSWORD }),
    });
  });

  it('после регистрации сразу показывает вошедшего пользователя', async () => {
    fetchMock.mockResolvedValueOnce(successfulResponse(201));
    const user = userEvent.setup();
    render(<App />);

    await user.click(screen.getByRole('button', { name: 'Регистрация' }));
    await fillCredentials(user);
    await user.click(
      screen.getByRole('button', { name: 'Зарегистрироваться' }),
    );

    expect(
      await screen.findByRole('heading', { name: 'Вы вошли' }),
    ).toBeTruthy();
    expect(screen.getByText(EMAIL)).toBeTruthy();

    expect(fetchMock).toHaveBeenCalledExactlyOnceWith('/api/auth/register', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: EMAIL, password: PASSWORD }),
    });
  });

  it('выходит из аккаунта и возвращает форму входа', async () => {
    fetchMock
      .mockResolvedValueOnce(successfulResponse())
      .mockResolvedValueOnce(new Response(null, { status: 204 }));

    const user = userEvent.setup();
    render(<App />);

    await fillCredentials(user);
    await user.click(screen.getByRole('button', { name: 'Войти' }));
    await user.click(await screen.findByRole('button', { name: 'Выйти' }));

    expect(await screen.findByRole('heading', { name: 'Вход' })).toBeTruthy();
    expect(screen.queryByText(EMAIL)).toBeNull();

    expect(fetchMock).toHaveBeenLastCalledWith('/api/auth/logout', {
      method: 'POST',
      headers: { 'X-Requested-With': 'XMLHttpRequest' },
      body: undefined,
    });
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it.each([
    {
      status: 401,
      register: false,
      message: 'Проверьте email и пароль.',
    },
    {
      status: 409,
      register: true,
      message: 'Этот email уже зарегистрирован.',
    },
  ])(
    'показывает сообщение при ответе $status',
    async ({ status, register, message }) => {
      fetchMock.mockResolvedValueOnce(new Response(null, { status }));
      const user = userEvent.setup();
      render(<App />);

      if (register) {
        await user.click(screen.getByRole('button', { name: 'Регистрация' }));
      }

      await fillCredentials(user);
      await user.click(
        screen.getByRole('button', {
          name: register ? 'Зарегистрироваться' : 'Войти',
        }),
      );

      expect((await screen.findByRole('alert')).textContent).toBe(message);
      expect(screen.queryByRole('heading', { name: 'Вы вошли' })).toBeNull();
    },
  );

  it('показывает ошибку соединения и позволяет повторить вход', async () => {
    fetchMock
      .mockRejectedValueOnce(new TypeError('Failed to fetch'))
      .mockResolvedValueOnce(successfulResponse());

    const user = userEvent.setup();
    render(<App />);

    await fillCredentials(user);
    await user.click(screen.getByRole('button', { name: 'Войти' }));

    expect((await screen.findByRole('alert')).textContent).toBe(
      'Не удалось получить ответ сервера. Попробуйте ещё раз.',
    );

    await user.click(screen.getByRole('button', { name: 'Войти' }));

    expect(
      await screen.findByRole('heading', { name: 'Вы вошли' }),
    ).toBeTruthy();
    expect(screen.queryByRole('alert')).toBeNull();
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('блокирует повторную отправку, пока ожидает ответ', async () => {
    let finishRequest!: (response: Response) => void;

    fetchMock.mockReturnValueOnce(
      new Promise<Response>((resolve) => {
        finishRequest = resolve;
      }),
    );

    const user = userEvent.setup();
    render(<App />);

    await fillCredentials(user);
    await user.click(screen.getByRole('button', { name: 'Войти' }));

    const submitButton = await screen.findByRole('button', {
      name: 'Подождите…',
    });

    expect(submitButton.hasAttribute('disabled')).toBe(true);
    expect(
      screen
        .getByRole('button', { name: 'Регистрация' })
        .hasAttribute('disabled'),
    ).toBe(true);

    await user.click(submitButton);
    expect(fetchMock).toHaveBeenCalledTimes(1);

    finishRequest(successfulResponse());

    expect(
      await screen.findByRole('heading', { name: 'Вы вошли' }),
    ).toBeTruthy();
    expect(
      screen.getByRole('button', { name: 'Выйти' }).hasAttribute('disabled'),
    ).toBe(false);
  });
});
