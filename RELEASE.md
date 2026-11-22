# TouchMaster Pro — yayın kontrol listesi

## Güncel durum

- GitHub deposu `ereasaf9-hue/TouchMaster-Pro` oluşturuldu ve public olarak ayarlandı.
- İlk sürümün release keystore’u proje deposunun dışında oluşturuldu. Keystore ve güçlü parola dosyası ayrı ve yalnızca yerel çalışma alanında tutuluyor.
- GitHub bağlantısının mevcut izinleri Actions Secrets yazmaya yetmedi; dört Secret GitHub’a eklenemedi. Bu nedenle Actions üzerinden otomatik yayın, Secrets eklenene kadar çalışmaz.
- Yayın workflow’u imza Secret’ları yoksa hata verip durur; imzasız APK’yı release’e yüklemez. Geçici anahtar değil, korunması gereken kalıcı keystore kullanılmalıdır.

## Gerekli Actions Secrets

Deponun `Settings > Secrets and variables > Actions` bölümüne şu değerleri ekleyin:

- `KEYSTORE_BASE64`: release keystore dosyasının Base64 metni
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS` (değer: `touchmaster_release`)
- `KEY_PASSWORD`

Keystore ve parolayı sohbet, commit, log ya da herkese açık dosya olarak paylaşmayın. Keystore’un şifreli ve güvenli yedeğini saklayın. Anahtar kaybolursa mevcut kullanıcıların uygulama güncellemeleri etkilenebilir.

## Yayınlama

`.github/workflows/android-release.yml` workflow’u `workflow_dispatch` ile sürümü (`1.0.0` biçiminde) alır veya `v*` Git tag’iyle tetiklenir. Testleri, lint’i ve iki APK derlemesini çalıştırır; release imzasını ve APK imzasını doğrular; SHA-256 manifesti üretir ve GitHub Release oluşturup APK’ları yükler. Secrets yoksa yayın başarısız olur ve durur.

Secrets eklendikten ve kod depoya gönderildikten sonra workflow’u GitHub Actions ekranından `1.0.0` sürümüyle başlatın veya:

```sh
git tag v1.0.0
git push origin v1.0.0
```

Her yeni uygulama sürümünde `versionCode` artırılmalı; ilk sürüm yapılandırması `versionCode = 1` kullanır.
