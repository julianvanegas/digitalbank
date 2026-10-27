# Política de seguridad

DigitalBank maneja autenticación, datos de clientes y operaciones financieras. Esta política explica qué versiones reciben soporte, cómo reportar vulnerabilidades y qué prácticas deben seguirse durante una investigación.

## Versiones con soporte

El proyecto está en desarrollo activo. El soporte de seguridad se concentra en la versión desplegada desde `main` y en las ramas que la preceden durante el proceso de validación.

| Rama | Soporte de seguridad |
| --- | --- |
| `main` (producción) | Soporte activo |
| `quality` y `develop` | Soporte durante la integración de parches |
| Otras ramas | Sin soporte garantizado |

## Cómo reportar una vulnerabilidad

No abras un issue ni un pull request público para reportar una vulnerabilidad. Los reportes públicos pueden exponer información explotable antes de que exista una corrección.

Usa la función privada de reporte de vulnerabilidades en la pestaña [Security](https://github.com/julianvanegas/digitalbank/security) del repositorio. Completa el reporte y adjunta únicamente la información necesaria para reproducir y evaluar el problema.

Nunca incluyas datos reales de clientes, credenciales ni secretos en el reporte. Si necesitas demostrar exposición de datos, utiliza datos sintéticos.

## Divulgación responsable

Te pedimos que:

- Nos des un plazo razonable para corregir el problema antes de divulgarlo públicamente.
- Evites acceder, modificar o eliminar datos que no sean tuyos, degradar el servicio o ejecutar pruebas de denegación de servicio.
- Pruebes únicamente con cuentas y datos que tú hayas creado.
- Detengas la investigación y nos informes inmediatamente si encuentras datos personales, credenciales o acceso a sistemas que no te pertenezcan.

## Fuera de alcance

- Hallazgos de escáneres automáticos sin evidencia de impacto o una prueba de explotación reproducible.
- Vulnerabilidades que requieran acceso físico al dispositivo de la víctima.
- Ingeniería social contra mantenedores o usuarios.
- Vulnerabilidades en dependencias de terceros sin impacto demostrable en DigitalBank; repórtalas también al proyecto afectado.
- Divulgación de versiones o banners sin impacto de seguridad demostrable.
- Falta de buenas prácticas sin una consecuencia explotable.

## Buenas prácticas

- Nunca subas `.env`, claves de API ni secretos al repositorio; usa [`.env.example`](.env.example) solo con valores de desarrollo.
- En producción, genera valores propios y robustos, de al menos 32 caracteres, para `JWT_SECRET`, `VERIFICATION_HMAC_SECRET` y `ACCOUNT_NUMBER_SECRET`.
- Deja `SWAGGER_ENABLED=false` en producción.
- Rota de inmediato cualquier secreto que se haya expuesto.
- Revisa los permisos de GitHub Actions y concede a cada job únicamente los permisos necesarios.
- No incluyas secretos en issues, pull requests, logs, imágenes de contenedor ni documentación.
