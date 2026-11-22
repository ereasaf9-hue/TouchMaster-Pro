# TouchMaster Pro

## Yaklaşım
Kotlin + Jetpack Compose + MVVM. Erişilebilirlik hizmeti tek zorunlu izin olarak kullanılır; overlay pencereleri `TYPE_ACCESSIBILITY_OVERLAY` ile açılır. Çekirdek motor Android bağımsız arayüzlerle test edilebilir tutulur. Noktalar oran bazlı ve DİKEY/YATAY ayrı saklanır.

## Tasarım
Neon kontrol paneli: koyu #08080D zemin, kart #151520, mor #8B5CF6 ve neon mavi #00C8FF. Kompakt durum kapsülleri, görünür acil durdurma ve canlı sayaçlar. Kullanıcıyı yanıltmayan metinler; içerik okuma, gizli çalışma ve anti-cheat özellikleri yok.

## Klasörler
- `data`: yön bazlı nokta modelleri ve Room varlıkları
- `engine`: gesture yürütme, durum makinesi ve makro çekirdeği
- `service`: AccessibilityService ve overlay kontrol katmanı
- `ui`: onboarding, izin kartı ve ana navigasyon
