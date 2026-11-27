# RAIN Browser — Phase 2 Addendum
## Video Offline Download System

> สถานะ: ข้อกำหนดและแนวทางหลักที่เจ้าของโครงการยืนยัน วันที่ 2026-11-27
> Platform: Android 16 / API 36+
> Engine: Android WebView เดิม
> Priority: Workstream ลำดับแรกของ Phase 2 ก่อน Private Browsing
> หลักการ: ต่อยอดระบบ Download และ Storage เดิม ไม่สร้าง Browser Engine หรือ Download Manager ซ้ำ
> หมายเหตุ: เอกสารนี้เป็นแผนและข้อกำหนด ไม่ใช่คำสั่งให้เริ่ม implementation เต็มรูปแบบ; ต้องผ่าน Source Audit และ Feasibility Gates ก่อน

---

## 1. เป้าหมายผลิตภัณฑ์

ให้ผู้ใช้ดาวน์โหลดวิดีโอจากหน้าเว็บไซต์ที่กำลังเปิดใน RAIN เก็บไว้ในอุปกรณ์ และกลับมาเปิดดูได้โดยไม่ต้องเชื่อมต่ออินเทอร์เน็ต โดยมีประสบการณ์คลังวิดีโอใกล้เคียง YouTube Downloads แต่ใช้ RAIN branding

หลักการที่เจ้าของโครงการกำหนด: **วิดีโอใดที่ RAIN เล่นได้และแหล่งเว็บไซต์อนุญาตให้บันทึกได้ ให้ผู้ใช้มีทางเลือกดาวน์โหลดเพื่อดูแบบออฟไลน์** ทั้งนี้ การเล่นและการเข้าถึงไฟล์สำหรับดาวน์โหลดเป็นคนละความสามารถ จึงไม่รับประกันว่าวิดีโอทุกชนิดที่เล่นได้จะดาวน์โหลดได้

## 2. ข้อกำหนดฟังก์ชัน

### 2.1 Video Detection และ Popup

- ตรวจจับวิดีโอจากหน้าเว็บปัจจุบันเมื่อผู้ใช้เริ่มเล่น โดยใช้ Hybrid Detection: HTML5/player context ร่วมกับ network/resource signals ที่ WebView เปิดเผยได้
- JavaScript ส่งเฉพาะสัญญาณขั้นต่ำ เช่น playback state, duration, dimensions, visibility และ metadata ประกอบที่ไม่อ่อนไหว; Native เป็นผู้ประเมินและตัดสินใจขั้นสุดท้าย
- ห้ามสมมติว่า `shouldInterceptRequest` เห็น media requests ทุกชนิด และห้ามเชื่อถือ URL ที่มาจาก JavaScript โดยตรง
- แยกโฆษณาด้วยหลายสัญญาณ เช่น player ad state, Advertisement/Skip Ad indicators, source/title/poster changes และ page context; ห้ามใช้ duration เป็นเกณฑ์ตัดสินเดี่ยว
- ใช้ stability check หลังพบ candidate; ช่วง 3–5 วินาทีเป็นค่าเริ่มต้นสำหรับ PoC ที่ต้องปรับตามผลทดสอบ ไม่ใช่กฎตายตัว
- ถ้าเป็นโฆษณา, ข้อมูลขัดแย้ง, ไม่แน่ใจ หรือไม่มีแหล่งดาวน์โหลดที่อนุญาต ให้ fail closed และไม่แสดง Popup
- เมื่อยืนยันได้ ให้แสดง Popup ขนาดเล็ก มี thumbnail/title เมื่อหาได้ พร้อม Download และ Close; Close ปิดเฉพาะ Popup และไม่หยุด playback
- ไม่แสดง Popup ซ้ำสำหรับวิดีโอเดิมหลังผู้ใช้ปิดหรือเริ่มดาวน์โหลด; แสดงใหม่เมื่อพบวิดีโอใหม่ที่ยืนยันได้เท่านั้น

### 2.2 Supported Media / Quality

