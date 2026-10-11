# Build APK dwiChat hanya dari HP Android

## 1. Buat repository GitHub
1. Buka https://github.com di browser HP dan login/buat akun.
2. Buat repository baru bernama `dwiChat`.
3. Ekstrak ZIP proyek ini di aplikasi pengelola file/arsip HP.
4. Upload seluruh ISI folder `dwiChat-project` ke repository (pastikan folder `.github/workflows` ikut terunggah).

## 2. Jalankan build
1. Buka repository GitHub > tab **Actions**.
2. Pilih workflow **Build dwiChat APK**.
3. Tekan **Run workflow** > **Run workflow**.
4. Tunggu sampai workflow selesai dengan tanda hijau.
5. Buka hasil run tersebut, cari bagian **Artifacts**, lalu unduh `dwiChat-debug-apk`.
6. Ekstrak file ZIP artifact dan instal `app-debug.apk` di HP. Jika diminta, izinkan instalasi dari sumber tersebut.

## Catatan penting
- Build ini menghasilkan APK debug untuk pengujian, bukan APK rilis Play Store.
- APK default menunjuk ke `10.0.2.2`, alamat khusus emulator Android Studio. Agar chat bekerja di HP sungguhan, backend harus di-deploy dan `API_BASE_URL` di `app/build.gradle.kts` harus diubah ke URL HTTPS backend yang aktif, lalu build ulang.
- Backend memerlukan Node.js dan PostgreSQL yang aktif. Workflow ini hanya membangun APK Android; tidak meng-host backend.
- Jangan commit password, token, atau file `.env` berisi rahasia ke repository publik.
