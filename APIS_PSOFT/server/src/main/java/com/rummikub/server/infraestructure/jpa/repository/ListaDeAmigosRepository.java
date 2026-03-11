package com.rummikub.server.infraestructure.jpa.repository;

import com.rummikub.server.infraestructure.jpa.entity.ListaDeAmigosEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
//con la biblioteca jpa se tienen las operaciones de bbdd basicas tipo-->
//.save(tipo)
//.findById(id)
//.finAll()
//.deleteAll()
//.deleteById(id)
//la biblioteca toma en cuenta los parametros de la clase entity para crear las operaciones
//de comunicacion con la BBDD

public interface ListaDeAmigosRepository extends JpaRepository<ListaDeAmigosEntity,String>{

    List<ListaDeAmigosEntity> findByJugador1_IdOrJugador2_Id(String jugador1Id, String jugador2Id);
}
