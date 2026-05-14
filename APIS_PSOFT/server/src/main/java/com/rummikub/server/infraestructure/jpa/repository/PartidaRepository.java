package com.rummikub.server.infraestructure.jpa.repository;

import com.rummikub.server.infraestructure.jpa.entity.PartidaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
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

    java.util.List<PartidaEntity> findByModoArcadeAndPrivadaAndEstadoAndCorriendoFalse(
            boolean modoArcade,
            boolean privada,
            String estado);
}
