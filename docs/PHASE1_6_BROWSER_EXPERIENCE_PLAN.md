# RAIN Browser — Phase 1.6
## แผนพัฒนาต่อยอดระบบเดิม: Chrome-like Browser Menu & Core Experience

> สถานะ: แผนงานที่ปรับปรุงตามคำสั่งเจ้าของโครงการ 2026-11-27  
> Repository: `phukthaisongp1-collab/Rain`  
> Platform: Android  
> Engine: Android WebView เดิม  
> หลักการ: ต่อเติม Source Code และระบบที่มีอยู่ ไม่รื้อสร้างใหม่  
> หมายเหตุ: เอกสารนี้เป็นแผนและข้อกำหนด ไม่ใช่คำสั่งให้เริ่ม implementation โดยอัตโนมัติ

---

## 1. เป้าหมาย Phase 1.6

พัฒนา RAIN Browser จาก Source Code, UI, Manager, Repository, Storage และ browser flow ที่มีอยู่ ให้มีประสบการณ์ใช้งานใกล้เคียง Google Chrome Android โดยเน้นเมนูหลักที่เปิดจากปุ่ม 3 ขีดเดิมของ RAIN

เมื่อผู้ใช้กดปุ่ม 3 ขีด ต้องแสดงเมนูที่มีหน้าตาและรูปแบบการใช้งานใกล้เคียง Chrome Android ทั้งการจัดกลุ่ม รายการ ไอคอน ระยะห่าง ลำดับ และการตอบสนอง และ **ทุกคำสั่งที่แสดงในเมนูต้องทำงานได้จริง** ไม่ใช่เพียง UI หรือปุ่มตัวอย่าง

เป้าหมายคือยกระดับ browser ที่มีอยู่ให้สมบูรณ์ขึ้น ไม่ใช่เริ่มโครงการใหม่หรือสร้าง browser อีกชุดหนึ่ง

## 2. ข้อกำหนดที่ห้ามเปลี่ยน

1. คง Android WebView เป็น web rendering engine
2. คง Kotlin, Android SDK, Gradle และสถาปัตยกรรมหลักปัจจุบัน
3. ต่อยอด TabManager, WebView lifecycle, navigation, History, Bookmark, Download, Settings, storage และ component เดิมเมื่อมีอยู่
4. ห้ามสร้าง Manager/Repository/Storage ซ้ำ หากระบบเดิมสามารถขยายได้
5. ห้ามเปลี่ยน applicationId `com.rain.browser` หรือ Signing Key
6. รักษาข้อมูลผู้ใช้ แท็บ และ session เดิมตามความสามารถของระบบปัจจุบัน
7. ไม่มี RAIN Account และไม่มี Cloud Sync
8. ไม่เปลี่ยน Engine, architecture, data format ที่มีผลต่อข้อมูลผู้ใช้ หรือ Product Behavior หลักโดยไม่ได้รับอนุมัติ
9. การเพิ่ม dependency หรือบริการภายนอกต้องตรวจสอบความจำเป็น ความปลอดภัย ความเป็นส่วนตัว ค่าใช้จ่าย และเงื่อนไขก่อน
10. งาน Phase 1.6 ต้องไม่ทำให้ความสามารถที่ผ่านการตรวจรับใน Phase ก่อนหน้าถดถอย

## 3. วิธีเริ่มงาน: ตรวจของเดิมก่อนเขียนเพิ่ม

ก่อนแก้ไขแต่ละความสามารถ Developer ต้องตรวจ Source Code และบันทึก Inventory อย่างน้อยดังนี้:

- ไฟล์ UI และ event handler ของปุ่มเมนู 3 ขีด
- Activity/Fragment และ navigation flow ที่ใช้อยู่
- TabManager และ model ของแท็บ
- WebView lifecycle, WebViewClient และ WebChromeClient
- BrowserUrlResolver และ search/navigation flow
- History repository/storage และหน้าประวัติ
- Bookmark repository/storage และหน้าบุ๊กมาร์ก
- DownloadListener, DownloadManager integration และหน้าดาวน์โหลด
- Site permission handling และ Android runtime permissions
- Settings และ persistence
- Find-in-page implementation
- Fullscreen custom view / video lifecycle
- WindowInsets, Status Bar, Navigation Bar และ orientation handling
- Unit, instrumentation, UI tests และ GitHub Actions ที่มีอยู่

