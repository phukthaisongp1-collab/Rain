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

## 2. แผนดำเนินงาน 44 วัน (ฉบับปัจจุบัน)

| วัน | งานที่ต้องดำเนินการ | สถานะ |
|---|---|---|
| 1–7 | WebView/Media callbacks, Download Policy, Storage, HTML5 Video detection, ad signals, Direct MP4 foundation | เสร็จตามเกณฑ์ Day 8 ที่ปรับปรุง |
| 8 | Detection feasibility gate และบันทึกข้อจำกัดเว็บไซต์จริง | ปิดตามเงื่อนไขใหม่ |
| 9 | Download Core Foundation และ SQLite Schema v3 แบบ additive-only | ปิดแล้ว |
| 10–18 | Direct MP4/WebM detection, validation, DownloadManager lifecycle, notifications และ recovery | ปิดแล้ว |
| 19–31 | Video Library, offline player, metadata, Like/Unlike, expiry, checksum, storage cap และ DownloadManager recovery | ปิดแล้ว |
| 32 | ตรวจจับ HLS จาก URL ใน shouldInterceptRequest และรวมรายการกับ DOM scan | ปิดแล้ว — CI Push/PR ผ่าน |
| 33 | Parse Master/Media playlist และปฏิเสธรูปแบบที่ไม่รองรับ | ปิดแล้ว — CI Push/PR ผ่าน |
| 34 | HLS transport, DNS/connected-IP validation และ redirect controls | ปิดแล้ว — CI Push/PR ผ่าน |
| 35 | ดาวน์โหลด segments, AES-128, retry และ resumable chunks | ปิดแล้ว — CI Push/PR ผ่าน |
| 36 | ประกอบ TS/fMP4, remux MP4 และตรวจสอบ output | ปิดแล้ว — CI Push/PR ผ่าน |
| 37 | HLS lifecycle, foreground service, Library, notifications และ process recovery | ปิดแล้ว — CI Push/PR ผ่าน |
| 38 | Security review และ unsupported-source behavior | ปิดแล้ว — CI Push/PR ผ่าน |
| 39 | Stabilization, ตรวจความสอดคล้องของเอกสาร และ Pre-Regression handoff | เสร็จ — Push/PR CI ผ่าน |
| 40–41 | Integration + Regression รอบเดียว ครอบคลุม Direct/HLS, lifecycle, recovery, security และ regression ของ browser/download เดิม | เสร็จ — Regression suite ผ่านใน CI รอบรวมเดียว (Push/PR) |
| 42 | แก้ไขเฉพาะปัญหาที่พบจาก Day 40–41 และรันทดสอบซ้ำเฉพาะส่วนที่แก้ | เสร็จ — ตรวจผล regression แล้วไม่พบ defect ที่ยืนยันได้ จึงไม่มี targeted code fix เพิ่ม |
| 43 | Pre-Acceptance CI, ตรวจหลักฐาน build/lint, manifest, schema, signing และเอกสารข้อจำกัด | เสร็จ — Static audit และ CI รอบรวม Push/PR ผ่าน; ตรวจ signing APK จริงใน Day44 |
| 44 | ทดสอบบนโทรศัพท์ Android 16 เครื่องจริงและ Final Acceptance | รอทดสอบ |

กติกา CI: ทุกวันต้องให้ Push และ PR CI ผ่านทั้งคู่ก่อนเริ่มวันถัดไป; จำกัดรอบซ่อม CI ไม่เกิน 2 รอบต่อวัน; PR #45 คงสถานะ Draft และห้าม Merge จนกว่าจะได้รับอนุมัติอย่างชัดเจน

### 2.4 ข้อจำกัดของ Detection Hook ขั้นต่ำ

