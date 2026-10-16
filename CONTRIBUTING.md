# Flujo de colaboracion (Gitflow)

## Ramas y estado

- `main`: entrega estable anterior. No subir EV2 directamente aqui ni presentar la infraestructura pendiente como terminada.
- `develop`: base de integracion; recibe funcionalidades por pull request revisado.
- `codex/feature-ev2-mensajeria`: avance EV2 de este repositorio. El prefijo `codex/` identifica el entorno de trabajo; es una rama **feature**, no otra rama estable.
- `codex/release-<version>`: crear desde develop cuando la integracion este aprobada. PR a main y posterior sincronizacion con develop.
- `codex/hotfix-<descripcion>`: solo correcciones urgentes de main, con retorno tambien a develop.

No se crean releases, etiquetas de entrega o hotfix ficticios para aparentar etapas no realizadas. No reescribir historia compartida ni usar force-push.

## Colaborar sin perder trabajo

1. Clonar este repositorio y las otras carpetas de componentes como hermanas; seguir el README de EV2.
2. Ejecutar `git fetch origin` y `git status`. Conservar/confirmar cambios propios antes de cambiar de rama. No usar reset --hard.
3. Para revisar el avance existente: `git switch --track origin/codex/feature-ev2-mensajeria` (si ya existe local, `git switch codex/feature-ev2-mensajeria`).
4. Para trabajo nuevo, acordar la base: normalmente `origin/develop`; si depende del PR EV2 abierto, usar su rama feature explicitamente, no mezclar a ciegas.
5. Crear una rama propia, commits descriptivos y PR a develop. Describir pruebas, dependencias entre repositorios y limites cloud.
6. Revisar diferencias y CI. Integrar con merge commit para conservar la rama y el PR; no borrar historia para simular trabajo de otros integrantes.
7. Solo despues de login institucional y recorrido AWS/RDS/API Gateway reales, preparar release, revisar el PR a main y etiquetar la version aprobada.

El codigo puede estar publicado en una feature sin que este integrado en develop. Un PR abierto o un check verde NO equivale a despliegue listo ni a aprobacion de la profesora.

## Seguridad y evidencia

No subir .env reales, passwords, tokens, claves privadas, node_modules, target, modelos ni fotos/audios de clase. Los IDs de aplicaciones son publicos, pero nunca sustituyen contrasenas ni consentimiento. Los tests locales con claves generadas/fixtures no acreditan login Duoc real.

Los workflows solo prueban codigo, con permisos de lectura; no despliegan AWS/Azure y no necesitan sus credenciales. Los integrantes realizan sus propios commits con su identidad: no se inventa autoria ni se distribuyen commits artificialmente.