ผล Inventory ต้องแยกให้ชัดว่าแต่ละรายการ:
- มีอยู่และใช้งานได้ — นำกลับมาใช้
- มีอยู่แต่ยังไม่สมบูรณ์ — แก้/ขยายของเดิม
- ยังไม่มี — เพิ่มโดยเชื่อมเข้ากับโครงสร้างเดิม
- ทำไม่ได้หรือมีข้อจำกัด platform/provider — บันทึกข้อจำกัดและเสนอทางเลือกก่อนเพิ่ม dependency หรือเปลี่ยนพฤติกรรม

ห้ามสรุปว่าไม่มีระบบเดิมจนกว่าจะตรวจ Source Code ที่เกี่ยวข้องแล้ว

## 4. เมนูหลักจากปุ่ม 3 ขีด

### 4.1 รูปแบบและพฤติกรรม

- ใช้ปุ่ม 3 ขีดเดิมของ RAIN
- เปิดเมนูแบบ Chrome Android โดยอ้างอิงภาพ/เวอร์ชัน Chrome Android ที่ใช้เป็นต้นแบบในช่วงพัฒนา
- จัดกลุ่มคำสั่ง ไอคอน ชื่อรายการ ระยะห่าง และลำดับให้ใกล้เคียงต้นแบบ
- ใช้ UI theme ของ RAIN โดยคงลำดับชั้นและรูปแบบการโต้ตอบของ Chrome
- เมนูต้องรองรับหน้าจอแนวตั้งและแนวนอน ไม่ล้นจอหรือบังคำสั่ง
- กดรายการแล้วเรียก action จริงของ RAIN หรือ Android ตามหน้าที่
- ปิดเมนูอย่างถูกต้องหลัง action หรือเมื่อผู้ใช้แตะภายนอก/กด Back
- ทุก control ที่ดูเหมือนกดได้ต้องมี action; ห้ามมี dead button, placeholder หรือผลลัพธ์จำลอง

### 4.2 รายการคำสั่งที่อยู่ในขอบเขต

รายการสุดท้ายต้องผ่าน UI/Source inventory และจัดกลุ่มตามต้นแบบ Chrome Android:

**Browser navigation และ tab**
- New tab
- Recent tabs / Recently closed tabs ที่เก็บในอุปกรณ์
- Tab groups และการจัดการกลุ่มแท็บ โดยต่อยอด TabManager เดิม
- Back
- Forward
- Reload
- Tab switcher และจำนวนแท็บ

**ข้อมูลการท่องเว็บ**
- Bookmarks — เปิด ดู ค้นหา เพิ่ม แก้ไข และลบรายการ
- History — ดู ค้นหา และเปิดรายการเดิม
- Clear browsing data — เลือกประเภทข้อมูลและช่วงเวลาที่ระบบรองรับ พร้อมยืนยันก่อนลบ
- Downloads — ดูสถานะ เปิด แชร์ และลบรายการ/ไฟล์ตามสิทธิ์ที่ Android อนุญาต

**เครื่องมือบนหน้าเว็บ**
- Share — แชร์ URL/ชื่อหน้า ผ่าน Android Sharesheet
- Find in page — ค้นหา ถัดไป/ก่อนหน้า และแสดงจำนวนผลที่พบ
- Translate — แปลหน้าเว็บจริงผ่านวิธี/provider ที่ตรวจสอบแล้ว
- Listen to this page — อ่านเนื้อหาหน้าเว็บด้วย Android TextToSpeech หรือกลไกที่รองรับจริง
- Reading mode — แสดงเนื้อหาที่อ่านได้ในมุมมองอ่าน โดยต้องมี empty/error state
- Desktop site — เปลี่ยน User-Agent/viewport ตามวิธีที่ WebView รองรับ และ reload หน้าอย่างถูกต้อง
- Add to Home screen — สร้าง shortcut ด้วย Android API และข้อมูลเว็บไซต์จริง
- Install — แสดงเฉพาะกรณีที่เว็บไซต์และ Android รองรับการติดตั้งเว็บแอปจริง; ห้ามแสดงปุ่มติดตั้งที่ทำงานไม่ได้

