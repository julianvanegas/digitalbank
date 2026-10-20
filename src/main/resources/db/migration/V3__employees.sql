-- Módulo employee: datos personales del empleado (rol ADMIN). Depende de auth (users).

CREATE TABLE employees (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL,
    role_id     SMALLINT NOT NULL DEFAULT 2,
    first_names VARCHAR(100) NOT NULL,
    last_names  VARCHAR(100) NOT NULL,
    created_at  TIMESTAMP NOT NULL,
    updated_at  TIMESTAMP NOT NULL,
    CONSTRAINT uk_employees_user UNIQUE (user_id),
    CONSTRAINT ck_employees_role CHECK (role_id = 2),
    -- Junto con el CHECK de role_id impide que un usuario CUSTOMER tenga fila en employees
    -- (y, con la FK equivalente de customers, que un usuario sea cliente y empleado a la vez)
    CONSTRAINT fk_employees_user FOREIGN KEY (user_id, role_id) REFERENCES users (id, role_id)
);

-- Listado paginado de empleados ordenado por apellidos
CREATE INDEX idx_employees_last_names ON employees (last_names, id);
