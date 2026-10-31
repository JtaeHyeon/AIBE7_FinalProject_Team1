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
  for (const id of ['filters', 'filter-panel', 'refresh', 'trades-tab', 'audits-tab', 'actor-label', 'date-label', 'status-field', 'action-field', 'scope-note', 'result-title', 'message', 'count', 'range', 'previous', 'next', 'detail', 'detail-title', 'detail-body', 'rows', 'columns', 'close-detail', 'reset-filters', 'updated', 'results', 'page-title', 'page-description', 'breadcrumb', 'records-table', 'related-scope', 'related-product', 'clear-related']) env.add('div', { id });
  env.document.body = env.document.root;
  const detail = env.document.getElementById('detail');
  detail.showModal = () => { detail.open = true; };
  detail.close = () => { detail.open = false; };
  env.document.getElementById('filters').reset = () => {};
  env.window.FormData = class { constructor(form) { this.entries = form.formEntries || []; } *[Symbol.iterator]() { yield* this.entries; } };
  env.document.getElementById('related-scope').hidden = true;
  env.run('admin.js');
  env.$ = id => env.document.getElementById(id);
  return env;
}
const trade = (id = 41) => ({ id, productId: 101, listingId: 501, sellerId: 1, buyerId: 2, product: { title: `검증 노트북 ${id}`, manufacturer: 'Apple', modelName: 'MacBook Pro 14' }, seller: { nickname: '판매회원', email: 'seller@fixture.invalid' }, buyer: { nickname: '구매회원', email: 'buyer@fixture.invalid' }, requestStatus: 'ACCEPTED', transactionStatus: 'RESERVED', reservedAt: '2026-10-30T09:00:00Z' });
const audit = (id = 104) => ({ id, productId: 101, product: { title: `검증 제품 ${id}`, manufacturer: 'Apple', modelName: 'MacBook Pro 14' }, actor: { nickname: '처리회원', email: 'actor@fixture.invalid' }, targetId: 77, actorId: 1, action: 'HISTORY_CORRECTED', result: 'SUCCESS', createdAt: '2026-10-30T09:00:00Z' });
const page = (items, total = items.length) => ({ items, total, page: 0, size: 20 });
const event = (overrides = {}) => ({ key: 'AUDIT:1:PURCHASE_REQUEST_ACCEPTED', action: 'PURCHASE_REQUEST_ACCEPTED', source: 'AUDIT', actorType: 'MEMBER', actor: { nickname: '판매회원', email: 'seller@fixture.invalid' }, occurredAt: '2026-10-28T09:00:00Z', ...overrides });
const timeline = (items = [event()], total = items.length, number = 0, available = true) => ({ tradeId: 41, events: { ...page(items, total), page: number }, reviewHistoryAvailable: available });
const progress = (overrides = {}) => ({ tradeId: 41, state: 'WAITING_BOTH_CONFIRMATIONS', sellerConfirmation: 'NOT_CONFIRMED', buyerConfirmation: 'NOT_CONFIRMED', unreviewedChangeCount: 0, factsVersion: 2, sellerConfirmedVersion: null, buyerConfirmedVersion: null, ...overrides });

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

test('타임라인은 저장된 처리 주체·사유를 안전하게 표시하고 자체 페이지를 이동한다', async () => {
  const env = setup(path => path.includes('/timeline') ? timeline([event(path.includes('page=1') ? { actorType: 'UNRECORDED', actor: null, action: 'OWNERSHIP_TRANSFERRED' } : { reason: '<img src=x onerror=alert(1)>' })], 21, path.includes('page=1') ? 1 : 0, false) : path.endsWith('/trades/41') ? trade() : page([trade()]));
  await flush(); await env.$('rows').querySelector('button').emit('click'); await flush();
  const block = env.$('detail-body').querySelector('.timeline');
  assert.match(block.textContent, /구매 요청 수락/); assert.match(block.textContent, /onerror=alert/);
  assert.match(block.textContent, /seller@fixture.invalid/); assert.match(block.textContent, /활성화되지 않아/);
  assert.match(block.textContent, /KST/); assert.equal(block.querySelector('img'), null);
  await block.querySelector('.timeline-paging').children[2].emit('click'); await flush();
  assert.match(env.calls.at(-1).path, /timeline\?page=1&size=20/);
  assert.match(block.textContent, /소유권 이전 완료/); assert.match(block.textContent, /처리 주체 미기록/);
  assert.doesNotMatch(block.textContent, /onerror=alert/);
  assert.equal(block.querySelector('.timeline-paging').children[2].disabled, true);
});