**การควบคุมเว็บไซต์และแอป**
- Site controls — แสดงและจัดการ permission/site settings ที่ RAIN รองรับจริง
- Settings — เปิดหน้าตั้งค่า RAIN เดิมและเชื่อมการตั้งค่าที่มีผลกับ browser จริง
- Help & Feedback — เปิดหน้าช่วยเหลือ/ช่องทางส่ง feedback ที่กำหนดไว้จริง

### 4.3 ขอบเขตที่ต้องแยกออกจาก Phase 1.6

- **Incognito / Private Browsing ไม่อยู่ใน Phase 1.6** เนื่องจากแผนหลักของโครงการกำหนดให้ Private Browsing เป็นงาน Phase 2 พร้อม workflow, isolation และ cleanup verification แยกต่างหาก
- ห้ามสร้าง Incognito tab ที่เป็นเพียงหน้าตา หรือใช้ normal WebView แล้วเรียกว่า Private
- ห้ามนำ Private Browsing มารวมกับ acceptance ของ Phase 1.6 หรือกล่าวอ้างว่าผ่าน
- Recent tabs ใน Phase 1.6 หมายถึงข้อมูลแท็บ/แท็บที่ปิดล่าสุดซึ่งมีอยู่ในอุปกรณ์เท่านั้น ไม่ใช่การซิงก์แท็บจากอุปกรณ์อื่น เพราะ RAIN ไม่มี Cloud Sync
- รายการที่ยังไม่มี implementation ต้องไม่แสดงเป็น action ที่กดได้จนกว่าจะพัฒนาและทดสอบครบ

## 5. Toolbar และ browser interactions

ต่อยอด Toolbar เดิมให้คำสั่งที่อยู่ในต้นแบบ Chrome Android ทำงานได้จริง:
- Back / Forward ตาม WebView navigation state
- Bookmark เพิ่ม/ยกเลิก bookmark ของ URL ปัจจุบัน
- Download เริ่มการดาวน์โหลดจาก resource จริงและใช้ระบบดาวน์โหลดเดิม
- Reload โหลดหน้าเว็บปัจจุบันใหม่
- Address/Search field ส่ง URL หรือ query ผ่าน BrowserUrlResolver เดิม
- Tab switcher เปิด/สลับ/ปิดแท็บผ่าน TabManager เดิม
- แสดงสถานะ enabled/disabled ให้สอดคล้องกับ navigation state

ห้ามสร้าง navigation stack หรือ tab manager ชุดใหม่ซ้ำกับระบบเดิม

## 6. Status Bar, orientation และ fullscreen

- ปรับ Status Bar ในหน้าการใช้งานปกติให้มีพื้นหลังสีดำสอดคล้องกับ Toolbar
- สีไอคอนนาฬิกา แบตเตอรี่ และสัญญาณต้อง contrast ถูกต้องและอ่านได้
- จัดการ WindowInsets เพื่อไม่ให้ Toolbar หรือเนื้อหาเว็บทับ System Bars
- รองรับ Portrait และ Landscape โดยไม่ทำให้ tab, navigation หรือ form state สูญหาย
- ปรับ layout ตามพื้นที่หน้าจอจริงเมื่อหมุนเครื่อง
- การเล่นวิดีโอ Fullscreen ต้องซ่อน/แสดง System Bars ตาม lifecycle ของ fullscreen แยกจากหน้าปกติ
- ตรวจสอบพฤติกรรมบน Android API 36 ซึ่งมี edge-to-edge enforcement; ห้ามอาศัยการตั้ง `statusBarColor` เพียงอย่างเดียวโดยไม่ตรวจผลบนเครื่องจริง
- ไม่เปลี่ยนพฤติกรรมหน้า Home ที่ยังไม่มีข้อสรุปเพิ่มเติมโดยพลการ

## 7. ข้อกำหนดเฉพาะของความสามารถที่พึ่งพา Platform/Provider

