# 🌧️ RAIN Browser — แผนพัฒนาและโครงสร้างโค้ด v3.2

> สถานะ: Development Specification — ฉบับหลักแทน v3  
> เวอร์ชันแผน: **v3.2**  
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
- Private Browsing ยังคงเป็นเป้าหมายผลิตภัณฑ์ของ RAIN แต่ย้ายกำหนดการพัฒนาและตรวจรับไป Phase 2; ไม่อยู่ใน Phase 1 acceptance
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
- Android ขั้นต่ำที่รองรับ: Android 16 (API 36) ขึ้นไปเท่านั้น; `minSdk = 36`; Android 15 และเก่ากว่าไม่รองรับ (ตามมติอนุมัติ 2026-11-25)
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

### รอบ (ค): Privacy Controls + Network Controls (ไม่รวม Private Browsing)
1. ตรวจสอบและยืนยันนโยบาย HTTPS/SSL error handling; ห้าม bypass certificate errors
2. กำหนดและตรวจสอบ Mixed Content handling และ Safe Browsing ตามความสามารถของ Android WebView
3. ตรวจสอบว่า file access และ content access ถูกปิด เว้นแต่มีข้อกำหนดที่อนุมัติและมีเหตุผลชัดเจน
4. ตรวจสอบ external intents และ URL schemes พร้อมจำกัดการเปิดใช้งานที่ไม่ปลอดภัย
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

**ขอบเขตที่ย้ายออก:** Private Browsing ทั้งการพัฒนา การแยกข้อมูล การล้างข้อมูล และการทดสอบ ย้ายไป Phase 2 ตามหัวข้อ 7 โดยไม่เป็นเงื่อนไขตรวจรับหรือ blocker ของ Phase 1

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

### 7.1 Phase 2 — Private Browsing

**สถานะ:** งาน Phase 2 ที่ได้รับมอบหมายให้จัดทำแผนแล้ว; การเริ่มพัฒนายังต้องได้รับคำสั่งอนุมัติแยกต่างหาก  
**หลักการบังคับ:** Private Browsing เป็น workstream อิสระ ไม่เป็น dependency, acceptance gate หรือ blocker ของ Phase 1 และต้องไม่ดึงกลับไปผูกกับ Round C หรือ Phase 1 โดยอัตโนมัติ

#### 7.1.1 เป้าหมายและข้อกำหนดคงที่

1. คง Android WebView และสถาปัตยกรรม Android process แยก `:private` พร้อม WebView data-directory suffix `rain_private`; ห้ามเปลี่ยน Engine หรือ Architecture หลักโดยไม่มีหลักฐานและการอนุมัติแยกต่างหาก
2. รองรับ Android 16 / API 36 ขึ้นไปตาม baseline ของโครงการ
3. กำหนด Suffix ก่อนเรียก WebView API ใด ๆ ใน Private Process และต้องไม่มี WebView API ถูกเรียกก่อนการกำหนด Suffix
4. แยกข้อมูล WebView ของ Private ออกจาก Normal ตามขอบเขตที่ Provider รองรับ
5. Application Code ต้องป้องกัน Private URL, Title, Search Query, Session State และข้อมูลอื่นไม่ให้เข้า Normal History, Resume, Downloads Metadata, Favicon, Thumbnail, Logs หรือ Persistent Store
6. ไม่มี RAIN Account และไม่มี Cloud Sync
7. ห้ามกล่าวอ้างว่าเป็น RAM-only, ไม่มีร่องรอยโดยสมบูรณ์ หรือป้องกันการติดตามระดับ OS/Network ได้ เว้นแต่มีหลักฐานที่พิสูจน์ขอบเขตนั้นจริง

#### 7.1.2 หลักฐานเดิมและการแยกสาเหตุ

