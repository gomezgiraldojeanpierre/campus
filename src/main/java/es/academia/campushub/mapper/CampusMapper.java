package es.academia.campushub.mapper;

import es.academia.campushub.controller.dto.ActividadHistorialResponse;
import es.academia.campushub.controller.dto.ActividadResponse;
import es.academia.campushub.controller.dto.ContactoResponse;
import es.academia.campushub.controller.dto.DepartamentoResponse;
import es.academia.campushub.controller.dto.DocenteResponse;
import es.academia.campushub.controller.dto.EstudianteResponse;
import es.academia.campushub.controller.dto.EtiquetaResponse;
import es.academia.campushub.controller.dto.GrupoResponse;
import es.academia.campushub.controller.dto.InscripcionResponse;
import es.academia.campushub.controller.dto.SeguimientoResponse;
import es.academia.campushub.document.Seguimiento;
import es.academia.campushub.entity.Actividad;
import es.academia.campushub.entity.ContactoEstudiante;
import es.academia.campushub.entity.Departamento;
import es.academia.campushub.entity.Docente;
import es.academia.campushub.entity.Estudiante;
import es.academia.campushub.entity.Etiqueta;
import es.academia.campushub.entity.Grupo;
import es.academia.campushub.entity.Inscripcion;
import org.springframework.stereotype.Component;

@Component
public class CampusMapper {

  public DepartamentoResponse aDepartamento(Departamento departamento) {
    return new DepartamentoResponse(departamento.getId(), departamento.getNombre());
  }

  public DocenteResponse aDocente(Docente docente) {
    return new DocenteResponse(
        docente.getId(),
        docente.getNombre(),
        docente.getApellidos(),
        docente.getEmail(),
        docente.getDepartamento().getId());
  }

  public GrupoResponse aGrupo(Grupo grupo) {
    return new GrupoResponse(
        grupo.getId(), grupo.getNombre(), grupo.getNivel(), grupo.getTutor().getId());
  }

  public EstudianteResponse aEstudiante(Estudiante estudiante) {
    return new EstudianteResponse(
        estudiante.getId(),
        estudiante.getNombre(),
        estudiante.getApellidos(),
        estudiante.getEmail(),
        estudiante.getFechaNacimiento().toString(),
        estudiante.getGrupo().getId());
  }

  public ActividadResponse aActividad(Actividad actividad) {
    return new ActividadResponse(
        actividad.getId(),
        actividad.getTitulo(),
        actividad.getDescripcion(),
        actividad.getTipo().name(),
        actividad.getFechaPrevista().toString(),
        actividad.getMaxParticipantes(),
        actividad.getDocente().getId());
  }

  public InscripcionResponse aInscripcion(Inscripcion inscripcion) {
    return new InscripcionResponse(
        inscripcion.getId(),
        inscripcion.getActividad().getId(),
        inscripcion.getEstudiante().getId(),
        inscripcion.getFechaInscripcion().toString());
  }

  public ActividadHistorialResponse aHistorial(Inscripcion inscripcion) {
    Actividad actividad = inscripcion.getActividad();
    return new ActividadHistorialResponse(
        actividad.getId(),
        actividad.getTitulo(),
        actividad.getTipo().name(),
        actividad.getFechaPrevista().toString(),
        inscripcion.getFechaInscripcion().toString(),
        aDocente(actividad.getDocente()));
  }

  public ContactoResponse aContacto(ContactoEstudiante contacto) {
    return new ContactoResponse(
        contacto.getEstudianteId(),
        contacto.getTutorLegal(),
        contacto.getTelefono(),
        contacto.getEmail(),
        contacto.getAutorizado(),
        contacto.getFechaAutorizacion() == null
            ? null
            : contacto.getFechaAutorizacion().toString());
  }

  public EtiquetaResponse aEtiqueta(Etiqueta etiqueta) {
    return new EtiquetaResponse(etiqueta.getId(), etiqueta.getNombre());
  }

  public SeguimientoResponse aSeguimiento(Seguimiento seguimiento) {
    return new SeguimientoResponse(
        seguimiento.getId(),
        seguimiento.getActividadId(),
        seguimiento.getTipo(),
        seguimiento.getDatos(),
        seguimiento.getCreadoEn().toString());
  }
}
