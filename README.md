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

## Protección visible de configuración y supervisión de uso

En **Child** hay dos opciones independientes y voluntarias:

1. **Administración de dispositivo:** abre el diálogo nativo de Android mediante `ACTION_ADD_DEVICE_ADMIN`. Activar la opción hace que desinstalar una aplicación administradora requiera desactivar primero el permiso de administrador en Ajustes. **No bloquea definitivamente** la desinstalación, los ajustes, el restablecimiento de fábrica ni la detención de una captura autorizada.
2. **Estadísticas locales:** abre `ACTION_USAGE_ACCESS_SETTINGS` para que se conceda manualmente acceso a los tiempos de uso y muestra las cinco aplicaciones con mayor tiempo de uso durante las últimas 24 horas. Los datos no salen del teléfono.

El permiso de administrador tradicional NO equivale a **Device Owner**. Para una política gestionada más resistente a cambios se requiere una solución de Android Enterprise/Device Policy Controller y aprovisionamiento formal del dispositivo, normalmente durante la configuración inicial, con avisos claros. No se activa por root ni se instala de manera silenciosa. Este repositorio no implementa Device Owner ni bloqueo remoto persistente.

La sesión de pantalla siempre requiere consentimiento del sistema y ofrece detener la sesión incluso si la administración de dispositivo está activa. No incluye permisos de accesibilidad para control remoto, ni funciones de esconder el icono o permanecer invisible.
