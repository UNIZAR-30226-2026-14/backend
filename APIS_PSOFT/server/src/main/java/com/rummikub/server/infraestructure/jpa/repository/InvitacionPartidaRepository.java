package com.rummikub.server.infraestructure.jpa.repository;

import com.rummikub.server.infraestructure.jpa.entity.InvitacionPartidaEntity;
import com.rummikub.server.infraestructure.jpa.entity.InvitacionPartidaId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InvitacionPartidaRepository extends JpaRepository<InvitacionPartidaEntity, InvitacionPartidaId> {
    List<InvitacionPartidaEntity> findByInvitado_Id(Integer idInvitado);
}
