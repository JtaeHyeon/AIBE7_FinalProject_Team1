// 게시 상세의 AI 상태를 주기적으로 조회한다. 생성 요청과 완료 결과의 표시는 분리해서 처리한다.
(() => {
  'use strict';
  let root=null, listingId=null, sequence=0, request=null, timer=null, state=null, issuing=false, owner=false, sample=false, polls=0, notice='';
  const make=(tag,cls,text)=>{const node=document.createElement(tag);node.className=cls;if(text!=null)node.textContent=text;return node;};
  const current=()=>listingId && document.querySelector('#detail.active') && String(new URL(location.href).searchParams.get('listingId'))===listingId;
  const path=()=>'/api/v1/listings/'+listingId+'/summary';
  const messages={NOT_REQUESTED:'아직 AI 요약이 생성되지 않았어요.',QUEUED:'요약 생성 순서를 기다리고 있어요.',PROCESSING:'등록된 제품 정보와 전체 핵심 이력으로 요약을 만들고 있어요.',READY:'등록된 기록에 근거한 AI 요약이에요. 전체 이력도 함께 확인해 주세요.',FAILED:'AI 요약을 만들지 못했어요. 기본 제품 정보와 이력은 계속 확인할 수 있어요.'};
  const reasons={AI_002:'응답 시간이 초과됐어요.',AI_003:'결과가 검증을 통과하지 못했어요.',AI_004:'전체 핵심 이력 연결을 확인하지 못했어요.',AI_005:'제품 정보나 판매 조건이 변경되어 이전 요약을 표시하지 않아요.',AI_006:'전체 이력이 AI 입력 한도를 초과했어요.',AI_007:'AI 요청이 많아 잠시 기다려야 해요.'};

  // 다른 페이지로 이동하면 조회·타이머를 끊고 이전 판매글의 늦은 응답을 무시한다.
  function reset() {
    sequence++;request?.abort();request=null;clearTimeout(timer);timer=null;
    root=null;listingId=null;state=null;issuing=false;polls=0;notice='';
  }
  function render(note=notice) {
    if(!root||!state)return;
    notice=note;
    root.replaceChildren(make('h3','','AI 카드 요약 관리'));
    const previous=Boolean(state.outdated && state.text);
    const updating=['QUEUED','PROCESSING'].includes(state.status);
    const explanation=previous?(updating?'등록 정보가 변경되어 요약을 최신화하고 있어요. 위 내용은 이전 판매 조건을 기준으로 한 요약이에요.':'새 요약을 만들지 못했어요. 위 내용은 이전 판매 조건을 기준으로 한 요약이며, 현재 정보와 다를 수 있어요.'):messages[state.status];
    const message=make('p','sub',note||explanation||messages.FAILED);
    message.setAttribute('role','status');message.setAttribute('aria-live','polite');
    // 이전 요약과 최신화 안내를 함께 표시해 오래된 문장을 최신 결과로 오해하지 않게 한다.
    if(previous) {
      root.append(make('p','sub','이전 판매 조건 기준 요약'));
      root.append(make('p','listing-ai-summary-text',state.text));
      root.append(message);
    } else {
      root.append(message);
      if(state.status==='READY' && state.text)root.append(make('p','listing-ai-summary-text',state.text));
    }
    if(state.status==='FAILED' && reasons[state.errorCode])root.append(make('p','sub',reasons[state.errorCode]));
    if(sample)root.append(make('p','sl-publication-sample','로컬 샘플 이력으로 검증하는 화면이에요.'));
    if(state.retryAfterSeconds>0)root.append(make('p','sub',state.retryAfterSeconds+'초 후 다시 요청할 수 있어요.'));
    const actions=make('div','listing-ai-summary-actions');
    const refresh=make('button','sc-btn','요약 상태 새로고침');refresh.type='button';refresh.disabled=issuing;
    refresh.addEventListener('click',()=>{polls=0;fetchState();});actions.append(refresh);
    if(owner && ['NOT_REQUESTED','FAILED'].includes(state.status)) {
      const retry=make('button','sc-btn',issuing?'요청 중…':state.status==='NOT_REQUESTED'?'요약 생성':'요약 다시 생성');
      retry.type='button';retry.disabled=issuing||!state.canRequest;
      retry.addEventListener('click',()=>fetchState(true));actions.append(retry);
    }
    root.append(actions);
  }
  // 생성 중에는 자주 조회하되 횟수를 제한한다. 완료 후에는 종료 여부를 확인하기 위해 간격을 늘린다.
  function schedule() {
    clearTimeout(timer);timer=null;
    if(!current()||document.hidden||!state)return;
    let delay=0;
    if(['QUEUED','PROCESSING'].includes(state.status)) {if(polls<60)delay=polls<30?2000:10000;else render('생성이 오래 걸리고 있어요. 상태 새로고침으로 다시 확인해 주세요.');}
    else if(['READY','NOT_REQUESTED'].includes(state.status))delay=15000;
    else if(state.status==='FAILED')delay=state.retryAfterSeconds>0?Math.min(60000,state.retryAfterSeconds*1000+100):15000;
    if(delay)timer=setTimeout(()=>fetchState(),delay);
  }
  function closeDetail(status) {
    window.PastportListingManagement?.reset();
    document.querySelector('#detail [data-listing-content]')?.replaceChildren();
    const message=document.querySelector('#detail [data-listing-status]');
    if(message)message.textContent=status===401?'로그인 후 판매글 상세를 확인해 주세요.':'이 판매글은 현재 조회할 수 없어요.';
    for(const selector of ['[data-listing-more]','[data-listing-manage]']) {const node=document.querySelector('#detail '+selector);if(node)node.hidden=true;}
    const login=document.querySelector('#detail [data-listing-login]');if(login)login.hidden=status!==401;
    reset();
  }
  // 기본 GET은 저장된 상태만 조회한다. 판매자가 생성·재시도 버튼을 누른 경우에만 POST로 접수한다.
  async function fetchState(post=false) {
    if(!current()||document.hidden||(issuing&&!post))return;
    clearTimeout(timer);request?.abort();
    const controller=new AbortController();request=controller;const token=sequence;
    issuing=post;if(post)render('요약 생성을 요청하고 있어요.');
    try {
      const headers=new Headers();
      if(post) {
        const cookie=document.cookie.split('; ').find(value=>value.startsWith('XSRF-TOKEN='));
        if(cookie)headers.set('X-XSRF-TOKEN',decodeURIComponent(cookie.substring('XSRF-TOKEN='.length)));
      }
      const fetcher=window.PastportApi?.fetch||window.fetch.bind(window);
      const response=await fetcher(path(),{method:post?'POST':'GET',headers,credentials:'same-origin',cache:'no-store',signal:controller.signal});
      const payload=await response.json().catch(()=>null);
      if(controller.signal.aborted||sequence!==token||request!==controller||!current())return;
      if(response.status===401||response.status===404){closeDetail(response.status);return;}
      if(!response.ok) {
        if(response.status===429) {
          const wait=Math.min(3600,Math.max(1,Number(response.headers.get('Retry-After'))||60));
          state={...state,status:'FAILED',text:null,errorCode:'AI_007',canRequest:false,retryAfterSeconds:wait};notice='';render();schedule();return;
        }
        throw new Error('summary state unavailable');
      }
      if(!payload?.data||!Object.hasOwn(messages,payload.data.status))throw new Error('invalid summary state');
      state=payload.data;notice='';polls++;render();schedule();
    } catch(error) {
      if(!controller.signal.aborted&&sequence===token&&current()) {
        state={status:'FAILED',text:null,canRequest:false,retryAfterSeconds:0};
        render('요약 상태를 확인하지 못했어요. 제품 정보·이력은 계속 볼 수 있고 상태 새로고침으로 다시 확인할 수 있어요.');
      }
    } finally {
      if(sequence===token&&request===controller) {issuing=false;render();}
    }
  }
  function bind(data) {
    reset();root=document.querySelector('#detail [data-listing-summary]');
    if(!data.listingId)return;
    listingId=String(data.listingId);owner=Boolean(data.canManage);sample=Boolean(data.history?.sample);
    state=data.summary||{status:'NOT_REQUESTED',canRequest:false,retryAfterSeconds:0};render();schedule();
  }
  document.addEventListener('pastport:page-shown',event=>{if(event.detail.id!=='detail')reset();});
  document.addEventListener('visibilitychange',()=>{
    clearTimeout(timer);
    if(document.hidden) {request?.abort();if(state?.text){state={...state,text:null};render('다시 화면을 열면 최신 요약 상태를 확인해요.');}}
    else if(current())fetchState();
  });
  window.addEventListener('pagehide',()=>{root?.replaceChildren();reset();});
  window.PastportListingSummary=Object.freeze({bind,reset});
})();
