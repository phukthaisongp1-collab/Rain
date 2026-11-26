# RAIN Browser — Private Browsing Rebuild Plan

**เอกสารเสริมแผน Phase 2**  
**สถานะ:** Planning / Evidence review only — ยังไม่เริ่มแก้ Source Code หรือ Workflow  
**วันที่รวบรวม:** 2026-11-26  
**Repository:** phukthaisongp1-collab/Rain  
**Baseline:** main commit d3e94649cc7470a57d916a0d22832d5e0b25a179  
**Platform:** Android 16+ (API 36+), Kotlin, Android WebView

---

## 1. วัตถุประสงค์

เอกสารนี้เป็นแผนสร้างและตรวจรับ Private Browsing ใหม่ โดยเก็บรักษาหลักฐานปัญหาจากรอบก่อนให้ครบ แยกสาเหตุที่ยืนยันแล้วออกจากสมมติฐาน และกำหนดวิธีป้องกันไม่ให้ความผิดพลาดของ CI/Test Harness ถูกเข้าใจผิดว่าเป็นความล้มเหลวของระบบ Private Browsing

**กติกาสำคัญ**
- งานนี้เป็น Phase 2 ไม่เป็น dependency หรือ blocker ของ Phase 1
- ยังไม่แก้โค้ด/Workflow จนกว่าจะผ่านการทบทวนแผน
- ไม่เปลี่ยนสถาปัตยกรรมที่เจ้าของโครงการอนุมัติ
- ไม่กล่าวว่า Private Browsing ผ่านจนกว่าจะมีหลักฐานทดสอบ Isolation และ Cleanup จริง
- ห้ามอ้างว่าเป็น RAM-only, ลบได้ทุก trace หรือป้องกันการติดตามได้สมบูรณ์
- ผลทดสอบที่ล้มจาก Emulator, ADB, การติดตั้ง APK หรือ IPC harness ต้องจัดเป็นปัญหาตามชั้นที่เกิดจริง ไม่ใช่โยนเป็น Product Bug โดยอัตโนมัติ

## 2. ข้อกำหนดที่ล็อกไว้แล้ว

1. Android 16 / API 36 ขึ้นไปเท่านั้น; minSdk = 36
2. ใช้ Kotlin + Android SDK + Android WebView ตามสถาปัตยกรรมเดิม
3. Normal browsing อยู่ใน process หลัก
4. Private Browsing อยู่ใน Android process แยกชื่อ :private
5. Private process ต้องเรียก WebView.setDataDirectorySuffix("rain_private") ก่อนใช้ WebView API ใด ๆ
6. ห้ามใช้ WebView instance เดียวสลับ Normal/Private ด้วยการล้าง History อย่างเดียว
7. Cookie, Cache, Web Storage และข้อมูลเว็บไซต์ต้องแยกตามขอบเขตที่ WebView รองรับจริง
8. ปิด Private Session แล้วล้างข้อมูลที่ provider/API รองรับ; การเปิด Private ใหม่ต้องไม่เห็นข้อมูล Session ก่อนหน้า
9. ข้อมูล Private ห้ามไหลเข้า Normal History, Resume, Download metadata, favicon, thumbnail หรือ persistent stores ของโหมดปกติ
10. Service Worker cleanup ต้อง feature-detect ตาม WebView provider; ถ้าไม่รองรับต้องรายงานข้อจำกัด
11. ไม่เปลี่ยน WebView engine, ไม่เพิ่มบัญชี RAIN, ไม่เพิ่ม Cloud Sync และไม่เปลี่ยน applicationId/Signing Key
12. การทดสอบบนโทรศัพท์จริงคงกำหนด Day 57–60 ตามแผนโครงการ

## 3. สภาพปัจจุบันจาก Source Code

### 3.1 โครงสร้างที่มีอยู่บน main

