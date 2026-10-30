# Mis finanzas

Aplicación Android personal en español. Local-first, captura manual, moneda MXN y sin Firebase.

## Funciones implementadas

- Gastos e ingresos con fecha, categoría y nota; edición, eliminación confirmada y filtros por mes, tipo y categoría.
- Categorías personalizadas, edición de nombre y archivado conservando el historial.
- Gastos fijos y sueldos semanales, cada 15 días o mensuales; confirmar el importe y fecha reales registra el movimiento y avanza el vencimiento sin duplicarlo.
- Metas de ahorro con objetivo, fecha opcional, aportaciones, retiros y progreso. El ahorro no se contabiliza como gasto/ingreso.
- Inicio con resumen mensual, pendientes y últimos movimientos; desglose por categoría y comparación con el mes anterior.
- Respaldos manuales cifrados y restauración completa validada y transaccional. Se guarda una copia previa exportable antes de reemplazar los datos.
- Tema claro/oscuro según el sistema.

## Respaldos y Google Drive

En Más → Respaldos, elige Crear respaldo, define una contraseña y selecciona el destino en el selector del sistema. Puedes elegir Drive si está instalado, tiene una cuenta configurada y aparece como proveedor; también puedes guardar el archivo localmente y subirlo manualmente.

Restaurar respaldo usa el mismo selector para abrir una copia `.fpb`. Introduce la contraseña de ese archivo y confirma el reemplazo. La contraseña no se almacena ni se puede recuperar. La app confirma la escritura al proveedor, no una subida remota finalizada: comprueba el estado en Drive.

La copia previa a restauración se exporta desde el botón correspondiente y usa la contraseña introducida en esa restauración. Una nueva restauración reemplaza la copia previa interna. Exporta esa copia antes si deseas conservar varias versiones.

No requiere Firebase, backend propio, proyecto Google Cloud, OAuth propio, permisos generales de almacenamiento ni acceso a internet desde esta app. Drive realiza su propia transferencia. No hay sincronización automática ni combinación de bases.

## Arquitectura

Un módulo `app` con Clean Architecture + MVVM:

- `domain/model`: modelos y reglas monetarias/recurrencias/validación.
- `domain/repository`: contrato de persistencia.
- `domain/usecase`: confirmación de vencimientos.
- `data/local`: tablas y DAO Room, con esquema exportado.
- `data/repository`: implementación transaccional y mapeos.
- `data/backup`: formato versionado, cifrado y acceso al proveedor de archivos.
- `presentation`: pantallas Compose, ViewModel y StateFlow.
- `di`: Hilt e inyección por constructor.

Room es la fuente de verdad. Importes en centavos Long, sin Float/Double en cálculos monetarios. DataStore conserva la fecha del último archivo de respaldo escrito; ese metadato es propio del dispositivo y no se importa.

## Compilar y probar

