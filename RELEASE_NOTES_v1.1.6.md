# TouchMaster Pro v1.1.6 — hedef işaretçisi düzeltmesi

## Düzeltmeler

- `+ HEDEF EKLE` sonrasında hedef işaretçisinin bir saniyeden kısa sürede kaybolmasına neden olan kendi overlay pencere olayları düzeltildi.
- TouchMaster’ın kendi panel ve balon olayları artık hedef uygulamadan çıkış olarak yorumlanmıyor.
- Panel açıkken geçici Android sistem pencereleri göründüğünde hedef işaretçileri korunuyor.
- Bu akış için regresyon testi eklendi.

## Doğrulama

GitHub Actions üzerinde Java 17 ve Android SDK ile test, lint, debug APK ve release APK derlemeleri çalıştırılacaktır.
