# ms-rutaexpress-bff

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

El frontend debe consumir únicamente el BFF o AWS API Gateway; no debe llamar
directamente a los microservicios de dominio en el despliegue final.

## Configuración local

Las variables de identidad y rutas se mantienen fuera de Git. Copia
`.env.example` como `.env` y cárgalas en tu entorno antes de iniciar el BFF.

```powershell
.\mvnw.cmd test
docker build -t rutaexpress-bff:local .
```

Para desarrollo local se permite CORS desde `http://localhost:4200`. En AWS se
configurará el origen definitivo en `CORS_ALLOWED_ORIGIN`.

## Salud y estado

`GET /actuator/health` está disponible sin JWT para comprobaciones de Docker y
AWS. Los endpoints de negocio requieren un access token de Microsoft Entra ID
con el rol de aplicación `Admin`.

Para Entra ID configura `JWT_ISSUER_URI` con el emisor v2.0 de tu tenant y
`JWT_AUDIENCE` con el identificador expuesto por la API (por ejemplo,
`api://<id-de-aplicacion-api>`). El BFF valida firma, emisor, expiración y
audiencia; después convierte el claim `roles` de Entra a autoridades Spring
Security. El rol `Admin` se configura como *App role* en el registro de la API.
