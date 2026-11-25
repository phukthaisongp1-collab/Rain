# 🌧️ RAIN Browser — แผนพัฒนาและโครงสร้างโค้ด v3.1

> สถานะ: Development Specification — ฉบับหลักแทน v3  
> เวอร์ชันแผน: **v3.1**  
> เวอร์ชันแอปเริ่มต้น: **0.1.0**  
> Android applicationId: **com.rain.browser**  
> แพลตฟอร์ม: **Android เท่านั้น**

เอกสารนี้รวมแผนเดิมและข้อกำหนดเพิ่มเติมจากเจ้าของโครงการ โดยเปลี่ยนสถาปัตยกรรมจาก Electron เป็น Android อย่างชัดเจน การอัปเดตเอกสารนี้ไม่ใช่การอนุมัติให้เริ่มเขียนโค้ด ต้องได้รับคำสั่งเริ่มงานแยกต่างหาก

---

## 1. เป้าหมายและกติกาหลัก

### 1.1 เป้าหมายผลิตภัณฑ์
RAIN คือเว็บเบราว์เซอร์ Android ที่เน้นความเป็นส่วนตัว การเปิดเว็บไซต์และดูวิดีโออย่างลื่นไหล การดาวน์โหลดเพื่อใช้งานออฟไลน์ และมี Desktop Mode สำหรับเปิดเว็บไซต์ในรูปแบบเดสก์ท็อปบนโทรศัพท์

### 1.1.1 เป้าหมายความสามารถระดับผลิตภัณฑ์ (ยืนยันโดยเจ้าของโครงการ 2026-11-25)
RAIN ต้องรวมความสามารถหลักของ Google Chrome และ Apple Safari ไว้ในผลิตภัณฑ์เดียว และมุ่งพัฒนาประสบการณ์โดยรวมให้ดีกว่าทั้งสอง โดย:
- ใช้ความสามารถหลักของ Chrome เป็นเกณฑ์ด้านการท่องเว็บ แท็บ การดาวน์โหลด การจัดการข้อมูล ความเข้ากันได้ และความปลอดภัย
- ใช้ความสามารถหลักของ Safari เป็นเกณฑ์ด้าน Private Browsing การป้องกันการติดตาม การควบคุมความเป็นส่วนตัว รายงานความเป็นส่วนตัว และสิทธิ์เว็บไซต์
- คงความสามารถเพิ่มเติมที่เจ้าของโครงการอนุมัติสำหรับ RAIN เช่น Anti-Redirect, Web Protection, Smart RAM/Battery Management, Crash/Session Recovery และการจัดการ Bookmark/History
- นำมาใช้เฉพาะความสามารถที่ Android และ Android WebView รองรับได้จริง พร้อมระบุข้อจำกัดและทดสอบก่อนกล่าวอ้าง
- ไม่เพิ่มบัญชี RAIN, Cloud Sync หรือเปลี่ยนสถาปัตยกรรม Android WebView ตามเป้าหมายนี้
- เป้าหมายเหนือกว่าเป็นทิศทางผลิตภัณฑ์ ไม่ใช่คำกล่าวอ้างว่าทำได้เหนือกว่าทุกด้านก่อนมีหลักฐานจากการทดสอบ

การกำหนดเป้าหมายนี้ไม่ตัดฟีเจอร์ที่อนุมัติไว้และไม่เปลี่ยนลำดับ/เกณฑ์ตรวจรับของ Phase 1 โดยอัตโนมัติ

### 1.2 รุ่นผลิตภัณฑ์
- ผลิต RAIN เป็นผลิตภัณฑ์รุ่นใช้งานจริง (Production) เพียงรุ่นเดียว
- ไม่แยกผลิตภัณฑ์ Demo, Prototype, Alpha หรือ Beta
- CI และการทดสอบภายในเป็นกระบวนการควบคุมคุณภาพ ไม่ใช่ผลิตภัณฑ์อีกสายหนึ่ง
- ทุก Release ต้องผ่าน Build, Test, Security Audit และ Signing ก่อนเผยแพร่

