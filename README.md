# Haw Diet Pro

Aplikasi Android untuk tracking diet pribadi berbasis **foto makanan**. Foto piring Anda, lalu aplikasi:

- mengenali nama menu (paham masakan Indonesia: nasi padang, soto, pecel, gorengan, nasi uduk, ayam geprek, dll),
- memecah setiap elemen di piring (nasi, lauk, sayur, sambal, kuah, kerupuk…) lengkap dengan gram, kalori, protein, karbo, lemak, serat,
- memvalidasi silang dengan **database nutrisi lokal** (TKPI + makanan populer) agar angkanya konsisten,
- memberi skor gizi **A–E**, label (seimbang / tinggi karbo / kurang sayur, …), dan saran praktis.

Semua data tersimpan **hanya di HP** (offline-first, tanpa akun, tanpa server). **100% gratis.**

---

## Daftar isi

1. [Install pertama kali](#1-install-pertama-kali)
2. [Ambil API key Gemini gratis](#2-ambil-api-key-gemini-gratis)
3. [Setup keystore di GitHub Secrets](#3-setup-keystore-di-github-secrets)
4. [Cara kerja auto-update](#4-cara-kerja-auto-update)
5. [Remote config (update instan tanpa install)](#5-remote-config-update-instan-tanpa-install)
6. [Fitur](#6-fitur)
7. [Arsitektur & struktur folder](#7-arsitektur--struktur-folder)
8. [Database & aturan migrasi](#8-database--aturan-migrasi)
9. [Build & test](#9-build--test)
10. [Troubleshooting](#10-troubleshooting)

---

## 1. Install pertama kali

1. Pastikan GitHub Actions aktif di repo (tab **Actions**; jika ada tombol *"I understand my workflows, go ahead and enable them"*, klik).
2. Pastikan 4 secret keystore sudah diisi (lihat [bagian 3](#3-setup-keystore-di-github-secrets)).
3. Setiap push ke `main` otomatis membuat **GitHub Release** berisi APK. Buka dari HP:
   `https://github.com/wahyusubrata10/dietapp/releases/latest`
4. Unduh file `HawDietPro-<versi>.apk`, buka, lalu izinkan **"Instal aplikasi tidak dikenal"** untuk browser/file manager bila diminta.
5. Buka aplikasi → ikuti onboarding (data diri → target → API key).

Setelah instalasi pertama ini, **Anda tidak perlu mengunduh APK manual lagi**. Aplikasi akan memperbarui dirinya sendiri (lihat [bagian 4](#4-cara-kerja-auto-update)).

## 2. Ambil API key Gemini gratis

Analisa foto memakai **Google Gemini** (model Flash multimodal) dengan API key milik Anda sendiri, gratis tanpa kartu kredit.

1. Buka <https://aistudio.google.com/apikey> dan login dengan akun Google.
2. Klik **Create API key** (pilih/buat project bila diminta).
3. Salin key (diawali `AIza…`).
4. Di aplikasi: **Profil → Pengaturan → API Key Gemini**, tempel, lalu ketuk **Simpan & uji**.

Catatan:

- Key disimpan **terenkripsi** (EncryptedSharedPreferences + Android Keystore) dan **tidak pernah** ada di repo atau APK.
- Free tier punya batas permintaan per menit/hari. Jika batas tercapai, aplikasi otomatis retry dengan backoff. Jika masih gagal, foto bisa dimasukkan ke **antrian** dan diproses otomatis nanti.
- Model default diambil dari `remote/gemini_prompt.json` (`gemini-flash-latest`, dengan cadangan `gemini-2.5-flash`). Model bisa diganti di Pengaturan atau lewat remote config tanpa update APK.
- Tanpa API key, aplikasi tetap bisa dipakai dengan **input manual** dari database makanan lokal.

## 3. Setup keystore di GitHub Secrets

APK update hanya bisa dipasang di atas versi lama jika **ditandatangani dengan keystore yang sama**. Karena itu keystore disimpan di GitHub Secrets (base64), bukan di repo.

Buka **Settings → Secrets and variables → Actions → New repository secret**, lalu isi 4 secret berikut:

| Nama secret | Isi |
|---|---|
| `KEYSTORE_BASE64` | isi file `.jks` dalam base64 (satu baris) |
| `KEYSTORE_PASSWORD` | password keystore |
| `KEY_ALIAS` | alias kunci (mis. `hawdietpro`) |
| `KEY_PASSWORD` | password kunci (untuk PKCS12 sama dengan password keystore) |

Keystore untuk proyek ini sudah dibuatkan (file `hawdietpro-release.jks` + `SECRETS-GITHUB.txt`). **Simpan kedua file itu di tempat aman** (mis. Google Drive pribadi). Jika keystore hilang, update tidak bisa dipasang di atas versi lama. Anda harus uninstall dulu, jadi lakukan backup data sebelumnya.

Jika ingin membuat keystore baru sendiri (cukup sekali seumur hidup aplikasi), gunakan JDK/`keytool`, misalnya di GitHub Codespaces atau Termux:

```bash
keytool -genkeypair -v -keystore hawdietpro-release.jks -storetype PKCS12 \
  -alias hawdietpro -keyalg RSA -keysize 4096 -validity 36500
base64 -w0 hawdietpro-release.jks   # salin hasilnya ke secret KEYSTORE_BASE64
```

Jika secret belum diisi, workflow tetap mem-build dan menjalankan test, tetapi **tidak** membuat GitHub Release. Workflow juga menampilkan peringatan.

## 4. Cara kerja auto-update

### Lapis 1: update APK lewat GitHub Releases

```
push ke main ──► GitHub Actions
                  ├─ unit test
                  ├─ build release APK (signed, keystore dari Secrets)
                  │    versionCode = github.run_number
                  │    versionName = YYYY.MM.DD.<run_number>
                  └─ GitHub Release "build-<run_number>" + APK + changelog (dari commit message)

Aplikasi ──► GET api.github.com/repos/wahyusubrata10/dietapp/releases/latest
             (saat app dibuka + WorkManager tiap 6 jam, tanpa token)
          ──► versi lebih baru? → banner "Update tersedia" + changelog
          ──► unduh APK di background (default: hanya Wi-Fi), dengan progress
          ──► verifikasi: nama paket sama, versi lebih baru, tanda tangan SAMA
          ──► PackageInstaller → Anda cukup tap "Install"
```

- Pertama kali, Android meminta izin **"Izinkan dari sumber ini"** untuk Haw Diet Pro (sekali saja).
- Di **Pengaturan** tersedia opsi: unduh otomatis, hanya lewat Wi-Fi, cek update manual, dan info versi saat ini.
- **Data tidak hilang saat update.** Database memakai migrasi Room yang eksplisit (tanpa `fallbackToDestructiveMigration`), dan salinan database dibuat otomatis sebelum migrasi skema.
- Changelog diambil dari judul commit sejak release sebelumnya, jadi tulis judul commit yang jelas.
- Repo ini **public**, sehingga API Releases bisa diakses tanpa token. Jangan pernah menaruh token GitHub di APK.

### Lapis 2: remote config (tanpa install)

Lihat [bagian 5](#5-remote-config-update-instan-tanpa-install).

## 5. Remote config (update instan tanpa install)

Folder `/remote` diambil aplikasi dari
`https://raw.githubusercontent.com/wahyusubrata10/dietapp/main/remote/<file>`:

| File | Isi |
|---|---|
| `config.json` | feature flags, versi minimum app (`minSupportedVersionCode`), pengumuman, tips harian |
| `gemini_prompt.json` | model Gemini, system prompt, template prompt, `responseSchema`, ukuran foto |
| `food_db.json` | database nutrisi makanan Indonesia (per 100 g) |
| `insight_rules.json` | aturan skor gizi A–E, label, saran, dan insight otomatis |

Aturan:

- Setiap file punya `version`. Aplikasi **hanya memakai file yang versinya lebih baru** dari yang sedang dipakai. Setiap kali mengedit, **naikkan `version`**.
- `minAppVersionCode` adalah versi aplikasi minimum yang memahami format file tersebut. Jika aplikasi lebih lama, file diabaikan dan pengguna diarahkan untuk update (Lapis 1).
- File divalidasi sebelum dipakai (parsing JSON + validasi isi). File rusak **diabaikan**, dan aplikasi tetap memakai cache atau salinan bawaan, jadi tidak crash.
- Salinan bawaan di APK disalin otomatis dari `/remote` saat build, jadi sumbernya hanya satu.
- Sinkron terjadi saat aplikasi dibuka (bila sudah lebih dari 1 jam), lewat WorkManager periodik, atau manual dari **Pengaturan → Sinkron sekarang**.
- Push yang **hanya** mengubah `/remote/**` atau `*.md` tidak memicu build APK baru.

Contoh:

```jsonc
// config.json: tampilkan pengumuman & paksa update untuk versi lama
{
  "version": 2,
  "minSupportedVersionCode": 15,
  "announcement": { "id": "ramadan-2027", "title": "Mode Ramadan", "message": "Tips sahur & berbuka sehat sudah tersedia!", "level": "info" },
  "features": { "insights": true, "ai_analysis": true }
}
```

```jsonc
// food_db.json: tambah makanan (nilai per 100 g), lalu naikkan "version"
{ "id": "nasi_campur_bali", "name": "Nasi campur Bali", "aliases": ["nasi campur"], "category": "hidangan",
  "kcal": 175, "protein": 7.5, "carbs": 22, "fat": 6.5, "fiber": 1.2, "servingG": 350, "servingLabel": "1 piring", "source": "POPULER" }
```

```jsonc
// insight_rules.json: aturan skor (metric: carbsPct, fatPct, proteinPct, fiberG, vegGrams, targetShare, friedCount, ...)
{ "id": "tinggi_karbo", "metric": "carbsPct", "op": ">", "value": 60, "points": -15, "minCalories": 150,
  "label": "Tinggi karbo", "suggestion": "Kurangi nasi setengah porsi, tambah sayur." }
```

## 6. Fitur

- **Foto → hasil.** Kamera (CameraX) atau galeri. Waktu makan ditebak otomatis dari jam. Ada catatan opsional untuk AI. Foto dikecilkan (maks ±1024 px) sebelum dikirim, lalu disimpan terkompresi di penyimpanan internal.
- **Kartu hasil yang bisa diedit.** Ubah nama elemen (otomatis dicocokkan ke DB lokal), atur gram dengan slider, hapus, atau tambah elemen yang terlewat. Kalori dan skor dihitung ulang otomatis. Setiap angka diberi badge sumber **AI / DB lokal / Manual**.
- **Input manual cepat.** Cari di DB lokal (±1.500 makanan Indonesia: bahan dasar, masakan daerah, jajanan, minuman, plus kombinasi lauk × bumbu seperti "ayam suwir bumbu bali" atau "semur jengkol") atau isi kalori sendiri, lalu simpan ke DB untuk dipakai lagi. Ejaan lain dikenali otomatis (suir/suwir, cabe/cabai, telor/telur, sambel/sambal, dll).
- **Antrian offline.** Tanpa internet atau saat kuota habis, foto masuk antrian dan dianalisa otomatis saat online (WorkManager). Hasilnya langsung tercatat dan muncul notifikasi.
- **Analisa per foto.** Skor A–E, label penilaian, saran realistis, dan catatan AI.
- **Harian.** Ring kalori masuk vs target, sisa kalori, progress protein/karbo/lemak/serat, ringkasan per waktu makan, insight hari ini, dan tips.
- **Mingguan & bulanan.** Grafik tren kalori vs target (Vico), rata-rata makro, hari over/under/sesuai target, kalori per waktu makan, pola jam makan, menu paling sering, sumber kalori terbesar, insight otomatis berbasis aturan, dan tren berat badan.
- **Target personal.** BMR Mifflin-St Jeor × aktivitas = TDEE, dengan batas aman:
  - defisit maks 500 kkal/hari dan 20% TDEE,
  - surplus maks 300 kkal/hari,
  - batas bawah 1200 kkal (wanita) / 1500 kkal (pria),
  - tidak ada defisit bila BMI < 18,5.
- **Log berat badan** dengan grafik tren dan BMI.
- **Makan lagi & favorit.** Tandai ★ menu favorit, catat ulang menu lama dengan 1 ketukan, atau "Sama seperti kemarin?" untuk menyalin satu waktu makan dari hari sebelumnya.
- **Porsi cepat.** Tombol ½ · ¾ · 1 · 1½ · 2 porsi untuk menskalakan semua elemen sekaligus.
- **Gula & natrium.** Dihitung per elemen (DB lokal, atau estimasi AI bila DB belum punya data), dibandingkan batas harian Kemenkes (gula 50 g, natrium 2.000 mg).
- **Saran menu AI.** Dari Beranda: saran 3–4 menu realistis sesuai sisa kalori, kekurangan protein/serat, tempat makan, dan catatan Anda.
- **Pencapaian.** Badge kebiasaan (streak, sarapan, sayur, protein, tepat target, gula & garam terkendali, rajin menimbang, progres berat) dihitung dari data, jadi tetap ada setelah restore backup.
- **Riwayat.** Timeline dan kalender (warna sesuai/lebih/kurang target), pencarian menu/elemen, filter waktu makan dan skor.
- **Backup.** ZIP (data + foto) untuk ekspor dan impor (gabung atau ganti), plus **CSV** untuk Excel (pemisah `;`, desimal koma).
- **Tema.** Dark mode (ikuti sistem, terang, atau gelap) dan seluruh UI berbahasa Indonesia.

## 7. Arsitektur & struktur folder

Kotlin + Jetpack Compose (Material 3), Hilt, Coroutines/Flow, Room, WorkManager, CameraX, OkHttp + kotlinx.serialization, Coil, dan Vico. Proyek memakai satu modul Gradle dengan pemisahan per paket (MVVM / clean architecture). Paket `domain/` berisi Kotlin murni tanpa Android, sehingga mudah di-unit-test.

```
.github/workflows/build-release.yml   build + test + sign + GitHub Release
remote/                               remote config (sumber tunggal, ikut dibundel ke APK)
app/src/main/java/com/hawdietpro/app/
├── core/          ImageProcessor, NetworkMonitor, Notifications, Format
├── di/            modul Hilt
├── data/
│   ├── local/     Room: entity, dao, migrations, backup pra-migrasi, mapper
│   ├── gemini/    klien REST Gemini + model + error berbahasa Indonesia
│   ├── remote/    RemoteConfigStore (cache + validasi) & RemoteConfigSync
│   ├── update/    UpdateRepository (GitHub Releases, verifikasi tanda tangan, PackageInstaller)
│   ├── backup/    ZIP & CSV
│   ├── prefs/     DataStore + penyimpanan API key terenkripsi
│   └── repository/ Meal, Food (seeder DB), Profile, Weight, Pending, MealAnalyzer
├── domain/
│   ├── model/     model inti
│   ├── nutrition/ TdeeCalculator, NutritionCalculator, FoodMatcher
│   ├── scoring/   MealScorer (A–E) & FoodClassifier
│   ├── insight/   PeriodStatsCalculator & InsightEngine
│   └── config/    model & validator remote config
├── work/          PendingAnalysisWorker, UpdateCheckWorker, UpdateDownloadWorker
└── ui/            tema, komponen, navigasi, dan layar per fitur
app/src/test/      unit test (TDEE, nutrisi, pencocokan DB, skor, insight, validasi remote)
```

## 8. Database & aturan migrasi

Entity Room: `UserProfile`, `MealEntry`, `FoodElement`, `FoodReference`, `WeightLog`, `PendingAnalysis`.

Jika mengubah skema:

1. Naikkan `HawDatabase.DATABASE_VERSION`.
2. Tambahkan `Migration(old, new)` di `Migrations.ALL`. **Jangan** memakai `fallbackToDestructiveMigration`.
3. Skema JSON diekspor ke `app/schemas/` saat build. Commit file tersebut agar riwayat skema tersimpan.

Sebelum database versi lama dibuka oleh versi baru, `PreMigrationBackup` menyalin file database ke `files/backups/` (3 salinan terakhir).

## 9. Build & test

Build otomatis berjalan di GitHub Actions (tidak perlu Android Studio). Jika ingin build lokal (butuh JDK 17 + Android SDK):

```bash
./gradlew testReleaseUnitTest   # unit test
./gradlew assembleDebug         # APK debug (package .debug, bisa terpasang berdampingan)
```

## 10. Troubleshooting

| Masalah | Solusi |
|---|---|
| Workflow tidak jalan | Aktifkan GitHub Actions di tab **Actions** repo. Di **Settings → Actions → General**, set *Workflow permissions* ke **Read and write**. |
| Release tidak dibuat | Cek 4 secret keystore. Lihat peringatan di log workflow. |
| "API key tidak valid" | Buat ulang key di AI Studio, lalu tempel ulang di Pengaturan. |
| "Batas pemakaian gratis tercapai" | Tunggu sebentar, atau simpan ke antrian (otomatis diproses nanti). |
| Update gagal dipasang | Pastikan APK dibuat dengan keystore yang sama. APK debug/beda keystore akan ditolak demi keamanan. |
| Angka gizi terasa kurang pas | Edit gram atau elemen di kartu hasil, atau koreksi `remote/food_db.json` lalu naikkan `version`. |

---

Data nutrisi bersumber dari TKPI (Tabel Komposisi Pangan Indonesia) dan perkiraan untuk makanan olahan populer. Aplikasi ini bukan alat medis; konsultasikan target diet dengan tenaga kesehatan bila memiliki kondisi khusus.
