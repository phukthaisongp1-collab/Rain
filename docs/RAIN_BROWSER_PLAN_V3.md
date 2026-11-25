# 🌧️ RAIN Browser — แผนพัฒนาและโครงสร้างโค้ด v3

> สถานะเอกสาร: Development Specification  
> เวอร์ชันแผน: v3  
> เวอร์ชันแอปเริ่มต้น: 0.1.0  
> Application ID: `com.rain.browser`  
> ชื่อผลิตภัณฑ์: `RAIN`

เอกสารนี้เป็นสเปกหลักสำหรับการพัฒนา RAIN Browser โดยให้ดำเนินงานตามเฟสและรอบย่อยที่กำหนด ตรวจสอบผลลัพธ์ทีละรอบ และไม่เริ่มรอบถัดไปจนกว่าจะได้รับการตรวจรับรอบปัจจุบัน

---

## 1. ภาพรวมและกติกาหลัก (Scope, Identity & Security)

### 1.1 เป้าหมายผลิตภัณฑ์

- ชื่อแอป: **RAIN**
- ประเภท: เบราว์เซอร์ที่เน้นความเป็นส่วนตัว ดูสื่อ/วิดีโอได้ลื่นไหล และดาวน์โหลดเก็บไว้ดูแบบออฟไลน์
- สถาปัตยกรรมเฟส 1: Vanilla Electron (ไม่ใช้ bundler) + `WebContentsView`
- แยก Data Layer ออกจาก UI อย่างเด็ดขาด
- เตรียม Hooks/Stubs สำหรับเฟส 2–4 ตามที่ระบุในเอกสารนี้

### 1.2 App Identity และการอัปเดตทับเวอร์ชันเดิม

กำหนดค่าต่อไปนี้ให้คงที่ทุกบิลด์:

- `name`
- `productName: "RAIN"`
- `appId: "com.rain.browser"`

เริ่มต้นที่ `version: "0.1.0"` และเพิ่มเลขเวอร์ชันทุกครั้งที่ออกบิลด์ใหม่ เพื่อรักษาตัวตนแอปและตำแหน่ง `userData` ไม่ให้เปลี่ยน ซึ่งช่วยป้องกันข้อมูลคลังออฟไลน์และข้อมูลในเครื่องสูญหายจากการเปลี่ยน identity ของแอป

### 1.3 ธีมหลัก

- Dark Slate: `#0F172A`
- Rain Blue: `#38BDF8`
- Deep Navy: `#1E293B`

### 1.4 ข้อกำหนดความปลอดภัย

ทุกหน้าต่างและทุก `WebContentsView` ต้องกำหนดค่าต่อไปนี้:

- `contextIsolation: true`
- `nodeIntegration: false`
- `sandbox: true`

### 1.5 ขอบเขตที่ไม่รองรับถาวร

- ไม่รองรับเว็บไซต์ที่ใช้ DRM เช่น Netflix และ Disney+
- ไม่มีบัญชีผู้ใช้
- ไม่มี Cloud Sync
- ไม่มีระบบ Extension จากภายนอก

---

## 2. แผนพัฒนาเฟส 1 — แบ่งตรวจรับ 4 รอบย่อย

ให้ทำงานตามลำดับ ก → ข → ค → ง โดยทำให้รอบปัจจุบันเสร็จ ตรวจสอบ และรายงานผลก่อนเริ่มรอบถัดไป

### รอบ (ก): โครง Repository + CI + หน้าต่างเปล่า

1. สร้างโครงสร้างโฟลเดอร์:
   - `main/`
   - `preload/`
   - `data/`
   - `renderer/`
   - โฟลเดอร์เปล่า `modules-phase2/`
   - โฟลเดอร์เปล่า `modules-phase3/`
2. สร้างไฟล์ Stub:
   - `player.html`
   - `library.html`
   - `view-preload.js` โดยมี `contextBridge.exposeInMainWorld('rainView', {})` เปล่า ๆ
3. ตั้งค่า `package.json`:
   - ล็อกเวอร์ชัน Electron ให้ชัดเจน
   - `name` คงที่
   - `productName: "RAIN"`
   - `appId: "com.rain.browser"`
   - `version: "0.1.0"`
4. สร้าง `.github/workflows/build.yml`:
   - ใช้ Node 20 LTS
   - Build แบบ unsigned fallback ได้เมื่อยังไม่มี Secrets
   - เมื่อมี Secrets สำหรับ signing ให้ดึงมาใช้เซ็นอัตโนมัติ
