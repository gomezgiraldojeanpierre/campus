# CampusHub

API REST con Spring Boot para gestionar las actividades complementarias de un grupo de centros
educativos. La persistencia está repartida en dos motores:

- **PostgreSQL** guarda los datos relacionales: departamentos, docentes, grupos, estudiantes,
  actividades, inscripciones, fichas de contacto y etiquetas.
- **MongoDB** guarda los seguimientos de las actividades, porque su estructura varía según el tipo.

Documentación del diseño:

- [Modelo de datos](docs/modelo-datos.md): diagrama relacional, restricciones, índices y colección de MongoDB.
- [Decisiones técnicas](docs/decisiones-tecnicas.md): reparto entre motores, alternativas, riesgos y limitaciones.
- [Contrato HTTP](docs/api-contract.md): endpoints y campos de la API.

No se han modificado los endpoints, los DTO ni las pruebas entregadas. En `ApiExceptionHandler` solo
se ha reescrito un comentario. Se ha añadido
`ContextoSucioTrasClaseListener` en `src/test`, porque sin él los tests de integración fallan al
ejecutarse juntos (Spring reutiliza un contexto que apunta a contenedores ya parados).

## Comprobar que los datos están guardados

Con la aplicación arrancada y algún dato creado desde Swagger:

```bash
docker compose exec postgres psql -U campushub -d campushub -c "select id, titulo, tipo, max_participantes from actividad;"
```

```bash
docker compose exec mongo mongosh campushub --eval "db.seguimientos.find().pretty()"
```

Para ver el validador y el índice de MongoDB:

```bash
docker compose exec mongo mongosh campushub --eval "db.getCollectionInfos({name:'seguimientos'}); db.seguimientos.getIndexes()"
```

Para ver la caché de Redis, después de consultar `GET /api/actividades/1/detalle`:

```bash
docker compose exec redis redis-cli keys "actividad:*"
```

```bash
docker compose exec redis redis-cli ttl "actividad:1:detalle"
```

La caché se puede desactivar con `campushub.cache.enabled=false`. Si Redis no está disponible, la
API sigue funcionando y consulta directamente PostgreSQL y MongoDB.

Los datos se conservan al reiniciar la aplicación, porque los volúmenes de Docker mantienen las
bases de datos. Para empezar desde cero hay que ejecutar `docker compose down -v`.

Hibernate crea los `CHECK` y las restricciones únicas solo cuando crea la tabla. Si la base de
datos ya existía de antes con otro esquema, conviene ejecutar `docker compose down -v` para que se
cree de nuevo con todas las restricciones.

## Requisitos

- JDK 21 y Maven 3.9+.
- Docker Engine / Docker Desktop con Docker Compose v2.

## Estructura del proyecto

El código sigue una arquitectura por capas:

```text
controller/       endpoints REST y tratamiento de errores HTTP
controller/dto/   contrato de entrada y salida de la API
service/          casos de uso y reglas de negocio
repository/       repositorios Spring Data (JPA y MongoDB)
entity/           entidades JPA
document/         documentos MongoDB
mapper/           conversión entre entidades, documentos y DTO
cache/            caché del detalle de actividades en Redis
config/           inicialización de la colección de MongoDB
```

La conversión entre persistencia y DTO se hace a mano en `CampusMapper`.

## Arranque

```bash
mvn spring-boot:run
```

Spring Boot detecta `compose.yaml`, ejecuta automáticamente `docker compose up` antes de arrancar
y detiene los contenedores cuando finaliza la aplicación. Por tanto, no hace falta arrancar
PostgreSQL, MongoDB y Redis manualmente. Si se prefiere gestionarlos por separado:

```bash
docker compose up -d
mvn spring-boot:run -Dspring-boot.run.arguments=--spring.docker.compose.enabled=false
```

Consultar PostgreSQL:

```bash
docker compose exec postgres psql -U campushub -d campushub
```

Consultar MongoDB:

```bash
docker compose exec mongo mongosh campushub
```

Consultar Redis:

```bash
docker compose exec redis redis-cli
```

## Swagger y OpenAPI

Con la aplicación arrancada, la API se publica automáticamente en:

- Swagger UI: <http://localhost:8080/swagger-ui.html>
- OpenAPI JSON: <http://localhost:8080/v3/api-docs>
- OpenAPI YAML: <http://localhost:8080/v3/api-docs.yaml>

Springdoc obtiene el contrato de los controllers y DTO. Los endpoints y los cuerpos están fijados
por las pruebas y no se han cambiado.

## Tests

```bash
mvn test                # tests unitarios con patrón *Test
mvn verify              # tests de integración *IT y comprobación de formato
mvn spotless:apply      # aplicar formato antes de verify
```

Testcontainers necesita un motor Docker funcional. Las pruebas crean bases de datos **aisladas**;
no utilizan las del `compose.yaml`, cuyo arranque automático está deshabilitado en el perfil de test.
Con la persistencia implementada, `mvn verify` ejecuta las 7 pruebas de integración y todas pasan.

## Contrato público

Campos de fecha: cadenas ISO-8601 (`YYYY-MM-DD`). Los nombres y estructuras están fijados
por los `record` en `controller/dto`. Consultar `docs/api-contract.md`.

## Tratamiento de errores

Los casos de uso lanzan `ResourceNotFoundException` (404), `BusinessConflictException` (409) o
`InvalidInputException` (400), y `ApiExceptionHandler` los convierte en la respuesta HTTP. Si una
restricción de la base de datos se incumple, el handler de `DataIntegrityViolationException`
devuelve un 409 sin exponer SQL interno.

## Persistencia en desarrollo

`spring.jpa.hibernate.ddl-auto=update` genera el esquema a partir de las anotaciones de las
entidades. En un entorno de producción convendría versionarlo con Flyway o Liquibase.

## Limitaciones conocidas

Están detalladas en [Decisiones técnicas](docs/decisiones-tecnicas.md). Las principales: no hay
transacción distribuida entre PostgreSQL y MongoDB, `ddl-auto: update` solo crea las restricciones
al crear la tabla y la caché de Redis puede mostrar un detalle con hasta 60 segundos de retraso.