Run #77–#82 เป็นประวัติการวิเคราะห์ Harness/CI และยังไม่ใช่หลักฐานว่า Product Isolation ผ่าน:
- Run #80: App APK และ Test APK ที่สร้างคนละ Job ใช้ Debug Signing Key ไม่ตรงกัน ทำให้ติดตั้งร่วมกันไม่ได้
- Run #81: Test เรียก `CookieManager.setCookie()` จาก Thread ที่ไม่มี Looper ทำให้เกิด `IllegalStateException`
- Run #82: ส่ง `ResultReceiver` ที่ Class อยู่ใน Test APK ข้าม Process ทำให้เกิด `BadParcelableException` / `ClassNotFoundException` ก่อน WebView Initialization
- ปัญหา KVM, ADB Parser, Shell Quoting และ Emulator Acceleration ต้องยืนยันจาก Log ของแต่ละ Run; ห้ามอนุมาน CPU contention หรือ TCG เป็น Root Cause หากไม่มีหลักฐาน

**กติกาการวินิจฉัย:** Harness Failure หมายถึงยังไม่ได้ทดสอบ Product Isolation; ห้ามนับเป็น Isolation Pass หรือ Product Failure จนกว่าการทดสอบจะไปถึง Assertion ของผลิตภัณฑ์จริง

#### 7.1.3 Architecture และ IPC

- คง Process `:private` และ Suffix `rain_private`; ห้ามใช้ Default WebView Data Directory ใน Private Process
- ตรวจ Initialization Order ตั้งแต่ Application/Component Startup เพื่อยืนยันว่าไม่มี Android WebView API ถูกเรียกก่อน Suffix
- เสนอใช้ Android Messenger บน Handler/Looper เป็น IPC เริ่มต้นสำหรับ Test Control Interface; AIDL เป็นทางเลือกที่ต้องเสนอพร้อมเหตุผลก่อนเปลี่ยน
- IPC รับเฉพาะ Message/Bundle ที่ผ่าน Allow-list ของ Key และ Primitive Types เช่น String, Int และ Boolean
- ห้ามส่ง Custom Parcelable, Serializable, Callback หรือ Class ที่นิยามใน Test APK ไปยัง App Process
- ทุกคำสั่งต้องมี Request ID, Timeout, Command Allow-list และ Error Result ที่ชัดเจน
- แยก Test เป็น IPC Contract, Process Readiness และ WebView Isolation; IPC/Readiness ต้องพิสูจน์ผ่านก่อนเริ่ม WebView Isolation
- ใช้ Readiness Handshake/Response จาก Private Process แทน Fixed Sleep; ตรวจ Process Name และ PID จริง
- Test Control Interface ต้องจำกัดการเข้าถึงเฉพาะ Debug/Test Build หรือกลไก Permission ที่ผ่าน Security Review; การเพิ่ม Service/Component หรือแก้ Manifest ต้องอยู่ใน Change Set ที่ได้รับอนุมัติ

#### 7.1.4 Data Isolation และ Cleanup Matrix

| Data Type | Owner / Strategy | Verification / Limitation |
|---|---|---|
| Cookies | WebView Private Data Directory และ CookieManager Cleanup | ตรวจ Cookie จริงระหว่าง Normal/Private; เรียกบน Thread/Looper ตาม API contract และรอ Callback |
| localStorage / Web Storage | WebView Private Data Directory และ Cleanup API ที่รองรับ | ทดสอบอ่าน/เขียนและเปิด Session ใหม่ |
| Session Storage | Private Tab/Session Lifecycle | ตรวจว่าไม่ข้าม Session และไม่ถูก Restore ใน Normal |
| IndexedDB | WebView Private Data Directory | ทดสอบแยก Profile และ Cleanup บน Provider จริง |
| HTTP Cache | WebView Private Data Directory และ API ที่รองรับ | ระบุขอบเขต Cache ที่พิสูจน์ได้; ไม่เหมารวม Network/OS Cache |
| Cache Storage | WebView Private Data Directory / Provider Capability | Feature Detection และ Test แยก |
| Service Worker | Provider-dependent | ห้ามรับรอง Cleanup หากไม่มี API/หลักฐานเฉพาะ |
| History / Resume | Application Code | ห้ามเขียน Private ลง Normal Store |
| Downloads / Metadata | Application Code | แยก Metadata; ไฟล์ที่ผู้ใช้บันทึกออกนอก Private Data Directory อาจยังอยู่ในเครื่อง |
| Website Permissions | Application Code และ Android Runtime Permission Boundary | แยก Site Permission; Android Runtime Permission เป็นระดับแอป ไม่ใช่ระดับ Tab |
| Favicon / Thumbnail | Application Code และ WebView Data Boundary | ห้ามบันทึก Private Metadata ลง Normal Cache |
| Logs / Diagnostics | Application Code และ CI Artifact Handling | หลีกเลี่ยง URL, Query, Cookie, Token และเนื้อหาส่วนตัวใน Log |

