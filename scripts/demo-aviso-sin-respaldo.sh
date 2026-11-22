#!/usr/bin/env bash
# Demo de RF 7.6 — "Llevas 3 días sin respaldar tu información…" sin esperar 3 días.
#
# Requisitos: APK de depuración (usa run-as), teléfono conectado por USB, sesión iniciada y el
# permiso de notificaciones concedido.
#
# Qué hace:
#   1. Activa el modo avión (para que la app no sincronice) y te pide registrar algo en la app
#      (una venta, una entrada…), que quedará pendiente de sincronizar.
#   2. Detiene la app, cambia la última sincronización a hace 4 días y borra la fecha del último aviso.
#   3. Abre la app: la revisión al abrir encuentra pendientes y más de 3 días → aparece el aviso.
#   Al terminar, quita el modo avión: la app sincroniza y el ciclo del aviso vuelve a empezar.
#
# Uso: scripts/demo-aviso-sin-respaldo.sh [serie-del-teléfono]   (la serie sale en `adb devices`)
set -euo pipefail

PAQUETE=mx.ambulantpoint.misventas.demo
PREFS=shared_prefs/mis_ventas_sync.xml

ADB=adb
if ! command -v adb >/dev/null 2>&1; then
  for c in "${LOCALAPPDATA:-}/Android/Sdk/platform-tools/adb.exe" "$HOME/Android/Sdk/platform-tools/adb"; do
    if [ -x "$c" ]; then ADB="$c"; break; fi
  done
fi
SERIE=()
if [ $# -ge 1 ]; then SERIE=(-s "$1"); fi
adb_() { "$ADB" "${SERIE[@]}" "$@"; }

HACE_4_DIAS=$(( ($(date +%s) - 4 * 24 * 3600) * 1000 ))
TMP=$(mktemp)
trap 'rm -f "$TMP"' EXIT

echo "1) Activando modo avión…"
adb_ shell cmd connectivity airplane-mode enable < /dev/null
read -r -p "   Registra algo en la app (ej. una venta) y presiona Enter… "

echo "2) Deteniendo la app y retrocediendo la última sincronización 4 días…"
adb_ shell am force-stop "$PAQUETE" < /dev/null
adb_ exec-out run-as "$PAQUETE" cat "$PREFS" < /dev/null | tr -d '\r' > "$TMP"
sed -i '/ultimo_aviso_sin_respaldo/d; /ultima_sincronizacion/d' "$TMP"
sed -i "s#</map>#    <long name=\"ultima_sincronizacion\" value=\"$HACE_4_DIAS\" />\n</map>#" "$TMP"
adb_ exec-in run-as "$PAQUETE" sh -c "cat > $PREFS" < "$TMP"
adb_ exec-out run-as "$PAQUETE" cat "$PREFS" < /dev/null

echo "3) Abriendo la app: el aviso debe aparecer en unos segundos."
adb_ shell monkey -p "$PAQUETE" -c android.intent.category.LAUNCHER 1 < /dev/null >/dev/null 2>&1
echo "   Al terminar, quita el modo avión (o: adb shell cmd connectivity airplane-mode disable)."
