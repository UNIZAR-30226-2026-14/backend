package com.rummikub.server.infraestructure.jpa.repository;

import com.rummikub.server.infraestructure.jpa.entity.PartidaEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
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

    java.util.List<MatchmakingPartidaView> findTop20ByModoArcadeAndPrivadaFalseAndEstadoAndCorriendoFalseOrderByIdPartidaDesc(
            boolean modoArcade,
            String estado);

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
