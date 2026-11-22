# TouchMaster Pro — yayınlama ve imzalama durumu

## Mevcut GitHub iş akışı

Depo `ereasaf9-hue/TouchMaster-Pro` herkese açıktır. `.github/workflows/android-release.yml`, `v*` tag'i gönderildiğinde test, lint, debug/release derlemesi ve APK hash kontrolü yapar; ardından GitHub Release oluşturup `app-debug.apk` ile `app-release-unsigned.apk` dosyalarını ekler.

İş akışı şu anda release keystore kullanmıyor, Actions signing Secret'larını okumuyor ve release APK imzasını doğrulamıyor. `app-release-unsigned.apk` imzasızdır ve doğrudan kurulum için uygun değildir. `app-debug.apk`, debug anahtarıyla imzalanır; farklı bir dağıtım anahtarıyla imzalanmış mevcut uygulamanın üzerine güncelleme olarak kurulamaması mümkündür.

## İmzalı dağıtım öncesi gerekenler

İmzalı APK isteniyorsa önce Gradle'a açık bir release `signingConfig` ekleyin; Secret adlarını ve workflow adımlarını bu yapılandırmayla eşleştirin. Release build sonrasında `apksigner verify` çalıştırıp workflow'u başarısız imzada durdurun. Anahtar parolalarını veya keystore içeriğini kaynak koda, loglara ya da sohbete koymayın. Anahtar yalnızca güvenli, erişimi kısıtlı depoda tutulmalı ve şifreli yedeği saklanmalıdır.

Secret eklemeden önce workflow'da bu Secret'ların gerçekten kullanıldığını doğrulayın; mevcut workflow bunları tüketmez. Yerel imzalı build için Gradle signing config de ayrıca eklenmelidir.

## Yayın akışı

Yeni sürüm için `versionCode` artırıp `versionName` güncelleyin, test/lint/derlemeleri çalıştırın, değişiklikleri ana dala gönderin ve ilgili `vX.Y.Z` tag'ini push edin. Tag, mevcut workflow'u tetikler ve imzasız release APK'sını yayımlar. İmzalı APK veya mağaza dağıtımı için yukarıdaki imzalama işleri tamamlanmadan bu workflow'u imzalı dağıtım yolu olarak sunmayın.
