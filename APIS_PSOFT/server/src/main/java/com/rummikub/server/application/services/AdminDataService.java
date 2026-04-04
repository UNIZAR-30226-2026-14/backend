package com.rummikub.server.application.services;

import com.rummikub.server.infraestructure.jpa.repository.JugadorRepository;
import com.rummikub.server.infraestructure.jpa.repository.ListaDeAmigosRepository;
import com.rummikub.server.infraestructure.jpa.repository.ParticipacionRepository;
import com.rummikub.server.infraestructure.jpa.repository.PartidaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminDataService {

    private final ListaDeAmigosRepository listaDeAmigosRepository;
    private final ParticipacionRepository participacionRepository;
    private final PartidaRepository partidaRepository;
    private final JugadorRepository jugadorRepository;
    private final PartidaService partidaService;

    public AdminDataService(
            ListaDeAmigosRepository listaDeAmigosRepository,
            ParticipacionRepository participacionRepository,
            PartidaRepository partidaRepository,
            JugadorRepository jugadorRepository,
            PartidaService partidaService
    ) {
        this.listaDeAmigosRepository = listaDeAmigosRepository;
        this.participacionRepository = participacionRepository;
        this.partidaRepository = partidaRepository;
        this.jugadorRepository = jugadorRepository;
        this.partidaService = partidaService;
    }

    @Transactional
    public void wipeAllData() {
        // Orden importante por claves foraneas: hijos -> padres.
        listaDeAmigosRepository.deleteAllInBatch();
        participacionRepository.deleteAllInBatch();
        partidaRepository.deleteAllInBatch();
        jugadorRepository.deleteAllInBatch();
        partidaService.clearTurnRuntimeCache();
    }
}