5. เขียน `main.js` ให้เปิดหน้าต่างเปล่าได้จริง พร้อม security flags ครบถ้วน

**เกณฑ์ตรวจรับรอบ (ก):** โครงสร้างและไฟล์ Stub อยู่ครบ, package identity ตรงตามข้อกำหนด, CI Build ได้ทั้งกรณีไม่มีและมี signing Secrets ตามเงื่อนไข, แอปเปิดหน้าต่างเปล่าได้จริง และตรวจไม่พบการตั้งค่าความปลอดภัยที่ขัดกับข้อกำหนด

### รอบ (ข): หน้าต่าง + แท็บ (WebContentsView) + Address Bar

1. สร้าง `window-manager.js` และ `tab-manager.js` แยกการจัดการ Shell UI ด้านบนออกจาก `WebContentsView` ที่แสดงเนื้อหาเว็บ
2. กำหนดความสูงแถบควบคุมเป็นค่าคงที่ในหน่วย DIP ของ Electron และใช้ `setBounds`
3. คำนวณและอัปเดต bounds ใหม่ทุกครั้งเมื่อ:
   - resize
   - maximize
   - unmaximize
   - เข้า fullscreen
   - ออกจาก fullscreen
4. ป้องกันหน้าเว็บใน `WebContentsView` บังเมนู HTML ของ Shell UI:
   - เมนู Dropdown, เมนู Bookmark และป๊อปอัปจากแถบ UI ต้องแสดงผลได้จริง
   - ใช้เมนู `WebContentsView` แยกอีกชั้น หรือย่อ bounds ของ content view ชั่วคราวระหว่างเปิดเมนู
5. ทำระบบ:
   - เปิดแท็บ
   - ปิดแท็บ
   - สลับแท็บ
   - Back
   - Forward
   - Reload
   - Home
   - Address Bar
6. การประมวลผล Address Bar:
   - ตรวจด้วย `new URL(input)` ก่อน
   - ถ้าเป็น URL ให้เปิด URL โดยตรง
   - ถ้าไม่ใช่ URL ให้ค้นหาด้วย Search Engine ที่เลือก
   - ค่าเริ่มต้นเป็น DuckDuckGo
   - รองรับการสลับไปใช้ Google

### รอบ (ค): DoH + กฎ Proxy/Whitelist + โหมดส่วนตัว

1. เรียก `app.configureHostResolver()` หลัง `app.whenReady()` เท่านั้น
2. รองรับ DoH:
   - Cloudflare
   - Google
   - NextDNS
3. กฎ DoH และ Proxy:
   - คง DoH ไว้ได้ ไม่จำเป็นต้องปิด DoH เมื่อเปิด Proxy
   - ใช้ `proxyBypassRules` แยกโดเมน Whitelist ให้เชื่อมต่อแบบ Direct
   - Whitelist:
     - `*.scb.co.th`
     - `*.kbank.com`
     - `*.go.th`
     - `*.gmail.com`
     - `*.outlook.com`
   - ปิด Ad-block เมื่อถึงเฟสที่เกี่ยวข้องตามแผน
4. Private Window:
   - คีย์ลัด `Ctrl+Shift+N`
   - ใช้ partition ที่ไม่มี prefix `persist:` เช่น `session.fromPartition('private-' + Date.now())`
   - ข้อมูล Session ต้องอยู่ใน RAM เท่านั้น
   - กำกับหน้าต่างด้วย `isPrivate: true`
5. เมื่อปิด Private Window:
   - Electron ไม่มี `session.destroy()`
   - เรียก `session.clearStorageData()` และ `session.clearCache()`
   - จากนั้นปล่อย reference ให้ Garbage Collector จัดการ
   - ต้องป้องกันข้อมูลส่วนตัวรั่วผ่าน:
     - Downloads
     - Favicon cache
     - ภาพย่อ New Tab
     - ประวัติการเข้าชม
     - ตำแหน่งดูวิดีโอค้าง
   - ข้อมูลที่เกิดจาก Private Window ต้องไม่ถูกบันทึกปะปนกับข้อมูลปกติ

### รอบ (ง): Data Layer + New Tab ธีมฝน + Settings

1. สร้าง Data Layer ใน `src/data/` แยกจาก UI โดยเด็ดขาด ให้ UI ติดต่อผ่าน `window.rainAPI` และ IPC เท่านั้น
2. ออกแบบ `storage.js` เป็น Interface กลาง รองรับ:
   - `get`
   - `set`
   - `query`
   - `delete`
