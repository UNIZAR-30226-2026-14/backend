package com.rummikub.server.infraestructure.jpa.repository;

import com.rummikub.server.infraestructure.jpa.entity.ParticipacionEntity;
import com.rummikub.server.infraestructure.jpa.entity.ParticipacionId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ParticipacionRepository extends JpaRepository<ParticipacionEntity, ParticipacionId> {
    List<ParticipacionEntity> findByJugador_Id(Integer jugadorId);

    List<ParticipacionEntity> findByPartida_IdPartida(Integer partidaId);

    List<ParticipacionEntity> findByPartida_IdPartidaIn(Collection<Integer> partidaIds);

    List<ParticipacionEntity> findByPartida_IdPartidaOrderByJugador_Id(Integer partidaId);

    @Query("""
            select
                p.partida.idPartida as idPartida,
                p.jugador.id as idJugador,
                p.fichasActuales as fichasActuales
            from ParticipacionEntity p
            where p.partida.idPartida = :partidaId
            """)
    List<ParticipacionFichasView> findFichasByPartidaId(@Param("partidaId") Integer partidaId);

    @Query("""
            select
                p.partida.idPartida as idPartida,
                p.jugador.id as idJugador,
                p.fichasActuales as fichasActuales
            from ParticipacionEntity p
            where p.partida.idPartida in :partidaIds
            """)
    List<ParticipacionFichasView> findFichasByPartidaIds(@Param("partidaIds") Collection<Integer> partidaIds);

    interface ParticipacionFichasView {
        Integer getIdPartida();

        Integer getIdJugador();

        int getFichasActuales();
    }
}
