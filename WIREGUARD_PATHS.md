# مسارات وإعدادات بروتوكول WireGuard في سورس كود AsteriskBOX

هذا الملف يوضح المسار المباشر لجميع التعديلات البرمجية الخاصة ببروتوكول `WireGuard` و `WARP`، بالإضافة إلى رابط العقدة القياسي للرجوع إليه والتعديل عليه بسهولة في المستقبل.

---

## 🔗 رابط العقدة المعتمد (WireGuard / WARP URL)
```text
wireguard://98qlf4cSnq2VMmonjeWZI1dS1994IzfbR%2FfRdG%2FiCoE%3D@engage.cloudflareclient.com:2408?address=172.16.0.2%2F32%2C2606%3A4700%3A110%3A8d70%3A8df1%3A6e3d%3A693b%3Aea40%2F128&publickey=bmXOC%2BF1FxEMF9dyiK2H5%2F1SUtzH0JuVo51h2wPfgyo%3D&privatekey=98qlf4cSnq2VMmonjeWZI1dS1994IzfbR%2FfRdG%2FiCoE%3D#warp
```

---

## 📁 خريطة مسارات ملفات السورس كود (Source Code Paths)

### 1. مسار معالجة واستيراد الروابط:
- **الملف**: `app/src/main/kotlin/features/outbound/OutboundImportPipeline.kt`
- **الدور**:
  - فك حظر مخططات `wireguard`, `wg`, `warp` من قائمة `RejectedOutboundUrlSchemes`.
  - التقاط الرابط في `ProxyUrlOutboundParser.parseOutcomeOrNull` وتحويله تلقائياً إلى عقدة مقبولة.

### 2. مسار فك وتفكيك معلمات WireGuard و WARP:
- **الملف**: `app/src/main/kotlin/features/endpoint/WireGuardEndpointParser.kt`
- **الدور**:
  - فك المفاتيح (`private_key`, `public_key`) وعناوين الـ IP المحجوزة (`address`, `allowed_ips`) والـ MTU والمنفذ.
  - دعم مخططات `wireguard` و `warp` و `wg`.

### 3. مسار مترجم إعدادات نواة sing-box:
- **الملف**: `app/src/main/kotlin/engine/singbox/config/SingBoxConfigCompiler.kt`
- **الدور**:
  - دالة `compileEndpoints`: جمع عقد الـ `WireGuard` وتوليدها ضمن مصفوفة `endpoints: [...]`.
  - دالة `compileOutbounds`: منع وضع العقدة في `outbounds` الخام لتفادي أخطاء التقادم، مع إدراج الـ `Tag` الخاص بها في `Selectors` والمجموعات والـ Global Selector.

### 4. مسار فاحص الصلاحية والتقادم:
- **الملف**: `app/src/main/kotlin/engine/singbox/config/SingBoxDeprecatedConfigValidator.kt`
- **الدور**:
  - السماح بنوع `wireguard` ضمن العقد البرمجية دون رفضه.

### 5. مسار تسجيل البروكسيات المدعومة:
- **الملف**: `app/src/main/kotlin/features/outbound/SingBoxOutboundImporter.kt`
- **الدور**:
  - إضافة `wireguard` إلى `SupportedSingBoxProxyOutboundTypes`.

### 6. مسار عرض بيانات النود في القائمة:
- **الملف**: `app/src/main/kotlin/features/outbound/OutboundListBehavior.kt`
- **الدور**:
  - قراءة السيرفر والمنفذ من `peers[0].address` و `peers[0].port` لعرضه بشكل سليم في البطاقة.

### 7. مسار فحص الاتصال وسرعة الاستجابة (Ping):
- **الملف**: `app/src/main/kotlin/features/outbound/OutboundPing.kt`
- **الدور**:
  - استخراج عنوان السيرفر من مصفوفة النظراء وفحصه عبر دالة `pingHostOrNull`.

### 8. مسارات واجهة ومحرر العقدة اليدوي:
- **الملف**: `app/src/main/kotlin/features/outbound/OutboundEditorSchema.kt`
  - تسجيل واصف `wireguard` ومخطط الحقول.
- **الملف**: `app/src/main/kotlin/features/outbound/OutboundEditorOtherContent.kt`
  - تحديد الحقول المدخلة (`system`, `private_key`, `local_address`, `reserved`, `mtu`).
- **الملف**: `app/src/main/kotlin/features/outbound/OutboundEditorContent.kt`
  - توجيه المحرر إلى `wireguardOutboundEditor`.
