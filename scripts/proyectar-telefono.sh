#!/usr/bin/env bash
# Proyecta la pantalla del teléfono en la computadora (por cable USB) para exponer la demo.
# Usa scrcpy: no necesita red ni apps en el teléfono, y se puede manejar la app con el mouse y
# el teclado de la laptop o tocando el teléfono (los toques se ven en el proyector).
#
# Requisitos (una sola vez):
#   - Linux (CachyOS): sudo pacman -S scrcpy
#   - Windows: winget install Genymobile.scrcpy   (y correr este script desde Git Bash)
#   - En el teléfono: Opciones de desarrollador → Depuración por USB activada, y aceptar el
#     aviso "¿Permitir depuración por USB?" la primera vez que se conecte a esta computadora
#     (marcar "Permitir siempre desde esta computadora").
#
# Uso: scripts/proyectar-telefono.sh [opciones]
#   (sin opciones)  pantalla completa, abre la app demo (mx.ambulantpoint.misventas.demo)
#   --ventana       en ventana en lugar de pantalla completa
#   --app real|pruebas|demo|ninguna   qué app abrir al empezar (por defecto: demo)
#   --apagar        apaga la pantalla del teléfono mientras proyecta (se maneja desde la laptop)
#   --serie XXXX    si hay más de un teléfono conectado (la serie sale en `adb devices`)
#   --sin-audio     el sonido se queda en el teléfono (por defecto se manda a la laptop)
#   --audio-doble   el sonido suena en la laptop Y en el teléfono (Android 13 o más nuevo)
#
# Audio: el sonido del teléfono (Android 11 o más nuevo) sale por la salida de audio que tenga
# elegida la laptop. Para mandarlo a unas bocinas o al proyector (HDMI), elígelo en el ícono de
# volumen de KDE → Dispositivos de salida, o mueve solo «scrcpy» en la pestaña Aplicaciones.
# Mientras se proyecta, el teléfono deja de sonar por su bocina (salvo con --audio-doble).
#
# Atajos dentro de la ventana de scrcpy (MOD = tecla Alt izquierda o Super):
#   MOD+f  entrar/salir de pantalla completa     MOD+h  Inicio       MOD+b  Atrás
#   MOD+r  girar pantalla                         MOD+o  apagar pantalla del teléfono
#   MOD+g  tamaño 1:1                             Ctrl+c o cerrar la ventana: terminar
set -euo pipefail

APP=demo
COMPLETA=1
APAGAR=0
AUDIO=1
AUDIO_DOBLE=0
SERIE=()
while [ $# -gt 0 ]; do
  case "$1" in
    --ventana) COMPLETA=0 ;;
    --apagar)  APAGAR=1 ;;
    --sin-audio)   AUDIO=0 ;;
    --audio-doble) AUDIO_DOBLE=1 ;;
    --app)     APP="${2:?falta el nombre de la app}"; shift ;;
    --serie)   SERIE=(-s "${2:?falta la serie}"); shift ;;
    -h|--help) sed -n '2,30p' "$0"; exit 0 ;;
    *) echo "Opción desconocida: $1 (usa --help)"; exit 1 ;;
  esac
  shift
done

case "$APP" in
  demo)    PAQUETE=mx.ambulantpoint.misventas.demo ;;
  pruebas) PAQUETE=mx.ambulantpoint.misventas.pruebas ;;
  real)    PAQUETE=mx.ambulantpoint.misventas ;;
  ninguna) PAQUETE= ;;
  *) echo "App desconocida: $APP (demo, pruebas, real o ninguna)"; exit 1 ;;
esac

# adb: del PATH o del SDK de Android.
ADB=adb
if ! command -v adb >/dev/null 2>&1; then
  for c in "$HOME/Android/Sdk/platform-tools/adb" "${LOCALAPPDATA:-}/Android/Sdk/platform-tools/adb.exe"; do
    if [ -x "$c" ]; then ADB="$c"; break; fi
  done
fi
adb_() { "$ADB" "${SERIE[@]}" "$@"; }

if ! command -v scrcpy >/dev/null 2>&1; then
  echo "No encuentro scrcpy. Instálalo con:"
  echo "  Linux:   sudo pacman -S scrcpy"
  echo "  Windows: winget install Genymobile.scrcpy"
  exit 1
fi
# scrcpy trae su propio adb; usamos el mismo que este script para no tener dos servidores peleando.
export ADB

echo "Esperando el teléfono por USB… (si no aparece, revisa el cable y acepta el aviso de depuración en el teléfono)"
"$ADB" start-server >/dev/null
for _ in $(seq 1 30); do
  ESTADO=$(adb_ get-state 2>/dev/null || true)
  [ "$ESTADO" = "device" ] && break
  if "$ADB" devices | grep -q unauthorized; then
    echo "  El teléfono pide autorización: desbloquéalo y toca «Permitir» en el aviso de depuración USB."
  fi
  sleep 1
done
if [ "$(adb_ get-state 2>/dev/null || true)" != "device" ]; then
  echo "No se detectó el teléfono. Salida de adb devices:"
  "$ADB" devices
  exit 1
fi

MODELO=$(adb_ shell getprop ro.product.model | tr -d '\r')
echo "Conectado: $MODELO"

if [ -n "$PAQUETE" ] && ! adb_ shell pm list packages "$PAQUETE" | tr -d '\r' | grep -qx "package:$PAQUETE"; then
  echo "Aviso: la app $PAQUETE no está instalada; se proyecta sin abrirla."
  PAQUETE=
fi

OPCIONES=(
  --window-title "Mis Ventas — $MODELO"
  --stay-awake          # el teléfono no se bloquea mientras esté conectado
  --show-touches        # los toques se ven como círculos en el proyector
  --keyboard=uhid       # escribir en la app con el teclado de la laptop
  --video-bit-rate=8M
  --max-fps=60
)
[ "$COMPLETA" = 1 ] && OPCIONES+=(--fullscreen)
if [ "$AUDIO" = 0 ]; then
  OPCIONES+=(--no-audio)
else
  VERSION_ANDROID=$(adb_ shell getprop ro.build.version.sdk | tr -d '\r')
  if [ "${VERSION_ANDROID:-0}" -lt 30 ]; then
    echo "Aviso: este teléfono tiene Android anterior al 11; no se puede mandar el audio a la laptop."
    OPCIONES+=(--no-audio)
  else
    OPCIONES+=(--audio-source=output --audio-buffer=80)
    if [ "$AUDIO_DOBLE" = 1 ]; then
      if [ "$VERSION_ANDROID" -ge 33 ]; then OPCIONES+=(--audio-dup)
      else echo "Aviso: --audio-doble necesita Android 13 o más nuevo; el sonido saldrá solo en la laptop."; fi
    fi
  fi
fi
[ "$APAGAR" = 1 ] && OPCIONES+=(--turn-screen-off)
# Abre la app (si ya estaba abierta, la deja donde estaba).
[ -n "$PAQUETE" ] && OPCIONES+=(--start-app="$PAQUETE")

# Desbloquear/encender la pantalla si estaba apagada.
adb_ shell input keyevent KEYCODE_WAKEUP || true

echo "Proyectando. Alt+f alterna pantalla completa; cierra la ventana o Ctrl+c para terminar."
exec scrcpy "${SERIE[@]}" "${OPCIONES[@]}"
