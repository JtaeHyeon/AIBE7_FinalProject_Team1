import test from 'node:test';
import assert from 'node:assert/strict';
import { environment, deferred, flush } from './dom.mjs';

/** 실제 화면 모듈을 DOM 대역에 연결한다. 화면 크기·레이아웃은 이 시험의 검증 범위가 아니다. */
function setup(handler) {
  const env = environment(handler);
  env.document.getElementById = id => env.document.querySelector('#' + id);
  const create = env.document.createElement.bind(env.document);
  env.document.createElement = tag => {
    const node = create(tag);
    node.closest = selector => { for (let n = node; n; n = n.parent) if (n.matches(selector)) return n; return null; };
    return node;
  };
  for (const id of ['filters', 'filter-panel', 'refresh', 'trades-tab', 'audits-tab', 'actor-label', 'date-label', 'status-field', 'action-field', 'scope-note', 'result-title', 'message', 'count', 'range', 'previous', 'next', 'detail', 'detail-title', 'detail-body', 'rows', 'columns', 'close-detail', 'reset-filters', 'updated', 'results', 'page-title', 'page-description', 'breadcrumb', 'records-table']) env.add('div', { id });
  env.document.body = env.document.root;
  const detail = env.document.getElementById('detail');
  detail.showModal = () => { detail.open = true; };
  detail.close = () => { detail.open = false; };
  env.document.getElementById('filters').reset = () => {};
  env.run('admin.js');
  env.$ = id => env.document.getElementById(id);
  return env;
}
const trade = (id = 41) => ({ id, productId: 101, listingId: 501, sellerId: 1, buyerId: 2, product: { title: `검증 노트북 ${id}`, manufacturer: 'Apple', modelName: 'MacBook Pro 14' }, seller: { nickname: '판매회원', email: 'seller@fixture.invalid' }, buyer: { nickname: '구매회원', email: 'buyer@fixture.invalid' }, requestStatus: 'ACCEPTED', transactionStatus: 'RESERVED', reservedAt: '2026-10-30T09:00:00Z' });
const audit = (id = 104) => ({ id, productId: 101, product: { title: `검증 제품 ${id}`, manufacturer: 'Apple', modelName: 'MacBook Pro 14' }, actor: { nickname: '처리회원', email: 'actor@fixture.invalid' }, targetId: 77, actorId: 1, action: 'HISTORY_CORRECTED', result: 'SUCCESS', createdAt: '2026-10-30T09:00:00Z' });
const page = (items, total = items.length) => ({ items, total, page: 0, size: 20 });

test('목록·페이지 이동은 조회 요청만 보내고 실제 전체 건수를 표시한다', async () => {
  const env = setup(path => page([trade(path.includes('page=1') ? 42 : 41)], 21));
  await flush();
  assert.match(env.$('count').textContent, /21건/);
  assert.match(env.$('rows').textContent, /검증 노트북 41/);
  assert.equal(env.$('rows').querySelector('.sequence').textContent, '1');
  assert.match(env.$('rows').textContent, /seller@fixture.invalid/);
  await env.$('next').emit('click'); await flush();
  assert.match(env.calls.at(-1).path, /page=1/);
  assert.match(env.$('rows').textContent, /검증 노트북 42/);
  assert.equal(env.$('rows').querySelector('.sequence').textContent, '21');
  assert.equal(env.$('next').disabled, true);
  assert.ok(env.calls.every(call => !call.options.method && call.options.cache === 'no-store'));
});

test('탭 전환 뒤 도착한 이전 거래 응답은 업무 기록 목록을 덮어쓰지 않는다', async () => {
  const slow = deferred();
  const env = setup(path => path.includes('/trades') ? slow.promise : page([audit()]));
  await env.$('audits-tab').emit('click'); await flush();
  slow.resolve(page([trade(999)])); await flush();
  assert.match(env.$('rows').textContent, /검증 제품 104/);
  assert.doesNotMatch(env.$('rows').textContent, /검증 노트북 999/);
  assert.equal(env.$('status-field').hidden, true);
});

test('상세의 사용자 입력은 HTML로 실행하지 않고 텍스트로 표시한다', async () => {
  const content = '<img src=x onerror=alert(1)>';
  const env = setup(path => path.includes('/audit-logs/104') ? { record: audit(), reason: content, before: { available: true, values: { title: '점검' } }, after: { available: true, values: { content, specsAfter: { ramGb: 32 } } } } : page([audit()]));
  await env.$('audits-tab').emit('click'); await flush();
  await env.$('rows').querySelector('button').emit('click'); await flush();
  assert.match(env.$('detail-body').textContent, /onerror=alert/);
  assert.match(env.$('detail-body').textContent, /32/);
  assert.equal(env.$('detail-body').querySelector('img'), null);
});

