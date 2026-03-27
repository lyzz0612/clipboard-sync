const BASE_URL = process.env.BASE_URL || 'http://localhost:8787';
const username = 'testuser_' + Math.random().toString(36).slice(2, 10);
const password = 'testpass_' + Date.now();

let token = '';
let clipId = '';

async function request(method, path, body, authToken) {
  const headers = { 'Content-Type': 'application/json' };
  if (authToken) headers['Authorization'] = `Bearer ${authToken}`;
  const opts = { method, headers };
  if (body) opts.body = JSON.stringify(body);
  const res = await fetch(`${BASE_URL}${path}`, opts);
  const data = await res.json();
  return { status: res.status, data };
}

function assert(condition, msg) {
  if (!condition) {
    console.error(`FAIL: ${msg}`);
    process.exit(1);
  }
  console.log(`  ✓ ${msg}`);
}

async function run() {
  console.log(`Running smoke tests against ${BASE_URL}`);
  console.log(`Test user: ${username}\n`);

  // 1. Register
  console.log('1. Register');
  const reg = await request('POST', '/api/register', { username, password });
  assert(reg.status === 201, `Register returned ${reg.status}`);
  assert(reg.data.ok === true, 'Register response has ok: true');

  // 2. Login
  console.log('2. Login');
  const login = await request('POST', '/api/login', { username, password });
  assert(login.status === 200, `Login returned ${login.status}`);
  assert(typeof login.data.token === 'string', 'Login returned a token');
  token = login.data.token;

  // 3. POST a clip
  console.log('3. POST clip');
  const beforePost = new Date(Date.now() - 1000).toISOString();
  const post = await request('POST', '/api/clips', { text: 'hello smoke test' }, token);
  assert(post.status === 201, `POST clip returned ${post.status}`);
  assert(post.data.clip.text === 'hello smoke test', 'Clip text matches');
  clipId = post.data.clip.id;

  // 4. GET /api/clips
  console.log('4. GET clips');
  const get = await request('GET', '/api/clips', null, token);
  assert(get.status === 200, `GET clips returned ${get.status}`);
  assert(Array.isArray(get.data.clips), 'Clips is an array');
  assert(get.data.clips.some(c => c.id === clipId), 'Clip is in the list');

  // 5. GET /api/clips/delta
  console.log('5. GET clips/delta');
  const delta = await request('GET', `/api/clips/delta?since=${encodeURIComponent(beforePost)}`, null, token);
  assert(delta.status === 200, `GET delta returned ${delta.status}`);
  assert(delta.data.clips.some(c => c.id === clipId), 'Clip appears in delta');
  assert(typeof delta.data.serverTime === 'string', 'Delta has serverTime');

  // 6. DELETE the clip
  console.log('6. DELETE clip');
  const del = await request('DELETE', `/api/clips/${clipId}`, null, token);
  assert(del.status === 200, `DELETE returned ${del.status}`);
  assert(del.data.ok === true, 'Delete response has ok: true');

  // 7. Verify empty
  console.log('7. Verify clips empty');
  const after = await request('GET', '/api/clips', null, token);
  assert(after.status === 200, `GET clips returned ${after.status}`);
  assert(after.data.clips.length === 0, 'Clips list is empty');

  console.log('\n✅ All smoke tests passed!');
}

run().catch(err => {
  console.error('Smoke test failed:', err);
  process.exit(1);
});