test('타임라인 조회 실패는 거래 상세를 지우지 않으며 같은 패널에서 재시도한다', async () => {
  let fail = true;
  const env = setup(path => path.includes('/timeline') ? fail ? { response: { ok: false, status: 503, json: async () => ({ message: '타임라인 일시 오류' }) } } : timeline([]) : path.endsWith('/trades/41') ? trade() : page([trade()]));
  await flush(); await env.$('rows').querySelector('button').emit('click'); await flush();
  assert.match(env.$('detail-body').textContent, /구매회원/); assert.match(env.$('detail-body').textContent, /일시 오류/);
  const retry = env.$('detail-body').querySelector('.timeline').querySelector('button');
  assert.equal(retry.hidden, false); fail = false; await retry.emit('click'); await flush();
  assert.match(env.$('detail-body').textContent, /시각이 저장된 처리 내역이 없어요/); assert.equal(retry.hidden, true);
});

test('상세를 닫은 뒤 도착한 타임라인은 폐기하고 권한 회수 시에는 모든 업무 표시를 비운다', async () => {
  const slow = deferred(); let revoke = false;
  const env = setup(path => path.includes('/timeline') ? revoke ? { response: { ok: false, status: 403 } } : slow.promise : path.endsWith('/trades/41') ? trade() : page([trade()]));
  await flush(); await env.$('rows').querySelector('button').emit('click'); await flush();
  await env.$('close-detail').emit('click'); slow.resolve(timeline([event({ reason: 'LATE_RESULT' })])); await flush();
  assert.equal(env.$('detail-body').textContent, ''); assert.equal(env.$('detail').hidden, true);
  revoke = true; await env.$('rows').querySelector('button').emit('click'); await flush();
  assert.equal(env.$('detail-body').textContent, ''); assert.equal(env.$('rows').textContent, '');
  assert.match(env.$('message').textContent, /관리자 권한/);
});

test('제품 기록 바로가기는 정확한 제품 ID 범위를 검색·페이지 이동에도 유지하고 전체 보기로 해제한다', async () => {
  const env = setup(path => path.includes('/timeline') ? timeline() : path.endsWith('/trades/41') ? trade() : path.includes('/audit-logs') ? page([audit()], 21) : page([trade()]));
  await flush(); await env.$('rows').querySelector('button').emit('click'); await flush();
  await env.$('detail-body').querySelector('.related-link').querySelector('button').emit('click'); await flush();
  assert.equal(env.$('detail').hidden, true); assert.equal(env.$('related-scope').hidden, false);
  assert.match(env.$('related-product').textContent, /검증 노트북 41/);
  assert.equal(new URL(env.calls.at(-1).path, 'http://test.invalid').searchParams.get('productId'), '101');
  env.$('filters').formEntries = [['memberQuery', 'actor@fixture.invalid']];
  await env.$('filters').emit('submit'); await flush();
  let params = new URL(env.calls.at(-1).path, 'http://test.invalid').searchParams;
  assert.equal(params.get('productId'), '101'); assert.equal(params.get('memberQuery'), 'actor@fixture.invalid');
  await env.$('next').emit('click'); await flush();
  params = new URL(env.calls.at(-1).path, 'http://test.invalid').searchParams;
  assert.equal(params.get('productId'), '101'); assert.equal(params.get('page'), '1');
  await env.$('clear-related').emit('click'); await flush();
  assert.equal(env.$('related-scope').hidden, true); assert.equal(env.$('related-product').textContent, '');
  assert.equal(new URL(env.calls.at(-1).path, 'http://test.invalid').searchParams.has('productId'), false);
});

test('현재 확인 상태는 구매자 변경 확인 대기와 양측 완료 확인 상태를 함께 표시한다', async () => {
  const env = setup(path => path.includes('/progress') ? progress({ state: 'WAITING_BUYER_CHANGE_REVIEW', sellerConfirmation: 'CURRENT', buyerConfirmation: 'RECONFIRM_REQUIRED', unreviewedChangeCount: 2 }) : path.includes('/timeline') ? timeline() : path.endsWith('/trades/41') ? trade() : page([trade()]));
  await flush(); await env.$('rows').querySelector('button').emit('click'); await flush();
  const card = env.$('detail-body').querySelector('.trade-progress');
  assert.match(card.textContent, /구매자의 변경 이력 확인 대기/);
  assert.match(card.textContent, /판매자 완료 확인최신 내용 확인함/);
  assert.match(card.textContent, /구매자 완료 확인내용 변경 · 재확인 필요/);
  assert.match(card.textContent, /변경 이력 2건/);
  assert.equal(card.getAttribute('aria-busy'), 'false');
  assert.equal(card.querySelector('button').hidden, true);
  assert.ok(env.calls.every(call => !call.options.method));
});

