-- Creación de tablas según el modelo E/R (diagrama fondo negro)

-- Tabla de jugadores
CREATE TABLE JUGADOR (
    ID              INT PRIMARY KEY,
    NOMBRE          VARCHAR(100) NOT NULL,
    CONTRASENA      VARCHAR(100) NOT NULL,
    URL_IMG_PERFIL  VARCHAR(255),
    COSMETICOS      VARCHAR(255),
    MONEDAS         INT NOT NULL DEFAULT 0,
    PARTIDAS_GANADAS INT NOT NULL DEFAULT 0
    PARTIDAS_PERDIDAS INT NOT NULL DEFAULT 0
    PARTIDAS_EMPATADAS INT NOT NULL DEFAULT 0
    PARTIDAS_PENDIENTES INT NOT NULL DEFAULT 0
    PARTIDAS_FINALIZADAS INT NOT NULL DEFAULT 0
);

-- Tabla de partidas
CREATE TABLE PARTIDA (
    ID_Partida  INT PRIMARY KEY,
    TURNO       INT NOT NULL,
    FECHA       DATE NOT NULL,
    MERCADO     VARCHAR(100),
    BOLSA       VARCHAR(255),   -- campo para la bolsa
    CORRIENDO   BOOLEAN NOT NULL DEFAULT FALSE
);

-- Tabla intermedia de participación (relación muchos-a-muchos)
CREATE TABLE PARTICIPACION (
    ID_Jugador          INT NOT NULL,
    ID_Partida          INT NOT NULL,
    FICHAS_ACTUALES     INT NOT NULL,
    HABILIDADES_ACTUALES VARCHAR(255),
    PRIMARY KEY (ID_Jugador, ID_Partida),
    FOREIGN KEY (ID_Jugador) REFERENCES JUGADOR(ID),
    FOREIGN KEY (ID_Partida) REFERENCES PARTIDA(ID_Partida)
);

-- Tabla de lista de amigos (relación de amistad entre jugadores)
CREATE TABLE LISTA_AMIGOS (
    ID_Jugador   INT NOT NULL,
    ID_Amigo     INT NOT NULL,
    FECHA        DATE NOT NULL,
    ESTADO       VARCHAR(50) NOT NULL,
    PRIMARY KEY (ID_Jugador, ID_Amigo),
    FOREIGN KEY (ID_Jugador) REFERENCES JUGADOR(ID),
    FOREIGN KEY (ID_Amigo)   REFERENCES JUGADOR(ID)
);