SDK Android 35 y JDK 21 para el daemon (según la configuración de toolchain del proyecto); código Java/Kotlin dirigido a JVM 17. Wrapper Gradle 8.11.1 y AGP 8.9.2.

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```

Las pruebas locales cubren importes, límites, recurrencia mensual, separación del ahorro e integridad referencial. Las instrumentadas cubren confirmaciones duplicadas, restauración Room, round trip cifrado, contraseña incorrecta y corrupción.

## Límites de esta entrega

- Moneda MXN fija, sin cuentas bancarias; saldo calculado desde los movimientos registrados.
- Cada 15 días no significa dos fechas fijas al mes.
- Las programaciones se confirman manualmente, sin notificaciones automáticas.
- Los archivos están cifrados; la base Room usa el almacenamiento privado de Android, sin SQLCipher adicional.
- Drive depende del proveedor del sistema; no hay integración REST ni navegador propio de respaldos.
- Recuperar respaldos exige conservar su contraseña.

## Referencias oficiales

- [Room](https://developer.android.com/training/data-storage/room)
- [Selector de documentos y proveedores en la nube](https://developer.android.com/training/data-storage/shared/documents-files)
- [Compatibilidad AGP 8.9](https://developer.android.com/build/releases/agp-8-9-0-release-notes)



## Validación de la versión 1.0 (29 de octubre de 2026)

- `assembleDebug`: correcto. APK firmado con la clave de depuración, sin datos de ejemplo incorporados.
- Cuatro pruebas locales y dos instrumentadas: correctas. Incluyen arranque del ViewModel, persistencia y restauración.
- `lintDebug`: sin errores; quedan avisos de versiones disponibles y recursos heredados de la plantilla.
- Emulador Medium Phone con Android 17: arranque, captura manual y persistencia tras reapertura verificados.
- Recorrido manual: confirmar renta de $5,000, cobrar sueldo de $10,000 y aportar $500 al ahorro; totales separados y correctos.
- Selector del sistema: exportar un archivo cifrado y restaurarlo manualmente mantuvo los totales; también se verificó la copia previa.
- Segunda pasada de frontend: acceso directo a ingresos, calendario, errores de montos, secciones de Resumen/Categorías/Respaldos y revisión visual clara/oscura.
- Google Drive con cuenta real y teléfono físico: pendientes de verificación. Se usa el proveedor del selector del sistema, no la API REST.

El APK entregado está en la raíz como `Finanzas Personales v1.0.apk`.


## Saldo actual

Inicio muestra el dinero registrado disponible hasta hoy: ingresos menos gastos de contado y pagos a tarjetas/deudas, incluyendo meses anteriores. Las compras a crédito se reflejan como gasto y deuda pendiente; no reducen este dinero hasta registrar el abono. No cambia al navegar entre meses. Los movimientos futuros y programados sin confirmar no lo alteran. Las aportaciones/retiros de ahorro no lo modifican: el saldo incluye el dinero apartado en metas. El cálculo parte de cero y de los registros de la app, sin consultar saldos bancarios.

Validación del saldo actual: cinco pruebas locales correctas, compilación y Lint sin errores; comprobado en emulador el resultado $1,000 - $250 = $750, su independencia del mes seleccionado y conservación de datos al actualizar el APK.


## Tarjetas, otras deudas y compras MSI

En **Más → Tarjetas** registra una tarjeta de crédito con nombre, saldo que ya debías y fecha de ese saldo. Si comienzas sin deuda, deja el importe en cero. No se pide número de tarjeta ni CVV. **Más → Deudas** sirve para otros préstamos o deudas. En **Programados → Agregar compra MSI** también puedes registrar la tarjeta sin perder lo que llevas capturado.

Una compra MSI nueva genera deuda por el total. En **Gastos del mes**, los reportes y el historial aparece **solo la mensualidad que vence en el mes mostrado**, incluso si pagaste anticipadamente; el total de la compra no se suma como gasto de ese mes. El pago registrado reduce el saldo actual y la deuda en su fecha real, sin volver a sumarse al gasto. Una compra histórica permite indicar mensualidades pagadas antes de usar la app y si el saldo pendiente ya estaba registrado en la tarjeta.

Se pueden editar ingresos, gastos, aportaciones y retiros de ahorro, pagos de tarjetas/deudas y compras MSI. Al editar una compra MSI con pagos, se conservan sus importes y fechas reales; el saldo restante se reparte entre las mensualidades futuras. No se acepta un total menor de lo ya pagado ni un plazo menor que las mensualidades registradas. Desde el historial, **Editar** en una mensualidad abre la compra MSI completa. Los pagos MSI también admiten edición desde la tarjeta.

La base Room usa esquema 4 y migra los esquemas 1, 2 y 3 sin borrar registros. Los respaldos nuevos usan formato 4; se importan los formatos anteriores 1, 2 y 3. El respaldo sigue siendo manual y cifrado mediante el selector de archivos de Android; Google Drive aparece como destino si está instalado como proveedor. No se requiere Firebase.

El APK de esta versión está en la raíz como **Finanzas Personales v1.0.apk**. La versión conserva el nombre 1.0 solicitado.


## Día de corte de tarjetas (actualización)

Al registrar o editar una tarjeta se indica el día de corte (1 a 31). Una compra normal hecha hasta ese día cuenta en los gastos del mes de compra; si se hizo después, cuenta en los gastos del mes siguiente. Si la tarjeta corta el 15, una compra del 22 de octubre aparece en noviembre. Esto también se aplica a gastos fijos confirmados con la tarjeta. La fecha real de compra, la deuda pendiente y los pagos no se desplazan. Las compras MSI conservan su fecha de primera mensualidad elegida y cada mensualidad aparece en su mes de vencimiento. Las tarjetas existentes sin corte conservan el cálculo anterior hasta que se editen. La base Room usa esquema 5 y los nuevos respaldos formato 5; se importan formatos 1 a 4.


## Préstamos con cuotas finitas

En Programados → Agregar préstamo con cuotas registra nombre, importe por cuota, categoría de gasto, número de cuotas (1 a 600), próxima fecha, frecuencia semanal/cada 15 días/mensual y forma de pago (contado o tarjeta). Cada cuota cuenta como gasto fijo normal solo cuando la confirmas. Con tarjeta se crea un cargo a esa tarjeta y el saldo actual baja cuando registras su pago; contado baja al confirmar. El contador disminuye al confirmar y la programación termina tras la última cuota, conservando los movimientos registrados. Se pueden editar las cuotas pendientes sin modificar las ya confirmadas. No se registra un desembolso inicial del préstamo ni se reduce una deuda separada. Room esquema 6; respaldo formato 6 con lectura de formatos anteriores.


## Actualizaciones desde GitHub

En Más → Actualizaciones → Buscar actualización la app consulta manualmente el último Release público de `Meminzazo/FinanzasPersonales`. Si el tag tiene una versión mayor (por ejemplo, `v1.2`), muestra sus notas y permite descargar e instalar el APK incluido. La descarga se comprueba por tamaño, paquete, código de versión y firma; si GitHub publica una huella SHA-256 del asset, también se verifica. Android pide permiso para instalar desde esta app y confirmación final. No hay consulta ni descarga automática. Una respuesta 404 antes del primer Release se muestra como ausencia de actualización.

Para publicar la primera versión, crea un Release estable con tag `v1.1` y un único APK de esta app como asset. En cada versión posterior, incrementa `versionCode` y `versionName` en `app/build.gradle.kts`, usa el tag correspondiente (`v1.2`, etc.) y firma el APK con **la misma clave que la versión ya instalada**. El APK debug entregado se firma con la clave debug local de esta computadora: un APK firmado con otra clave no podrá instalarse encima sin desinstalar, lo que pondría en riesgo los datos locales. Guarda la clave de firma fuera del repositorio.
