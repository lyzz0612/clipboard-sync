import { spawn } from 'node:child_process';
import { readFileSync, rmSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const args = process.argv.slice(2);

if (args.length === 0) {
  console.error('Usage: node scripts/run-with-d1-config.mjs <wrangler args...>');
  process.exit(1);
}

const databaseId = process.env.D1_DATABASE_ID?.trim();
if (!databaseId) {
  console.error('Missing D1_DATABASE_ID environment variable.');
  process.exit(1);
}

const scriptDir = dirname(fileURLToPath(import.meta.url));
const projectDir = resolve(scriptDir, '..');
const templatePath = join(projectDir, 'wrangler.jsonc');
const generatedConfigPath = join(projectDir, '.wrangler.generated.jsonc');
const template = readFileSync(templatePath, 'utf8');

writeFileSync(
  generatedConfigPath,
  template.replaceAll('${D1_DATABASE_ID}', databaseId),
  'utf8',
);

const npxCommand = process.platform === 'win32' ? 'npx.cmd' : 'npx';
const child = spawn(npxCommand, ['wrangler', '--config', generatedConfigPath, ...args], {
  cwd: projectDir,
  stdio: 'inherit',
  env: process.env,
  shell: process.platform === 'win32',
});

function cleanup() {
  rmSync(generatedConfigPath, { force: true });
}

child.on('error', (error) => {
  cleanup();
  console.error(error);
  process.exit(1);
});

child.on('exit', (code, signal) => {
  cleanup();
  if (signal) {
    process.kill(process.pid, signal);
    return;
  }
  process.exit(code ?? 1);
});