- app/src/main/AndroidManifest.xml ประกาศ PrivateBrowsingActivity ให้ทำงานใน process :private และ exported=false
- app/src/main/java/com/rain/browser/RainApplication.kt ตรวจ process name และกำหนด WebView data-directory suffix เป็น rain_private ใน attachBaseContext
- app/src/main/java/com/rain/browser/privacy/PrivateBrowsingActivity.kt สร้าง WebView แยก, เคลียร์ข้อมูลก่อนเริ่ม Session ใหม่, ทำลาย WebView เมื่อปิด และเรียก cleanup API
- ใช้ WebViewFactory และ NetworkPolicyManager ร่วมกับ Browser ส่วนปกติ
- main ปัจจุบันตั้ง minSdk = 36 แล้ว
- PrivateBrowsingActivity และ RainApplication ยังคงอยู่ใน main; การย้ายไป Phase 2 ไม่ได้หมายถึงลบโค้ดเดิม

### 3.2 พฤติกรรม Cleanup ที่มีอยู่

- หาก WebView provider รองรับ WebViewFeature.DELETE_BROWSING_DATA จะเรียก WebStorageCompat.deleteBrowsingData
- หากไม่รองรับ จะ fallback ไป WebStorage.deleteAllData และ CookieManager.removeAllCookies
- Fallback ไม่สามารถยืนยันการลบ Service Worker ได้
- การลบข้อมูลเป็น asynchronous/non-atomic ตาม API/provider
- onDestroy มี cleanup แบบ best-effort; Android อาจ kill process โดยไม่รับประกันว่าจะเรียก onDestroy
- มี startup cleanup ก่อนสร้าง WebView ใหม่เพื่อช่วยจัดการข้อมูลค้างจาก Process ก่อนหน้า

ข้อสรุป: โครงสร้างพื้นฐานมีอยู่แล้ว แต่ยังไม่มีหลักฐาน Instrumentation ที่ผ่านการพิสูจน์ Isolation และ Cleanup end-to-end

## 4. บัญชีปัญหาเดิมจากหลักฐาน

สถานะในตาราง:
- **ยืนยันแล้ว:** มี Source, Log, Test Report หรือ Artifact ยืนยันโดยตรง
- **ข้อจำกัดที่ยืนยันแล้ว:** เป็นข้อจำกัด API/provider ไม่ใช่บั๊กที่แก้ได้ด้วยการ retry
- **ความเสี่ยงจาก Source:** พบรูปแบบที่ควรทดสอบ แต่ยังไม่มีหลักฐานว่าเกิดความเสียหายจริง
- **สมมติฐาน:** ยังไม่ควรใช้เป็น Root Cause จนกว่าจะทดสอบเพิ่ม

### 4.1 ปัญหา Test Harness / Cross-process IPC

| ID | ปัญหา | หลักฐาน | สถานะ / ผลกระทบ |
|---|---|---|---|
| PB-T01 | Test เรียก CookieManager.setCookie จาก Thread ที่ไม่มี Looper | Run #81, Artifact 15073480798, XML/HTML ระบุ IllegalStateException: SetCookie must be called on a thread with a running Looper; stack ชี้ PrivateBrowsingIsolationTest.setCookie line 144 | **ยืนยันแล้ว** — Test หยุดตั้งแต่ต้น ยังไม่ได้พิสูจน์ Cookie isolation |
| PB-T02 | ส่ง ResultReceiver subclass จาก Test APK ผ่าน Intent ไปยัง App Process | Run #82, Artifact 15073318212, Logcat ระบุ BadParcelableException และ ClassNotFoundException สำหรับ PrivateBrowsingIsolationTest$sendPrivateCommand$resultReceiver$1 | **ยืนยันแล้ว** — :private process crash ใน BroadcastReceiver ก่อนตอบ OP_STATUS |
| PB-T03 | Test รอ Private WebView จน timeout หลัง Receiver crash | Run #82 XML/HTML ระบุ Private WebView was not initialized before timeout ที่ awaitPrivateWebView line 154 | **ยืนยันแล้ว** — เป็นผลต่อเนื่องจาก PB-T02 ไม่ใช่หลักฐานว่า data-directory isolation ล้มเหลว |
| PB-T04 | ใช้ ActivityScenario กับ Activity ที่อยู่คนละ Process ไม่เหมาะสม | แนวทางเดิมพยายามเปิด PrivateBrowsingActivity ผ่าน ActivityScenario; การแก้รอบหลังเปลี่ยนเป็น explicit Intent + FLAG_ACTIVITY_NEW_TASK | **ยืนยันจากรูปแบบ Test เดิม** — Test ใหม่ต้องไม่พึ่ง process-local ActivityScenario สำหรับ :private |
| PB-T05 | Debug test bridge ถูกเพิ่มใน PrivateBrowsingActivity บน PR #3 | PR #3 diff เพิ่ม dynamic BroadcastReceiver, operation strings และ ResultReceiver handling ใน Activity | **ยืนยันแล้ว** — ไม่ควรปล่อย test-only control surface เข้า Production APK; ย้ายไป debug/test source set หรือใช้ test harness ที่ไม่อยู่ใน Production |
| PB-T06 | การสื่อสารข้าม Process ยังไม่มีวิธีที่ผ่านการพิสูจน์ | Run #82 ล้มใน deserialization ก่อนคำสั่ง Test ทำงาน | **ยืนยันแล้ว** — ต้องทำ IPC smoke test แยกก่อนนำไปใช้กับ Isolation Test |

