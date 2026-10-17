-- Números de cuenta sin ensayo y error: antes se sorteaba un candidato y se consultaba si ya existía. Ahora cada número
-- sale de esta secuencia, que entrega un valor único sin mirar la tabla, y el servidor lo pasa por una permutación
-- secreta que lo reparte en el rango de 12 dígitos (AccountNumberPermutation), para que siga sin poder deducirse
-- el número de una cuenta ajena. El UNIQUE de accounts.account_number sigue siendo la garantía final.
--
-- El rango es el de los números válidos: 9 · 10^11 valores (de 100000000000 a 999999999999). NO CYCLE hace que, si
-- algún día se agotara, la apertura falle en vez de repetir números. Una secuencia no es transaccional: una apertura
-- que se revierte deja un hueco, y eso no importa porque los números no son consecutivos para quien los ve.
CREATE SEQUENCE account_number_seq
    START WITH 1
    MINVALUE 1
    MAXVALUE 899999999999
    NO CYCLE;
