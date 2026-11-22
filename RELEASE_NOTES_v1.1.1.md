# TouchMaster Pro v1.1.1

## Oyun içi yuvarlak TouchMaster balonu

Bir hedef uygulama başlatıldığında büyük kontrol paneli yerine uygulamanın kendi logosunu taşıyan yuvarlak balon gösterilir. Balona dokunarak ayrıntılı menüyü açıp kapatabilirsiniz. Menü gizlendiğinde balon ve hedef işaretçileri oyun üzerinde kalır. Balonu ekranda sürükleyerek konumlandırabilirsiniz.

## Ayrıntılı kontrol ve görünüm ayarları

- Hedef ekleme; numaralı hedefleri sürükleyerek konumlandırma ve hedefi uzun basarak kaldırma.
- Tıklama aralığı (50–5000 ms), basılı tutma süresi (10–500 ms) ve tekrar sayısı (süresiz veya 1–100000).
- Balon boyutu ve saydamlığı; hedef işaretçisi boyutu ve saydamlığı; menü saydamlığı.
- Menü üst başlığını sürükleyerek panel konumunu değiştirme.
- Hedef noktaları, ayarlar ve balon/menü konumları hedef uygulama başına saklanır ve sonraki oturumlarda yüklenir.
- Başlat, duraklat/devam et ve durdur düğmeleri menüde bulunur.

Hedef uygulamadan çıkınca çalışan oturum temizlenir. Android'in AccessibilityService API'si daha önce gönderilmiş bir gesture'ı iptal etmediğinden, durdurma isteği geldikten sonra o anda devam eden kısa dokunuş tamamlanabilir.

## Test ve paketleme

Yerel doğrulama: `testDebugUnitTest`, `lintDebug`, `assembleDebug` ve `assembleRelease` başarılıdır. Bu çalışma ortamında bağlı Android cihaz/emülatör bulunmadığından gerçek cihaz üzerindeki görsel/dokunma testi yapılamadı. İmza anahtarı yapılandırılmadığı için oluşturulan release APK imzasızdır; yüklenebilir bir dağıtım için release signing yapılandırılmalıdır.
