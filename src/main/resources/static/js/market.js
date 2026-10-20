// 화면 목업용 UI 상호작용(칩 선택 / 찜 / 등급 선택). 페이지 HTML이 동적으로 주입되므로 이벤트 위임 사용.
document.addEventListener('click', (event) => {
  const chip = event.target.closest('[data-chip]');
  if (chip) {
    chip.parentElement.querySelectorAll('[data-chip]').forEach((item) => item.classList.toggle('active', item === chip));
    return;
  }
  const wish = event.target.closest('[data-wish]');
  if (wish) {
    const on = wish.getAttribute('aria-pressed') !== 'true';
    wish.setAttribute('aria-pressed', String(on));
    wish.textContent = on ? '♥' : '♡';
    return;
  }
  const grade = event.target.closest('[data-grade]');
  if (grade) grade.parentElement.querySelectorAll('[data-grade]').forEach((item) => item.classList.toggle('on', item === grade));
});