test('완료된 거래와 미제공 확인 기능은 일반적인 확인 대기로 표시하지 않는다', async () => {
  for (const state of ['COMPLETED', 'CONFIRMATION_UNAVAILABLE']) {
    const env = setup(path => path.includes('/progress') ? progress({ state, sellerConfirmation: state === 'COMPLETED' ? 'NOT_APPLICABLE' : 'UNAVAILABLE', buyerConfirmation: state === 'COMPLETED' ? 'NOT_APPLICABLE' : 'UNAVAILABLE', unreviewedChangeCount: null }) : path.includes('/timeline') ? timeline() : path.endsWith('/trades/41') ? trade() : page([trade()]));
    await flush(); await env.$('rows').querySelector('button').emit('click'); await flush();
    const card = env.$('detail-body').querySelector('.trade-progress');
    assert.match(card.textContent, state === 'COMPLETED' ? /거래 완료됨/ : /확인 상태 제공 준비 중/);
    assert.equal(card.querySelector('.progress-checks'), null);
    assert.doesNotMatch(card.textContent, /변경 이력 0건|판매자의 완료 확인 대기|판매자·구매자의 완료 확인 대기/);
  }
});

test('확인 상태 조회만 실패하면 기본 상세·타임라인을 유지하고 패널 안에서 다시 시도한다', async () => {
  let fail = true;
  const env = setup(path => path.includes('/progress') ? fail ? { response: { ok: false, status: 503, json: async () => ({ message: '상태 조회 일시 오류' }) } } : progress({ state: 'WAITING_BUYER_CONFIRMATION', sellerConfirmation: 'CURRENT' }) : path.includes('/timeline') ? timeline() : path.endsWith('/trades/41') ? trade() : page([trade()]));
  await flush(); await env.$('rows').querySelector('button').emit('click'); await flush();
  const card = env.$('detail-body').querySelector('.trade-progress');
  assert.match(card.textContent, /일시 오류/); assert.match(env.$('detail-body').textContent, /구매회원/);
  assert.match(env.$('detail-body').querySelector('.timeline').textContent, /구매 요청 수락/);
  fail = false; await card.querySelector('button').emit('click'); await flush();
  assert.match(card.textContent, /구매자의 완료 확인 대기/); assert.doesNotMatch(card.textContent, /일시 오류/);
});

test('다른 거래를 연 뒤 도착한 이전 확인 상태는 새 거래의 안내를 바꾸지 않는다', async () => {
  const slow = deferred();
  const env = setup(path => path.includes('/trades/41/progress') ? slow.promise : path.includes('/progress') ? progress({ tradeId: 42, state: 'WAITING_SELLER_CONFIRMATION', buyerConfirmation: 'CURRENT' }) : path.includes('/timeline') ? timeline() : path.endsWith('/trades/41') ? trade() : path.endsWith('/trades/42') ? trade(42) : page([trade(), trade(42)]));
  await flush(); const buttons = env.$('rows').querySelectorAll('button');
  await buttons[0].emit('click'); await flush(); await buttons[1].emit('click'); await flush();
  slow.resolve(progress({ state: 'WAITING_BUYER_CHANGE_REVIEW', unreviewedChangeCount: 999 })); await flush();
  assert.match(env.$('detail-body').textContent, /검증 노트북 42/);
  assert.match(env.$('detail-body').querySelector('.trade-progress').textContent, /판매자의 완료 확인 대기/);
  assert.doesNotMatch(env.$('detail-body').textContent, /변경 이력 999건/);
});

test('확인 상태 요청에서 권한 회수가 확인되면 기본 상세·타임라인·목록을 함께 지운다', async () => {
  const env = setup(path => path.includes('/progress') ? { response: { ok: false, status: 403 } } : path.includes('/timeline') ? timeline() : path.endsWith('/trades/41') ? trade() : page([trade()]));
  await flush(); await env.$('rows').querySelector('button').emit('click'); await flush();
  assert.equal(env.$('detail-body').textContent, ''); assert.equal(env.$('rows').textContent, '');
  assert.equal(env.$('detail').hidden, true); assert.match(env.$('message').textContent, /관리자 권한/);
});
