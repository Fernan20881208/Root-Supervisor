# Root Supervisor

Proyecto Android de acompañamiento familiar con dos aplicaciones: **Child** y **Parent**.

## Privacidad y funcionamiento

- El teléfono que comparte la pantalla debe iniciar manualmente la sesión y autorizar el diálogo nativo de captura de Android.
- Durante toda la sesión hay un servicio de primer plano, notificación persistente y botón para detenerla.
- La visualización se permite solamente en la misma red Wi-Fi, con código efímero por sesión. No guarda capturas.
- Las imágenes se cifran con AES-GCM; cada solicitud requiere un HMAC y un nonce.
- No contiene acceso oculto por SurfaceFlinger, instalación silenciosa, ocultación del icono ni control remoto encubierto.
- Root no es necesario para compartir pantalla; podría usarse en el futuro para controles locales visibles.

## Compilar

Actions → **Build Android APKs** → Run workflow. Se generan dos APK (debug) como artefactos.

Requisitos: Android 10+ para ambos; Wi-Fi local. En el teléfono compartido abre Child, pulsa «Compartir pantalla» y acepta la autorización; en Parent introduce la IP y el código mostrados en Child.

**Limitaciones:** el visor está pensado para asistencia puntual en Wi-Fi doméstica; no implementa WebRTC ni transporte a través de Internet, audio o control remoto. El vídeo puede estar limitado a unas pocas imágenes por segundo.
