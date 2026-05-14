package com.rummikub.server.infraestructure.jpa.repository;

import com.rummikub.server.infraestructure.jpa.entity.ListaDeAmigosEntity;
import com.rummikub.server.infraestructure.jpa.entity.ListaDeAmigosId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
//con la biblioteca jpa se tienen las operaciones de bbdd basicas tipo-->
//.save(tipo)
//.findById(id)
//.finAll()
//.deleteAll()
//.deleteById(id)
//la biblioteca toma en cuenta los parametros de la clase entity para crear las operaciones
//de comunicacion con la BBDD

public interface ListaDeAmigosRepository extends JpaRepository<ListaDeAmigosEntity, ListaDeAmigosId> {

    List<ListaDeAmigosEntity> findByJugador1_IdOrJugador2_Id(Integer jugador1Id, Integer jugador2Id);

    @Query("SELECT l FROM ListaDeAmigosEntity l WHERE l.jugador1.id = :idJugador OR l.jugador2.id = :idJugador")
    List<ListaDeAmigosEntity> findAllByJugadorInRelation(@Param("idJugador") Integer idJugador);
}
