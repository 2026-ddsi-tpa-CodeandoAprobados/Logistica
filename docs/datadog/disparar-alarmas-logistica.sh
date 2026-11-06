#!/usr/bin/env bash
# Deja en rojo los dos monitores de Logistica que se pueden provocar en la demo:
#   1) "Logistica: se estan rechazando donaciones por capacidad"  (mas de 2 rechazos en 10 minutos)
#   2) "Logistica: un deposito supera el 90% de su capacidad"      (ocupacion mayor a 0.9)
#
# Que hace:
#   - Crea un deposito de capacidad 10 llamado "demo-alarmas".
#   - Le manda 3 donaciones de 150 unidades. No entran: Logistica responde 409 y suma un rechazo por cada una.
#   - Le manda 1 donacion de 10 unidades. Entra: el deposito queda lleno (100%).
#   - Comprueba en /actuator/metrics que la ocupacion maxima haya subido.
#
# Los monitores tardan 2 a 4 minutos en pasar a Alert. Conviene correrlo unos 10 a 15 minutos antes de la demo.
#   - El de rechazos se apaga solo 10 minutos despues de la ultima donacion rechazada.
#   - El de ocupacion queda en rojo mientras el deposito siga lleno. Se apaga con "limpiar" o al reiniciar
#     la base con la coleccion de Postman (que empieza con DELETE /testing/reset).
#
# Uso:
#   ./disparar-alarmas-logistica.sh            dispara las dos alarmas
#   ./disparar-alarmas-logistica.sh limpiar    borra TODOS los datos de Logistica (pide confirmacion)
#   BASE=http://localhost:8083 ./disparar-alarmas-logistica.sh      contra otra direccion
#
# Nota: los textos van sin acentos a proposito, porque un acento mal codificado rompe el JSON y da 400.

BASE="${BASE:-https://logistica-entrega3.onrender.com}"

if [ "$1" = "limpiar" ]; then
  echo "Esto borra TODOS los depositos, paquetes y asignaciones de $BASE."
  read -r -p "Escribi SI para continuar: " respuesta
  if [ "$respuesta" != "SI" ]; then echo "Cancelado."; exit 1; fi
  codigo=$(curl -s -o /dev/null -w "%{http_code}" --max-time 120 -X DELETE "$BASE/testing/reset")
  echo "DELETE /testing/reset -> HTTP $codigo (se espera 200)"
  exit 0
fi

echo "Logistica en $BASE"
echo
echo "1) Creando el deposito de capacidad 10..."
respuesta=$(curl -s --max-time 120 -X POST "$BASE/depositos" -H "Content-Type: application/json" \
  -d '{"nombre":"demo-alarmas","direccion":"Medrano 951","capacidadMaxima":10}')
ID=$(echo "$respuesta" | sed -E 's/.*"id":"([0-9]+)".*/\1/')
if ! [[ "$ID" =~ ^[0-9]+$ ]]; then
  echo "   No se pudo crear el deposito. Respuesta: $respuesta"
  exit 1
fi
echo "   deposito $ID creado"

echo
echo "2) Enviando 3 donaciones de 150 unidades (no entran, se esperan 409)..."
for i in 1 2 3; do
  codigo=$(curl -s -o /dev/null -w "%{http_code}" --max-time 60 -X POST "$BASE/donaciones" \
    -H "Content-Type: application/json" -H "X-Trace-Id: demo-rechazo-$i" \
    -d "{\"id\":\"demo-rechazo-$i\",\"donadorID\":\"donador-demo\",\"depositoID\":\"$ID\",\"descripcion\":\"no entra\",\"detallesProductosDTO\":[{\"id\":null,\"productoID\":\"99901\",\"cantidadProducto\":150}]}")
  echo "   donacion $i -> HTTP $codigo"
done

echo
echo "3) Enviando 1 donacion de 10 unidades (llena el deposito)..."
codigo=$(curl -s -o /dev/null -w "%{http_code}" --max-time 60 -X POST "$BASE/donaciones" \
  -H "Content-Type: application/json" -H "X-Trace-Id: demo-llenado-1" \
  -d "{\"id\":\"demo-llenado-1\",\"donadorID\":\"donador-demo\",\"depositoID\":\"$ID\",\"descripcion\":\"llena el deposito\",\"detallesProductosDTO\":[{\"id\":null,\"productoID\":\"99902\",\"cantidadProducto\":10}]}")
echo "   donacion -> HTTP $codigo (se espera 201)"

echo
echo "4) Esperando 12 segundos a que el Worker guarde el stock..."
sleep 12
ocupacion=$(curl -s --max-time 30 "$BASE/actuator/metrics/logistica.ocupacion_maxima" | sed -E 's/.*"value":([0-9.]+).*/\1/')
echo "   ocupacion maxima de los depositos: $ocupacion (tiene que ser 1.0)"

echo
if [ "$ocupacion" = "1.0" ]; then
  echo "LISTO. En Datadog (Monitors > Manage Monitors) los dos monitores pasan a Alert en 2 a 4 minutos."
else
  echo "ATENCION: la ocupacion no llego a 1.0. Puede que el Worker este tardando: probá GET $BASE/depositos/$ID"
fi
