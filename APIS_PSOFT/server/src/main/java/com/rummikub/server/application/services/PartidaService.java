package com.rummikub.server.application.services;

import com.rummikub.server.api.dto.PartidaDTO;
import com.rummikub.server.infraestructure.jpa.entity.ParticipacionEntity;
import com.rummikub.server.infraestructure.jpa.entity.PartidaEntity;
import com.rummikub.server.infraestructure.jpa.mapper.Mapper;
import com.rummikub.server.infraestructure.jpa.repository.ParticipacionRepository;
import com.rummikub.server.infraestructure.jpa.repository.PartidaRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PartidaService {

    private static final int MAX_TURN_SLOTS = 4;
    private static final int INITIAL_HAND_SIZE = 14;
    private static final int TURN_TIMEOUT_SECONDS = 60;

    private final PartidaRepository partidaRepository;
    private final ParticipacionRepository participacionRepository;
    private final Map<Integer, TurnRuntime> turnRuntimeByPartida = new ConcurrentHashMap<>();
    private final Object turnMutex = new Object();

    public PartidaService(PartidaRepository partidaRepository, ParticipacionRepository participacionRepository) {
        this.partidaRepository = partidaRepository;
        this.participacionRepository = participacionRepository;
    }

    @PostConstruct
    public void warmUpTurnRuntime() {
        LocalDateTime now = LocalDateTime.now();
        List<PartidaEntity> runningGames = partidaRepository.findByCorriendoTrue();
        for (PartidaEntity partida : runningGames) {
            TurnRuntime runtime = createRuntimeFromDatabase(partida, now);
            if (runtime != null) {
                turnRuntimeByPartida.put(partida.getIdPartida(), runtime);
            }
        }
    }

    public List<PartidaDTO> getAll() {
        return partidaRepository.findAll().stream()
                .map(Mapper::toDTO)
                .toList();
    }

    @Transactional
    public PartidaDTO getById(Integer idPartida) {
        PartidaEntity partida = partidaRepository.findById(idPartida)
                .orElseThrow(() -> new NoSuchElementException("Partida no encontrada: " + idPartida));
        return Mapper.toDTO(partida);
    }

    @Transactional
    public PartidaDTO create(PartidaDTO dto) {
        if (dto == null || dto.getIdPartida() == null) {
            throw new IllegalArgumentException("El id de la partida es obligatorio");
        }
        if (partidaRepository.existsById(dto.getIdPartida())) {
            throw new IllegalStateException("Ya existe una partida con id: " + dto.getIdPartida());
        }

        PartidaEntity partida = new PartidaEntity();
        partida.setIdPartida(dto.getIdPartida());
        partida.setTurno(dto.getTurno());
        partida.setFecha(dto.getFecha() == null ? LocalDate.now() : dto.getFecha());
        partida.setBolsa(safe(dto.getBolsa()));
        partida.setMercado(safe(dto.getMercado()));
        partida.setConjuntoMesa(safe(dto.getConjuntoMesa()));
        partida.setCorriendo(dto.isCorriendo());
        partida.setTurnoInicio(null);

        if (dto.isCorriendo()) {
            initializeGameState(partida);
        } else {
            turnRuntimeByPartida.remove(partida.getIdPartida());
        }

        return Mapper.toDTO(partidaRepository.save(partida));
    }

    @Transactional
    public PartidaDTO update(Integer idPartida, PartidaDTO dto) {
        PartidaEntity partida = partidaRepository.findById(idPartida)
                .orElseThrow(() -> new NoSuchElementException("Partida no encontrada: " + idPartida));

        boolean wasRunning = partida.isCorriendo();
        boolean willRun = dto.isCorriendo();

        partida.setTurno(dto.getTurno());
        partida.setFecha(dto.getFecha() == null ? partida.getFecha() : dto.getFecha());
        partida.setBolsa(safe(dto.getBolsa()));
        partida.setMercado(safe(dto.getMercado()));
        partida.setConjuntoMesa(safe(dto.getConjuntoMesa()));
        partida.setCorriendo(willRun);

        if (!wasRunning && willRun) {
            initializeGameState(partida);
        } else if (wasRunning && !willRun) {
            partida.setTurnoInicio(null);
            turnRuntimeByPartida.remove(partida.getIdPartida());
        } else if (willRun) {
            synchronized (turnMutex) {
                TurnRuntime runtime = createRuntimeFromDatabase(partida, LocalDateTime.now());
                if (runtime != null) {
                    turnRuntimeByPartida.put(partida.getIdPartida(), runtime);
                }
            }
        }

        return Mapper.toDTO(partidaRepository.save(partida));
    }

    @Transactional
    public PartidaDTO siguienteTurno(Integer idPartida) {
        synchronized (turnMutex) {
            PartidaEntity partida = partidaRepository.findById(idPartida)
                    .orElseThrow(() -> new NoSuchElementException("Partida no encontrada: " + idPartida));

            if (!partida.isCorriendo()) {
                throw new IllegalStateException("La partida no esta en curso");
            }

            TurnRuntime runtime = turnRuntimeByPartida.get(idPartida);
            if (runtime == null) {
                runtime = createRuntimeFromDatabase(partida, LocalDateTime.now());
                if (runtime == null) {
                    throw new IllegalStateException("La partida no tiene jugadores activos");
                }
                turnRuntimeByPartida.put(idPartida, runtime);
            }

            int currentTurn = normalizeTurn(partida.getTurno());
            if (!runtime.occupiedSlots.contains(currentTurn)) {
                currentTurn = runtime.currentTurn;
            }

            int nextTurn = nextOccupiedTurn(currentTurn, runtime.occupiedSlots);
            LocalDateTime now = LocalDateTime.now();

            runtime.currentTurn = nextTurn;
            runtime.deadline = now.plusSeconds(TURN_TIMEOUT_SECONDS);

            partida.setTurno(nextTurn);
            partida.setTurnoInicio(now);
            return Mapper.toDTO(partidaRepository.save(partida));
        }
    }

    @Transactional
    public PartidaDTO iniciar(Integer idPartida) {
        synchronized (turnMutex) {
            PartidaEntity partida = partidaRepository.findById(idPartida)
                    .orElseThrow(() -> new NoSuchElementException("Partida no encontrada: " + idPartida));

            if (partida.isCorriendo()) {
                throw new IllegalStateException("La partida ya esta en curso");
            }

            partida.setCorriendo(true);
            initializeGameState(partida);
            return Mapper.toDTO(partidaRepository.save(partida));
        }
    }

    public void delete(Integer idPartida) {
        if (!partidaRepository.existsById(idPartida)) {
            throw new NoSuchElementException("Partida no encontrada: " + idPartida);
        }
        turnRuntimeByPartida.remove(idPartida);
        partidaRepository.deleteById(idPartida);
    }

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void processTurnTimeouts() {
        LocalDateTime now = LocalDateTime.now();
        List<Integer> runningIds = new ArrayList<>(turnRuntimeByPartida.keySet());
        for (Integer partidaId : runningIds) {
            TurnRuntime runtime = turnRuntimeByPartida.get(partidaId);
            if (runtime == null) {
                continue;
            }
            if (now.isBefore(runtime.deadline)) {
                continue;
            }
            advanceTurnOnTimeout(partidaId, now);
        }
    }

    private void initializeGameState(PartidaEntity partida) {
        List<ParticipacionEntity> participaciones = getOrderedParticipaciones(partida.getIdPartida());

        if (participaciones.isEmpty()) {
            throw new IllegalStateException("No se puede iniciar la partida sin jugadores en PARTICIPACION");
        }
        if (participaciones.size() > MAX_TURN_SLOTS) {
            throw new IllegalStateException("Solo se permiten 4 jugadores por partida");
        }

        List<String> bag = createAndShuffleFullBag();
        for (int i = 0; i < participaciones.size(); i++) {
            ParticipacionEntity participacion = participaciones.get(i);
            participacion.setOrdenTurno(i);

            List<String> hand = drawTiles(bag, INITIAL_HAND_SIZE);
            participacion.setManoActual(String.join(",", hand));
            participacion.setFichasActuales(hand.size());
        }

        participacionRepository.saveAll(participaciones);

        partida.setConjuntoMesa("");
        partida.setMercado("");
        partida.setBolsa(String.join(",", bag));
        partida.setTurno(0);
        LocalDateTime now = LocalDateTime.now();
        partida.setTurnoInicio(now);
        partida.setCorriendo(true);

        Set<Integer> occupiedSlots = buildOccupiedSlots(participaciones);
        if (!occupiedSlots.isEmpty()) {
            turnRuntimeByPartida.put(
                    partida.getIdPartida(),
                    new TurnRuntime(occupiedSlots, 0, now.plusSeconds(TURN_TIMEOUT_SECONDS))
            );
        }
    }

    private List<ParticipacionEntity> getOrderedParticipaciones(Integer idPartida) {
        return participacionRepository.findByPartida_IdPartidaOrderByJugador_Id(idPartida);
    }

    private Set<Integer> buildOccupiedSlots(List<ParticipacionEntity> participaciones) {
        Set<Integer> occupiedSlots = new HashSet<>();
        for (ParticipacionEntity participacion : participaciones) {
            Integer slot = participacion.getOrdenTurno();
            if (slot != null && slot >= 0 && slot < MAX_TURN_SLOTS) {
                occupiedSlots.add(slot);
            }
        }
        return occupiedSlots;
    }

    private int nextOccupiedTurn(int currentTurn, Set<Integer> occupiedSlots) {
        int normalized = normalizeTurn(currentTurn);
        for (int step = 1; step <= MAX_TURN_SLOTS; step++) {
            int candidate = (normalized + step) % MAX_TURN_SLOTS;
            if (occupiedSlots.contains(candidate)) {
                return candidate;
            }
        }
        return normalized;
    }

    private int normalizeTurn(int turn) {
        int normalized = turn % MAX_TURN_SLOTS;
        return normalized < 0 ? normalized + MAX_TURN_SLOTS : normalized;
    }

    private List<String> createAndShuffleFullBag() {
        List<String> bag = new ArrayList<>();
        String[] colors = {"R", "B", "O", "K"};

        for (String color : colors) {
            for (int value = 1; value <= 13; value++) {
                bag.add(color + value);
                bag.add(color + value);
            }
        }

        bag.add("J1");
        bag.add("J2");
        Collections.shuffle(bag);
        return bag;
    }

    private List<String> drawTiles(List<String> bag, int count) {
        int realCount = Math.min(count, bag.size());
        List<String> drawn = new ArrayList<>(bag.subList(0, realCount));
        bag.subList(0, realCount).clear();
        return drawn;
    }

    private void advanceTurnOnTimeout(Integer idPartida, LocalDateTime now) {
        synchronized (turnMutex) {
            TurnRuntime runtime = turnRuntimeByPartida.get(idPartida);
            if (runtime == null || now.isBefore(runtime.deadline)) {
                return;
            }

            PartidaEntity partida = partidaRepository.findById(idPartida).orElse(null);
            if (partida == null || !partida.isCorriendo()) {
                turnRuntimeByPartida.remove(idPartida);
                return;
            }

            if (runtime.occupiedSlots.isEmpty()) {
                turnRuntimeByPartida.remove(idPartida);
                return;
            }

            int baseTurn = normalizeTurn(partida.getTurno());
            if (!runtime.occupiedSlots.contains(baseTurn)) {
                baseTurn = runtime.currentTurn;
            }

            int nextTurn = nextOccupiedTurn(baseTurn, runtime.occupiedSlots);
            runtime.currentTurn = nextTurn;
            runtime.deadline = now.plusSeconds(TURN_TIMEOUT_SECONDS);

            partida.setTurno(nextTurn);
            partida.setTurnoInicio(now);
            partidaRepository.save(partida);
        }
    }

    private TurnRuntime createRuntimeFromDatabase(PartidaEntity partida, LocalDateTime now) {
        List<ParticipacionEntity> participaciones = getOrderedParticipaciones(partida.getIdPartida());
        Set<Integer> occupiedSlots = buildOccupiedSlots(participaciones);
        if (occupiedSlots.isEmpty()) {
            return null;
        }

        int currentTurn = normalizeTurn(partida.getTurno());
        if (!occupiedSlots.contains(currentTurn)) {
            currentTurn = occupiedSlots.stream().min(Integer::compareTo).orElse(0);
        }

        LocalDateTime deadline = (partida.getTurnoInicio() == null ? now : partida.getTurnoInicio())
                .plusSeconds(TURN_TIMEOUT_SECONDS);
        return new TurnRuntime(occupiedSlots, currentTurn, deadline);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    public void clearTurnRuntimeCache() {
        turnRuntimeByPartida.clear();
    }

    private static final class TurnRuntime {
        private final Set<Integer> occupiedSlots;
        private int currentTurn;
        private LocalDateTime deadline;

        private TurnRuntime(Set<Integer> occupiedSlots, int currentTurn, LocalDateTime deadline) {
            this.occupiedSlots = new HashSet<>(occupiedSlots);
            this.currentTurn = currentTurn;
            this.deadline = deadline;
        }
    }
}
