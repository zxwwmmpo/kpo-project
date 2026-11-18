import { defineConfig } from '@playwright/test';
import { resolve } from 'node:path';

export default defineConfig({
  testDir: './e2e',
  forbidOnly: !!process.env.CI,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: { baseURL: 'http://127.0.0.1:5173', trace: 'retain-on-failure' },
  projects: [{ name: 'chromium', use: { browserName: 'chromium' } }],
  webServer: [
    {
      cwd: './backend',
      command:
        (process.platform === 'win32' ? 'mvnw.cmd' : 'bash mvnw') +
        ' -Dspring-boot.run.useTestClasspath=true spring-boot:run',
      url: 'http://127.0.0.1:8080/api/auth/me',
      timeout: 180_000,
      env: {
        SERVER_ADDRESS: '127.0.0.1',
        SPRING_DATASOURCE_URL: 'jdbc:h2:mem:portfolio_e2e',
        SPRING_DATASOURCE_USERNAME: 'sa',
        SPRING_DATASOURCE_PASSWORD: '',
        SPRING_JPA_HIBERNATE_DDL_AUTO: 'create-drop',
        APP_ADMIN_ENABLED: 'true',
        APP_ADMIN_PASSWORD: 'PlaywrightLocal123!',
        APP_PROFILEPHOTO_DIRECTORY: resolve('test-results', 'profile-photos'),
      },
    },
    {
      cwd: './frontend',
      command: 'npm run dev',
      url: 'http://127.0.0.1:5173',
    },
  ],
});
