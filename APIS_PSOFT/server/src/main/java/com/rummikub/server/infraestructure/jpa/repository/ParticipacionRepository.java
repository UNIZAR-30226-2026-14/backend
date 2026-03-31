package com.rummikub.server.infraestructure.jpa.repository;

import com.rummikub.server.infraestructure.jpa.entity.ParticipacionEntity;
import com.rummikub.server.infraestructure.jpa.entity.ParticipacionId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ParticipacionRepository extends JpaRepository<ParticipacionEntity, ParticipacionId> {
    List<ParticipacionEntity> findByJugador_Id(Integer jugadorId);

    List<ParticipacionEntity> findByPartida_IdPartida(Integer partidaId);
}