- รองรับเฉพาะ URL วิดีโอ MP4/WebM แบบ Direct ที่เปิดเผยจากองค์ประกอบ `<video>` หรือ `<source>` และต้องผ่านการตรวจ MIME/container จาก Source Validator ก่อนดาวน์โหลด
- ไม่มีการแยกโฆษณาอัตโนมัติ และไม่รับประกันว่าวิดีโอที่ตรวจพบเป็นเนื้อหาหลักของหน้า
- ไม่รองรับ `blob:`, `data:`, Media Source Extensions (MSE), stream segments หรือแหล่งที่ไม่สามารถตรวจสอบ URL โดยตรงได้
- ไม่รับประกันการตรวจจับหรือดาวน์โหลดได้จากทุกเว็บไซต์ โดยการตรวจจับเกิดขึ้นเมื่อผู้ใช้สั่งจากเมนูเท่านั้น ไม่มี Popup อัตโนมัติเมื่อพบหรือเล่นวิดีโอ

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

### 6.1 ตารางความคืบหน้างานดาวน์โหลดวิดีโอออฟไลน์

| ช่วงงาน | ขอบเขต | สถานะจริง |
|---|---|---|
| Day 1–8 | Detection foundation, feasibility gate และข้อจำกัด real-site | เสร็จตามเกณฑ์ที่อนุมัติ |
| Day 9 | Download Core และ SQLite Schema v3 แบบ additive-only | เสร็จ; ใช้ตาราง `videos` เดิม ไม่มี Migration เพิ่มใน HLS |
| Day 10–18 | Direct MP4/WebM detection, validation, DownloadManager, notifications และ recovery | เสร็จ; CI ผ่านตาม Gate ของช่วงงาน |
| Day 19–21 | Video Library, metadata, search, thumbnail และรายละเอียดรายการ | เสร็จ |
| Day 22 | Custom offline player: seek, double-tap ±10s, speed, hold 2x, brightness/volume gestures, scrub preview, fullscreen, auto-hide และ PiP | Implemented; รอ runtime acceptance Day 40–44 |
| Day 23–28 | Playback validation, resume position, Like/Unlike, expiry, checksum และ managed-file cleanup | เสร็จ; playback ตรวจ existence/size ไม่ hash ทั้งไฟล์ซ้ำทุกครั้ง |
| Day 29–31 | Shared 4 GiB cap, storage preflight/recovery และ DownloadManager orphan protection | เสร็จ; Direct DownloadManager ยังมีข้อจำกัด polling และ post-preflight DNS/redirect ตามที่ยอมรับ |
| Day 32 | HLS URL discovery จาก `shouldInterceptRequest` และรวม DOM candidates | เสร็จ; Push/PR CI ผ่าน |
| Day 33 | HLS Master/Media parser และ fail-closed สำหรับรูปแบบที่ไม่รองรับ | เสร็จ; Push/PR CI ผ่าน |
| Day 34 | HLS-only OkHttp transport, DNS/connected socket IP checks และ redirect validation | เสร็จ; Push/PR CI ผ่าน |
| Day 35 | Parallel segment transfer, AES-128, retries, resume และ aggregate cap | เสร็จ; Push/PR CI ผ่าน |
| Day 36 | TS/fMP4 assembly, MediaExtractor/MediaMuxer remux และ output validation | เสร็จ; Push/PR CI ผ่าน |
| Day 37 | HLS repository lifecycle, dataSync Foreground Service, Library, notifications และ recovery | เสร็จ; Push/PR CI ผ่าน |
| Day 38 | HLS security review และข้อความปฏิเสธ DRM/token/login/Cloudflare/DASH/live | เสร็จ; Push/PR CI ผ่าน |
| Day 39 | Stabilization และปรับ execution plan/ข้อจำกัดให้ตรงโค้ดจริง | เสร็จ — Push/PR CI ผ่าน |
| Day 40–41 | Integration + Regression รอบเดียว รวม Direct/HLS และ browser/download regression | เสร็จ — เพิ่ม HlsOfflineRegressionTest ครอบคลุม parser, URL policy/registry, managed paths, container signature และ VideoRepository lifecycle; CI รอบเดียวผ่านทั้ง Push/PR |
| Day 42 | Targeted fixes จากผล Day 40–41 เท่านั้น | เสร็จ — ผล Regression ไม่พบ defect ที่ต้องแก้ จึงไม่มีการเปลี่ยน production code เพิ่ม |
| Day 43 | Pre-Acceptance CI, manifest/schema/signing/docs audit | เสร็จ — Static audit, lint, unit tests และ debug build ผ่าน; Release APK signing fingerprint ยังต้องตรวจใน Day44 |
| Day 44 | ทดสอบบนโทรศัพท์ Android 16 เครื่องจริงและ Final Acceptance | ยังไม่เริ่ม |

