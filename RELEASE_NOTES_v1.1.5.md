# TouchMaster Pro v1.1.5 — düzeltme adayı

## Düzeltmeler

- Yuvarlak TouchMaster balonuna dokununca açılan ayar menüsü, hedef dışı pencere olayları nedeniyle artık otomatik kapatılmamalı.
- Hedef dışı uygulamaya geçildiğinde çalışan tıklayıcı güvenlik için duraklatılır ve hedef işaretçileri kaldırılır; kullanıcının açık menüsü korunur.
- Menü kapalıyken bilinen Android izin/kurulum pencerelerinde balon geçici olarak gizlenebilir; hedef uygulamaya dönünce yeniden gösterilir.
- Pencere geçiş politikasına açık menünün korunması ve kapalı menüde sistem penceresinin balonu gizlemesi için JVM testleri eklendi.

## Doğrulama

`git diff --check` ve `./gradlew clean testDebugUnitTest lintDebug assembleDebug assembleRelease` başarılı tamamlandı. 18 JVM birim testi geçti; lint ve debug/release APK derlemeleri başarılı. Yerel APK yolları ve SHA-256 değerleri [QA raporunda](QA_REPORT_v1.1.5.md) kayıtlıdır.

Bağlı Android cihaz/emülatör olmadığından oyun içinde etkileşimli doğrulama yapılamamıştır. Bu, henüz GitHub'da yayımlanmamış bir sürüm adayıdır.
