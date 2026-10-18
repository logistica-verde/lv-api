CREATE TABLE usuarios (
    id bigint IDENTITY NOT NULL,
    nome varchar(120) NOT NULL,
    email varchar(160) NOT NULL,
    senha_hash varchar(100) NOT NULL,
    perfil varchar(20) NOT NULL,
    ativo bit NOT NULL,
    CONSTRAINT pk_usuarios PRIMARY KEY (id),
    CONSTRAINT uq_usuarios_email UNIQUE (email),
    CONSTRAINT ck_usuarios_perfil CHECK (perfil IN ('OPERATOR', 'AUDITOR', 'ADMIN'))
);
