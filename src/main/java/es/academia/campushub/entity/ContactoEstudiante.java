package es.academia.campushub.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "contacto_estudiante")
@Getter
@Setter
@NoArgsConstructor
public class ContactoEstudiante {

  @Id
  @Column(name = "estudiante_id")
  private Long estudianteId;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @MapsId
  @JoinColumn(name = "estudiante_id")
  @OnDelete(action = OnDeleteAction.CASCADE)
  private Estudiante estudiante;

  @Column(name = "tutor_legal", nullable = false, length = 150)
  private String tutorLegal;

  @Column(nullable = false, length = 20)
  private String telefono;

  @Column(length = 254)
  private String email;

  @Column(nullable = false)
  private Boolean autorizado;

  @Column(name = "fecha_autorizacion")
  private LocalDate fechaAutorizacion;
}
