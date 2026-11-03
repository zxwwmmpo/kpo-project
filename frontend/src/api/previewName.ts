export async function previewName(name: string): Promise<string> {
  const response = await fetch('/api/profile/name/preview', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name }),
  });

  if (response.status === 400) {
    throw new Error('Введите имя студента');
  }

  if (!response.ok) {
    throw new Error('Не удалось получить предпросмотр');
  }

  const data: { name: string } = await response.json();
  return data.name;
}