### 6.2 หลักฐาน CI ล่าสุด

- Day 40–43 combined commit: `86caccda01f42261e3487bdb24bded52b31e898d`
- Push #46135264509: https://github.com/phukthaisongp1-collab/Rain/actions/runs/46135264509 — **Success**
- PR #46135269278: https://github.com/phukthaisongp1-collab/Rain/actions/runs/46135269278 — **Success**
- Job `Android Lint and Debug Build`: Success; `lint`, `testDebugUnitTest`, `assembleDebug` ผ่าน และอัปโหลด Unit Test Reports แล้ว
- PR #45: Draft, ยังไม่ Merge
- Day 40–43 ปิดแล้วตามขอบเขต CI/Static Pre-Acceptance; ยังไม่ใช่ Final Acceptance บนอุปกรณ์จริง

### หลักฐาน CI สำหรับ Day 8

- Run #337: https://github.com/phukthaisongp1-collab/Rain/actions/runs/46079735506
- CI workflow สำเร็จ รวม Lint, Unit Tests, Android 16 Emulator Instrumentation, Real-site Probe, install/launch smoke test และ artifact upload
- Real-site probe บันทึกว่า 037-hdd.com และ nunghd4k.com โหลดหน้าเริ่มต้นได้แต่ไม่พบ Video DOM; fwip-tv.com/visionquest แสดง Cloudflare Challenge
- ผลดังกล่าวเป็นข้อจำกัดของการตรวจเว็บจริงในสถานะเริ่มต้น ไม่ใช่ข้อสรุปว่าเว็บไซต์ไม่มีวิดีโอ และไม่มีการพยายามข้าม Challenge
- CI ผ่านตามเกณฑ์ทดสอบที่ควบคุมได้และบันทึกข้อจำกัดครบตามเงื่อนไขใหม่ ไม่ใช่การรับประกันการตรวจจับหรือดาวน์โหลดสื่อทุกประเภทจากทุกเว็บไซต์

### 6.3 งานรวม Day 40–43 และ Pre-Acceptance Checklist

ดำเนิน Day 40–43 เป็นชุดงานเดียวและใช้ CI รอบรวมเพียงครั้งเดียวตามคำสั่งเจ้าของโครงการ ผล Push และ PR ของ commit เดียวกันผ่านทั้งคู่แล้ว จึงปิดช่วงงานนี้ได้ตามขอบเขตที่อนุมัติ

**Regression suite ที่เพิ่ม:** `app/src/test/java/com/rain/browser/downloads/HlsOfflineRegressionTest.kt`

- Master playlist: variants, bandwidth/resolution, codecs และ relative URL resolution
- Media playlist: media sequence, segment duration, ENDLIST, AES-128 key/IV และ EXT-X-MAP
- Fail-closed: live playlist, SAMPLE-AES/DRM key format, byte-range, invalid IV, separate audio rendition, Low-Latency parts และเกิน 20,000 segments
- URL policy: credential/token/local-network rejection และ HTTP(S) candidate detection จาก path/query โดยไม่จับ MP4 หรือ blob URL
- Registry: deduplication, maximum entries และ clear เมื่อเปลี่ยนหน้า
- File safety: managed path containment และ MP4/WebM container signature
- Repository lifecycle: HLS queue/download/verify/READY, progress แบบ monotonic, shared 4 GiB cap, checksum, Like/Unlike/expiry, playback resume/completion และ cancel/retry
- Migration regression เดิมยังคงอยู่ใน `StorageMigrationTest` เพื่อยืนยันข้อมูล bookmarks/history/resume/settings และการสร้าง videos table

