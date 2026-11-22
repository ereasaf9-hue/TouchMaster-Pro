# TouchMaster Pro

Android için Kotlin ve Jetpack Compose ile geliştirilen dokunma otomasyonu prototipi. Paket adı `com.touchmaster.pro`, minimum Android 8.0 (API 26).

Kaynak kodundaki sürüm **1.1.4**'tür.

## Özellikler

- Koyu temalı Türkçe Compose arayüzü; ana sayfa, uygulama listesi, araçlar ve ayarlar sekmeleri.
- Erişilebilirlik servisi, kullanıcının ekranda konumlandırdığı hedeflere dokunma hareketi gönderir; ekran içeriğini okumaz.
- Oyun başında uygulama logosu taşıyan yuvarlak balon gösterilir. Balona dokunmak ayrıntılı, kaydırılabilir menüyü açıp kapatır. Menü gizlenince balon ve hedef işaretçileri oyunda kalır.
- Hedef ekleme, sürükleyerek konumlandırma ve uzun basarak silme; tıklama aralığı, basılı tutma süresi ve tekrar sayısı kontrolleri.
- Balon/hedef boyutu ve saydamlığı, menü saydamlığı ve sürüklenebilir menü konumu.
- Oyun başına saklanan hedef konumları ve görünüm/zamanlama tercihleri.
- Çalışırken hedef veya zamanlama ayarı değiştirildiğinde sonraki dokunuşlar yeni ayarları kullanır. Daha önce gönderilmiş gesture iptal edilemez. Çoklu hedef turu seçilen aralıktan uzun sürüyorsa gerçek hız tur süresiyle sınırlanır.
- Saf Kotlin makro parser/validator: CLICK, PRESS, SWIPE, DELAY, LOOP, LOOP_FOREVER, END_LOOP ve STOP_MACRO komutları.
- Koordinat doğrulama/normalizasyon yardımcıları ve birim testler.

Hedef uygulama dışındaki bir pencere algılanınca çalışan tıklama duraklatılır, panel ve hedef işaretçileri gizlenir; yuvarlak balon oturum kapanana kadar kalır. Hedef uygulamaya dönünce işaretçiler tekrar gösterilir. Sistem izin/kurulum pencerelerinde balon da geçici gizlenir ve hedef uygulamaya dönünce geri gelir. Oturumu kapatmak için balondan menüyü açıp × düğmesine basın. Ayarlar uygulama başına saklanır.

## Kapsam sınırları

Bu, kapsamlı v4 talebinin çalışan bir başlangıç/MVP uygulamasıdır; tüm kabul kriterleri tamamlanmış değildir. Makro editörü komutları doğrulayabilir ancak henüz makro çalıştırmıyor veya kaydetmiyor. PRESS/SWIPE parser tarafından tanınır fakat uygulama ekranındaki araçlara bağlanmamıştır. Room tabloları ve migration, kayıt, multi-touch, fiziksel tuş eşleme, jiroskop, performans kalibrasyonu, tanılama grafikleri, foreground service, JSON yedekleme ve Shizuku ile dokunma enjeksiyonu dahil edilmemiştir.

Erişilebilirlik gesture'ları Android sürümü, üretici ve hedef uygulamaya göre iptal edilebilir ya da engellenebilir; hassas milisaniye garantisi yoktur. Uygulamayı yalnızca izin verilen ve ilgili hizmet şartlarının izin verdiği kullanım senaryolarında kullanın.

## Derleme

JDK 17, Android SDK Platform 35 ve Gradle wrapper gerekir:

```sh
./gradlew clean testDebugUnitTest lintDebug assembleDebug assembleRelease
```

Mevcut Android release iş akışı `app-debug.apk` ve imzasız `app-release-unsigned.apk` oluşturup yayımlar. Workflow'da release signing yapılandırması etkin değildir. İmzalı dağıtım için Gradle signing config ve güvenli Actions secret kullanımı ayrıca eklenip doğrulanmalıdır. Debug APK debug anahtarıyla imzalanır.

## Shizuku ve izinler

Shizuku Binder bağlantısı Ayarlar'da isteğe bağlı olarak kullanılabilir; mevcut oyun tıklama akışı için gerekli değildir. Tıklama hareketleri Erişilebilirlik servisi üzerinden gönderilir. Hedef uygulama başlatmak için Erişilebilirlik ve ekran üstü gösterim izinleri gerekir. Erişilebilirlik izni yalnızca kullanıcı tarafından verilir; Android 13+ sideload kısıtlamalarında Uygulama bilgisi > menü > Kısıtlanmış ayarlara izin adımı gerekebilir.

## Sürüm notları

- [v1.1.4 yayın notları](RELEASE_NOTES_v1.1.4.md)
- [v1.1.3 yayın notları](RELEASE_NOTES_v1.1.3.md)
- [v1.1.2 yayın notları](RELEASE_NOTES_v1.1.2.md)
- [v1.1.1 yayın notları](RELEASE_NOTES_v1.1.1.md)
- [v1.1.0 yayın notları](RELEASE_NOTES_v1.1.0.md)
