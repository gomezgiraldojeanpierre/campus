package es.academia.campushub.config;

import es.academia.campushub.document.Seguimiento;
import java.util.List;
import org.bson.Document;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.CollectionOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.validation.Validator;
import org.springframework.stereotype.Component;

@Component
public class MongoInicializador implements ApplicationRunner {

  private final MongoTemplate mongoTemplate;

  public MongoInicializador(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }

  @Override
  public void run(ApplicationArguments args) {
    Document validador = new Document("$jsonSchema", esquema());

    if (mongoTemplate.collectionExists(Seguimiento.COLECCION)) {

      mongoTemplate.executeCommand(
          new Document("collMod", Seguimiento.COLECCION).append("validator", validador));
    } else {
      mongoTemplate.createCollection(
          Seguimiento.COLECCION,
          CollectionOptions.empty().validator(Validator.document(validador)));
    }

    mongoTemplate
        .indexOps(Seguimiento.COLECCION)
        .createIndex(
            new Index()
                .on("actividadId", Sort.Direction.ASC)
                .on("creadoEn", Sort.Direction.ASC)
                .named("idx_actividad_creado_en"));
  }

  private Document esquema() {
    Document propiedades =
        new Document()
            .append("actividadId", new Document("bsonType", List.of("int", "long")))
            .append(
                "tipo",
                new Document("bsonType", "string").append("minLength", 1).append("maxLength", 50))
            .append("datos", new Document("bsonType", "object"))
            .append("creadoEn", new Document("bsonType", "date"));

    return new Document("bsonType", "object")
        .append("required", List.of("actividadId", "tipo", "datos", "creadoEn"))
        .append("properties", propiedades);
  }
}
