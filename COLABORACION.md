# Guía de colaboración y organización de ramas

Convención acordada por el equipo el 17-10-2026: `matizk` para el trabajo personal y `desarrollo` para integrar. Es un flujo inspirado en Gitflow, adaptado a ramas personales; no se exige que cada integrante comparta la rama matizk.

## Ramas

- `main`: entrega estable anterior. Solo recibe una entrega/corrección revisada; no subir cambios directamente.
- `desarrollo`: integración del equipo; recibe cambios mediante solicitud de integración revisada.
- `matizk`: rama personal de esta copia. Se actualiza desde desarrollo antes de continuar, preservando cambios propios.
- Los otros integrantes conservan sus ramas personales y abren sus PR hacia desarrollo.
- `entrega/<version>`: preparar desde desarrollo cuando estén aprobadas las pruebas institucionales/cloud; PR a main y sincronización posterior con desarrollo.
- `correccion/<descripcion>`: corrección urgente de main, con retorno también a desarrollo.

Flujo habitual: rama personal → PR a desarrollo → revisión/pruebas → confirmación de fusión. No crear nombres o commits para aparentar participación. El código integrado no equivale a despliegue ni a evaluación terminada.

## Actualizar esta copia personal sin perder trabajo

1. Revisar `git status`; conservar cambios propios antes de cambiar de rama.
2. Ejecutar `git fetch origin --prune`. La rama de integración ahora se llama desarrollo. Si esta copia fue clonada con una sola rama y no aparece origin/desarrollo, usar `git remote set-branches --add origin desarrollo` y volver a hacer fetch.
3. Usar `git switch matizk` si existe local; si solo existe en el remoto, `git switch --track origin/matizk`.
4. Ejecutar `git merge --ff-only origin/desarrollo`. Si hay divergencia, detenerse y coordinar la integración sin reset ni force-push; no sobrescribir trabajo propio o de otros.
5. Realizar los cambios, ejecutar pruebas, crear commits descriptivos y subir con `git push -u origin matizk`.
6. Abrir PR con base desarrollo y comparar matizk. Explicar alcance, pruebas, dependencias y pendientes reales; integrar con confirmación de fusión.
7. Después del merge, actualizar la rama personal nuevamente desde origin/desarrollo antes de seguir. Los demás integrantes hacen lo mismo con SUS ramas.

Para ejecutar el proyecto y migrar una copia que aún tenga develop, consultar la [guía central del equipo](https://github.com/matizk/ms-rutaexpress-envios/blob/desarrollo/PREPARACION_EQUIPO_EV2.md). No clonar encima de cambios existentes.

## Historial y seguridad

## Idioma y mantenimiento documental

Los repositorios, las guías y los nuevos mensajes descriptivos se escriben en español. Se conserva `README.md` por convención de GitHub y los nombres técnicos que exige cada herramienta (por ejemplo `Dockerfile`, `pom.xml`, `.github/workflows` o rutas de API). No traducir identificadores internos ni reescribir commits compartidos para cambiar su idioma. Los textos de la propia interfaz de GitHub no pertenecen al proyecto.

Una corrección exclusivamente documental puede partir de main en `correccion/documentacion-espanol`, revisarse mediante PR e integrarse sin publicar código nuevo de EV2. Comprobar que el cambio solo contenga documentos. Después se sincroniza con desarrollo conservando sus guías ampliadas. Esto no sustituye la validación cloud exigida para una entrega funcional.

### Conservación del historial

El renombrado no reescribe commits ni elimina PR antiguos. Los informes fechados y los PR anteriores conservan los nombres utilizados entonces; no son instrucciones para crear de nuevo esas ramas. Solo se retiran referencias antiguas totalmente integradas y sin PR abiertos. No borrar historia compartida ni inventar autoría.

No publicar .env reales, contraseñas, tokens de acceso, claves, datos personales ni fotos/audios de clase. Los tests con claves generadas y PostgreSQL local no acreditan login Duoc, RDS ni AWS reales. Los workflows prueban con permisos de lectura y sin credenciales cloud; no despliegan recursos.
