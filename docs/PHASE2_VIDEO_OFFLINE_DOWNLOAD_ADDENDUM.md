# RAIN Browser — Phase 2 Addendum
## Video Offline Download System

> สถานะ: ข้อกำหนดเพิ่มเติมของ Phase 2 ตามความต้องการเจ้าของโครงการ วันที่ 2026-11-27
> Platform: Android
> Engine: Android WebView เดิม
> หลักการ: ต่อยอดระบบ Download และ WebView เดิม ไม่สร้าง Browser Engine หรือ Download Manager ซ้ำ
> หมายเหตุ: เอกสารนี้เป็นการเพิ่มรายละเอียดแผน ยังไม่ใช่คำสั่งเริ่ม implementation โดยอัตโนมัติ

---

## 1. เป้าหมาย

เพิ่มความสามารถให้ผู้ใช้ดาวน์โหลดวิดีโอจากหน้าเว็บไซต์ที่กำลังเปิดใน RAIN เก็บไว้ในอุปกรณ์ และกลับมาเปิดดูได้โดยไม่ต้องเชื่อมต่ออินเทอร์เน็ต โดยมีประสบการณ์ใช้งานคล้ายคลังวิดีโอ Downloads ของ YouTube

หลักการผลิตภัณฑ์ที่เจ้าของโครงการกำหนด:

**วิดีโอใดที่ RAIN เล่นได้และแหล่งเว็บไซต์อนุญาตให้บันทึกได้ ให้ผู้ใช้มีทางเลือกดาวน์โหลดเพื่อดูแบบออฟไลน์**

ข้อความนี้ไม่ใช่การรับประกันว่าวิดีโอทุกชนิดที่เล่นได้จะต้องสามารถดาวน์โหลดเป็นไฟล์ได้ เพราะการเล่นและสิทธิ์/วิธีการส่งมอบไฟล์เป็นคนละความสามารถกัน

## 2. ขอบเขตความสามารถ

### 2.1 ตรวจจับและเสนอการดาวน์โหลด
- ตรวจสอบแหล่งวิดีโอจากหน้าเว็บที่ผู้ใช้เปิดอยู่ โดยใช้กลไกที่ WebView และเว็บไซต์อนุญาต
- แสดงคำสั่งดาวน์โหลดเมื่อพบแหล่งวิดีโอที่รองรับจริง
- รองรับลิงก์ไฟล์วิดีโอโดยตรง เช่น MP4 และ WebM
- ประเมินการรองรับสตรีมมิง เช่น HLS/DASH เป็นรายรูปแบบและรายเว็บไซต์ เฉพาะกรณีที่สามารถบันทึกได้อย่างถูกต้องและได้รับอนุญาต
- ไม่แสดงปุ่มดาวน์โหลดที่กดได้ หากยังไม่มีวิธีดาวน์โหลดจริงสำหรับ resource นั้น
- ไม่ดึง URL หรือข้อมูลลับออกจากระบบป้องกันของเว็บไซต์ และไม่ทำให้การเข้าถึงที่เว็บไซต์จำกัดกลายเป็นการดาวน์โหลดที่ไม่ได้รับอนุญาต

### 2.2 ตัวเลือกก่อนดาวน์โหลด
- แสดงชื่อวิดีโอหรือชื่อไฟล์ที่ตรวจสอบได้
- แสดงตัวเลือกความละเอียด/คุณภาพเมื่อแหล่งข้อมูลมีตัวเลือกจริง
- แสดงขนาดโดยประมาณเมื่อสามารถอ่านได้; หากไม่ทราบต้องระบุว่าไม่ทราบ ไม่สร้างค่าประมาณเทียม
- ให้ผู้ใช้ยืนยันก่อนเริ่มดาวน์โหลดตามนโยบาย Download ของ RAIN
- ใช้การเชื่อมต่อและข้อกำหนดการยืนยันที่เหมาะสมกับ resource นั้น

