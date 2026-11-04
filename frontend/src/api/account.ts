export type User = { id: number; email: string; role: 'ADMIN' | 'STUDENT' };
export type Entry = { id: number; name: string; parentId: number | null };
export type Catalog = {
  faculties: Entry[];
  directions: Entry[];
  groups: Entry[];
};
export type Profile = {
  email: string;
  fullName: string | null;
  faculty: Entry | null;
  direction: Entry | null;
  studyGroup: Entry | null;
  about: string | null;
};
export type Contact = {
  id: number;
  type: 'PHONE' | 'URL' | 'GITHUB';
  value: string;
};

export class ApiError extends Error {
  status: number;
  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

export async function request(path: string, options: RequestInit = {}) {
  let response: Response;
  try {
    response = await fetch('/api' + path, {
      ...options,
      headers: {
        ...(options.method && options.method !== 'GET'
          ? { 'X-Requested-With': 'XMLHttpRequest' }
          : {}),
        ...options.headers,
      },
    });
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError')
      throw error;
    throw new Error('Не удалось получить ответ сервера. Попробуйте ещё раз.', {
      cause: error,
    });
  }
  if (!response.ok) {
    const defaults: Record<number, string> = {
      400: 'Проверьте введённые данные.',
      401: 'Необходимо войти в систему.',
      403: 'Недостаточно прав.',
      409: 'Данные используются или уже существуют.',
      413: 'Размер фотографии не должен превышать 5 МБ.',
    };
    const body = await response.json().catch(() => null);
    if (
      response.status === 401 &&
      !['/auth/me', '/auth/login', '/auth/register'].includes(path)
    )
      window.dispatchEvent(new Event('session-expired'));
    throw new ApiError(
      response.status,
      body?.detail ||
        defaults[response.status] ||
        'Сервис недоступен. Попробуйте позже.',
    );
  }
  return response;
}

export async function api<T>(
  path: string,
  method = 'GET',
  body?: unknown,
  signal?: AbortSignal,
): Promise<T> {
  const response = await request(path, {
    method,
    signal,
    ...(body === undefined
      ? {}
      : {
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(body),
        }),
  });
  return response.status === 204 ? (undefined as T) : response.json();
}

export async function loadCatalog(signal?: AbortSignal): Promise<Catalog> {
  const [faculties, directions, groups] = await Promise.all(
    ['faculties', 'directions', 'groups'].map((type) =>
      api<Entry[]>('/catalog/' + type, 'GET', undefined, signal),
    ),
  );
  return { faculties, directions, groups };
}

export function message(error: unknown) {
  return error instanceof Error
    ? error.message
    : 'Не удалось выполнить действие.';
}
