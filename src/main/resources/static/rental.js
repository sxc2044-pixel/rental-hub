'use strict';
const landlordNav=[['rental-dashboard','chart','工作台'],['rental-houses','home','我的房源'],['rental-applications','file','租赁申请'],['rental-contracts','book','租赁合同'],['rental-moveouts','logout','退租审批'],['rental-bills','file','账单管理'],['rental-repairs','settings','报修工单'],['rental-chat','message','在线聊天'],['rental-profile','user','个人中心']];
Object.assign(labels,{LANDLORD:'房东',DRAFT:'待双方签署',PAID:'已结清',UNPAID:'待结清',ASSIGNED:'已派工',TENANT:'租客',SHARED:'双方分担'});
const rentalChoice=(label,name,choices)=>`<label class="field"><span>${label}</span><select name="${name}" required>${choices.map(([v,t])=>`<option value="${esc(v)}">${esc(t)}</option>`).join('')}</select></label>`;
const rentalForm=(title,path,body,method='POST')=>modal(title,`<form id="rental-form" data-path="${esc(path)}" data-method="${method}">${body}<div class="modal-actions">${submit('确认保存')}</div></form>`);
const pickIds=()=>Array.from(document.querySelectorAll('[name=batch]:checked')).map(e=>Number(e.value));
const selectRow=id=>`<input type="checkbox" name="batch" value="${id}" aria-label="选择记录${id}">`;
const rButton=(t,a,id='',extra='')=>button(t,'r-'+a,id,extra);
async function rentalPage(page){
 if(page==='dashboard'){const d=await api('/rental/dashboard');$('#content').innerHTML=heading('房东工作台','房源、租约与待办，一目了然。',rButton('发布房源','new-house'))+`<div class="r-metrics">${[['我的房源',d.houses],['待审核房源',d.pending],['有效合同',d.contracts],['本月账单收入','¥'+money(d.income)],['待处理报修',d.repairs]].map(([t,n])=>`<section class="panel"><p>${t}</p><strong>${esc(n)}</strong></section>`).join('')}</div><section class="panel"><h2>接下来可以做什么</h2><p>发布房源后由管理员审核上架。收到租赁申请后，审批通过将自动生成待签合同，双方确认后开始管理账单与报修。</p><div class="actions"><a class="btn" href="#rental-applications">处理租赁申请</a><a class="btn secondary" href="#rental-repairs">查看报修工单</a></div></section>`;return;}
 if(page==='profile'){const [u,d]=await Promise.all([api('/me'),api('/rental/dashboard')]);$('#content').innerHTML=heading('个人中心','维护对外联系信息与个人头像。')+`<div class="profile-grid"><section class="panel"><h2>我的资料</h2><img class="r-avatar" src="${esc(u.avatar||'/favicon.svg')}" alt="当前头像"><label class="field"><span>修改头像</span><input type="file" id="r-avatar" accept="image/png,image/jpeg"></label><form id="rental-form" data-path="/me" data-method="PUT">${field('姓名','name',u.name,'text','required maxlength="40"')}${field('学校或机构（选填）','school',u.school)}${field('手机号','phone',u.phone,'tel','required pattern="1[3-9][0-9]{9}"')}${submit('保存资料')}</form></section><section class="panel"><h2>我的房源数据</h2><p>在管房源 ${d.houses} 套</p><p>待审核 ${d.pending} 套</p><p>有效合同 ${d.contracts} 份</p><a href="#rental-houses" class="btn secondary">查看我的房源</a></section></div>`;return;}
 if(page==='chat')return rentalChat();
 const rows=await api('/rental/'+page);state.rows=rows;
 if(page==='houses'){$('#content').innerHTML=heading('我的房源','编辑后重新审核；历史关联记录保留。',rButton('发布房源','new-house')+rButton('删除选中房源','batch-house'))+table(['选择','房源','月租','状态 / 审核意见','操作'],rows.map(h=>[selectRow(h.id),esc(h.title),'¥'+money(h.rent),badge(h.status)+'<p>'+esc(h.review_note||'')+'</p>',`<div class="actions">${rButton('编辑','edit-house',h.id)}${h.status==='PUBLISHED'?rButton('下架','house-status',h.id,'data-status="OFFLINE"'):h.status==='OFFLINE'?rButton('上架','house-status',h.id,'data-status="PUBLISHED"'):''}${rButton('删除','delete-house',h.id)}</div>`]));return;}
 if(page==='applications'){$('#content').innerHTML=heading(isLandlord()?'租赁申请处理':'我的租赁申请','审批通过后自动生成合同，双方分别确认签署。')+table(['房源','租客','租期','申请说明','状态 / 意见','操作'],rows.map(a=>[esc(a.title),esc(a.tenant),`${esc(a.start_date)} 至 ${esc(a.end_date)}`,esc(a.note),badge(a.status)+'<p>'+esc(a.review_note||'')+'</p>',isLandlord()&&a.status==='PENDING'?rButton('通过','review',a.id,'data-kind="applications" data-status="APPROVED"')+rButton('驳回','review',a.id,'data-kind="applications" data-status="REJECTED"'):a.contract_id?'<a href="#rental-contracts">查看合同</a>':'—']));return;}
 if(page==='contracts'){$('#content').innerHTML=heading('租赁合同','自动生成的合同保存租金与条款快照，双方分别阅读并确认，保存签署记录。')+table(['编号 / 房源','租期','月租','状态','签署情况','操作'],rows.map(c=>[esc('#'+c.id+' '+c.title),`${esc(c.start_date)} 至 ${esc(c.end_date)}`,'¥'+money(c.rent),badge(c.status),`房东 ${c.landlord_signed?'已签':'未签'} / 租客 ${c.tenant_signed?'已签':'未签'}`,rButton('查看条款','contract-view',c.id)+(c.status==='DRAFT'&&!(isLandlord()?c.landlord_signed:c.tenant_signed)?rButton('确认签署','sign',c.id):'')+(!isLandlord()&&c.status==='SIGNED'?rButton('申请退租','moveout',c.id)+rButton('发起报修','repair',c.id):'')]));return;}
 if(page==='moveouts'){$('#content').innerHTML=heading(isLandlord()?'退租审批':'退租申请','未结清账单及未完成报修处理完毕后，可批准结束租约。')+table(['房源 / 合同','退租原因','状态 / 意见','操作'],rows.map(m=>[esc(m.title)+' #'+m.contract_id,esc(m.reason),badge(m.status)+'<p>'+esc(m.review_note||'')+'</p>',isLandlord()&&m.status==='PENDING'?rButton('通过','review',m.id,'data-kind="moveouts" data-status="APPROVED"')+rButton('驳回','review',m.id,'data-kind="moveouts" data-status="REJECTED"'):'—']));return;}
 if(page==='bills'){$('#content').innerHTML=heading('账单管理','按合同和月份开单，结清由房东登记线下收款。',isLandlord()?rButton('按合同开单','new-bill')+rButton('删除选中已结清账单','batch-bill'):'')+table(['选择','房源 / 合同','账期 / 到期日','金额','费用说明','状态','操作'],rows.map(b=>[isLandlord()?selectRow(b.id):'—',esc(b.title)+' #'+b.contract_id,esc(b.bill_month)+' / '+esc(b.due_date),'¥'+money(b.amount),esc(b.note),badge(b.status),isLandlord()&&b.status==='UNPAID'?rButton('登记结清','settle',b.id):date(b.paid_at)]));return;}
 if(page==='repairs'){$('#content').innerHTML=heading('报修工单','租客发起报修，房东派工并登记费用与责任方。')+table(['房源 / 合同','故障描述','状态','维修人员','费用 / 责任方','处理记录','操作'],rows.map(r=>[esc(r.title)+' #'+r.contract_id,esc(r.description),badge(r.status),esc(r.worker||'待分配'),'¥'+money(r.cost)+' / '+esc(labels[r.responsibility]||'待定'),esc(r.result_note||''),isLandlord()&&r.status!=='COMPLETED'?rButton(r.status==='PENDING'?'接单派工':'确认完成','work',r.id):'—']));return;}
 throw new Error('页面不存在');
}
async function rentalAction(action,id,el){
 if(action==='new-house'||action==='edit-house'){houseEditor(action==='new-house'?'':id);return;}
 if(action==='house-status')await api('/rental/houses/'+id+'/status','PUT',{status:el.dataset.status});
 if(action==='delete-house'){if(!confirm('删除这套房源？有进行中业务时会阻止删除。'))return;await api('/rental/houses/'+id,'DELETE');}
 if(action==='batch-house'||action==='batch-bill'){if(!confirm('确认删除所选记录？所有记录须满足删除条件。'))return;await api('/rental/'+(action==='batch-house'?'houses':'bills')+'/batch-delete','POST',{ids:pickIds()});}
 if(action==='review')return rentalForm('审批意见','/rental/'+el.dataset.kind+'/'+id+'/review',`<input type="hidden" name="status" value="${esc(el.dataset.status)}">${el.dataset.kind==='applications'&&el.dataset.status==='APPROVED'?field('押金（元，不收取填0）','deposit',0,'number','required min="0" max="999999.99" step="0.01"')+textfield('补充约定（将写入合同）','extra','无。',400):''}${textfield('处理意见','note','',255)}`,'PUT');
 if(action==='apply')return rentalForm('申请租赁','/rental/applications',`<input type="hidden" name="houseId" value="${id}">${field('租期开始','startDate','','date','required')}${field('租期结束','endDate','','date','required')}${textfield('申请说明','note')}`);
 if(action==='contract-view'||action==='sign'){
  const c=(await api('/rental/contracts')).find(c=>c.id===Number(id));
  if(!c)throw new Error('合同不存在或无权查看');
  const signed=isLandlord()?c.landlord_signed:c.tenant_signed;
  const form=c.status==='DRAFT'&&!signed?`<form id="contract-sign-form" data-id="${c.id}">
   <input type="hidden" name="termsHash" value="${esc(c.terms_hash)}">
   <h3>本人签署确认</h3><p class="hint">请核对全文及双方身份。本次操作保存账号确认记录，未提供实名认证、CA认证或第三方存证，不承诺达到可靠电子签名标准。</p>
   ${field('签署姓名（与个人资料一致）','signerName',state.user.name,'text','required maxlength="40"')}
   ${field('复核登录密码','password','','password','required maxlength="72" autocomplete="current-password"')}
   <label class="sign-consent"><input type="checkbox" name="consent" value="true" required> 我已阅读完整合同，核对租金、押金、租期和补充约定，自愿使用电子方式确认本人意思。</label>
   <div class="modal-actions">${submit('确认签署本合同')}</div></form>`:'';
  return modal('租赁合同 #'+id,contractDocument(c)+`<div class="contract-tools">${button('打印 / 保存 PDF','print')}</div>`+form);
 }
 if(action==='moveout')return rentalForm('申请退租','/rental/moveouts',`<input type="hidden" name="contractId" value="${id}">${textfield('退租原因','reason')}`);
 if(action==='repair')return rentalForm('发起报修','/rental/repairs',`<input type="hidden" name="contractId" value="${id}">${textfield('故障描述及联系时间','description','',2000)}`);
 if(action==='new-bill'){const cs=(await api('/rental/contracts')).filter(c=>c.status==='SIGNED');if(!cs.length)throw new Error('暂无可开账单的有效合同');return rentalForm('按合同开账单','/rental/bills',rentalChoice('租赁合同','contractId',cs.map(c=>[c.id,'#'+c.id+' '+c.title]))+field('账期','month','','month','required')+field('到期日','dueDate','','date','required')+field('金额（元）','amount','','number','required min="0.01" max="9999999.99" step="0.01"')+textfield('费用说明','note','',255));}
 if(action==='settle'){if(!confirm('确认已线下收到此笔款项？'))return;await api('/rental/bills/'+id+'/settle','PUT');}
 if(action==='work'){const r=state.rows.find(r=>r.id===Number(id));return rentalForm(r.status==='PENDING'?'接单并派工':'确认维修完成','/rental/repairs/'+id,`<input type="hidden" name="status" value="${r.status==='PENDING'?'ASSIGNED':'COMPLETED'}">${field('维修人员及联系方式','worker',r.worker||'','text','required maxlength="80"')}${field('费用（元，免费填0）','cost',r.cost||0,'number','required min="0" max="999999.99" step="0.01"')}${rentalChoice('责任方','responsibility',[['LANDLORD','房东承担'],['TENANT','租客承担'],['SHARED','双方分担']])}${textfield('处理说明','note',r.result_note||'',255)}`,'PUT');}
 if(action==='chat'){state.chatPeer=Number(id);location.hash='rental-chat';if(state.route==='rental-chat')return rentalChat();return;}
 if(action==='chat-house'){const input=prompt('输入要发送的已上架房源编号');if(!input)return;await api('/rental/chat/'+state.chatPeer,'POST',{type:'HOUSE',content:'',houseId:Number(input)});return refreshChat();}
 toast('操作已保存');return route();
}
let chatLast=0,chatBusy=false,unreadBusy=false;
async function rentalChat(){
 const contacts=await api('/rental/contacts');if(!state.chatPeer&&contacts.length)state.chatPeer=Number(contacts[0].id);
 const peer=state.chatPeer;chatLast=0;
 $('#content').innerHTML=heading('在线聊天','文字、图片与房源卡片；每2秒同步新消息及未读数。')+`<div class="r-chat"><aside class="panel"><h2>联系人</h2>${contacts.map(c=>`<button class="r-contact ${c.id===peer?'active':''}" data-action="r-chat" data-id="${c.id}">${esc(c.name)} <small>${esc(labels[c.role])}</small></button>`).join('')||'<p>可从房源详情发起聊天。</p>'}</aside><section class="panel"><h2>${esc(contacts.find(c=>c.id===peer)?.name|| (peer?'对话 #'+peer:'选择联系人'))}</h2><div id="r-chat-log" class="r-chat-log" aria-live="polite"></div>${peer?`<form id="r-chat-form">${field('输入消息','content','','text','required maxlength="1000"')}<div class="actions">${submit('发送文字')}<label class="btn secondary">发送图片<input id="r-chat-image" type="file" accept="image/png,image/jpeg" hidden></label>${rButton('发送房源卡片','chat-house')}</div></form>`:''}<p id="r-chat-error" class="hint" role="status"></p></section></div>`;
 if(peer)await refreshChat();
}
async function refreshChat(){
 if(chatBusy||state.route!=='rental-chat'||!state.chatPeer||document.hidden)return;chatBusy=true;const peer=state.chatPeer;
 try{const rows=await api('/rental/chat/'+peer+'?after='+chatLast);if(state.route!=='rental-chat'||peer!==state.chatPeer)return;
 const log=$('#r-chat-log');if(!log)return;
 for(const m of rows){const div=document.createElement('article');div.className='r-bubble '+(m.sender_id===state.user.id?'mine':'');div.innerHTML=`<small>${m.sender_id===state.user.id?'我':'对方'} ${esc(date(m.created_at))}</small>`+(m.type==='IMAGE'?`<a href="${esc(m.content)}" target="_blank" rel="noopener"><img src="${esc(m.content)}" alt="聊天图片"></a>`:m.type==='HOUSE'?`<a class="r-house-card" href="#house/${m.house_id}"><img src="${esc(m.image||'/favicon.svg')}" alt="房源"><strong>${esc(m.title||'房源已失效')}</strong><span>¥${money(m.rent||0)} / 月 · ${esc(labels[m.house_status]||'不可访问')}</span></a>`:`<p>${esc(m.content)}</p>`);log.append(div);chatLast=Math.max(chatLast,Number(m.id));}
 if(rows.length){await Promise.all(Array.from(log.querySelectorAll('img'),i=>i.decode().catch(()=>{})));log.scrollTop=log.scrollHeight;await api('/rental/chat/'+peer+'/read','PUT',{through:chatLast});}$('#r-chat-error').textContent='';
 }catch(e){if($('#r-chat-error'))$('#r-chat-error').textContent='消息同步失败：'+e.message;}finally{chatBusy=false;}
}
document.addEventListener('click',async e=>{const el=e.target.closest('[data-action^="r-"]');if(!el)return;e.preventDefault();e.stopImmediatePropagation();el.disabled=true;try{await rentalAction(el.dataset.action.slice(2),el.dataset.id,el);}catch(ex){toast(ex.message);}finally{el.disabled=false;}},true);
document.addEventListener('submit',async e=>{const f=e.target;if(!['rental-form','r-chat-form'].includes(f.id))return;e.preventDefault();e.stopImmediatePropagation();const btn=f.querySelector('[type=submit]');btn.disabled=true;try{const d=Object.fromEntries(new FormData(f));if(f.id==='r-chat-form'){await api('/rental/chat/'+state.chatPeer,'POST',{type:'TEXT',content:d.content});f.reset();await refreshChat();}else{await api(f.dataset.path,f.dataset.method,d);$('#modal').close();toast('已保存');await route();}}catch(ex){toast(ex.message);}finally{btn.disabled=false;}},true);
document.addEventListener('change',async e=>{if(!['r-avatar','r-chat-image'].includes(e.target.id)||!e.target.files[0])return;try{const data=new FormData();data.append('file',e.target.files[0]);const r=await api(e.target.id==='r-avatar'?'/me/avatar':'/rental/images','POST',data);if(e.target.id==='r-avatar')await route();else{await api('/rental/chat/'+state.chatPeer,'POST',{type:'IMAGE',content:r.url});await refreshChat();}}catch(ex){toast(ex.message);}});
setInterval(async()=>{if(!state.user||isAdmin()||document.hidden||unreadBusy)return;unreadBusy=true;try{const d=await api('/rental/unread');let el=$('#r-unread');if(!el){el=document.createElement('a');el.id='r-unread';el.href='#rental-chat';el.className='btn secondary small';$('.top-actions')?.prepend(el);}el.textContent='未读消息 '+d.count;el.setAttribute('aria-label','未读消息'+d.count);await refreshChat();}catch{}finally{unreadBusy=false;}},2000);

