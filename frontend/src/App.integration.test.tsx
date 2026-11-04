// @vitest-environment jsdom
import {
  cleanup,
  render,
  screen,
  waitFor,
  within,
} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import App from './App';
import type { User } from './api/account';

const student: User = { id: 1, email: 'student@example.com', role: 'STUDENT' };
let current: User | null;
const handlers = new Map<
  string,
  (options: RequestInit) => Response | Promise<Response>
>();
const fetchMock = vi.fn<typeof fetch>();
const emptyProfile = {
  email: student.email,
  fullName: null,
  faculty: null,
  direction: null,
  studyGroup: null,
  about: null,
};
const faculties = [
  { id: 1, name: 'ФИСТ', parentId: null },
  { id: 2, name: 'Другой факультет', parentId: null },
];
const directions = [{ id: 3, name: 'Программная инженерия', parentId: 1 }];
const groups = [{ id: 4, name: 'ПИбд-41', parentId: 3 }];

beforeEach(() => {
  current = null;
  handlers.clear();
  fetchMock.mockReset();
  vi.stubGlobal('fetch', fetchMock);
  vi.spyOn(window, 'confirm').mockReturnValue(true);
  vi.stubGlobal(
    'URL',
    class extends URL {
      static createObjectURL = vi.fn(() => 'blob:test');
      static revokeObjectURL = vi.fn();
    },
  );
  fetchMock.mockImplementation(async (input, options = {}) => {
    const path = String(input);
    const handler = handlers.get((options.method || 'GET') + ' ' + path);
    if (handler) return handler(options);
    if (path === '/api/auth/me')
      return current
        ? Response.json(current)
        : new Response(null, { status: 401 });
    if (path === '/api/auth/login' || path === '/api/auth/register') {
      current = student;
      return Response.json(student, {
        status: path.endsWith('register') ? 201 : 200,
      });
    }
    if (path === '/api/auth/logout') {
      current = null;
      return new Response(null, { status: 204 });
    }
    if (path === '/api/profile') return Response.json(emptyProfile);
    if (path === '/api/profile/contacts') return Response.json([]);
    if (path === '/api/profile/photo')
      return new Response(null, { status: 404 });
    if (path === '/api/catalog/faculties') return Response.json(faculties);
    if (path === '/api/catalog/directions') return Response.json(directions);
    if (path === '/api/catalog/groups') return Response.json(groups);
    throw new Error('Unexpected request: ' + path);
  });
});

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

async function credentials(user: ReturnType<typeof userEvent.setup>) {
  await user.type(await screen.findByLabelText('Email'), student.email);
  await user.type(screen.getByLabelText('Пароль'), 'Student123!');
}

it('открывает вход и переключает регистрацию', async () => {
  const user = userEvent.setup();
  render(<App />);
  expect(await screen.findByRole('heading', { name: 'Вход' })).toBeTruthy();
  await user.click(screen.getByRole('button', { name: 'Регистрация' }));
  expect(screen.getByRole('heading', { name: 'Регистрация' })).toBeTruthy();
  await user.click(
    screen.getByRole('button', { name: 'Уже есть аккаунт? Войти' }),
  );
  expect(screen.getByRole('heading', { name: 'Вход' })).toBeTruthy();
});

it.each(['login', 'register'])(
  'после %s открывает профиль и выходит обратно к входу',
  async (mode) => {
    const user = userEvent.setup();
    render(<App />);
    await screen.findByLabelText('Email');
    if (mode === 'register')
      await user.click(screen.getByRole('button', { name: 'Регистрация' }));
    await credentials(user);
    await user.click(
      screen.getByRole('button', {
        name: mode === 'register' ? 'Зарегистрироваться' : 'Войти',
      }),
    );
    expect(
      await screen.findByRole('heading', { name: 'Заполните профиль' }),
    ).toBeTruthy();
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/auth/' + mode,
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ email: student.email, password: 'Student123!' }),
      }),
    );
    await user.click(screen.getByRole('button', { name: 'Выйти' }));
    expect(await screen.findByRole('heading', { name: 'Вход' })).toBeTruthy();
  },
);