Cleanup ต้องใช้ Feature Detection ก่อนเรียก API เช่น `WebStorageCompat.deleteBrowsingData` เมื่อ Provider รองรับ และต้องรอ Completion Callback ตาม API Contract; ห้ามตีความ Callback ของ API หนึ่งว่าเป็นหลักฐานว่าข้อมูลทุกชนิดรวมถึง Service Worker ถูกล้างแล้ว

Cleanup Report ต้องรายงานแยกประเภทด้วยสถานะ:
- `SUCCESS`
- `PARTIAL`
- `UNSUPPORTED`
- `TIMEOUT`
- `FAILED`

ห้ามรายงาน Global Success หากมี Data Type ที่ไม่รองรับหรือยังยืนยันผลไม่ได้

#### 7.1.5 Cleanup และ Lifecycle Scenarios

ต้องออกแบบและทดสอบ:
1. Normal Close: หยุด Navigation, ทำลาย WebView ตามลำดับที่ปลอดภัย, Cleanup, รอ Callback/Timeout และบันทึกผล
2. Reopen: เปิด Session ใหม่และยืนยันว่าไม่มีข้อมูล Session เก่าตามขอบเขตที่รองรับ
3. App/Process Death ระหว่าง Cleanup: ห้ามถือว่า Cleanup สำเร็จ; ตรวจสถานะเมื่อเริ่มครั้งถัดไป
4. Force-stop: ถือว่า Cleanup อาจไม่สมบูรณ์; ห้ามใช้ Force-stop เป็น Cleanup ปกติ
5. Low Memory: Private Process อาจถูก OS ฆ่า; Normal Browsing ต้องไม่ล่มตาม และ Private Session ใหม่ต้องไม่ Restore State เก่า
6. Provider Unsupported / API Error / Timeout: รายงานข้อจำกัดหรือ Failure ตามจริง
7. Repeated Session Soak: เปิด-ปิดซ้ำและตรวจ Resource Leak, Process Leak และ Data Leakage
8. ห้ามลบ Data Directory ขณะที่ WebView Provider หรือ Process ยังใช้งาน; การลบ Directory ตอน Startup ต้องมีหลักฐาน Lifecycle/Lock Safety ก่อนเสนอใช้

#### 7.1.6 Dedicated Private Browsing Workflow

สร้าง Workflow เฉพาะ Phase 2 แยกจาก Phase 1 โดยใช้ Source Commit เดียวกันสำหรับ App/Test APK ที่ติดตั้งร่วมกัน

**KVM / Emulator Gate**
- ตรวจ /dev/kvm ด้วยการเปิดใช้งานจริง ไม่ใช่เพียงตรวจว่ามีไฟล์
- ตรวจ Emulator Acceleration และ Startup Log ว่าใช้ Hardware Acceleration
- ไม่ใช้ TCG หรือ `-accel off` เป็น Fallback
- หาก KVM/Acceleration ไม่พร้อม ให้ Fail Fast และเก็บ Diagnostics
- ไม่กำหนด `adb root` เป็นเงื่อนไขบังคับ

**Build / ADB / Timeout**
- Build App APK และ Test APK ภายใน Job เดียวกันและติดตั้ง Artifact จาก Job นั้น
- ตรวจ APK Signature Compatibility
- ใส่ Timeout ครอบคลุม ADB, Shell/Pipeline, Emulator Boot, Instrumentation และ Cleanup
- ตรวจ ADB Parser กับรูปแบบ `adb devices -l` จริง
- ควบคุม CPU/Worker ตามผลวัด ไม่ตั้งสมมติฐานว่า CPU contention เป็นสาเหตุโดยไม่มีหลักฐาน

