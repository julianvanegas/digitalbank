package co.edu.udea.certificacion.digitalbank.models;

import co.edu.udea.certificacion.digitalbank.utils.Json;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** Datos de un cliente de prueba. Cada llamada a {@link #valido()} genera documento y correo únicos. */
public record DatosCliente(String nombres, String apellidos, int tipoDocumento, String numeroDocumento,
                           String telefono, LocalDate fechaNacimiento, String correo) {

    /** Cumple la política: 8 a 16 caracteres, mayúscula, minúscula, número y carácter especial. */
    public static final String CONTRASENA_VALIDA = "Segura#2026";

    private static final AtomicLong SECUENCIA = new AtomicLong();

    public static DatosCliente valido() {
        String unico = String.format("%d%03d", System.currentTimeMillis(), SECUENCIA.getAndIncrement() % 1000);
        return new DatosCliente("Ana Maria", "Prueba Qa", 1, unico, "+573001112233",
                LocalDate.now().minusYears(30), "qa." + unico + "@example.com");
    }

    public DatosCliente conFechaNacimiento(LocalDate nueva) {
        return new DatosCliente(nombres, apellidos, tipoDocumento, numeroDocumento, telefono, nueva, correo);
    }

    public DatosCliente conNumeroDocumento(String nuevo) {
        return new DatosCliente(nombres, apellidos, tipoDocumento, nuevo, telefono, fechaNacimiento, correo);
    }

    public DatosCliente conCorreo(String nuevo) {
        return new DatosCliente(nombres, apellidos, tipoDocumento, numeroDocumento, telefono, fechaNacimiento, nuevo);
    }

    public String aJson() {
        return Json.objeto(campos());
    }

    /** Igual que {@link #aJson()} pero sin uno de los campos; recibe el nombre del campo en el JSON (por ejemplo, "email"). */
    public String aJsonSin(String campo) {
        Map<String, Object> campos = campos();
        campos.remove(campo);
        return Json.objeto(campos);
    }

    private Map<String, Object> campos() {
        Map<String, Object> campos = new LinkedHashMap<>();
        campos.put("firstNames", nombres);
        campos.put("lastNames", apellidos);
        campos.put("documentTypeId", tipoDocumento);
        campos.put("documentNumber", numeroDocumento);
        campos.put("phone", telefono);
        campos.put("birthDate", fechaNacimiento.toString());
        campos.put("email", correo);
        return campos;
    }
}
