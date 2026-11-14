package es.academia.campushub.service;

import es.academia.campushub.cache.DetalleCache;
import es.academia.campushub.controller.dto.*;
import es.academia.campushub.document.Seguimiento;
import es.academia.campushub.entity.Actividad;
import es.academia.campushub.entity.ContactoEstudiante;
import es.academia.campushub.entity.Departamento;
import es.academia.campushub.entity.Docente;
import es.academia.campushub.entity.Estudiante;
import es.academia.campushub.entity.Etiqueta;
import es.academia.campushub.entity.Grupo;
import es.academia.campushub.entity.Inscripcion;
import es.academia.campushub.entity.TipoActividad;
import es.academia.campushub.mapper.CampusMapper;
import es.academia.campushub.repository.ActividadRepository;
import es.academia.campushub.repository.ContactoEstudianteRepository;
import es.academia.campushub.repository.DepartamentoRepository;
import es.academia.campushub.repository.DocenteRepository;
import es.academia.campushub.repository.EstudianteRepository;
import es.academia.campushub.repository.EtiquetaRepository;
import es.academia.campushub.repository.GrupoRepository;
import es.academia.campushub.repository.InscripcionRepository;
import es.academia.campushub.repository.SeguimientoRepository;
import es.academia.campushub.util.exception.BusinessConflictException;
import es.academia.campushub.util.exception.InvalidInputException;
import es.academia.campushub.util.exception.ResourceNotFoundException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CampusHubServiceImpl implements CampusHubService {

  private static final Pattern PATRON_EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

  private final DepartamentoRepository departamentoRepository;
  private final DocenteRepository docenteRepository;
  private final GrupoRepository grupoRepository;
  private final EstudianteRepository estudianteRepository;
  private final ActividadRepository actividadRepository;
  private final InscripcionRepository inscripcionRepository;
  private final ContactoEstudianteRepository contactoRepository;
  private final EtiquetaRepository etiquetaRepository;
  private final SeguimientoRepository seguimientoRepository;
  private final CampusMapper mapper;
  private final DetalleCache detalleCache;

  public CampusHubServiceImpl(
      DepartamentoRepository departamentoRepository,
      DocenteRepository docenteRepository,
      GrupoRepository grupoRepository,
      EstudianteRepository estudianteRepository,
      ActividadRepository actividadRepository,
      InscripcionRepository inscripcionRepository,
      ContactoEstudianteRepository contactoRepository,
      EtiquetaRepository etiquetaRepository,
      SeguimientoRepository seguimientoRepository,
      CampusMapper mapper,
      DetalleCache detalleCache) {
    this.departamentoRepository = departamentoRepository;
    this.docenteRepository = docenteRepository;
    this.grupoRepository = grupoRepository;
    this.estudianteRepository = estudianteRepository;
    this.actividadRepository = actividadRepository;
    this.inscripcionRepository = inscripcionRepository;
    this.contactoRepository = contactoRepository;
    this.etiquetaRepository = etiquetaRepository;
    this.seguimientoRepository = seguimientoRepository;
    this.mapper = mapper;
    this.detalleCache = detalleCache;
  }

  @Override
  public DepartamentoResponse crearDepartamento(CreateDepartamentoRequest request) {
    String nombre = texto(request.nombre(), "El nombre del departamento", 100);
    if (departamentoRepository.existsByNombreIgnoreCase(nombre)) {
      throw new BusinessConflictException("Ya existe un departamento con ese nombre");
    }
    Departamento departamento = new Departamento();
    departamento.setNombre(nombre);
    return mapper.aDepartamento(departamentoRepository.saveAndFlush(departamento));
  }

  @Override
  public DocenteResponse crearDocente(CreateDocenteRequest request) {
    String nombre = texto(request.nombre(), "El nombre del docente", 100);
    String apellidos = texto(request.apellidos(), "Los apellidos del docente", 150);
    String email = email(request.email(), "El correo del docente");
    Departamento departamento =
        departamentoRepository
            .findById(obligatorio(request.departamentoId(), "El departamento"))
            .orElseThrow(() -> new ResourceNotFoundException("El departamento no existe"));
    if (docenteRepository.existsByEmail(email)) {
      throw new BusinessConflictException("Ya existe un docente con ese correo");
    }
    Docente docente = new Docente();
    docente.setNombre(nombre);
    docente.setApellidos(apellidos);
    docente.setEmail(email);
    docente.setDepartamento(departamento);
    return mapper.aDocente(docenteRepository.saveAndFlush(docente));
  }

  @Override
  public GrupoResponse crearGrupo(CreateGrupoRequest request) {
    String nombre = texto(request.nombre(), "El nombre del grupo", 100);
    String nivel = texto(request.nivel(), "El nivel del grupo", 50);
    Docente tutor =
        docenteRepository
            .findById(obligatorio(request.tutorDocenteId(), "El docente tutor"))
            .orElseThrow(() -> new ResourceNotFoundException("El docente tutor no existe"));
    Grupo grupo = new Grupo();
    grupo.setNombre(nombre);
    grupo.setNivel(nivel);
    grupo.setTutor(tutor);
    return mapper.aGrupo(grupoRepository.saveAndFlush(grupo));
  }

  @Override
  public EstudianteResponse crearEstudiante(CreateEstudianteRequest request) {
    String nombre = texto(request.nombre(), "El nombre del estudiante", 100);
    String apellidos = texto(request.apellidos(), "Los apellidos del estudiante", 150);
    String email = email(request.email(), "El correo del estudiante");
    LocalDate nacimiento = fecha(request.fechaNacimiento(), "La fecha de nacimiento");
    if (!nacimiento.isBefore(LocalDate.now())) {
      throw new InvalidInputException("La fecha de nacimiento debe ser anterior a hoy");
    }
    Grupo grupo =
        grupoRepository
            .findById(obligatorio(request.grupoId(), "El grupo"))
            .orElseThrow(() -> new ResourceNotFoundException("El grupo no existe"));
    if (estudianteRepository.existsByEmail(email)) {
      throw new BusinessConflictException("Ya existe un estudiante con ese correo");
    }
    Estudiante estudiante = new Estudiante();
    estudiante.setNombre(nombre);
    estudiante.setApellidos(apellidos);
    estudiante.setEmail(email);
    estudiante.setFechaNacimiento(nacimiento);
    estudiante.setGrupo(grupo);
    return mapper.aEstudiante(estudianteRepository.saveAndFlush(estudiante));
  }

  @Override
  @Transactional(readOnly = true)
  public EstudianteResponse obtenerEstudiante(Long id) {
    return mapper.aEstudiante(buscarEstudiante(id));
  }

  @Override
  public ActividadResponse crearActividad(CreateActividadRequest request) {
    String titulo = texto(request.titulo(), "El título de la actividad", 150);
    String descripcion = texto(request.descripcion(), "La descripción de la actividad", 100_000);
    TipoActividad tipo = tipoActividad(request.tipo());
    LocalDate fechaPrevista = fecha(request.fechaPrevista(), "La fecha prevista");
    Integer maxParticipantes =
        obligatorio(request.maxParticipantes(), "El máximo de participantes");
    if (maxParticipantes <= 0) {
      throw new InvalidInputException("El máximo de participantes debe ser mayor que cero");
    }
    Docente docente =
        docenteRepository
            .findById(obligatorio(request.docenteId(), "El docente responsable"))
            .orElseThrow(() -> new ResourceNotFoundException("El docente responsable no existe"));

    Actividad actividad = new Actividad();
    actividad.setTitulo(titulo);
    actividad.setDescripcion(descripcion);
    actividad.setTipo(tipo);
    actividad.setFechaPrevista(fechaPrevista);
    actividad.setMaxParticipantes(maxParticipantes);
    actividad.setDocente(docente);
    return mapper.aActividad(actividadRepository.saveAndFlush(actividad));
  }

  @Override
  @Transactional(readOnly = true)
  public ActividadResponse obtenerActividad(Long id) {
    return mapper.aActividad(buscarActividad(id));
  }

  @Override
  public InscripcionResponse inscribir(Long actividadId, CreateInscripcionRequest request) {
    Long estudianteId = obligatorio(request.estudianteId(), "El estudiante");

    Actividad actividad =
        actividadRepository
            .buscarParaActualizar(actividadId)
            .orElseThrow(() -> new ResourceNotFoundException("La actividad no existe"));
    Estudiante estudiante = buscarEstudiante(estudianteId);

    if (inscripcionRepository.existsByActividadIdAndEstudianteId(actividadId, estudianteId)) {
      throw new BusinessConflictException("El estudiante ya está inscrito en la actividad");
    }
    if (inscripcionRepository.countByActividadId(actividadId) >= actividad.getMaxParticipantes()) {
      throw new BusinessConflictException("La actividad no tiene plazas disponibles");
    }

    Inscripcion inscripcion = new Inscripcion();
    inscripcion.setActividad(actividad);
    inscripcion.setEstudiante(estudiante);
    inscripcion.setFechaInscripcion(Instant.now());
    InscripcionResponse respuesta =
        mapper.aInscripcion(inscripcionRepository.saveAndFlush(inscripcion));
    detalleCache.invalidar(actividadId);
    return respuesta;
  }

  @Override
  @Transactional(readOnly = true)
  public List<EstudianteResponse> participantes(Long actividadId) {
    buscarActividad(actividadId);
    return estudianteRepository.buscarParticipantes(actividadId).stream()
        .map(mapper::aEstudiante)
        .toList();
  }

  @Override
  public SeguimientoResponse registrarSeguimiento(
      Long actividadId, CreateSeguimientoRequest request) {
    String tipo = texto(request.tipo(), "El tipo de seguimiento", 50);
    Map<String, Object> datos = obligatorio(request.datos(), "Los datos del seguimiento");
    comprobarClaves(datos);

    if (!actividadRepository.existsById(actividadId)) {
      throw new ResourceNotFoundException("La actividad no existe");
    }

    Seguimiento seguimiento = new Seguimiento();
    seguimiento.setActividadId(actividadId);
    seguimiento.setTipo(tipo);
    seguimiento.setDatos(datos);

    seguimiento.setCreadoEn(Instant.now().truncatedTo(ChronoUnit.MILLIS));
    SeguimientoResponse respuesta = mapper.aSeguimiento(seguimientoRepository.save(seguimiento));
    detalleCache.invalidar(actividadId);
    return respuesta;
  }

  @Override
  @Transactional(readOnly = true)
  public ActividadDetalleResponse detalle(Long id) {
    Optional<ActividadDetalleResponse> enCache = detalleCache.obtener(id);
    if (enCache.isPresent()) {
      return enCache.get();
    }

    Actividad actividad = buscarActividad(id);

    int ocupadas = (int) inscripcionRepository.countByActividadId(id);
    int disponibles = actividad.getMaxParticipantes() - ocupadas;

    List<EstudianteResponse> estudiantes =
        estudianteRepository.buscarParticipantes(id).stream().map(mapper::aEstudiante).toList();
    List<EtiquetaResponse> etiquetas =
        actividad.getEtiquetas().stream()
            .sorted(Comparator.comparing(Etiqueta::getNombre))
            .map(mapper::aEtiqueta)
            .toList();
    List<SeguimientoResponse> seguimientos =
        seguimientoRepository.findByActividadIdOrderByCreadoEnAsc(id).stream()
            .map(mapper::aSeguimiento)
            .toList();

    ActividadDetalleResponse detalle =
        new ActividadDetalleResponse(
            mapper.aActividad(actividad),
            mapper.aDocente(actividad.getDocente()),
            ocupadas,
            disponibles,
            estudiantes,
            etiquetas,
            seguimientos);
    detalleCache.guardar(id, detalle);
    return detalle;
  }

  @Override
  @Transactional(readOnly = true)
  public List<ActividadHistorialResponse> historial(Long estudianteId) {
    buscarEstudiante(estudianteId);
    return inscripcionRepository.buscarHistorial(estudianteId).stream()
        .map(mapper::aHistorial)
        .toList();
  }

  @Override
  public ContactoResponse guardarContacto(Long estudianteId, UpsertContactoRequest request) {
    String tutorLegal = texto(request.tutorLegal(), "El nombre del tutor legal", 150);
    String telefono = texto(request.telefono(), "El teléfono", 20);
    String email = request.email() == null ? null : email(request.email(), "El correo del tutor");
    Boolean autorizado = obligatorio(request.autorizado(), "La autorización");
    LocalDate fechaAutorizacion =
        request.fechaAutorizacion() == null
            ? null
            : fecha(request.fechaAutorizacion(), "La fecha de autorización");

    Estudiante estudiante = buscarEstudiante(estudianteId);

    ContactoEstudiante contacto =
        contactoRepository.findById(estudianteId).orElseGet(ContactoEstudiante::new);
    contacto.setEstudiante(estudiante);
    contacto.setTutorLegal(tutorLegal);
    contacto.setTelefono(telefono);
    contacto.setEmail(email);
    contacto.setAutorizado(autorizado);
    contacto.setFechaAutorizacion(fechaAutorizacion);
    return mapper.aContacto(contactoRepository.saveAndFlush(contacto));
  }

  @Override
  @Transactional(readOnly = true)
  public ContactoResponse contacto(Long estudianteId) {
    buscarEstudiante(estudianteId);
    return contactoRepository
        .findById(estudianteId)
        .map(mapper::aContacto)
        .orElseThrow(
            () -> new ResourceNotFoundException("El estudiante no tiene ficha de contacto"));
  }

  @Override
  public EtiquetaResponse crearEtiqueta(CreateEtiquetaRequest request) {
    String nombre = texto(request.nombre(), "El nombre de la etiqueta", 50);
    if (etiquetaRepository.existsByNombreIgnoreCase(nombre)) {
      throw new BusinessConflictException("Ya existe una etiqueta con ese nombre");
    }
    Etiqueta etiqueta = new Etiqueta();
    etiqueta.setNombre(nombre);
    return mapper.aEtiqueta(etiquetaRepository.saveAndFlush(etiqueta));
  }

  @Override
  public void asociarEtiqueta(Long actividadId, Long etiquetaId) {

    Actividad actividad =
        actividadRepository
            .buscarParaActualizar(actividadId)
            .orElseThrow(() -> new ResourceNotFoundException("La actividad no existe"));
    Etiqueta etiqueta =
        etiquetaRepository
            .findById(etiquetaId)
            .orElseThrow(() -> new ResourceNotFoundException("La etiqueta no existe"));

    boolean yaAsociada =
        actividad.getEtiquetas().stream().anyMatch(e -> e.getId().equals(etiqueta.getId()));
    if (!yaAsociada) {
      actividad.getEtiquetas().add(etiqueta);
      detalleCache.invalidar(actividadId);
    }
  }

  @Override
  @Transactional(readOnly = true)
  public List<ActividadResponse> actividadesPorEtiqueta(Long etiquetaId) {
    if (!etiquetaRepository.existsById(etiquetaId)) {
      throw new ResourceNotFoundException("La etiqueta no existe");
    }
    return actividadRepository.buscarPorEtiqueta(etiquetaId).stream()
        .map(mapper::aActividad)
        .toList();
  }

  private Estudiante buscarEstudiante(Long id) {
    return estudianteRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("El estudiante no existe"));
  }

  private Actividad buscarActividad(Long id) {
    return actividadRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("La actividad no existe"));
  }

  private static <T> T obligatorio(T valor, String campo) {
    if (valor == null) {
      throw new InvalidInputException(campo + " es obligatorio");
    }
    return valor;
  }

  private static void comprobarClaves(Object valor) {
    if (valor instanceof Map<?, ?> mapa) {
      for (Map.Entry<?, ?> entrada : mapa.entrySet()) {
        if (String.valueOf(entrada.getKey()).contains(".")) {
          throw new InvalidInputException("Las claves de 'datos' no pueden contener puntos");
        }
        comprobarClaves(entrada.getValue());
      }
    } else if (valor instanceof List<?> lista) {
      for (Object elemento : lista) {
        comprobarClaves(elemento);
      }
    }
  }

  private static String texto(String valor, String campo, int maximo) {
    if (valor == null || valor.isBlank()) {
      throw new InvalidInputException(campo + " es obligatorio");
    }
    String limpio = valor.strip();
    if (limpio.length() > maximo) {
      throw new InvalidInputException(campo + " no puede superar los " + maximo + " caracteres");
    }
    return limpio;
  }

  private static String email(String valor, String campo) {
    String limpio = texto(valor, campo, 254).toLowerCase(Locale.ROOT);
    if (!PATRON_EMAIL.matcher(limpio).matches()) {
      throw new InvalidInputException(campo + " no tiene un formato válido");
    }
    return limpio;
  }

  private static LocalDate fecha(String valor, String campo) {
    try {
      return LocalDate.parse(texto(valor, campo, 10));
    } catch (DateTimeParseException e) {
      throw new InvalidInputException(campo + " debe tener el formato AAAA-MM-DD");
    }
  }

  private static TipoActividad tipoActividad(String valor) {
    String texto = texto(valor, "El tipo de actividad", 20);
    try {
      return TipoActividad.valueOf(texto.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new InvalidInputException(
          "El tipo de actividad debe ser uno de: TALLER, CHARLA, VISITA, JORNADA");
    }
  }
}
