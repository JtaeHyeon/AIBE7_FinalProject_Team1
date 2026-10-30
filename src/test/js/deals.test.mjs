import test from 'node:test';
import assert from 'node:assert/strict';
import { environment, deferred, flush } from './dom.mjs';

const room = id => ({ roomId: id, listingId: id + 10, listingStatus: 'ACTIVE', listingTitle: '상품 ' + id, counterpartyNickname: '상대방' });

/** 실제 deals 모듈에 목록·방 전환 이벤트를 보내는 시험 화면을 만든다. */
async function screen(override = () => undefined, active = 'deals') {
  const state = { user: 1, rooms: [room(1), room(2)], cleared: 0 };
  const env = environment(async (path, options, calls) => {
    const custom = override(path, options, calls, state);
    if (custom !== undefined) return custom;
    if (path === '/api/auth/me') return { id: state.user };
    if (path.startsWith('/api/v1/chat-rooms?')) return { items: state.rooms };
    if (path.includes('/messages?')) return { items: [] };
    if (path.includes('/purchase-requests?')) return { items: [], roomContext: { listingStatus: 'ACTIVE' } };
    if (path.startsWith('/api/v1/listings/')) return { canManage: false, content: { price: 30000 } };
    if (path.endsWith('/chat-rooms')) return { roomId: 1 };
    return {};
  });
  const page = env.add('section', { id: 'deals', className: 'page' + (active === 'deals' ? ' active' : '') });
  env.add('section', { id: 'detail', className: 'page' + (active === 'detail' ? ' active' : '') });
  const grid = env.add('div', { className: 'dl' }, page);
  const nodes = {};
  for (const name of ['rooms', 'messages', 'completion', 'room-title', 'listing-status', 'info-title', 'info-price', 'info-trade-status', 'chat-section', 'info', 'request-buy', 'leave', 'back', 'input', 'send']) {
    nodes[name] = env.add(['leave', 'back', 'send', 'request-buy'].includes(name) ? 'button' : 'div', { ['data-deals-' + name]: '' }, grid);
  }
  env.window.PastportTradeCompletion = { clear() { state.cleared++; }, async mount() {} };
  env.run('deals.js');
  await env.document.emit('pastport:page-loaded', { id: 'deals' });
  await flush();
  return { ...env, state, nodes, grid, async select(index) { await nodes.rooms.children[index].emit('click'); } };
}

test('계정 A의 늦은 목록 성공은 계정 B의 목록을 덮지 않는다', async () => {
  const pending = deferred(); let first = true;
  const s = await screen(path => {
    if (path.startsWith('/api/v1/chat-rooms?') && first) { first = false; return pending.promise; }
  });
  s.state.user = 2; s.state.rooms = [room(7)];
  await s.document.emit('pastport:auth-changed', { signedIn: true, userId: 2 }); await flush();
  pending.resolve({ items: [room(99)] }); await flush();
  assert.match(s.nodes.rooms.children[0].innerHTML, /상품 7/);
  assert.doesNotMatch(s.nodes.rooms.children[0].innerHTML, /상품 99/);
  assert.equal(s.calls.find(x => x.path.startsWith('/api/v1/chat-rooms?')).options.signal.aborted, true);
});

test('계정 A의 늦은 목록 오류는 계정 B의 화면을 지우지 않는다', async () => {
  const pending = deferred(); let first = true;
  const s = await screen(path => { if (path.startsWith('/api/v1/chat-rooms?') && first) { first = false; return pending.promise; } });
  s.state.user = 2; s.state.rooms = [room(7)];
  await s.document.emit('pastport:auth-changed', { signedIn: true }); await flush();
  pending.reject(new Error('old account failure')); await flush();
  assert.match(s.nodes.rooms.children[0].innerHTML, /상품 7/);
  assert.equal(s.nodes.rooms.innerHTML, '');
});

test('로그아웃 뒤 도착한 사용자 조회도 목록 요청을 다시 시작하지 않는다', async () => {
  const pending = deferred(); let first = true;
  const s = await screen(path => { if (path === '/api/auth/me' && first) { first = false; return pending.promise; } });
  await s.document.emit('pastport:auth-changed', { signedIn: false });
  pending.resolve({ id: 1 }); await flush();
  assert.equal(s.calls.filter(x => x.path.startsWith('/api/v1/chat-rooms?')).length, 0);
  assert.equal(s.nodes.rooms.children.length, 0);
});