test('권한 회수 응답이면 목록과 상세를 모두 비우고 안내한다', async () => {
  const env = setup(path => path.includes('/trades/41') ? { response: { ok: false, status: 403 } } : page([trade()]));
  await flush(); await env.$('rows').querySelector('button').emit('click'); await flush();
  assert.equal(env.$('rows').textContent, '');
  assert.equal(env.$('detail-body').textContent, '');
  assert.equal(env.$('detail').hidden, true);
  assert.match(env.$('message').textContent, /관리자 권한/);
});

test('화면을 숨기면 기록을 지우고 계정이 바뀐 뒤 도착한 상세 응답도 버린다', async () => {
  const slow = deferred();
  const env = setup(path => path.includes('/trades/41') ? slow.promise : page([trade()]));
  await flush(); const click = env.$('rows').querySelector('button').emit('click');
  env.document.hidden = true; await env.document.emit('visibilitychange');
  slow.resolve(trade()); await click; await flush();
  assert.equal(env.$('rows').textContent, ''); assert.equal(env.$('detail-body').textContent, '');
  assert.equal(env.$('detail').hidden, true);
});

test('조회 실패와 실제 빈 목록을 구분하고 새로고침으로 재시도한다', async () => {
  let failed = true;
  const env = setup(() => failed ? { response: { ok: false, status: 503, json: async () => ({ message: '거래 조회 기능이 아직 활성화되지 않았습니다.' }) } } : page([]));
  await flush(); assert.match(env.$('message').textContent, /활성화/); assert.equal(env.$('count').textContent, '');
  failed = false; await env.$('refresh').emit('click'); await flush();
  assert.match(env.$('message').textContent, /조건에 맞는 기록이 없어요/);
  assert.equal(env.$('count').textContent, '총 0건');
});

test('Escape로 상세를 닫으면 늦은 응답을 버리고 원래 목록 버튼으로 포커스를 돌린다', async () => {
  const slow = deferred();
  const env = setup(path => path.includes('/trades/41') ? slow.promise : page([trade()]));
  await flush();
  const button = env.$('rows').querySelector('button');
  const loading = button.emit('click');
  assert.equal(env.$('detail').open, true);
  await env.$('detail').emit('cancel');
  slow.resolve(trade()); await loading; await flush();
  assert.equal(env.$('detail').open, false);
  assert.equal(env.$('detail').hidden, true);
  assert.equal(env.$('detail-body').textContent, '');
  assert.equal(env.document.activeElement, button);
});

test('변경 전후 비교는 달라진 항목을 한 행에 두고 동일한 항목은 반복하지 않는다', async () => {
  const env = setup(path => path.includes('/audit-logs/104') ? { record: audit(), reason: '검증 정정', before: { available: true, values: { title: '점검', content: '16GB' } }, after: { available: true, values: { title: '점검', content: '32GB' } } } : page([audit()]));
  await env.$('audits-tab').emit('click'); await flush();
  await env.$('rows').querySelector('button').emit('click'); await flush();
  const row = env.$('detail-body').querySelector('.comparison-row');
  assert.deepEqual(row.children.map(node => node.textContent), ['내용', '16GB', '32GB']);
  assert.doesNotMatch(env.$('detail-body').querySelector('.comparison').textContent, /점검/);
});

test('회원이 사라진 업무 기록은 시스템 처리와 구분하고 이름·이메일을 텍스트로 표시한다', async () => {
  const env = setup(() => page([{ ...audit(), actor: { nickname: '<img src=x onerror=alert(1)>', email: 'real@fixture.invalid' } }, { ...audit(105), actorId: null, actor: null }, { ...audit(106), actorId: 999, actor: { id: 999, nickname: null, email: null } }]));
  await env.$('audits-tab').emit('click'); await flush();
  assert.match(env.$('rows').textContent, /onerror=alert/);
  assert.match(env.$('rows').textContent, /real@fixture.invalid/);
  assert.match(env.$('rows').textContent, /자동 처리/);
  assert.match(env.$('rows').textContent, /탈퇴한 회원/);
  assert.equal(env.$('rows').querySelector('img'), null);
});