### 1.3 แพลตฟอร์มและสถาปัตยกรรม
- เป้าหมาย Android เท่านั้น
- Android ขั้นต่ำที่รองรับ: Android 10 (API 29) เพื่อสมดุลระหว่างความเข้ากันได้ ความปลอดภัย และภาระดูแลระยะยาว
- ใช้ Kotlin, Android SDK และ Gradle
- ใช้ Android WebView เป็น engine แสดงเว็บไซต์
- ใช้ Android UI สำหรับแถบเครื่องมือ เมนู แท็บ และ Settings
- แยก UI, browser engine, data/storage และ network policy ออกจากกัน
- ไม่ใช้ Electron, Node.js runtime, WebContentsView หรือ Electron preload/IPC
- ใช้ GitHub Actions บน Cloud สำหรับ Build/Test โดยเจ้าของโครงการทำงานผ่านโทรศัพท์และ Termux

### 1.4 App Identity และการอัปเดต
ค่าที่ต้องคงที่ทุก Release:
- applicationId: com.rain.browser
- ชื่อผลิตภัณฑ์: RAIN
- versionName เริ่มต้น: 0.1.0
- กำหนด versionCode เริ่มต้นและเพิ่มขึ้นทุก Release ห้ามลดหรือใช้ซ้ำ

ห้ามเปลี่ยน applicationId หรือ Signing Key หลังเผยแพร่รุ่นแรก เพราะจะกระทบการอัปเดตทับแอปเดิมและความต่อเนื่องของข้อมูล

### 1.5 ธีม
- Dark Slate: #0F172A
- Rain Blue: #38BDF8
- Deep Navy: #1E293B

### 1.6 ขอบเขตที่ไม่รองรับ
- ไม่รับประกันเว็บไซต์ที่ใช้ DRM เช่น Netflix และ Disney+
- ไม่มีบัญชีผู้ใช้ RAIN
- ไม่มี Cloud Sync
- ไม่มี Extension จากภายนอก

---

## 2. สภาพแวดล้อมและความรับผิดชอบของโครงการ

### 2.1 อุปกรณ์
- เจ้าของโครงการมีโทรศัพท์ Android เพียงเครื่องเดียวและไม่มีคอมพิวเตอร์
- ใช้ Termux เป็นเครื่องมือหลักสำหรับ shell, Git และจัดการไฟล์
- ใช้ GitHub เป็นศูนย์กลาง Source Code
- ใช้ GitHub Actions เป็น Cloud Runner สำหรับ Android Build/Test/Release

### 2.2 GitHub และหน้าที่ Developer
Repository หลัก: phukthaisongp1-collab/Rain

Developer (ผู้ช่วย) รับผิดชอบ Source Code และการดูแล Repository ทั้งหมดภายในสิทธิ์และเครื่องมือที่เชื่อมต่อได้:
- สร้าง แก้ไข ตรวจสอบ และจัดระเบียบ Source Code
- จัดการ Commit/Push และเอกสาร
- ดูแล GitHub Actions, Build, Test และ Release workflow
- ตรวจสอบความปลอดภัยและแก้ไข Bugs
- รายงานผลตามหลักฐานจริงเท่านั้น

เจ้าของโครงการกำหนดเป้าหมายและอนุมัติสเปก Developer ต้องทำตามสเปก ห้ามเปลี่ยนสถาปัตยกรรมหรือเพิ่มฟีเจอร์เอง


### 2.2.1 กระบวนการใช้ที่ปรึกษาโครงการ 6 คน

**ข้อกำหนดถาวร:** เมื่อ Developer ต้องจัดทำคำขอคำปรึกษา ขอความเห็นทางเทคนิค หรือขอให้ช่วยวิเคราะห์/แก้ปัญหา ต้องกลับมาอ่านหัวข้อนี้ก่อนเขียนคำขอทุกครั้ง เพื่อไม่ให้ข้อมูลสำคัญตกหล่น

