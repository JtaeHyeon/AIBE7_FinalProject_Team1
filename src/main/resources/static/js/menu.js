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

  /** 전체 메뉴와 개별 하위 메뉴가 동시에 펼쳐지지 않도록 한다. */
  const setOpen = (open) => {
    if (open) closeDropdown();
    mega.hidden = !open;
    toggle.setAttribute('aria-expanded', String(open));
    toggle.classList.toggle('open', open);
  };

  let activeGroup = null;
  let openedByHover = false;
  /** 숨길 하위 메뉴 안에 키보드 포커스가 있으면 먼저 상위 버튼으로 돌려준다. */
  const closeDropdown = () => {
    if (!activeGroup) return;
    const group = activeGroup;
    activeGroup = null;
    openedByHover = false;
    const trigger = group.querySelector(':scope > button');
    if (group.querySelector('.nav-drop').contains(document.activeElement)) trigger.focus({ preventScroll: true });
    group.removeAttribute('data-submenu-open');
    trigger.setAttribute('aria-expanded', 'false');
  };

  /** 가로 스크롤 밖에 메뉴를 고정한다. 아래 공간이 부족하면 위로 열고, 낮은 화면에서도 스크롤 공간을 확보한다. */
  const openDropdown = (item, hover = false) => {
    closeDropdown();
    setOpen(false);
    const trigger = item.querySelector(':scope > button');
    const dropdown = item.querySelector('.nav-drop');
    const anchor = trigger.getBoundingClientRect();
    const viewportWidth = document.documentElement.clientWidth;
    const viewportHeight = document.documentElement.clientHeight;
    const edge = Math.min(8, viewportHeight / 4);
    const viewportSpace = Math.max(1, viewportHeight - edge * 2);
    dropdown.style.maxHeight = 'none';
    item.setAttribute('data-submenu-open', '');
    const naturalHeight = dropdown.scrollHeight + 2;
    const below = Math.max(0, viewportHeight - edge - anchor.bottom);
    const above = Math.max(0, Math.min(viewportHeight - edge, anchor.top) - edge);
    const minimum = Math.min(100, naturalHeight, viewportSpace);
    const opensAbove = below < minimum && above > below;
    let available = Math.min(viewportSpace, opensAbove ? above : below);
    let top = opensAbove ? anchor.top - Math.min(naturalHeight, available) : anchor.bottom;
    if (available < minimum) { available = viewportSpace; top = edge; }
    top = Math.max(edge, Math.min(top, viewportHeight - edge - Math.min(naturalHeight, available)));
    dropdown.style.top = `${top}px`;
    dropdown.style.maxHeight = `${available}px`;
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
