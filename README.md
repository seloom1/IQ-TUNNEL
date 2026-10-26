# IQ TUNNEL (VIP PROTOCOLS)

<div dir="rtl" align="center">

**تطبيق أندرويد متقدم وعالي الأداء لتشغيل وإدارة بروكسيات وبروتوكولات الـ VPN الحديثة المبنية على نواة sing-box.**

[![Telegram Channel](https://img.shields.io/badge/Telegram-Channel-2CA5E0?style=for-the-badge&logo=telegram&logoColor=white)](https://t.me/FREEVPSIRAQ)
[![Developer](https://img.shields.io/badge/Developer-@SELOOM1-0088cc?style=for-the-badge&logo=telegram&logoColor=white)](https://t.me/SELOOM1)

</div>

---

## 🌐 قنوات الدعم والتحديثات (Official Channels)

- 📢 **قناة التحديثات الرسمية**: [@FREEVPSIRAQ](https://t.me/FREEVPSIRAQ)
- 👨‍💻 **المطور الرسمي**: [@SELOOM1](https://t.me/SELOOM1)

---

## ⚡ مميزات التطبيق (Features)

- 🚀 **واجهة سايبر حديثة وسريعة (Cyber Reactor UI)**: تصميم عصري وجذاب ومحسن لسرعة الاستجابة وفتح التطبيق الفوري.
- 📡 **مراقبة حية ودقيقة لنشاط الشبكة**: رسم بياني متدفق يعكس سرعات الرفع والتحميل الفعلية لحظة بلحظة.
- ⏱️ **قياس استجابة حقيقي (Real Server Ping)**: فحص دوري ذكي لزمن استجابة السيرفر المتصل فعلياً.
- 🔄 **تبديل فوري للسيرفر أثناء الاتصال**: إمكانية تغيير السيرفر والبروكسي بدون انقطاع أو الحاجة لإعادة تشغيل الخدمة.
- 📸 **التقاط الشاشة الافتراضي (Screenshots Allowed)**: مفعّل افتراضياً لسهولة مشاركة الإعدادات وحل المشاكل.
- 🔋 **بدون إزعاج إذن البطارية**: طلب الإذن يظهر مرة واحدة فقط عند التثبيت لأول مرة.
- 🌐 **دعم كامل للغة العربية والإنجليزية**: تبديل مباشر وسلس للغة من داخل إعدادات التطبيق.
- 🛡️ **دعم كافة البروتوكولات المتقدمة**: VLESS, VMess, Trojan, ShadowTLS, Shadowsocks, WireGuard وغيرها.

---

## 🛠️ أنماط التشغيل (Run Modes)

### 1. وضع الـ VPN العادي (VPN Service - No Root)
- يعمل على جميع أجهزة أندرويد بدون الحاجة لصلاحيات الروت.
- يعتمد على معمارية `VpnService` الرسمية لنظام أندرويد بتشفير وأداء فائق.

### 2. أوضاع الروت المتقدمة (ROOT Modes)
- **TPROXY**: توجيه شفاف فائق السرعة عبر `iptables` و `Policy Routing`.
- **TUN / TUN2SOCKS**: إنشاء منفذ شبكة محلي وتوجيه الحزم بكفاءة عالية.
- **eBPF / BPF2SOCKS**: اعتراض حزم البيانات مباشرة على مستوى نواة لينكس (Kernel-level) للأجهزة الداعمة لتقنية eBPF.

---

## 📦 التثبيت والبناء (Build from Source)

### متطلبات البناء:
- JDK 17 أو 21
- Android SDK و NDK

### أوامر البناء عبر سطر الأوامر:

على نظام **Windows (PowerShell)**:
```powershell
.\gradlew.bat assembleDebug
```

على أنظمة **macOS / Linux**:
```bash
./gradlew assembleDebug
```

ستجد ملفات الـ APK الناتجة داخل المجلد:
`app/build/outputs/apk/debug/` أو داخل `apk_builds/`.

---

## 📜 الترخيص والحقوق (License & Credits)

- تم التطوير والإشراف بواسطة: **[SELOOM1](https://t.me/SELOOM1)**
- قناة التحديثات: **[FREEVPSIRAQ](https://t.me/FREEVPSIRAQ)**
- يعتمد التطبيق على نواة: [sing-box](https://github.com/SagerNet/sing-box)
- الترخيص البرمجي: [GPL-3.0 License](LICENSE)