#### วัตถุประสงค์
ส่งบริบทและหลักฐานให้ที่ปรึกษาทั้ง 6 คนเห็นภาพรวมสถานะ RAIN ให้มากที่สุดเท่าที่เข้าถึงได้ เพื่อให้แต่ละคนวิเคราะห์ได้จากข้อมูลจริง ไม่ใช่เดาจาก Error สั้น ๆ

#### ชุดข้อมูลที่ต้องแนบทุกครั้ง
1. **ภาพรวมโครงการ:** เป้าหมายผลิตภัณฑ์ สถาปัตยกรรม เทคโนโลยี ข้อกำหนดที่อนุมัติ และข้อห้าม/ข้อจำกัดที่เกี่ยวข้อง
2. **สถานะปัจจุบัน:** Phase, รอบ/Day, สิ่งที่เสร็จแล้ว สิ่งที่กำลังทำ จุดที่ติดขัด และเกณฑ์ที่ยังไม่ผ่าน
3. **Source Code:** ลิงก์ Repository หลัก พร้อมลิงก์ Branch, Pull Request, Commit และไฟล์/โฟลเดอร์ที่เกี่ยวข้อง รวมถึงไฟล์ทดสอบและ Workflow เท่าที่มี
4. **หลักฐาน CI:** ลิงก์ GitHub Actions run/job, Error log ฉบับเต็มหรือส่วนที่เกี่ยวข้องโดยไม่ตัด stack trace, Test report (เช่น XML/HTML), Logcat และ Artifact ที่ดาวน์โหลดตรวจสอบได้
5. **ประวัติการแก้ปัญหา:** สิ่งที่ทดลองแล้ว Commit ที่เกี่ยวข้อง ผลของแต่ละรอบ และเหตุผลที่วิธีเดิมยังไม่สำเร็จ
6. **ข้อเท็จจริงกับสมมติฐาน:** แยกสิ่งที่ยืนยันจาก Log/Source Code ออกจากข้อสงสัยหรือสมมติฐานอย่างชัดเจน
7. **คำถามและผลลัพธ์ที่ต้องการ:** ขอให้ระบุสาเหตุที่เป็นไปได้ จัดอันดับตามหลักฐาน เสนอวิธีแก้พร้อมผลกระทบ/ความเสี่ยง และวิธีทดสอบเพื่อพิสูจน์ว่าแก้ได้จริง

#### วิธีส่งและวิเคราะห์
- ใช้คำอธิบายและชุดหลักฐานพื้นฐานชุดเดียวกันส่งให้ที่ปรึกษาทั้ง 6 คน เพื่อให้เปรียบเทียบคำตอบได้อย่างเป็นธรรม
- ขอให้แต่ละคนวิเคราะห์อย่างอิสระ ไม่ชี้นำให้เห็นด้วยกับสมมติฐานของ Developer หรือคำตอบของที่ปรึกษาคนอื่น
- หากที่ปรึกษาบางคนเปิด Repository, ไฟล์, ลิงก์ หรือ Artifact ไม่ได้ ให้ระบุข้อจำกัดนั้น และแนบข้อมูลสำคัญในคำขอเท่าที่จำเป็น โดยไม่ถือว่าการเข้าถึงไม่ได้เป็นเหตุให้ตัดความเห็นทิ้ง
- หลังได้รับคำตอบ ให้รวบรวม เปรียบเทียบจุดที่เห็นตรงกัน/ขัดแย้ง ตรวจสอบกับ Source Code และหลักฐาน CI จริง แล้วสรุปแนวทางที่เหมาะสม
- ห้ามตัดสินใจจากเสียงข้างมากเพียงอย่างเดียว ต้องให้น้ำหนักกับหลักฐาน ความถูกต้อง ความปลอดภัย และข้อกำหนดของ RAIN
- หากข้อเสนอใดเปลี่ยนสถาปัตยกรรม ขอบเขตผลิตภัณฑ์ หรือข้อกำหนดที่เจ้าของโครงการอนุมัติ ต้องหยุดและขออนุมัติก่อนดำเนินการ

