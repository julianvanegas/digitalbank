-- Llave visible de la cuenta (CA01): arroba + dos letras del primer nombre + seis dígitos del
-- documento + la posición de la cuenta. El UUID sigue siendo la clave primaria y la que referencian
-- las claves foráneas; esta llave es el identificador que ve el usuario.

-- 1. Se agrega sin restricciones para poder rellenar las cuentas que ya existen.
ALTER TABLE accounts ADD COLUMN account_key VARCHAR(10);

-- 2. Se calcula la llave de las cuentas ya abiertas, con la misma regla que AccountKeyGenerator:
--    primer nombre (hasta el primer espacio), sin tildes, en mayúscula y relleno con X si tiene una
--    sola letra; los seis últimos dígitos del documento, rellenos con ceros a la izquierda.
UPDATE accounts a
SET account_key = '@'
    || rpad(
         substr(
           regexp_replace(
             translate(upper(split_part(c.first_names, ' ', 1)),
                       'ÁÉÍÓÚÜÑ', 'AEIOUUN'),
             '[^A-Z]', '', 'g'),
           1, 2),
         2, 'X')
    || lpad(right(regexp_replace(c.document_number, '\D', '', 'g'), 6), 6, '0')
    || a.account_index::text
FROM customers c
WHERE c.id = a.customer_id;

-- 3. Ya con todas las filas pobladas, la columna pasa a ser obligatoria y única.
--    El UNIQUE es lo que garantiza que la llave identifique una sola cuenta; el CHECK fija su forma.
ALTER TABLE accounts
    ALTER COLUMN account_key SET NOT NULL,
    ADD CONSTRAINT uk_accounts_key UNIQUE (account_key),
    ADD CONSTRAINT ck_accounts_key CHECK (account_key ~ '^@[A-Z]{2}[0-9]{6}[1-3]$');