it('восстанавливает сессию администратора и показывает справочники', async () => {
  current = { ...student, role: 'ADMIN' };
  render(<App />);
  expect(
    await screen.findByRole('heading', { name: 'Управление справочниками' }),
  ).toBeTruthy();
  expect(
    screen.queryByRole('button', { name: 'Редактировать профиль' }),
  ).toBeNull();
});

it.each([401, 409])('показывает ошибку авторизации %s', async (status) => {
  handlers.set('POST /api/auth/login', () => new Response(null, { status }));
  const user = userEvent.setup();
  render(<App />);
  await credentials(user);
  await user.click(screen.getByRole('button', { name: 'Войти' }));
  expect((await screen.findByRole('alert')).textContent).toBe(
    status === 401
      ? 'Проверьте email и пароль.'
      : 'Этот email уже зарегистрирован.',
  );
});

it('позволяет повторить вход после сетевой ошибки', async () => {
  handlers.set('POST /api/auth/login', () => {
    throw new TypeError('Failed to fetch');
  });
  const user = userEvent.setup();
  render(<App />);
  await credentials(user);
  await user.click(screen.getByRole('button', { name: 'Войти' }));
  expect((await screen.findByRole('alert')).textContent).toContain(
    'Не удалось получить ответ сервера',
  );
  handlers.delete('POST /api/auth/login');
  await user.click(screen.getByRole('button', { name: 'Войти' }));
  expect(
    await screen.findByRole('heading', { name: 'Заполните профиль' }),
  ).toBeTruthy();
});

it('блокирует повторную отправку до ответа сервера', async () => {
  let finish!: (response: Response) => void;
  handlers.set(
    'POST /api/auth/login',
    () =>
      new Promise((resolve) => {
        finish = resolve;
      }),
  );
  const user = userEvent.setup();
  render(<App />);
  await credentials(user);
  await user.click(screen.getByRole('button', { name: 'Войти' }));
  const button = screen.getByRole('button', { name: 'Подождите…' });
  expect(button.hasAttribute('disabled')).toBe(true);
  await user.click(button);
  expect(
    fetchMock.mock.calls.filter(([path]) => path === '/api/auth/login'),
  ).toHaveLength(1);
  current = student;
  finish(Response.json(student));
  await screen.findByRole('heading', { name: 'Заполните профиль' });
});

it('сбрасывает зависимые списки и сохраняет выбранную группу', async () => {
  current = student;
  handlers.set('PUT /api/profile', (options) =>
    Response.json({
      ...emptyProfile,
      ...JSON.parse(String(options.body)),
      faculty: faculties[0],
      direction: directions[0],
      studyGroup: groups[0],
    }),
  );
  const user = userEvent.setup();
  render(<App />);
  await user.click(
    await screen.findByRole('button', { name: 'Редактировать профиль' }),
  );
  await user.type(screen.getByLabelText('ФИО'), 'Иван Иванов');
  await user.selectOptions(screen.getByLabelText('Факультет'), '1');
  await user.selectOptions(screen.getByLabelText('Направление обучения'), '3');
  await user.selectOptions(screen.getByLabelText('Группа'), '4');
  await user.selectOptions(screen.getByLabelText('Факультет'), '2');
  expect((screen.getByLabelText('Группа') as HTMLSelectElement).value).toBe('');
  expect(
    (screen.getByLabelText('Направление обучения') as HTMLSelectElement).value,
  ).toBe('');
  await user.selectOptions(screen.getByLabelText('Факультет'), '1');
  await user.selectOptions(screen.getByLabelText('Направление обучения'), '3');
  await user.selectOptions(screen.getByLabelText('Группа'), '4');
  await user.click(screen.getByRole('button', { name: 'Сохранить' }));
  expect(
    await screen.findByRole('heading', { name: 'Иван Иванов' }),
  ).toBeTruthy();
  expect(fetchMock).toHaveBeenCalledWith(
    '/api/profile',
    expect.objectContaining({
      method: 'PUT',
      body: JSON.stringify({
        fullName: 'Иван Иванов',
        groupId: 4,
        about: null,
      }),
    }),
  );
});

