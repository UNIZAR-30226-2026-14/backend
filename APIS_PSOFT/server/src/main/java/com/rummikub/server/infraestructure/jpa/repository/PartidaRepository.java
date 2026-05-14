package com.rummikub.server.infraestructure.jpa.repository;

import com.rummikub.server.infraestructure.jpa.entity.PartidaEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
//con la biblioteca jpa se tienen las operaciones de bbdd basicas tipo-->
//.save(tipo)
//.findById(id)
//.finAll()
//.deleteAll()
//.deleteById(id)
//la biblioteca toma en cuenta los parametros de la clase entity para crear las operaciones
//de comunicacion con la BBDD

public interface PartidaRepository extends JpaRepository<PartidaEntity, Integer>{
    java.util.List<PartidaEntity> findByCorriendoTrue();

    @Query("""
            select p
            from PartidaEntity p
            where p.modoArcade = :modoArcade
              and p.privada = false
              and p.estado = :estado
              and p.corriendo = false
            order by p.idPartida desc
            """)
    java.util.List<PartidaEntity> findMatchmakingCandidates(
            @Param("modoArcade") boolean modoArcade,
            @Param("estado") String estado,
            Pageable pageable);

    @Query("""
            select
                p.idPartida as idPartida,
                p.turno as turno,
                p.fecha as fecha,
                p.eventoActual as eventoActual,
                p.modoArcade as modoArcade,
                p.turnoInicio as turnoInicio,
                p.estado as estado,
                p.ganadorId as ganadorId,
                p.privada as privada,
                p.corriendo as corriendo
            from PartidaEntity p
            order by p.idPartida desc
            """)
    java.util.List<PartidaSummaryView> findAllSummaries();

    @Query("""
            select
                p.idPartida as idPartida,
                p.turno as turno,
                p.fecha as fecha,
                p.eventoActual as eventoActual,
                p.modoArcade as modoArcade,
                p.turnoInicio as turnoInicio,
                p.estado as estado,
                p.ganadorId as ganadorId,
                p.privada as privada,
                p.corriendo as corriendo
            from PartidaEntity p
            where exists (
                select 1
                from ParticipacionEntity participacion
                where participacion.partida = p
                    and participacion.jugador.id = :usuarioId
            )
            order by p.idPartida desc
            """)
    java.util.List<PartidaSummaryView> findSummariesByUsuarioId(@Param("usuarioId") Integer usuarioId);

    java.util.List<MatchmakingPartidaView> findTop20ByModoArcadeAndPrivadaFalseAndEstadoAndCorriendoFalseOrderByIdPartidaDesc(
            boolean modoArcade,
            String estado);

    interface PartidaSummaryView {
        Integer getIdPartida();

        int getTurno();

        LocalDate getFecha();

        String getEventoActual();

        boolean getModoArcade();

        LocalDateTime getTurnoInicio();

        String getEstado();

        Integer getGanadorId();

        boolean getPrivada();

        boolean getCorriendo();
    }

    @Query("""
            select p
            from PartidaEntity p
            where p.privada = false
              and p.corriendo = false
              and p.estado = :estado
              and p.turnoInicio is not null
              and p.turnoInicio <= :cutoff
            """)
    java.util.List<PartidaEntity> findExpiredPublicLobbies(
            @Param("estado") String estado,
            @Param("cutoff") LocalDateTime cutoff);

    interface MatchmakingPartidaView {
        Integer getIdPartida();

        int getTurno();

        LocalDate getFecha();

        boolean getModoArcade();

        String getEstado();

        boolean getPrivada();

        boolean getCorriendo();
    }
}