**Artifacts**
- JUnit XML, HTML Report, Logcat, Process/PID Diagnostics, Emulator Startup Log, KVM/ADB Diagnostics, Cleanup Report และ APK Hash
- ใช้ Artifact Upload ที่ทำงานแม้ Test ล้มเหลว และเผื่อเวลา Upload ก่อน Job Timeout
- เก็บ/ปกปิดข้อมูล Private และ Secrets ก่อนอัปโหลด Artifact

**Stability**
- M0/M1 ต้องผ่าน 3 รอบติดต่อกัน
- Isolation/Cleanup ต้องมีรอบทดสอบซ้ำที่กำหนดและแยก Flaky Harness ออกจาก Product Failure
- Retry ได้เฉพาะ Infrastructure Flake ที่จำแนกสาเหตุได้; ห้าม Retry เพื่อกลบ Isolation/Cleanup Failure

#### 7.1.7 Milestones และ Gate

**M0 — Dedicated CI & Harness Proof**
- สร้าง Dedicated Workflow, KVM Gate, Emulator Readiness, ADB/Shell Timeout, Diagnostics และ Artifact Upload
- Build App/Test APK ใน Job เดียวกันและยืนยัน Signature Compatibility
- Gate: Harness/CI ผ่าน 3 รอบติดต่อกันโดยไม่มี Signing, Quoting, Parser หรือ Timeout Failure ที่ยังอธิบายไม่ได้
- Stop: KVM/Acceleration ไม่พร้อม หรือ Emulator หรือ system_server ล้มซ้ำโดยไม่มีแนวทางแก้ที่พิสูจน์ได้
- หาก M0 ไม่ผ่าน ห้ามเริ่ม M1

**M1 — IPC & Process Readiness**
- ทดสอบ IPC Ping/Pong, Request/Response, Invalid Command, Timeout และ Error Handling โดยไม่สร้าง WebView
- ยืนยัน Private Process Name/PID และ Readiness Handshake
- Gate: IPC/Readiness ผ่าน 3 รอบติดต่อกัน ไม่มี BadParcelableException, ClassNotFoundException, Looper Error, Deadlock หรือ Race ที่ยังไม่แก้
- หาก M1 ไม่ผ่าน ห้ามเริ่ม M2

**M2 — WebView Isolation Proof**
- ทดสอบ Cookie, localStorage, Session Storage, IndexedDB, HTTP Cache, Cache Storage และ Service Worker แยกตาม Provider Capability
- ใช้หน้า HTML/Test Origin จริงและตรวจ Normal/Private ทั้งสองทิศทาง
- Gate: ไม่มีข้อมูลข้าม Profile ใน Data Type ที่ระบุว่ารองรับ และมี Evidence รายประเภท
- หากพบ Data Leakage ให้หยุดและแก้ก่อนเดินหน้าต่อ

**M3 — Cleanup, Lifecycle & Soak**
- ทดสอบ Close/Reopen, Process Death, Force-stop, Low Memory, API Unsupported, Exception, Timeout และเปิด-ปิดซ้ำ
- ตรวจ Cleanup Report และ Startup Recovery
- Gate: Report ตรงกับผลจริง, Normal Data ไม่เสียหาย, Session ใหม่สะอาดตามขอบเขตที่รับรอง

**M4 — Security Review & Real Device Validation**
- ตรวจ Application Metadata, History/Resume, Downloads, Permissions, Favicon/Thumbnail, Logs และ Diagnostics
- ผ่าน Security Review ก่อนทดสอบบนอุปกรณ์จริง
- ทดสอบบนโทรศัพท์ Android 16+ ตามกำหนด Day 57–60 โดยไม่อ้างว่าผ่านก่อนมีผลจริง
- Gate: Isolation/Cleanup ผ่านบนอุปกรณ์จริงตามขอบเขตที่รับรองและมี Evidence ครบ

#### 7.1.8 Acceptance Criteria รวม