- Initial supported target: direct single-file MP4/WebM
- เลือกคุณภาพเฉพาะเมื่อ source มีตัวเลือกจริง; ไม่สร้าง quality หรือ file size เทียม
- Initial scope ตรวจพบ/จัดประเภท HLS/DASH ได้เมื่อข้อมูลเปิดเผย แต่ยังไม่ดาวน์โหลด; การเพิ่ม clear HLS/DASH เป็นงานย่อยที่ต้องประเมินและอนุมัติแยก
- ไม่ bypass/decrypt DRM, Widevine/PlayReady, encryption, paywall, authentication/token restrictions หรือข้อจำกัดเว็บไซต์
- MSE/Blob, cross-origin iframe หรือ Player ที่ไม่เปิดเผย source อาจตรวจพบ/ดาวน์โหลดไม่ได้ ต้องรายงานข้อจำกัดตามหลักฐาน
- ไม่รับประกัน universal website support

### 2.3 Download Manager และ Reliability

- ทำ Source Audit ของ DownloadListener, Android DownloadManager, DownloadPolicy, Storage/Repository และ UI ก่อน
- ใช้ DownloadManager เดิมสำหรับ direct file เมื่อ audit ยืนยันว่าเหมาะสม
- ใช้ WorkManager สำหรับ recovery/reconciliation, retention cleanup และงานเบื้องหลังที่เหมาะสม ไม่แทน DownloadManager โดยอัตโนมัติ
- Foreground Service ใช้เมื่อ Android/platform requirement และความจำเป็นพิสูจน์ได้เท่านั้น
- ไม่เพิ่ม OkHttp, Media3/ExoPlayer หรือ dependency/SDK ภายนอกจนกว่าจะพบ gap ชัดเจนและได้รับอนุมัติ
- รองรับ queue, status, progress, cancel, retry และ pause/resume ตามความสามารถจริงของ backend/source
- Resume ต้องตรวจ HTTP Range/206/Content-Range และ ETag/Last-Modified เมื่อมี; ห้าม append partial file หาก validator เปลี่ยนหรือ server ไม่ยืนยัน Range
- แยก TRANSFER_COMPLETED จาก VIDEO_READY; ตรวจไฟล์ก่อนเปิดให้เล่น
- ตรวจพื้นที่, network loss, HTTP errors, expired URL/token, permissions และ incomplete/corrupt files; corrupted file ใช้ FAILED_CORRUPTED เพื่อให้ retry/delete ได้
- ทำ duplicate detection ด้วย identity/fingerprint ที่เชื่อถือได้ ไม่ใช้ URL เพียงอย่างเดียว
- Recovery ตรวจ DownloadManager ID, metadata, file state และ orphan partial files ให้สอดคล้องกัน; การลบไฟล์/metadata ต้อง recoverable และ idempotent

### 2.4 Background Behavior

หลังเริ่มดาวน์โหลด งานต้องไม่ผูกกับ WebView, หน้าเว็บ, Tab หรือ Activity lifecycle และต้องพยายามทำงานต่อเมื่อ:
- ผู้ใช้ออกจากหน้า playback หรือเปลี่ยน Tab
- ปิด Tab ต้นทาง
- ออกจาก RAIN ตามปกติหรือปัดแอปออกจาก Recent Apps
- ปิดหน้าจอ
- เครือข่ายขาดและกลับมา (เมื่อ source/backend รองรับ)
- อุปกรณ์ reboot (reconcile/resume/restart ตามความสามารถ Android และ source)

ข้อจำกัดที่ต้องแจ้งชัดเจน: Android Force Stop, battery/background restrictions, OEM task management, URL/token expiry และ session requirements อาจทำให้งานหยุดหรือ resume ไม่ได้ ห้ามรับประกันการทำงานทุกกรณี

### 2.5 Offline Video Library

- แสดงคลังวิดีโอแยกมุมมองจาก Downloads ทั่วไป ใช้ RAIN logo/branding และ UX ใกล้ YouTube Downloads โดยไม่คัดลอก YouTube branding
- แสดง thumbnail, title, duration, file size, download date, status และ expiry เมื่อข้อมูลมีจริง
- รองรับค้นหา, Like ❤️, Unlike, เล่นจาก local file, ลบรายรายการ และจัดการสถานะ
- Playback ต้องไม่เรียก URL ต้นทาง; ทดสอบ airplane mode, seek, portrait/landscape และ fullscreen ตาม player เดิม
- ไม่กระทบ normal downloads, APK download, Tabs, History, Bookmarks หรือ WebView

## 3. Retention Policy — ข้อกำหนดที่เจ้าของโครงการยืนยัน

