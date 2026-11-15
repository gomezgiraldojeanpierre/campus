# Decisiones técnicas de CampusHub

## 1. Qué guarda cada motor

**PostgreSQL** guarda departamentos, docentes, grupos, estudiantes, actividades, inscripciones, fichas de contacto y etiquetas.
Porque estos datos tienen una estructura estable y relaciones entre sí. Necesitan claves foráneas, unicidad, `CHECK` y transacciones, y PostgreSQL los protege en el propio almacenamiento.

**MongoDB** guarda solo los seguimientos de las actividades.
Porque cada tipo de actividad produce datos distintos (taller, visita, charla) y pueden aparecer tipos nuevos. Un documento conserva exactamente la estructura recibida, y los registros antiguos siguen siendo legibles cuando el formato evoluciona. Además, un seguimiento no se relaciona con otros registros más que por el identificador de su actividad.

## 2. Alternativas consideradas

- **Todo en PostgreSQL con `jsonb`.** Es viable y tendría una ventaja clara: una sola base, una transacción y una clave foránea real hacia la actividad. Lo descartamos porque el enunciado exige usar ambos motores. En un proyecto real habría que valorarlo seriamente.
- **Todo en MongoDB.** Lo descartamos porque las inscripciones necesitan unicidad compuesta, control de aforo bajo concurrencia y claves foráneas. MongoDB no ofrece claves foráneas.
- **Seguimientos dentro del documento de la actividad.** Lo descartamos porque la actividad vive en PostgreSQL y el documento crecería sin límite.
- **Una tabla por tipo de seguimiento.** Lo descartamos porque cada tipo nuevo exigiría una migración.

## 3. Riesgos de usar dos motores

No hay transacción distribuida entre PostgreSQL y MongoDB. Pueden aparecer estas inconsistencias:

1. **Seguimiento huérfano.** Si se borrase una actividad en PostgreSQL, sus seguimientos seguirían en MongoDB. La API no permite borrar actividades, y esa es la primera defensa.
2. **Escritura a medias.** El servicio comprueba que la actividad existe en PostgreSQL y después escribe en MongoDB. Si MongoDB cae entre las dos operaciones, la petición falla y no queda ningún dato a medias. El caso contrario no existe, porque el seguimiento es lo único que se escribe en MongoDB.
3. **Lectura parcial del detalle.** Si MongoDB no responde, el detalle devuelve un error y no una respuesta incompleta.

Para reducir el riesgo conviene comprobar siempre la existencia en PostgreSQL antes de escribir en MongoDB, que es lo que hacemos. Si se añadiesen borrados, lo correcto sería eliminar primero los seguimientos y después la actividad, o tener un proceso periódico que detecte huérfanos.

## 4. Mecanismos de integridad

- Las reglas viven en la base de datos (`UNIQUE`, `CHECK`, `NOT NULL`, claves foráneas), no solo en el servicio.
- El servicio comprueba antes los casos habituales para devolver el código correcto: 400 por datos inválidos, 404 por recursos inexistentes y 409 por duplicados o falta de plazas.
- Si dos peticiones coinciden y se salta la comprobación, la restricción de la base de datos lanza `DataIntegrityViolationException`, que el manejador convierte en 409 sin exponer SQL.
- El aforo se controla con un bloqueo de fila (`SELECT ... FOR UPDATE`) sobre la actividad.
- El esquema se genera con anotaciones de JPA. Elegimos este mecanismo porque deja el esquema junto a las entidades y evita mantener scripts SQL aparte.

## 5. Limitaciones

- `ddl-auto: update` solo crea las restricciones al crear la tabla. Si una tabla ya existía sin `CHECK`, hay que borrar el volumen de Docker o migrar a mano. En producción conviene Flyway o Liquibase.
- Las claves del objeto `datos` de un seguimiento no pueden contener puntos, porque Spring Data MongoDB las rechaza. El servicio devuelve un 400 en ese caso.
- Los correos se guardan en minúsculas. El servicio compara los nombres de etiquetas y departamentos sin distinguir mayúsculas, así que `Java` y `java` dan un 409. La restricción `UNIQUE` de la base de datos sí distingue mayúsculas, por tanto solo protege los duplicados exactos si dos peticiones coinciden a la vez.
- No hay paginación en las consultas de listas.
- No hay autenticación ni autorización.
- La caché de Redis puede mostrar un detalle con hasta 60 segundos de retraso en un caso poco frecuente (ver el apartado 6).

## 6. Caché con Redis (ampliación)

- **Qué se guarda:** la respuesta de `GET /api/actividades/{id}/detalle`. Porque es la consulta que más se repite y combina datos de PostgreSQL y de MongoDB.
- **Clave:** `actividad:{id}:detalle`. Cada actividad tiene su propia entrada, así que un cambio invalida solo esa actividad.
- **Tiempo de vida:** 60 segundos. Es una red de seguridad por si alguna invalidación fallase.
- **Qué invalida la entrada:** inscribir a un estudiante, asociar una etiqueta y registrar un seguimiento. La entrada se borra después de que PostgreSQL confirme la transacción (`afterCommit`). Así ninguna lectura puede guardar de nuevo el dato antiguo antes de que el cambio sea definitivo.
- **Riesgo de datos obsoletos:** si una consulta empieza justo antes de un cambio y termina justo después de la invalidación, puede guardar el valor antiguo. Ese valor desaparece como máximo a los 60 segundos.
- **Si Redis cae:** cada operación de caché captura el error, escribe un aviso en el registro y la API consulta las bases de datos. Los tiempos de espera son de 500 ms para no bloquear las peticiones.
- **Control de plazas:** no depende nunca de la caché. Lo garantizan el bloqueo de fila en PostgreSQL y la restricción `UNIQUE (actividad_id, estudiante_id)`. La caché solo guarda lecturas.
- **Interruptor:** la propiedad `campushub.cache.enabled` activa la caché. Está activa en `application.yaml` y desactivada por defecto en los tests, salvo en `CacheDetalleIT`. Así los tests no tocan el Redis del entorno de desarrollo.

## 7. Decisiones que cambiaron respecto al diseño inicial

- Los seguimientos se guardaban en la base `test` de MongoDB y no en `campushub`, porque Spring Boot 4 no toma el nombre de la base de la URI. Lo corregimos con la propiedad `spring.data.mongodb.database` y lo comprobamos con `mongosh`.
- Los tests de integración fallaban al ejecutarse juntos, porque Spring reutilizaba un contexto apuntando a contenedores ya parados. Añadimos `ContextoSucioTrasClaseListener` en `src/test`, sin modificar ninguna prueba existente.