### 4.2 ปัญหา Build / APK / Workflow

| ID | ปัญหา | หลักฐาน | สถานะ / ผลกระทบ |
|---|---|---|---|
| PB-CI01 | APK debug ที่ติดตั้งมาจาก Build job มีลายเซ็นคนละชุดกับ APK ที่ instrumentation job สร้างใหม่ | Run #80, job 139344075539, INSTALL_FAILED_UPDATE_INCOMPATIBLE: Existing package signatures do not match | **ยืนยันแล้ว** — แก้ใน Run #81 โดย build/install APK ใน job เดียวกัน |
| PB-CI02 | Diagnostics shell script มี if block ปิดไม่ครบ | Run #80 log: syntax error: unexpected end of file | **ยืนยันแล้ว** — เป็นข้อผิดพลาดใน Workflow diagnostics แยกจาก Test failure |
| PB-CI03 | Nested shell quoting ในคำสั่ง Emulator/ADB ทำให้คำสั่งถูกส่งเข้า shell ไม่ครบ | Run #77 / 45633424587: /usr/bin/sh: Syntax error: Unterminated quoted string | **ยืนยันแล้ว** — ใช้ YAML multiline script โดยตรงและตรวจ Bash syntax ก่อน Push |
| PB-CI04 | ADB device parser สมมติว่า device state อยู่ท้ายบรรทัด แต่ adb devices -l มี metadata ต่อท้าย | Run #79 / 45651617414; แก้ parser ใน Run #80 ด้วย awk ตรวจ field 1 และ field 2 | **ยืนยันแล้วและมีการแก้แล้ว** — ต้องคง parser ที่ไม่ผูกกับท้ายบรรทัด |
| PB-CI05 | Timeout ครอบคลุมเฉพาะคำสั่งต้นทางของ pipeline ไม่ครอบคลุม pipeline ทั้งชุด | พบจากการ review Workflow draft รอบก่อน | **ข้อผิดพลาดเชิง Workflow ที่ระบุแล้ว** — ทุก ADB/pipe operation ต้องมี deadline ครอบคลุม operation ทั้งชุด |
| PB-CI06 | ADB wait-for-device และ ADB shell calls บางจุดไม่มี timeout ที่จำกัดจริง | พบจาก review Workflow รอบก่อน; มีการกำหนดให้เพิ่ม explicit timeout | **ข้อกำหนดแก้ไข** — ต้องมี timeout ทั้ง wait-for-device, install, shell, logcat และ diagnostics |
| PB-CI07 | Emulator process/ADB device readiness ไม่เสถียรในบาง Run; มี ShellCommandUnresponsiveException และ No compatible devices connected | Run #78 / 45623390946 และ Run #77 logs | **ยืนยันแล้วว่าเกิด Environment/Emulator failure** — Test ไม่ได้รันจนถึง assertion ที่เกี่ยวกับ Private Browsing |
| PB-CI08 | KVM device มีอยู่แต่ runner ไม่มีสิทธิ์เปิด /dev/kvm | Run #78 / 45632103595: /dev/kvm เป็น root:kvm mode 0660 และ exec open ได้ Permission denied | **ยืนยันแล้ว** — การมีไฟล์ /dev/kvm ไม่ได้แปลว่าใช้งานได้ |
| PB-CI09 | KVM module diagnostic ใช้ regex ที่ escape word-boundary เกิน | Run #77 log แสดง grep pattern '^kvm(_intel|_amd)?\\b' | **ยืนยันแล้ว** — diagnostic false-negative ได้; ต้องตรวจ sysfs และ emulator -accel-check ประกอบ |
| PB-CI10 | บังคับ TCG / ปิด hardware acceleration ทำให้ Emulator และ Android framework ไม่เสถียร | Runs #76–#78 มี system_server watchdog/DeadSystem และ WebView runtime failure ใน Emulator ที่ใช้ TCG/accel off | **ยืนยันผล Environment failure; ความสัมพันธ์เชิงสาเหตุต้องแยกตาม Run** — ห้ามใช้ TCG เป็น fallback เพื่อให้ CI ดูเหมือนผ่าน |
| PB-CI11 | Emulator SDK/system-image installation และ AVD preparation ใช้เวลานาน/อาจ timeout | Runs #79–#82 มี SDK Preview package/license output และการติดตั้ง system image ก่อน Boot | **ยืนยันว่าเป็นต้นทุน CI** — ต้องติดตั้งเฉพาะ package ที่จำเป็นและกำหนด timeout รายขั้น |
| PB-CI12 | การเก็บ Diagnostics เองเคยทำให้ job ล้มหลัง Test ล้มแล้ว | Run #80 unexpected EOF | **ยืนยันแล้ว** — Diagnostics ต้องใช้ if: always แต่ต้องไม่กลบ exit code ต้นเหตุ |
| PB-CI13 | การแบ่ง Build job กับ Instrumentation job อาจสร้าง debug keystore ชั่วคราวคนละชุด | Run #80 | **ยืนยันแล้ว** — ให้ Build + Instrumentation อยู่ job เดียวกัน หรือสร้าง/ส่ง signing artifact ที่ออกแบบอย่างชัดเจน; แนวทางแรกง่ายและปลอดภัยกว่า |
| PB-CI14 | การใช้ adb root เป็นเงื่อนไขบังคับอาจทำให้ CI เปราะบาง | ข้อเสนอจากที่ปรึกษาและข้อจำกัด Emulator image | **แนวทางป้องกัน** — adb root เป็น best-effort เท่านั้น; ใช้ logcat, DropBox, ANR และ process diagnostics เป็นหลัก |

