# RAIN Phase 2 — แผนพัฒนาระบบดาวน์โหลดวิดีโอออฟไลน์ (ฉบับปรับปรุง)

> Updated: 2026-11-28
> Repository: `phukthaisongp1-collab/Rain`
> Application: `com.rain.browser` / `0.1.4` / `versionCode 5`
> This revision supersedes the previous Phase 2 execution plan.

## 1. เป้าหมายของโครงการ

พัฒนาระบบดาวน์โหลดวิดีโอสำหรับเบราว์เซอร์ RAIN โดยมีเป้าหมายหลักคือ

- ตรวจจับวิดีโอจากเว็บไซต์ทั่วไป ไม่จำกัดเพียงเว็บไซต์ทดสอบ
- แยกวิดีโอโฆษณาออกจากเนื้อหาหลักอย่างน่าเชื่อถือ
- ตรวจสอบแหล่งที่มาของสื่อก่อนอนุญาตให้ดาวน์โหลด
- รองรับไฟล์ MP4 และ WebM ตามความสามารถที่ตรวจสอบได้
- แสดงสถานะและความคืบหน้าการดาวน์โหลด
- รองรับยกเลิก ดาวน์โหลดซ้ำ และกู้คืนเมื่อเครือข่ายขัดข้อง
- จัดเก็บวิดีโอใน Video Library และเล่นแบบออฟไลน์
- จัดการ Like, Unlike และลบวิดีโอเก่าอัตโนมัติ
- ตรวจสอบความสมบูรณ์ ความปลอดภัย และพื้นที่จัดเก็บ

### 1.1 ข้อจำกัดด้านความปลอดภัย (ต้องบังคับใช้ในโค้ด)

- ไม่ข้าม DRM, Token, Authentication, Paywall, Cloudflare Challenge หรือข้อจำกัดการดาวน์โหลดของเว็บไซต์
- หากตรวจสอบสิทธิ์ไม่ได้ ให้ปฏิเสธอย่างปลอดภัย (Fail-closed) และระบุว่าไม่รองรับหรือรองรับบางส่วน
- ไม่แสดง Popup ดาวน์โหลดเพียงเพราะตรวจพบหรือเล่นวิดีโอได้
- แอปควรแจ้งผู้ใช้ให้ดาวน์โหลดเฉพาะสื่อที่ตนมีสิทธิ์ใช้งาน

## 2. แผนดำเนินงาน 35 วัน

| วัน | งานที่ต้องดำเนินการ | สถานะ |
|---|---|---|
| 1–7 | WebView/Media Callbacks, Download Policy, Storage, ตรวจจับ HTML5 Video, สัญญาณโฆษณา, Direct MP4 | เสร็จ |
| 8 | ปิด Detection Feasibility Gate ตามเงื่อนไขใหม่ (หัวข้อ 3) | ปิดตามเงื่อนไขใหม่ |
| 9 | Download Core Foundation + SQLite Schema v2 → v3 (เพิ่มตาราง `videos` แบบ additive-only และออกแบบคอลัมน์ให้รองรับ Day 19–30 ครบในครั้งเดียว) | ปิดแล้ว — CI 97/97 ผ่าน รวม `StorageMigrationTest` |
| 10 | Direct MP4 Download + รวม Detection Hook ขั้นต่ำ (ผู้ใช้สั่งเอง) | ปิดแล้ว — CI Build/Lint ผ่าน |
### 2.4 ข้อจำกัดของ Detection Hook ขั้นต่ำ

- รองรับเฉพาะ URL วิดีโอ MP4/WebM แบบ Direct ที่เปิดเผยจากองค์ประกอบ `<video>` หรือ `<source>` และต้องผ่านการตรวจ MIME/container จาก Source Validator ก่อนดาวน์โหลด
- ไม่มีการแยกโฆษณาอัตโนมัติ และไม่รับประกันว่าวิดีโอที่ตรวจพบเป็นเนื้อหาหลักของหน้า
- ไม่รองรับ `blob:`, `data:`, Media Source Extensions (MSE), stream segments หรือแหล่งที่ไม่สามารถตรวจสอบ URL โดยตรงได้
- ไม่รับประกันการตรวจจับหรือดาวน์โหลดได้จากทุกเว็บไซต์ โดยการตรวจจับเกิดขึ้นเมื่อผู้ใช้สั่งจากเมนูเท่านั้น ไม่มี Popup อัตโนมัติเมื่อพบหรือเล่นวิดีโอ