1. วิดีโอเริ่มนับ 30 วันเมื่อดาวน์โหลดเสร็จและตรวจสอบว่าเป็นไฟล์พร้อมเล่นแล้ว
2. กด Like ❤️: เก็บไม่จำกัดเวลาและยกเว้น auto-delete
3. กด Unlike: เริ่มนับ 30 วันใหม่จากเวลาที่กด Unlike
4. ครบ 30 วัน: ลบเฉพาะวิดีโอที่ยังไม่ได้ Like
5. ผู้ใช้ลบเองได้ทุกเวลา
6. Favorite state, retention start และ expiry ต้องคงอยู่หลังปิดแอป/รีบูต
7. Cleanup ห้ามลบ liked videos หรือไฟล์ Downloads ประเภทอื่น; หากลบไฟล์/metadata ไม่สำเร็จให้เก็บสถานะ retry ได้
8. ยังไม่กำหนด hard storage quota จนกว่าจะมีข้อมูลพื้นที่จริงและอนุมัติตัวเลข

ตัวอย่าง: ดาวน์โหลดวันที่ 1, Like วันที่ 5, Unlike วันที่ 20 → เริ่มนับ 30 วันใหม่จากวันที่ 20 และลบได้เมื่อครบ 30 วัน หากยังไม่ได้ Like อีก

## 4. Storage, Privacy และ Security

- ตรวจ Storage/Repository/Database เดิมก่อนเลือก schema หรือเพิ่ม Room/SQLite; ห้ามสร้างระบบจัดเก็บซ้ำ
- ใช้ app-specific storage หรือแนวทางเดิมตามผล audit และ scoped-storage; ต้องระบุผล uninstall/clear data
- หากเพิ่ม schema ต้องมี migration; ห้าม destructive migration ที่ทำข้อมูลผู้ใช้สูญหาย
- แยก Video Library metadata จาก History และ Private Browsing data; ห้ามบันทึก Private URL/title/media metadata ลง normal store
- JS bridge ห้ามสั่ง download/delete/read file หรือเข้าถึง database/cookies โดยตรง
- Native ตรวจ scheme, host, redirects, resolved IP/private network access, content type และ page/resource context เพื่อป้องกัน unsafe URL/SSRF
- หลีกเลี่ยงส่ง cookies/credentials ข้าม origin โดยไม่จำเป็น; ห้ามบันทึก URL/token/cookie ที่อ่อนไหวลง log หรือส่ง browsing data ไป third party
- เคารพลิขสิทธิ์ DRM และข้อจำกัดการเข้าถึงทุกกรณี

## 5. Architecture Decision — แนวทางที่เลือกหลังเปรียบเทียบคำปรึกษา

| ประเด็น | แนวทางหลัก |
|---|---|
| Detection | Hybrid: HTML5/player context + network signals; Native final decision |
| Ad classification | Multi-signal + stability check + fail closed; no duration-only rule |
| Direct download | Existing Android DownloadManager when audit confirms fit |
| Recovery/cleanup | WorkManager for reconciliation, retention and cleanup |
| Foreground Service | Only when required and justified by Android behavior |
| New downloader/OkHttp | Not in initial design; propose only if measured gap remains |
| Database | Reuse existing Storage first; choose Room/SQLite only after audit |
| MP4/WebM | Initial download target |
| HLS/DASH | Detect/classify only initially; full download requires separate approval |
| DRM | No bypass/decryption |
| JS bridge | Minimal untrusted signals only; native validates every resource |
| Corrupt files | FAILED_CORRUPTED; retry/delete; never mark ready |
| Storage quota | No fixed quota until approved |

## 6. Workstreams และ Gates

| ลำดับ | งาน | Deliverable / Gate |
|---|---|---|
| A0 | Source Audit | Inventory WebView, DownloadListener/DownloadManager, DownloadPolicy, Storage, Manifest, UI, tests, CI; reuse/gap matrix |
| A1 | Detection Feasibility PoC | Test direct HTML5, pre-roll, mid-roll, multiple videos/iframe/MSE where feasible; fail closed when uncertain |
| B | Download Core | MP4/WebM direct download, queue/status/progress/error/retry/cancel, file validation, metadata/retention |
| C | Offline Library & Playback | RAIN library, search, Like/Unlike, 30-day expiry, local playback |
| D | Background/Recovery/Security | network/reboot recovery, URL/bridge security, cleanup consistency |
| E | Integration & Acceptance | regression, CI, test matrix and real-device acceptance |

