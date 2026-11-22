# TouchMaster Pro v1.0.0

İlk MVP sürümü. Kullanıcı tarafından açıkça başlatılan erişilebilirlik gesture otomasyonu içerir.

## İçerik

- Türkçe, koyu temalı Jetpack Compose arayüzü.
- Erişilebilirlik servisi üzerinden Auto Clicker: koordinat, aralık ve tıklama sayısı; kullanıcı Başlat/Durdur kontrolü.
- Makro komutlarını doğrulama ve çalıştırma: `CLICK`, `PRESS`, `SWIPE`, `DELAY`, `LOOP`, `LOOP_FOREVER`, `END_LOOP`, `STOP_MACRO`.
- Koordinat sınır kontrolü ve makro için birim testleri.

## Kurulum ve izin

APK’yi Android cihazda kurun. Uygulamada erişilebilirlik açıklamasını okuyun, Android Ayarları’nda TouchMaster servis iznini kendiniz etkinleştirin ve otomasyonu yalnızca uygulamadaki Başlat düğmesiyle çalıştırın. Android 13+ yan yüklemesinde kısıtlanmış ayar izni gerekebilir.

## Sınırlar

Minimum API 26, target API 36 olarak derlendi. Gerçek Android cihaz/emülatörde kurulum testi yapılmadı; üretici ve uygulamaya göre erişilebilirlik gesture’ları iptal edilebilir veya kabul edilmeyebilir. Kesin milisaniye hassasiyeti garanti edilmez.

Bu MVP’de Room/makro kalıcılığı, yüzen panel, multi-touch, tuş eşleme, dokunma kaydı, foreground service, jiroskop, tanılama/kalibrasyon, JSON yedekleme ve Shizuku yoktur. Release APK imzalıdır; doğrulama ve SHA-256 değerleri ayrıca yayın varlıklarında bulunur.
