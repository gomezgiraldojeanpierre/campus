package es.academia.campushub;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import es.academia.campushub.document.Seguimiento;
import es.academia.campushub.repository.SeguimientoRepository;
import es.academia.campushub.support.ApiIntegrationSupport;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CacheDetalleIT extends ApiIntegrationSupport {

  @Container
  static final GenericContainer<?> REDIS =
      new GenericContainer<>("redis:8-alpine").withExposedPorts(6379);

  @DynamicPropertySource
  static void propiedadesRedis(DynamicPropertyRegistry r) {
    r.add("campushub.cache.enabled", () -> "true");
    r.add("spring.data.redis.host", REDIS::getHost);
    r.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    r.add("spring.data.redis.timeout", () -> "500ms");
    r.add("spring.data.redis.connect-timeout", () -> "500ms");
  }

  @Autowired StringRedisTemplate redis;
  @Autowired SeguimientoRepository seguimientoRepository;

  private String unico() {
    return String.valueOf(System.nanoTime());
  }

  private long crearDocente() throws Exception {
    String u = unico();
    long departamento = id(post("/api/departamentos", "{\"nombre\":\"Dep" + u + "\"}"));
    return id(
        post(
            "/api/docentes",
            "{\"nombre\":\"Ana\",\"apellidos\":\"Test\",\"email\":\"doc"
                + u
                + "@test.invalid\",\"departamentoId\":"
                + departamento
                + "}"));
  }

  private long crearGrupo(long docente) throws Exception {
    return id(
        post(
            "/api/grupos",
            "{\"nombre\":\"G"
                + unico()
                + "\",\"nivel\":\"2 DAM\",\"tutorDocenteId\":"
                + docente
                + "}"));
  }

  private long crearEstudiante(long grupo) throws Exception {
    return id(
        post(
            "/api/estudiantes",
            "{\"nombre\":\"Eva\",\"apellidos\":\"Test\",\"email\":\"est"
                + unico()
                + "@test.invalid\",\"fechaNacimiento\":\"2004-01-01\",\"grupoId\":"
                + grupo
                + "}"));
  }

  private long crearActividad(long docente) throws Exception {
    return id(
        post(
            "/api/actividades",
            "{\"titulo\":\"Actividad "
                + unico()
                + "\",\"descripcion\":\"Descripcion\",\"tipo\":\"TALLER\",\"fechaPrevista\":\"2027-05-01\",\"maxParticipantes\":5,\"docenteId\":"
                + docente
                + "}"));
  }

  private String clave(long actividad) {
    return "actividad:" + actividad + ":detalle";
  }

  @Test
  @Order(1)
  void laPrimeraConsultaGuardaElDetalleEnRedis() throws Exception {
    long actividad = crearActividad(crearDocente());
    assertFalse(redis.hasKey(clave(actividad)));
    status(200, get("/api/actividades/" + actividad + "/detalle"));
    assertTrue(redis.hasKey(clave(actividad)));
  }

  @Test
  @Order(2)
  void laSegundaConsultaSaleDeRedisYNoDeLasBasesDeDatos() throws Exception {
    long actividad = crearActividad(crearDocente());
    status(200, get("/api/actividades/" + actividad + "/detalle"));

    Seguimiento escondido = new Seguimiento();
    escondido.setActividadId(actividad);
    escondido.setTipo("ESCONDIDO");
    escondido.setDatos(Map.of("a", 1));
    escondido.setCreadoEn(Instant.now());
    seguimientoRepository.save(escondido);

    var segunda = get("/api/actividades/" + actividad + "/detalle");
    assertFalse(segunda.body().contains("ESCONDIDO"), segunda.body());
  }

  @Test
  @Order(3)
  void unaInscripcionInvalidaElDetalle() throws Exception {
    long docente = crearDocente();
    long actividad = crearActividad(docente);
    long estudiante = crearEstudiante(crearGrupo(docente));

    var antes = get("/api/actividades/" + actividad + "/detalle");
    assertTrue(antes.body().contains("\"plazasOcupadas\":0"), antes.body());

    status(
        201,
        post(
            "/api/actividades/" + actividad + "/inscripciones",
            "{\"estudianteId\":" + estudiante + "}"));

    var despues = get("/api/actividades/" + actividad + "/detalle");
    assertTrue(despues.body().contains("\"plazasOcupadas\":1"), despues.body());
  }

  @Test
  @Order(4)
  void unaEtiquetaNuevaInvalidaElDetalle() throws Exception {
    long actividad = crearActividad(crearDocente());
    long etiqueta = id(post("/api/etiquetas", "{\"nombre\":\"E" + unico() + "\"}"));

    var antes = get("/api/actividades/" + actividad + "/detalle");
    assertTrue(antes.body().contains("\"etiquetas\":[]"), antes.body());

    status(204, put("/api/actividades/" + actividad + "/etiquetas/" + etiqueta, null));

    var despues = get("/api/actividades/" + actividad + "/detalle");
    assertFalse(despues.body().contains("\"etiquetas\":[]"), despues.body());
  }

  @Test
  @Order(5)
  void unSeguimientoNuevoInvalidaElDetalle() throws Exception {
    long actividad = crearActividad(crearDocente());

    var antes = get("/api/actividades/" + actividad + "/detalle");
    assertTrue(antes.body().contains("\"seguimientos\":[]"), antes.body());

    status(
        201,
        post(
            "/api/actividades/" + actividad + "/seguimientos",
            "{\"tipo\":\"TALLER\",\"datos\":{\"lenguaje\":\"Java\"}}"));

    var despues = get("/api/actividades/" + actividad + "/detalle");
    assertTrue(despues.body().contains("\"lenguaje\":\"Java\""), despues.body());
  }

  @Test
  @Order(99)
  void siRedisSeDetieneLaApiSigueFuncionando() throws Exception {
    long docente = crearDocente();
    long actividad = crearActividad(docente);
    long estudiante = crearEstudiante(crearGrupo(docente));

    REDIS.stop();

    var detalle = get("/api/actividades/" + actividad + "/detalle");
    status(200, detalle);
    status(
        201,
        post(
            "/api/actividades/" + actividad + "/inscripciones",
            "{\"estudianteId\":" + estudiante + "}"));
    var despues = get("/api/actividades/" + actividad + "/detalle");
    assertTrue(despues.body().contains("\"plazasOcupadas\":1"), despues.body());
  }
}