### 4.3 ปัญหา Product / WebView / Storage ที่ต้องตรวจต่อ

| ID | ปัญหา/ข้อจำกัด | หลักฐาน | สถานะ / ผลกระทบ |
|---|---|---|---|
| PB-D01 | DELETE_BROWSING_DATA ขึ้นกับ WebView provider ไม่ใช่ Android API level อย่างเดียว | ANDROID_WEBVIEW_PRIVACY_FINDINGS.md และ source feature detection | **ข้อจำกัดที่ยืนยันแล้ว** — ต้องบันทึก provider/version และ feature support ทุก Run |
| PB-D02 | Fallback WebStorage.deleteAllData + removeAllCookies ไม่รับประกัน Service Worker cleanup | เอกสาร Android WebView findings และ source | **ข้อจำกัดที่ยืนยันแล้ว** — ห้ามนับเป็น full cleanup หาก Service Worker API ไม่รองรับ |
| PB-D03 | Browsing-data deletion ไม่ใช่ atomic operation | AndroidX API behavior ที่บันทึกไว้ใน findings | **ข้อจำกัดที่ยืนยันแล้ว** — ต้อง serialize การปิด/เปิด Session และรอ callback ก่อนสร้าง WebView รอบถัดไป |
| PB-D04 | Android อาจ kill Process โดยไม่เรียก Activity.onDestroy | Android lifecycle constraint ที่บันทึกใน findings | **ข้อจำกัดที่ยืนยันแล้ว** — cleanup ใน onDestroy เป็น best-effort; startup cleanup ต้องเป็นแนวป้องกันหลักสำหรับ Session ถัดไป |
| PB-D05 | หาก cleanup callback ไม่กลับมา Activity อาจไม่เปิด WebView หรือไม่ finish | Source PrivateBrowsingActivity: callback เป็นตัวเรียก initialize/finish โดยไม่มี explicit failure state/timeout | **ความเสี่ยงจาก Source** — ต้องออกแบบ timeout + fail-closed UI และห้ามเปิด Session ต่อเมื่อ cleanup ไม่ยืนยัน |
| PB-D06 | Private Activity ปัจจุบันมี WebView เดียว | Source และ ANDROID_WEBVIEW_PRIVACY_FINDINGS.md | **ยืนยันแล้ว** — Private tabs, downloads และ permission parity ยังไม่ใช่สิ่งที่พิสูจน์แล้ว; ต้องแยก scope MVP กับ backlog ให้ชัด |
| PB-D07 | แยก WebView data directory ไม่ได้แปลว่า app-wide files/preferences/database หรือ Android Download Manager แยกให้อัตโนมัติ | ขอบเขต API ของ setDataDirectorySuffix และ app architecture | **ข้อจำกัดเชิงสถาปัตยกรรม** — ต้องตรวจทุก persistent store ที่ RAIN ควบคุมเองแยกจาก WebView storage |
| PB-D08 | Test รอบก่อนใช้ loadDataWithBaseURL กับ synthetic origin เพื่อทดสอบ Cookie/localStorage | PR #3 test source | **ช่องว่างด้าน Coverage** — ใช้ทดสอบ DOM storage เบื้องต้นได้ แต่ไม่ครอบคลุม network cache, HTTP response cookies, Service Worker และ Cache Storage แบบครบถ้วน |
| PB-D09 | ยังไม่มีหลักฐานยืนยันว่าข้อมูล Private ไม่เข้า History/Resume/Download metadata/favicon/thumbnail/logs | ไม่มี end-to-end test ที่ผ่าน | **ยังไม่พิสูจน์** — ต้องเพิ่ม negative assertions ที่ตรวจ persistent stores ทั้งหมด |
| PB-D10 | Cleanup บน onDestroy เป็น asynchronous best-effort และไม่รอ completion ก่อน Process ถูกทำลาย | Source PrivateBrowsingActivity | **ความเสี่ยงจาก Source** — ต้องทดสอบ explicit close, configuration change, task removal และ process kill แยกกัน |
| PB-D11 | การคง WebView ข้าม configuration change ต้องไม่ trigger cleanup กลาง Session | onRetainCustomNonConfigurationInstance ใน Source | **ความเสี่ยงที่ต้องทดสอบ** — Rotation/recreation ต้องไม่ทำให้ข้อมูลหายหรือเกิด WebView ซ้อน |
| PB-D12 | การเปิด Activity แบบ singleTask และการเปิด Private ซ้ำยังไม่มี lifecycle test | Manifest launchMode=singleTask; ไม่มี Test ผ่าน | **ยังไม่พิสูจน์** — ต้องทดสอบ launch ซ้ำ, close ซ้ำ และการกลับเข้า Private |
| PB-D13 | การทดสอบบนโทรศัพท์จริงยังไม่เกิด | Tracker กำหนด Day57–60 | **ยังไม่ทดสอบ** — ห้ามรายงาน Device Verified ก่อนถึงกำหนดและมีผลจริง |

