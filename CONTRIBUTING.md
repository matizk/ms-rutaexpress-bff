# Organización de ramas y colaboración

Convención acordada por el equipo el 17-10-2026: `matizk` para el trabajo personal y `desarrollo` para integrar. Es un flujo inspirado en Gitflow, adaptado a ramas personales; no se exige que cada integrante comparta la rama matizk.

## Ramas

- `main`: entrega estable anterior. Solo recibe una release/hotfix revisada; no subir cambios directamente.
- `desarrollo`: integración del equipo; recibe cambios mediante pull request revisado.
- `matizk`: rama personal de esta copia. Se actualiza desde desarrollo antes de continuar, preservando cambios propios.
- Los otros integrantes conservan sus ramas personales y abren sus PR hacia desarrollo.
- `release/<version>`: preparar desde desarrollo cuando estén aprobadas las pruebas institucionales/cloud; PR a main y sincronización posterior con desarrollo.
- `hotfix/<descripcion>`: corrección urgente de main, con retorno también a desarrollo.

Flujo habitual: rama personal → PR a desarrollo → revisión/pruebas → merge commit. No crear nombres o commits para aparentar participación. El código integrado no equivale a despliegue ni a evaluación terminada.

## Actualizar esta copia personal sin perder trabajo

1. Revisar `git status`; conservar cambios propios antes de cambiar de rama.
2. Ejecutar `git fetch origin --prune`. La rama de integración ahora se llama desarrollo. Si esta copia fue clonada con una sola rama y no aparece origin/desarrollo, usar `git remote set-branches --add origin desarrollo` y volver a hacer fetch.
3. Usar `git switch matizk` si existe local; si solo existe en el remoto, `git switch --track origin/matizk`.
4. Ejecutar `git merge --ff-only origin/desarrollo`. Si hay divergencia, detenerse y coordinar la integración sin reset ni force-push; no sobrescribir trabajo propio o de otros.
5. Realizar los cambios, ejecutar pruebas, crear commits descriptivos y subir con `git push -u origin matizk`.
6. Abrir PR con base desarrollo y comparar matizk. Explicar alcance, pruebas, dependencias y pendientes reales; integrar con merge commit.
7. Después del merge, actualizar la rama personal nuevamente desde origin/desarrollo antes de seguir. Los demás integrantes hacen lo mismo con SUS ramas.

Para ejecutar el proyecto y migrar una copia que aún tenga develop, consultar la [guía central del equipo](https://github.com/matizk/ms-rutaexpress-shipments/blob/desarrollo/PREPARACION_EQUIPO_EV2.md). No clonar encima de cambios existentes.

## Historial y seguridad

El renombrado no reescribe commits ni elimina PR antiguos. Los informes fechados y los PR anteriores conservan los nombres utilizados entonces; no son instrucciones para crear de nuevo esas ramas. Solo se retiran referencias antiguas totalmente integradas y sin PR abiertos. No borrar historia compartida ni inventar autoría.

No publicar .env reales, passwords, bearer tokens, claves, datos personales ni fotos/audios de clase. Los tests con claves generadas y PostgreSQL local no acreditan login Duoc, RDS ni AWS reales. Los workflows prueban con permisos de lectura y sin credenciales cloud; no despliegan recursos.