1. Dedicated Workflow แยกจาก Phase 1 และทำงานกับ Source Commit ที่ตรวจสอบได้
2. KVM/Hardware Acceleration ผ่าน Gate จริง
3. App/Test APK สร้างและติดตั้งจาก Job เดียวกันโดย Signature Compatible
4. IPC และ Process Readiness ผ่าน 3 รอบติดต่อกัน
5. Process Name เป็น `com.rain.browser:private` และกำหนด `rain_private` ก่อน WebView API
6. Normal/Private Isolation ผ่านสำหรับ Data Type ที่ Provider รองรับและมีหลักฐานแยก
7. Normal Browsing ไม่ได้รับผลกระทบ
8. Cleanup Report ระบุ Success/Partial/Unsupported/Timeout/Failed ตามจริง
9. เปิด Private Session ใหม่แล้วไม่มีข้อมูลเก่าตามขอบเขตที่รับรอง
10. มี JUnit XML, HTML, Logcat, Process/KVM/ADB Diagnostics และ Cleanup Report
11. ผ่าน 3 CI Runs ติดต่อกันตามชุดทดสอบสำคัญ
12. ผ่าน Security Review และ Real Device Validation ใน Day 57–60
13. Privacy Disclosure ระบุข้อจำกัด Android OS, Network, WebView Provider และไฟล์ที่ผู้ใช้ดาวน์โหลดอย่างชัดเจน

#### 7.1.9 Timeline และ Stop Conditions

กรอบประมาณการสำหรับวางแผน: **7–11 สัปดาห์หลังอนุมัติ** ไม่ใช่กำหนดส่งที่รับประกัน; ปรับประมาณการอีกครั้งหลัง M0 ผ่าน โดยห้ามลด Acceptance Criteria เพื่อเร่งกำหนดส่ง

หยุดงานและรายงาน CEO เมื่อ:
- KVM/Hardware Acceleration Gate ไม่ผ่าน
- Emulator หรือ system_server ล้มซ้ำโดยไม่มีหลักฐานใหม่
- IPC Deadlock/Crash/Race ยังแก้ไม่ได้
- พบ Private Data Leakage ข้าม Profile
- Cleanup เสี่ยงลบ Directory ขณะ Provider ใช้งาน
- Provider ไม่รองรับความสามารถที่จำเป็นต่อการรับรอง
- ต้องเปลี่ยน Architecture, Manifest Contract หรือ Product Behavior ที่ยังไม่ได้รับอนุมัติ

#### 7.1.10 สิ่งที่ต้องอนุมัติก่อนเริ่มพัฒนา

1. อนุมัติแผน M0–M4 และกรอบเวลา
2. อนุมัติ Dedicated Private Browsing Workflow
3. อนุมัติ Messenger และ Test Control Interface
4. อนุมัติการเพิ่ม Test Service/Component ใน Manifest หากจำเป็น
5. อนุมัติ Cleanup Reporting และขอบเขตการรับรอง
6. อนุมัติ Privacy Disclosure
7. การเปลี่ยน Architecture หรือ Product Behavior ต้องเสนอเป็น Option แยกพร้อมหลักฐานและผลกระทบ

**สถานะสุดท้าย:** แผน Phase 2 นี้เป็นข้อกำหนดวางแผนที่รวมไว้ในเอกสารหลักแล้ว แต่ไม่ใช่คำสั่งเริ่มพัฒนา; ต้องได้รับคำสั่งเริ่ม M0 แยกต่างหาก

### 7.2 Phase 2 — Reference Home Experience: Functional Features

**สถานะ:** เจ้าของโครงการอนุมัติให้เพิ่มขอบเขตนี้เข้า Phase 2 เมื่อ 2026-11-27  
**หลักการ:** ใช้ภาพตัวอย่างหน้าแรกที่เจ้าของโครงการเลือกเป็นข้อกำหนดด้านหน้าตาและรายการความสามารถ ทุกองค์ประกอบ/ปุ่ม/ไอคอนที่แสดงในภาพและสื่อถึงการทำงาน ต้องทำงานได้จริง ไม่ทำเป็นปุ่มตกแต่งหรือจำลองผลลัพธ์  
**การแยกเฟส:** งานนี้เป็น workstream ของ Phase 2 แยกจาก Phase 1.5 อย่างชัดเจน ห้ามนำรายการนี้ไปนับเป็นความคืบหน้าหรือเกณฑ์ตรวจรับของ Phase 1.5 และห้ามทำให้ Phase 1 acceptance เปลี่ยนแปลง