1. **Translate:** ตรวจสอบ provider, API/ข้อกำหนดบริการ, privacy, data transfer, quota, key และค่าใช้จ่ายก่อนเลือก หากต้องมีค่าใช้จ่ายหรือส่งข้อมูลหน้าเว็บให้บริการภายนอก ต้องเสนอรายละเอียดให้เจ้าของโครงการอนุมัติก่อน
2. **Listen to this page:** ต้องจัดการภาษา เสียงที่ไม่มีในเครื่อง การหยุด/เล่นต่อ และ lifecycle ของ TTS
3. **Reading mode:** ต้องจัดการหน้าเว็บที่ดึงเนื้อหาไม่ได้, paywall, iframe, dynamic content และหน้าไม่มีเนื้อหาหลัก โดยไม่ทำลายหน้าเว็บต้นฉบับ
4. **Add to Home screen / Install:** แยก shortcut ปกติออกจาก PWA install; แสดงเฉพาะ capability ที่ตรวจสอบได้
5. **Site controls:** แสดง permission ตาม origin ปัจจุบัน และใช้ Android/WebView permission API ที่ถูกต้อง; ห้ามให้สิทธิ์กว้างเกินกว่าที่ผู้ใช้เลือก
6. **Clear browsing data:** ระบุประเภทข้อมูลที่ลบได้จริง; ห้ามแสดงว่าลบ Cookie/Cache/Storage สำเร็จหาก WebView API ไม่ยืนยันผล
7. หาก capability ใดทำไม่ได้บน WebView/Android รุ่นที่รองรับ ให้รายงานข้อจำกัดและเสนอวิธีที่ไม่เปลี่ยน architecture; ห้ามจำลองผลลัพธ์

## 8. แผนดำเนินงานรายเดือน (ประมาณการ)

กรอบนี้เป็นลำดับงานสำหรับวางแผน ไม่ใช่การรับประกันวันส่งมอบ แต่ละเดือนต้องผ่าน Gate ก่อนเริ่มเดือนถัดไป

### เดือนที่ 1 — Audit, UX Specification และ Main Menu
- ตรวจ Source Code และระบบเดิมตาม Section 3
- ทำรายการ mapping: Menu item → existing class/API → action → test
- เก็บภาพอ้างอิง Chrome Android รุ่นที่กำหนดและสรุป menu layout
- ปรับ UI เมนู 3 ขีดเดิมให้ใกล้เคียง Chrome Android
- เชื่อมคำสั่งที่มีระบบพร้อมอยู่แล้ว โดยไม่สร้างระบบซ้ำ
- Gate: ไม่มี action ที่แสดงแต่ยังไม่มี owner/implementation mapping; UI ผ่าน portrait/landscape review

### เดือนที่ 2 — Core Browser Actions และ Data Management
- เชื่อม Back/Forward/Reload/Bookmark/Download/Tab switcher
- ทำ Bookmarks, History, Recent/Recently closed tabs และ Clear browsing data ให้ครบตามระบบเดิม
- ตรวจ persistence, migration compatibility และการรักษาข้อมูลผู้ใช้
- เชื่อม Android Sharesheet และ Find in page
- Gate: automated tests ของ action/data ผ่าน และไม่มี regression ต่อ tab/navigation/data เดิม

### เดือนที่ 3 — Page Tools และ Site Controls
- พัฒนา/เชื่อม Desktop site, Site controls และ permission handling
- พัฒนา Listen to this page และ Reading mode
- สรุปและเชื่อม Translate หลังผ่าน provider/privacy/cost review
- ทำ Add to Home screen และ Install เฉพาะ capability ที่รองรับ
- เชื่อม Settings และ Help & Feedback
- Gate: ทุก action มี end-to-end test หรือหลักฐาน platform test; provider-dependent action ต้องผ่านการอนุมัติและทดสอบจริง

### เดือนที่ 4 — Integration, Responsive UI และ Acceptance
- ทดสอบเมนูและ Toolbar ทั้งหมดแบบ end-to-end
- ทดสอบ portrait/landscape, Android System Bars และ video fullscreen
- ทดสอบเว็บไซต์หลายประเภท รวม redirect, popup, media, permission prompt, download และหน้า error
- Regression test กับ TabManager, WebView lifecycle, History, Bookmark, Download และข้อมูลเดิม
- Build/Lint/Unit/Instrumentation/UI tests ผ่าน
- ทดสอบบนอุปกรณ์ Android จริงและบันทึกหลักฐาน
- Gate: ปิด Critical/High bugs ทั้งหมด; รายงานรายการที่รองรับ/ไม่รองรับตามจริง; ผ่าน acceptance criteria ก่อนตรวจรับ

