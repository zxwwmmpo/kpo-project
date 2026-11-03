import { afterEach, expect, it, vi } from 'vitest';
import { previewName } from './previewName';

afterEach(() => {
  vi.unstubAllGlobals();
});

it('отправляет введённое имя и возвращает результат сервера', async () => {
  const send = vi
    .fn()
    .mockResolvedValue(Response.json({ name: 'Иван Иванов' }));
  vi.stubGlobal('fetch', send);

  await expect(previewName('  Иван Иванов  ')).resolves.toBe('Иван Иванов');

  expect(send).toHaveBeenCalledWith('/api/profile/name/preview', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name: '  Иван Иванов  ' }),
  });
});

it('просит ввести имя, если сервер вернул 400', async () => {
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue(new Response(null, { status: 400 })),
  );

  await expect(previewName('   ')).rejects.toThrow('Введите имя студента');
});

it('сообщает об ошибке сервера при ответе 500', async () => {
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue(new Response(null, { status: 500 })),
  );

  await expect(previewName('Иван')).rejects.toThrow(
    'Не удалось получить предпросмотр',
  );
});