| 11 | Direct WebM Download ผ่าน Detection Hook และ Download Core เดิม โดยตรวจ MIME/container จริง | ปิดแล้ว — CI Build/Lint ผ่าน |
| 12 | เลือกคุณภาพเฉพาะเมื่อ Source เดียวกันมีหลาย variant ที่ระบุคุณภาพ/ความละเอียดได้จริง | ปิดแล้ว — CI Build/Lint ผ่าน |
| 13 | Dialog รายการดาวน์โหลดวิดีโอ แสดงชื่อ สถานะ และเปอร์เซ็นต์ พร้อม refresh ทุก 1.5 วินาทีขณะเปิด | ปิดแล้ว — CI Run #46099702278 ผ่าน |
| 14 | ยกเลิกงานที่ยังทำงาน และดาวน์โหลดใหม่สำหรับรายการล้มเหลว/ถูกยกเลิก โดยตรวจสอบและลบเฉพาะไฟล์เดิมใน RAIN | กำลังดำเนินการ |
| 15 | ใช้ Android DownloadManager ทำงานต่อเบื้องหลังโดยไม่เพิ่ม Foreground Service หรือ Permission ใหม่ | ปิดแล้ว — CI Run #46100166799 ผ่าน |
| 16 | แสดงสถานะรอเครือข่ายจาก DownloadManager; ใช้ If-Match/If-Unmodified-Since จาก ETag/Last-Modified และเมื่อ Source เปลี่ยนให้การ Resume ถูกปฏิเสธ จากนั้น Retry จะลบ partial ใน RAIN และตรวจ Source ใหม่ก่อนเริ่มจากต้น | ปิดแล้ว — CI Run #46100436441 ผ่าน |
| 17 | เมื่อเริ่มแอปให้เทียบรายการใน `videos` กับ DownloadManager ผ่าน `download_id`; งานที่ไม่มี system request จะถูกทำเครื่องหมาย FAILED และลบ partial เฉพาะใน RAIN; orphan DownloadManager request จะถูกลบเฉพาะเมื่อยืนยัน local path อยู่ใน RAIN (ตำแหน่งไม่ทราบจะไม่แตะต้อง) | ปิดแล้ว — CI Run #46100857529 ผ่าน |
| 18 | ขอสิทธิ์ `POST_NOTIFICATIONS` ในการดาวน์โหลดครั้งแรก; รับ `ACTION_DOWNLOAD_COMPLETE` แล้วตรวจผลผ่าน Download Core ก่อนส่งการแจ้งเตือนสำเร็จ/ล้มเหลว โดยใช้การแจ้งเตือนของ DownloadManager เฉพาะระหว่างดาวน์โหลดเพื่อไม่ให้แจ้งเตือนเสร็จซ้ำ; แตะการแจ้งเตือนเพื่อเปิด Dialog รายการดาวน์โหลดเดิม | กำลังดำเนินการ |\n\nหมายเหตุ Dialog ดาวน์โหลดวิดีโอใน Day 13–18 เป็น UI ชั่วคราว เปิดจากเมนูเบราว์เซอร์เดิมหรือแตะการแจ้งเตือนของ RAIN เท่านั้น ไม่มี Activity ใหม่ และจะถูกแทนที่ใน Day 19 เมื่อเริ่ม Video Library; หากผู้ใช้ปฏิเสธ/ปิดสิทธิ์การแจ้งเตือน ระบบยังดาวน์โหลดต่อได้ แต่ Android จะไม่แสดงการแจ้งเตือนจาก RAIN\n
| 19 | สร้าง Video Library | ยังไม่เริ่ม |
| 20 | บันทึกและอัปเดตข้อมูลวิดีโอใน Library โดยใช้ตาราง `videos` ที่สร้างใน Day 9 (ไม่มี Migration เพิ่ม) | ยังไม่เริ่ม |
| 21 | แสดงรายการวิดีโอและรายละเอียด | ยังไม่เริ่ม |
| 22 | เล่นวิดีโอแบบ Offline | ยังไม่เริ่ม |
| 23 | จัดการสถานะและการเล่นวิดีโอ | ยังไม่เริ่ม |
| 24 | ระบบ Like | ยังไม่เริ่ม |
| 25 | ระบบ Unlike | ยังไม่เริ่ม |
| 26 | ลบวิดีโอเก่าอัตโนมัติ 30 วัน | ยังไม่เริ่ม |
| 27 | ตรวจสอบความสมบูรณ์ของไฟล์ | ยังไม่เริ่ม |
| 28 | ตรวจสอบความปลอดภัยของไฟล์และ Source | ยังไม่เริ่ม |
| 29 | จัดการพื้นที่จัดเก็บ | ยังไม่เริ่ม |
| 30 | กู้คืนข้อมูลและ Storage Recovery | ยังไม่เริ่ม |
| 31–32 | Integration + Regression Test (รอบเดียวรวมกัน) | ยังไม่เริ่ม |
| 33 | แก้ไขปัญหาที่พบและทดสอบซ้ำเฉพาะส่วนที่แก้ | ยังไม่เริ่ม |
| 34 | Pre-Acceptance CI และตรวจหลักฐาน | ยังไม่เริ่ม |
| 35 | ทดสอบบนโทรศัพท์ Android 16 เครื่องจริงและ Final Acceptance | รอทดสอบ |

