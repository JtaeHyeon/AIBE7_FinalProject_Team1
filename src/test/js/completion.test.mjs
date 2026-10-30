import test from 'node:test';
import assert from 'node:assert/strict';
import { environment, deferred, flush } from './dom.mjs';

const context = (version = 7) => ({ factsVersion: version, completionAllowed: true, unreviewedChangeCount: 0, listing: {} });
const options = status => ({ userId: 2, roomId: 1, role: 'buyer', title: '노트북', request: { requestId: 3, transactionStatus: status || 'RESERVED' }, isCurrent: () => true, onChanged: async () => {} });

/** 실제 완료 화면 모듈에 지연·실패 응답을 주입한다. */
function screen(handler) {
  const env = environment(handler);
  const root = env.add('section', { 'data-deals-completion': '' });
  env.run('deals-completion.js');
  return { ...env, root, mount: env.window.PastportTradeCompletion.mount };
}

test('실패 후 같은 거래를 정상 조회하면 이전 오류 문구가 사라진다', async () => {
  let fail = true;
  const s = screen(() => fail ? { response: { ok: false, status: 503, async json() { return { message: '이전 조회 실패' }; } } } : context());
  await s.mount(options());
  assert.match(s.root.textContent, /이전 조회 실패/);
  fail = false; await s.mount(options());
  assert.doesNotMatch(s.root.textContent, /이전 조회 실패/);
  assert.ok(s.root.querySelector('input'));
});

test('제품 버전 변경 안내와 확인 입력 초기화는 오류 정리 후에도 유지한다', async () => {
  let version = 7;
  const s = screen(() => context(version));
  await s.mount(options());
  const checkbox = s.root.querySelector('input'); checkbox.checked = true; await checkbox.emit('change');
  version = 8; await s.mount(options());
  assert.match(s.root.textContent, /제품 정보가 변경/);
  assert.equal(s.root.querySelector('input').checked, false);
});

for (const outcome of ['success', 'error']) {
  test(`완료 전에 시작한 조회의 늦은 ${outcome} 응답은 완료 화면을 덮지 않는다`, async () => {
    const pending = deferred();
    const s = screen(() => pending.promise);
    const loading = s.mount(options());
    await s.mount(options('COMPLETED'));
    if (outcome === 'success') pending.resolve(context());
    else pending.reject(new Error('늦은 조회 오류'));
    await loading;
    assert.match(s.root.textContent, /거래가 완료되어/);
    assert.doesNotMatch(s.root.textContent, /늦은 조회 오류/);
    assert.equal(s.root.querySelector('input'), null);
  });
}

test('변경 이력 조회가 늦어도 최종 완료 화면은 유지한다', async () => {
  const pending = deferred();
  const s = screen(path => path.includes('/change-reviews') ? pending.promise : { ...context(), unreviewedChangeCount: 1 });
  const loading = s.mount(options()); await flush();
  await s.mount(options('COMPLETED'));
  pending.resolve({ items: [], page: { number: 0, totalPages: 1 } }); await loading;
  assert.match(s.root.textContent, /거래가 완료되어/);
  assert.equal(s.root.querySelector('input'), null);
});

test('완료 후 과거 예약 상태를 다시 전달해도 완료 화면을 유지한다', async () => {
  const s = screen(() => context());
  await s.mount(options('COMPLETED')); await s.mount(options('RESERVED'));
  assert.match(s.root.textContent, /거래가 완료되어/);
  assert.equal(s.root.querySelector('input'), null);
  assert.equal(s.calls.length, 0);
});