#### ความปลอดภัยของข้อมูล
- ห้ามแนบ GitHub PAT, Keystore, private key, รหัสผ่าน, Actions Secrets, ข้อมูลบัญชี หรือข้อมูลลับอื่นใด
- ตรวจสอบและปกปิด Token/Secrets/ข้อมูลส่วนบุคคลใน Log และ Artifact ก่อนส่งทุกครั้ง
- แนบเฉพาะข้อมูลที่เกี่ยวข้องกับปัญหา แต่ต้องเพียงพอให้เห็นบริบทและทำซ้ำ/วิเคราะห์ได้

**ข้อบังคับในการทำงาน:** ก่อนเขียนคำขอคำปรึกษาทุกครั้ง Developer ต้องตรวจสถานะ Repository และ CI ล่าสุด แล้วจัดทำชุดข้อมูลตามรายการข้างต้น ห้ามใช้รายงานเก่าหรือสถานะที่ยังไม่ได้ยืนยันเป็นข้อมูลปัจจุบัน

### 2.3 GitHub Token สำหรับ Termux
เจ้าของโครงการจะสร้าง GitHub Personal Access Token (PAT) เพื่อให้ Termux ใช้ Push โค้ด:
- จำกัดสิทธิ์เฉพาะ Repository phukthaisongp1-collab/Rain เท่าที่ GitHub อนุญาต
- ใช้ Token ผ่าน Git credential mechanism ใน Termux
- ห้ามฝัง Token ใน Source Code, script, command history หรือไฟล์ที่ Commit
- ห้ามส่ง Token ในแชตหรือเปิดเผยใน log
- ใช้สิทธิ์ขั้นต่ำที่จำเป็น และเพิกถอน/หมุน Token ได้เมื่อสงสัยว่ารั่วไหล
- การสร้างและตั้งค่า Token ทำโดยเจ้าของบัญชีหรือผ่านขั้นตอนที่เจ้าของอนุมัติ

---

## 3. ระบบลงลายเซ็น Android Keystore

### 3.1 ข้อกำหนดบังคับ
- ใช้ Android App Signing Key จาก Keystore เท่านั้น
- ต้องเตรียม Signing Key ตั้งแต่ Release แรก (versionName 0.1.0)
- ใช้ Keystore และ key alias เดิมสำหรับการอัปเดตแอปทุกเวอร์ชัน
- ห้ามสร้าง Signing Key ใหม่สำหรับอัปเดตแอปเดิม เว้นแต่มีแผน Key Upgrade/Rotation ที่ตรวจสอบข้อจำกัด Android และได้รับอนุมัติ
- APK ที่เผยแพร่ต้องผ่านการ Sign และ Verify ก่อนเสมอ
- ห้ามมี unsigned fallback สำหรับ Release หากไม่มี Signing Secrets ให้หยุด Release และแจ้งข้อผิดพลาด

### 3.2 การสร้างและเก็บรักษา
- สร้าง Keystore ด้วย keytool ในสภาพแวดล้อมที่เจ้าของควบคุม เช่น Termux หากรองรับ หรือสภาพแวดล้อมปลอดภัยที่เจ้าของอนุมัติ
- เก็บ Keystore ต้นฉบับและข้อมูลกู้คืนไว้นอก Repository ในที่ปลอดภัย
- ห้าม Commit/Push Keystore, private key หรือรหัสผ่านขึ้น GitHub
- GitHub Actions รับ Keystore และรหัสผ่านผ่าน GitHub Actions Secrets เท่านั้น ใช้ไฟล์ชั่วคราวใน runner และลบทิ้งเมื่อจบงาน
- สำรอง Keystore อย่างปลอดภัยและทดสอบการกู้คืน
- ห้ามส่ง Keystore หรือรหัสผ่านให้ผู้ช่วยผ่านแชต

### 3.3 GitHub Actions Secrets
กำหนดชื่อ Secrets เช่น:
- ANDROID_KEYSTORE_BASE64
- ANDROID_KEYSTORE_PASSWORD
- ANDROID_KEY_ALIAS
- ANDROID_KEY_PASSWORD

หาก Secrets ไม่ครบ หรือ Signing/Verification ไม่ผ่าน Workflow ต้องล้มเหลวและห้ามเผยแพร่ Release