**Static Pre-Acceptance audit:**

- Application ID ยังคงเป็น `com.rain.browser`; compile/target SDK 36 และ Java toolchain ตาม baseline
- Manifest มี HLS service แบบ `exported=false` และ `foregroundServiceType=dataSync`; permissions Foreground Service ที่อนุมัติยังคงเป็น `FOREGROUND_SERVICE` และ `FOREGROUND_SERVICE_DATA_SYNC`
- Database ยังคง Schema Version 3; HLS ใช้ `videos` table เดิม ไม่มี migration/table เพิ่ม และ migration เดิมเป็น additive-only
- Release signing configuration ยังคงรับค่าจาก environment variables; fingerprint ที่ต้องรักษายังคงบันทึกไว้ในเอกสาร
- Workflow CI ปัจจุบันรัน `lint`, `testDebugUnitTest` และ `assembleDebug`; ไม่ได้สร้าง Release APK ด้วย signing secrets ดังนั้นยังไม่อ้างว่าตรวจลายเซ็น APK จริงแล้ว ต้องตรวจ artifact/signature ใน Day44 ก่อน Final Acceptance
- ไม่มีการทดสอบบนโทรศัพท์จริงในช่วงนี้; การทำงานเบื้องหลัง, codec/device compatibility, notification behavior และการติดตั้งทับบนเครื่องจริงยังเป็น Day44
- ไม่เพิ่ม dependency, permission, Media3, schema migration, network credential forwarding หรือ DRM/token bypass

**ผลปิดงาน:** CI รอบรวมผ่านทั้ง Push และ PR แล้วทั้งคู่; Day 40–43 ปิดตามขอบเขตงานที่อนุมัติ โดย Day44 ยังต้องตรวจ Release APK signing จริงและทดสอบบนโทรศัพท์ Android 16

## 7. เงื่อนไขการปิดโครงการ

- Day 1–39: งานตามแผน 44 วันครบตามขอบเขต และ Push/PR CI ผ่านทุก Gate ของวันนั้น
- Day 40–41: Integration + Regression ผ่านสำหรับ Direct MP4/WebM, HLS clear/AES-128, Library/player, cancellation/retry/resume, process recovery, storage cap, notification/Foreground Service และ regression ของ browser/download เดิม
- Day 42: ปัญหาที่พบจาก Day 40–41 ได้รับการแก้ไขและทดสอบซ้ำเฉพาะส่วนที่เปลี่ยน
- Day 43: Pre-Acceptance ผ่าน รวม CI, manifest/permissions, schema/data preservation, signing identity และเอกสารข้อจำกัด
- Day 44: ทดสอบบนอุปกรณ์ Android 16 จริงของเจ้าของโครงการและ Final Acceptance ผ่าน
- PR #45 ต้องคง Draft และห้าม Merge จนกว่าเจ้าของโครงการจะอนุมัติอย่างชัดเจน

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
- The selected playlist is fetched by RAIN through the HLS-only `HlsHttpClient` transport, with a generic User-Agent, no page credentials, manual validated redirects, actual connected-IP checks, and a 1 MiB response limit. The content must begin with `#EXTM3U`; Content-Type is not used as a substitute for playlist validation.
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
- Playlist, segment, initialization-map, and AES key requests all use this transport, so each actual socket destination is checked.
- Requests contain only a generic RAIN User-Agent, Accept, identity encoding, and Connection headers. No WebView Cookie, Authorization, Referer, or other page headers are copied; no cookie jar is configured.
- HTTP 401/403 fails with the exact user message: `เว็บนี้จำกัดการดาวน์โหลด ไม่รองรับ`.

