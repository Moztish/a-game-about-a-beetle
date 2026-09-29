const path = require('node:path');
const fs = require('node:fs');
const http = require('node:http');
const crypto = require('node:crypto');
const { DatabaseSync } = require('node:sqlite');

const port = Number(process.env.PORT) || 3000;
const dataDirectory = path.join(__dirname, 'data');
fs.mkdirSync(dataDirectory, { recursive: true });

const database = new DatabaseSync(path.join(dataDirectory, 'bug-users.db'));
database.exec('PRAGMA journal_mode = WAL');
database.exec(`
  CREATE TABLE IF NOT EXISTS users (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    nickname TEXT NOT NULL UNIQUE COLLATE NOCASE,
    shell_color TEXT NOT NULL,
    email TEXT NOT NULL UNIQUE COLLATE NOCASE,
    password_hash TEXT NOT NULL,
    password_salt TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
  )
`);

const insertUser = database.prepare(`
  INSERT INTO users (nickname, shell_color, email, password_hash, password_salt)
  VALUES (@nickname, @shellColor, @email, @passwordHash, @passwordSalt)
`);

function hashPassword(password, salt = crypto.randomBytes(16).toString('hex')) {
  const passwordHash = crypto.scryptSync(password, salt, 64).toString('hex');
  return { passwordHash, passwordSalt: salt };
}

function sendJson(response, statusCode, payload) {
  response.writeHead(statusCode, { 'Content-Type': 'application/json; charset=utf-8' });
  response.end(JSON.stringify(payload));
}

function readBody(request) {
  return new Promise((resolve, reject) => {
    let body = '';
    request.on('data', (chunk) => {
      body += chunk;
      if (body.length > 10_000) reject(new Error('payload-too-large'));
    });
    request.on('end', () => resolve(body));
    request.on('error', reject);
  });
}

function validateRegistration(input) {
  const nickname = typeof input.nickname === 'string' ? input.nickname.trim() : '';
  const email = typeof input.email === 'string' ? input.email.trim().toLowerCase() : '';
  const shellColor = typeof input.shellColor === 'string' ? input.shellColor.trim() : '';
  const password = typeof input.password === 'string' ? input.password : '';

  if (nickname.length < 2 || nickname.length > 24) return 'Имя жука должно содержать от 2 до 24 символов.';
  if (!/^[\p{L}\p{N}_ -]+$/u.test(nickname)) return 'Имя жука содержит недопустимые символы.';
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) return 'Проверь адрес почты.';
  if (!['Глубокий мох', 'Ночная слива', 'Медный песок', 'Тёмный янтарь'].includes(shellColor)) return 'Выбран неизвестный цвет панциря.';
  if (password.length < 8 || password.length > 128) return 'Пароль должен содержать от 8 до 128 символов.';
  return null;
}

async function handleRegistration(request, response) {
  try {
    const input = JSON.parse(await readBody(request));
    const error = validateRegistration(input);
    if (error) return sendJson(response, 400, { error });

    const nickname = input.nickname.trim();
    const email = input.email.trim().toLowerCase();
    const { passwordHash, passwordSalt } = hashPassword(input.password);

    const result = insertUser.run({
      nickname,
      shellColor: input.shellColor,
      email,
      passwordHash,
      passwordSalt
    });

    return sendJson(response, 201, { message: 'Регистрация завершена.', userId: result.lastInsertRowid });
  } catch (error) {
    if (error instanceof SyntaxError) return sendJson(response, 400, { error: 'Некорректные данные регистрации.' });
    if (error.code === 'SQLITE_CONSTRAINT_UNIQUE') {
      return sendJson(response, 409, { error: 'Такое имя жука или почта уже заняты.' });
    }
    if (error.message === 'payload-too-large') return sendJson(response, 413, { error: 'Слишком большой запрос.' });
    console.error(error);
    return sendJson(response, 500, { error: 'Не удалось сохранить регистрацию.' });
  }
}

const server = http.createServer(async (request, response) => {
  const requestUrl = new URL(request.url, `http://${request.headers.host}`);
  if (request.method === 'POST' && requestUrl.pathname === '/api/register') {
    return handleRegistration(request, response);
  }

  const filePath = requestUrl.pathname === '/' ? path.join(__dirname, 'index.html') : path.join(__dirname, requestUrl.pathname);
  if (!filePath.startsWith(__dirname) || !fs.existsSync(filePath) || fs.statSync(filePath).isDirectory()) {
    return sendJson(response, 404, { error: 'Страница не найдена.' });
  }

  const contentTypes = { '.html': 'text/html; charset=utf-8', '.css': 'text/css; charset=utf-8', '.js': 'text/javascript; charset=utf-8' };
  response.writeHead(200, { 'Content-Type': contentTypes[path.extname(filePath)] || 'application/octet-stream' });
  fs.createReadStream(filePath).pipe(response);
});

server.listen(port, () => {
  console.log(`Навозник запущен: http://localhost:${port}`);
});
