# TouchMaster Pro v1.1.5 — QA ve yayın durumu

## Bulgu ve düzeltme

Kullanıcı, yuvarlak balona dokununca açılan ayar menüsünün hemen kapandığını bildirdi. Hedef dışı pencere olaylarında ayar panelini kapatan davranış kaldırıldı. Açık menü korunurken tıklayıcı beklenmeyen pencere/uygulama geçişinde güvenlik için duraklatılır ve hedef işaretçileri kaldırılır. Menü kapalıysa bilinen Android izin/kurulum pencerelerinde balon geçici gizlenebilir.

## Otomatik doğrulama

Komut başarıyla tamamlandı:

```sh
git diff --check && ANDROID_HOME=/home/ubuntu/Android/Sdk ANDROID_SDK_ROOT=/home/ubuntu/Android/Sdk ./gradlew clean testDebugUnitTest lintDebug assembleDebug assembleRelease
```

18 JVM birim testi geçti; `lintDebug`, debug APK ve release APK derlemeleri başarılı. Gradle gösterdiği compileSdk 35 / Android Gradle Plugin 8.5.2 uyumluluk uyarısıdır; build'i durdurmadı.

- Debug APK: `app/build/outputs/apk/debug/app-debug.apk` — SHA-256 `338fdf8871f1da4976e25a2f4c95545e35da64f05c445108cbaba4daecda5666`
- Release APK (imzasız): `app/build/outputs/apk/release/app-release-unsigned.apk` — SHA-256 `04f3fc8e2fda644213b670db8f56f9e2e79bc42f868197df9b0a80fb2c7d6955`

## Sınırlamalar ve yayın durumu

Bağlı Android cihaz/emülatör olmadığından gerçek oyunda balona dokunma ve menünün ekranda kalması etkileşimli olarak sınanamadı. Bu sürüm adayı için GitHub release yayımlanmadı; kaynak kodunda v1.1.5 / versionCode 15 olarak hazırlanmıştır. Release imzası yapılandırılmadığından release APK imzasızdır; debug APK debug anahtarıyla imzalanmıştır.
