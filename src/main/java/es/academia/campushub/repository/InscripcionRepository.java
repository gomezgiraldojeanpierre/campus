package es.academia.campushub.repository;

import es.academia.campushub.entity.Inscripcion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InscripcionRepository extends JpaRepository<Inscripcion, Long> {

  boolean existsByActividadIdAndEstudianteId(Long actividadId, Long estudianteId);

  long countByActividadId(Long actividadId);

  @Query(
      "select i from Inscripcion i join fetch i.actividad a join fetch a.docente "
          + "where i.estudiante.id = :estudianteId order by a.fechaPrevista, a.id")
  List<Inscripcion> buscarHistorial(@Param("estudianteId") Long estudianteId);
}
