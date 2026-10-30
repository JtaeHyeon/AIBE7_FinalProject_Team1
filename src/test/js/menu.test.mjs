import test from 'node:test';
import assert from 'node:assert/strict';
import { environment } from './dom.mjs';

/** 펼침 메뉴의 실제 이벤트와 좌표 계산을 검사할 대역을 만든다. */
function screen(height = 700, anchor = { top: 80, bottom: 120, left: 20 }) {
  const env = environment();
  env.document.documentElement.clientHeight = height;
  const nav = env.add('nav', { className: 'landing-nav' });
  env.add('button', { id: 'nav-all' }, nav);
  const mega = env.add('div', { id: 'mega' }); mega.hidden = true;
  env.add('div', { id: 'mega-grid' }, mega);
  const groups = [0, 1].map(() => {
    const item = env.add('div', { className: 'nav-item' }, nav);
    const trigger = env.add('button', {}, item); trigger.rect = anchor;
    const dropdown = env.add('div', { className: 'nav-drop' }, item);
    const child = env.add('button', { 'data-page': 'deals' }, dropdown);
    return { item, trigger, dropdown, child };
  });
  env.run('menu.js');
  return { ...env, groups };
}

test('포커스가 든 메뉴를 다른 메뉴로 교체하기 전에 상위 버튼으로 복귀한다', async () => {
  const s = screen(); const [a, b] = s.groups;
  await a.item.emit('pointerenter', { pointerType: 'mouse' }); a.child.focus();
  await b.item.emit('pointerenter', { pointerType: 'mouse' });
  assert.equal(s.document.activeElement, a.trigger);
  assert.equal(a.item.getAttribute('data-submenu-open'), null);
  assert.equal(b.trigger.getAttribute('aria-expanded'), 'true');
});

test('화면 아래 공간이 부족하면 양수 높이로 위쪽에 연다', async () => {
  const s = screen(200, { top: 170, bottom: 195, left: 250 }); const a = s.groups[0];
  await a.trigger.emit('click');
  const top = parseFloat(a.dropdown.style.top), height = parseFloat(a.dropdown.style.maxHeight);
  assert.ok(height > 0 && top >= 0);
  assert.ok(top + Math.min(a.dropdown.scrollHeight + 2, height) <= 170);
  assert.ok(parseFloat(a.dropdown.style.left) + a.dropdown.rect.width <= 390);
});

test('상단 영역보다 화면이 낮아도 메뉴를 뷰포트 안에서 스크롤할 수 있다', async () => {
  const s = screen(70, { top: 0, bottom: 120, left: 20 }); const a = s.groups[0];
  await a.trigger.emit('click');
  const top = parseFloat(a.dropdown.style.top), height = parseFloat(a.dropdown.style.maxHeight);
  assert.ok(height > 0 && top >= 0 && top + height <= 70);
});

test('화살표로 연 메뉴는 Escape로 닫고 상위 버튼의 포커스를 유지한다', async () => {
  const s = screen(); const a = s.groups[0];
  await a.trigger.emit('keydown', { key: 'ArrowDown' });
  assert.equal(s.document.activeElement, a.child);
  await s.document.emit('keydown', null, { key: 'Escape' });
  assert.equal(s.document.activeElement, a.trigger);
  assert.equal(a.trigger.getAttribute('aria-expanded'), 'false');
});