### 4.4 ปัญหา Emulator/WebView ที่ห้ามสรุปเกินหลักฐาน

- Run #76 / 45617663615 มี WebView Chromium native SIGTRAP ระหว่าง WebViewChromium.init/AwContents ขณะเปิด MainActivity ก่อนเข้า Private Activity; ยังไม่มีหลักฐานว่าเกิดจาก Private process หรือ data-directory suffix
- Run #78 / 45623390946 มี Android framework/system_server watchdog หรือ DeadSystem และ ADB รายงาน No compatible devices; Test ไม่ได้ทำงานครบ
- Run #82 ยืนยันว่า Activity ใน :private ถูกแสดงขึ้นจริง แต่ Receiver crash ก่อนส่ง status และก่อน Test จะพิสูจน์ WebView readiness
- จำนวน CPU Emulator 4 cores บน runner 4 vCPU อาจแย่งทรัพยากรกับ Gradle/ADB ตามข้อสังเกตที่ปรึกษา แต่ยังเป็น **สมมติฐานความเชื่อมั่นต่ำ** ไม่ใช่ Root Cause ที่พิสูจน์แล้ว
- KVM permission, Emulator boot, ADB readiness และ WebView product behavior เป็นคนละชั้น ต้องเก็บผลแยกกันทุกครั้ง

