-- Llave visible de la cuenta (CA01): arroba + dos letras del primer nombre + tres dígitos del
-- documento + un consecutivo. El UUID sigue siendo la clave primaria y la que referencian las claves
-- foráneas; esta llave es el identificador que ve el usuario.
--
-- El consecutivo no es la posición de la cuenta dentro del cliente: es el primer número libre para
-- ese prefijo. Dos clientes que coincidan en nombre y documento continúan la misma numeración en vez
-- de chocar, así que la columna necesita margen para crecer.

-- 1. Se agrega sin restricciones para poder rellenar las cuentas que ya existen.
ALTER TABLE accounts ADD COLUMN account_key VARCHAR(64);

-- 2. Se calcula la llave de las cuentas ya abiertas, con la misma regla que AccountKeyGenerator:
--    primer nombre (hasta el primer espacio) sin tildes, en mayúscula y relleno con X si tiene una
--    sola letra; los tres últimos dígitos del documento; y el consecutivo por prefijo, repartido en
--    orden de apertura igual que lo haría el código al abrirlas una por una.
WITH prefijos AS (
    SELECT a.id,
           a.created_at,
           '@' || rpad(
                    substr(
                      regexp_replace(
                        translate(upper(split_part(c.first_names, ' ', 1)),
                                  'ÁÉÍÓÚÜÑ', 'AEIOUUN'),
                        '[^A-Z]', '', 'g'),
                      1, 2),
                    2, 'X')
               || lpad(right(regexp_replace(c.document_number, '\D', '', 'g'), 3), 3, '0') AS prefijo
    FROM accounts a
    JOIN customers c ON c.id = a.customer_id
),
numeradas AS (
    SELECT id,
           prefijo,
           row_number() OVER (PARTITION BY prefijo ORDER BY created_at, id) AS consecutivo
    FROM prefijos
)
UPDATE accounts a
SET account_key = n.prefijo || n.consecutivo::text
FROM numeradas n
WHERE n.id = a.id;

-- 3. Ya con todas las filas pobladas, la columna pasa a ser obligatoria y única.
--    El UNIQUE es lo que garantiza que la llave identifique una sola cuenta; el CHECK fija su forma:
--    el consecutivo empieza en 1 y no lleva ceros a la izquierda.
ALTER TABLE accounts
    ALTER COLUMN account_key SET NOT NULL,
    ADD CONSTRAINT uk_accounts_key UNIQUE (account_key),
    ADD CONSTRAINT ck_accounts_key CHECK (account_key ~ '^@[A-Z]{2}[0-9]{3}[1-9][0-9]*$');