3. Backend เฟส 1:
   - ใช้ JSON ใน `userData`
   - ไม่ใช้ `better-sqlite3` ในเฟส 1 เพื่อหลีกเลี่ยงปัญหา rebuild native module ใน CI
   - เขียนไฟล์ผ่าน temp file แล้ว rename
   - ใช้ debounce เพื่อลดความเสี่ยงไฟล์เสียเมื่อปิดแอปกะทันหัน
4. ขีดจำกัดข้อมูล:
   - History สูงสุด 5,000 รายการ
   - วิดีโอที่ดูค้างสูงสุด 10 รายการ
   - ข้ามการบันทึกทันทีเมื่อ `isPrivate: true`
   - Cleanup รายการวิดีโอที่ดูค้างและเกิน 30 วันแบบ async หลัง `app.whenReady()` และก่อนเปิดหน้าต่าง
5. สร้างหน้า New Tab ตามธีม:
   - `#0F172A`
   - `#38BDF8`
   - `#1E293B`
6. สร้างหน้า Settings แบบแท็บภายในแอป
7. ใช้ Vanilla JS และระบบแปลภาษาด้วย `data-i18n` เชื่อมกับ:
   - `th.json`
   - `en.json`
8. รองรับการสลับภาษาไทย/อังกฤษได้ทันที

---

## 3. กติกาความปลอดภัยและการตรวจโค้ดก่อนรับงาน

### 3.1 Preload ภายใต้ Sandbox

เมื่อใช้ `sandbox: true`:

- อนุญาตเฉพาะ `contextBridge` และ `ipcRenderer` ตามความจำเป็น
- ห้าม `require('fs')`
- ห้าม `require('path')`
- ห้ามเรียกโมดูลของโปรเจกต์โดยตรงจาก Preload

### 3.2 Security Audit

ตรวจสอบทุกไฟล์ก่อนส่งมอบ โดยต้องไม่พบ:

- `nodeIntegration: true`
- การตั้ง `contextIsolation: false`
- การตั้ง `sandbox: false` หรือการปิด Sandbox
- ช่องทาง IPC ที่เปิดให้ Renderer เรียกใช้ความสามารถเกินจำเป็น

### 3.3 การเตรียมโครงสร้างสำหรับเฟส 2–4

คงตำแหน่งและจุดเชื่อมต่อสำหรับความสามารถต่อไปนี้ไว้ตามแผน โดยยังไม่เพิ่มฟีเจอร์เหล่านี้ในเฟส 1 เว้นแต่ได้รับสเปกของเฟสนั้น:

- Ad-blocker: `@ghostery/adblocker-electron`
- Network Sniffer
- RAIN Player: `hls.js` + Plyr
- ระบบดาวน์โหลด 3 ชั้น
- ffmpeg ซึ่งคาดว่าจะเพิ่มขนาดแพ็กเกจประมาณ 50–80 MB เมื่อถึงเฟสที่เกี่ยวข้อง
- คลังออฟไลน์ 7 วัน
- Auto-Proxy
- Export/Import JSON
- เฟส 4:
  - Profile
  - Reader Mode
  - Command Palette (`Ctrl+K`)
  - Rain Stats

---

## 4. โครงสร้างไฟล์และโฟลเดอร์เป้าหมาย

