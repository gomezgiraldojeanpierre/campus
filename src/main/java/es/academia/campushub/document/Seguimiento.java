package es.academia.campushub.document;

import java.time.Instant;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = Seguimiento.COLECCION)
@Getter
@Setter
@NoArgsConstructor
public class Seguimiento {

  public static final String COLECCION = "seguimientos";

  @Id private String id;

  private Long actividadId;

  private String tipo;

  private Map<String, Object> datos;

  private Instant creadoEn;
}
