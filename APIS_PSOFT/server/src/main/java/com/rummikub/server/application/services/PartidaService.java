package com.rummikub.server.application.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rummikub.server.api.dto.PartidaDTO;
import com.rummikub.server.api.dto.ParticipacionDTO;
import com.rummikub.server.api.dto.bot.BotItemUseDTO;
import com.rummikub.server.api.dto.bot.BotMoveDTO;
import com.rummikub.server.api.dto.bot.BotMoveRequest;
import com.rummikub.server.api.dto.bot.BotMoveResponse;
import com.rummikub.server.api.dto.partida.MatchmakingResponse;
import com.rummikub.server.api.dto.partida.MercadoItemDTO;
import com.rummikub.server.api.dto.partida.MercadoParticipacionDTO;
import com.rummikub.server.api.dto.partida.UsarObjetoMercadoResponse;
import com.rummikub.server.infraestructure.jpa.entity.JugadorEntity;
import com.rummikub.server.infraestructure.jpa.entity.ParticipacionEntity;
import com.rummikub.server.infraestructure.jpa.entity.ParticipacionId;
import com.rummikub.server.infraestructure.jpa.entity.PartidaEntity;
import com.rummikub.server.infraestructure.jpa.mapper.Mapper;
import com.rummikub.server.infraestructure.jpa.repository.JugadorRepository;
import com.rummikub.server.infraestructure.jpa.repository.ParticipacionRepository;
import com.rummikub.server.infraestructure.jpa.repository.PartidaRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.data.domain.PageRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PartidaService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PartidaService.class);

    private static final int MAX_TURN_SLOTS = 4;
    private static final int INITIAL_HAND_SIZE = 14;
    private static final int TURN_TIMEOUT_SECONDS = 60;
    private static final int LOBBY_TIMEOUT_SECONDS = 50;
    private static final String BOT_NAME_PREFIX = "BOT_";
    private static final int BOT_LEVEL = 5;
    private static final double BOT_RANDOMNESS = 0.20;
    private static final int MAX_AUTOMATED_BOT_TURNS = 16;
    private static final int MAX_BOT_ARCADE_ITEM_PHASES = 4;
    private static final int MARKET_OBJECTS_PER_PLAYER = 3;
    private static final int MARKET_ITEM_STOCK = 1;
    private static final int SWAP_ON_FAIL_VISIBLE_TILES = 3;
    private static final int INACTIVITY_LIMIT_TURNS = 2;
    private static final String JOKER_CANONICAL = "J*";
    private static final int ARCADE_GOLD_DUPLICATES_PER_VALUE = 1;
    private static final double ARCADE_RAINBOW_DRAW_CHANCE = 0.20;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String ESTADO_WAITING = "WAITING";
    private static final String ESTADO_RUNNING = "RUNNING";
    private static final String ESTADO_PAUSED = "PAUSED";
    private static final String ESTADO_FINISHED = "FINISHED";

    private static final Pattern TILE_PATTERN = Pattern.compile("^([RBOK])(1[0-3]|0?[1-9])([AD]?)$");
    private static final Pattern TILE_PREFIX_PATTERN = Pattern.compile("^([AD])([RBOK])(1[0-3]|0?[1-9])$");
    private static final Pattern MARKET_OBJECT_PATTERN = Pattern.compile(
            "^(GUARDIAN_ANGEL|CRYSTAL_BALL|MIDAS_TOUCH|PLUS_FOUR|SWAP_ON_FAIL|WHITE_GLOVE|SMOKE_BOMB|CHILI_PEPPER|GLASS_CEILING)$",
            Pattern.CASE_INSENSITIVE
    );
    private static final List<String> MARKET_OBJECT_CODES = List.of(
            "GUARDIAN_ANGEL",
            "CRYSTAL_BALL",
            "MIDAS_TOUCH",
            "PLUS_FOUR",
            "SWAP_ON_FAIL",
            "WHITE_GLOVE",
            "SMOKE_BOMB",
            "CHILI_PEPPER",
            "GLASS_CEILING"
    );
    private static final Map<String, Integer> MARKET_OBJECT_VALUES = Map.of(
            "GUARDIAN_ANGEL", 6,
            "CRYSTAL_BALL", 6,
            "MIDAS_TOUCH", 3,
            "PLUS_FOUR", 6,
            "SWAP_ON_FAIL", 6,
            "WHITE_GLOVE", 6,
            "SMOKE_BOMB", 6,
            "CHILI_PEPPER", 6,
            "GLASS_CEILING", 6
    );
    private static final Set<String> IA_SUPPORTED_MARKET_OBJECTS = Set.of(
            "GUARDIAN_ANGEL",
            "CRYSTAL_BALL",
            "MIDAS_TOUCH",
            "PLUS_FOUR",
            "SWAP_ON_FAIL",
            "WHITE_GLOVE",
            "SMOKE_BOMB",
            "CHILI_PEPPER",
            "GLASS_CEILING"
    );
    private static final String EFFECT_PREFIX = "FX:";
    private static final List<String> ARCADE_BASE_EVENTS = List.of(
            "+pieza",
            "50porcien"
    );
    private static final List<String> ARCADE_PROHIBITED_COLORS = List.of(
            "rojo",
            "azul",
            "naranja",
            "negro"
    );

    private final PartidaRepository partidaRepository;
    private final ParticipacionRepository participacionRepository;
    private final JugadorRepository jugadorRepository;
    private final BotIntegrationService botIntegrationService;
    private final TransactionTemplate transactionTemplate;
    private final Map<Integer, TurnRuntime> turnRuntimeByPartida = new ConcurrentHashMap<>();
    private final Map<Integer, Object> turnMutexByPartida = new ConcurrentHashMap<>();
    private final Set<Integer> botTurnJobsInProgress = ConcurrentHashMap.newKeySet();
    private final ExecutorService botTurnExecutor = Executors.newCachedThreadPool();
    private final Object matchmakingMutex = new Object();

    public PartidaService(
            PartidaRepository partidaRepository,
            ParticipacionRepository participacionRepository,
            JugadorRepository jugadorRepository,
            BotIntegrationService botIntegrationService,
            TransactionTemplate transactionTemplate) {
        this.partidaRepository = partidaRepository;
        this.participacionRepository = participacionRepository;
        this.jugadorRepository = jugadorRepository;
        this.botIntegrationService = botIntegrationService;
        this.transactionTemplate = transactionTemplate;
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

    @PreDestroy
    public void shutdownBotTurnExecutor() {
        botTurnExecutor.shutdownNow();
    }

    public List<PartidaDTO> getAll() {
        List<PartidaDTO> partidas = partidaRepository.findAllSummaries().stream()
                .map(this::toSummaryDTO)
                .toList();
        return attachFichasPorJugador(partidas);
    }

    public List<PartidaDTO> getByUsuario(Integer usuarioId) {
        if (usuarioId == null) {
            return List.of();
        }
        List<PartidaDTO> partidas = partidaRepository.findSummariesByUsuarioId(usuarioId).stream()
                .map(this::toSummaryDTO)
                .toList();
        return attachFichasPorJugador(partidas);
    }

    public List<PartidaDTO> getOpenPublicGames(Boolean modoArcade) {
        boolean arcade = Boolean.TRUE.equals(modoArcade);
        return partidaRepository
                .findTop20ByModoArcadeAndPrivadaFalseAndEstadoAndCorriendoFalseOrderByIdPartidaDesc(arcade, ESTADO_WAITING)
                .stream()
                .map(this::toMatchmakingDTO)
                .toList();
    }

    @Transactional
    public MatchmakingResponse matchmaking(Integer idJugador, boolean modoArcade) {
        if (idJugador == null) {
            throw new IllegalArgumentException("idJugador es obligatorio");
        }

        synchronized (matchmakingMutex) {
            ParticipacionEntity existingWaiting = findExistingWaitingPublicParticipation(idJugador, modoArcade);
            if (existingWaiting != null) {
                return MatchmakingResponse.builder()
                        .creadaNuevaPartida(false)
                        .partida(toPartidaDTO(existingWaiting.getPartida()))
                        .participacion(Mapper.toDTO(existingWaiting))
                        .build();
            }

            PartidaEntity target = findJoinablePublicGame(modoArcade);
            boolean created = false;
            if (target == null) {
                target = createPublicWaitingGame(modoArcade);
                created = true;
            }

            ParticipacionEntity participacion = createOrReuseParticipation(idJugador, target);
            PartidaDTO partidaActualizada = autoStartIfPublicFull(target.getIdPartida());
            return MatchmakingResponse.builder()
                    .creadaNuevaPartida(created)
                    .partida(partidaActualizada)
                    .participacion(Mapper.toDTO(participacion))
                    .build();
        }
    }

    @Transactional
    public PartidaDTO autoStartIfPublicFull(Integer idPartida) {
        synchronized (getTurnMutex(idPartida)) {
            PartidaEntity partida = partidaRepository.findById(idPartida)
                    .orElseThrow(() -> new NoSuchElementException("Partida no encontrada: " + idPartida));
            ensureDefaultState(partida);

            if (partida.isPrivada() || partida.isCorriendo() || !ESTADO_WAITING.equalsIgnoreCase(safe(partida.getEstado()))) {
                return toPartidaDTO(partida);
            }

            int playerCount = participacionRepository.findByPartida_IdPartida(idPartida).size();
            if (playerCount < MAX_TURN_SLOTS) {
                return toPartidaDTO(partida);
            }

            return iniciar(idPartida);
        }
    }

    @Transactional
    public PartidaDTO getById(Integer idPartida) {
        PartidaEntity partida = partidaRepository.findById(idPartida)
                .orElseThrow(() -> new NoSuchElementException("Partida no encontrada: " + idPartida));
        ensureDefaultState(partida);
        return toPartidaDTO(partida);
    }

    @Transactional
    public MercadoParticipacionDTO getMercadoJugador(Integer idPartida, Integer idJugador) {
        PartidaEntity partida = partidaRepository.findById(idPartida)
                .orElseThrow(() -> new NoSuchElementException("Partida no encontrada: " + idPartida));
        ensureDefaultState(partida);

        ParticipacionEntity participacion = mustGetParticipacion(idPartida, idJugador);
        validateArcadeModeForMarket(partida);
        List<String> habilidadesCompradas = parsePurchasedObjectCodes(participacion.getHabilidadesActuales());
        List<String> efectosActivos = parseActiveEffectCodes(participacion.getHabilidadesActuales());
        String serializedHabilidades = serializeHabilidadesState(habilidadesCompradas, efectosActivos);
        if (!serializedHabilidades.equals(safe(participacion.getHabilidadesActuales()))) {
            participacion.setHabilidadesActuales(serializedHabilidades);
            participacionRepository.save(participacion);
        }

        LinkedHashMap<String, Integer> stockByCode = getOrCreateMarketStockByPlayer(partida, idJugador);
        return buildMercadoParticipacionDTO(idPartida, idJugador, participacion.getJugador().getMonedas(), stockByCode,
                habilidadesCompradas, efectosActivos);
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
        List<String> efectosActivos = parseActiveEffectCodes(participacion.getHabilidadesActuales());
        habilidadesCompradas.add(codigoObjeto);
        participacion.setHabilidadesActuales(serializeHabilidadesState(habilidadesCompradas, efectosActivos));
        participacionRepository.save(participacion);

        return buildMercadoParticipacionDTO(
                idPartida,
                idJugador,
                jugador.getMonedas(),
                stockByCode,
                habilidadesCompradas,
                efectosActivos
        );
    }

    @Transactional
    public UsarObjetoMercadoResponse usarObjetoMercado(
            Integer idPartida,
            Integer idJugador,
            String codigoObjetoRaw,
            Integer idJugadorObjetivo,
            String codigoObjetoObjetivoRaw,
            String fichaPropiaRaw,
            String fichaObjetivoRaw) {
        PartidaEntity partida = partidaRepository.findById(idPartida)
                .orElseThrow(() -> new NoSuchElementException("Partida no encontrada: " + idPartida));
        ensureDefaultState(partida);
        validateArcadeModeForMarket(partida);

        ParticipacionEntity actor = mustGetParticipacion(idPartida, idJugador);
        String codigoObjeto = normalizeMarketObjectCode(codigoObjetoRaw);

        List<String> actorInventario = parsePurchasedObjectCodes(actor.getHabilidadesActuales());
        List<String> actorEfectos = parseActiveEffectCodes(actor.getHabilidadesActuales());
        if (!actorInventario.remove(codigoObjeto)) {
            throw new IllegalStateException("No tienes el objeto " + codigoObjeto + " en tu inventario");
        }

        ParticipacionEntity objetivo = null;
        List<String> objetivoInventario = List.of();
        List<String> objetivoEfectos = List.of();
        if (requiresTarget(codigoObjeto)) {
            if (idJugadorObjetivo == null) {
                throw new IllegalArgumentException("idJugadorObjetivo es obligatorio para " + codigoObjeto);
            }
            if (idJugador.equals(idJugadorObjetivo)) {
                throw new IllegalArgumentException("No puedes usar " + codigoObjeto + " sobre ti mismo");
            }
            objetivo = mustGetParticipacion(idPartida, idJugadorObjetivo);
            objetivoInventario = new ArrayList<>(parsePurchasedObjectCodes(objetivo.getHabilidadesActuales()));
            objetivoEfectos = new ArrayList<>(parseActiveEffectCodes(objetivo.getHabilidadesActuales()));
        }

        List<String> fichasObjetivoVisibles = List.of();
        List<String> habilidadesObjetivoVisibles = null;
        String mensaje;

        switch (codigoObjeto) {
            case "GUARDIAN_ANGEL" -> {
                addActiveEffect(actorEfectos, "GUARDIAN_ANGEL");
                mensaje = "GUARDIAN_ANGEL activado sobre tu jugador";
            }
            case "CRYSTAL_BALL" -> {
                fichasObjetivoVisibles = parseTileList(objetivo.getManoActual());
                habilidadesObjetivoVisibles = new ArrayList<>(objetivoInventario);
                mensaje = "CRYSTAL_BALL usada correctamente";
            }
            case "MIDAS_TOUCH" -> {
                applyMidasTouch(actor);
                mensaje = "MIDAS_TOUCH aplicada sobre tu mano";
            }
            case "PLUS_FOUR" -> {
                addActiveEffect(objetivoEfectos, "PLUS_FOUR");
                mensaje = "PLUS_FOUR marcada sobre el jugador objetivo";
            }
            case "SWAP_ON_FAIL" -> {
                List<String> preview = getSwapOnFailPreview(objetivo);
                if (fichaPropiaRaw == null || fichaPropiaRaw.isBlank() || fichaObjetivoRaw == null || fichaObjetivoRaw.isBlank()) {
                    actorInventario.add(codigoObjeto);
                    return buildUsarObjetoResponse(
                            partida,
                            actor,
                            codigoObjeto,
                            idJugadorObjetivo,
                            false,
                            false,
                            "Elige una ficha tuya y una de las visibles del objetivo para completar el intercambio",
                            preview,
                            null,
                            objetivoInventario,
                            objetivoEfectos
                    );
                }
                applySwapOnFail(actor, objetivo, preview, fichaPropiaRaw, fichaObjetivoRaw);
                fichasObjetivoVisibles = preview;
                mensaje = "SWAP_ON_FAIL aplicada correctamente";
            }
            case "WHITE_GLOVE" -> {
                if (objetivoInventario.isEmpty()) {
                    mensaje = "WHITE_GLOVE no encontro objetos que robar";
                } else {
                    int randomIndex = ThreadLocalRandom.current().nextInt(objetivoInventario.size());
                    String robbed = objetivoInventario.remove(randomIndex);
                    actorInventario.add(robbed);
                    habilidadesObjetivoVisibles = List.of(robbed);
                    mensaje = "WHITE_GLOVE robo el objeto " + robbed;
                }
            }
            case "SMOKE_BOMB" -> {
                addActiveEffect(objetivoEfectos, "SMOKE_BOMB");
                mensaje = "SMOKE_BOMB marcada sobre el jugador objetivo";
            }
            case "CHILI_PEPPER" -> {
                addActiveEffect(objetivoEfectos, "CHILI_PEPPER");
                mensaje = "CHILI_PEPPER marcada sobre el jugador objetivo";
            }
            case "GLASS_CEILING" -> {
                addActiveEffect(objetivoEfectos, "GLASS_CEILING");
                mensaje = "GLASS_CEILING marcada sobre el jugador objetivo";
            }
            default -> throw new IllegalArgumentException("Objeto no soportado para uso: " + codigoObjeto);
        }

        actor.setHabilidadesActuales(serializeHabilidadesState(actorInventario, actorEfectos));
        participacionRepository.save(actor);

        if (objetivo != null) {
            objetivo.setHabilidadesActuales(serializeHabilidadesState(objetivoInventario, objetivoEfectos));
            participacionRepository.save(objetivo);
        }
        partidaRepository.save(partida);

        return buildUsarObjetoResponse(
                partida,
                actor,
                codigoObjeto,
                idJugadorObjetivo,
                true,
                false,
                mensaje,
                fichasObjetivoVisibles,
                habilidadesObjetivoVisibles,
                objetivoInventario,
                objetivoEfectos
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
        partida.setPrivada(Boolean.TRUE.equals(dto.getPrivada()));
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

        return toPartidaDTO(partida);
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
        if (dto.getPrivada() != null) {
            partida.setPrivada(dto.getPrivada());
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
            synchronized (getTurnMutex(idPartida)) {
                TurnRuntime runtime = createRuntimeFromDatabase(partida, LocalDateTime.now());
                if (runtime != null) {
                    turnRuntimeByPartida.put(partida.getIdPartida(), runtime);
                }
            }
        }

        return toPartidaDTO(partidaRepository.save(partida));
    }

    @Transactional
    public PartidaDTO iniciar(Integer idPartida) {
        synchronized (getTurnMutex(idPartida)) {
            PartidaEntity partida = partidaRepository.findById(idPartida)
                    .orElseThrow(() -> new NoSuchElementException("Partida no encontrada: " + idPartida));

            ensureDefaultState(partida);

            if (ESTADO_FINISHED.equals(partida.getEstado())) {
                throw new IllegalStateException("La partida ya finalizo");
            }
            if (partida.isCorriendo() || ESTADO_RUNNING.equals(partida.getEstado())) {
                return toPartidaDTO(partida);
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
            return toPartidaDTO(partida);
        }
    }

    @Transactional
    public PartidaDTO pausarPartida(Integer idPartida, Integer idJugadorSolicitante) {
        synchronized (getTurnMutex(idPartida)) {
            PartidaEntity partida = mustGetRunningPartida(idPartida);
            mustGetParticipacion(idPartida, idJugadorSolicitante);

            partida.setCorriendo(false);
            partida.setEstado(ESTADO_PAUSED);
            partida.setTurnoInicio(null);
            turnRuntimeByPartida.remove(idPartida);

            return toPartidaDTO(partidaRepository.save(partida));
        }
    }

    @Transactional
    public PartidaDTO reanudarPartida(Integer idPartida, Integer idJugadorSolicitante) {
        synchronized (getTurnMutex(idPartida)) {
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
            triggerAutomatedBotTurnsAsync(idPartida);
            return toPartidaDTO(partida);
        }
    }

    @Transactional
    public PartidaDTO siguienteTurno(Integer idPartida, Integer idJugador) {
        return pasarTurno(idPartida, idJugador);
    }

    @Transactional
    public PartidaDTO pasarTurno(Integer idPartida, Integer idJugador) {
        synchronized (getTurnMutex(idPartida)) {
            PartidaEntity partida = mustGetRunningPartida(idPartida);
            ParticipacionEntity participacion = mustGetParticipacion(idPartida, idJugador);
            validatePlayerTurn(partida, participacion);
            resetPlayerInactivity(participacion);
            PartidaDTO updated = advanceTurn(partida, LocalDateTime.now());
            triggerAutomatedBotTurnsAsync(idPartida);
            return attachFichasPorJugador(updated);
        }
    }

    @Transactional
    public PartidaDTO robarFicha(Integer idPartida, Integer idJugador) {
        synchronized (getTurnMutex(idPartida)) {
            PartidaEntity partida = mustGetRunningPartida(idPartida);
            ParticipacionEntity participacion = mustGetParticipacion(idPartida, idJugador);
            validatePlayerTurn(partida, participacion);

            List<String> bag = parseTileList(partida.getBolsa());
            if (bag.isEmpty()) {
                throw new IllegalStateException("No quedan fichas en la bolsa");
            }

            String drawnTile = applyArcadeRainbowOnDraw(partida, bag.remove(0));
            List<String> hand = parseTileList(participacion.getManoActual());
            hand.add(drawnTile);

            participacion.setManoActual(serializeTileList(hand));
            participacion.setFichasActuales(hand.size());
            participacion.setTurnosInactivo(0);
            participacionRepository.save(participacion);

            partida.setBolsa(serializeTileList(bag));
            PartidaDTO result = advanceTurn(partida, LocalDateTime.now());
            triggerAutomatedBotTurnsAsync(idPartida);
            result.setFichaRobada(drawnTile);
            result.setFichasRobadas(List.of(drawnTile));
            return result;
        }
    }

    @Transactional
    public PartidaDTO robarSinPasarTurno(Integer idPartida, Integer idJugador, Integer cantidadRobar) {
        synchronized (getTurnMutex(idPartida)) {
            PartidaEntity partida = mustGetRunningPartida(idPartida);
            ParticipacionEntity participacion = mustGetParticipacion(idPartida, idJugador);
            validatePlayerTurn(partida, participacion);

            List<String> bag = parseTileList(partida.getBolsa());
            if (bag.isEmpty()) {
                throw new IllegalStateException("No quedan fichas en la bolsa");
            }

            int requested = (cantidadRobar == null) ? 1 : cantidadRobar;
            int drawCount = Math.min(requested, bag.size());
            List<String> drawnTiles = new ArrayList<>(drawCount);
            for (int i = 0; i < drawCount; i++) {
                drawnTiles.add(applyArcadeRainbowOnDraw(partida, bag.remove(0)));
            }
            List<String> hand = parseTileList(participacion.getManoActual());
            hand.addAll(drawnTiles);

            participacion.setManoActual(serializeTileList(hand));
            participacion.setFichasActuales(hand.size());
            participacion.setTurnosInactivo(0);
            participacionRepository.save(participacion);

            partida.setBolsa(serializeTileList(bag));
            PartidaDTO result = toPartidaDTO(partidaRepository.save(partida));
            result.setFichaRobada(drawnTiles.get(0));
            result.setFichasRobadas(drawnTiles);
            return result;
        }
    }

    @Transactional
    public PartidaDTO jugarGrupos(Integer idPartida, Integer idJugador, List<List<String>> grupos) {
        synchronized (getTurnMutex(idPartida)) {
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
            enforceGlassCeilingIfNeeded(participacion, playedTiles);
            participacion.setManoActual(serializeTileList(updatedHand));
            participacion.setFichasActuales(updatedHand.size());
            awardObjectsForRainbowTilesRemovedFromHand(participacion, playedTiles);
            participacion.setTurnosInactivo(0);
            participacionRepository.save(participacion);

            List<List<String>> mesa = parseMesaGroups(partida.getConjuntoMesa());
            mesa.addAll(normalizedGroups);
            partida.setConjuntoMesa(serializeMesaGroups(mesa));

            if (updatedHand.isEmpty()) {
                finishGame(partida, idJugador);
                return toPartidaDTO(partidaRepository.save(partida));
            }

            PartidaDTO updated = advanceTurn(partida, LocalDateTime.now());
            triggerAutomatedBotTurnsAsync(idPartida);
            return updated;
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
        synchronized (getTurnMutex(idPartida)) {
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
        synchronized (getTurnMutex(idPartida)) {
            PartidaEntity partida = partidaRepository.findById(idPartida)
                    .orElseThrow(() -> new NoSuchElementException("Partida no encontrada: " + idPartida));
            ensureDefaultState(partida);

            ParticipacionEntity participacion = mustGetParticipacion(idPartida, idJugador);
            if (!partida.isCorriendo() || !ESTADO_RUNNING.equals(partida.getEstado())) {
                participacionRepository.delete(participacion);
                return toPartidaDTO(partida);
            }
            replaceParticipationWithBot(partida, participacion);

            LocalDateTime now = LocalDateTime.now();
            TurnRuntime runtime = createRuntimeFromDatabase(partida, now);
            if (runtime != null) {
                turnRuntimeByPartida.put(partida.getIdPartida(), runtime);
            }

            triggerAutomatedBotTurnsAsync(idPartida);
            return toPartidaDTO(partida);
        }
    }

    @Transactional
    public PartidaDTO finalizarPartida(Integer idPartida, Integer idJugadorSolicitante) {
        synchronized (getTurnMutex(idPartida)) {
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
            return toPartidaDTO(partidaRepository.save(partida));
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
        processExpiredPublicLobbies(now);
    }

    private void processExpiredPublicLobbies(LocalDateTime now) {
        List<PartidaEntity> expiredLobbies = partidaRepository.findExpiredPublicLobbies(
                ESTADO_WAITING, now.minusSeconds(LOBBY_TIMEOUT_SECONDS));

        for (PartidaEntity partida : expiredLobbies) {
            try {
                synchronized (getTurnMutex(partida.getIdPartida())) {
                    // Re-leer con lock para evitar condiciones de carrera
                    PartidaEntity fresh = partidaRepository.findById(partida.getIdPartida()).orElse(null);
                    if (fresh == null
                            || fresh.isPrivada()
                            || fresh.isCorriendo()
                            || !ESTADO_WAITING.equalsIgnoreCase(safe(fresh.getEstado()))) {
                        continue;
                    }
                    int playerCount = participacionRepository
                            .findByPartida_IdPartida(fresh.getIdPartida()).size();
                    if (playerCount > 0) {
                        // Hay al menos 1 jugador humano: rellenar huecos con bots e iniciar
                        LOGGER.info("Lobby publico {} expiro con {}/{} jugadores. Rellenando con bots.",
                                fresh.getIdPartida(), playerCount, MAX_TURN_SLOTS);
                        iniciar(fresh.getIdPartida());
                    } else {
                        // Nadie se unio: cerrar el lobby vacio
                        LOGGER.info("Lobby publico {} expiro vacio. Cerrando.", fresh.getIdPartida());
                        fresh.setEstado(ESTADO_FINISHED);
                        fresh.setCorriendo(false);
                        partidaRepository.save(fresh);
                    }
                }
            } catch (Exception ex) {
                LOGGER.warn("Error procesando lobby expirado {}: {}", partida.getIdPartida(), ex.getMessage());
            }
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

        List<String> bag = createAndShuffleBag(partida.isModoArcade());
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
            botParticipacion.setConectado(true);
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
            lastState = toPartidaDTO(partida);
        }
        return lastState;
    }

    private void triggerAutomatedBotTurnsAsync(Integer idPartida) {
        if (idPartida == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    submitAutomatedBotTurns(idPartida);
                }
            });
            return;
        }
        submitAutomatedBotTurns(idPartida);
    }

    private void submitAutomatedBotTurns(Integer idPartida) {
        if (!botTurnJobsInProgress.add(idPartida)) {
            return;
        }
        botTurnExecutor.submit(() -> {
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    synchronized (getTurnMutex(idPartida)) {
                        PartidaEntity partida = partidaRepository.findById(idPartida).orElse(null);
                        if (partida == null) {
                            return;
                        }
                        ensureDefaultState(partida);
                        runAutomatedBotTurnsIfNeeded(partida, LocalDateTime.now());
                    }
                });
            } catch (Exception ex) {
                LOGGER.warn("No se pudieron procesar turnos automaticos de bot para partida {}: {}",
                        idPartida, ex.getMessage());
            } finally {
                botTurnJobsInProgress.remove(idPartida);
            }
        });
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
        return askBotMove(partida, botParticipacion, List.of());
    }

    private BotMoveResponse askBotMove(
            PartidaEntity partida,
            ParticipacionEntity botParticipacion,
            List<String> itemsUsedThisTurn) {
        BotMoveRequest payload = new BotMoveRequest();
        payload.setBoard(toIaBoard(parseMesaGroups(partida.getConjuntoMesa())));
        payload.setPoolCount(parseTileList(partida.getBolsa()).size());
        payload.setMyTiles(toIaTiles(parseTileList(botParticipacion.getManoActual())));
        payload.setOpponentRackCounts(getOpponentRackCounts(partida.getIdPartida(), botParticipacion.getJugador().getId()));
        payload.setOpened(!parseMesaGroups(partida.getConjuntoMesa()).isEmpty());
        payload.setLevel(BOT_LEVEL);
        payload.setRandomness(BOT_RANDOMNESS);
        payload.setTurnNumber(partida.getTurno());
        payload.setArcade(buildIaArcadePayload(partida, botParticipacion, itemsUsedThisTurn));
        return botIntegrationService.askMove(payload);
    }

    private Map<String, Object> buildIaArcadePayload(
            PartidaEntity partida,
            ParticipacionEntity botParticipacion,
            List<String> itemsUsedThisTurn) {
        if (partida == null || botParticipacion == null || !partida.isModoArcade()) {
            return null;
        }

        Map<String, Object> arcade = new LinkedHashMap<>();
        arcade.put("enabled", true);

        String blockedColor = toIaBlockedColor(partida.getEventoActual());
        if (blockedColor != null) {
            arcade.put("blocked_color", blockedColor);
        }
        if (hasActiveEffect(botParticipacion, "GLASS_CEILING")) {
            arcade.put("min_play_points", 30);
        }

        List<String> inventory = getSupportedInventory(botParticipacion);
        arcade.put("my_items", inventory);
        arcade.put("opponent_item_counts", getIaItemCounts(partida.getIdPartida(), botParticipacion.getJugador().getId()));
        arcade.put("time_limit_s", hasActiveEffect(botParticipacion, "CHILI_PEPPER")
                ? TURN_TIMEOUT_SECONDS / 2
                : TURN_TIMEOUT_SECONDS);
        if ("50porcien".equalsIgnoreCase(safe(partida.getEventoActual()))) {
            arcade.put("shop_discount", 0.5);
        }
        arcade.put("draw_at_turn_start", "+pieza".equalsIgnoreCase(safe(partida.getEventoActual())));
        arcade.put("items_used_this_turn", itemsUsedThisTurn == null ? List.of() : new ArrayList<>(itemsUsedThisTurn));
        arcade.put("guardian_angel_active", false);

        Map<String, Object> shop = buildIaShopPayload(partida, botParticipacion, inventory);
        if (shop != null) {
            arcade.put("shop", shop);
        }
        return arcade;
    }

    private String toIaBlockedColor(String eventoActual) {
        if (eventoActual == null || !eventoActual.startsWith("prohibido_")) {
            return null;
        }
        return switch (eventoActual.substring("prohibido_".length()).toLowerCase(Locale.ROOT)) {
            case "rojo" -> "R";
            case "azul" -> "B";
            case "naranja" -> "O";
            case "negro" -> "K";
            default -> null;
        };
    }

    private List<String> getSupportedInventory(ParticipacionEntity participacion) {
        return parsePurchasedObjectCodes(participacion.getHabilidadesActuales()).stream()
                .filter(IA_SUPPORTED_MARKET_OBJECTS::contains)
                .toList();
    }

    private List<Integer> getIaItemCounts(Integer idPartida, Integer idBotJugador) {
        List<Integer> counts = new ArrayList<>();
        ParticipacionEntity bot = mustGetParticipacion(idPartida, idBotJugador);
        counts.add(getSupportedInventory(bot).size());
        for (ParticipacionEntity p : getOrderedParticipaciones(idPartida)) {
            if (!p.getJugador().getId().equals(idBotJugador)) {
                counts.add(getSupportedInventory(p).size());
            }
        }
        return counts;
    }

    private Map<String, Object> buildIaShopPayload(
            PartidaEntity partida,
            ParticipacionEntity botParticipacion,
            List<String> inventory) {
        if (inventory != null && inventory.size() >= MARKET_OBJECTS_PER_PLAYER) {
            return null;
        }
        LinkedHashMap<String, Integer> stock = getOrCreateMarketStockByPlayer(
                partida,
                botParticipacion.getJugador().getId()
        );
        List<Map<String, Object>> offer = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : stock.entrySet()) {
            String code = entry.getKey();
            int units = entry.getValue() == null ? 0 : entry.getValue();
            Integer price = MARKET_OBJECT_VALUES.get(code);
            if (units > 0 && price != null && IA_SUPPORTED_MARKET_OBJECTS.contains(code)) {
                offer.add(Map.of("item", code, "price", price));
            }
        }
        if (offer.isEmpty()) {
            return null;
        }
        Map<String, Object> shop = new LinkedHashMap<>();
        shop.put("offer", offer);
        shop.put("balance", botParticipacion.getJugador().getMonedas());
        return shop;
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
        if ("use_item".equals(moveType)) {
            applyBotArcadeItemPhases(partida, botParticipacion, move.getItemUse(), now);
            return;
        }
        applyBotShopChoice(partida, botParticipacion, move);
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

    private void applyBotArcadeItemPhases(
            PartidaEntity partida,
            ParticipacionEntity botParticipacion,
            BotItemUseDTO firstItemUse,
            LocalDateTime now) {
        if (!partida.isModoArcade()) {
            throw new IllegalStateException("La IA pidio usar objeto fuera de modo arcade");
        }

        List<String> itemsUsedThisTurn = new ArrayList<>();
        BotItemUseDTO currentItemUse = firstItemUse;
        for (int phase = 0; phase < MAX_BOT_ARCADE_ITEM_PHASES; phase++) {
            String usedCode = applyBotItemUseOrDeny(partida, botParticipacion, currentItemUse);
            if (usedCode != null && !itemsUsedThisTurn.contains(usedCode)) {
                itemsUsedThisTurn.add(usedCode);
            }

            BotMoveResponse nextMove = askBotMove(partida, botParticipacion, itemsUsedThisTurn);
            if (nextMove == null || nextMove.getMove() == null || nextMove.getMove().getMoveType() == null) {
                throw new IllegalStateException("La IA no devolvio una jugada valida tras usar objeto");
            }
            BotMoveDTO move = nextMove.getMove();
            if (!"use_item".equals(move.getMoveType())) {
                applyBotMove(partida, botParticipacion, nextMove, now);
                return;
            }
            currentItemUse = move.getItemUse();
        }

        BotMoveResponse finalMove = askBotMove(partida, botParticipacion, itemsUsedThisTurn);
        if (finalMove != null && finalMove.getMove() != null && !"use_item".equals(finalMove.getMove().getMoveType())) {
            applyBotMove(partida, botParticipacion, finalMove, now);
            return;
        }
        drawOneTileIfPossible(partida, botParticipacion);
        advanceTurn(partida, now);
    }

    private String applyBotItemUseOrDeny(
            PartidaEntity partida,
            ParticipacionEntity botParticipacion,
            BotItemUseDTO itemUse) {
        if (itemUse == null || itemUse.getItem() == null || itemUse.getItem().isBlank()) {
            return null;
        }

        String codigoObjeto;
        try {
            codigoObjeto = normalizeMarketObjectCode(itemUse.getItem());
        } catch (IllegalArgumentException ex) {
            LOGGER.warn("La IA propuso un objeto arcade no soportado: {}", itemUse.getItem());
            return itemUse.getItem();
        }
        if (!IA_SUPPORTED_MARKET_OBJECTS.contains(codigoObjeto)) {
            LOGGER.warn("La IA propuso un objeto arcade no implementado por backend: {}", codigoObjeto);
            return codigoObjeto;
        }

        Integer idObjetivo = resolveIaTargetPlayerId(
                partida.getIdPartida(),
                botParticipacion.getJugador().getId(),
                itemUse.getTargetPlayerIdx()
        );

        if ("SWAP_ON_FAIL".equals(codigoObjeto) || "CRYSTAL_BALL".equals(codigoObjeto)) {
            LOGGER.info("Objeto de IA {} denegado: requiere interacción/información que el backend no reinyecta al bot", codigoObjeto);
            return codigoObjeto;
        }

        String codigoObjetoObjetivo = null;
        if ("WHITE_GLOVE".equals(codigoObjeto)) {
            ParticipacionEntity objetivo = idObjetivo == null ? null : mustGetParticipacion(partida.getIdPartida(), idObjetivo);
            List<String> objetivoInventario = objetivo == null ? List.of() : getSupportedInventory(objetivo);
            if (objetivoInventario.isEmpty()) {
                return codigoObjeto;
            }
            codigoObjetoObjetivo = objetivoInventario.get(0);
        }

        try {
            usarObjetoMercado(
                    partida.getIdPartida(),
                    botParticipacion.getJugador().getId(),
                    codigoObjeto,
                    idObjetivo,
                    codigoObjetoObjetivo,
                    null,
                    null
            );
        } catch (RuntimeException ex) {
            LOGGER.warn("Se denego el uso automatico de {} por la IA en partida {}: {}",
                    codigoObjeto, partida.getIdPartida(), ex.getMessage());
        }
        return codigoObjeto;
    }

    private Integer resolveIaTargetPlayerId(Integer idPartida, Integer idBotJugador, Integer targetPlayerIdx) {
        if (targetPlayerIdx == null) {
            return null;
        }
        List<ParticipacionEntity> opponents = getOrderedParticipaciones(idPartida).stream()
                .filter(p -> !p.getJugador().getId().equals(idBotJugador))
                .toList();
        if (targetPlayerIdx > 0 && targetPlayerIdx <= opponents.size()) {
            return opponents.get(targetPlayerIdx - 1).getJugador().getId();
        }
        if (targetPlayerIdx >= 0 && targetPlayerIdx < opponents.size()) {
            return opponents.get(targetPlayerIdx).getJugador().getId();
        }
        return null;
    }

    private void applyBotShopChoice(PartidaEntity partida, ParticipacionEntity botParticipacion, BotMoveDTO move) {
        if (!partida.isModoArcade() || move.getShopChoice() == null
                || move.getShopChoice().getBuy() == null || move.getShopChoice().getBuy().isBlank()) {
            return;
        }

        String codigoObjeto;
        try {
            codigoObjeto = normalizeMarketObjectCode(move.getShopChoice().getBuy());
        } catch (IllegalArgumentException ex) {
            LOGGER.warn("La IA propuso comprar un objeto arcade no soportado: {}", move.getShopChoice().getBuy());
            return;
        }
        if (!IA_SUPPORTED_MARKET_OBJECTS.contains(codigoObjeto)) {
            return;
        }

        try {
            comprarObjetoMercado(partida.getIdPartida(), botParticipacion.getJugador().getId(), codigoObjeto);
        } catch (RuntimeException ex) {
            LOGGER.warn("Se denego la compra automatica de {} por la IA en partida {}: {}",
                    codigoObjeto, partida.getIdPartida(), ex.getMessage());
        }
    }

    private void drawOneTileIfPossible(PartidaEntity partida, ParticipacionEntity participacion) {
        List<String> bag = parseTileList(partida.getBolsa());
        if (bag.isEmpty()) {
            return;
        }
        String drawn = applyArcadeRainbowOnDraw(partida, bag.remove(0));
        List<String> hand = parseTileList(participacion.getManoActual());
        hand.add(drawn);
        participacion.setManoActual(serializeTileList(hand));
        participacion.setFichasActuales(hand.size());
        participacion.setTurnosInactivo(0);
        participacionRepository.save(participacion);
        partida.setBolsa(serializeTileList(bag));
    }

    private boolean requiresTarget(String codigoObjeto) {
        return switch (codigoObjeto) {
            case "CRYSTAL_BALL", "PLUS_FOUR", "SWAP_ON_FAIL", "WHITE_GLOVE", "SMOKE_BOMB", "CHILI_PEPPER",
                    "GLASS_CEILING" -> true;
            default -> false;
        };
    }

    private void drawTilesForTarget(PartidaEntity partida, ParticipacionEntity objetivo, int cantidad) {
        List<String> bag = parseTileList(partida.getBolsa());
        List<String> hand = parseTileList(objetivo.getManoActual());
        int drawCount = Math.min(Math.max(cantidad, 0), bag.size());
        for (int i = 0; i < drawCount; i++) {
            hand.add(bag.remove(0));
        }
        objetivo.setManoActual(serializeTileList(hand));
        objetivo.setFichasActuales(hand.size());
        participacionRepository.save(objetivo);
        partida.setBolsa(serializeTileList(bag));
    }

    private void applyMidasTouch(ParticipacionEntity actor) {
        List<String> hand = parseTileList(actor.getManoActual());
        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < hand.size(); i++) {
            String tile = hand.get(i);
            if (!isJoker(tile) && !isArcadeGold(tile)) {
                candidates.add(i);
            }
        }
        if (candidates.isEmpty()) {
            participacionRepository.save(actor);
            return;
        }
        Collections.shuffle(candidates);
        int maxTransforms = Math.min(4, candidates.size());
        int minTransforms = Math.min(2, maxTransforms);
        int transformCount = ThreadLocalRandom.current().nextInt(minTransforms, maxTransforms + 1);
        for (int i = 0; i < transformCount; i++) {
            int index = candidates.get(i);
            hand.set(index, convertTileToGold(hand.get(index)));
        }
        actor.setManoActual(serializeTileList(hand));
        actor.setFichasActuales(hand.size());
        participacionRepository.save(actor);
    }

    private String convertTileToGold(String tile) {
        if (tile == null || tile.isBlank() || isJoker(tile)) {
            return tile;
        }
        String normalized = normalizeTile(tile);
        char color = parseColor(normalized);
        int value = parseValue(normalized);
        return color + String.format("%02d", value) + "D";
    }

    private List<String> getSwapOnFailPreview(ParticipacionEntity objetivo) {
        List<String> hand = parseTileList(objetivo.getManoActual());
        int limit = Math.min(SWAP_ON_FAIL_VISIBLE_TILES, hand.size());
        return new ArrayList<>(hand.subList(0, limit));
    }

    private void applySwapOnFail(
            ParticipacionEntity actor,
            ParticipacionEntity objetivo,
            List<String> preview,
            String fichaPropiaRaw,
            String fichaObjetivoRaw) {
        String fichaPropia = normalizeTile(fichaPropiaRaw);
        String fichaObjetivo = normalizeTile(fichaObjetivoRaw);
        if (!preview.contains(fichaObjetivo)) {
            throw new IllegalArgumentException("La ficha objetivo debe estar entre las 3 visibles");
        }

        List<String> handActor = parseTileList(actor.getManoActual());
        List<String> handObjetivo = parseTileList(objetivo.getManoActual());
        if (!handActor.remove(fichaPropia)) {
            throw new IllegalStateException("No tienes la ficha " + fichaPropia + " en tu mano");
        }
        if (!handObjetivo.remove(fichaObjetivo)) {
            throw new IllegalStateException("La ficha objetivo ya no esta disponible");
        }

        handActor.add(fichaObjetivo);
        handObjetivo.add(fichaPropia);
        actor.setManoActual(serializeTileList(handActor));
        actor.setFichasActuales(handActor.size());
        awardObjectsForRainbowTilesRemovedFromHand(actor, List.of(fichaPropia));
        objetivo.setManoActual(serializeTileList(handObjetivo));
        objetivo.setFichasActuales(handObjetivo.size());
        awardObjectsForRainbowTilesRemovedFromHand(objetivo, List.of(fichaObjetivo));
        participacionRepository.save(actor);
        participacionRepository.save(objetivo);
    }

    private void addActiveEffect(List<String> effects, String effectCode) {
        if (effects == null || effectCode == null || effectCode.isBlank()) {
            return;
        }
        if (!effects.contains(effectCode)) {
            effects.add(effectCode);
        }
    }

    private boolean hasActiveEffect(ParticipacionEntity participacion, String effectCode) {
        return parseActiveEffectCodes(participacion.getHabilidadesActuales()).contains(effectCode);
    }

    private boolean consumeActiveEffect(ParticipacionEntity participacion, String effectCode) {
        List<String> inventory = parsePurchasedObjectCodes(participacion.getHabilidadesActuales());
        List<String> effects = parseActiveEffectCodes(participacion.getHabilidadesActuales());
        boolean removed = effects.remove(effectCode);
        if (removed) {
            participacion.setHabilidadesActuales(serializeHabilidadesState(inventory, effects));
            participacionRepository.save(participacion);
        }
        return removed;
    }

    private UsarObjetoMercadoResponse buildUsarObjetoResponse(
            PartidaEntity partida,
            ParticipacionEntity actor,
            String codigoObjeto,
            Integer idJugadorObjetivo,
            boolean consumido,
            boolean bloqueadoPorGuardianAngel,
            String mensaje,
            List<String> fichasObjetivoVisibles,
            List<String> habilidadesObjetivoVisibles,
            List<String> objetivoInventario,
            List<String> objetivoEfectos) {
        return UsarObjetoMercadoResponse.builder()
                .idPartida(partida.getIdPartida())
                .idJugador(actor.getJugador().getId())
                .codigoObjeto(codigoObjeto)
                .idJugadorObjetivo(idJugadorObjetivo)
                .consumido(consumido)
                .bloqueadoPorGuardianAngel(bloqueadoPorGuardianAngel)
                .mensaje(mensaje)
                .manoActual(serializeTileList(parseTileList(actor.getManoActual())))
                .habilidadesCompradas(parsePurchasedObjectCodes(actor.getHabilidadesActuales()))
                .efectosActivos(parseActiveEffectCodes(actor.getHabilidadesActuales()))
                .fichasObjetivoVisibles(fichasObjetivoVisibles == null ? List.of() : new ArrayList<>(fichasObjetivoVisibles))
                .habilidadesObjetivoVisibles(habilidadesObjetivoVisibles == null ? null : new ArrayList<>(habilidadesObjetivoVisibles))
                .efectosActivosObjetivo(objetivoEfectos == null ? List.of() : new ArrayList<>(objetivoEfectos))
                .build();
    }

    private int resolveTurnDurationSeconds(Integer idPartida, int turnSlot) {
        return TURN_TIMEOUT_SECONDS;
    }

    private void enforceGlassCeilingIfNeeded(ParticipacionEntity participacion, List<String> playedTiles) {
        // GLASS_CEILING se expone al frontend como efecto activo; su gestion funcional ya no se fuerza en backend.
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
        enforceGlassCeilingIfNeeded(participacion, List.of(tile));
        participacion.setManoActual(serializeTileList(updatedHand));
        participacion.setFichasActuales(updatedHand.size());
        awardObjectsForRainbowTilesRemovedFromHand(participacion, List.of(tile));
        participacion.setTurnosInactivo(0);
        participacionRepository.save(participacion);

        mesa.set(extendIndex, extended);
        partida.setConjuntoMesa(serializeMesaGroups(mesa));

        if (updatedHand.isEmpty()) {
            finishGame(partida, participacion.getJugador().getId());
            return toPartidaDTO(partidaRepository.save(partida));
        }

        PartidaDTO updated = advanceTurn(partida, LocalDateTime.now());
        triggerAutomatedBotTurnsAsync(partida.getIdPartida());
        return updated;
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
        enforceGlassCeilingIfNeeded(participacion, tilesToRemove);
        participacion.setManoActual(serializeTileList(updatedHand));
        participacion.setFichasActuales(updatedHand.size());
        awardObjectsForRainbowTilesRemovedFromHand(participacion, tilesToRemove);
        participacionRepository.save(participacion);

        partida.setConjuntoMesa(serializeMesaGroups(normalized));

        if (updatedHand.isEmpty()) {
            finishGame(partida, participacion.getJugador().getId());
            return toPartidaDTO(partidaRepository.save(partida));
        }

        PartidaDTO updated = advanceTurn(partida, LocalDateTime.now());
        triggerAutomatedBotTurnsAsync(partida.getIdPartida());
        return updated;
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
        awardObjectsForRainbowTilesRemovedFromHand(botParticipacion, List.of(tile));
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
        awardObjectsForRainbowTilesRemovedFromHand(botParticipacion, tilesToRemove);
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
        return normalized;
    }

    private String normalizeIaTile(String iaTile) {
        if (iaTile == null || iaTile.isBlank()) {
            throw new IllegalStateException("La IA devolvio una ficha vacia");
        }
        String t = iaTile.trim().toUpperCase();
        if ("J".equals(t) || JOKER_CANONICAL.equals(t)) {
            return JOKER_CANONICAL;
        }
        try {
            return normalizeTile(t);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Formato de ficha IA invalido: " + iaTile);
        }
    }

    private String toBackendTile(String iaTile) {
        if (JOKER_CANONICAL.equals(iaTile) || "J".equals(iaTile)) {
            return JOKER_CANONICAL;
        }
        return normalizeTile(iaTile);
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
        runtime.deadline = now.plusSeconds(resolveTurnDurationSeconds(partida.getIdPartida(), nextTurn));

        partida.setTurno(nextTurn);
        partida.setTurnoInicio(now);
        partida.setEventoActual(partida.isModoArcade() ? getRandomArcadeEvent() : "");
        return toPartidaDTO(partidaRepository.save(partida));
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
        partida.setPuntuacionFinal(serializeScoreSummary(buildScoreSummary(winnerId, winnerPoints, remaining)));
        partida.setTurnoInicio(null);
        turnRuntimeByPartida.remove(partida.getIdPartida());
    }

    private Map<String, Object> buildScoreSummary(Integer winnerId, int winnerPoints, Map<Integer, Integer> remaining) {
        Map<String, Integer> normalizedRemaining = new LinkedHashMap<>();
        List<Integer> orderedPlayerIds = new ArrayList<>(remaining.keySet());
        Collections.sort(orderedPlayerIds);
        for (Integer playerId : orderedPlayerIds) {
            normalizedRemaining.put(String.valueOf(playerId), remaining.get(playerId));
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("winnerId", winnerId);
        summary.put("winnerPoints", winnerPoints);
        summary.put("remaining", normalizedRemaining);
        return summary;
    }

    private int calculateHandPoints(List<String> hand) {
        int points = 0;
        for (String tile : hand) {
            if (isJoker(tile)) {
                points += 30;
            } else {
                int base = parseValue(tile);
                if (isArcadeGold(tile)) {
                    base = base * 2;
                }
                points += base;
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

    private List<String> createAndShuffleBag(boolean modoArcade) {
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

        if (modoArcade) {
            // En arcade las doradas forman parte de la bolsa. Las arcoiris se generan al robar.
            for (String color : colors) {
                for (int value = 1; value <= 13; value++) {
                    for (int copies = 0; copies < ARCADE_GOLD_DUPLICATES_PER_VALUE; copies++) {
                        bag.add(color + String.format("%02d", value) + "D");
                    }
                }
            }
        }

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
        synchronized (getTurnMutex(idPartida)) {
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
            boolean replaced = maybeReplaceInactivePlayerWithBot(partida, timedOut);
            if (!replaced && !isBotPlayer(timedOut)) {
                // Primer timeout: forzar robo de ficha como si el jugador hubiera pasado
                drawOneTileIfPossible(partida, timedOut);
            }
            runtime.currentTurn = nextTurn;
            runtime.deadline = now.plusSeconds(resolveTurnDurationSeconds(idPartida, nextTurn));

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
                .plusSeconds(resolveTurnDurationSeconds(partida.getIdPartida(), currentTurn));
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
        if (partida.getPuntuacionFinal() == null) {
            partida.setPuntuacionFinal("");
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
        int index = ThreadLocalRandom.current().nextInt(ARCADE_BASE_EVENTS.size() + ARCADE_PROHIBITED_COLORS.size());
        if (index < ARCADE_BASE_EVENTS.size()) {
            return ARCADE_BASE_EVENTS.get(index);
        }
        return "prohibido_" + ARCADE_PROHIBITED_COLORS.get(index - ARCADE_BASE_EVENTS.size());
    }

    /**
     * Incrementa el contador de inactividad del jugador.
     * Si llega al limite (2), lo sustituye por un bot.
     * @return true si el jugador fue reemplazado por bot, false si solo se incremento el contador.
     */
    private boolean maybeReplaceInactivePlayerWithBot(PartidaEntity partida, ParticipacionEntity participacion) {
        if (participacion == null || isBotPlayer(participacion)) {
            return false;
        }
        int nextValue = Math.min(participacion.getTurnosInactivo() + 1, INACTIVITY_LIMIT_TURNS);
        participacion.setTurnosInactivo(nextValue);
        if (nextValue >= INACTIVITY_LIMIT_TURNS) {
            replaceParticipationWithBot(partida, participacion);
            return true;
        }
        participacionRepository.save(participacion);
        return false;
    }

    private void resetPlayerInactivity(ParticipacionEntity participacion) {
        if (participacion == null || isBotPlayer(participacion)) {
            return;
        }
        if (participacion.getTurnosInactivo() != 0) {
            participacion.setTurnosInactivo(0);
            participacionRepository.save(participacion);
        }
    }

    private ParticipacionEntity replaceParticipationWithBot(PartidaEntity partida, ParticipacionEntity participacion) {
        JugadorEntity bot = createBotPlayer(partida.getIdPartida());
        ParticipacionEntity botParticipacion = new ParticipacionEntity();
        botParticipacion.setId(new ParticipacionId(bot.getId(), partida.getIdPartida()));
        botParticipacion.setJugador(bot);
        botParticipacion.setPartida(partida);
        botParticipacion.setFichasActuales(participacion.getFichasActuales());
        botParticipacion.setHabilidadesActuales(participacion.getHabilidadesActuales());
        botParticipacion.setManoActual(participacion.getManoActual());
        botParticipacion.setOrdenTurno(participacion.getOrdenTurno());
        botParticipacion.setTurnosInactivo(0);
        botParticipacion.setConectado(true);

        participacionRepository.delete(participacion);
        return participacionRepository.save(botParticipacion);
    }

    private PartidaDTO toPartidaDTO(PartidaEntity partida) {
        return attachFichasPorJugador(Mapper.toDTO(partida));
    }

    private ParticipacionEntity findExistingWaitingPublicParticipation(Integer idJugador, boolean modoArcade) {
        return participacionRepository.findByJugador_Id(idJugador).stream()
                .filter(p -> p.getPartida() != null)
                .filter(p -> !p.getPartida().isPrivada())
                .filter(p -> !p.getPartida().isCorriendo())
                .filter(p -> ESTADO_WAITING.equalsIgnoreCase(safe(p.getPartida().getEstado())))
                .filter(p -> p.getPartida().isModoArcade() == modoArcade)
                .findFirst()
                .orElse(null);
    }

    private PartidaEntity findJoinablePublicGame(boolean modoArcade) {
        List<PartidaEntity> candidates = partidaRepository.findMatchmakingCandidates(
                modoArcade,
                ESTADO_WAITING,
                PageRequest.of(0, 20));
        for (PartidaEntity candidata : candidates) {
            if (candidata == null || candidata.getIdPartida() == null) {
                continue;
            }
            int playerCount = participacionRepository.findByPartida_IdPartida(candidata.getIdPartida()).size();
            if (playerCount < MAX_TURN_SLOTS) {
                return candidata;
            }
        }
        return null;
    }

    private PartidaEntity createPublicWaitingGame(boolean modoArcade) {
        PartidaEntity partida = new PartidaEntity();
        partida.setTurno(0);
        partida.setFecha(LocalDate.now());
        partida.setMercado("");
        partida.setBolsa("");
        partida.setConjuntoMesa("");
        partida.setEventoActual("");
        partida.setModoArcade(modoArcade);
        // Se guarda el instante de creacion del lobby para el timer de auto-llenado
        partida.setTurnoInicio(LocalDateTime.now());
        partida.setEstado(ESTADO_WAITING);
        partida.setGanadorId(null);
        partida.setPuntuacionFinal("");
        partida.setPrivada(false);
        partida.setCorriendo(false);
        return partidaRepository.save(partida);
    }

    private ParticipacionEntity createOrReuseParticipation(Integer idJugador, PartidaEntity partida) {
        ParticipacionId id = new ParticipacionId(idJugador, partida.getIdPartida());
        ParticipacionEntity existing = participacionRepository.findById(id).orElse(null);
        if (existing != null) {
            if (!existing.isConectado()) {
                existing.setConectado(true);
                existing = participacionRepository.save(existing);
            }
            return existing;
        }

        JugadorEntity jugador = jugadorRepository.findById(idJugador)
                .orElseThrow(() -> new NoSuchElementException("Jugador no encontrado: " + idJugador));

        ParticipacionEntity entity = new ParticipacionEntity();
        entity.setId(id);
        entity.setJugador(jugador);
        entity.setPartida(partida);
        entity.setFichasActuales(0);
        entity.setHabilidadesActuales("");
        entity.setManoActual("");
        entity.setOrdenTurno(null);
        entity.setTurnosInactivo(0);
        entity.setConectado(true);
        return participacionRepository.save(entity);
    }

    private PartidaDTO toMatchmakingDTO(PartidaRepository.MatchmakingPartidaView partida) {
        if (partida == null) {
            return null;
        }
        return PartidaDTO.builder()
                .idPartida(partida.getIdPartida())
                .turno(partida.getTurno())
                .fecha(partida.getFecha())
                .modoArcade(partida.getModoArcade())
                .estado(partida.getEstado())
                .privada(partida.getPrivada())
                .corriendo(partida.getCorriendo())
                .build();
    }

    private PartidaDTO toSummaryDTO(PartidaRepository.PartidaSummaryView partida) {
        if (partida == null) {
            return null;
        }
        return PartidaDTO.builder()
                .idPartida(partida.getIdPartida())
                .turno(partida.getTurno())
                .fecha(partida.getFecha())
                .eventoActual(partida.getEventoActual())
                .modoArcade(partida.getModoArcade())
                .turnoInicio(partida.getTurnoInicio())
                .estado(partida.getEstado())
                .ganadorId(partida.getGanadorId())
                .privada(partida.getPrivada())
                .corriendo(partida.getCorriendo())
                .build();
    }

    private PartidaDTO attachFichasPorJugador(PartidaDTO dto) {
        if (dto == null || dto.getIdPartida() == null) {
            return dto;
        }
        Map<Integer, Integer> fichas = new LinkedHashMap<>();
        for (ParticipacionRepository.ParticipacionFichasView p : participacionRepository.findFichasByPartidaId(dto.getIdPartida())) {
            if (p.getIdJugador() != null) {
                fichas.put(p.getIdJugador(), p.getFichasActuales());
            }
        }
        dto.setFichasPorJugador(fichas);
        return dto;
    }

    private List<PartidaDTO> attachFichasPorJugador(List<PartidaDTO> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return List.of();
        }
        List<Integer> partidaIds = dtos.stream()
                .map(PartidaDTO::getIdPartida)
                .filter(id -> id != null)
                .toList();
        if (partidaIds.isEmpty()) {
            return dtos;
        }
        Map<Integer, Map<Integer, Integer>> fichasPorPartida = new HashMap<>();
        for (ParticipacionRepository.ParticipacionFichasView p : participacionRepository.findFichasByPartidaIds(partidaIds)) {
            if (p.getIdPartida() == null || p.getIdJugador() == null) {
                continue;
            }
            fichasPorPartida
                    .computeIfAbsent(p.getIdPartida(), ignored -> new LinkedHashMap<>())
                    .put(p.getIdJugador(), p.getFichasActuales());
        }
        for (PartidaDTO dto : dtos) {
            if (dto != null && dto.getIdPartida() != null) {
                Map<Integer, Integer> fichas = fichasPorPartida.get(dto.getIdPartida());
                dto.setFichasPorJugador(fichas == null ? new LinkedHashMap<>() : fichas);
            }
        }
        return dtos;
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
                codes.add(matcher.group(1).toUpperCase(Locale.ROOT));
            }
        }
        return codes;
    }

    private List<String> parseActiveEffectCodes(String encoded) {
        List<String> effects = new ArrayList<>();
        if (encoded == null || encoded.isBlank()) {
            return effects;
        }

        String[] parts = encoded.split(",");
        for (String rawPart : parts) {
            String token = rawPart == null ? "" : rawPart.trim();
            if (token.regionMatches(true, 0, EFFECT_PREFIX, 0, EFFECT_PREFIX.length())) {
                String effect = token.substring(EFFECT_PREFIX.length()).trim().toUpperCase(Locale.ROOT);
                if (!effect.isBlank()) {
                    effects.add(effect);
                }
            }
        }
        return effects;
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
        String candidate = rawCode.trim().toUpperCase(Locale.ROOT);
        Matcher matcher = MARKET_OBJECT_PATTERN.matcher(candidate);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Objeto de mercado invalido: " + rawCode);
        }
        return matcher.group(1).toUpperCase(Locale.ROOT);
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

    private String serializeHabilidadesState(List<String> purchasedCodes, List<String> activeEffectCodes) {
        List<String> encoded = new ArrayList<>();
        if (purchasedCodes != null) {
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
        }
        if (activeEffectCodes != null) {
            for (String effectRaw : activeEffectCodes) {
                if (effectRaw == null || effectRaw.isBlank()) {
                    continue;
                }
                encoded.add(EFFECT_PREFIX + effectRaw.trim().toUpperCase(Locale.ROOT));
            }
        }
        return String.join(",", encoded);
    }

    private MercadoParticipacionDTO buildMercadoParticipacionDTO(
            Integer idPartida,
            Integer idJugador,
            int monedasJugador,
            Map<String, Integer> stockByCode,
            List<String> habilidadesCompradas,
            List<String> efectosActivos) {
        return MercadoParticipacionDTO.builder()
                .idPartida(idPartida)
                .idJugador(idJugador)
                .monedasJugador(monedasJugador)
                .objetosMercado(toMercadoItems(stockByCode))
                .habilidadesCompradas(new ArrayList<>(habilidadesCompradas))
                .efectosActivos(new ArrayList<>(efectosActivos))
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
        Matcher suffixMatcher = TILE_PATTERN.matcher(tile);
        if (suffixMatcher.matches()) {
            String color = suffixMatcher.group(1);
            int value = Integer.parseInt(suffixMatcher.group(2));
            String suffix = canonicalizeArcadeSuffix(suffixMatcher.group(3));
            return color + String.format("%02d", value) + suffix;
        }

        Matcher prefixMatcher = TILE_PREFIX_PATTERN.matcher(tile);
        if (prefixMatcher.matches()) {
            String prefix = canonicalizeArcadeSuffix(prefixMatcher.group(1));
            String color = prefixMatcher.group(2);
            int value = Integer.parseInt(prefixMatcher.group(3));
            return color + String.format("%02d", value) + prefix;
        }

        throw new IllegalArgumentException("Formato de ficha invalido: " + tileRaw);
    }

    private boolean isJoker(String tile) {
        if (tile == null || tile.isBlank()) {
            return false;
        }
        String normalized = tile.trim().toUpperCase();
        return JOKER_CANONICAL.equals(normalized) || "J1".equals(normalized) || "J2".equals(normalized) || "J".equals(normalized);
    }

    private String consumeAnyJoker(Map<String, Integer> countMap) {
        for (Map.Entry<String, Integer> entry : countMap.entrySet()) {
            String key = entry.getKey();
            int current = entry.getValue() == null ? 0 : entry.getValue();
            if (current <= 0) {
                continue;
            }
            if (isJoker(key)) {
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
        String upper = token.toUpperCase(Locale.ROOT);
        if (isJoker(upper)) {
            return JOKER_CANONICAL;
        }
        return normalizeTile(upper);
    }

    private char parseColor(String tile) {
        return tile.charAt(0);
    }

    private int parseValue(String tile) {
        String normalized = normalizeTile(tile);
        Matcher matcher = Pattern.compile("^[RBOK](0[1-9]|1[0-3])([AD]?)$").matcher(normalized);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("No se pudo extraer valor de ficha: " + tile);
        }
        return Integer.parseInt(matcher.group(1));
    }

    private boolean isArcadeGold(String tile) {
        if (tile == null || tile.isBlank()) {
            return false;
        }
        String normalized = tile.trim().toUpperCase(Locale.ROOT);
        return normalized.matches("^[RBOK](0[1-9]|1[0-3]).*D.*$");
    }

    private boolean isArcadeWildcard(String tile) {
        if (tile == null || tile.isBlank()) {
            return false;
        }
        String normalized = tile.trim().toUpperCase(Locale.ROOT);
        return normalized.matches("^[RBOK](0[1-9]|1[0-3]).*A.*$");
    }

    private String applyArcadeRainbowOnDraw(PartidaEntity partida, String drawnTile) {
        if (drawnTile == null || drawnTile.isBlank() || partida == null || !partida.isModoArcade()) {
            return drawnTile;
        }
        String normalized = normalizeTile(drawnTile);
        if (isJoker(normalized) || isArcadeGold(normalized) || isArcadeWildcard(normalized)) {
            return normalized;
        }
        if (ThreadLocalRandom.current().nextDouble() >= ARCADE_RAINBOW_DRAW_CHANCE) {
            return normalized;
        }
        return normalizeTile(normalized + "A");
    }

    private void awardObjectsForRainbowTilesRemovedFromHand(ParticipacionEntity participacion, List<String> removedTiles) {
        if (participacion == null || removedTiles == null || removedTiles.isEmpty()) {
            return;
        }

        int rainbowCount = 0;
        for (String tile : removedTiles) {
            if (isArcadeWildcard(tile)) {
                rainbowCount++;
            }
        }
        if (rainbowCount <= 0) {
            return;
        }

        List<String> inventario = parsePurchasedObjectCodes(participacion.getHabilidadesActuales());
        List<String> efectosActivos = parseActiveEffectCodes(participacion.getHabilidadesActuales());
        for (int i = 0; i < rainbowCount; i++) {
            inventario.add(randomMarketObjectCode());
        }
        participacion.setHabilidadesActuales(serializeHabilidadesState(inventario, efectosActivos));
    }

    private String randomMarketObjectCode() {
        return MARKET_OBJECT_CODES.get(ThreadLocalRandom.current().nextInt(MARKET_OBJECT_CODES.size()));
    }

    private String canonicalizeArcadeSuffix(String rawSuffix) {
        if (rawSuffix == null || rawSuffix.isBlank()) {
            return "";
        }
        String normalized = rawSuffix.trim().toUpperCase(Locale.ROOT);
        if (!"A".equals(normalized) && !"D".equals(normalized)) {
            throw new IllegalArgumentException("La ficha arcade solo puede tener una habilidad A o D");
        }
        return normalized;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String serializeScoreSummary(Map<String, Object> scoreSummary) {
        if (scoreSummary == null || scoreSummary.isEmpty()) {
            return "";
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(scoreSummary);
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo serializar la puntuacion final");
        }
    }

    public void clearTurnRuntimeCache() {
        turnRuntimeByPartida.clear();
        turnMutexByPartida.clear();
    }

    private Object getTurnMutex(Integer idPartida) {
        if (idPartida == null) {
            throw new IllegalArgumentException("idPartida es obligatorio");
        }
        return turnMutexByPartida.computeIfAbsent(idPartida, ignored -> new Object());
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
