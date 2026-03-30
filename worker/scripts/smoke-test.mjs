const BASE_URL = process.env.BASE_URL || 'http://localhost:8787';
const username = 'testuser_' + Math.random().toString(36).slice(2, 10);
const password = 'testpass_' + Date.now();
const adminPassword = process.env.ADMIN_PASSWORD || '';

let token = '';
let clipId = '';
let adminToken = '';

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

  // 1. Registration status
  console.log('1. GET register status');
  const registerStatus = await request('GET', '/api/register-status');
  assert(registerStatus.status === 200, `Register status returned ${registerStatus.status}`);
  assert(typeof registerStatus.data.enabled === 'boolean', 'Register status returned enabled boolean');

  // 2. Register
  console.log('2. Register');
  const reg = await request('POST', '/api/register', { username, password });
  assert(reg.status === 201, `Register returned ${reg.status}`);
  assert(reg.data.ok === true, 'Register response has ok: true');

  // 3. Login
  console.log('3. Login');
  const login = await request('POST', '/api/login', { username, password });
  assert(login.status === 200, `Login returned ${login.status}`);
  assert(typeof login.data.token === 'string', 'Login returned a token');
  token = login.data.token;

  // 4. POST a clip
  console.log('4. POST clip');
  const beforePost = new Date(Date.now() - 1000).toISOString();
  const post = await request('POST', '/api/clips', { text: 'hello smoke test' }, token);
  assert(post.status === 201, `POST clip returned ${post.status}`);
  assert(post.data.clip.text === 'hello smoke test', 'Clip text matches');
  clipId = post.data.clip.id;

  // 5. GET /api/clips
  console.log('5. GET clips');
  const get = await request('GET', '/api/clips', null, token);
  assert(get.status === 200, `GET clips returned ${get.status}`);
  assert(Array.isArray(get.data.clips), 'Clips is an array');
  assert(get.data.clips.some(c => c.id === clipId), 'Clip is in the list');

  // 6. GET /api/clips/delta
  console.log('6. GET clips/delta');
  const delta = await request('GET', `/api/clips/delta?since=${encodeURIComponent(beforePost)}`, null, token);
  assert(delta.status === 200, `GET delta returned ${delta.status}`);
  assert(delta.data.clips.some(c => c.id === clipId), 'Clip appears in delta');
  assert(typeof delta.data.serverTime === 'string', 'Delta has serverTime');

  // 7. DELETE the clip
  console.log('7. DELETE clip');
  const del = await request('DELETE', `/api/clips/${clipId}`, null, token);
  assert(del.status === 200, `DELETE returned ${del.status}`);
  assert(del.data.ok === true, 'Delete response has ok: true');

  // 8. Verify empty
  console.log('8. Verify clips empty');
  const after = await request('GET', '/api/clips', null, token);
  assert(after.status === 200, `GET clips returned ${after.status}`);
  assert(after.data.clips.length === 0, 'Clips list is empty');

  if (adminPassword) {
    // 9. Admin login
    console.log('9. Admin login');
    const adminLogin = await request('POST', '/api/admin/login', { password: adminPassword });
    assert(adminLogin.status === 200, `Admin login returned ${adminLogin.status}`);
    assert(typeof adminLogin.data.token === 'string', 'Admin login returned a token');
    adminToken = adminLogin.data.token;

    // 10. Admin list users
    console.log('10. Admin list users');
    const users = await request('GET', '/api/admin/users', null, adminToken);
    assert(users.status === 200, `Admin users returned ${users.status}`);
    assert(Array.isArray(users.data.users), 'Admin users returned an array');
    assert(users.data.users.some(u => u.username === username), 'Admin users includes created user');

    // 11. Toggle registration off
    console.log('11. Disable registration');
    const disable = await request('PUT', '/api/admin/settings', { allowRegistration: false }, adminToken);
    assert(disable.status === 200, `Disable registration returned ${disable.status}`);
    assert(disable.data.settings.allowRegistration === false, 'Registration disabled');

    const blocked = await request(
      'POST',
      '/api/register',
      { username: `${username}_blocked`, password: `${password}_blocked` },
    );
    assert(blocked.status === 403, `Register while disabled returned ${blocked.status}`);

    // 12. Delete user and re-enable registration
    console.log('12. Delete user and re-enable registration');
    const remove = await request('DELETE', '/api/admin/users', { usernames: [username] }, adminToken);
    assert(remove.status === 200, `Admin delete users returned ${remove.status}`);
    assert(remove.data.deleted.some(u => u.username === username), 'Admin delete removed created user');

    const enable = await request('PUT', '/api/admin/settings', { allowRegistration: true }, adminToken);
    assert(enable.status === 200, `Enable registration returned ${enable.status}`);
    assert(enable.data.settings.allowRegistration === true, 'Registration re-enabled');
  } else {
    console.log('9-12. Skipped admin tests because ADMIN_PASSWORD is not set');
  }

  console.log('\n✅ All smoke tests passed!');
}

run().catch(err => {
  console.error('Smoke test failed:', err);
  process.exit(1);
});