#### 7.2.1 ขอบเขตหน้าแรกตามภาพตัวอย่าง

1. **Responsive layout และ orientation**
   - รองรับโทรศัพท์แนวตั้งและแนวนอน และปรับ layout เมื่อผู้ใช้หมุนอุปกรณ์
   - จัดสัดส่วน ระยะห่าง ขนาดภาพ โลโก้ ช่องค้นหา ทางลัด และแถบเครื่องมือให้เหมาะกับพื้นที่จริงในแต่ละ orientation
   - ไม่ยืด/บิดภาพพื้นหลังหรือซ่อนปุ่มสำคัญ; ตรวจสอบทั้งหน้าจอแคบ/กว้างและความสูงที่ต่างกัน
2. **ฉากหลังและภาพประกอบ**
   - แสดงฉากภูเขา เมฆ ท้องฟ้า และองค์ประกอบภาพตาม reference
   - จัดการ asset ให้เหมาะสมกับความละเอียดและขนาดหน้าจอ พร้อม fallback เมื่อโหลด asset ไม่ได้
   - ฉากหลังเป็นองค์ประกอบภาพ ไม่ใช่ปุ่มที่ไม่มีความหมาย
3. **RAIN branding**
   - ใช้ไฟล์โลโก้ RAIN ที่เจ้าของโครงการอนุมัติเท่านั้น
   - รักษาสัดส่วน ความคมชัด และตำแหน่งที่เหมาะสมทั้งแนวตั้งและแนวนอน
4. **Weather widget**
   - แสดงสภาพอากาศจากแหล่งข้อมูลจริง ไม่ใช้ข้อมูลตัวอย่างหรือค่าคงที่
   - ระบุสถานที่ หน่วย อุณหภูมิ และเวลาที่ข้อมูลอัปเดต; รองรับสถานะกำลังโหลด/ไม่มีข้อมูล/เครือข่ายผิดพลาด
   - การใช้ตำแหน่งต้องขอ permission อย่างเหมาะสม มีทางเลือกกำหนดเมืองเอง และไม่ติดตามตำแหน่งเบื้องหลังโดยไม่มีเหตุผล/การอนุญาต
   - ระบุแหล่งข้อมูล เงื่อนไข API key, quota และข้อจำกัดการใช้งานก่อนเลือกผู้ให้บริการ
5. **Website shortcuts**
   - shortcut ทุกอันเปิด URL ปลายทางจริงผ่าน browser navigation ที่มีอยู่
   - เพิ่ม แก้ไข ลบ และจัดลำดับ shortcut ได้ พร้อมบันทึกใน local storage
   - ตรวจสอบ URL และ external intent ตามนโยบายความปลอดภัยเดิม
   - จัดการกรณี URL ไม่ถูกต้องหรือเว็บไซต์เปิดไม่ได้อย่างเหมาะสม
6. **Voice Search**
   - ปุ่มไมโครโฟนต้องเรียก Android speech recognition จริง รับผลการรู้จำเสียง และส่งข้อความที่ได้เข้ากระบวนการค้นหาปกติ
   - รองรับ permission/บริการรู้จำเสียงไม่พร้อมใช้งาน/ผู้ใช้ยกเลิก/ไม่รู้จำเสียง/เครือข่ายผิดพลาด
   - แสดงข้อความที่รู้จำได้ให้ผู้ใช้ตรวจสอบหรือแก้ไขก่อนค้นหาได้ตาม UX ที่เหมาะสม
   - แจ้งผู้ใช้เมื่อเสียงถูกส่งไปยังบริการภายนอก; ไม่บันทึกเสียงหรือ transcript โดยไม่มีข้อกำหนดและความยินยอม