function contractDocument(c){
 return `<article class="contract-paper"><div class="contract-meta"><span>校园安居 · 租赁档案</span><span>编号 RH-${String(c.id).padStart(6,'0')}</span></div><h2>城镇房屋租赁合同</h2><p class="contract-status">${badge(c.status)} 月租 ¥${money(c.rent)} · ${esc(c.start_date)} 至 ${esc(c.end_date)}</p><div class="contract-terms">${esc(c.terms)}</div><div class="signature-grid">${[['landlord','出租人'],['tenant','承租人']].map(([k,t])=>`<section><h3>${t}签署区</h3><strong>${esc(c[k+'_signed_name']||(c[k+'_signed']?'历史账号确认':'待本人确认'))}</strong><p>${c[k+'_signed_time']?esc(date(c[k+'_signed_time'])):'尚无本次格式的签署时间记录'}</p><small>${c[k+'_signed_hash']?'签署正文摘要：'+esc(c[k+'_signed_hash']):'未记录签署正文摘要'}</small></section>`).join('')}</div><p class="contract-hash">当前正文 SHA-256：${esc(c.terms_hash)}</p><p class="hint">可使用浏览器打印中的“另存为 PDF”保存完整正文及签署信息。</p></article>`;
}
document.addEventListener('submit',async e=>{
 const f=e.target;if(f.id!=='contract-sign-form')return;e.preventDefault();e.stopImmediatePropagation();
 const btn=f.querySelector('[type=submit]');btn.disabled=true;
 try{const d=Object.fromEntries(new FormData(f));await api('/rental/contracts/'+f.dataset.id+'/sign','PUT',d);f.reset();$('#modal').close();toast('本人签署记录已保存');await route();}
 catch(ex){f.querySelector('[name=password]').value='';toast(ex.message);}finally{btn.disabled=false;}
},true);