### 2.3 การจัดการงานดาวน์โหลด
- ต่อยอด Download Manager, DownloadListener, repository และ storage เดิมหลังจากตรวจ Source Code
- แสดงสถานะ queued / downloading / completed / failed ตามสถานะจริง
- แสดงความคืบหน้าและขนาดเมื่อแหล่งข้อมูลรายงานได้
- รองรับ pause/resume เฉพาะเมื่อกลไกต้นทางและระบบดาวน์โหลดรองรับจริง
- รองรับ cancel, retry เมื่อเหมาะสม และลบรายการ
- จัดการกรณีพื้นที่ไม่พอ, network หลุด, URL หมดอายุ, HTTP error, permission ถูกปฏิเสธ และไฟล์ไม่สมบูรณ์
- ไม่รายงานว่าสำเร็จจนกว่าไฟล์จะถูกบันทึกและตรวจสอบการจบงานได้

### 2.4 คลังวิดีโอออฟไลน์
- มีหน้า/หมวดวิดีโอที่ดาวน์โหลดไว้ให้เข้าถึงได้ง่าย คล้ายหน้า Downloads ของ YouTube
- แสดงชื่อวิดีโอ, thumbnail เมื่อมี, ระยะเวลา/ขนาดเมื่ออ่านได้ และสถานะไฟล์
- เปิดเล่นจากไฟล์ในเครื่องได้โดยไม่ต้องเรียก URL ต้นทางหรือใช้อินเทอร์เน็ต
- รองรับการเล่นต่อ/seek ตามความสามารถของ media player ที่ใช้อยู่
- ให้ลบวิดีโอและจัดการพื้นที่จัดเก็บได้
- แยกรายการวิดีโอออกจากไฟล์ดาวน์โหลดประเภทอื่นใน UI ได้ โดยไม่สร้างระบบจัดเก็บไฟล์ซ้ำ
- การลบรายการต้องจัดการไฟล์จริงตามสิทธิ์และแสดงผลตรงกับสถานะจริง

### 2.5 นโยบายไฟล์ดาวน์โหลดอื่น
- ความสามารถนี้ไม่ทำให้ RAIN จำกัดการดาวน์โหลดไว้เฉพาะวิดีโอ
- APK จาก GitHub/แหล่งที่ผู้ใช้เลือกต้องยังดาวน์โหลดได้ตาม flow ยืนยันที่กำหนดไว้
- ไฟล์ประเภทอื่นยังใช้ Download policy และการยืนยันตามข้อกำหนดเดิม
- ห้ามเปลี่ยน policy ประเภทไฟล์โดยพลการในงาน Video Offline Download

## 3. ข้อจำกัดและความปลอดภัย

- ไม่ข้าม DRM, encryption, paywall, authentication, token protection หรือข้อจำกัดการดาวน์โหลดของเว็บไซต์
- ไม่แกะ/ถอดรหัสหรือประกอบสตรีมที่ถูกป้องกันเพื่อหลีกเลี่ยงสิทธิ์การเข้าถึง
- ไม่รับรองการดาวน์โหลดจากทุกเว็บไซต์ แม้ WebView จะเล่นวิดีโอนั้นได้
- เว็บไซต์อาจอนุญาตให้ดูผ่าน player แต่ไม่อนุญาตให้บันทึกไฟล์; กรณีนี้ต้องแสดงข้อความอธิบายอย่างชัดเจน
- ต้องเคารพเงื่อนไขเว็บไซต์ ลิขสิทธิ์ และสิทธิ์ของเจ้าของเนื้อหา
- ห้ามส่ง URL, cookies, credentials หรือข้อมูล browsing ไปยัง third-party provider โดยไม่มีการตรวจสอบ privacy/security และการอนุมัติที่จำเป็น
- ไม่เพิ่ม SDK/service ภายนอกหรือค่าใช้จ่ายโดยไม่มีการประเมินและอนุมัติ
- ป้องกันการเปิดไฟล์ที่ไม่สมบูรณ์หรือไฟล์ที่มี MIME/type ไม่ตรงกับเนื้อหาจริงเท่าที่ตรวจสอบได้

## 4. การต่อยอดสถาปัตยกรรมเดิม

