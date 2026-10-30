// 전체메뉴(메가 메뉴): 상단 네비게이션의 그룹을 읽어 한 번에 펼쳐 보여준다.
(() => {
  const toggle = document.querySelector('#nav-all');
  const mega = document.querySelector('#mega');
  const grid = document.querySelector('#mega-grid');
  if (!toggle || !mega || !grid) return;

  const groups = [...document.querySelectorAll('.landing-nav .nav-item')].filter((item) => item.querySelector('.nav-drop'));
  grid.append(
    ...groups.map((item) => {
      const column = document.createElement('div');
      column.className = 'mega-col';
      const title = item.querySelector(':scope > button').cloneNode(true);
      title.className = 'mega-title';
      column.append(title, ...[...item.querySelectorAll('.nav-drop button')].map((button) => button.cloneNode(true)));
      return column;
    })
  );

  const setOpen = (open) => {
    if (open) closeDropdown();
    mega.hidden = !open;
    toggle.setAttribute('aria-expanded', String(open));
    toggle.classList.toggle('open', open);
  };

  let activeGroup = null;
  let openedByHover = false;
  const closeDropdown = () => {
    if (!activeGroup) return;
    activeGroup.removeAttribute('data-submenu-open');
    activeGroup.querySelector(':scope > button').setAttribute('aria-expanded', 'false');
    activeGroup = null;
    openedByHover = false;
  };

  // 가로 스크롤하는 모바일 메뉴 밖으로 펼쳐, 하위 메뉴가 잘리지 않도록 화면에 고정한다.
  const openDropdown = (item, hover = false) => {
    closeDropdown();
    setOpen(false);
    const trigger = item.querySelector(':scope > button');
    const dropdown = item.querySelector('.nav-drop');
    const anchor = trigger.getBoundingClientRect();
    const viewportWidth = document.documentElement.clientWidth;
    const viewportHeight = document.documentElement.clientHeight;
    const top = Math.min(anchor.bottom, viewportHeight - 8);
    dropdown.style.top = `${top}px`;
    dropdown.style.maxHeight = `${Math.max(0, viewportHeight - top - 8)}px`;
    item.setAttribute('data-submenu-open', '');
    dropdown.style.left = `${Math.max(8, Math.min(anchor.left, viewportWidth - dropdown.getBoundingClientRect().width - 8))}px`;
    trigger.setAttribute('aria-expanded', 'true');
    activeGroup = item;
    openedByHover = hover;
  };

  groups.forEach((item, index) => {
    const trigger = item.querySelector(':scope > button');
    const dropdown = item.querySelector('.nav-drop');
    dropdown.id ||= `nav-submenu-${index}`;
    trigger.type = 'button';
    trigger.setAttribute('aria-expanded', 'false');
    trigger.setAttribute('aria-controls', dropdown.id);
    trigger.addEventListener('click', (event) => {
      // 상위 항목은 메뉴를 열고, 실제 페이지 이동은 하위 항목에서 처리한다.
      event.preventDefault();
      event.stopPropagation();
      if (activeGroup === item && !openedByHover) closeDropdown();
      else openDropdown(item);
    });
    item.addEventListener('pointerenter', (event) => {
      if (event.pointerType === 'mouse' && activeGroup !== item) openDropdown(item, true);
    });
    item.addEventListener('pointerleave', () => {
      if (activeGroup === item && !item.contains(document.activeElement)) closeDropdown();
    });
    item.addEventListener('focusout', (event) => {
      if (activeGroup === item && !item.contains(event.relatedTarget)) closeDropdown();
    });
    trigger.addEventListener('keydown', (event) => {
      if (event.key !== 'ArrowDown') return;
      event.preventDefault();
      openDropdown(item);
      dropdown.querySelector('button')?.focus();
    });
    dropdown.addEventListener('click', (event) => {
      if (event.target.closest('[data-page]')) closeDropdown();
    });
  });

  document.querySelector('.landing-nav').addEventListener('scroll', closeDropdown);
  window.addEventListener('resize', closeDropdown);
  window.addEventListener('scroll', closeDropdown);
  document.addEventListener('pastport:page-shown', closeDropdown);

  toggle.addEventListener('click', () => setOpen(mega.hidden));
  mega.addEventListener('click', (event) => {
    if (event.target.closest('[data-page]')) setOpen(false);
  });
  document.addEventListener('click', (event) => {
    if (activeGroup && !activeGroup.contains(event.target)) closeDropdown();
    if (!mega.hidden && !event.target.closest('#mega, #nav-all')) setOpen(false);
  });
  document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape' && activeGroup) {
      const trigger = activeGroup.querySelector(':scope > button');
      closeDropdown();
      trigger.focus();
    }
    if (event.key === 'Escape' && !mega.hidden) {
      setOpen(false);
      toggle.focus();
    }
  });
})();
