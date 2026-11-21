# Pañol-App

Gestión y trazabilidad de tablets corporativas por QR. Android nativo (Kotlin), SQLite, ZXing. Documentación en `docs/Carpeta_Tecnica.md`.

## Obtener la APK (`panol_app.apk`)
**Opción A — GitHub Actions (sin instalar nada):** subí esta carpeta a un repo de GitHub; la pestaña *Actions → Compilar APK* produce el artefacto `panol_app.apk` (también se puede lanzar a mano con *Run workflow*).

**Opción B — Android Studio:** abrir la carpeta, esperar el sync de Gradle y *Build → Build APK(s)*. La APK queda en `app/build/outputs/apk/debug/app-debug.apk` (renombrar a `panol_app.apk`).

**Opción C — terminal** (con Android SDK instalado y `ANDROID_HOME` definido):
```bash
./gradlew assembleDebug
```

Instalar en el celular: copiar la APK y habilitar «instalar apps de origen desconocido».

## Probar
En `qr-ejemplos/` hay QR de prueba (`TAB-0001..3.png`) y uno inválido para probar el flujo de QR dañado. Mostralos en pantalla o imprimilos y escanealos con la app.
Flujo sugerido: *Alta* (escanear TAB-0001) → *Empleados y tareas* (legajo + tarea) → escanear → *Despachar* en Home Office → escanear → *Recibir devolución*.