---

## 4. แผนพัฒนาเฟส 1 — 4 รอบย่อย

ทำตามลำดับ ก → ข → ค → ง ทำเฉพาะรอบที่ได้รับคำสั่ง ตรวจสอบและรายงานก่อนเริ่มรอบถัดไป

### รอบ (ก): Android Project Scaffold + CI + หน้าจอเริ่มต้น
1. สร้าง Android project ด้วย Kotlin และ Gradle Wrapper
2. กำหนด applicationId, versionName, versionCode และชื่อผลิตภัณฑ์ตามข้อ 1.4
3. Pin เวอร์ชัน JDK, Gradle และ Android Gradle Plugin ให้เข้ากัน
4. สร้าง .github/workflows/android-build.yml สำหรับติดตั้ง JDK/Android SDK, ตรวจโค้ด, รัน Unit Tests และ Build APK artifact
5. เตรียม Release Signing โดยอ่าน Secrets จาก GitHub Actions เท่านั้น
6. Release workflow ต้องหยุดหากไม่มี Keystore/Secrets หรือ Verify ลายเซ็นไม่ผ่าน ห้าม unsigned Release
7. สร้าง Activity เริ่มต้นและหน้าจอ RAIN ที่เปิดได้จริง
8. กำหนด WebView security baseline ก่อนอนุญาตให้โหลดเว็บไซต์

**เกณฑ์ตรวจรับ:** Gradle Build ผ่านบน GitHub Actions, applicationId/version ถูกต้อง, แอปเปิดหน้าจอเริ่มต้นได้, Secrets ไม่รั่วใน log และ Release Signing บังคับใช้ Keystore

### รอบ (ข): Browser UI + Tabs + Navigation + Desktop Mode
1. สร้าง UI แถบเครื่องมือและพื้นที่ WebView ให้เหมาะกับหน้าจอ Android
2. สร้าง Tab Manager สำหรับเปิด ปิด และสลับแท็บ พร้อมจัดการ WebView lifecycle
3. เพิ่ม Back, Forward, Reload/Stop, Home/New Tab, Address Bar และการจัดการแท็บ
4. Address Bar:
   - ตรวจสอบ URL อย่างปลอดภัย
   - URL ที่ถูกต้องเปิดโดยตรง
   - ข้อความค้นหาใช้ Search Engine ที่เลือก
   - ค่าเริ่มต้น DuckDuckGo และเลือก Google ได้
   - ปฏิเสธ scheme ที่ไม่อนุญาต และตรวจสอบ external intent ก่อนเปิด
5. **Desktop Mode ต้องมีตั้งแต่รุ่นแรก**
   - สลับ Mobile Mode/Desktop Mode ได้
   - ปรับ WebView User-Agent ให้เหมาะกับโหมด
   - ตั้งค่า viewport/layout ที่จำเป็น เช่น wide viewport และ overview mode ตามผลทดสอบ
   - Reload หลังเปลี่ยนโหมด
   - จำค่าแยกตามเว็บไซต์และเปลี่ยนกลับเป็น Mobile Mode ได้
   - มีตัวเลือกในเมนูและแสดงสถานะโหมดปัจจุบัน
   - รองรับ Desktop website เท่าที่ WebView และหน้าจอโทรศัพท์ทำได้จริง
   - แจ้งว่า Desktop Mode ไม่ได้เปลี่ยนโทรศัพท์เป็นคอมพิวเตอร์ และบางเว็บอาจจำกัดฟังก์ชัน
6. รองรับการซูมหน้าเว็บและปรับขนาดตัวอักษร โดยไม่ทำให้ UI หลักเสียรูป