### 2.1 ขอบเขตการทำงาน Day 9 และการนำโค้ดจาก PR #40 มาใช้

- แตก Branch ใหม่จาก Branch หลักปัจจุบัน `phase2/video-detection-poc` เท่านั้น
- ใช้ PR #40 เป็นแหล่งอ้างอิงเพื่อคัดเลือกโค้ด ไม่ Merge PR #40 ทั้งก้อน และไม่เปลี่ยน Base Branch ไปใช้ Branch ต้นทางของ PR #40
- ดึงเฉพาะไฟล์/การเปลี่ยนแปลงที่จำเป็นสำหรับ Day 9 ได้แก่ Download Core Coordinator, Video Repository และ SQLite Migration ที่เกี่ยวข้อง หลังตรวจสอบ dependency และความเข้ากันได้กับโค้ดบน Branch หลัก
- ห้ามนำฟีเจอร์ของ Day 10–17 หรือ Library UI ที่ยังไม่ถึงกำหนดเข้ามาโดยอ้อม หากไฟล์ใดมี dependency ร่วม ให้แยกเฉพาะส่วนที่ Day 9 ต้องใช้โดยไม่เปลี่ยนสถาปัตยกรรมหลัก
- Day 9 ต้องทำให้ Core สามารถบันทึก/อ่านข้อมูล Download ผ่าน Repository บน Schema v3 ได้ โดยไม่ Merge PR #40 ทั้งก้อน
- CI Build และ Lint ต้องผ่านก่อน Merge งานของ Day 9

### 2.2 ข้อกำหนด SQLite Schema v3 (สร้างครั้งเดียวใน Day 9)

Migration จาก Schema v2 เป็น v3 ต้องเป็นแบบเพิ่มอย่างเดียว (additive-only):

- เพิ่มตารางใหม่ชื่อ `videos` เพียงอย่างเดียวใน Migration นี้
- ห้ามลบ แก้โครงสร้าง ล้างข้อมูล หรือสร้างตารางเดิมใหม่ ได้แก่ `bookmarks`, `history`, `resume`, `settings`
- ออกแบบคอลัมน์ของ `videos` ให้รองรับการทำงานตั้งแต่ Download Core จนถึง Day 19–30 โดยไม่ต้องเพิ่ม Schema v4 สำหรับฟีเจอร์ที่อยู่ในแผนนี้
- ต้องมีข้อมูลหลักที่จำเป็นต่อ Download Core/Library เช่น รหัสรายการ, ข้อมูลชื่อและ Source, URI/ตำแหน่งไฟล์ในเครื่อง, รูปแบบสื่อ, สถานะและความคืบหน้าการดาวน์โหลด, ขนาดไฟล์, วันที่บันทึก, สถานะ Like และ Checksum
- ต้องรองรับการจัดการสถานะไฟล์และการเล่นออฟไลน์ ตลอดจนการตรวจสอบความสมบูรณ์ ความปลอดภัย พื้นที่จัดเก็บ และการลบอัตโนมัติเมื่อครบ 30 วัน
- ฟิลด์และชนิดข้อมูลจริงต้องสอดคล้องกับ Model/Repository ที่เลือกใช้จาก PR #40 และต้องตรวจสอบความเข้ากันได้ก่อนเขียน Migration ห้ามสร้างคอลัมน์ซ้ำหรือเดาชื่อคอลัมน์จากเอกสารโดยไม่ตรวจโค้ดจริง
- Migration ต้องรักษาข้อมูลเดิมทั้งหมด และต้องไม่ลดเลข Schema Version หรือทำ destructive migration

