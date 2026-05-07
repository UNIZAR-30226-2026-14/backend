package com.rummikub.server.application.services;

import com.rummikub.server.api.dto.PartidaDTO;
import com.rummikub.server.api.dto.bot.BotMoveDTO;
import com.rummikub.server.api.dto.bot.BotMoveRequest;
import com.rummikub.server.api.dto.bot.BotMoveResponse;
import com.rummikub.server.api.dto.partida.MercadoItemDTO;
import com.rummikub.server.api.dto.partida.MercadoParticipacionDTO;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PartidaService {

    private static final int MAX_TURN_SLOTS = 4;
    private static final int INITIAL_HAND_SIZE = 14;
    private static final int TURN_TIMEOUT_SECONDS = 60;
    private static final String BOT_NAME_PREFIX = "BOT_";
    private static final int BOT_LEVEL = 5;
    private static final double BOT_RANDOMNESS = 0.20;
    private static final int MAX_AUTOMATED_BOT_TURNS = 16;
    private static final int MARKET_OBJECTS_PER_PLAYER = 4;
    private static final int MARKET_ITEM_STOCK = 1;
    private static final int INACTIVITY_LIMIT_TURNS = 2;
    private static final String JOKER_CANONICAL = "J*";

    private static final String ESTADO_WAITING = "WAITING";
    private static final String ESTADO_RUNNING = "RUNNING";
    private static final String ESTADO_PAUSED = "PAUSED";
    private static final String ESTADO_FINISHED = "FINISHED";

    private static final Pattern TILE_PATTERN = Pattern.compile("^([RBOK])(1[0-3]|[1-9])$");
    private static final Pattern MARKET_OBJECT_PATTERN = Pattern.compile("^(obj[1-7])$", Pattern.CASE_INSENSITIVE);
    private static final List<String> MARKET_OBJECT_CODES = List.of("obj1", "obj2", "obj3", "obj4", "obj5", "obj6", "obj7");
    private static final Map<String, Integer> MARKET_OBJECT_VALUES = Map.of(
            "obj1", 10,
            "obj2", 20,
            "obj3", 30,
            "obj4", 40,
            "obj5", 50,
            "obj6", 60,
            "obj7", 70
    );
    private static final List<String> ARCADE_EVENTS = List.of(
            "LLUVIA_DORADA",
            "CAMBIO_DE_COLOR",
            "TURNO_DOBLE",
            "ROBO_EXTRA",
            "BLOQUEO_MERCADO"
    );

    private final PartidaRepository partidaRepository;
    private final ParticipacionRepository participacionRepository;
    private final JugadorRepository jugadorRepository;
    private final BotIntegrationService botIntegrationService;
    private final Map<Integer, TurnRuntime> turnRuntimeByPartida = new ConcurrentHashMap<>();
    private final Object turnMutex = new Object();

    public PartidaService(
            PartidaRepository partidaRepository,
            ParticipacionRepository participacionRepository,
            JugadorRepository jugadorRepository,
            BotIntegrationService botIntegrationService) {
        this.partidaRepository = partidaRepository;
        this.participacionRepository = participacionRepository;
        this.jugadorRepository = jugadorRepository;
        this.botIntegrationService = botIntegrationService;
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
    public MercadoParticipacionDTO getMercadoJugador(Integer idPartida, Integer idJugador) {
        PartidaEntity partida = partidaRepository.findById(idPartida)
                .orElseThrow(() -> new NoSuchElementException("Partida no encontrada: " + idPartida));
        ensureDefaultState(partida);

        ParticipacionEntity participacion = mustGetParticipacion(idPartida, idJugador);
        validateArcadeModeForMarket(partida);
        List<String> habilidadesCompradas = parsePurchasedObjectCodes(participacion.getHabilidadesActuales());
        String serializedHabilidades = serializePurchasedObjectCodes(habilidadesCompradas);
        if (!serializedHabilidades.equals(safe(participacion.getHabilidadesActuales()))) {
            participacion.setHabilidadesActuales(serializedHabilidades);
            participacionRepository.save(participacion);
        }

        LinkedHashMap<String, Integer> stockByCode = getOrCreateMarketStockByPlayer(partida, idJugador);
        return buildMercadoParticipacionDTO(idPartida, idJugador, participacion.getJugador().getMonedas(), stockByCode,
                habilidadesCompradas);
    }

    @Transactional
    public MercadoParticipacionDTO comprarObjetoMercado(Integer idPartida, Integer idJugador, String codigoObjetoRaw) {
        if (codigoObjetoRaw == null || codigoObjetoRaw.isBlank()) {
            throw new IllegalArgumentException("codigoObjeto es obligatorio");
        }

        PartidaEntity partida = partidaRepository.findById(idPartida)
                .orElseThrow(() -> new NoSuchElementException("Partida no encontrada: " + idPartida));
        ensureDefaultState(partida);
        validateArcadeModeForMarket(partida);

        ParticipacionEntity participacion = mustGetParticipacion(idPartida, idJugador);
        String codigoObjeto = normalizeMarketObjectCode(codigoObjetoRaw);

        LinkedHashMap<String, Integer> stockByCode = getOrCreateMarketStockByPlayer(partida, idJugador);
        int stockActual = stockByCode.getOrDefault(codigoObjeto, 0);
        if (stockActual <= 0) {
            throw new IllegalStateException("El objeto " + codigoObjeto + " no esta disponible en tu mercado");
        }

        int coste = MARKET_OBJECT_VALUES.get(codigoObjeto);
        JugadorEntity jugador = participacion.getJugador();
        if (jugador.getMonedas() < coste) {
            throw new IllegalStateException("No tienes monedas suficientes para comprar " + codigoObjeto);
        }

        jugador.setMonedas(jugador.getMonedas() - coste);
        jugadorRepository.save(jugador);

        stockByCode.put(codigoObjeto, stockActual - 1);
        updateMarketStockByPlayer(partida, idJugador, stockByCode);

        List<String> habilidadesCompradas = parsePurchasedObjectCodes(participacion.getHabilidadesActuales());
        habilidadesCompradas.add(codigoObjeto);
        participacion.setHabilidadesActuales(serializePurchasedObjectCodes(habilidadesCompradas));
        participacionRepository.save(participacion);

        return buildMercadoParticipacionDTO(
                idPartida,
                idJugador,
                jugador.getMonedas(),
                stockByCode,
                habilidadesCompradas
        );
    }

    @Transactional
    public PartidaDTO create(PartidaDTO dto) {
        PartidaEntity partida = new PartidaEntity();
        partida.setTurno(dto.getTurno());
        partida.setFecha(dto.getFecha() == null ? LocalDate.now() : dto.getFecha());
        partida.setBolsa(safe(dto.getBolsa()));
        partida.setMercado(safe(dto.getMercado()));
        partida.setConjuntoMesa(safe(dto.getConjuntoMesa()));
        partida.setEventoActual("");
        partida.setModoArcade(Boolean.TRUE.equals(dto.getModoArcade()));
        if (!partida.isModoArcade()) {
            partida.setMercado("");
        }
        partida.setTurnoInicio(null);
        partida.setGanadorId(null);
        partida.setPuntuacionFinal("");

        partida = partidaRepository.save(partida);

        if (dto.isCorriendo()) {
            partida.setCorriendo(true);
            partida.setEstado(ESTADO_RUNNING);
            initializeGameState(partida);
            partida = partidaRepository.save(partida);
        } else {
            partida.setCorriendo(false);
            partida.setEstado(ESTADO_WAITING);
        }

        return Mapper.toDTO(partida);
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
        if (dto.getModoArcade() != null) {
            partida.setModoArcade(dto.getModoArcade());
        }
        if (!partida.isModoArcade()) {
            partida.setMercado("");
            partida.setEventoActual("");
        }
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
            if (hasExistingGameState(partida)) {
                TurnRuntime runtime = createRuntimeFromDatabase(partida, LocalDateTime.now());
                if (runtime != null) {
                    turnRuntimeByPartida.put(partida.getIdPartida(), runtime);
                }
            } else {
                initializeGameState(partida);
            }
            partida = partidaRepository.save(partida);
            PartidaDTO result = runAutomatedBotTurnsIfNeeded(partida, LocalDateTime.now());
            return result == null ? Mapper.toDTO(partida) : result;
        }
    }

    @Transactional
    public PartidaDTO pausarPartida(Integer idPartida, Integer idJugadorSolicitante) {
        synchronized (turnMutex) {
            PartidaEntity partida = mustGetRunningPartida(idPartida);
            mustGetParticipacion(idPartida, idJugadorSolicitante);

            partida.setCorriendo(false);
            partida.setEstado(ESTADO_PAUSED);
            partida.setTurnoInicio(null);
            turnRuntimeByPartida.remove(idPartida);

            return Mapper.toDTO(partidaRepository.save(partida));
        }
    }

    @Transactional
    public PartidaDTO reanudarPartida(Integer idPartida, Integer idJugadorSolicitante) {
        synchronized (turnMutex) {
            PartidaEntity partida = partidaRepository.findById(idPartida)
                    .orElseThrow(() -> new NoSuchElementException("Partida no encontrada: " + idPartida));
            ensureDefaultState(partida);
            mustGetParticipacion(idPartida, idJugadorSolicitante);

            if (ESTADO_FINISHED.equals(partida.getEstado())) {
                throw new IllegalStateException("La partida ya finalizo");
            }
            if (partida.isCorriendo() || ESTADO_RUNNING.equals(partida.getEstado())) {
                throw new IllegalStateException("La partida ya esta en curso");
            }
            if (!hasExistingGameState(partida)) {
                throw new IllegalStateException("La partida aun no se ha iniciado, usa /iniciar");
            }

            partida.setCorriendo(true);
            partida.setEstado(ESTADO_RUNNING);
            LocalDateTime now = LocalDateTime.now();
            partida.setTurnoInicio(now);

            TurnRuntime runtime = createRuntimeFromDatabase(partida, now);
            if (runtime == null) {
                throw new IllegalStateException("La partida no tiene jugadores activos");
            }
            turnRuntimeByPartida.put(partida.getIdPartida(), runtime);

            partida = partidaRepository.save(partida);
            PartidaDTO botUpdated = runAutomatedBotTurnsIfNeeded(partida, now);
            return botUpdated == null ? Mapper.toDTO(partida) : botUpdated;
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
            registerInactiveTurn(participacion);
            PartidaDTO updated = advanceTurn(partida, LocalDateTime.now());
            PartidaDTO botUpdated = runAutomatedBotTurnsIfNeeded(partida, LocalDateTime.now());
            return botUpdated == null ? updated : botUpdated;
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
            registerInactiveTurn(participacion);
            PartidaDTO updated = advanceTurn(partida, LocalDateTime.now());
            PartidaDTO botUpdated = runAutomatedBotTurnsIfNeeded(partida, LocalDateTime.now());
            PartidaDTO result = botUpdated == null ? updated : botUpdated;
            result.setFichaRobada(drawnTile);
            return result;
        }
    }

    @Transactional
    public PartidaDTO robarSinPasarTurno(Integer idPartida, Integer idJugador) {
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
            PartidaDTO result = Mapper.toDTO(partidaRepository.save(partida));
            result.setFichaRobada(drawnTile);
            return result;
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
            participacion.setTurnosInactivo(0);
            participacionRepository.save(participacion);

            List<List<String>> mesa = parseMesaGroups(partida.getConjuntoMesa());
            mesa.addAll(normalizedGroups);
            partida.setConjuntoMesa(serializeMesaGroups(mesa));

            if (updatedHand.isEmpty()) {
                finishGame(partida, idJugador);
                return Mapper.toDTO(partidaRepository.save(partida));
            }

            PartidaDTO updated = advanceTurn(partida, LocalDateTime.now());
            PartidaDTO botUpdated = runAutomatedBotTurnsIfNeeded(partida, LocalDateTime.now());
            return botUpdated == null ? updated : botUpdated;
        }
    }

    @Transactional
    public PartidaDTO jugarAvanzado(
            Integer idPartida,
            Integer idJugador,
            String moveType,
            List<List<String>> grupos,
            Integer extendIndex,
            List<String> extensionTiles,
            List<List<String>> newBoard) {
        synchronized (turnMutex) {
            if (moveType == null || moveType.isBlank()) {
                throw new IllegalArgumentException("moveType es obligatorio");
            }

            String action = moveType.trim().toLowerCase(Locale.ROOT);
            if ("play_melds".equals(action)) {
                return jugarGrupos(idPartida, idJugador, grupos);
            }

            PartidaEntity partida = mustGetRunningPartida(idPartida);
            ParticipacionEntity participacion = mustGetParticipacion(idPartida, idJugador);
            validatePlayerTurn(partida, participacion);

            if ("extend_meld".equals(action)) {
                return jugarExtendHumano(partida, participacion, extendIndex, extensionTiles);
            }
            if ("replace_board".equals(action)) {
                return jugarReplaceBoardHumano(partida, participacion, newBoard);
            }
            throw new IllegalArgumentException("moveType no soportado: " + moveType);
        }
    }

    @Transactional
    public PartidaDTO salirPartida(Integer idPartida, Integer idJugador) {
        synchronized (turnMutex) {
            PartidaEntity partida = partidaRepository.findById(idPartida)
                    .orElseThrow(() -> new NoSuchElementException("Partida no encontrada: " + idPartida));
            ensureDefaultState(partida);

            ParticipacionEntity participacion = mustGetParticipacion(idPartida, idJugador);
            if (!partida.isCorriendo() || !ESTADO_RUNNING.equals(partida.getEstado())) {
                participacionRepository.delete(participacion);
                return Mapper.toDTO(partida);
            }

            JugadorEntity bot = createBotPlayer(partida.getIdPartida());
            ParticipacionEntity botParticipacion = new ParticipacionEntity();
            botParticipacion.setId(new ParticipacionId(bot.getId(), partida.getIdPartida()));
            botParticipacion.setJugador(bot);
            botParticipacion.setPartida(partida);
            botParticipacion.setFichasActuales(participacion.getFichasActuales());
            botParticipacion.setHabilidadesActuales(participacion.getHabilidadesActuales());
            botParticipacion.setManoActual(participacion.getManoActual());
            botParticipacion.setOrdenTurno(participacion.getOrdenTurno());
            botParticipacion.setTurnosInactivo(participacion.getTurnosInactivo());

            participacionRepository.delete(participacion);
            participacionRepository.save(botParticipacion);

            LocalDateTime now = LocalDateTime.now();
            TurnRuntime runtime = createRuntimeFromDatabase(partida, now);
            if (runtime != null) {
                turnRuntimeByPartida.put(partida.getIdPartida(), runtime);
            }

            PartidaDTO botUpdated = runAutomatedBotTurnsIfNeeded(partida, now);
            return botUpdated == null ? Mapper.toDTO(partida) : botUpdated;
        }
    }

    @Transactional
    public PartidaDTO finalizarPartida(Integer idPartida, Integer idJugadorSolicitante) {
        synchronized (turnMutex) {
            PartidaEntity partida = partidaRepository.findById(idPartida)
                    .orElseThrow(() -> new NoSuchElementException("Partida no encontrada: " + idPartida));
            ensureDefaultState(partida);

            mustGetParticipacion(idPartida, idJugadorSolicitante);

            if (ESTADO_FINISHED.equals(partida.getEstado())) {
                throw new IllegalStateException("La partida ya esta finalizada");
            }
            if (!partida.isCorriendo() || !ESTADO_RUNNING.equals(partida.getEstado())) {
                throw new IllegalStateException("Solo se puede finalizar una partida en curso");
            }

            List<ParticipacionEntity> participaciones = getOrderedParticipaciones(idPartida);
            if (participaciones.isEmpty()) {
                throw new IllegalStateException("La partida no tiene jugadores activos");
            }

            Integer winnerId = participaciones.stream()
                    .min(Comparator
                            .comparingInt((ParticipacionEntity p) -> calculateHandPoints(parseTileList(p.getManoActual())))
                            .thenComparingInt(p -> p.getJugador().getId()))
                    .map(p -> p.getJugador().getId())
                    .orElseThrow(() -> new IllegalStateException("No se pudo calcular ganador"));

            finishGame(partida, winnerId);
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
            if (runtime == null || now.isBefore(runtime.deadline)) {
                continue;
            }
            advanceTurnOnTimeout(partidaId, now);
        }
    }

    private void initializeGameState(PartidaEntity partida) {
        List<ParticipacionEntity> participaciones = ensurePlayersAndGetForStart(partida);

        if (participaciones.isEmpty()) {
            throw new IllegalStateException("No se puede iniciar la partida sin jugadores en PARTICIPACION");
        }
        if (participaciones.size() > MAX_TURN_SLOTS) {
            throw new IllegalStateException("Solo se permiten 4 jugadores por partida");
        }

        List<String> bag = createAndShuffleFullBag();
        Map<Integer, LinkedHashMap<String, Integer>> marketByPlayer = new HashMap<>();
        for (int i = 0; i < participaciones.size(); i++) {
            ParticipacionEntity participacion = participaciones.get(i);
            participacion.setOrdenTurno(i);

            List<String> hand = drawTiles(bag, INITIAL_HAND_SIZE);
            participacion.setManoActual(serializeTileList(hand));
            participacion.setFichasActuales(hand.size());
            participacion.setHabilidadesActuales("");
            participacion.setTurnosInactivo(0);
            if (partida.isModoArcade()) {
                marketByPlayer.put(participacion.getJugador().getId(), createRandomMarketStock());
            }
        }
        participacionRepository.saveAll(participaciones);

        partida.setConjuntoMesa("");
        partida.setMercado(partida.isModoArcade() ? serializePartidaMarket(marketByPlayer) : "");
        partida.setBolsa(serializeTileList(bag));
        partida.setEventoActual(partida.isModoArcade() ? getRandomArcadeEvent() : "");
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

    private List<ParticipacionEntity> ensurePlayersAndGetForStart(PartidaEntity partida) {
        List<ParticipacionEntity> participaciones = getOrderedParticipaciones(partida.getIdPartida());
        if (participaciones.isEmpty()) {
            return participaciones;
        }

        int missing = MAX_TURN_SLOTS - participaciones.size();
        if (missing <= 0) {
            return participaciones;
        }

        for (int i = 0; i < missing; i++) {
            JugadorEntity bot = createBotPlayer(partida.getIdPartida());
            ParticipacionEntity botParticipacion = new ParticipacionEntity();
            botParticipacion.setId(new ParticipacionId(bot.getId(), partida.getIdPartida()));
            botParticipacion.setJugador(bot);
            botParticipacion.setPartida(partida);
            botParticipacion.setFichasActuales(0);
            botParticipacion.setHabilidadesActuales("");
            botParticipacion.setManoActual("");
            botParticipacion.setOrdenTurno(null);
            participacionRepository.save(botParticipacion);
        }

        return getOrderedParticipaciones(partida.getIdPartida());
    }

    private JugadorEntity createBotPlayer(Integer idPartida) {
        int suffix = 1;
        String candidate;
        do {
            candidate = BOT_NAME_PREFIX + idPartida + "_" + suffix;
            suffix++;
        } while (jugadorRepository.existsByNombreIgnoreCase(candidate));
        return jugadorRepository.save(new JugadorEntity(candidate, "bot"));
    }

    private boolean isBotPlayer(ParticipacionEntity participacion) {
        return participacion != null
                && participacion.getJugador() != null
                && participacion.getJugador().getNombre() != null
                && participacion.getJugador().getNombre().startsWith(BOT_NAME_PREFIX);
    }

    private PartidaDTO runAutomatedBotTurnsIfNeeded(PartidaEntity partida, LocalDateTime now) {
        if (partida == null || !partida.isCorriendo() || !ESTADO_RUNNING.equals(partida.getEstado())) {
            return null;
        }

        PartidaDTO lastState = null;
        int safety = 0;
        while (partida.isCorriendo() && ESTADO_RUNNING.equals(partida.getEstado()) && safety < MAX_AUTOMATED_BOT_TURNS) {
            safety++;
            ParticipacionEntity botTurn = getParticipacionByTurn(partida.getIdPartida(), partida.getTurno());
            if (!isBotPlayer(botTurn)) {
                break;
            }

            BotMoveResponse moveResponse = askBotMove(partida, botTurn);
            applyBotMove(partida, botTurn, moveResponse, now);
            partida = partidaRepository.save(partida);
            lastState = Mapper.toDTO(partida);
        }
        return lastState;
    }

    private ParticipacionEntity getParticipacionByTurn(Integer idPartida, int turno) {
        int slot = normalizeTurn(turno);
        List<ParticipacionEntity> participaciones = getOrderedParticipaciones(idPartida);
        return participaciones.stream()
                .filter(p -> p.getOrdenTurno() != null && normalizeTurn(p.getOrdenTurno()) == slot)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No hay jugador asignado al turno " + slot));
    }

    private BotMoveResponse askBotMove(PartidaEntity partida, ParticipacionEntity botParticipacion) {
        BotMoveRequest payload = new BotMoveRequest();
        payload.setBoard(toIaBoard(parseMesaGroups(partida.getConjuntoMesa())));
        payload.setPoolCount(parseTileList(partida.getBolsa()).size());
        payload.setMyTiles(toIaTiles(parseTileList(botParticipacion.getManoActual())));
        payload.setOpponentRackCounts(getOpponentRackCounts(partida.getIdPartida(), botParticipacion.getJugador().getId()));
        payload.setOpened(!parseMesaGroups(partida.getConjuntoMesa()).isEmpty());
        payload.setLevel(BOT_LEVEL);
        payload.setRandomness(BOT_RANDOMNESS);
        payload.setTurnNumber(partida.getTurno());
        return botIntegrationService.askMove(payload);
    }

    private List<Integer> getOpponentRackCounts(Integer idPartida, Integer idBotJugador) {
        List<ParticipacionEntity> participaciones = getOrderedParticipaciones(idPartida);
        List<Integer> counts = new ArrayList<>();
        for (ParticipacionEntity p : participaciones) {
            if (!p.getJugador().getId().equals(idBotJugador)) {
                counts.add(p.getFichasActuales());
            }
        }
        return counts;
    }

    private void applyBotMove(
            PartidaEntity partida,
            ParticipacionEntity botParticipacion,
            BotMoveResponse moveResponse,
            LocalDateTime now) {
        if (moveResponse == null || moveResponse.getMove() == null || moveResponse.getMove().getMoveType() == null) {
            throw new IllegalStateException("La IA no devolvio una jugada valida");
        }

        BotMoveDTO move = moveResponse.getMove();
        String moveType = move.getMoveType();
        switch (moveType) {
            case "pass":
                drawOneTileIfPossible(partida, botParticipacion);
                advanceTurn(partida, now);
                return;
            case "play_melds":
                applyBotPlayMelds(partida, botParticipacion, move.getNewMelds(), now);
                return;
            case "extend_meld":
                applyBotExtendMeld(partida, botParticipacion, move.getExtendIndex(), move.getExtensionTiles(), now);
                return;
            case "replace_board":
                applyBotReplaceBoard(partida, botParticipacion, move.getNewBoard(), now);
                return;
            default:
                throw new IllegalStateException("Tipo de jugada de IA no soportado: " + moveType);
        }
    }

    private void drawOneTileIfPossible(PartidaEntity partida, ParticipacionEntity participacion) {
        List<String> bag = parseTileList(partida.getBolsa());
        if (bag.isEmpty()) {
            return;
        }
        String drawn = bag.remove(0);
        List<String> hand = parseTileList(participacion.getManoActual());
        hand.add(drawn);
        participacion.setManoActual(serializeTileList(hand));
        participacion.setFichasActuales(hand.size());
        participacion.setTurnosInactivo(0);
        participacionRepository.save(participacion);
        partida.setBolsa(serializeTileList(bag));
    }

    private PartidaDTO jugarExtendHumano(
            PartidaEntity partida,
            ParticipacionEntity participacion,
            Integer extendIndex,
            List<String> extensionTiles) {
        if (extendIndex == null || extensionTiles == null || extensionTiles.size() != 1) {
            throw new IllegalArgumentException("Para extend_meld debes enviar extendIndex y una extensionTiles");
        }

        List<List<String>> mesa = parseMesaGroups(partida.getConjuntoMesa());
        if (extendIndex < 0 || extendIndex >= mesa.size()) {
            throw new IllegalArgumentException("extendIndex fuera de rango");
        }

        String tile = normalizeTile(extensionTiles.get(0));
        List<String> hand = parseTileList(participacion.getManoActual());
        if (!hand.contains(tile)) {
            throw new IllegalStateException("La ficha " + tile + " no esta en tu mano");
        }

        List<String> target = new ArrayList<>(mesa.get(extendIndex));
        List<String> appended = new ArrayList<>(target);
        appended.add(tile);

        List<String> prepended = new ArrayList<>();
        prepended.add(tile);
        prepended.addAll(target);

        List<String> extended;
        if (isValidRummikubGroup(appended)) {
            extended = appended;
        } else if (isValidRummikubGroup(prepended)) {
            extended = prepended;
        } else {
            throw new IllegalArgumentException("No se puede extender ese grupo con la ficha indicada");
        }

        List<String> updatedHand = removeTilesFromHand(hand, List.of(tile));
        participacion.setManoActual(serializeTileList(updatedHand));
        participacion.setFichasActuales(updatedHand.size());
        participacion.setTurnosInactivo(0);
        participacionRepository.save(participacion);

        mesa.set(extendIndex, extended);
        partida.setConjuntoMesa(serializeMesaGroups(mesa));

        if (updatedHand.isEmpty()) {
            finishGame(partida, participacion.getJugador().getId());
            return Mapper.toDTO(partidaRepository.save(partida));
        }

        PartidaDTO updated = advanceTurn(partida, LocalDateTime.now());
        PartidaDTO botUpdated = runAutomatedBotTurnsIfNeeded(partida, LocalDateTime.now());
        return botUpdated == null ? updated : botUpdated;
    }

    private PartidaDTO jugarReplaceBoardHumano(
            PartidaEntity partida,
            ParticipacionEntity participacion,
            List<List<String>> newBoard) {
        if (newBoard == null || newBoard.isEmpty()) {
            throw new IllegalArgumentException("Para replace_board debes enviar newBoard");
        }

        List<List<String>> normalized = normalizeGroups(newBoard);
        for (List<String> group : normalized) {
            if (!isValidRummikubGroup(group)) {
                throw new IllegalArgumentException("Grupo invalido en newBoard: " + group);
            }
        }

        List<List<String>> currentBoard = parseMesaGroups(partida.getConjuntoMesa());
        List<String> hand = parseTileList(participacion.getManoActual());
        Map<String, Integer> boardCount = buildCountMap(flattenGroups(currentBoard));
        Map<String, Integer> handCount = buildCountMap(hand);

        Map<String, Integer> normalizedCount = buildCountMap(flattenGroups(normalized));
        if (!isSubsetCount(normalizedCount, mergeCounts(boardCount, handCount))) {
            throw new IllegalStateException("newBoard usa fichas inexistentes en tablero/mano");
        }

        Map<String, Integer> usedFromHand = subtractCountsIgnoringNegatives(normalizedCount, boardCount);
        if (!isSubsetCount(usedFromHand, handCount)) {
            throw new IllegalStateException("newBoard requiere fichas no disponibles en mano");
        }

        List<String> tilesToRemove = expandCountMap(usedFromHand);
        List<String> updatedHand = removeTilesFromHand(hand, tilesToRemove);
        participacion.setManoActual(serializeTileList(updatedHand));
        participacion.setFichasActuales(updatedHand.size());
        participacionRepository.save(participacion);

        partida.setConjuntoMesa(serializeMesaGroups(normalized));

        if (updatedHand.isEmpty()) {
            finishGame(partida, participacion.getJugador().getId());
            return Mapper.toDTO(partidaRepository.save(partida));
        }

        PartidaDTO updated = advanceTurn(partida, LocalDateTime.now());
        PartidaDTO botUpdated = runAutomatedBotTurnsIfNeeded(partida, LocalDateTime.now());
        return botUpdated == null ? updated : botUpdated;
    }

    private void applyBotPlayMelds(
            PartidaEntity partida,
            ParticipacionEntity botParticipacion,
            List<List<String>> iaMelds,
            LocalDateTime now) {
        if (iaMelds == null || iaMelds.isEmpty()) {
            drawOneTileIfPossible(partida, botParticipacion);
            advanceTurn(partida, now);
            return;
        }

        List<String> hand = parseTileList(botParticipacion.getManoActual());
        Map<String, Integer> handCount = buildCountMap(hand);
        List<List<String>> normalizedGroups = normalizeIaGroupsWithHand(iaMelds, handCount);

        List<String> playedTiles = new ArrayList<>();
        for (List<String> group : normalizedGroups) {
            if (!isValidRummikubGroup(group)) {
                throw new IllegalStateException("La IA devolvio un grupo invalido: " + group);
            }
            playedTiles.addAll(group);
        }

        List<String> updatedHand = removeTilesFromHand(hand, playedTiles);
        botParticipacion.setManoActual(serializeTileList(updatedHand));
        botParticipacion.setFichasActuales(updatedHand.size());
        botParticipacion.setTurnosInactivo(0);
        participacionRepository.save(botParticipacion);

        List<List<String>> mesa = parseMesaGroups(partida.getConjuntoMesa());
        mesa.addAll(normalizedGroups);
        partida.setConjuntoMesa(serializeMesaGroups(mesa));

        if (updatedHand.isEmpty()) {
            finishGame(partida, botParticipacion.getJugador().getId());
            return;
        }
        advanceTurn(partida, now);
    }

    private void applyBotExtendMeld(
            PartidaEntity partida,
            ParticipacionEntity botParticipacion,
            Integer extendIndex,
            List<String> extensionTiles,
            LocalDateTime now) {
        if (extendIndex == null || extensionTiles == null || extensionTiles.size() != 1) {
            drawOneTileIfPossible(partida, botParticipacion);
            advanceTurn(partida, now);
            return;
        }

        List<List<String>> mesa = parseMesaGroups(partida.getConjuntoMesa());
        if (extendIndex < 0 || extendIndex >= mesa.size()) {
            drawOneTileIfPossible(partida, botParticipacion);
            advanceTurn(partida, now);
            return;
        }

        List<String> hand = parseTileList(botParticipacion.getManoActual());
        Map<String, Integer> handCount = buildCountMap(hand);
        String tile = resolveIaTileForHand(extensionTiles.get(0), handCount);

        List<String> target = new ArrayList<>(mesa.get(extendIndex));
        List<String> appended = new ArrayList<>(target);
        appended.add(tile);

        List<String> prepended = new ArrayList<>();
        prepended.add(tile);
        prepended.addAll(target);

        List<String> extended;
        if (isValidRummikubGroup(appended)) {
            extended = appended;
        } else if (isValidRummikubGroup(prepended)) {
            extended = prepended;
        } else {
            drawOneTileIfPossible(partida, botParticipacion);
            advanceTurn(partida, now);
            return;
        }

        List<String> updatedHand = removeTilesFromHand(hand, List.of(tile));
        botParticipacion.setManoActual(serializeTileList(updatedHand));
        botParticipacion.setFichasActuales(updatedHand.size());
        botParticipacion.setTurnosInactivo(0);
        participacionRepository.save(botParticipacion);

        mesa.set(extendIndex, extended);
        partida.setConjuntoMesa(serializeMesaGroups(mesa));

        if (updatedHand.isEmpty()) {
            finishGame(partida, botParticipacion.getJugador().getId());
            return;
        }
        advanceTurn(partida, now);
    }

    private void applyBotReplaceBoard(
            PartidaEntity partida,
            ParticipacionEntity botParticipacion,
            List<List<String>> iaNewBoard,
            LocalDateTime now) {
        if (iaNewBoard == null || iaNewBoard.isEmpty()) {
            drawOneTileIfPossible(partida, botParticipacion);
            advanceTurn(partida, now);
            return;
        }

        List<List<String>> currentBoard = parseMesaGroups(partida.getConjuntoMesa());
        List<String> hand = parseTileList(botParticipacion.getManoActual());
        Map<String, Integer> boardCount = buildCountMap(flattenGroups(currentBoard));
        Map<String, Integer> handCount = buildCountMap(hand);
        List<List<String>> normalized = normalizeIaBoardWithPools(iaNewBoard, boardCount, handCount);

        for (List<String> group : normalized) {
            if (!isValidRummikubGroup(group)) {
                throw new IllegalStateException("La IA devolvio una reorganizacion invalida");
            }
        }

        Map<String, Integer> normalizedCount = buildCountMap(flattenGroups(normalized));
        if (!isSubsetCount(normalizedCount, mergeCounts(boardCount, handCount))) {
            throw new IllegalStateException("La IA uso fichas que no existen en tablero/mano");
        }

        Map<String, Integer> usedFromHand = subtractCountsIgnoringNegatives(normalizedCount, boardCount);
        if (!isSubsetCount(usedFromHand, handCount)) {
            throw new IllegalStateException("La IA requiere fichas no disponibles en mano");
        }

        List<String> tilesToRemove = expandCountMap(usedFromHand);
        List<String> updatedHand = removeTilesFromHand(hand, tilesToRemove);
        botParticipacion.setManoActual(serializeTileList(updatedHand));
        botParticipacion.setFichasActuales(updatedHand.size());
        botParticipacion.setTurnosInactivo(0);
        participacionRepository.save(botParticipacion);

        partida.setConjuntoMesa(serializeMesaGroups(normalized));
        if (updatedHand.isEmpty()) {
            finishGame(partida, botParticipacion.getJugador().getId());
            return;
        }
        advanceTurn(partida, now);
    }

    private List<List<String>> normalizeIaGroupsWithHand(List<List<String>> iaGroups, Map<String, Integer> handCount) {
        List<List<String>> groups = new ArrayList<>();
        for (List<String> group : iaGroups) {
            if (group == null || group.size() < 3) {
                throw new IllegalStateException("La IA devolvio un grupo vacio o demasiado pequeno");
            }
            List<String> normalized = new ArrayList<>();
            for (String iaTile : group) {
                normalized.add(resolveIaTileForHand(iaTile, handCount));
            }
            groups.add(normalized);
        }
        return groups;
    }

    private List<List<String>> normalizeIaBoardWithPools(
            List<List<String>> iaGroups,
            Map<String, Integer> boardCount,
            Map<String, Integer> handCount) {
        Map<String, Integer> available = mergeCounts(boardCount, handCount);
        List<List<String>> groups = new ArrayList<>();
        for (List<String> group : iaGroups) {
            if (group == null || group.size() < 3) {
                throw new IllegalStateException("La IA devolvio un grupo invalido en new_board");
            }
            List<String> normalized = new ArrayList<>();
            for (String iaTile : group) {
                String resolved = resolveIaTileFromPool(iaTile, available);
                normalized.add(resolved);
            }
            groups.add(normalized);
        }
        return groups;
    }

    private String resolveIaTileForHand(String iaTile, Map<String, Integer> handCount) {
        String normalizedIa = normalizeIaTile(iaTile);
        if (JOKER_CANONICAL.equals(normalizedIa)) {
            String consumed = consumeAnyJoker(handCount);
            if (consumed != null) {
                return consumed;
            }
            throw new IllegalStateException("La IA intento usar comodin sin tenerlo en mano");
        }

        String tile = toBackendTile(normalizedIa);
        Integer available = handCount.getOrDefault(tile, 0);
        if (available <= 0) {
            throw new IllegalStateException("La IA intento usar la ficha " + tile + " que no esta en mano");
        }
        handCount.put(tile, available - 1);
        return tile;
    }

    private String resolveIaTileFromPool(String iaTile, Map<String, Integer> available) {
        String normalizedIa = normalizeIaTile(iaTile);
        if (JOKER_CANONICAL.equals(normalizedIa)) {
            String consumed = consumeAnyJoker(available);
            if (consumed != null) {
                return consumed;
            }
            throw new IllegalStateException("La IA uso mas comodines de los disponibles");
        }

        String tile = toBackendTile(normalizedIa);
        Integer count = available.getOrDefault(tile, 0);
        if (count <= 0) {
            throw new IllegalStateException("La IA uso una ficha inexistente: " + tile);
        }
        available.put(tile, count - 1);
        return tile;
    }

    private List<List<String>> toIaBoard(List<List<String>> board) {
        List<List<String>> out = new ArrayList<>();
        for (List<String> group : board) {
            out.add(toIaTiles(group));
        }
        return out;
    }

    private List<String> toIaTiles(List<String> backendTiles) {
        List<String> out = new ArrayList<>();
        for (String tile : backendTiles) {
            out.add(toIaTile(tile));
        }
        return out;
    }

    private String toIaTile(String backendTile) {
        String normalized = normalizeTile(backendTile);
        if (isJoker(normalized)) {
            return JOKER_CANONICAL;
        }
        char color = parseColor(normalized);
        int value = parseValue(normalized);
        return color + String.format("%02d", value);
    }

    private String normalizeIaTile(String iaTile) {
        if (iaTile == null || iaTile.isBlank()) {
            throw new IllegalStateException("La IA devolvio una ficha vacia");
        }
        String t = iaTile.trim().toUpperCase();
        if ("J".equals(t) || JOKER_CANONICAL.equals(t)) {
            return JOKER_CANONICAL;
        }
        if (!t.matches("^[RBOK](0[1-9]|1[0-3])$")) {
            throw new IllegalStateException("Formato de ficha IA invalido: " + iaTile);
        }
        return t;
    }

    private String toBackendTile(String iaTile) {
        if (JOKER_CANONICAL.equals(iaTile) || "J".equals(iaTile)) {
            return JOKER_CANONICAL;
        }
        char color = iaTile.charAt(0);
        int value = Integer.parseInt(iaTile.substring(1));
        return color + String.valueOf(value);
    }

    private List<String> flattenGroups(List<List<String>> groups) {
        List<String> out = new ArrayList<>();
        for (List<String> group : groups) {
            if (group != null) {
                out.addAll(group);
            }
        }
        return out;
    }

    private Map<String, Integer> mergeCounts(Map<String, Integer> left, Map<String, Integer> right) {
        Map<String, Integer> merged = new HashMap<>();
        for (Map.Entry<String, Integer> entry : left.entrySet()) {
            merged.put(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, Integer> entry : right.entrySet()) {
            merged.put(entry.getKey(), merged.getOrDefault(entry.getKey(), 0) + entry.getValue());
        }
        return merged;
    }

    private boolean isSubsetCount(Map<String, Integer> candidate, Map<String, Integer> reference) {
        for (Map.Entry<String, Integer> entry : candidate.entrySet()) {
            if (entry.getValue() > reference.getOrDefault(entry.getKey(), 0)) {
                return false;
            }
        }
        return true;
    }

    private Map<String, Integer> subtractCountsIgnoringNegatives(Map<String, Integer> a, Map<String, Integer> b) {
        Map<String, Integer> out = new HashMap<>();
        for (Map.Entry<String, Integer> entry : a.entrySet()) {
            int diff = entry.getValue() - b.getOrDefault(entry.getKey(), 0);
            if (diff > 0) {
                out.put(entry.getKey(), diff);
            }
        }
        return out;
    }

    private List<String> expandCountMap(Map<String, Integer> map) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            for (int i = 0; i < entry.getValue(); i++) {
                out.add(entry.getKey());
            }
        }
        out.sort(Comparator.naturalOrder());
        return out;
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
        partida.setEventoActual(partida.isModoArcade() ? getRandomArcadeEvent() : "");
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

        bag.add(JOKER_CANONICAL);
        bag.add(JOKER_CANONICAL);
        Collections.shuffle(bag);
        return bag;
    }

    private boolean hasExistingGameState(PartidaEntity partida) {
        List<ParticipacionEntity> participaciones = getOrderedParticipaciones(partida.getIdPartida());
        if (participaciones.isEmpty()) {
            return false;
        }
        for (ParticipacionEntity p : participaciones) {
            if (p.getOrdenTurno() == null) {
                return false;
            }
        }
        return !safe(partida.getBolsa()).isBlank();
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
            ParticipacionEntity timedOut = getParticipacionByTurn(idPartida, baseTurn);
            registerInactiveTurn(timedOut);
            runtime.currentTurn = nextTurn;
            runtime.deadline = now.plusSeconds(TURN_TIMEOUT_SECONDS);

            partida.setTurno(nextTurn);
            partida.setTurnoInicio(now);
            partida.setEventoActual(partida.isModoArcade() ? getRandomArcadeEvent() : "");
            partidaRepository.save(partida);
            runAutomatedBotTurnsIfNeeded(partida, now);
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
        if (partida.getEventoActual() == null) {
            partida.setEventoActual("");
        }
        if (!partida.isModoArcade()) {
            partida.setMercado("");
            partida.setEventoActual("");
        }
    }

    private void validateArcadeModeForMarket(PartidaEntity partida) {
        if (!partida.isModoArcade()) {
            throw new IllegalStateException("El mercado solo esta disponible en modo arcade");
        }
    }

    private String getRandomArcadeEvent() {
        int index = ThreadLocalRandom.current().nextInt(ARCADE_EVENTS.size());
        return ARCADE_EVENTS.get(index);
    }

    private void registerInactiveTurn(ParticipacionEntity participacion) {
        if (participacion == null || isBotPlayer(participacion)) {
            return;
        }
        int nextValue = Math.min(participacion.getTurnosInactivo() + 1, INACTIVITY_LIMIT_TURNS);
        participacion.setTurnosInactivo(nextValue);
        participacionRepository.save(participacion);
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
                tiles.add(normalizeTileTokenForStorage(tile));
            }
        }
        return tiles;
    }

    private List<String> parsePurchasedObjectCodes(String encoded) {
        List<String> codes = new ArrayList<>();
        if (encoded == null || encoded.isBlank()) {
            return codes;
        }

        String[] parts = encoded.split(",");
        for (String rawPart : parts) {
            String token = rawPart == null ? "" : rawPart.trim();
            if (token.isEmpty()) {
                continue;
            }

            int pipeIndex = token.indexOf('|');
            if (pipeIndex > 0) {
                token = token.substring(0, pipeIndex).trim();
            }
            int colonIndex = token.indexOf(':');
            if (colonIndex > 0) {
                token = token.substring(0, colonIndex).trim();
            }

            Matcher matcher = MARKET_OBJECT_PATTERN.matcher(token);
            if (matcher.matches()) {
                codes.add(matcher.group(1).toLowerCase(Locale.ROOT));
            }
        }
        return codes;
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

    private String normalizeMarketObjectCode(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            throw new IllegalArgumentException("Codigo de objeto vacio");
        }
        String candidate = rawCode.trim().toLowerCase(Locale.ROOT);
        Matcher matcher = MARKET_OBJECT_PATTERN.matcher(candidate);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Objeto de mercado invalido: " + rawCode);
        }
        return matcher.group(1).toLowerCase(Locale.ROOT);
    }

    private List<String> createRandomMarketObjectCodes() {
        List<String> randomCodes = new ArrayList<>(MARKET_OBJECT_CODES);
        Collections.shuffle(randomCodes);
        return new ArrayList<>(randomCodes.subList(0, MARKET_OBJECTS_PER_PLAYER));
    }

    private LinkedHashMap<String, Integer> createRandomMarketStock() {
        LinkedHashMap<String, Integer> stockByCode = new LinkedHashMap<>();
        for (String code : createRandomMarketObjectCodes()) {
            stockByCode.put(code, MARKET_ITEM_STOCK);
        }
        return stockByCode;
    }

    private LinkedHashMap<String, Integer> normalizeMarketStock(Map<String, Integer> rawStockByCode, boolean fillMissing) {
        LinkedHashMap<String, Integer> normalized = new LinkedHashMap<>();
        if (rawStockByCode != null) {
            for (Map.Entry<String, Integer> entry : rawStockByCode.entrySet()) {
                String codeRaw = entry.getKey();
                if (codeRaw == null || codeRaw.isBlank()) {
                    continue;
                }

                String code;
                try {
                    code = normalizeMarketObjectCode(codeRaw);
                } catch (IllegalArgumentException ex) {
                    continue;
                }

                if (normalized.containsKey(code)) {
                    continue;
                }
                int units = entry.getValue() == null ? 0 : Math.max(entry.getValue(), 0);
                normalized.put(code, units);
                if (normalized.size() == MARKET_OBJECTS_PER_PLAYER) {
                    break;
                }
            }
        }

        if (fillMissing && normalized.size() < MARKET_OBJECTS_PER_PLAYER) {
            List<String> remainingCodes = new ArrayList<>(MARKET_OBJECT_CODES);
            remainingCodes.removeAll(normalized.keySet());
            Collections.shuffle(remainingCodes);

            int missing = MARKET_OBJECTS_PER_PLAYER - normalized.size();
            for (int i = 0; i < missing; i++) {
                normalized.put(remainingCodes.get(i), MARKET_ITEM_STOCK);
            }
        }
        return normalized;
    }

    private Map<Integer, LinkedHashMap<String, Integer>> parsePartidaMarket(String encoded) {
        Map<Integer, LinkedHashMap<String, Integer>> marketByPlayer = new HashMap<>();
        if (encoded == null || encoded.isBlank()) {
            return marketByPlayer;
        }

        String[] playerSegments = encoded.split(";");
        for (String rawSegment : playerSegments) {
            String segment = rawSegment == null ? "" : rawSegment.trim();
            if (segment.isEmpty()) {
                continue;
            }

            int eqIndex = segment.indexOf('=');
            if (eqIndex <= 0) {
                continue;
            }

            String playerRaw = segment.substring(0, eqIndex).trim();
            Integer playerId;
            try {
                playerId = Integer.parseInt(playerRaw);
            } catch (NumberFormatException ex) {
                continue;
            }

            LinkedHashMap<String, Integer> stockByCode = new LinkedHashMap<>();
            String stockRaw = segment.substring(eqIndex + 1).trim();
            if (!stockRaw.isEmpty()) {
                String[] stockEntries = stockRaw.split(",");
                for (String rawEntry : stockEntries) {
                    String entry = rawEntry == null ? "" : rawEntry.trim();
                    if (entry.isEmpty()) {
                        continue;
                    }

                    String[] parts = entry.split("\\|", -1);
                    String codeRaw = parts[0].trim();

                    String code;
                    try {
                        code = normalizeMarketObjectCode(codeRaw);
                    } catch (IllegalArgumentException ex) {
                        continue;
                    }

                    int units = MARKET_ITEM_STOCK;
                    if (parts.length > 1) {
                        try {
                            units = Math.max(Integer.parseInt(parts[1].trim()), 0);
                        } catch (NumberFormatException ex) {
                            units = MARKET_ITEM_STOCK;
                        }
                    }

                    if (!stockByCode.containsKey(code)) {
                        stockByCode.put(code, units);
                    }
                    if (stockByCode.size() == MARKET_OBJECTS_PER_PLAYER) {
                        break;
                    }
                }
            }
            marketByPlayer.put(playerId, stockByCode);
        }

        return marketByPlayer;
    }

    private String serializePartidaMarket(Map<Integer, LinkedHashMap<String, Integer>> marketByPlayer) {
        if (marketByPlayer == null || marketByPlayer.isEmpty()) {
            return "";
        }

        List<Integer> playerIds = new ArrayList<>(marketByPlayer.keySet());
        Collections.sort(playerIds);
        List<String> encodedPlayers = new ArrayList<>();
        for (Integer playerId : playerIds) {
            Map<String, Integer> stockByCode = marketByPlayer.get(playerId);
            if (stockByCode == null || stockByCode.isEmpty()) {
                continue;
            }

            List<String> encodedStock = new ArrayList<>();
            for (Map.Entry<String, Integer> entry : stockByCode.entrySet()) {
                String code = entry.getKey();
                if (!MARKET_OBJECT_VALUES.containsKey(code)) {
                    continue;
                }
                int units = entry.getValue() == null ? 0 : Math.max(entry.getValue(), 0);
                encodedStock.add(code + "|" + units);
            }

            if (!encodedStock.isEmpty()) {
                encodedPlayers.add(playerId + "=" + String.join(",", encodedStock));
            }
        }
        return String.join(";", encodedPlayers);
    }

    private LinkedHashMap<String, Integer> getOrCreateMarketStockByPlayer(PartidaEntity partida, Integer idJugador) {
        Map<Integer, LinkedHashMap<String, Integer>> marketByPlayer = parsePartidaMarket(partida.getMercado());
        LinkedHashMap<String, Integer> currentStock = marketByPlayer.get(idJugador);

        LinkedHashMap<String, Integer> normalizedStock;
        if (currentStock == null || currentStock.isEmpty()) {
            normalizedStock = createRandomMarketStock();
        } else {
            normalizedStock = normalizeMarketStock(currentStock, true);
        }

        marketByPlayer.put(idJugador, normalizedStock);
        String normalizedEncoded = serializePartidaMarket(marketByPlayer);
        if (!normalizedEncoded.equals(safe(partida.getMercado()))) {
            partida.setMercado(normalizedEncoded);
            partidaRepository.save(partida);
        }

        return normalizedStock;
    }

    private void updateMarketStockByPlayer(PartidaEntity partida, Integer idJugador, Map<String, Integer> updatedStockByCode) {
        Map<Integer, LinkedHashMap<String, Integer>> marketByPlayer = parsePartidaMarket(partida.getMercado());
        marketByPlayer.put(idJugador, normalizeMarketStock(updatedStockByCode, false));
        String encoded = serializePartidaMarket(marketByPlayer);
        if (!encoded.equals(safe(partida.getMercado()))) {
            partida.setMercado(encoded);
            partidaRepository.save(partida);
        }
    }

    private String serializePurchasedObjectCodes(List<String> purchasedCodes) {
        if (purchasedCodes == null || purchasedCodes.isEmpty()) {
            return "";
        }

        List<String> encoded = new ArrayList<>();
        for (String codeRaw : purchasedCodes) {
            if (codeRaw == null || codeRaw.isBlank()) {
                continue;
            }
            try {
                encoded.add(normalizeMarketObjectCode(codeRaw));
            } catch (IllegalArgumentException ex) {
                // Ignoramos codigos antiguos/no validos en datos legacy.
            }
        }
        return String.join(",", encoded);
    }

    private MercadoParticipacionDTO buildMercadoParticipacionDTO(
            Integer idPartida,
            Integer idJugador,
            int monedasJugador,
            Map<String, Integer> stockByCode,
            List<String> habilidadesCompradas) {
        return MercadoParticipacionDTO.builder()
                .idPartida(idPartida)
                .idJugador(idJugador)
                .monedasJugador(monedasJugador)
                .objetosMercado(toMercadoItems(stockByCode))
                .habilidadesCompradas(new ArrayList<>(habilidadesCompradas))
                .build();
    }

    private List<MercadoItemDTO> toMercadoItems(Map<String, Integer> stockByCode) {
        List<MercadoItemDTO> items = new ArrayList<>();
        if (stockByCode == null || stockByCode.isEmpty()) {
            return items;
        }

        for (Map.Entry<String, Integer> entry : stockByCode.entrySet()) {
            String code = entry.getKey();
            Integer value = MARKET_OBJECT_VALUES.get(code);
            int units = entry.getValue() == null ? 0 : Math.max(entry.getValue(), 0);
            if (value == null || units <= 0) {
                continue;
            }

            items.add(MercadoItemDTO.builder()
                    .codigo(code)
                    .valor(value)
                    .unidadesDisponibles(units)
                    .build());
        }
        return items;
    }

    private String normalizeTile(String tileRaw) {
        if (tileRaw == null || tileRaw.isBlank()) {
            throw new IllegalArgumentException("Ficha vacia en jugada");
        }
        String tile = tileRaw.trim().toUpperCase();
        if ("J".equals(tile) || JOKER_CANONICAL.equals(tile) || "J1".equals(tile) || "J2".equals(tile)) {
            return JOKER_CANONICAL;
        }

        Matcher matcher = TILE_PATTERN.matcher(tile);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Formato de ficha invalido: " + tileRaw);
        }
        return tile;
    }

    private boolean isJoker(String tile) {
        if (tile == null || tile.isBlank()) {
            return false;
        }
        String normalized = tile.trim().toUpperCase();
        return JOKER_CANONICAL.equals(normalized) || "J1".equals(normalized) || "J2".equals(normalized) || "J".equals(normalized);
    }

    private String consumeAnyJoker(Map<String, Integer> countMap) {
        List<String> jokerKeys = List.of(JOKER_CANONICAL, "J1", "J2", "J");
        for (String key : jokerKeys) {
            int current = countMap.getOrDefault(key, 0);
            if (current > 0) {
                countMap.put(key, current - 1);
                return key;
            }
        }
        return null;
    }

    private String normalizeTileTokenForStorage(String rawToken) {
        String token = rawToken == null ? "" : rawToken.trim();
        if (token.isEmpty()) {
            return "";
        }
        if (isJoker(token)) {
            return JOKER_CANONICAL;
        }
        return token.toUpperCase(Locale.ROOT);
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
