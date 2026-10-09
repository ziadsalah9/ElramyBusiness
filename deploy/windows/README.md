# مفروشات الرامي - التشغيل على لابتوب ويندوز
# Elramy store - running on the Windows laptop

> **الأسهل: `Elramy-Setup-<version>.exe`** يثبّت كل شيء وحده (البرنامج + Java + PostgreSQL) بدون إنترنت.
> شغّله بزر الماوس الأيمن ← "تشغيل كمسؤول" واتبع الخطوات. في النهاية يفتح ملف أول دخول (المستخدم admin1 وكلمة مرور عشوائية) والمتصفح.
> لنقل بياناتك من الماك: قائمة ابدأ ← Elramy ← "استرجاع نسخة احتياطية" واختر ملف ‎.dump.
> للتحديث: شغّل ملف Setup الأحدث فوق القديم (يأخذ نسخة احتياطية أولاً ولا يمس البيانات).
> إلغاء التثبيت من "التطبيقات" في ويندوز يحذف البرنامج فقط، وتبقى البيانات والنسخ في C:\Elramy.
> سجل التثبيت: C:\Elramy\logs\setup.log
>
> **Easiest: `Elramy-Setup-<version>.exe`** installs everything by itself (the store, Java, PostgreSQL), no internet needed.
> Right-click → "Run as administrator" and follow the wizard. At the end it opens the first-login note (user admin1, random password) and the browser.
> To bring your data from the Mac: Start menu → Elramy → "Restore a backup", pick the .dump file.
> To update: run the newer Setup over the old one (it backs up first and keeps the data).
> Uninstalling (Windows "Apps") removes the program only; data and backups stay in C:\Elramy.
> Setup log: C:\Elramy\logs\setup.log
>
> The steps below are the manual kit (the zip), for when the installer can't be used.

---

## بالعربي

### ١. مرة واحدة: تثبيت البرامج المطلوبة
1. **Java 21**: من https://adoptium.net نزّل **Eclipse Temurin 21** (Windows x64، ملف `.msi`). أثناء التثبيت فعّل خيار **Add to PATH**.
2. **PostgreSQL 16**: من https://www.postgresql.org/download/windows/ نزّل المثبّت. احفظ **كلمة مرور المستخدم postgres** التي تختارها أثناء التثبيت (ستحتاجها مرة واحدة). اترك المنفذ 5432.

### ٢. تثبيت البرنامج
1. فك ضغط ملف `Elramy-....zip` في أي مكان (مثلاً سطح المكتب).
2. اضغط مرتين على **install.bat** ووافق على صلاحيات المسؤول.
3. اكتب كلمة مرور postgres عند الطلب.
4. في النهاية يفتح المتصفح على `http://localhost:8090` ويظهر اختصار **Elramy** على سطح المكتب.

بعد ذلك يعمل البرنامج **تلقائياً عند تشغيل الويندوز**، ولا تحتاج لفتح أي شيء غير الاختصار.

### ٣. نقل البيانات من الماك (مرة واحدة)
1. على الماك: آخر نسخة احتياطية موجودة في `shop/.local-dev/db-backups/daily/` (ملف `.dump`). انسخها على فلاشة.
2. على اللابتوب: اضغط مرتين على **restore.bat**، اختر الملف، واكتب `YES`.
3. ادخل بنفس اسم المستخدم وكلمة المرور المعتادين.

### ٤. النسخ الاحتياطي
- كل يوم الساعة 11:30 مساءً تُحفظ نسخة في `C:\Elramy\backups` (آخر 30 يوماً).
- **الفلاشة**: سمِّ الفلاشة **ELRAMY-BACKUP** (كليك يمين على الفلاشة ← Rename). عندما تكون موصّلة تُنسخ عليها كل النسخ الجديدة في مجلد `ElramyBackups` (آخر 90 يوماً). يكفي توصيلها مرة كل فترة: ما فاتها يُنسخ عليها تلقائياً.
- إذا كان اللابتوب مغلقاً وقت النسخ، تُعمل النسخة عند تشغيله.

### ٥. تحديث البرنامج
فك ضغط الإصدار الجديد واضغط مرتين على **update.bat** (يعمل نسخة احتياطية أولاً).

### ٦. عند وجود مشكلة
- السجل: `C:\Elramy\logs\elramy.log` و `C:\Elramy\logs\backup.log`.
- إعادة تشغيل اللابتوب تعيد تشغيل البرنامج.
- استرجاع نسخة: **restore.bat**.

---

## English

### 1. Once: install the prerequisites
1. **Java 21** - https://adoptium.net, **Eclipse Temurin 21** (Windows x64 `.msi`). Tick **Add to PATH**.
2. **PostgreSQL 16** - https://www.postgresql.org/download/windows/. Remember the password you set for the **postgres** user. Keep port 5432.

### 2. Install the store
1. Unzip `Elramy-....zip` anywhere.
2. Double-click **install.bat** and allow administrator access.
3. Type the postgres password when asked (used once, not saved).
4. The browser opens `http://localhost:8090`; an **Elramy** shortcut is on the desktop.

It then **starts by itself whenever Windows starts** (scheduled task "Elramy Store", restarted automatically if it stops). Only this laptop can open it (it listens on 127.0.0.1).

What install does: creates `C:\Elramy\{app,secrets,logs,backups}`, a database `ElramyGroup` with its own user `elramy` (random password in `C:\Elramy\secrets`, readable by administrators only), a random login-signing key, the two scheduled tasks and the shortcut. Running it again is safe.

On a brand-new empty database the first login is printed at the end of the install (user `admin1`); change that password immediately.

### 3. Move the data from the Mac (once)
1. On the Mac, the latest backup is in `shop/.local-dev/db-backups/daily/` (`.dump`). Copy it to a USB stick.
2. On the laptop double-click **restore.bat**, pick the file, type `YES`.
3. Log in with your usual username and password.

### 4. Backups
- Daily at 23:30 into `C:\Elramy\backups` (30 days kept); missed runs happen at the next start.
- Name a USB drive **ELRAMY-BACKUP**: whenever it is plugged in during a backup, every backup it does not have yet is copied to `ElramyBackups` on it (90 days kept).
- Manual backup: `powershell -ExecutionPolicy Bypass -File C:\Elramy\app\backup.ps1`.

### 5. Updates
Unzip the new release and double-click **update.bat** (backs up first, keeps the previous version as `elramy.previous.jar`).

### 6. Troubleshooting
- Logs: `C:\Elramy\logs\elramy.log`, `C:\Elramy\logs\backup.log`.
- Task Scheduler (taskschd.msc) shows "Elramy Store" and "Elramy Backup".
- **uninstall.bat** removes the tasks and shortcut; data, backups and the database are kept.
- Settings (folder, port, USB name, backup time) are at the top of `settings.ps1`; change them, then run install.bat again.
