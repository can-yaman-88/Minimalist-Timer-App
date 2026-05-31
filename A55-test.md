# A55 üzerinde FocusTimer'ı çalıştırma / test etme

Bu dosya, **FocusTimer** Android uygulamasını komut satırından derleyip
Samsung Galaxy A55 (SM-A556E) cihazına kurup test etmek için gereken her şeyi
içerir. Android Studio açmadan, sadece terminalden tam bir derle → kur → çalıştır
→ gözlemle döngüsü kurabilmek için yazıldı.

---

## İçindekiler
1. [Uygulama nedir, neyi test ediyoruz](#1-uygulama-nedir-neyi-test-ediyoruz)
2. [Ortam (bu makinedeki araçlar)](#2-ortam-bu-makinedeki-araçlar)
3. [Tek seferlik kurulum](#3-tek-seferlik-kurulum)
4. [Hızlı çalıştırma](#4-hızlı-çalıştırma-her-seferinde-bu-yeterli)
5. [Her komut ne yapıyor](#5-her-komut-ne-yapıyor)
6. [Ekran görüntüsü ve ekran kaydı](#6-ekran-görüntüsü-ve-ekran-kaydı)
7. [Log izleme ve hata ayıklama](#7-log-izleme-ve-hata-ayıklama)
8. [Manuel test kontrol listesi](#8-manuel-test-kontrol-listesi)
9. [Sık karşılaşılan sorunlar](#9-sık-karşılaşılan-sorunlar)
10. [Hızlı başvuru kartı](#10-hızlı-başvuru-kartı)

---

## 1. Uygulama nedir, neyi test ediyoruz

FocusTimer; çevrimdışı çalışan, derin-çalışma (deep work) odaklı bir zamanlayıcı.
Öne çıkan davranışlar (test ederken bunlara bak):

- **Pitch-black / sepya arayüz** — hiçbir beyaz/parlak öğe sızmamalı (ripple,
  scrim, disabled durumlar dahil).
- **Çok oturumlu odak+mola akışı** ve **kronometre modu**.
- **Tam ekran (immersive) büyük rakamlar.**
- **Ön plan servisi (foreground service) zamanlayıcı motoru** — ekran kilitliyken,
  uygulama arka plandayken bile doğru zamanı korur (`SystemClock.elapsedRealtime`
  ile sürüklenme/drift olmaz).
- **Hızlı menü** (aktif ekrana dokununca açılır): Duraklat/Devam, **Molaya Geç**,
  Tam Ekran, **Bitir**, **Durdur**.
- **Room ile yerel günlük istatistik** + **SAF üzerinden CSV/JSON dışa aktarım**.
- Ağ yok, bulut yok, analitik yok.

> Son düzeltme (commit `a3acfd3`): hızlı menü scrim'i artık opak — arkadaki büyük
> zamanlayıcı rakamları menü düğmelerinin arasından görünmüyor. Test ederken
> zamanlayıcı çalışırken ekrana dokunup menüyü aç ve arkanın tamamen siyah
> olduğunu doğrula.

---

## 2. Ortam (bu makinedeki araçlar)

| Bileşen          | Yol / değer                                               |
|------------------|-----------------------------------------------------------|
| JDK 17           | `~/jdk17` (Temurin 17.0.19)                                |
| Android SDK      | `~/android-sdk` (platform `android-35`, build-tools `35.0.0`) |
| adb              | `/usr/bin/adb`                                             |
| Gradle           | Wrapper üzerinden `./gradlew` (Gradle 8.9)                 |
| Hedef cihaz      | `R5CX90CKWTK` — SM-A556E (Galaxy A55, Android 16 / SDK 36) |
| Uygulama paketi  | `com.deepwork.focustimer`                                  |
| Ana aktivite     | `com.deepwork.focustimer/.MainActivity`                    |
| Yüklü sürüm      | versionName `1.0`, versionCode `1`                         |

Alternatif bağlı cihaz (gerekirse): `R52Y501SKFM` — SM-X820 (Galaxy Tab S10+).

---

## 3. Tek seferlik kurulum

Bunlar zaten bir kere yapıldı; sadece sıfırdan kuruluyorsa veya repo yeniden
klonlandıysa gerekir.

### 3a. `local.properties` (Gradle'a SDK yolunu söyler)
Makineye özeldir, `.gitignore` içindedir, **commit edilmez**:
```bash
echo "sdk.dir=$HOME/android-sdk" > ~/İndirilenler/FocusTimer/local.properties
```

### 3b. Gradle wrapper jar (repoda commit edilmemiş)
`gradle/wrapper/gradle-wrapper.jar` repoya dahil değil, bir kere üretilmeli.
Sistemde standalone gradle yoksa 8.9'u indirip wrapper'ı üret:
```bash
curl -sL -o /tmp/gradle-8.9-bin.zip https://services.gradle.org/distributions/gradle-8.9-bin.zip
unzip -q -o /tmp/gradle-8.9-bin.zip -d /tmp
cd ~/İndirilenler/FocusTimer
JAVA_HOME=$HOME/jdk17 /tmp/gradle-8.9/bin/gradle wrapper --gradle-version 8.9
```
Sonrasında `./gradlew` her şeyi halleder.

---

## 4. Hızlı çalıştırma (her seferinde bu yeterli)

```bash
cd ~/İndirilenler/FocusTimer
export JAVA_HOME=$HOME/jdk17
export ANDROID_SERIAL=R5CX90CKWTK      # tüm gradle/adb komutları bu cihaza gider

# 1) Cihaz bağlı mı? (durum "device" olmalı; "unauthorized" ise telefondan onayla)
adb -s R5CX90CKWTK devices

# 2) Derle + kur (APK'yı üretip doğrudan cihaza yükler)
./gradlew installDebug

# 3) Uygulamayı başlat
adb -s R5CX90CKWTK shell am start -n com.deepwork.focustimer/.MainActivity

# 4) (opsiyonel) Gerçekten çalışıyor mu?
adb -s R5CX90CKWTK shell pidof com.deepwork.focustimer && echo "ÇALIŞIYOR"
```

> **fish shell kullanıyorsan** `export VAR=val` yerine `set -x VAR val` yaz:
> ```fish
> set -x JAVA_HOME $HOME/jdk17
> set -x ANDROID_SERIAL R5CX90CKWTK
> ```
> (Varsayılan kabuk zsh; yukarıdaki bash/zsh sözdizimi olduğu gibi çalışır.)

---

## 5. Her komut ne yapıyor

```bash
# Sadece APK üret (kurmadan). Çıktı:
#   app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleDebug

# Derle + cihaza kur (en sık kullanılan). Cihazda eski sürümü günceller.
./gradlew installDebug

# Üretilmiş APK'yı elle (yeniden) kur. -r = mevcut sürümün üzerine yaz.
adb -s R5CX90CKWTK install -r app/build/outputs/apk/debug/app-debug.apk

# Derleme önbelleğini temizle (tuhaf/eskimiş derleme hatalarında)
./gradlew clean

# Bağımlılıkları/derlemeyi yeniden çözmeye zorla (çok nadiren gerekir)
./gradlew clean installDebug

# Uygulamayı başlat (kategori ile — daha açık)
adb -s R5CX90CKWTK shell am start -n com.deepwork.focustimer/.MainActivity

# Uygulamayı durdur (test arası temiz başlangıç için)
adb -s R5CX90CKWTK shell am force-stop com.deepwork.focustimer

# Tamamen kaldır (imza uyuşmazlığı / temiz kurulum için)
adb -s R5CX90CKWTK uninstall com.deepwork.focustimer
```

İlk derleme ~4 dk sürebilir (Gradle daemon + tüm görevler). Sonraki kurulumlar
artımlı olduğu için genelde 10-20 sn.

---

## 6. Ekran görüntüsü ve ekran kaydı

Görsel davranışı (özellikle siyah scrim düzeltmesini) doğrulamak için:

```bash
# Tek kare ekran görüntüsü -> bilgisayara çek
adb -s R5CX90CKWTK exec-out screencap -p > /tmp/a55-shot.png
xdg-open /tmp/a55-shot.png      # görüntüleyicide aç

# Ekran kaydı (Ctrl+C ile durdur, sonra çek). Maks ~180 sn.
adb -s R5CX90CKWTK shell screenrecord /sdcard/a55-rec.mp4
# (Ctrl+C sonrası)
adb -s R5CX90CKWTK pull /sdcard/a55-rec.mp4 /tmp/a55-rec.mp4
adb -s R5CX90CKWTK shell rm /sdcard/a55-rec.mp4
```

---

## 7. Log izleme ve hata ayıklama

```bash
# Sadece bu uygulamanın loglarını canlı izle
adb -s R5CX90CKWTK logcat --pid=$(adb -s R5CX90CKWTK shell pidof com.deepwork.focustimer)

# Sadece hatalar/uyarılar (gürültüyü azaltır)
adb -s R5CX90CKWTK logcat *:W --pid=$(adb -s R5CX90CKWTK shell pidof com.deepwork.focustimer)

# Geçmiş logları temizle (yeni test öncesi)
adb -s R5CX90CKWTK logcat -c

# Çökme (crash) varsa son izi yakala
adb -s R5CX90CKWTK logcat -d -b crash | tail -50

# Uygulamanın yüklü sürüm/izin bilgisi
adb -s R5CX90CKWTK shell dumpsys package com.deepwork.focustimer | grep -E "versionName|versionCode|granted=true"
```

---

## 8. Manuel test kontrol listesi

Kurduktan sonra cihazda elle kontrol edilecekler:

- [ ] **Açılış**: uygulama açılıyor, arayüz tamamen siyah/sepya, beyaz sızma yok.
- [ ] **Odak başlat**: bir odak süresi (ör. 25/50 dk) ayarla, başlat, rakamlar geri sayıyor.
- [ ] **Hızlı menü (scrim düzeltmesi)**: zamanlayıcı çalışırken ekrana dokun →
      menü açılır, **arka plan tamamen siyah**, büyük rakamlar düğmelerin
      arasından görünmüyor. ⬅️ son commit'in asıl testi.
- [ ] **Duraklat/Devam**: menüden duraklat → süre durur; devam → kaldığı yerden sayar.
- [ ] **Molaya Geç**: odak sırasında "Molaya Geç" → o ana kadarki süre kaydedilir,
      mola hemen başlar, mola bitince sıradaki odak otomatik kuyruğa girer.
- [ ] **Tam ekran**: immersive mod büyük rakamları gösterir, sistem çubukları gizli.
- [ ] **Ekran kilidi dayanıklılığı**: zamanlayıcı çalışırken ekranı kilitle, ~1 dk
      bekle, aç → süre doğru ilerlemiş olmalı (servis + wakelock testi).
- [ ] **Uygulamayı kapatma**: son uygulamalardan kaydır → bildirimde zamanlayıcı
      çalışmaya devam ediyor.
- [ ] **Kronometre modu**: sayma yukarı doğru çalışıyor.
- [ ] **İstatistikler**: tamamlanan oturum bugünün istatistiklerine yansıyor.
- [ ] **Dışa aktarım**: CSV/JSON dışa aktarım, dosya seçici (SAF) açılıyor ve dosya yazılıyor.
- [ ] **Ekran döndürme**: döndürünce çalışan zamanlayıcı ve form durumu korunuyor.

---

## 9. Sık karşılaşılan sorunlar

- **`INSTALL_FAILED_UPDATE_INCOMPATIBLE: signatures do not match`**
  Cihazda farklı imzayla (ör. GitHub Actions APK'sı) kurulu bir sürüm var.
  Yerel debug imzası ona uymaz. Çöz: önce kaldır, sonra kur:
  ```bash
  adb -s R5CX90CKWTK uninstall com.deepwork.focustimer
  ./gradlew installDebug
  ```

- **`adb devices` cihazı göstermiyor / `unauthorized`**
  USB kablosunu/portu kontrol et; telefon ekranındaki "USB hata ayıklamaya izin
  ver" istemini onayla. Takılırsa adb'yi yeniden başlat:
  ```bash
  adb kill-server && adb start-server && adb devices
  ```

- **`INSTALL_FAILED_INSUFFICIENT_STORAGE`**
  Cihazda yer aç veya eski sürümü kaldırıp tekrar kur.

- **Birden fazla cihaz bağlı / yanlış cihaza kuruyor**
  `export ANDROID_SERIAL=R5CX90CKWTK` (veya her komutta `-s R5CX90CKWTK`) doğru
  cihazı garantiler. Diğer cihaz: `R52Y501SKFM` (SM-X820).

- **`SDK location not found` / `sdk.dir`**
  `local.properties` eksik veya yanlış. Bkz. [3a](#3a-localproperties-gradlea-sdk-yolunu-söyler).

- **`Unsupported class file major version` / JDK hatası**
  Yanlış Java sürümü. `export JAVA_HOME=$HOME/jdk17` ayarlı olmalı; doğrula:
  ```bash
  $JAVA_HOME/bin/java -version   # 17.x olmalı
  ```

- **`gradlew: Permission denied`**
  ```bash
  chmod +x ./gradlew
  ```

- **Gradle wrapper jar yok / wrapper hatası**
  Bkz. [3b](#3b-gradle-wrapper-jar-repoda-commit-edilmemiş).

- **`Failed to install ... INSTALL_FAILED_USER_RESTRICTED` (Samsung)**
  Samsung'da Geliştirici Seçenekleri → "USB ile uygulama yükleme"yi aç (Samsung
  hesabı oturumu gerekebilir).

---

## 10. Hızlı başvuru kartı

```bash
# Tek seferde: ortamı ayarla, derle+kur, başlat
cd ~/İndirilenler/FocusTimer
export JAVA_HOME=$HOME/jdk17 ANDROID_SERIAL=R5CX90CKWTK
./gradlew installDebug && \
  adb -s R5CX90CKWTK shell am start -n com.deepwork.focustimer/.MainActivity
```

| İş                        | Komut                                                                 |
|---------------------------|-----------------------------------------------------------------------|
| Cihazları listele         | `adb devices -l`                                                      |
| Derle + kur               | `./gradlew installDebug`                                              |
| Sadece APK üret           | `./gradlew assembleDebug`                                             |
| Başlat                    | `adb -s R5CX90CKWTK shell am start -n com.deepwork.focustimer/.MainActivity` |
| Durdur                    | `adb -s R5CX90CKWTK shell am force-stop com.deepwork.focustimer`     |
| Kaldır                    | `adb -s R5CX90CKWTK uninstall com.deepwork.focustimer`              |
| Ekran görüntüsü           | `adb -s R5CX90CKWTK exec-out screencap -p > /tmp/a55-shot.png`        |
| Log (sadece uygulama)     | `adb -s R5CX90CKWTK logcat --pid=$(adb -s R5CX90CKWTK shell pidof com.deepwork.focustimer)` |
| Temiz derleme             | `./gradlew clean installDebug`                                       |
