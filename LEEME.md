# Aviso Pagos — app Android

Lee las notificaciones de Yape, Plin, Interbank y BBVA, **anuncia el pago en voz alta**
y lo **envía al Multichat** (o a tu hosting) como JSON.

## 1. Compilar

### Opción A (recomendada): con GitHub, sin instalar nada
1. Crea un repositorio en github.com (puede ser privado).
2. Sube TODO el contenido de esta carpeta (incluida la carpeta oculta `.github`).
3. GitHub compila solo (pestaña **Actions**, tarda ~5 min).
4. Entra a la pestaña **Releases** → "Aviso Pagos - última versión" → descarga
   `AvisoPagos.apk` **desde el celular** e instálalo.
Cada vez que se actualiza el código, el APK se vuelve a generar solo.

### Opción B: con Android Studio
1. Abre la carpeta `AvisoPagos` en **Android Studio** (File → Open).
2. Espera a que sincronice Gradle (descarga lo necesario solo).
3. Conecta el celular por USB con "Depuración USB" activa y pulsa ▶ Run.
   Para generar el APK: Build → Build APK(s).

## 2. Configurar en el celular
1. Abre **Aviso Pagos**.
2. Pulsa **"1. Dar acceso a notificaciones"** y activa *Aviso Pagos*.
3. Pulsa **"2. Excluir del ahorro de batería"** (en Xiaomi/Huawei revisa también
   "Inicio automático" en los ajustes del teléfono).
4. Escribe la **URL de destino** y pulsa **Guardar**.
5. Pulsa **"Probar"**: debe hablar y llegar un pago de prueba a tu servidor.

## 3. Calibrar (importante)
Los nombres de paquete y el texto exacto de cada notificación pueden variar.
Haz un pago real pequeño a cada cuenta y mira el **Registro**:
- Aparece el paquete de la app entre corchetes, ej. `[com.bcp.innovacxion.yapeapp]`.
- Si dice **(no escuchado)**, copia ese paquete a la lista "Apps a escuchar" y guarda.
- Si el monto o el nombre no salen bien, pásame el texto del registro y ajusto el lector.

## 4. Lo que recibe el Multichat
`POST` a la URL configurada, con cabecera `Authorization: Bearer <token>` si pusiste token:

Se envía **toda notificación** de las apps escuchadas, tal cual llegó:

```json
{
  "app": "Yape",
  "paquete": "com.bcp.innovacxion.yapeapp",
  "titulo": "Confirmación de Pago",
  "texto": "Juan Perez te envió un pago por S/ 50...",
  "fecha_ms": 1763648400000,
  "dispositivo": "Celular 1",
  "monto": "50.00",
  "pagador": "Juan Perez",
  "es_pago_recibido": true
}
```
`monto`, `pagador` y `es_pago_recibido` son una ayuda: si la app no logró leerlos
vienen en `null`/`false`, pero `titulo` y `texto` siempre llegan completos.
Responde con cualquier código 2xx para confirmar. Si no hay respuesta, la app guarda
el pago y lo reenvía después (hasta 200 pendientes).

### ¿A qué URL apuntar?
- **Multichat en la misma red WiFi:** `http://IP-DE-LA-PC:PUERTO/ruta` (la PC necesita
  IP fija y el puerto abierto en el firewall de Windows).
- **Varias PCs o fuera de la oficina:** apunta a tu **hosting**
  (`https://tudominio.com/api/pago`) y que cada Multichat lea de ahí.

## Notas
- Ignora pagos *salientes* ("enviaste", "pagaste", "yapeaste").
- Evita duplicados: la misma notificación en menos de 2 minutos se cuenta una sola vez.
- Funciona solo en Android (iPhone no permite leer notificaciones de otras apps).