**Implementation Gate:** ห้ามเริ่ม implementation ของระบบเต็มรูปแบบจน A0/A1 แสดงหลักฐานว่าแหล่งสื่อที่ต้องการรองรับตรวจพบและดาวน์โหลดได้อย่างถูกต้องภายใน WebView/Android เดิม หากจำเป็นต้องเปลี่ยน architecture หรือเพิ่ม dependency ให้เสนอผลกระทบและขออนุมัติก่อน

## 7. Acceptance Criteria

1. Popup แสดงเฉพาะเมื่อยืนยัน main video และมี resource ที่ดาวน์โหลดได้โดยชอบ; ad/uncertain cases ไม่แสดง
2. Popup ขนาดเล็กมี Download/Close; Close ไม่หยุด playback และไม่แสดงซ้ำสำหรับวิดีโอเดิม
3. ดาวน์โหลด direct MP4/WebM ที่อนุญาตได้; quality options มีเฉพาะที่ source เสนอจริง
4. HLS/DASH ถูกจัดประเภทตามข้อมูลที่เปิดเผย แต่ initial scope ไม่ bypass DRM หรือดาวน์โหลด stream ที่ไม่รองรับ
5. Transfer complete ต้องผ่าน validation ก่อนเป็น VIDEO_READY
6. เปิดเล่น local file ใน airplane mode ได้โดยไม่เรียกเว็บไซต์ต้นทาง
7. Like/Unlike/30-day expiry ตรงกฎและคงอยู่หลัง force close/reboot; auto-delete ไม่ลบ liked videos หรือไฟล์อื่น
8. Normal download และ APK download ตาม policy เดิมยังทำงาน
9. Network interruption, insufficient storage, expired URL, cancel/retry, corrupted file, reboot และ cleanup recovery ผ่าน test cases
10. ไม่มี Critical/High security issue ใน bridge, URL validation, file access และ data handling
11. Build/lint/unit/instrumentation/UI tests ผ่าน พร้อม CI evidence
12. ทดสอบอุปกรณ์จริง Android 16/API 36; บันทึก device, OS, WebView provider, website/format matrix และข้อจำกัดจริง

## 8. ขอบเขตที่ไม่รวม / ต้องอนุมัติแยก

- Full HLS/DASH download
- DRM bypass/decryption หรือการดาวน์โหลดที่เว็บไซต์ไม่อนุญาต
- เปลี่ยน WebView Engine หรือ Playback Layer
- เพิ่ม OkHttp, Media3/ExoPlayer หรือ third-party SDK/service
- กำหนด hard storage quota
- แทน DownloadManager เดิมด้วย downloader ใหม่โดยไม่มีหลักฐานจาก audit

## 9. Reporting / Definition of Done

รายงานแยก Supported / Partially supported / Not supported / Blocked พร้อมหลักฐาน commit, PR, CI, test report, device/OS/WebView provider และ site/format matrix ห้ามอ้าง universal support จากการทดสอบไม่กี่เว็บไซต์

Phase 2 ลำดับรวม:
1. Video Offline Download (เริ่ม A0/A1 และผ่าน Gate ก่อน)
2. Private Browsing (คง workstream และเกณฑ์ isolation เดิม)
3. Reference Home Experience และงาน Phase 2 อื่นตาม dependency/แผน PM

Phase 1.6 ไม่เปลี่ยนแปลง และการเพิ่มเอกสารนี้ไม่ใช่คำสั่งเริ่ม implementation

---

## Change Log

### 2026-11-27 — Architecture decision and Phase 2 priority
- เลือก Hybrid Detection, multi-signal ad classification, fail-closed behavior
- เลือก existing DownloadManager เมื่อ audit ยืนยันว่าเหมาะสม และ WorkManager สำหรับ recovery/cleanup
- กำหนด MP4/WebM เป็น initial download target; HLS/DASH detect-only ใน initial scope
- เพิ่ม retention 30 วัน, Like/Unlike reset, offline library, background caveats, secure bridge และ acceptance gates
- กำหนด Video Offline Download เป็นงานแรกของ Phase 2 ก่อน Private Browsing โดยคง Phase 1.6 และ Private Browsing scope เดิม