ก่อน implementation ต้องทำ Source Inventory:
- DownloadListener และการรับ event จาก WebView
- DownloadManager integration หรือกลไกดาวน์โหลดที่มีอยู่
- Download repository/database และ file storage
- หน้ารายการ Downloads และการเปิด/ลบไฟล์
- WebViewClient / WebChromeClient และ lifecycle ของ media
- กลไก fullscreen video และ media playback
- permission / MediaStore / scoped storage ตาม Android API ที่รองรับ
- tests และ GitHub Actions ที่เกี่ยวข้อง

การตัดสินใจ:
- มีระบบแล้วและใช้ได้: นำกลับมาใช้
- มีระบบแต่ยังไม่ครบ: ขยายของเดิม
- ยังไม่มี: เพิ่มส่วนประกอบที่จำเป็นและเชื่อมกับโครงสร้างเดิม
- ไม่สามารถทำได้ด้วย WebView/platform/provider: บันทึกข้อจำกัดและเสนอทางเลือกก่อนเปลี่ยน architecture หรือเพิ่ม dependency

ข้อห้าม:
- ห้ามเปลี่ยน Android WebView เป็น Engine อื่น
- ห้ามสร้าง Download Manager, file repository หรือ storage ซ้ำ
- ห้ามกระทบ applicationId, signing identity, user data หรือระบบแท็บเดิม
- ไม่มี RAIN Account และ Cloud Sync
- ห้ามนำ Private Browsing มาใช้เป็นข้ออ้างให้รวมข้อมูลส่วนตัวเข้ากับคลังดาวน์โหลดปกติ; ต้องเคารพข้อกำหนด isolation ของ Private Browsing ใน Phase 2

## 5. แผนดำเนินงานย่อย

### Workstream A — Audit และ Technical Feasibility
- ตรวจระบบ Download และ media ของเดิม
- ทำตารางชนิด resource / วิธีตรวจจับ / วิธีดาวน์โหลด / ข้อจำกัด
- ระบุ website test matrix ที่ครอบคลุม direct file และ streaming ที่อนุญาต
- สรุปสิ่งที่ทำได้, ทำได้บางกรณี, และไม่รองรับ พร้อมเหตุผล
- Gate: เจ้าของโครงการ/PM เห็นชอบ feasibility และ scope ก่อน implementation

### Workstream B — Video Detection และ Download Flow
- ตรวจจับเฉพาะ resource ที่รองรับจริง
- เพิ่ม entry point สำหรับเริ่มดาวน์โหลดจากหน้าเว็บ
- เพิ่มตัวเลือกคุณภาพเมื่อมีจริง
- เชื่อม Download Manager เดิมและจัดการ error/retry/cancel
- Gate: ทดสอบการดาวน์โหลดไฟล์ที่ได้รับอนุญาตและยืนยันไฟล์สมบูรณ์ได้

### Workstream C — Offline Video Library และ Playback
- เพิ่มมุมมองวิดีโอใน Downloads เดิมหรือหน้าแยกที่ใช้ data/storage เดิม
- เปิดเล่นไฟล์ในเครื่องได้โดยไม่ต้องใช้อินเทอร์เน็ต
- จัดการ metadata, thumbnail, delete และ storage errors
- Gate: ดาวน์โหลดเสร็จ ปิดอินเทอร์เน็ต/เปิดโหมดเครื่องบิน แล้วเปิดดูและ seek ได้ตาม format ที่รองรับ

### Workstream D — Integration, Regression และ Device Acceptance
- ทดสอบ portrait/landscape และ fullscreen
- ทดสอบ network interruption, insufficient storage, expired source URL และ corrupted/incomplete file
- Regression test Download ปกติ, APK download, History, Tabs และ WebView
- ทดสอบบนอุปกรณ์ Android จริงและบันทึก OS/WebView provider
- Gate: ไม่มี Critical/High bug; แนบ CI/test evidence และรายการข้อจำกัดที่ตรวจสอบแล้ว

## 6. Acceptance Criteria