### Day 35 — HLS segment transfer, AES-128, and resumable chunks

- Added a streaming-to-file HLS transport entry point on the Day 34 HlsHttpClient. It reuses DNS screening, actual connected-socket IP checks, manual redirect validation, HTTPS downgrade rejection, credential-free request headers, and the exact HTTP 401/403 failure message. Media bodies are streamed to temporary files instead of being buffered in memory.
- Media segments are downloaded with a bounded four-worker executor (configurable only from 3–4 workers). Each segment receives up to three attempts with exponential backoff. A cancellation token stops queued work and is checked during streaming; an in-flight socket is also bounded by the transport timeout.
- Identity AES-128 keys are fetched through HlsHttpClient, must be exactly 16 bytes, and are cached only in memory. Segments and encrypted initialization maps are decrypted with AES-CBC/PKCS padding. An explicit IV is used when supplied; otherwise the media sequence number is encoded as a 128-bit big-endian IV.
- Segment and initialization-map chunks are written under Movies/RAIN/hls-<record-id>. A per-playlist fingerprint and per-chunk SHA-256/byte-count metadata allow verified completed chunks to be skipped on retry. The metadata stores hashes and counts, not source URLs, cookies, keys, or authorization values. Incomplete .part files are discarded on retry.
- The existing shared VideoDownloadCoordinator.MAX_VIDEO_SIZE_BYTES constant enforces the aggregate 4 GiB cap across downloaded segment and initialization-map bytes, including concurrent workers. Unknown aggregate size uses the approved 500 MiB minimum free-space precheck. Progress reports completed segments and transferred bytes.
- This day provides the HLS segment-transfer engine and resumable chunk output. Integration with the videos record lifecycle, foreground service, notifications, Library and playback is reserved for Day 37; final concatenation/remux and container verification are Day 36.


### Day 36 — HLS MP4 assembly, remux, and output validation

- Added `HlsMediaRemuxer` using Android `MediaExtractor` and `MediaMuxer`; no Media3 dependency was added.
- Completed MPEG-TS playlists are assembled in playlist order into one temporary transport-stream input. Fragmented MP4 playlists are assembled as the initialization map followed by media fragments.
- The implementation accepts a single stable initialization map for the entire fragmented-MP4 playlist. Playlists that change maps, mix mapped and unmapped segments, or contain multiple map configurations are rejected rather than risking a corrupt output.
- The platform extractor enumerates audio/video tracks, rejects unsupported track types and codec/container combinations, and copies samples into an MP4 muxer while validating non-negative, non-decreasing timestamps per track.
- The output is limited by the shared 4 GiB constant, must have an MP4 `ftyp` signature, and must pass `VideoMediaInspector` duration and first-frame decoding checks before it is returned.
- Unsupported codec/media combinations fail with a clear Thai message: `ไม่รองรับ codec สำหรับการรวมไฟล์ออฟไลน์` (or the more general MP4 remux compatibility message when the platform cannot start the muxer).
- Temporary assembled input is removed in a `finally` block. After successful validation and finalization, the HLS segment/map working directory is removed only through `HlsSegmentDownloader.deleteTemporaryFiles`, which applies the managed-path policy. Failed remuxes retain verified segment chunks for retry and remove only managed output partials.
- Runtime codec compatibility and representative clear/AES-128 TS/fMP4 media validation remain part of the planned Day 40–41 regression suite; physical-device acceptance remains Day 44.

### Day 37 — HLS lifecycle, foreground service, Library, notifications, and recovery

