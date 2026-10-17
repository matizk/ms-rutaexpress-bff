# ms-rutaexpress-bff

> **Estás viendo main: versión estable de EV1.** El avance de EV2 y las instrucciones actuales para descargar las seis carpetas están en [desarrollo](https://github.com/matizk/ms-rutaexpress-bff/tree/desarrollo) y en la [guía central del equipo](https://github.com/matizk/ms-rutaexpress-envios/blob/desarrollo/CONTINUAR_EV2_EQUIPO.md). Esta actualización de main solo cambia documentos; no publica la implementación EV2 ni acredita despliegue cloud.

Los componentes de dominio usan nombres en español; frontend-rutaexpress y ms-rutaexpress-bff conservan sus nombres técnicos originales. Ver [COLABORACION.md](COLABORACION.md) para el flujo `matizk → desarrollo → release → main`. Las instrucciones EV1 que siguen son antecedentes de esta versión, no la configuración del sistema EV2 con los nuevos nombres. Para ejecutar la integración actual, seguir exclusivamente la guía de desarrollo.

BFF de RutaExpress para la Entrega 1 de DSY1107.

## Responsabilidad

- Recibir solicitudes desde AWS API Gateway.
- Validar JWT mediante Spring Security.
- Aplicar autorización por rol.
- Intermediar las llamadas hacia los microservicios de dominio.

## Rutas

| Prefijo público | Destino local por defecto |
|---|---|
| `/api/catalog/**` | `http://localhost:8081` |
| `/api/shipments/**` | `http://localhost:5000` |
| `/api/report/**` | `http://localhost:8082` |

La consulta `GET /api/shipments/track/{codigo}` es la única ruta de negocio
pública. Devuelve únicamente código, estado, origen, destino y última actualización;
no expone nombre ni correo del destinatario.

El frontend debe consumir únicamente el BFF o AWS API Gateway; no debe llamar
directamente a los microservicios de dominio en el despliegue final.

## Autenticación con AWS Cognito

```text
Angular obtiene un access token de Cognito
Angular -> API Gateway -> BFF -> catálogo / shipments -> PostgreSQL por servicio
```

El BFF es un gateway reactivo (Spring Cloud Gateway/WebFlux). No accede directamente a PostgreSQL:
cada microservicio administra sus datos. Mantiene las rutas completas y el cuerpo
de las solicitudes al reenviarlas.

El BFF acepta únicamente JWT con firma RS256 verificable con las claves públicas
del User Pool, `iss` igual al emisor configurado, `exp` obligatorio y vigente,
`nbf` válido si existe, `token_use=access` y `client_id` igual al App Client.
Spring permite 60 segundos de desfase de reloj. Las claves JWKS se consultan al
validar tokens y se almacenan en caché, sin llamar a AWS durante el arranque.

Sólo el grupo exacto `Admin` en `cognito:groups` se convierte en `ROLE_ADMIN`.
`admin`, `ADMIN`, grupos desconocidos y el antiguo claim `roles` no conceden acceso.
El grupo se asigna en AWS por un administrador; el usuario no puede elegirlo al registrarse.

| Solicitud | Resultado |
|---|---|
| GET `/actuator/health` sin token | 200, estado de salud |
| GET `/api/shipments/track/{codigo}` sin token | Consulta pública; 200 si existe, 404 si no existe |
| `/api/**` sin token o con token inválido | 401 Unauthorized |
| `/api/**` con access token válido pero sin grupo Admin | 403 Forbidden |
| Ruta de negocio con access token Admin válido | Se reenvía al microservicio |
| Preflight CORS del origen configurado | Permitido sin token |
| `POST /api/users` con token Admin | Registro de usuario de aplicación vinculado a Cognito |
| Otras rutas | Acceso denegado |

No se usa ID token para llamadas a la API. Cognito identifica el cliente de un
access token con `client_id`; no exigimos el `aud` propio del ID token. Si más
adelante se configura resource binding y una audiencia de API, añadir su validación.
El dominio de la pantalla de login tampoco es el issuer: el issuer es el del User Pool.

La API usa Bearer tokens, sin autenticación por cookies ni sesiones de login.
CORS se aplica en Spring Security antes de autenticar, también sobre respuestas
401/403. En AWS se debe coordinar CORS con API Gateway para evitar cabeceras duplicadas.

## Configuración y ejecución local

Requisitos: JDK 21 y Maven Wrapper, o Docker Desktop con contenedores Linux.
Usa `.env.example` como referencia. **Maven/Spring no carga `.env` automáticamente**:
para ejecutar con Maven define las variables en la terminal o en tu IDE.

```powershell
$env:COGNITO_ISSUER_URI = 'https://cognito-idp.<region>.amazonaws.com/<USER_POOL_ID>'
$env:COGNITO_CLIENT_ID = '<APP_CLIENT_ID>'
$env:CATALOGO_URL = 'http://localhost:8081'
$env:SHIPMENTS_URL = 'http://localhost:5000'
$env:REPORT_URL = 'http://localhost:8082'
$env:CORS_ALLOWED_ORIGIN = 'http://localhost:4200'
.\mvnw.cmd spring-boot:run
```

Reemplaza los marcadores por los valores reales antes de iniciar sesión. El backend
escucha en 8080. Inicia catálogo y shipments con sus respectivas configuraciones
PostgreSQL. Utiliza `http://localhost:4200` como origen del frontend; `127.0.0.1` es otro origen.

```powershell
.\mvnw.cmd clean verify
docker build -t rutaexpress-bff:local .
docker run --rm --name rutaexpress-bff -p 8080:8080 --env-file .env -e CATALOGO_URL=http://host.docker.internal:8081 -e SHIPMENTS_URL=http://host.docker.internal:5000 -e REPORT_URL=http://host.docker.internal:8082 rutaexpress-bff:local
```

Para Docker, crea un `.env` local a partir del ejemplo y completa sus valores.
`host.docker.internal` permite llegar desde Docker Desktop a servicios publicados
en Windows. En EC2/Compose usa nombres de los servicios y puertos internos de la
red Docker. `localhost` dentro de un contenedor se refiere al propio contenedor.

## Pruebas y ejemplos

```powershell
curl.exe -i http://localhost:8080/actuator/health
curl.exe -i http://localhost:8080/api/shipments
```

La segunda solicitud devuelve `401 Unauthorized`. Un Bearer válido de un usuario
sin `Admin` devuelve `403 Forbidden`. Un usuario Admin obtiene la respuesta del
microservicio (o un error de conexión si el servicio no está disponible).
No registres ni pegues tokens reales en GitHub o en capturas.

Las pruebas de integración arrancan el BFF real, firman JWT con claves RSA efímeras
y sirven JWKS y microservicios simulados en loopback. Verifican firma, claims,
expiración, rechazo de ID tokens, grupos, CORS, GET/POST y rutas del gateway.
No necesitan AWS ni credenciales y no sustituyen la prueba final con Cognito/PostgreSQL reales.

Validación local de esta migración: `mvnw test` aprobó 31 pruebas;
la imagen Docker compiló con Java 21 y el contenedor respondió salud `UP` (200)
y `401` al consultar shipments sin token. El arranque del contenedor se comprobó
con identificadores ficticios, no con un User Pool real.

## Despliegue AWS implementado

La entrega utiliza los siguientes componentes en AWS:

- Cognito User Pool, App Client público sin client secret, Managed Login y grupo
  exacto `Admin`.
- Angular integrado mediante Authorization Code con PKCE.
- API Gateway con stage publicado, CORS, JWT Authorizer y la ruta
  `ANY /api/{proxy+}` integrada con el BFF.
- BFF y microservicios ejecutados como contenedores en EC2 mediante Docker Compose.
- PostgreSQL separado para Catálogo y Envíos dentro del stack de contenedores.

API Gateway valida el JWT antes de reenviar la solicitud. El BFF conserva su propia
validación de firma, emisor, App Client, vigencia, `token_use=access` y grupo
`Admin`, por lo que la seguridad no depende únicamente del gateway externo.

Los identificadores y las direcciones del entorno se entregan mediante variables
como `COGNITO_ISSUER_URI`, `COGNITO_CLIENT_ID`, `CATALOGO_URL`, `SHIPMENTS_URL`,
`REPORT_URL` y `CORS_ALLOWED_ORIGIN`. Las contraseñas, tokens y credenciales AWS no
se versionan. El stack existente de Spring Boot 3.2.3 / Cloud 2023.0.2 se conserva
para esta entrega.

## Referencias

- [AWS: verificar JWT de Cognito](https://docs.aws.amazon.com/cognito/latest/developerguide/amazon-cognito-user-pools-using-tokens-verifying-a-jwt.html)
- [Spring Security: Resource Server reactivo](https://docs.spring.io/spring-security/reference/reactive/oauth2/resource-server/jwt.html)