ถือว่างาน Video Offline Download ผ่านได้เมื่อ:
1. RAIN ตรวจจับและเสนอดาวน์โหลดเฉพาะวิดีโอ/resource ที่มีวิธีดาวน์โหลดรองรับจริง
2. ผู้ใช้เริ่มดาวน์โหลดจากหน้าเว็บที่กำลังเปิดได้ในกรณีที่เว็บไซต์อนุญาต
3. ตัวเลือกคุณภาพแสดงเฉพาะตัวเลือกที่ต้นทางมีจริง
4. สถานะและ progress ตรงกับงานจริง; error แสดงสาเหตุที่เข้าใจได้
5. ไฟล์ที่ดาวน์โหลดเสร็จเปิดจากอุปกรณ์ได้โดยไม่ต้องใช้อินเทอร์เน็ต
6. มีคลัง/หมวดวิดีโอที่ดาวน์โหลดไว้ และผู้ใช้เปิด/ลบได้
7. การเล่นออฟไลน์ไม่เรียก resource จากเว็บไซต์ต้นทาง
8. Download ปกติและ APK download ตาม policy เดิมยังทำงานได้
9. ไม่มีการข้าม DRM หรือข้อจำกัดของเว็บไซต์
10. ทดสอบกรณีเครือข่ายหลุด พื้นที่ไม่พอ ดาวน์โหลดล้มเหลว และไฟล์ไม่สมบูรณ์
11. Build, lint, unit/instrumentation/UI tests ที่เกี่ยวข้องผ่าน
12. ทดสอบบนอุปกรณ์ Android จริง พร้อมหลักฐานและรายการ format/site ที่รองรับจริง

## 7. ความสัมพันธ์กับ Phase 2 เดิม

- เพิ่ม Video Offline Download เป็น workstream ใน Phase 2 ควบคู่กับ Private Browsing ไม่ได้แทนที่หรือลดขอบเขต Private Browsing
- Private Browsing ยังคงมี process/storage isolation, cookie/cache/Web Storage separation, Service Worker isolation, cleanup และ dedicated instrumentation verification ตามข้อกำหนดเดิม
- ห้ามนับ Video Offline Download ว่าทำให้ Private Browsing ผ่าน หรือกลับกัน
- การจัดลำดับเวลา/เดือนและ resource ของ Phase 2 ให้ PM รวมเข้ากับ master schedule หลังประเมิน effort และ dependency; เอกสารนี้ยังไม่กำหนดวันส่งมอบ
- Phase 1.6 ยังคงขอบเขตเดิม; เอกสารนี้ไม่อนุมัติให้เริ่ม implementation ใน Phase 1.6

## 8. Definition of Done และการรายงาน

รายงานผลแยก:
- Supported — ดาวน์โหลดและเล่นออฟไลน์ผ่านการทดสอบจริง
- Partially supported — รองรับเฉพาะบาง format/site หรือมีข้อจำกัดที่ระบุชัด
- Not supported — มี DRM/ข้อจำกัด/ข้อจำกัด platform หรือไม่มีวิธีดาวน์โหลดที่อนุญาต
- Blocked — ต้องรอการตัดสินใจหรือ dependency ที่ได้รับอนุมัติ

แนบ commit, PR, CI run, test report, อุปกรณ์/OS/WebView provider และ test matrix ทุกครั้ง ห้ามอ้างว่ารองรับทุกเว็บไซต์จากการทดสอบเพียงเว็บเดียว

---

## Change Log

### 2026-11-27 — เพิ่มข้อกำหนด Video Offline Download
- เพิ่มเป้าหมายดาวน์โหลดวิดีโอจากหน้าเว็บเพื่อดูแบบออฟไลน์ ตามความต้องการเจ้าของโครงการ
- เพิ่ม video detection, download flow, quality selection เมื่อมีจริง, offline library และ offline playback
- กำหนดให้ต่อยอด Download Manager/storage เดิม ไม่สร้างระบบซ้ำ
- ยืนยันว่า APK download และ Download ปกติยังต้องทำงานตาม policy เดิม
- กำหนดข้อจำกัด DRM, website permissions, privacy และ copyright
- เพิ่ม acceptance criteria และ device test requirements
- กำหนดให้เป็น workstream เพิ่มเติมใน Phase 2 โดยไม่แทนที่ Private Browsing