## 5. แนวทางสร้างใหม่

### Phase PB-A — Test Harness Proof ก่อนแตะ Product Logic

1. ตรวจ main, PR #3, PR #2, commits, workflow runs และ artifacts ล่าสุดก่อนเริ่มทุกครั้ง
2. แยก Private Browsing Instrumentation ออกจาก Phase 1 CI อย่างเด็ดขาด
3. ทำ IPC smoke test ขนาดเล็กใน :private โดยไม่ส่ง callback class หรือ custom Parcelable จาก Test APK เข้า App Process
4. พิจารณา Android Messenger หรือ AIDL ที่ใช้ interface/Parcelable ที่ App APK รู้จักอยู่แล้ว; ต้องพิสูจน์ด้วย minimal smoke test ก่อนเลือกใช้จริง
5. Test-only control component ต้องอยู่ใน debug/test source set และไม่ถูกบรรจุใน Production APK
6. ใช้ explicit Intent เพื่อเริ่ม PrivateBrowsingActivity ใน :private; ไม่ใช้ ActivityScenario ควบคุม Activity ข้าม Process
7. ยืนยัน IPC request/response, timeout, process name, PID และการปิด Activity ได้ครบก่อนเริ่ม Storage Test

**Gate PB-A:** IPC smoke test ผ่านซ้ำได้ และไม่มี BadParcelableException, ClassNotFoundException หรือ Process crash

### Phase PB-B — Process และ Storage Isolation

ใช้ controlled test origin ที่ deterministic ไม่พึ่งเว็บไซต์ภายนอก และบันทึก WebView provider/version ทุกครั้ง

ทดสอบแยกเป็นกรณี:
1. Normal process name/PID และ Private process name/PID ต้องแตกต่างกัน
2. ยืนยัน private suffix ถูกกำหนดก่อน WebView API
3. Normal Cookie ต้องไม่ปรากฏใน Private และ Private Cookie ต้องไม่ปรากฏใน Normal
4. localStorage และ IndexedDB แยกกัน
5. Cache Storage และ HTTP/network cache ตามความสามารถ provider
6. Service Worker registration/controlled page และ cleanup support
7. Cookie attributes ที่สำคัญ เช่น Secure, HttpOnly และ SameSite ในกรณีที่ test fixture รองรับ
8. ตรวจว่าการโหลดหน้าและ redirect ไม่ทำให้ข้อมูลข้าม process

ห้ามใช้เพียง CookieManager singleton check เป็นหลักฐาน Isolation ทั้งระบบ ต้องใช้ WebView page execution และ storage read/write ที่แยก process จริง

### Phase PB-C — Session Cleanup และ Lifecycle

1. เปิด Private ครั้งแรกและเขียนข้อมูลทดสอบทุกชนิด
2. ปิดด้วยปุ่ม Close/Back; ต้องหยุดโหลด ทำลาย WebView และรอ cleanup completion
3. เปิด Private ใหม่; ข้อมูล private เดิมต้องไม่ปรากฏ
4. ตรวจ Normal data ว่ายังคงอยู่ครบ
5. ทดสอบ cleanup callback error/timeout; ต้อง fail closed ไม่เปิด WebView ใหม่โดยไม่ยืนยันการ cleanup
6. ทดสอบ rotation/configuration change โดย Session ที่ยังเปิดอยู่ต้องไม่ถูกล้างกลางคัน
7. ทดสอบ task removal, Activity recreation, force-stop และ Process death; ระบุชัดว่ากรณีใดรับประกัน cleanup ได้และกรณีใดพึ่ง startup cleanup
8. ทดสอบปิดซ้ำ/เปิดซ้ำและป้องกัน callback ซ้ำหรือ finish ซ้ำ
9. ทดสอบว่าข้อมูล Private ไม่ถูกบันทึกลง History, Resume, Download metadata, favicon, thumbnail, normal settings หรือ app logs

