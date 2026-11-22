# TouchMaster Pro

Android için Kotlin ve Jetpack Compose ile geliştirilen dokunma otomasyonu prototipi. Paket adı `com.touchmaster.pro`, minimum Android 8.0 (API 26).

## Şu anki sürümde bulunanlar

- Koyu temalı Türkçe Compose arayüzü, ana sayfa, makro metin düzenleyicisi, araçlar ve ayarlar sekmeleri.
- Erişilebilirlik servisi: kullanıcı izni gerektirir; açıkça Başlat düğmesine basıldığında tek nokta dokunması gönderir. Servis ekran içeriğini okumaz.
- Basit Auto Clicker: koordinat, tıklama aralığı ve tekrar sayısı ile başlat/durdur.
- Saf Kotlin makro parser/validator: CLICK, PRESS, SWIPE, DELAY, LOOP, LOOP_FOREVER, END_LOOP ve STOP_MACRO komutları.
- Koordinat doğrulama/normalizasyon yardımcıları ve birim testler.
- Android release/debug derleme tanımları ve CI iş akışı.

## Önemli kapsam sınırları

Bu, kapsamlı v4 talebinin çalışan bir başlangıç/MVP uygulamasıdır; tüm kabul kriterleri tamamlanmış değildir. Makro editörü makroyu doğrulayabilir ancak henüz makro çalıştırmıyor veya kaydetmiyor. PRESS/SWIPE parser tarafından tanınır fakat uygulama ekranındaki araçlara bağlanmamıştır. Room tabloları ve migration, yüzen panel, kayıt, multi-touch, fiziksel tuş eşleme, jiroskop, performans kalibrasyonu, tanılama grafikleri, foreground service, JSON yedekleme, Shizuku ve GitHub release akışı dahil edilmemiştir. Hiçbir özellik bunlar varmış gibi gösterilmez.

Erişilebilirlik gesture'ları Android sürümü, üretici ve hedef uygulamaya göre iptal edilebilir ya da engellenebilir; hassas milisaniye garantisi yoktur. Uygulamayı yalnızca izin verilen ve ilgili hizmet şartlarının izin verdiği kullanım senaryolarında kullanın.

## Geliştirme / derleme

JDK 17, Android SDK Platform 36 ve Gradle wrapper gerekir:

```sh
./gradlew clean testDebugUnitTest lintDebug assembleDebug assembleRelease
```

Release imzalama, `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` ve `KEY_PASSWORD` ortam değişkenleriyle yapılandırılır. Bu değerler kaynak koda yazılmaz. Boş bırakıldığında release APK imzasızdır.

## Erişilebilirlik iznini açma

İlk olarak uygulamadaki erişilebilirlik uyarısına dokunun, Android Ayarları'nda TouchMaster servisini bulun ve bilinçli şekilde etkinleştirin. Android 13+ sideload kısıtlamaları sebebiyle seçeneğin açılması için Uygulama bilgisi > menü > Kısıtlanmış ayarlara izin ver adımı gerekebilir. İzin yalnızca kullanıcı tarafından verilir.
