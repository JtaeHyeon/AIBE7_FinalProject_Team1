import test from 'node:test';
import assert from 'node:assert/strict';
import { environment, flush } from './dom.mjs';

/** 실제 인증 화면 모듈을 실행한다. 쿠키의 HttpOnly 처리와 문서 리다이렉트는 실제 브라우저에서 별도로 검사한다. */
function setup(href, result) {
  const env = environment(path => path === '/pages/auth-art.html'
    ? { response: { ok: true, status: 200, text: async () => '' } } : result);
  env.window.location.href = href;
  env.window.location.replace = path => { env.navigations.push(path); };
  env.document.cookie = 'XSRF-TOKEN=csrf-test';
  env.document.dispatchEvent = event => env.document.emit(event.type, event.detail, event);
  const login = env.add('button', { 'data-page': 'auth' });
  login.click = () => env.navigations.push('auth');
  env.run('auth.js', { fetch: env.window.fetch.bind(env.window), Headers,
    CustomEvent: class { constructor(type, options) { this.type = type; this.detail = options.detail; } } });
  return env;
}

test('관리자 세션 복구가 성공한 경우에만 고정된 관리자 화면으로 복귀한다', async () => {
  const env = setup('http://test.invalid/?returnTo=admin', { user: { id: 9, role: 'ADMIN' } });
  await flush();
  assert.deepEqual(env.navigations, ['/admin']);
  const refresh = env.calls.find(call => call.path === '/api/auth/refresh');
  assert.equal(refresh.options.method, 'POST');
  assert.equal(refresh.options.headers['X-XSRF-TOKEN'], 'csrf-test');
});

test('갱신 쿠키도 만료됐다면 관리자 페이지를 반복해서 열지 않고 로그인 화면을 연다', async () => {
  const env = setup('http://test.invalid/?returnTo=admin', { response: { ok: false, status: 401, json: async () => ({ message: '세션이 만료됐어요.' }) } });
  await flush(); assert.deepEqual(env.navigations, ['auth']);
});

test('일반 회원이나 일반 홈 진입에는 관리자 자동 복귀를 적용하지 않는다', async () => {
  for (const [href, role] of [['http://test.invalid/?returnTo=admin', 'USER'], ['http://test.invalid/', 'ADMIN'], ['http://test.invalid/?returnTo=https://untrusted.invalid', 'ADMIN']]) {
    const env = setup(href, { user: { id: 9, role } }); await flush();
    assert.deepEqual(env.navigations, []);
  }
});