it('сохраняет О себе, не теряя учебные данные', async () => {
  current = student;
  const profile = {
    ...emptyProfile,
    fullName: 'Иван Иванов',
    faculty: faculties[0],
    direction: directions[0],
    studyGroup: groups[0],
  };
  handlers.set('GET /api/profile', () => Response.json(profile));
  handlers.set('PUT /api/profile', (options) =>
    Response.json({
      ...profile,
      about: JSON.parse(String(options.body)).about,
    }),
  );
  const user = userEvent.setup();
  render(<App />);
  await user.click(
    await screen.findByRole('button', { name: 'Редактировать' }),
  );
  await user.type(screen.getByLabelText('Расскажите о себе'), 'Изучаю Java.');
  await user.click(screen.getByRole('button', { name: 'Сохранить' }));
  expect(await screen.findByText('Изучаю Java.')).toBeTruthy();
  expect(fetchMock).toHaveBeenCalledWith(
    '/api/profile',
    expect.objectContaining({
      method: 'PUT',
      body: JSON.stringify({
        fullName: 'Иван Иванов',
        groupId: 4,
        about: 'Изучаю Java.',
      }),
    }),
  );
});

it('добавляет несколько контактов одного типа', async () => {
  current = student;
  let id = 0;
  handlers.set('POST /api/profile/contacts', (options) =>
    Response.json(
      { id: ++id, ...JSON.parse(String(options.body)) },
      { status: 201 },
    ),
  );
  const user = userEvent.setup();
  render(<App />);
  await screen.findByRole('heading', { name: 'Контакты' });
  for (const value of ['https://one.example', 'https://two.example']) {
    await user.click(screen.getByRole('button', { name: '+ Добавить' }));
    await user.selectOptions(screen.getByLabelText('Тип контакта'), 'URL');
    await user.type(screen.getByLabelText('Значение'), value);
    await user.click(screen.getByRole('button', { name: 'Сохранить контакт' }));
    expect(await screen.findByRole('link', { name: value })).toBeTruthy();
  }
  expect(screen.getAllByRole('link', { name: /https:\/\// })).toHaveLength(2);
});

it('оставляет занятую группу при отказе сервера в удалении', async () => {
  current = { ...student, role: 'ADMIN' };
  handlers.set('DELETE /api/catalog/groups/4', () =>
    Response.json({ detail: 'В группе есть студенты' }, { status: 409 }),
  );
  const user = userEvent.setup();
  render(<App />);
  await user.click(await screen.findByRole('button', { name: 'ФИСТ' }));
  await user.click(
    screen.getByRole('button', { name: 'Программная инженерия' }),
  );
  await user.click(screen.getByRole('button', { name: 'Удалить ПИбд-41' }));
  expect((await screen.findByRole('alert')).textContent).toBe(
    'В группе есть студенты',
  );
  expect(
    within(screen.getByRole('region', { name: 'Группы' })).getByText('ПИбд-41'),
  ).toBeTruthy();
});

it('показывает предпросмотр фото и отправляет multipart без ручного Content-Type', async () => {
  current = student;
  handlers.set(
    'POST /api/profile/photo',
    () => new Response(null, { status: 204 }),
  );
  const user = userEvent.setup();
  render(<App />);
  await screen.findByRole('button', { name: 'Загрузить фото' });
  await user.upload(
    screen.getByLabelText('Выбрать фотографию'),
    new File(['photo'], 'photo.png', { type: 'image/png' }),
  );
  expect(await screen.findByAltText('Предпросмотр фотографии')).toBeTruthy();
  await user.click(screen.getByRole('button', { name: 'Сохранить фото' }));
  await waitFor(() =>
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/profile/photo',
      expect.objectContaining({
        method: 'POST',
        body: expect.any(FormData),
        headers: { 'X-Requested-With': 'XMLHttpRequest' },
      }),
    ),
  );
});
