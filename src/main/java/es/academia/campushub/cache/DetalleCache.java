package es.academia.campushub.cache;

import es.academia.campushub.controller.dto.ActividadDetalleResponse;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

@Component
public class DetalleCache {

  private static final Logger LOG = LoggerFactory.getLogger(DetalleCache.class);
  private static final Duration TIEMPO_DE_VIDA = Duration.ofSeconds(60);

  private final StringRedisTemplate redis;
  private final ObjectMapper objectMapper;
  private final boolean activa;

  public DetalleCache(
      StringRedisTemplate redis,
      ObjectMapper objectMapper,
      @Value("${campushub.cache.enabled:false}") boolean activa) {
    this.redis = redis;
    this.objectMapper = objectMapper;
    this.activa = activa;
  }

  public Optional<ActividadDetalleResponse> obtener(Long actividadId) {
    if (!activa) {
      return Optional.empty();
    }
    try {
      String json = redis.opsForValue().get(clave(actividadId));
      if (json == null) {
        return Optional.empty();
      }
      return Optional.of(objectMapper.readValue(json, ActividadDetalleResponse.class));
    } catch (RuntimeException e) {
      LOG.warn("No se pudo leer la caché de la actividad {}: {}", actividadId, e.getMessage());
      return Optional.empty();
    }
  }

  public void guardar(Long actividadId, ActividadDetalleResponse detalle) {
    if (!activa) {
      return;
    }
    try {
      redis
          .opsForValue()
          .set(clave(actividadId), objectMapper.writeValueAsString(detalle), TIEMPO_DE_VIDA);
    } catch (RuntimeException e) {
      LOG.warn("No se pudo guardar la caché de la actividad {}: {}", actividadId, e.getMessage());
    }
  }

  public void invalidar(Long actividadId) {
    if (!activa) {
      return;
    }
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              borrar(actividadId);
            }
          });
    } else {
      borrar(actividadId);
    }
  }

  private void borrar(Long actividadId) {
    try {
      redis.delete(clave(actividadId));
    } catch (RuntimeException e) {
      LOG.warn("No se pudo invalidar la caché de la actividad {}: {}", actividadId, e.getMessage());
    }
  }

  private String clave(Long actividadId) {
    return "actividad:" + actividadId + ":detalle";
  }
}
