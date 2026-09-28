-- Módulo customer: datos personales del cliente. Depende de auth (users).

CREATE TABLE document_types (
    id   SMALLINT PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE
);

INSERT INTO document_types (id, code) VALUES
    (1, 'Cédula de ciudadanía'),
    (2, 'Cédula de extranjería'),
    (3, 'Pasaporte');

CREATE TABLE customers (
    id               UUID PRIMARY KEY,
    user_id          UUID NOT NULL,
    role_id          SMALLINT NOT NULL DEFAULT 1,
    first_names      VARCHAR(100) NOT NULL,
    last_names       VARCHAR(100) NOT NULL,
    document_type_id SMALLINT NOT NULL REFERENCES document_types (id),
    document_number  VARCHAR(20) NOT NULL,
    phone            VARCHAR(16) NOT NULL,
    birth_date       DATE NOT NULL,
    created_at       TIMESTAMP NOT NULL,
    updated_at       TIMESTAMP NOT NULL,
    CONSTRAINT uk_customers_user UNIQUE (user_id),
    -- Un mismo número puede existir en dos tipos de documento distintos, pero no repetirse dentro del mismo tipo
    CONSTRAINT uk_customers_document UNIQUE (document_type_id, document_number),
    CONSTRAINT ck_customers_role CHECK (role_id = 1),
    CONSTRAINT ck_customers_phone CHECK (phone ~ '^\+[0-9]{7,15}$'),
    -- Junto con el CHECK de role_id impide que un usuario ADMIN tenga fila en customers
    CONSTRAINT fk_customers_user FOREIGN KEY (user_id, role_id) REFERENCES users (id, role_id)
);