### รอบ (ค): Privacy + Private Browsing + Network Controls
1. Private Browsing ต้องไม่บันทึก History, Resume, Download metadata, favicon หรือภาพย่อจาก Private Session ลงข้อมูลปกติ
2. ก่อนเลือกวิธีแยก Private Session ต้องตรวจสอบข้อจำกัด Android WebView จริง ทั้ง Cookie, Cache, Web Storage, Service Worker และ WebView data directory
3. ห้ามอ้างว่า Private Mode เก็บข้อมูลใน RAM เท่านั้นจนกว่าจะพิสูจน์ด้วยการทดสอบบน Android รุ่นเป้าหมาย หากต้องแยก process/data directory ให้เสนอแบบและขออนุมัติก่อนเปลี่ยนสถาปัตยกรรม
4. เมื่อปิด Private Session ให้ปิด WebView และล้างข้อมูลที่ API รองรับตามขอบเขตที่ออกแบบ พร้อมตรวจไม่ให้ข้อมูล private เข้าสู่ stores ปกติ
5. DoH และ Proxy:
   - ตรวจสอบความสามารถจริงของ Android WebView และ Android networking ก่อนกำหนด implementation
   - DoH อาจต้องใช้ resolver/network layer เฉพาะ และอาจไม่ครอบคลุม traffic ใน WebView โดยอัตโนมัติ
   - Proxy ของ WebView/Android มีข้อจำกัดตาม Android API และอุปกรณ์ ต้องทดสอบบน Android รุ่นเป้าหมาย
   - ห้ามนำ API Electron เช่น app.configureHostResolver() หรือ proxyBypassRules มาใช้ใน Android
6. เป้าหมาย DoH ที่ต้องประเมิน: Cloudflare, Google และ NextDNS
7. เป้าหมาย Proxy Whitelist ที่ต้องประเมิน:
   - *.scb.co.th
   - *.kbank.com
   - *.go.th
   - *.gmail.com
   - *.outlook.com
8. หาก Android ทำ DoH/Proxy/Whitelist ได้ไม่ครบ ต้องรายงานข้อจำกัดและเสนอทางเลือกเพื่อขออนุมัติ ห้ามแสดงว่าเปิดใช้งานแล้วหาก traffic ไม่ได้ผ่านระบบนั้นจริง

### รอบ (ง): Data Layer + New Tab + Settings + Localization
1. แยก Data Layer ออกจาก UI และ Browser UI ผ่าน Repository/Manager layer
2. Storage interface กลางรองรับ get, set, query และ delete
3. เลือก local storage ที่เหมาะกับ Android และทดสอบความทนทานของข้อมูล ห้ามผูก UI กับ backend โดยตรง
4. จำกัดข้อมูล:
   - History สูงสุด 5,000 รายการ
   - วิดีโอที่ดูค้างสูงสุด 10 รายการ
   - ไม่บันทึกข้อมูล Private Session
   - Cleanup Resume ที่เกิน 30 วันแบบ asynchronous
5. สร้าง New Tab ตามธีม RAIN
6. สร้าง Settings ภายในแอป รองรับภาษา Search Engine ค่าเริ่มต้น Mobile/Desktop, Privacy controls และ DoH/Proxy เฉพาะความสามารถที่ผ่านการตรวจสอบจริง
7. รองรับภาษาไทยและอังกฤษ และสลับภาษาในแอปได้ตามข้อจำกัด Android

---

## 5. Android WebView Security Audit

### 5.1 Baseline
- ปิด file access และ content access ที่ไม่จำเป็น
- ปิด mixed content หรือใช้นโยบายปลอดภัยที่สุดตามความจำเป็น
- เปิด Safe Browsing เมื่ออุปกรณ์รองรับ
- ตรวจสอบ scheme และ URL ก่อนโหลด
- จำกัด external intent และ custom schemes
- ไม่ใช้ addJavascriptInterface กับเว็บไซต์ที่ไม่น่าเชื่อถือ
- ไม่ให้เว็บไซต์เข้าถึง Android API หรือไฟล์ในเครื่องโดยไม่มีเหตุผลและการอนุญาตชัดเจน
- ห้าม bypass SSL certificate error โดยอัตโนมัติ
- ขอ Android permissions เท่าที่จำเป็น
- ตรวจสอบ URI, MIME type และ permission สำหรับ Download/File Picker

