# LauncherYou + SetelanYou

Launcher Android bertema Material You (Kotlin + Jetpack Compose), dengan layar setelan sendiri
(SetelanYou) dan Control Center bergaya iOS.

## Cara membuka
1. Buka folder ini dengan Android Studio (Ladybug atau lebih baru). Biarkan Gradle sync selesai.
2. Jalankan modul `app` di perangkat atau emulator Android 8.0+ (warna dinamis butuh Android 12+).
3. Tekan Home, lalu pilih LauncherYou sebagai launcher bawaan.

## Fitur
- Warna dinamis dari wallpaper (Android 12+), atau warna kustom dari satu rona.
- Ikon bertema (monokrom, Android 13+), bentuk ikon, jumlah kolom, ukuran ikon, label.
- Laci aplikasi dengan pencarian. Tekan lama ikon: pasang di beranda, info aplikasi, copot.
- Control Center: geser ke bawah dari sisi kanan atas beranda.
- SetelanYou: aplikasi terpisah (muncul di laci) berisi semua kustomisasi tema.

## Batasan Android
Wi-Fi, Bluetooth, mode pesawat, dan data seluler tidak bisa dinyalakan langsung oleh aplikasi biasa
sejak Android 10/12, jadi tombolnya membuka panel/setelan sistem. Kecerahan dan kunci rotasi butuh izin
"Ubah setelan sistem", dan Jangan Ganggu butuh akses Jangan Ganggu. Tombol izinnya ada di SetelanYou.
Tombol media mengirim perintah putar/jeda/lanjut ke pemutar yang aktif.