7. **Visual / Image Search**
   - ปุ่มค้นหาด้วยภาพต้องให้เลือกภาพจากอุปกรณ์ และรองรับการถ่ายภาพเมื่อมีการอนุมัติ camera flow
   - ตรวจสอบ MIME type, ขนาด, URI permission และจัดการภาพอย่างปลอดภัย
   - ส่งภาพไปยังผู้ให้บริการค้นหาภาพที่ใช้งานได้จริง โดยต้องตรวจสอบ API/วิธีเชื่อมต่อ/เงื่อนไขบริการและความเป็นส่วนตัวก่อนเลือก
   - แสดงผลลัพธ์การค้นหาจริงหรือเปิดหน้าผลลัพธ์ของ provider; ห้ามแสดงผลลัพธ์ปลอม
   - แจ้งผู้ใช้ก่อนส่งภาพออกนอกอุปกรณ์ และไม่เก็บภาพเกินความจำเป็น
8. **Bottom feature toolbar**
   - สำรวจรายการไอคอน/ปุ่มทั้งหมดจาก reference และกำหนด action ของแต่ละรายการให้ชัดเจน
   - ทุกปุ่มต้องเชื่อมกับฟังก์ชัน RAIN ที่มีอยู่จริงหรือฟังก์ชันที่ระบุใน Phase 2; ห้ามมีปุ่ม dead/inert
   - หากรายการใดเป็นเพียงข้อมูล/ตกแต่งและไม่ใช่ action ต้องแสดงเป็น non-interactive element อย่างชัดเจน ไม่ทำให้เข้าใจว่าเป็นปุ่ม
9. **Search field**
   - รองรับทั้ง URL และ search query โดยใช้ BrowserUrlResolver/Search Engine ที่โครงการมีอยู่
   - Voice Search และ Visual Search ต้องกลับเข้าสู่ navigation/search flow ที่ตรวจสอบได้ ไม่สร้างระบบค้นหาแยกที่ขัดกับระบบหลัก

#### 7.2.2 ข้อกำหนดด้านสถาปัตยกรรมและความเป็นส่วนตัว

- คง Kotlin, Android SDK, Android WebView และโครงสร้าง browser/data/network ปัจจุบัน; ห้ามเปลี่ยน engine หรือ architecture หลัก
- เชื่อม UI เข้ากับ Repository/Manager ที่เหมาะสม ไม่ผูก view เข้ากับ network/provider โดยตรง
- เก็บการตั้งค่าและ shortcut ใน local storage; ไม่มี RAIN account และไม่มี cloud sync
- ขอ Android permission เฉพาะเมื่อผู้ใช้เรียกใช้ฟังก์ชันนั้น และอธิบายเหตุผลให้ชัดเจน
- ใช้ HTTPS สำหรับบริการภายนอก ตรวจสอบนโยบาย privacy, data retention, rate limit, API key และค่าใช้จ่ายก่อนนำมาใช้
- ห้ามฝัง secret/API key ส่วนตัวใน source code หรือ APK; หากต้องใช้ credential ฝั่ง server ต้องเสนอแบบที่ปลอดภัยและขออนุมัติ ห้ามสร้าง backend/cloud dependency ใหม่เอง
- Weather, Voice และ Image Search ต้องมี error/empty/offline state ที่ชัดเจน และไม่ทำให้ browser navigation หลักเสียหาย
- ห้ามเก็บ location, voice recording, transcript หรือภาพที่ผู้ใช้เลือกเกินกว่าที่จำเป็นต่อคำขอปัจจุบัน

#### 7.2.3 ลำดับ Milestones

- **M1 — Reference & control inventory:** ยืนยันรายการองค์ประกอบทุกชิ้นจากภาพและ mapping ของทุก control; ระบุ provider/ข้อจำกัดของ weather, voice และ image search
- **M2 — Responsive home surface:** สร้างฉากหลัง โลโก้ Layout แนวตั้ง/แนวนอน และ visual assets
- **M3 — Real data & navigation:** weather จริง, shortcut CRUD/persistence, search/URL navigation
- **M4 — Voice & image workflows:** speech recognition และ image search end-to-end พร้อม permission/error/privacy handling
- **M5 — Bottom toolbar & integration:** เชื่อมทุก action และตรวจ regression กับ browser features เดิม
- **M6 — Verification & acceptance:** automated tests, provider integration checks, security/privacy review, orientation matrix และ real-device tests

#### 7.2.4 Acceptance Criteria

