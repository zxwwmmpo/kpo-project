import { expect, test, type Page } from '@playwright/test';
import { randomUUID } from 'node:crypto';

const password = 'Student123!';
const button = (page: Page, name: string | RegExp) =>
  page.getByRole('button', { name, exact: typeof name === 'string' });

async function register(page: Page) {
  const email = `e2e-${randomUUID()}@example.com`;
  await page.goto('/');
  await button(page, 'Регистрация').click();
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Пароль').fill(password);
  await button(page, 'Зарегистрироваться').click();
  await expect(
    page.getByRole('heading', { name: 'Заполните профиль' }),
  ).toBeVisible();
  return email;
}

async function login(page: Page, email: string, secret: string) {
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Пароль').fill(secret);
  await button(page, 'Войти').click();
}

async function png(page: Page, name: string) {
  const data = await page.evaluate(() => {
    const canvas = document.createElement('canvas');
    canvas.width = canvas.height = 32;
    const ctx = canvas.getContext('2d')!;
    ctx.fillStyle = '#245488';
    ctx.fillRect(0, 0, 32, 32);
    return canvas.toDataURL('image/png').split(',')[1];
  });
  return { name, mimeType: 'image/png', buffer: Buffer.from(data, 'base64') };
}

test('Регистрация, выход и повторный вход', async ({ page }) => {
  const email = await register(page);
  await button(page, 'Выйти').click();
  await expect(page.getByLabel('Email')).toBeVisible();
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Вход' })).toBeVisible();
  await login(page, email, password);
  await expect(button(page, 'Выгрузить профиль')).toBeVisible();
});

test('Группа администратора и сохранение профиля студента', async ({
  page,
}) => {
  const suffix = randomUUID().slice(0, 8);
  const faculty = 'Факультет ' + suffix;
  const direction = 'Направление ' + suffix;
  const group = 'Группа ' + suffix;
  await page.goto('/');
  await login(page, 'admin@admin.com', 'PlaywrightLocal123!');
  for (const [section, name] of [
    ['Факультеты', faculty],
    ['Направления', direction],
    ['Группы', group],
  ]) {
    const column = page.getByRole('region', { name: section });
    await column.getByRole('button', { name: '+ Добавить' }).click();
    await column.getByLabel('Название').fill(name);
    await column.getByRole('button', { name: 'Сохранить' }).click();
    if (name !== group) await button(page, name).click();
    else await expect(column.getByText(group, { exact: true })).toBeVisible();
  }
  await button(page, 'Выйти').click();
  await expect(page.getByLabel('Email')).toBeVisible();
  await register(page);
  await button(page, 'Редактировать профиль').click();
  await page.getByLabel('ФИО').fill('Иван Иванов');
  await page.getByLabel('Факультет').selectOption({ label: faculty });
  await page
    .getByLabel('Направление обучения')
    .selectOption({ label: direction });
  await page
    .getByRole('combobox', { name: /^Группа/ })
    .selectOption({ label: group });
  await button(page, 'Сохранить').click();
  await expect(
    page.getByRole('heading', { name: 'Иван Иванов' }),
  ).toBeVisible();
  await button(page, 'Редактировать').click();
  await page.getByLabel('Расскажите о себе').fill('Изучаю Java.');
  await button(page, 'Сохранить').click();
  await button(page, '+ Добавить').click();
  await page.getByLabel('Тип контакта').selectOption('URL');
  await page.getByLabel('Значение').fill('https://example.org');
  await button(page, 'Сохранить контакт').click();
  await page
    .getByLabel('Выбрать фотографию')
    .setInputFiles(await png(page, 'avatar.png'));
  try {
    await button(page, 'Сохранить фото').click();
    await expect(page.getByAltText('Фотография профиля')).toBeVisible();
    await page.reload();
    await expect(
      page.getByRole('heading', { name: 'Иван Иванов' }),
    ).toBeVisible();
    await expect(page.getByText(group, { exact: true })).toBeVisible();
    await expect(page.getByText('Изучаю Java.', { exact: true })).toBeVisible();
    await expect(page.getByRole('link', { name: /example.org/ })).toBeVisible();
    await expect(page.getByAltText('Фотография профиля')).toBeVisible();
  } finally {
    const removed = await page.request.delete('/api/profile/photo', {
      headers: { 'X-Requested-With': 'XMLHttpRequest' },
    });
    expect(removed.ok()).toBeTruthy();
  }
});

test('Создание, редактирование и удаление проекта с материалами', async ({
  page,
}) => {
  await register(page);
  await button(page, 'Добавить проект').click();
  await page.getByLabel('Название', { exact: true }).fill('Расписание');
  await page.getByLabel('Описание', { exact: true }).fill('Расписание группы');
  await button(page, 'Сохранить проект').click();
  await button(page, 'Редактировать проект').click();
  await page.getByLabel('Название', { exact: true }).fill('Учебное расписание');
  await button(page, 'Сохранить проект').click();
  await page
    .getByLabel('Файл проекта')
    .setInputFiles(await png(page, 'schedule.png'));
  await button(page, 'Прикрепить файл').click();
  await expect(page.getByRole('link', { name: 'schedule.png' })).toBeVisible();
  await button(page, 'Добавить ссылку').click();
  await page.getByLabel('Адрес ссылки').fill('https://example.org/schedule');
  await button(page, 'Добавить ссылку').click();
  const link = page.getByRole('link', { name: /example\.org/ });
  await expect(link).toHaveAttribute('href', 'https://example.org/schedule');
  await page.reload();
  await button(page, /Учебное расписание/).click();
  await expect(link).toHaveAttribute('href', 'https://example.org/schedule');
  await expect(page.getByRole('link', { name: 'schedule.png' })).toBeVisible();
  page.once('dialog', (dialog) => dialog.dismiss());
  await button(page, 'Удалить проект').click();
  await expect(
    page.getByRole('heading', { name: 'Учебное расписание' }),
  ).toBeVisible();
  page.once('dialog', (dialog) => dialog.accept());
  await button(page, 'Удалить проект').click();
  await expect(page.getByText('У вас пока нет проектов.')).toBeVisible();
});

test('Добавление сертификата и удаление достижения', async ({ page }) => {
  await register(page);
  const open = () => page.getByRole('link', { name: /^Достижения/ }).click();
  await open();
  await button(page, 'Добавить достижение').click();
  await page.getByLabel('Название достижения').fill('Олимпиада');
  await page.getByLabel('Дата достижения').fill('2025-04-14');
  await page
    .getByLabel('Выбрать сертификат')
    .setInputFiles(await png(page, 'Диплом.png'));
  await button(page, 'Сохранить достижение').click();
  const certificate = page.getByRole('link', { name: 'Диплом.png' });
  await expect(certificate).toBeVisible();
  await page.reload();
  await open();
  await expect(certificate).toBeVisible();
  page.once('dialog', (dialog) => dialog.accept());
  await button(page, /Удалить достижение.*Олимпиада/).click();
  await expect(page.getByText('У вас пока нет достижений.')).toBeVisible();
  await expect(certificate).toHaveCount(0);
});

test('Кнопка выгрузки открывает предпросмотр', async ({ page }) => {
  await register(page);
  await button(page, 'Выгрузить профиль').click();
  await expect(
    page.getByRole('article', { name: 'Документ профиля' }),
  ).toBeVisible();
});