### Phase PB-D — Dedicated CI

- ใช้ Android 16/API 36 Emulator และตรวจ KVM ด้วยทั้ง /dev/kvm permissions และ emulator -accel-check
- ห้ามตั้ง ANDROID_I_WANT_MY_TCG และห้ามใช้ -accel off
- KVM gate ต้องตรวจว่าเปิด device ได้จริง; การเห็นไฟล์ /dev/kvm อย่างเดียวไม่เพียงพอ
- ปรับสิทธิ์ /dev/kvm แบบชั่วคราวเฉพาะ ephemeral runner ตามความจำเป็น; ไม่แก้ group membership ของ runner
- Build APK และ Instrumentation APK ใน job เดียวกันเพื่อลดปัญหา debug signing mismatch
- เริ่มจาก Workflow แบบสั้นและแบ่งขั้นชัดเจน; ไม่ยก monolithic Workflow draft 1,000+ บรรทัดมาใช้โดยตรง
- ใช้ timeout ครอบคลุมทุก ADB command และ pipeline รวมถึง wait-for-device
- Parse adb devices -l โดยตรวจ field ของ serial และ state ไม่สมมติว่าค่า device อยู่ท้ายบรรทัด
- ใช้ YAML multiline script โดยตรง หลีกเลี่ยง nested shell quoting
- Diagnostics ต้องทำงานเมื่อ Test fail ด้วย if: always และห้ามกลบ exit code ต้นเหตุ
- เก็บ JUnit XML/HTML, logcat, DropBox, last ANR, process list, activity dump, emulator startup log, KVM diagnostics, WebView provider/version และ APK SHA-256
- adb root เป็น best-effort ไม่ใช่ prerequisite; หากไม่ได้ root ให้เก็บหลักฐานจาก logcat/DropBox/ADB shell เท่าที่ทำได้
- เริ่มวัด Emulator 2 cores ก่อน; ปรับเป็น 3 หรือ 4 เฉพาะเมื่อมีข้อมูล boot time/CPU/memory รองรับ ไม่ถือว่าการใช้ 4 cores เป็นสาเหตุโดยไม่มีหลักฐาน
- ต้องผ่าน 3 CI runs ติดต่อกันบน fresh emulator state; ห้าม retry แล้วนับเป็น pass โดยไม่แยก attempt

### Phase PB-E — Review และ Device Verification

- ตรวจ Source, Manifest, release APK และยืนยันว่า debug test bridge ไม่อยู่ใน Production APK
- ตรวจ cleanup behavior ตาม WebView provider ที่ใช้จริง
- บันทึกข้อจำกัด provider ที่ไม่รองรับ DELETE_BROWSING_DATA
- ทดสอบบนโทรศัพท์จริงตาม Day57–60 เท่านั้น
- ขอรับรองเมื่อครบทุก acceptance criteria; ห้ามลดเกณฑ์เพื่อปิดงานตามกำหนด

## 6. เกณฑ์ตรวจรับสุดท้าย

### Product
- [ ] Process หลักและ :private แยกกันจริง พร้อม PID ต่างกัน
- [ ] rain_private suffix ถูกตั้งก่อน WebView API
- [ ] Cookie isolation ผ่าน
- [ ] localStorage/IndexedDB isolation ผ่าน
- [ ] Cache Storage และ HTTP cache behavior มีผลทดสอบ
- [ ] Service Worker cleanup มีผลตาม feature support ของ provider
- [ ] Explicit close รอ cleanup completion
- [ ] Reopen แล้ว private data เดิมไม่กลับมา
- [ ] Normal data ไม่ถูกลบหรือเปลี่ยน
- [ ] Private URL/data ไม่เข้า normal History/Resume/Download metadata/favicon/thumbnail/logs
- [ ] Lifecycle rotation/process death/force-stop มีผลทดสอบและข้อจำกัดชัดเจน
- [ ] ไม่มีการอ้าง RAM-only หรือ zero-trace