- HLS downloads are persisted in the existing `videos` table. The existing `mime_type` field uses the internal marker `application/vnd.apple.mpegurl`; no database schema migration or new table is introduced. The selected media-playlist URL is persisted in the existing `url` field, and the user's authorization confirmation is stored through the existing repository flow.
- The HLS confirmation dialog now starts the transfer only after the user explicitly selects a media playlist and confirms download. A master playlist still requires a quality variant selection before a record is created.
- Added `HlsDownloadService` as an Android foreground service with the approved `dataSync` type. It uses the existing HLS-only checked HTTP client, segment downloader, and MP4 remuxer. Direct MP4/WebM transfers remain owned by Android DownloadManager.
- HLS state transitions are persisted as QUEUED → DOWNLOADING → VERIFYING → READY, or FAILED/CANCELED/FAILED_CORRUPTED. Segment progress is persisted monotonically in `bytes_downloaded`; the final MP4 size, duration, downloaded timestamp, and 30-day expiry are written by the existing `markReady` lifecycle. The completed MP4 receives the existing SHA-256 checksum.
- The Library now shows HLS active and terminal records, displays persisted progress, offers cancel for queued/downloading HLS jobs, and offers retry for failed/canceled items. HLS retry reuses the existing per-record resumable chunk directory; direct-video retry continues through the existing DownloadManager coordinator.
- Foreground progress notifications include a cancel-all action. Completion/failure notifications open the Video Library. Final notifications are best-effort when Android notification permission is unavailable; the foreground-service notification remains mandatory while the service runs.
- The service uses `START_STICKY` recovery and the browser startup path also checks for active persisted HLS records. Interrupted downloads resume from verified chunks. A finalized MP4 left between remux and database finalization is validated and adopted on recovery; invalid managed output is removed safely. DownloadManager reconciliation explicitly excludes HLS records and protects their expected final output path from orphan cleanup.
- The service declares only the already-approved `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_DATA_SYNC` permissions and `foregroundServiceType="dataSync"`. No Media3, WorkManager, Cookie/Authorization/Referer forwarding, DRM support, or database migration was added.
- Runtime service scheduling, notification permission behavior, cancellation races, process-death recovery, and HLS media fixtures remain part of Days 40–41 regression validation. Physical-device acceptance remains Day 44.

### Day 38 — HLS security review and unsupported-source behavior

- Security review confirms every HLS playlist, key, initialization-map, segment, and redirect request uses `HlsHttpClient`. Its DNS resolver rejects non-public answers before connection, and its OkHttp network interceptor checks the actual connected socket IP before the request is sent. System proxies are explicitly disabled so the checked socket is the media origin, not an intermediary. Connections are not reused; automatic redirects and connection retries are disabled.
- HLS redirects are followed manually for at most five hops. Each target is revalidated for HTTP(S), public destination IP, embedded credentials, local hostnames, and sensitive query parameters. HTTPS-to-HTTP downgrade is rejected.
- The HLS transport sends only a generic RAIN User-Agent, Accept, identity encoding, and Connection: close. No CookieJar is configured. No WebView Cookie, Authorization, Referer, or page request headers are read, persisted, or forwarded.
- Token/signed URL query names are rejected before a playlist, key, or segment request is made. The filter includes common token suffixes, auth, JWT, API-key, CloudFront, AWS-signature, and HLS token parameter names. A URL whose credential is encoded in an unrecognized opaque path or arbitrary query value cannot be reliably classified; RAIN still does not attempt to obtain or bypass that credential.
- HLS media is limited to clear streams and identity AES-128 with an independently fetched 16-byte key. AES keys remain in memory only. SAMPLE-AES, non-identity KEYFORMAT, session keys, DRM, live playlists without ENDLIST, DASH/MPD, unsupported byte ranges, and unsupported playlist structures are rejected.
- Unsupported response classification is fail-closed and does not trigger another request. User-facing messages include:
  - HTTP 401/403: `เว็บนี้จำกัดการดาวน์โหลด ไม่รองรับ`
  - Token/auth/signed query URL: `ไม่รองรับ URL ที่มี Token หรือข้อมูลยืนยันตัวตน`
  - DASH: `ไม่รองรับ DASH (MPD) สำหรับการดาวน์โหลดวิดีโอออฟไลน์`
  - Login HTML: `เว็บนี้ต้องเข้าสู่ระบบ RAIN ไม่รองรับการใช้ Cookie หรือข้อมูลเข้าสู่ระบบจากเว็บไซต์`
  - Cloudflare challenge: `เว็บนี้แสดง Cloudflare Challenge หรือหน้าป้องกันอัตโนมัติ RAIN ไม่สามารถข้ามได้`
  - Live HLS: `ไม่รองรับ HLS Live Playlist ที่ไม่มี EXT-X-ENDLIST`
  - DRM/SAMPLE-AES: `ไม่รองรับ HLS SAMPLE-AES หรือ DRM` / `ไม่รองรับ HLS Session Key หรือ DRM`
  - Invalid non-HLS response: `URL นี้ไม่ใช่ HLS Playlist ที่ถูกต้อง`