### 5.2 ตรวจสอบก่อนส่งมอบ
- ไม่มี Token, Keystore หรือรหัสผ่านใน Source Code
- ไม่มีการเปิดเผย Actions Secrets ใน log/artifact
- ไม่มี JavaScript bridge ที่เปิดช่องให้เว็บภายนอกเรียก Android API โดยไม่จำเป็น
- ไม่มีการยอมรับ SSL certificate error อัตโนมัติ
- ไม่มีข้อมูล Private Session ใน storage ปกติ
- มีการตรวจ URL, Intent และ file access
- Release APK ต้องผ่านการตรวจ Signing เช่น apksigner verify

---

## 6. โครงสร้างโปรเจกต์ Android เป้าหมาย

    Rain/
    ├── .github/
    │   └── workflows/
    │       ├── android-build.yml
    │       └── android-release.yml
    ├── app/
    │   ├── build.gradle.kts
    │   └── src/
    │       ├── main/
    │       │   ├── AndroidManifest.xml
    │       │   ├── java/com/rain/browser/
    │       │   │   ├── MainActivity.kt
    │       │   │   ├── browser/
    │       │   │   │   ├── BrowserController.kt
    │       │   │   │   ├── BrowserTab.kt
    │       │   │   │   ├── TabManager.kt
    │       │   │   │   ├── WebViewFactory.kt
    │       │   │   │   └── DesktopModeManager.kt
    │       │   │   ├── privacy/
    │       │   │   │   ├── PrivateSessionManager.kt
    │       │   │   │   └── PrivacyPolicy.kt
    │       │   │   ├── network/
    │       │   │   │   └── NetworkPolicyManager.kt
    │       │   │   ├── data/
    │       │   │   │   ├── Storage.kt
    │       │   │   │   ├── SettingsRepository.kt
    │       │   │   │   ├── HistoryRepository.kt
    │       │   │   │   ├── BookmarkRepository.kt
    │       │   │   │   └── ResumeRepository.kt
    │       │   │   ├── downloads/
    │       │   │   │   └── DownloadManager.kt
    │       │   │   └── settings/
    │       │   │       └── SettingsActivity.kt
    │       │   └── res/
    │       │       ├── layout/
    │       │       ├── drawable/
    │       │       ├── values/
    │       │       └── values-th/
    │       └── test/
    ├── gradle/wrapper/
    ├── build.gradle.kts
    ├── settings.gradle.kts
    ├── gradle.properties
    ├── gradlew
    ├── gradlew.bat
    ├── gradle/libs.versions.toml
    ├── .gitignore
    └── README.md

โครงสร้างเป็นเป้าหมายเชิงตรรกะ อนุญาตให้จัดตำแหน่งตามมาตรฐาน Android/Gradle เมื่อจำเป็น แต่ห้ามเปลี่ยนขอบเขตหรือเพิ่มฟีเจอร์โดยไม่ได้รับอนุมัติ

---

## 7. ความสามารถสำหรับเฟสถัดไป

เตรียมจุดเชื่อมต่อโดยไม่เพิ่ม dependency หรือพัฒนา feature ล่วงหน้า:
- Ad-blocker
- Network/stream inspection ภายใต้ข้อจำกัดและนโยบายที่เกี่ยวข้อง
- RAIN Player และ HLS
- Download engine และ Offline Library
- Auto-Proxy เฉพาะเมื่อ Android รองรับและผ่านการทดสอบ
- Export/Import JSON
- Profile
- Reader Mode
- Command Palette
- Rain Stats

ระบบดาวน์โหลด 3 ชั้น, ffmpeg และ Offline Library 7 วันยังเป็นเป้าหมายในเฟสที่เกี่ยวข้อง ไม่ใช่งานรอบ (ก) โดยอัตโนมัติ

---

## 8. คำสั่งเริ่มต้นสำหรับ GPT — ทำเฉพาะรอบ (ก)

เมื่อเจ้าของโครงการอนุมัติให้เริ่มรอบ (ก) ให้ดำเนินการดังนี้:

