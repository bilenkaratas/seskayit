# Arama Kaydedici

Telefon, WhatsApp ve WhatsApp Business aramalarını otomatik kaydeden Android uygulaması.
Telegram, Signal ve Viber için de deneme desteği var.

Kayıtlar `Dahili depolama/Recordings/CallRec/` klasörüne, `.m4a` (AAC) olarak şu adlarla kaydedilir:

```
2026-10-15 15-39-37 (phone) +90 533 131 04 29 ↙.m4a                  rehberde yok, gelen
2026-10-12 13-43-47 (phone) Pasaliahmet +90 542 433 72 95 ↗.m4a      rehberde var, giden
2026-10-14 10-02-11 (whatsapp) Pasaliahmet +90 542 433 72 95 ↙.m4a
2026-10-14 11-20-05 (whatsapp business) +90 533 131 04 29 ↗.m4a
```

↙ = gelen, ↗ = giden. Tarihte `/` dosya adında yasak olduğu için `-` kullanılır.

## APK'yı derleme

**Yol A: GitHub (bilgisayara bir şey kurmadan)**
1. GitHub'da yeni, boş, **private** bir depo aç.
2. Bu klasörün içeriğini depoya yükle ("Add file › Upload files" ile sürükle-bırak yeterli; `.github` klasörü de gitmeli).
3. "Actions" sekmesinde "APK derle" işi otomatik çalışır (~5 dk).
4. Biten işin altındaki **AramaKaydedici-apk** dosyasını indir, içinden `app-debug.apk` çıkar.

**Yol B: Android Studio**
Klasörü Android Studio ile aç › Build › Build APK(s).

## Telefona kurulum (Xiaomi / Redmi / Poco)

1. APK'yı telefona at ve kur ("Bilinmeyen kaynaklar" izni istenirse ver).
2. Uygulamayı aç, sırayla:
   1. **İzinleri ver** › hepsine "İzin ver".
   2. **Erişilebilirlik servisini aç** › Yüklü uygulamalar › Arama Kaydedici › aç.
      Anahtar gri/tıklanamıyorsa: **Uygulama ayarları** › sağ üst ⋮ › **Kısıtlanmış ayarlara izin ver**, sonra tekrar dene.
   3. **Bildirim erişimini aç** › Arama Kaydedici › aç (WhatsApp için şart).
   4. **Pil kısıtlamasını kaldır** › "Kısıtlama yok".
   5. **Otomatik başlatmayı aç** › Arama Kaydedici'yi aç.
3. Son uygulamalar ekranında uygulamayı aşağı çekip **kilitle** (MIUI/HyperOS servisleri kapatmasın).
4. Ekranda 4 tane ✅ görünce hazır.

## Test ve sorun giderme

- Önce kısa bir normal arama ve bir WhatsApp araması yap, `Recordings/CallRec` klasörünü kontrol et.
- **Karşı taraf duyulmuyor/çok kısık:** uygulamada ses kaynağını sırayla "Mikrofon" ve "İletişim" yapıp tekrar dene. Hoparlörde konuşmak da her zaman işe yarar.
- **Hiç kayıt oluşmuyor:** Erişilebilirlik servisi kapanmış olabilir (Xiaomi bazen kapatır); uygulamayı açıp ✅'leri kontrol et.
- **WhatsApp kaydında numara yok:** WhatsApp bildiriminde sadece isim görünür; numara, rehberde *tam aynı isimle* kayıtlı kişiden bulunur.
- Telefonda root varsa "Hat" kaynağı normal aramaları doğrudan hattan kaydeder (en temiz ses).

## Dosya adını değiştirmek

Tüm format `app/src/main/java/com/bilen/callrec/Naming.kt` içindeki `fileName()` fonksiyonunda.
Kanal etiketleri (`whatsapp business` vb.) `CallNotificationListener.kt` başındaki `APPS` listesinde.

## Hukuki not

Türkiye'de karşı tarafın bilgisi olmadan görüşme kaydetmek TCK 133 ve KVKK kapsamında risklidir.
İş görüşmelerinde karşı tarafı bilgilendirmeniz önerilir.
