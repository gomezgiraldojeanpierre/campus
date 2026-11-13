package es.academia.campushub.repository;

import es.academia.campushub.entity.Estudiante;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EstudianteRepository extends JpaRepository<Estudiante, Long> {

  boolean existsByEmail(String email);

  @Query(
      "select i.estudiante from Inscripcion i "
          + "where i.actividad.id = :actividadId order by i.fechaInscripcion, i.id")
  List<Estudiante> buscarParticipantes(@Param("actividadId") Long actividadId);
}