test('이전 방의 정지된 폴링과 finally가 새 방의 갱신·busy 상태를 바꾸지 않는다', async () => {
  const a = deferred(), b = deferred(); let holdA = false, holdB = false;
  const s = await screen(path => {
    if (path.includes('/chat-rooms/1/messages?') && holdA) return a.promise;
    if (path.includes('/chat-rooms/2/messages?') && holdB) return b.promise;
  });
  await s.select(0); holdA = true;
  const oldTick = [...s.intervals.values()][0]();
  await s.select(1);
  const latest = [...s.intervals.values()][0]; holdB = true;
  const newTick = latest();
  a.resolve({ items: [] }); await oldTick;
  const count = s.calls.filter(x => x.path.includes('/chat-rooms/2/messages?')).length;
  await latest();
  assert.equal(s.calls.filter(x => x.path.includes('/chat-rooms/2/messages?')).length, count);
  b.resolve({ items: [] }); await newTick; holdB = false;
  await latest();
  assert.equal(s.calls.filter(x => x.path.includes('/chat-rooms/2/messages?')).length, count + 1);
});

test('방을 나가면 메시지·완료 확인·갱신을 정리하고 모바일 목록을 복구한다', async () => {
  const s = await screen(); await s.select(0);
  assert.equal(s.grid.classList.contains('dl-has-room'), true);
  await s.nodes.leave.emit('click');
  assert.equal(s.grid.classList.contains('dl-has-room'), false);
  assert.equal(s.nodes['chat-section'].style.display, 'none');
  assert.equal(s.intervals.size, 0);
  assert.ok(s.state.cleared > 0);
});

test('문의 POST 중 다른 판매글로 이동하면 이전 대화로 이동하지 않는다', async () => {
  const pending = deferred();
  const s = await screen(path => path.endsWith('/chat-rooms') ? pending.promise : undefined, 'detail');
  const opening = s.window.PastportDeals.openRoomForListing(5);
  s.window.location.href = 'http://test.invalid/?listingId=6';
  pending.resolve({ roomId: 1 }); await opening;
  assert.deepEqual(s.navigations, []);
});

test('문의 POST 중 계정이 바뀌면 이전 계정의 후속 이동을 막는다', async () => {
  const pending = deferred();
  const s = await screen(path => path.endsWith('/chat-rooms') ? pending.promise : undefined, 'detail');
  const opening = s.window.PastportDeals.openRoomForListing(5);
  await s.document.emit('pastport:auth-changed', { signedIn: true, userId: 2 });
  pending.resolve({ roomId: 1 }); await opening;
  assert.deepEqual(s.navigations, []);
});

test('같은 계정·판매글의 문의는 정상적으로 대화를 열고 가격을 표시한다', async () => {
  const s = await screen(undefined, 'detail');
  await s.window.PastportDeals.openRoomForListing(5); await flush();
  assert.deepEqual(s.navigations, ['deals']);
  assert.equal(s.nodes['chat-section'].style.display, 'flex');
  assert.equal(s.nodes['info-price'].textContent, '30,000원');
});

test('문의 POST 후 목록을 기다리는 중 계정을 바꿔도 이전 방을 선택하지 않는다', async () => {
  const pending = deferred(); let hold = true;
  const s = await screen(path => path.startsWith('/api/v1/chat-rooms?') && hold ? pending.promise : undefined, 'detail');
  const opening = s.window.PastportDeals.openRoomForListing(5); await flush();
  hold = false; s.state.user = 2; s.state.rooms = [room(8)];
  await s.document.emit('pastport:auth-changed', { signedIn: true, userId: 2 }); await flush();
  pending.resolve({ items: [room(1)] }); await opening; await flush();
  assert.match(s.nodes.rooms.children[0].innerHTML, /상품 8/);
  assert.equal(s.grid.classList.contains('dl-has-room'), false);
});

test('이전 계정의 늦은 메시지 401 응답은 새 계정의 대화를 초기화하지 않는다', async () => {
  const pending = deferred(); let hold = false;
  const s = await screen(path => path.includes('/chat-rooms/1/messages?') && hold ? pending.promise : undefined);
  await s.select(0); hold = true;
  const tick = [...s.intervals.values()][0]();
  s.state.user = 2; s.state.rooms = [room(8)];
  await s.document.emit('pastport:auth-changed', { signedIn: true }); await flush(); await s.select(0);
  pending.resolve({ response: { ok: false, status: 401, async json() { return { message: 'old unauthorized' }; } } });
  await tick;
  assert.equal(s.grid.classList.contains('dl-has-room'), true);
  assert.match(s.nodes['room-title'].innerHTML, /상품 8/);
  assert.equal(s.intervals.size, 1);
});