> เริ่มทำรอบ (ก) ของ RAIN Browser ตามแผน v3.1 โดยใช้ Kotlin, Android SDK และ Gradle:
> 1. ตรวจสอบ Repository ปัจจุบันก่อนแก้ไข และรักษาไฟล์/ประวัติที่ไม่เกี่ยวข้อง
> 2. สร้าง Android project scaffold และ Gradle Wrapper พร้อม pin เวอร์ชัน JDK, Gradle และ Android Gradle Plugin ที่เข้ากัน
> 3. กำหนด applicationId com.rain.browser, product name RAIN, versionName 0.1.0 และ versionCode เริ่มต้นที่เหมาะสม พร้อมบันทึกไว้
> 4. สร้าง Activity เริ่มต้นที่เปิดหน้าจอ RAIN ได้จริง และกำหนด WebView security baseline
> 5. สร้าง GitHub Actions CI สำหรับ lint/test/build APK artifact
> 6. เตรียม Release Signing ด้วย Android Keystore ผ่าน GitHub Actions Secrets โดยห้าม commit Keystore/รหัสผ่าน
> 7. Release workflow ต้อง fail หาก Secrets ไม่ครบหรือ APK signature verification ไม่ผ่าน ห้าม unsigned fallback
> 8. ทดสอบ Build บน GitHub Actions และตรวจสอบผลจริงก่อนรายงาน
> 9. ทำเฉพาะรอบ (ก) ห้ามเริ่มรอบ (ข), (ค), (ง) จนกว่าจะได้รับการตรวจรับและคำสั่งใหม่
>
> หากข้อกำหนดขัดแย้งหรือจำเป็นต้องตัดสินใจด้านสถาปัตยกรรม ให้หยุดและถามก่อนเปลี่ยนแปลง

---

## 9. กติกาการส่งมอบและตรวจรับ

1. ทำเฉพาะขอบเขตงานที่ได้รับมอบหมาย
2. ทำตามสเปกที่อนุมัติ ห้ามเปลี่ยนสถาปัตยกรรมหรือเพิ่มฟีเจอร์เอง
3. หากข้อกำหนดคลุมเครือ ขัดแย้ง หรือขาดข้อมูลจำเป็น ให้ถามก่อนลงมือ
4. ส่งมอบโค้ดฉบับสมบูรณ์ในส่วนที่สร้างหรือแก้ไข
5. สรุปไฟล์และข้อกำหนดที่ดำเนินการ
6. แจ้งผล Build/Test/Security Audit พร้อมหลักฐาน
7. แจ้งสิ่งที่ยังไม่ได้ทำและข้อจำกัดอย่างตรงไปตรงมา
8. ห้ามกล่าวว่าทดสอบผ่านหรือ Release สำเร็จ หากไม่มีผลยืนยันจริง

**การอนุมัติ:** การอัปเดตเอกสาร v3.1 ลง GitHub ไม่ใช่คำสั่งให้เริ่มเขียนโค้ดรอบ (ก) ต้องได้รับอนุมัติแยกต่างหาก

---

## 10. ประวัติการเปลี่ยนแปลงจาก v3 เป็น v3.1

- เปลี่ยนแพลตฟอร์มจาก Electron Desktop เป็น Android App
- เปลี่ยนสถาปัตยกรรมเป็น Kotlin + Android SDK + Gradle + Android WebView
- เปลี่ยน appId เป็น Android applicationId โดยคงค่า com.rain.browser
- เปลี่ยน Build เป็น Android Build ผ่าน GitHub Actions
- กำหนด Android Keystore Signing ตั้งแต่ Release แรก และยกเลิก unsigned Release fallback
- เพิ่ม Desktop Mode ตั้งแต่รุ่นแรก พร้อมการจำค่าแยกตามเว็บไซต์
- รวมข้อจำกัดการทำงานผ่านโทรศัพท์เครื่องเดียวและ Termux
- กำหนด GitHub PAT สำหรับ Termux โดยจำกัดสิทธิ์และเก็บอย่างปลอดภัย
- ระบุข้อจำกัดจริงของ Android WebView สำหรับ Private Session, DoH และ Proxy
- คงเป้าหมาย Privacy, tabs, bookmarks, history, resume, downloads, offline, localization และเฟสต่อยอดตามลำดับ
