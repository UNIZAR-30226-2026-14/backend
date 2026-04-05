package com.rummikub.server.application.services;

import com.rummikub.server.api.dto.PartidaDTO;
import com.rummikub.server.infraestructure.jpa.entity.JugadorEntity;
import com.rummikub.server.infraestructure.jpa.entity.ParticipacionEntity;
import com.rummikub.server.infraestructure.jpa.entity.ParticipacionId;
import com.rummikub.server.infraestructure.jpa.entity.PartidaEntity;
import com.rummikub.server.infraestructure.jpa.mapper.Mapper;
import com.rummikub.server.infraestructure.jpa.repository.JugadorRepository;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PartidaService {

    private static final int MAX_TURN_SLOTS = 4;
    private static final int INITIAL_HAND_SIZE = 14;
    private static final int TURN_TIMEOUT_SECONDS = 60;

    private static final String ESTADO_WAITING = "WAITING";
    private static final String ESTADO_RUNNING = "RUNNING";
    private static final String ESTADO_FINISHED = "FINISHED";

    private static final Pattern TILE_PATTERN = Pattern.compile("^([RBOK])(1[0-3]|[1-9])$");

    private final PartidaRepository partidaRepository;
    private final ParticipacionRepository participacionRepository;
    private final JugadorRepository jugadorRepository;
    private final Map<Integer, TurnRuntime> turnRuntimeByPartida = new ConcurrentHashMap<>();
    private final Object turnMutex = new Object();

    public PartidaService(
            PartidaRepository partidaRepository,
            ParticipacionRepository participacionRepository,
            JugadorRepository jugadorRepository) {
        this.partidaRepository = partidaRepository;
        this.participacionRepository = participacionRepository;
        this.jugadorRepository = jugadorRepository;
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
        ensureDefaultState(partida);
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
        partida.setTurnoInicio(null);
        partida.setGanadorId(null);
        partida.setPuntuacionFinal("");

        if (dto.isCorriendo()) {
            partida.setCorriendo(true);
            partida.setEstado(ESTADO_RUNNING);
            initializeGameState(partida);
        } else {
            partida.setCorriendo(false);
            partida.setEstado(ESTADO_WAITING);
            turnRuntimeByPartida.remove(partida.getIdPartida());
        }

        return Mapper.toDTO(partidaRepository.save(partida));
    }

    @Transactional
    public PartidaDTO update(Integer idPartida, PartidaDTO dto) {
        PartidaEntity partida = partidaRepository.findById(idPartida)
                .orElseThrow(() -> new NoSuchElementException("Partida no encontrada: " + idPartida));

        ensureDefaultState(partida);

        boolean wasRunning = partida.isCorriendo();
        boolean willRun = dto.isCorriendo();

        partida.setTurno(dto.getTurno());
        partida.setFecha(dto.getFecha() == null ? partida.getFecha() : dto.getFecha());
        partida.setBolsa(safe(dto.getBolsa()));
        partida.setMercado(safe(dto.getMercado()));
        partida.setConjuntoMesa(safe(dto.getConjuntoMesa()));
        partida.setCorriendo(willRun);

        if (ESTADO_FINISHED.equals(partida.getEstado()) && willRun) {
            throw new IllegalStateException("La partida ya finalizo y no se puede reanudar");
        }

        if (!wasRunning && willRun) {
            partida.setEstado(ESTADO_RUNNING);
            initializeGameState(partida);
        } else if (wasRunning && !willRun) {
            partida.setTurnoInicio(null);
            if (!ESTADO_FINISHED.equals(partida.getEstado())) {
                partida.setEstado(ESTADO_WAITING);
            }
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
    public PartidaDTO iniciar(Integer idPartida) {
        synchronized (turnMutex) {
            PartidaEntity partida = partidaRepository.findById(idPartida)
                    .orElseThrow(() -> new NoSuchElementException("Partida no encontrada: " + idPartida));

            ensureDefaultState(partida);

            if (ESTADO_FINISHED.equals(partida.getEstado())) {
                throw new IllegalStateException("La partida ya finalizo");
            }
            if (partida.isCorriendo()) {
                throw new IllegalStateException("La partida ya esta en curso");
            }

            partida.setCorriendo(true);
            partida.setEstado(ESTADO_RUNNING);
            initializeGameState(partida);
            return Mapper.toDTO(partidaRepository.save(partida));
        }
    }

    @Transactional
    public PartidaDTO siguienteTurno(Integer idPartida, Integer idJugador) {
        return pasarTurno(idPartida, idJugador);
    }

    @Transactional
    public PartidaDTO pasarTurno(Integer idPartida, Integer idJugador) {
        synchronized (turnMutex) {
            PartidaEntity partida = mustGetRunningPartida(idPartida);
            ParticipacionEntity participacion = mustGetParticipacion(idPartida, idJugador);
            validatePlayerTurn(partida, participacion);
            return advanceTurn(partida, LocalDateTime.now());
        }
    }

    @Transactional
    public PartidaDTO robarFicha(Integer idPartida, Integer idJugador) {
        synchronized (turnMutex) {
            PartidaEntity partida = mustGetRunningPartida(idPartida);
            ParticipacionEntity participacion = mustGetParticipacion(idPartida, idJugador);
            validatePlayerTurn(partida, participacion);

            List<String> bag = parseTileList(partida.getBolsa());
            if (bag.isEmpty()) {
                throw new IllegalStateException("No quedan fichas en la bolsa");
            }

            String drawnTile = bag.remove(0);
            List<String> hand = parseTileList(participacion.getManoActual());
            hand.add(drawnTile);

            participacion.setManoActual(serializeTileList(hand));
            participacion.setFichasActuales(hand.size());
            participacionRepository.save(participacion);

            partida.setBolsa(serializeTileList(bag));
            return advanceTurn(partida, LocalDateTime.now());
        }
    }

    @Transactional
    public PartidaDTO jugarGrupos(Integer idPartida, Integer idJugador, List<List<String>> grupos) {
        synchronized (turnMutex) {
            if (grupos == null || grupos.isEmpty()) {
                throw new IllegalArgumentException("Debes enviar al menos un grupo de fichas");
            }

            PartidaEntity partida = mustGetRunningPartida(idPartida);
            ParticipacionEntity participacion = mustGetParticipacion(idPartida, idJugador);
            validatePlayerTurn(partida, participacion);

            List<String> hand = parseTileList(participacion.getManoActual());
            Map<String, Integer> handCount = buildCountMap(hand);
            List<List<String>> normalizedGroups = normalizeGroups(grupos);

            List<String> playedTiles = new ArrayList<>();
            for (List<String> group : normalizedGroups) {
                if (!isValidRummikubGroup(group)) {
                    throw new IllegalArgumentException("Grupo invalido segun reglas de Rummikub: " + group);
                }
                for (String tile : group) {
                    Integer available = handCount.getOrDefault(tile, 0);
                    if (available <= 0) {
                        throw new IllegalStateException("La ficha " + tile + " no esta disponible en tu mano");
                    }
                    handCount.put(tile, available - 1);
                    playedTiles.add(tile);
                }
            }

            List<String> updatedHand = removeTilesFromHand(hand, playedTiles);
            participacion.setManoActual(serializeTileList(updatedHand));
            participacion.setFichasActuales(updatedHand.size());
            participacionRepository.save(participacion);

            List<List<String>> mesa = parseMesaGroups(partida.getConjuntoMesa());
            mesa.addAll(normalizedGroups);
            partida.setConjuntoMesa(serializeMesaGroups(mesa));

            if (updatedHand.isEmpty()) {
                finishGame(partida, idJugador);
                return Mapper.toDTO(partidaRepository.save(partida));
            }

            return advanceTurn(partida, LocalDateTime.now());
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
            if (runtime == null || now.isBefore(runtime.deadline)) {
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
            participacion.setManoActual(serializeTileList(hand));
            participacion.setFichasActuales(hand.size());
        }
        participacionRepository.saveAll(participaciones);

        partida.setConjuntoMesa("");
        partida.setMercado("");
        partida.setBolsa(serializeTileList(bag));
        partida.setGanadorId(null);
        partida.setPuntuacionFinal("");
        partida.setEstado(ESTADO_RUNNING);
        partida.setCorriendo(true);
        partida.setTurno(0);
        LocalDateTime now = LocalDateTime.now();
        partida.setTurnoInicio(now);

        Set<Integer> occupiedSlots = buildOccupiedSlots(participaciones);
        if (!occupiedSlots.isEmpty()) {
            turnRuntimeByPartida.put(
                    partida.getIdPartida(),
                    new TurnRuntime(occupiedSlots, 0, now.plusSeconds(TURN_TIMEOUT_SECONDS))
            );
        }
    }

    private PartidaEntity mustGetRunningPartida(Integer idPartida) {
        PartidaEntity partida = partidaRepository.findById(idPartida)
                .orElseThrow(() -> new NoSuchElementException("Partida no encontrada: " + idPartida));
        ensureDefaultState(partida);
        if (!partida.isCorriendo() || !ESTADO_RUNNING.equals(partida.getEstado())) {
            throw new IllegalStateException("La partida no esta en curso");
        }
        return partida;
    }

    private ParticipacionEntity mustGetParticipacion(Integer idPartida, Integer idJugador) {
        ParticipacionId id = new ParticipacionId(idJugador, idPartida);
        return participacionRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Participacion no encontrada para jugador " + idJugador));
    }

    private void validatePlayerTurn(PartidaEntity partida, ParticipacionEntity participacion) {
        Integer slot = participacion.getOrdenTurno();
        if (slot == null) {
            throw new IllegalStateException("La participacion no tiene orden de turno asignado");
        }

        int currentTurn = normalizeTurn(partida.getTurno());
        if (normalizeTurn(slot) != currentTurn) {
            throw new IllegalStateException("No es tu turno");
        }
    }

    private PartidaDTO advanceTurn(PartidaEntity partida, LocalDateTime now) {
        Integer idPartida = partida.getIdPartida();
        TurnRuntime runtime = turnRuntimeByPartida.get(idPartida);
        if (runtime == null) {
            runtime = createRuntimeFromDatabase(partida, now);
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
        runtime.currentTurn = nextTurn;
        runtime.deadline = now.plusSeconds(TURN_TIMEOUT_SECONDS);

        partida.setTurno(nextTurn);
        partida.setTurnoInicio(now);
        return Mapper.toDTO(partidaRepository.save(partida));
    }

    private void finishGame(PartidaEntity partida, Integer winnerId) {
        List<ParticipacionEntity> participaciones = getOrderedParticipaciones(partida.getIdPartida());
        Map<Integer, Integer> remaining = new HashMap<>();
        int winnerPoints = 0;

        for (ParticipacionEntity participacion : participaciones) {
            int jugadorId = participacion.getJugador().getId();
            int points = calculateHandPoints(parseTileList(participacion.getManoActual()));
            remaining.put(jugadorId, points);
            if (jugadorId != winnerId) {
                winnerPoints += points;
            }
        }

        for (ParticipacionEntity participacion : participaciones) {
            JugadorEntity jugador = participacion.getJugador();
            jugador.setPartidasFinalizadas(jugador.getPartidasFinalizadas() + 1);
            if (jugador.getId().equals(winnerId)) {
                jugador.setPartidasGanadas(jugador.getPartidasGanadas() + 1);
                jugador.setMonedas(jugador.getMonedas() + winnerPoints);
            } else {
                jugador.setPartidasPerdidas(jugador.getPartidasPerdidas() + 1);
            }
        }
        jugadorRepository.saveAll(participaciones.stream().map(ParticipacionEntity::getJugador).toList());

        partida.setCorriendo(false);
        partida.setEstado(ESTADO_FINISHED);
        partida.setGanadorId(winnerId);
        partida.setPuntuacionFinal(buildScoreSummary(winnerId, winnerPoints, remaining));
        partida.setTurnoInicio(null);
        turnRuntimeByPartida.remove(partida.getIdPartida());
    }

    private String buildScoreSummary(Integer winnerId, int winnerPoints, Map<Integer, Integer> remaining) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"winnerId\":").append(winnerId)
                .append(",\"winnerPoints\":").append(winnerPoints)
                .append(",\"remaining\":{");

        boolean first = true;
        for (Map.Entry<Integer, Integer> entry : remaining.entrySet()) {
            if (!first) {
                sb.append(",");
            }
            sb.append("\"").append(entry.getKey()).append("\":").append(entry.getValue());
            first = false;
        }
        sb.append("}}");
        return sb.toString();
    }

    private int calculateHandPoints(List<String> hand) {
        int points = 0;
        for (String tile : hand) {
            if (isJoker(tile)) {
                points += 30;
            } else {
                points += parseValue(tile);
            }
        }
        return points;
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
            if (partida == null || !partida.isCorriendo() || !ESTADO_RUNNING.equals(partida.getEstado())) {
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
        if (!partida.isCorriendo()) {
            return null;
        }
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

    private void ensureDefaultState(PartidaEntity partida) {
        if (partida.getEstado() == null || partida.getEstado().isBlank()) {
            partida.setEstado(partida.isCorriendo() ? ESTADO_RUNNING : ESTADO_WAITING);
        }
        if (partida.getPuntuacionFinal() == null) {
            partida.setPuntuacionFinal("");
        }
    }

    private List<List<String>> normalizeGroups(List<List<String>> groups) {
        List<List<String>> normalized = new ArrayList<>();
        for (List<String> group : groups) {
            if (group == null || group.size() < 3) {
                throw new IllegalArgumentException("Cada grupo debe tener al menos 3 fichas");
            }
            List<String> current = new ArrayList<>();
            for (String tile : group) {
                current.add(normalizeTile(tile));
            }
            normalized.add(current);
        }
        return normalized;
    }

    private boolean isValidRummikubGroup(List<String> group) {
        return isValidSet(group) || isValidRun(group);
    }

    private boolean isValidSet(List<String> group) {
        if (group.size() < 3 || group.size() > 4) {
            return false;
        }

        Integer value = null;
        Set<Character> colors = new HashSet<>();
        for (String tile : group) {
            if (isJoker(tile)) {
                continue;
            }
            int tileValue = parseValue(tile);
            char color = parseColor(tile);
            if (value == null) {
                value = tileValue;
            } else if (!value.equals(tileValue)) {
                return false;
            }
            if (!colors.add(color)) {
                return false;
            }
        }
        return true;
    }

    private boolean isValidRun(List<String> group) {
        if (group.size() < 3) {
            return false;
        }

        int jokerCount = 0;
        Character color = null;
        List<Integer> values = new ArrayList<>();

        for (String tile : group) {
            if (isJoker(tile)) {
                jokerCount++;
                continue;
            }
            char tileColor = parseColor(tile);
            if (color == null) {
                color = tileColor;
            } else if (!color.equals(tileColor)) {
                return false;
            }
            int value = parseValue(tile);
            if (values.contains(value)) {
                return false;
            }
            values.add(value);
        }

        if (values.isEmpty()) {
            return true;
        }

        Collections.sort(values);
        int requiredJokers = 0;
        for (int i = 1; i < values.size(); i++) {
            int diff = values.get(i) - values.get(i - 1);
            if (diff <= 0) {
                return false;
            }
            requiredJokers += diff - 1;
        }
        return requiredJokers <= jokerCount;
    }

    private List<String> removeTilesFromHand(List<String> hand, List<String> playedTiles) {
        List<String> remaining = new ArrayList<>(hand);
        for (String tile : playedTiles) {
            boolean removed = remaining.remove(tile);
            if (!removed) {
                throw new IllegalStateException("No se pudo retirar la ficha " + tile + " de la mano");
            }
        }
        return remaining;
    }

    private Map<String, Integer> buildCountMap(List<String> tiles) {
        Map<String, Integer> count = new HashMap<>();
        for (String tile : tiles) {
            count.put(tile, count.getOrDefault(tile, 0) + 1);
        }
        return count;
    }

    private List<String> parseTileList(String csv) {
        List<String> tiles = new ArrayList<>();
        if (csv == null || csv.isBlank()) {
            return tiles;
        }

        String[] parts = csv.split(",");
        for (String raw : parts) {
            String tile = raw == null ? "" : raw.trim();
            if (!tile.isEmpty()) {
                tiles.add(tile);
            }
        }
        return tiles;
    }

    private List<List<String>> parseMesaGroups(String encoded) {
        List<List<String>> groups = new ArrayList<>();
        if (encoded == null || encoded.isBlank()) {
            return groups;
        }

        String[] groupParts = encoded.split(";");
        for (String groupRaw : groupParts) {
            if (groupRaw == null || groupRaw.isBlank()) {
                continue;
            }
            List<String> tiles = parseTileList(groupRaw);
            if (!tiles.isEmpty()) {
                groups.add(tiles);
            }
        }
        return groups;
    }

    private String serializeMesaGroups(List<List<String>> groups) {
        if (groups == null || groups.isEmpty()) {
            return "";
        }
        List<String> encoded = new ArrayList<>();
        for (List<String> group : groups) {
            if (group != null && !group.isEmpty()) {
                encoded.add(serializeTileList(group));
            }
        }
        return String.join(";", encoded);
    }

    private String serializeTileList(List<String> tiles) {
        if (tiles == null || tiles.isEmpty()) {
            return "";
        }
        return String.join(",", tiles);
    }

    private String normalizeTile(String tileRaw) {
        if (tileRaw == null || tileRaw.isBlank()) {
            throw new IllegalArgumentException("Ficha vacia en jugada");
        }
        String tile = tileRaw.trim().toUpperCase();
        if ("J1".equals(tile) || "J2".equals(tile)) {
            return tile;
        }

        Matcher matcher = TILE_PATTERN.matcher(tile);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Formato de ficha invalido: " + tileRaw);
        }
        return tile;
    }

    private boolean isJoker(String tile) {
        return "J1".equals(tile) || "J2".equals(tile);
    }

    private char parseColor(String tile) {
        return tile.charAt(0);
    }

    private int parseValue(String tile) {
        return Integer.parseInt(tile.substring(1));
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
