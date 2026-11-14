/* 只读设计验收：不同屏宽的首页/详情、设施布局、减少动态效果。 */
const fs=require('node:fs');const path=require('node:path');const assert=require('node:assert/strict');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const base=process.env.BASE_URL||'http://127.0.0.1:8080';
(async()=>{
 const browser=await chromium.launch({headless:true,...(process.env.CHROME_PATH?{executablePath:process.env.CHROME_PATH}:{})});
 const context=await browser.newContext({locale:'zh-CN',timezoneId:'Asia/Shanghai',reducedMotion:'reduce'});
 const page=await context.newPage();const errors=[];const results=[];page.on('pageerror',e=>errors.push(e.message));
 for(const width of [320,390,768,1440]){
  await page.setViewportSize({width,height:900});await page.goto(base);
  await page.getByRole('heading',{name:'好房源，离校园更近',exact:true}).waitFor();
  await page.locator('.house-card').first().waitFor();
  assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'首页不应横向溢出 '+width);
  await page.locator('.card-title').first().click();await page.getByRole('heading',{name:'房源详情',exact:true}).waitFor();
  assert.equal(await page.locator('.facility').count(),12);
  assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'详情不应横向溢出 '+width);
  assert.equal(await page.locator('.btn').first().evaluate(el=>getComputedStyle(el).transitionDuration),'0s');
  if(width===390){await page.locator('.detail-image').evaluate(el=>el.loading='eager');await page.waitForFunction(()=>[...document.images].every(i=>i.complete&&i.naturalWidth>0));await page.screenshot({path:path.resolve('docs/evidence/10-mobile-detail.png'),fullPage:true});}
  results.push({width,homepage:'PASS',detail:'PASS',facilities:12,reducedMotion:'PASS'});console.log('PASS responsive '+width);
 }
 assert.deepEqual(errors,[]);fs.writeFileSync('docs/evidence/responsive.json',JSON.stringify({executedAt:new Date().toISOString(),results,pageErrors:errors},null,2));await browser.close();
})().catch(e=>{console.error(e);process.exit(1)});
