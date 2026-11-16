package es.academia.campushub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import es.academia.campushub.support.ApiIntegrationSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class IntegridadApiIT extends ApiIntegrationSupport {

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

  private String jsonEstudiante(String email, String fechaNacimiento, long grupo) {
    return "{\"nombre\":\"Eva\",\"apellidos\":\"Test\",\"email\":\""
        + email
        + "\",\"fechaNacimiento\":\""
        + fechaNacimiento
        + "\",\"grupoId\":"
        + grupo
        + "}";
  }

  private long crearEstudiante(long grupo) throws Exception {
    return id(
        post(
            "/api/estudiantes",
            jsonEstudiante("est" + unico() + "@test.invalid", "2004-01-01", grupo)));
  }

  private String jsonActividad(String tipo, int maximo, long docente) {
    return "{\"titulo\":\"Actividad "
        + unico()
        + "\",\"descripcion\":\"Descripcion\",\"tipo\":\""
        + tipo
        + "\",\"fechaPrevista\":\"2027-05-01\",\"maxParticipantes\":"
        + maximo
        + ",\"docenteId\":"
        + docente
        + "}";
  }

  private long crearActividad(long docente, int maximo) throws Exception {
    return id(post("/api/actividades", jsonActividad("TALLER", maximo, docente)));
  }

  @Test
  void estudianteDuplicadoEs409() throws Exception {
    long grupo = crearGrupo(crearDocente());
    String json = jsonEstudiante("repetido" + unico() + "@test.invalid", "2004-01-01", grupo);
    status(201, post("/api/estudiantes", json));
    status(409, post("/api/estudiantes", json));
  }

  @Test
  void estudianteConGrupoInexistenteEs404() throws Exception {
    status(
        404,
        post(
            "/api/estudiantes",
            jsonEstudiante("a" + unico() + "@test.invalid", "2004-01-01", 99999999)));
  }

  @Test
  void fechaDeNacimientoFuturaEs400() throws Exception {
    long grupo = crearGrupo(crearDocente());
    status(
        400,
        post(
            "/api/estudiantes",
            jsonEstudiante("b" + unico() + "@test.invalid", "2999-01-01", grupo)));
  }

  @Test
  void actividadConCapacidadCeroEs400() throws Exception {
    long docente = crearDocente();
    status(400, post("/api/actividades", jsonActividad("TALLER", 0, docente)));
  }

  @Test
  void actividadConTipoDesconocidoEs400() throws Exception {
    long docente = crearDocente();
    status(400, post("/api/actividades", jsonActividad("FIESTA", 5, docente)));
  }

  @Test
  void actividadConDocenteInexistenteEs404() throws Exception {
    status(404, post("/api/actividades", jsonActividad("TALLER", 5, 99999999)));
  }

  @Test
  void detalleDeActividadSinDatosDevuelveListasVacias() throws Exception {
    long actividad = crearActividad(crearDocente(), 5);
    var detalle = get("/api/actividades/" + actividad + "/detalle");
    status(200, detalle);
    assertTrue(detalle.body().contains("\"estudiantes\":[]"), detalle.body());
    assertTrue(detalle.body().contains("\"etiquetas\":[]"), detalle.body());
    assertTrue(detalle.body().contains("\"seguimientos\":[]"), detalle.body());
    assertTrue(detalle.body().contains("\"plazasOcupadas\":0"), detalle.body());
    assertTrue(detalle.body().contains("\"plazasDisponibles\":5"), detalle.body());
  }

  @Test
  void seguimientoConClaveConPuntoEs400() throws Exception {
    long actividad = crearActividad(crearDocente(), 5);
    status(
        400,
        post(
            "/api/actividades/" + actividad + "/seguimientos",
            "{\"tipo\":\"TALLER\",\"datos\":{\"a.b\":1}}"));
  }

  @Test
  void seguimientoDeActividadInexistenteEs404() throws Exception {
    status(
        404,
        post(
            "/api/actividades/99999999/seguimientos", "{\"tipo\":\"TALLER\",\"datos\":{\"a\":1}}"));
  }

  @Test
  void seguimientoConservaLaEstructuraRecibida() throws Exception {
    long actividad = crearActividad(crearDocente(), 5);
    status(
        201,
        post(
            "/api/actividades/" + actividad + "/seguimientos",
            "{\"tipo\":\"TALLER\",\"datos\":{\"lenguaje\":\"Java\",\"ejercicios\":[1,2,3],\"extra\":{\"nota\":\"ok\"}}}"));
    var detalle = get("/api/actividades/" + actividad + "/detalle");
    assertTrue(detalle.body().contains("\"ejercicios\":[1,2,3]"), detalle.body());
    assertTrue(detalle.body().contains("\"extra\":{\"nota\":\"ok\"}"), detalle.body());
  }

  @Test
  void etiquetaDuplicadaSinDistinguirMayusculasEs409() throws Exception {
    String nombre = "Etiqueta" + unico();
    status(201, post("/api/etiquetas", "{\"nombre\":\"" + nombre + "\"}"));
    status(409, post("/api/etiquetas", "{\"nombre\":\"" + nombre.toUpperCase() + "\"}"));
  }

  @Test
  void asociarDosVecesLaMismaEtiquetaNoLaDuplica() throws Exception {
    long actividad = crearActividad(crearDocente(), 5);
    long etiqueta = id(post("/api/etiquetas", "{\"nombre\":\"E" + unico() + "\"}"));
    status(204, put("/api/actividades/" + actividad + "/etiquetas/" + etiqueta, null));
    status(204, put("/api/actividades/" + actividad + "/etiquetas/" + etiqueta, null));

    var lista = get("/api/etiquetas/" + etiqueta + "/actividades");
    status(200, lista);

    assertEquals(1, lista.body().split("\"titulo\"", -1).length - 1, lista.body());
  }

  @Test
  void asociarEtiquetaInexistenteEs404() throws Exception {
    long actividad = crearActividad(crearDocente(), 5);
    status(404, put("/api/actividades/" + actividad + "/etiquetas/99999999", null));
  }

  @Test
  void guardarDosVecesLaFichaDeContactoLaActualiza() throws Exception {
    long estudiante = crearEstudiante(crearGrupo(crearDocente()));
    String ruta = "/api/estudiantes/" + estudiante + "/contacto";
    status(
        200,
        put(ruta, "{\"tutorLegal\":\"Primero\",\"telefono\":\"600000001\",\"autorizado\":false}"));
    status(
        200,
        put(
            ruta,
            "{\"tutorLegal\":\"Segundo\",\"telefono\":\"600000002\",\"autorizado\":true,\"fechaAutorizacion\":\"2026-10-01\"}"));

    var contacto = get(ruta);
    status(200, contacto);
    assertTrue(contacto.body().contains("Segundo"), contacto.body());
    assertFalse(contacto.body().contains("Primero"), contacto.body());
  }

  @Test
  void contactoDeEstudianteInexistenteEs404() throws Exception {
    status(404, get("/api/estudiantes/99999999/contacto"));
  }

  @Test
  void diezInscripcionesSimultaneasEnUnaActividadDeUnaPlazaSoloAdmitenUna() throws Exception {
    long docente = crearDocente();
    long grupo = crearGrupo(docente);
    long actividad = crearActividad(docente, 1);

    List<Long> estudiantes = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      estudiantes.add(crearEstudiante(grupo));
    }

    ExecutorService hilos = Executors.newFixedThreadPool(10);
    List<Future<Integer>> resultados = new ArrayList<>();
    for (long estudiante : estudiantes) {
      resultados.add(
          hilos.submit(
              () ->
                  post(
                          "/api/actividades/" + actividad + "/inscripciones",
                          "{\"estudianteId\":" + estudiante + "}")
                      .statusCode()));
    }

    int creadas = 0;
    int conflictos = 0;
    for (Future<Integer> resultado : resultados) {
      int codigo = resultado.get();
      if (codigo == 201) {
        creadas++;
      } else if (codigo == 409) {
        conflictos++;
      }
    }
    hilos.shutdown();

    assertEquals(1, creadas, "Solo debe entrar una inscripción");
    assertEquals(9, conflictos, "Las otras nueve deben dar 409");

    var detalle = get("/api/actividades/" + actividad + "/detalle");
    assertTrue(detalle.body().contains("\"plazasOcupadas\":1"), detalle.body());
    assertTrue(detalle.body().contains("\"plazasDisponibles\":0"), detalle.body());
  }
}