```text
rain-browser/
├── .github/
│   └── workflows/
│       └── build.yml               # Node 20 LTS, Build & unsigned fallback (รองรับ Secrets เมื่อมี)
├── package.json                    # ล็อก Electron version, name, productName="RAIN", appId="com.rain.browser", version="0.1.0"
├── .gitignore
└── src/
    ├── main/
    │   ├── main.js                 # เริ่มต้นแอป, รอ whenReady() ก่อนเรียก DoH & async cleanup (30 วัน)
    │   ├── window-manager.js       # หน้าต่างหลัก, resize/maximize/fullscreen, Private Window (clearStorageData + clearCache)
    │   ├── tab-manager.js          # WebContentsView, DIP bounds, เมนูแยกชั้นไม่ให้เว็บทับ
    │   ├── network-manager.js      # DoH (หลัง ready), proxyBypassRules & Whitelist pattern
    │   ├── ipc-handlers.js         # ช่องทาง IPC กลาง
    │   ├── modules-phase2/         # โครงเปล่าเตรียมเฟส 2 (adblocker.js, stream-sniffer.js)
    │   └── modules-phase3/         # โครงเปล่าเตรียมเฟส 3 (download-engine.js, offline-library.js, proxy-rotator.js)
    ├── data/
    │   ├── storage.js              # Interface get/set/query/delete + temp-file/rename + debounce (JSON ใน userData)
    │   ├── settings-store.js       # ภาษา, Search Engine, DoH, Proxy/Bypass, Whitelist
    │   ├── history-store.js        # ประวัติสูงสุด 5,000 รายการ (ข้ามเมื่อ isPrivate = true)
    │   ├── bookmark-store.js       # บุ๊กมาร์ก
    │   └── resume-store.js         # วิดีโอดูค้างสูงสุด 10 รายการ (ข้ามเมื่อ isPrivate = true, ล้างเกิน 30 วัน)
    ├── preload/
    │   ├── shell-preload.js        # เฉพาะ contextBridge + ipcRenderer สำหรับ Shell UI
    │   └── view-preload.js         # Stub มี contextBridge.exposeInMainWorld('rainView', {}) เปล่าๆ สำหรับเฟส 1
    └── renderer/
        ├── assets/theme.css        # #0F172A, #38BDF8, #1E293B
        ├── shell/                  # index.html, shell.css, shell.js
        ├── pages/                  # newtab.html/js, settings.html/js, player.html (stub), library.html (stub)
        └── i18n/                   # th.json, en.json (ทำงานร่วมกับ data-i18n)
```

---

## 5. คำสั่งเริ่มต้นสำหรับ GPT — ทำเฉพาะรอบ (ก)

ให้นำข้อความต่อไปนี้ไปใช้เป็นคำสั่งเริ่มงาน:

> เริ่มทำ รอบ (ก) ของ RAIN Browser ตามแผน v3 ด้านบน โดยใช้ Vanilla Electron และ Node 20 LTS:
>
> - สร้างโครงสร้างโฟลเดอร์และไฟล์ Stub ทั้งหมดตามแผน (modules-phase2/, modules-phase3/, view-preload.js ที่มี contextBridge.exposeInMainWorld('rainView', {}) เปล่า ๆ, player.html, library.html)
> - เขียน package.json โดยล็อกเวอร์ชัน Electron ให้ชัดเจน และคง name, productName: "RAIN", appId: "com.rain.browser", version: "0.1.0"
> - เขียน .github/workflows/build.yml ใช้ Node 20 LTS ที่ Build ผ่านได้ทั้งแบบ unsigned fallback และแบบดึง Secrets มาเซ็นเมื่อมี
> - เขียน main.js เปิดหน้าต่างเปล่าได้จริง โดยตั้ง contextIsolation: true, nodeIntegration: false, และ sandbox: true
> - ทำเฉพาะรอบ (ก) ให้เสร็จและตรวจสอบความปลอดภัยก่อน แล้วค่อยรายงานผล
>
> ห้ามเริ่มรอบ (ข), (ค), หรือ (ง) จนกว่าจะได้รับการตรวจรับและคำสั่งให้ดำเนินการต่อ

---

## 6. กติกาการส่งมอบและตรวจรับงาน

สำหรับแต่ละรอบ ให้ผู้พัฒนา:

1. ทำเฉพาะขอบเขตงานของรอบที่ได้รับมอบหมาย
2. ไม่เปลี่ยนสถาปัตยกรรมหลัก ชื่อไฟล์ หรือตัวแปรสำคัญนอกเหนือจากสเปกโดยไม่ได้รับอนุมัติ
3. หากข้อกำหนดขัดแย้ง คลุมเครือ หรือขาดข้อมูลที่จำเป็น ให้หยุดและถามก่อนลงมือ
4. ส่งมอบโค้ดฉบับสมบูรณ์ในส่วนที่สร้างหรือแก้ไข พร้อมจุดเชื่อมต่อที่ใช้งานได้จริง
5. สรุปว่าส่วนใดทำแล้วตามข้อกำหนดใด
6. แจ้งผลการทดสอบ ข้อควรระวัง และค่าที่ต้องตั้งค่าเพิ่มเติม
7. รายงานสิ่งที่ยังไม่ได้ทำอย่างชัดเจน ห้ามอ้างว่าผ่านการทดสอบหากยังไม่ได้ทดสอบจริง

**ขอบเขตการอนุมัติ:** การบันทึกเอกสารแผน v3 นี้ลง Repository ไม่ถือเป็นการอนุมัติให้เริ่มเขียนโค้ดรอบ (ก) โดยอัตโนมัติ ต้องได้รับคำสั่งเริ่มงานแยกต่างหาก