ข้อกำหนดขั้นต่ำของข้อมูลที่ต้องจัดเก็บใน `videos`:
- Like state สำหรับ Like/Unlike
- Saved/created timestamp ที่ใช้คำนวณอายุ 30 วัน
- File size สำหรับ Storage Management
- Checksum สำหรับ File Integrity
- Source และ local file reference สำหรับตรวจสอบแหล่งที่มาและ Offline Playback
- Download/playback state และ metadata ที่จำเป็นต่อการกู้คืนงานและ Library

### 2.3 ขอบเขต Day 20 หลังย้าย Migration มา Day 9

Day 20 เหลือเฉพาะการบันทึกและอัปเดตข้อมูลวิดีโอลง Video Library ผ่านตาราง `videos` ที่มีอยู่แล้ว ไม่ทำ Migration ซ้ำ ไม่เพิ่ม Schema Version และไม่สร้างตารางทดแทน

## 3. Day 8 — เงื่อนไขปิด Gate (ฉบับปรับปรุง)

Day 8 ผ่านเมื่อครบทั้งสองข้อ:

1. ลอจิกตรวจจับผ่านการทดสอบด้วยหน้าทดสอบที่ควบคุมได้ (`Html5VideoDetectionProbeTest` และ CI Run #337 ผ่านแล้ว)
2. บันทึกข้อจำกัดจากการตรวจเว็บจริงไว้ในเอกสาร:
   - `037-hdd.com` และ `nunghd4k.com`: ไม่พบ Video DOM ในสถานะเริ่มต้น
   - `fwip-tv.com/visionquest`: พบ Cloudflare Challenge จึงตรวจสอบไม่ได้ (ไม่พยายามข้าม)

การตรวจเว็บจริงเป็นหลักฐานประกอบ ไม่ใช่เงื่อนไขผ่าน/ไม่ผ่านอีกต่อไป ระบบต้องไม่รับประกันว่าสื่อทุกชนิดจากทุกเว็บไซต์จะตรวจจับหรือดาวน์โหลดได้ และให้ Fail-closed เมื่อข้อมูลกำกวม

## 4. กติกาด้านระบบและข้อมูลเดิม

| รายการ | ข้อกำหนด |
|---|---|
| Application ID | `com.rain.browser` |
| Version ปัจจุบัน | `0.1.4` (Version Code `5`) |
| Version Code รุ่นถัดไป | อย่างน้อย `6` |
| Database | SQLite Schema Version `2` ปัจจุบัน; เป้าหมาย Schema Version `3` หลัง Day 9 |
| ตารางเดิม | `bookmarks`, `history`, `resume`, `settings` |
| ตารางใหม่ | `videos` (เพิ่มใน Day 9 เท่านั้น) |
| Download เดิม | WebView `DownloadListener` → `DownloadPolicy` → Android `DownloadManager` |

ข้อบังคับ:

- ห้ามล้างข้อมูลผู้ใช้ ห้ามลบหรือสร้างตารางเดิมใหม่
- Database Migration ต้องเป็นแบบเพิ่มส่วนใหม่เท่านั้น
- ต้องรักษา Signing Certificate เดิม
- ต้องไม่ทำให้ระบบดาวน์โหลดไฟล์ทั่วไปและ APK เดิมเสีย
- การติดตั้งทับรุ่นเดิมต้องรักษาข้อมูลผู้ใช้ทั้งหมด โดยมีการตรวจหลักฐานใน Day 34/35

SHA-256 ของ Signing Certificate ที่ต้องรักษา:

`d1b9587fd636c1e0e92a5718576381aadbc72190f5c927f45e7d4ebe0eadb592`

## 5. กฎการทดสอบ (ฉบับปรับปรุง)

- Day 9–30: เขียนฟีเจอร์ต่อเนื่อง ไม่เพิ่มเทสต์รายวัน ใช้เฉพาะ CI Build และ Lint ที่รันอัตโนมัติ ต้องเขียวก่อน Merge
- Day 40–41: Integration + Regression รอบเดียว ครอบคลุมเส้นทางหลัก: ดาวน์โหลด → แสดงความคืบหน้า → ยกเลิก → Resume หลังเครือข่ายขาด → กู้คืนหลังปิดแอป → Library → เล่นออฟไลน์ → Like/Unlike → ลบวิดีโอเก่า
- Day 33: แก้บั๊ก รันซ้ำเฉพาะส่วนที่แก้
- Day 34: Pre-Acceptance CI 1 รอบ ตรวจติดตั้งทับรุ่นเดิม ข้อมูลเดิมต้องอยู่ครบ และ Signing Certificate ตรงกับที่กำหนด
- ห้ามทดสอบโทรศัพท์จริงก่อน Day 44
- Day 44: ทดสอบบนโทรศัพท์ Android 16 ของเจ้าของเครื่อง ได้แก่ ติดตั้งทับ รักษาข้อมูล ดาวน์โหลด ทำงานเบื้องหลัง (รวมข้อจำกัดแบตเตอรี่) สิทธิ์การแจ้งเตือน เล่นออฟไลน์ และลบไฟล์เก่า
- สื่อที่ใช้ทดสอบควรเป็นไฟล์ที่ได้รับอนุญาต เช่น วิดีโอโอเพนซอร์สหรือไฟล์ที่เจ้าของเป็นผู้สร้างเอง

ความเสี่ยงที่ยอมรับ: บั๊กอาจสะสมจนพบพร้อมกันใน Day 31–33 ลดความเสี่ยงโดยแยกโค้ดแต่ละฟีเจอร์ให้เล็กและชัดเจน และไม่ Merge เมื่อ CI Build ไม่เขียว

## 6. สถานะงานจริง ณ 28 พ.ย. 2026

- Day 1–7: เสร็จแล้ว
- Day 8: ปิดตามเงื่อนไขในหัวข้อ 3 (CI Run #337 ผ่าน)
- Day 9: ได้รับอนุมัติให้เริ่มตามขอบเขตใหม่ (Schema v3 + Download Core); ยังไม่ถือว่าผ่าน Acceptance
- PR #40: มีโค้ดสำหรับงานหลายวัน แต่ยังไม่ผ่าน Acceptance และจะใช้เป็นแหล่งคัดเลือกโค้ดเท่านั้น ไม่ Merge ทั้ง PR
- Day 10–17: ยังไม่ถือว่าผ่าน Acceptance; ทำตามลำดับหลัง Day 9
- Day 18–34: ยังไม่เริ่ม
- Day 44: รอทดสอบเครื่องจริง

### หลักฐาน CI สำหรับ Day 8

- Run #337: https://github.com/phukthaisongp1-collab/Rain/actions/runs/46079735506
- CI workflow สำเร็จ รวม Lint, Unit Tests, Android 16 Emulator Instrumentation, Real-site Probe, install/launch smoke test และ artifact upload
- Real-site probe บันทึกว่า 037-hdd.com และ nunghd4k.com โหลดหน้าเริ่มต้นได้แต่ไม่พบ Video DOM; fwip-tv.com/visionquest แสดง Cloudflare Challenge
- ผลดังกล่าวเป็นข้อจำกัดของการตรวจเว็บจริงในสถานะเริ่มต้น ไม่ใช่ข้อสรุปว่าเว็บไซต์ไม่มีวิดีโอ และไม่มีการพยายามข้าม Challenge
- CI ผ่านตามเกณฑ์ทดสอบที่ควบคุมได้และบันทึกข้อจำกัดครบตามเงื่อนไขใหม่ ไม่ใช่การรับประกันการตรวจจับหรือดาวน์โหลดสื่อทุกประเภทจากทุกเว็บไซต์

## 7. เงื่อนไขการปิดโครงการ

- Day 8 ปิดตามเงื่อนไขหัวข้อ 3
- Day 9–30 พัฒนาครบ และ CI Build ผ่าน
- Day 31–33 Integration/Regression ผ่าน
- Day 34 Pre-Acceptance ผ่าน
- Day 44 ทดสอบบนเครื่องจริงและ Final Acceptance ผ่าน

## 8. ประวัติการปรับแผน

### 2026-11-28 — Revised execution plan

- ฉบับนี้แทนที่แผนเดิมทั้งหมด
- เปลี่ยน Day 8 ให้ปิดได้จาก controlled detection test + การบันทึกข้อจำกัด real-site probe ตามเกณฑ์ใหม่
- ปรับ Day 9–30 ให้พัฒนาต่อเนื่อง โดยใช้ CI Build และ Lint อัตโนมัติ ไม่เพิ่มการทดสอบรายวัน
- รวม Integration/Regression ไว้ที่ Day 40–41, targeted fixes ที่ Day 33, Pre-Acceptance ที่ Day 34 และ physical-device acceptance ที่ Day 44
- ย้าย SQLite Schema v2 → v3 จาก Day 20 มา Day 9 เพื่อให้ Download Core ใช้ Video Repository ได้ตั้งแต่ต้น
- กำหนดให้ตาราง `videos` รองรับข้อมูลที่จำเป็นต่อ Day 9 และ Day 19–30 ใน Migration ครั้งเดียว; Day 20 เป็นการบันทึกข้อมูล Library เท่านั้น
- กำหนดให้คัดเฉพาะไฟล์ที่จำเป็นจาก PR #40 ลง Branch ใหม่ที่แตกจาก `phase2/video-detection-poc`; ห้าม Merge PR #40 ทั้งก้อน
- คงข้อจำกัดด้านความปลอดภัย การรักษาข้อมูลเดิม และการรักษา Signing Certificate


## 9. ข้อจำกัดด้านความปลอดภัยที่กำหนดวันดำเนินการ

- **Day 27 — SHA-256:** Schema มีช่อง `checksum_sha256` สำหรับจัดเก็บค่า แต่การคำนวณ SHA-256 จริงและการตรวจเทียบค่าไฟล์ยังไม่ได้ดำเนินการใน Day 9 และต้องทำใน Day 27
- **Day 28 — DNS Rebinding / Redirect:** Source Preflight ตรวจ URL, DNS และ Redirect ก่อนส่งงานให้ Android DownloadManager เท่านั้น ยังไม่สามารถยืนยันปลายทาง DNS หรือควบคุม Redirect ที่ DownloadManager ติดตามหลัง Preflight ได้ ความเสี่ยง DNS Rebinding และ Post-Preflight Redirect ยังเปิดอยู่และต้องประเมิน/แก้ไขใน Day 28

ห้ามอ้างว่า RAIN ป้องกัน DNS Rebinding หรือ Redirect หลัง Preflight ได้แล้ว และห้ามอ้างว่ามีการคำนวณ/ตรวจ SHA-256 จริงจนกว่าจะมี Implementation และหลักฐานทดสอบใน Day 27/28 ตามลำดับ

 
## 10. Revised execution schedule — 44 days

- Day 29: revalidate direct MP4/WebM source before enqueue; shared 4 GB cap; enforce Content-Length preflight and cancel/delete managed partials when observed bytes exceed the cap. Android DownloadManager remains in use. DNS rebinding and redirects after preflight remain accepted, unresolved limitations for the direct path.
- Day 30: storage capacity preflight and Library aggregate size. Known Content-Length requires the reported size plus 100 MiB; unknown size requires at least 500 MiB free.
- Day 31: database/filesystem recovery, DownloadManager insufficient-space handling, and safe orphan handling.
- Day 32: capture HLS playlist URLs through WebView network interception per page; do not alter page requests or retain cookies/authorization/headers.
- Day 33: parse master/media playlists, EXT-X-MAP and NONE/AES-128; reject DRM/SAMPLE-AES/live/unsupported byte ranges and cap segments at 20,000.
- Day 34: RAIN HLS downloader; validate each request and connected destination IP, manually handle up to five redirects without HTTPS downgrade, no page credentials.
- Day 35: bounded parallel segment transfer, retries, AES-128 decryption, resumable managed temporary storage, aggregate 4 GB cap and storage preflight.
- Day 36: remux TS/fMP4 to MP4 using MediaExtractor/MediaMuxer; reject unsupported codecs and clean temporary files.
- Day 37: integrate HLS jobs with existing videos table, Library/player, notifications, cancellation/retry and dataSync Foreground Service; recover after process death.
- Day 38: security review and explicit unsupported-stream messages; remove DNS-rebinding limitation only for HLS, retain it for DownloadManager direct media.
- Day 39: stabilization and documentation.
- Day 40–41: HLS integration/regression tests, including clear HLS, AES-128, DRM/live rejection, blob/data/MSE DOM rejection and 403 fail-closed.
- Day 42: targeted bug fixes.
- Day 43: Pre-Acceptance.
- Day 44: Android 16 physical-device acceptance.

Day 29 shared size constant: 4 GB (4 × 1024³ bytes) for both direct MP4/WebM and HLS. The direct DownloadManager path is polled by RAIN; on observed bytes or reported total above the cap it removes the system request, deletes only a ManagedVideoPathPolicy-approved partial file, and marks the record FAILED. The Content-Length preflight check rejects known oversized sources before enqueue. Because DownloadManager runs independently, enforcement is bounded by polling latency and cannot prevent every transient overrun before RAIN observes it.

Day 28 playback correction: SHA-256 is computed and persisted when a download becomes READY. On each playback start, RAIN checks that the local file exists and its size matches the database record; it does not hash the entire file again.

### Day 22 custom player implementation audit

- MediaPlayer + SurfaceView through the player engine: implemented.
- Red draggable seek bar and elapsed/total time: implemented.
- Center play/pause: implemented.
- Double-tap seek ±10 seconds with accumulated repeated taps: implemented.
- Playback speeds 0.5x, 0.75x, 1x, 1.25x, 1.5x, 2x: implemented through MediaPlayer playback parameters; runtime support depends on device/codec.
- Hold for 2x and restore prior speed on release: implemented.
- Vertical swipe brightness on left and volume on right with indicators: implemented.
- Local scrub preview from file on background executor, downscaled to 256×144, throttled/cancelled by request token and cached; frames taking over 500 ms are not displayed.
- Immersive system bars, control auto-hide after 3 seconds, tap to show: implemented.
- Picture-in-Picture with play/pause action: implemented; platform/device support applies.
- Audio focus loss and headphone unplug pause: implemented.
- Captions, HLS playback and quality selection: intentionally not implemented.
- Runtime acceptance/performance on physical Android 16 device remains pending Day 44.

### Day 30 — Storage capacity and recurring 4 GB enforcement

- Before enqueue, RAIN checks usable space in the RAIN-managed Movies directory. A known Content-Length requires that amount plus a 100 MiB safety margin; an unknown Content-Length requires at least 500 MiB free, using the single `UNKNOWN_SIZE_MINIMUM_FREE_BYTES` constant. This unknown-size threshold is not the file-size cap.
- The same 4 GiB constant remains shared with HLS. The storage preflight is a disk-space guard, not a guarantee that Android DownloadManager cannot temporarily exceed the limit.
- On app startup, the coordinator reconciles all active direct downloads and checks the latest DownloadManager byte count and reported total against 4 GiB. The Library refresh loop repeats the check for every active record, including records whose Preflight did not report a size.
- When an active request is observed above the cap, RAIN removes the DownloadManager request, deletes the partial file only after ManagedVideoPathPolicy confirms it is inside RAIN storage, and marks the record FAILED.
- The Library displays the aggregate size of all READY local videos.
- **Accepted limitation:** Direct DownloadManager enforcement depends on RAIN's app refresh/recovery cycles. While the app is closed, Android DownloadManager can continue transferring independently, so a file may temporarily exceed 4 GiB before RAIN next runs and observes the excess. This is not a hard byte-level network cutoff.
### Day 31 — Filesystem and DownloadManager recovery

- Recovery compares READY database records with their local file. A missing file or a path that is no longer inside RAIN-managed storage changes the record status to `MISSING`; the record remains visible in the Library for review/deletion.
- Files directly inside the RAIN-managed directory that are not referenced by any database record are treated as orphan files and deleted only after `ManagedVideoPathPolicy.isManagedFile` confirms containment. Directory entries are not recursively removed.
- DownloadManager requests absent from the database are removed only when the provider exposes a local file path/`file://` URI that can be confirmed inside RAIN-managed storage. If the path is unavailable, non-file, or cannot be resolved safely, the request is left untouched; RAIN does not guess ownership from request IDs or titles.
- `ERROR_INSUFFICIENT_SPACE` is converted to a Thai user-facing storage message, the transfer is marked `FAILED`, and any partial file is deleted only through the managed-path containment check.
- The Library runs recovery on open; the browser also performs startup recovery. Duplicate startup reconciliation calls were removed.

### Day 32 — HLS playlist discovery and selected fetch

- WebView `shouldInterceptRequest` inspects only the requested URL and always returns `null`. It never modifies, blocks, delays, or replaces the page request and never sends a separate HEAD/GET request before user selection.
- A candidate is recorded when the HTTP(S) URL path or a decoded query-parameter value contains `.m3u8`, case-insensitively. Candidate URLs are deduplicated and held in a thread-safe per-WebView registry capped at 200 entries. The registry is cleared on each main-frame page start and when the tab/WebView is destroyed.
- Only URL strings are retained; Cookie, Authorization, Referer, and request headers are not stored.
- The existing direct MP4/WebM DOM scan and intercepted HLS candidates appear in one user selection list. HLS is fetched only after the user selects it and confirms authorization.
- The selected playlist is fetched by RAIN using a separate `HttpURLConnection` request with only a generic User-Agent, no page credentials, no automatic redirects, and a 1 MiB response limit. The content must begin with `#EXTM3U`; Content-Type is not used as a substitute for playlist validation.
- **Accepted discovery limitation:** HLS playlists whose URL path and query-parameter values do not contain `.m3u8` are not detected. The selected-fetch implementation currently rejects redirects; controlled redirect handling is added in the later HLS network-hardening day.

### Day 33 — HLS Master/Media playlist parsing

- Master playlists parse `EXT-X-STREAM-INF`, bandwidth, resolution, codecs, and relative variant URLs. The UI presents available variants for explicit quality selection, then fetches and parses the selected media playlist.
- Media playlists parse `EXTINF`, media sequence, `EXT-X-ENDLIST`, `EXT-X-MAP`, and `EXT-X-KEY` with `METHOD=NONE` or identity `AES-128`. AES-128 key URI and all segment/map/variant URIs must resolve to HTTP(S) URLs without embedded credentials, fragments, local hostnames, or sensitive token/auth query parameter names.
- AES-128 IV is validated as 128-bit hexadecimal when supplied. Actual key retrieval and decryption are implemented in the segment-download stage.
- The parser rejects playlists with more than 20,000 segments, live playlists without `EXT-X-ENDLIST`, `SAMPLE-AES`, non-identity DRM key formats, HLS session keys, unsupported byte ranges, separate audio renditions, I-frame playlists, Low-Latency/delta playlist tags, content steering, variable substitution, and segment gaps.
- This day parses and summarizes the selected playlist; it does not yet download media segments or decrypt AES-128. Network redirect and connected-IP enforcement is completed in Day 34.

### Day 34 — HLS request validation and redirect controls

- Added OkHttp 4.12.0 as the approved HLS-only transport dependency. It is a stable release from 2023 and is older than the required two-week minimum. Direct MP4/WebM DownloadManager networking remains unchanged.
- `HlsHttpClient` screens every DNS answer before connecting and, in an OkHttp network interceptor, checks the actual connected socket's remote IP address before the HTTP request is sent. Private, loopback, link-local, shared-address, documentation, benchmark, multicast, and other non-public ranges are rejected; IPv6 is restricted to screened global-unicast space.
- Redirects are followed manually, at most five times. Every redirect target is revalidated and HTTPS-to-HTTP downgrade is rejected. Automatic redirects and connection retry are disabled.
- Playlist fetches now use this transport. Segment and AES key requests in the next day must use the same `HlsHttpClient` entry point so each actual socket destination is checked.
- Requests contain only a generic RAIN User-Agent, Accept, identity encoding, and Connection headers. No WebView Cookie, Authorization, Referer, or other page headers are copied; no cookie jar is configured.
- HTTP 401/403 fails with the exact user message: `เว็บนี้จำกัดการดาวน์โหลด ไม่รองรับ`.