### Test Harness / CI
- [ ] IPC smoke test ผ่าน และไม่ส่ง Test APK callback class ไปให้ App Process deserialize
- [ ] Test-only bridge ไม่อยู่ใน Production APK
- [ ] Build/Test APK ใช้ signing context ที่เข้ากันได้
- [ ] Android 16 Emulator boot ผ่านและ KVM acceleration ได้รับการยืนยัน
- [ ] ADB command ทุกจุดมี timeout
- [ ] Diagnostics ทำงานแม้ Test fail และไม่กลบสาเหตุเดิม
- [ ] XML/HTML/Logcat/DropBox/process diagnostics/artifacts ถูกเก็บครบ
- [ ] ผ่าน 3 CI runs ติดต่อกัน
- [ ] Physical-device test ผ่านใน Day57–60

## 7. ลำดับงานย่อยที่เสนอ

| Work ID | งาน | เงื่อนไขผ่าน |
|---|---|---|
| PB-00 | Source/Workflow/Artifact inventory รอบเริ่ม Phase 2 | Baseline SHA และรายการไฟล์/Run/ข้อจำกัดตรงกัน |
| PB-01 | Minimal cross-process IPC spike | request/response ผ่าน ไม่มี crash |
| PB-02 | Test-only harness isolation | ไม่มี debug bridge ใน Production APK |
| PB-03 | Process identity + suffix ordering test | ชื่อ Process/PID ถูกต้องและทดสอบได้ |
| PB-04 | Cookie + localStorage + IndexedDB isolation | Normal/Private ไม่เห็นข้อมูลกัน |
| PB-05 | Cache/Service Worker/provider capability | ผลทดสอบแยกตาม feature support |
| PB-06 | Close/reopen cleanup + failure path | cleanup complete หรือ fail-closed; normal data intact |
| PB-07 | Lifecycle + no-leak integration tests | rotation/process death/normal stores covered |
| PB-08 | Dedicated CI stabilization | 3 runs ติดต่อกันผ่าน |
| PB-09 | Security review + Production APK inspection | ไม่มี test-only surface และไม่มี overclaim |
| PB-10 | Physical-device verification | ผ่านบนอุปกรณ์จริงใน Day57–60 |

## 8. หลักฐานอ้างอิงเดิม

- Run #76: https://github.com/phukthaisongp1-collab/Rain/actions/runs/45617663615
- Run #78: https://github.com/phukthaisongp1-collab/Rain/actions/runs/45623390946
- Run #79: https://github.com/phukthaisongp1-collab/Rain/actions/runs/45651617414
- Run #80: https://github.com/phukthaisongp1-collab/Rain/actions/runs/45652887096
- Run #81: https://github.com/phukthaisongp1-collab/Rain/actions/runs/45722096039
- Run #82: https://github.com/phukthaisongp1-collab/Rain/actions/runs/45723328182
- PR #3 (Draft, not merged): https://github.com/phukthaisongp1-collab/Rain/pull/3
- PR #2 (Draft, not merged): https://github.com/phukthaisongp1-collab/Rain/pull/2
- Current Phase 2 specification: docs/RAIN_BROWSER_PLAN_V3.md, Section 7.1
- Platform findings: docs/ANDROID_WEBVIEW_PRIVACY_FINDINGS.md

## 9. สถานะและข้อห้ามก่อนเริ่ม Implementation

- เอกสารนี้เป็นแผนและบัญชีปัญหา ไม่ใช่ผลรับรอง Private Browsing
- PR #3 เป็น Draft และไม่ควร merge ทั้งก้อน เพราะมีทั้งการเปลี่ยน Android baseline, Workflow ขนาดใหญ่ และ test bridge ที่ยังล้มเหลว; baseline Android 16+ ได้ถูกนำขึ้น main แยกผ่าน PR #5 แล้ว
- PR #2 เป็น Draft ด้าน diagnostics; ต้องเลือกเฉพาะส่วนที่จำเป็นเมื่อออกแบบ Workflow ใหม่
- Source Private Browsing บน main ยังเป็น implementation เดิมที่ต้องตรวจและทดสอบใหม่ ไม่ถือว่าผ่าน
- ห้ามเริ่ม implementation จน PB-00 และ PB-01 มีผลตรวจชัดเจน
- Phase 1 ดำเนินต่อได้โดยไม่รอ Phase 2