## 9. Acceptance Criteria

Phase 1.6 จะผ่านได้ต่อเมื่อมีหลักฐานครบทุกข้อที่เกี่ยวข้อง:

1. ปุ่ม 3 ขีดเดิมเปิดเมนูที่มีหน้าตาและการจัดวางใกล้เคียง Chrome Android
2. ทุกเมนูที่แสดงและกดได้ทำงานจริงตามหน้าที่; ไม่มี dead button หรือ placeholder
3. รายการทุกตัวมี mapping ไปยัง implementation และ test case
4. New tab, tab switcher และ tab group ใช้ TabManager เดิม ไม่สร้างระบบแท็บซ้ำ
5. Bookmark, History, Downloads และ Clear browsing data ใช้ storage/repository เดิมหรือขยายของเดิม
6. Share เปิด Android Sharesheet และส่ง URL/ชื่อหน้าได้จริง
7. Find in page ค้นหาและเลื่อนผลลัพธ์ได้จริง
8. Desktop site เปลี่ยนโหมดและ reload ได้จริง พร้อม restore behavior ตามข้อกำหนดที่กำหนด
9. Site controls แสดง origin และ permission ที่ถูกต้อง และการอนุญาต/ปฏิเสธมีผลจริง
10. Translate, Listen และ Reading mode ผ่าน end-to-end test และแสดง failure state อย่างถูกต้อง
11. Add to Home screen สร้าง shortcut เปิด URL ที่ถูกต้องได้; Install แสดงเฉพาะเมื่อ install flow รองรับจริง
12. Status Bar ดำ ไอคอนอ่านได้ ไม่ทับเนื้อหา และทำงานร่วมกับ fullscreen video
13. Portrait/Landscape ไม่ทำให้ app crash, tab หาย, state สูญหาย หรือ layout ใช้งานไม่ได้
14. Build, lint, unit/instrumentation/UI tests ที่เกี่ยวข้องผ่าน พร้อมแนบ CI links/reports
15. ทดสอบบนอุปกรณ์ Android จริง พร้อมระบุรุ่น OS/WebView provider และผลแต่ละกรณี
16. ทดสอบอัปเดตทับแอปเดิมโดย applicationId/signing identity เดิม และตรวจข้อมูลผู้ใช้
17. Private Browsing/Incognito ไม่ถูกรวมใน Phase 1.6 และไม่ถูกนับว่าผ่าน

## 10. Definition of Done และการรายงาน

- รายงานแยก Done / In progress / Blocked / Not supported
- แนบ Commit, PR, CI run, test report และภาพ/วิดีโอการทดสอบเมื่อมี
- ห้ามอ้างว่าฟังก์ชันทำงานจริงจากการ compile ผ่านเพียงอย่างเดียว
- หาก provider, API, Android หรือ WebView มีข้อจำกัด ต้องบันทึกให้ชัดและขออนุมัติเมื่อมีผลต่อ scope/cost/privacy/architecture
- ห้าม merge หรือ release โดยไม่มีการตรวจสอบตาม workflow ของ Repository
- การทำเอกสารแผนนี้ไม่ใช่การอนุมัติเริ่ม implementation; ต้องมีคำสั่งเริ่มงานแยกต่างหาก

## 11. Change Log

### 2026-11-27 — Replan
- เปลี่ยน Phase 1.6 ให้ชัดว่าเป็นการต่อยอด Source Code และระบบ RAIN เดิม
- ยืนยัน Android WebView และ architecture เดิม; ตัดแนวทางเปลี่ยน Chromium Engine ออกจาก Phase 1.6
- กำหนดให้ปุ่ม 3 ขีดเดิมแสดงเมนูใกล้เคียง Chrome Android และทุกคำสั่งที่แสดงต้องใช้งานได้จริง
- เพิ่ม inventory-first workflow, menu mapping, test mapping และ acceptance criteria
- เพิ่ม Toolbar, Status Bar, orientation และ fullscreen requirements
- แบ่งแผนเป็น 4 เดือนตาม milestones
- แยก Incognito/Private Browsing ออกจาก Phase 1.6 ตามแผน Phase 2 ที่มีอยู่
- คงข้อกำหนด no RAIN account / no cloud sync และการรักษาข้อมูลผู้ใช้เดิม