- ภาพหน้าแรกตรงกับ reference ในองค์ประกอบและลำดับสำคัญ โดยไม่เสีย usability ในแนวตั้ง/แนวนอน
- หมุนอุปกรณ์แล้ว layout ปรับตาม orientation โดยไม่ crash, reset state หรือทำให้ navigation/tab state สูญหาย
- Weather แสดงข้อมูลจาก provider จริง พร้อม timestamp, location/permission behavior และ failure state
- Shortcut ทุกอันเปิดได้จริง; เพิ่ม/แก้ไข/ลบ/จัดลำดับแล้วข้อมูลคงอยู่หลังปิดและเปิดแอป
- Voice Search รับเสียงจริง แปลงเป็นข้อความ และค้นหาได้จริง รวมถึงกรณี permission/service failure
- Image Search รับภาพจริง ส่งไป provider ที่กำหนด และแสดงผลลัพธ์จริง พร้อม privacy disclosure
- ทุก control ที่หน้าตาเหมือนปุ่มมี action ที่ระบุและทดสอบได้; ไม่มี placeholder/dead button
- Build, lint, unit/instrumentation tests ที่เกี่ยวข้องผ่าน; บันทึกผล provider และ real-device test แยกตามหลักฐาน
- ไม่เปลี่ยนสถาปัตยกรรมหลัก ไม่เพิ่ม RAIN account/cloud sync และไม่ทำให้ฟังก์ชัน browser เดิม regression

#### 7.2.5 ข้อกำหนดก่อนเริ่มพัฒนา

- Phase 2 ต้องได้รับคำสั่งเริ่มงานแยกต่างหาก; การเพิ่มหัวข้อนี้ในแผนไม่ใช่คำสั่งให้เริ่ม implementation
- ก่อนเลือก Weather/Visual Search provider ให้จัดทำตัวเลือกพร้อมเงื่อนไข API, privacy, cost, quota และข้อจำกัด เพื่อให้เจ้าของโครงการตัดสินใจหากมีผลต่อข้อมูล/ค่าใช้จ่าย/บริการภายนอก
- หากภาพ reference มีไอคอนที่ยังระบุความหมายไม่ได้ ต้องยืนยัน mapping ก่อนเขียน action ห้ามเดาหน้าที่ของไอคอนเอง
- ทดสอบบนอุปกรณ์จริงทั้งแนวตั้งและแนวนอนก่อนตรวจรับ

### 7.3 Backlog ความสามารถอื่นสำหรับเฟสถัดไป

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
- ปรับแผนตามคำสั่งเจ้าของโครงการ 2026-11-26: ย้าย Private Browsing ทั้ง implementation และ verification จาก Phase 1 ไป Phase 2; ขยาย Section 7.1 เป็นแผน M0–M4 ครอบคลุม CI/Harness, IPC/Readiness, Isolation, Cleanup/Lifecycle, Security Review, Real Device Validation, Acceptance Criteria, Timeline และ Stop Conditions; ปรับ Android minimum baseline เป็น API 36 ให้ตรงกับมติอนุมัติ


## ภาพอ้างอิงอย่างเป็นทางการของ RAIN Browser

เก็บภาพต้นฉบับสำหรับการออกแบบและตรวจสอบ UI ไว้ใน `docs/assets/` โดยไม่แก้ไขไฟล์ต้นฉบับ:

- **ภาพหน้าหลัก (Home UI reference):** [file_00000000fc688211b9c8e02f71e27cd5.png](assets/file_00000000fc688211b9c8e02f71e27cd5.png) — PNG ขนาด 1536 × 864 พิกเซล
- **โลโก้ RAIN Browser (Official logo reference):** [Meta_AI_rain_browser_1_transparent_20261127_094535.png](assets/Meta_AI_rain_browser_1_transparent_20261127_094535.png) — PNG ขนาด 1920 × 1280 พิกเซล (RGBA)

ภาพทั้งสองเป็นไฟล์อ้างอิงเท่านั้น การนำไปใช้หรือดัดแปลงในแอปต้องเป็นไปตามข้อกำหนดและการอนุมัติของเจ้าของโครงการ ห้ามแก้ไขต้นฉบับทับไฟล์อ้างอิง