- The accepted DNS-rebinding limitation is removed for the HLS transport path by validating DNS answers and the actual connected socket for every new request, including redirects. This does not change the direct MP4/WebM Android DownloadManager path: its post-preflight DNS/redirect limitation remains explicitly accepted and documented.
- Cloudflare challenges, login pages, paywalls, DRM, and authentication requirements are not bypassed. The HLS URL discovery limitation remains: a playlist whose URL path and query values do not contain `.m3u8` is not discovered by the approved interception rule.
- No new permissions, dependencies, schema migrations, tests, or changes to the generic browser DownloadManager pipeline were introduced for this security review. Regression fixtures and negative security tests remain scheduled for Days 40–41.

### Day 39 — stabilization and Pre-Regression handoff

- Reconciled the execution schedule to the approved 44-day plan. Days 32–38 are recorded as completed only after both Push and PR CI passed; Day 39 is documentation/stabilization, Days 40–41 are the single planned Integration + Regression window, Day 42 is evidence-driven fixes, Day 43 is Pre-Acceptance, and Day 44 is physical-device acceptance.
- Corrected historical Day 32/34 transport notes so they describe the final implementation: all selected playlist, key, initialization-map, and segment network requests use the HLS-only `HlsHttpClient`, not the earlier `HttpURLConnection` prototype.
- No new database table, migration, permission, network credential flow, Media3 dependency, WorkManager dependency, or generic DownloadManager pipeline change is part of Day 39.
- HLS support is intentionally limited to finite VOD playlists with `EXT-X-ENDLIST`, clear media or identity AES-128, supported TS or one stable fMP4 initialization map, and codecs accepted by the Android platform extractor/muxer. Multiple or changing initialization maps, mixed mapped/unmapped segments, separate audio renditions, byte-range forms, SAMPLE-AES/DRM, live HLS, DASH/MPD, and unsupported codecs are not supported.
- HLS discovery remains URL-based: only HTTP(S) requests whose path or decoded query-parameter value contains `.m3u8` are candidates; no content-type probing or extra preselection request is made. Blob/MSE playback and URLs that hide the playlist endpoint remain outside the detection contract.
- Direct MP4/WebM continues to use Android DownloadManager. Its previously accepted DNS/redirect and app-closed 4 GiB polling-enforcement limitations remain; the stricter DNS answer and connected-socket validation applies only to the HLS-owned transport.
- The CI pipeline verifies Android lint and debug assembly, not actual codec compatibility, foreground-service timing, notification delivery, or real-world HLS media behavior. Those runtime and media-fixture checks are explicitly deferred to Days 40–41; physical Android 16 validation remains Day 44.
- PR #45 remains Draft and unmerged. No release signing, version bump, or physical-device acceptance is claimed by Day 39.
