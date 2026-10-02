# LEADEN1 Volume Diagnostic V5.2

V5.2 corrige el registro dinámico del BroadcastReceiver en Android moderno mediante
ContextCompat.registerReceiver(..., RECEIVER_EXPORTED).

Mantiene el polling de STREAM_MUSIC cada 250 ms, diagnóstico de KeyEvent y
MediaSession mediante Notification Listener.

Prueba: instalar, activar "LEADEN1 Media Monitor V5.2" en Acceso a notificaciones,
volver a la app, actualizar sesiones, reproducir música y probar central, volumen +
y volumen -.

No modifica la PWA LEADEN1, VPS ni Nginx.
