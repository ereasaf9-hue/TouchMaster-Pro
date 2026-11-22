# TouchMaster Pro

Android için Kotlin ve Jetpack Compose ile geliştirilen dokunma otomasyonu prototipi. Paket adı `com.touchmaster.pro`, minimum Android 8.0 (API 26).

## Şu anki sürümde bulunanlar

- Koyu temalı Türkçe Compose arayüzü, ana sayfa, makro metin düzenleyicisi, araçlar ve ayarlar sekmeleri.
- Erişilebilirlik servisi: kullanıcı izni gerektirir; ekran içeriğini okumadan kullanıcı tarafından konumlandırılan dokunma hedeflerini çalıştırır.
- Oyun üstü TouchMaster balonu: oturum başlangıcında yalnızca uygulama logosunu taşıyan taşınabilir yuvarlak düğme görünür; dokununca ayrıntılı, kaydırılabilir denetim menüsü açılır.
- Hedef düzenleme: numaralı hedef ekleme, sürükleyerek konumlandırma ve uzun basarak silme; tıklama aralığı, basılı tutma süresi ve tekrar sayısı kontrolleri.
- Görünüm ayarları: balon ve hedefler için ayrı saydamlık/boyut; menü saydamlığı ve menüyü başlık çubuğundan sürükleyerek konumlandırma.
- Uygulama başına profil: hedef noktaları, tıklama davranışı, balon/menü konumu ve görünüm tercihleri sonraki oturumlar için saklanır.
- Saf Kotlin makro parser/validator: CLICK, PRESS, SWIPE, DELAY, LOOP, LOOP_FOREVER, END_LOOP ve STOP_MACRO komutları.
- Koordinat doğrulama/normalizasyon yardımcıları ve birim testler.
- Android release/debug derleme tanımları ve CI iş akışı.

Balon menüsü gizlendiğinde oyun üstünde balon ve hedefler kalır. Hedef uygulamadan çıkıldığında çalışan tıklama durdurulur ve oturum overlay'leri temizlenir; ayarlar uygulama başına profilde saklanır.

## Önemli kapsam sınırları

Bu, kapsamlı v4 talebinin çalışan bir başlangıç/MVP uygulamasıdır; tüm kabul kriterleri tamamlanmış değildir. Makro editörü makroyu doğrulayabilir ancak henüz makro çalıştırmıyor veya kaydetmiyor. PRESS/SWIPE parser tarafından tanınır fakat uygulama ekranındaki araçlara bağlanmamıştır. Room tabloları ve migration, kayıt, multi-touch, fiziksel tuş eşleme, jiroskop, performans kalibrasyonu, tanılama grafikleri, foreground service, JSON yedekleme ve Shizuku ile dokunma enjeksiyonu dahil edilmemiştir. Shizuku Binder bağlantısı ve izin akışı desteklenir; dokunma hareketleri mevcut sürümde Erişilebilirlik servisi üzerinden gönderilir. Hiçbir özellik bunlar varmış gibi gösterilmez.

Erişilebilirlik gesture'ları Android sürümü, üretici ve hedef uygulamaya göre iptal edilebilir ya da engellenebilir; hassas milisaniye garantisi yoktur. Uygulamayı yalnızca izin verilen ve ilgili hizmet şartlarının izin verdiği kullanım senaryolarında kullanın.

## Geliştirme / derleme

JDK 17, Android SDK Platform 36 ve Gradle wrapper gerekir:

```sh
./gradlew clean testDebugUnitTest lintDebug assembleDebug assembleRelease
```

Release imzalama, `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` ve `KEY_PASSWORD` ortam değişkenleriyle yapılandırılır. Bu değerler kaynak koda yazılmaz. Boş bırakıldığında release APK imzasızdır.

## Shizuku bağlantısı
Shizuku uygulamasını kurup sunucusunu başlatın. TouchMaster ana ekranı veya Ayarlar sekmesindeki Shizuku kartından izni verin. Uygulama Binder bağlantısının kesilip yeniden kurulmasını izler ve izin sonucunu ekranda bildirir. Shizuku izni, uygulamanın dokunma hareketlerini Shizuku üzerinden gönderdiği anlamına gelmez; bu sürümde gesture yürütücüsü Erişilebilirlik servisini kullanır.

## Erişilebilirlik iznini açma

İlk olarak uygulamadaki erişilebilirlik uyarısına dokunun, Android Ayarları'nda TouchMaster servisini bulun ve bilinçli şekilde etkinleştirin. Android 13+ sideload kısıtlamaları sebebiyle seçeneğin açılması için Uygulama bilgisi > menü > Kısıtlanmış ayarlara izin ver adımı gerekebilir. İzin yalnızca kullanıcı tarafından verilir.

## Sürüm notları
v1.1.1 için [sürüm notlarına](RELEASE_NOTES_v1.1.1.md), önceki sürüm için [v1.1.0 notlarına](RELEASE_NOTES_v1.1.0.md) bakın.
