# Users Management — Spring Boot, Arquitectura Hexagonal y DDD

Aplicación de gestión de usuarios construida con Java 17 y Spring Boot. La API REST es el punto de entrada activo. El código de la antigua CLI se conserva como adaptador inactivo y no posee un contenedor de dependencias independiente.

Spring es el único *composition root*: `Main` inicia el contexto y las dependencias se resuelven mediante configuración y component scanning de Spring.

## Verificación

```bash
./mvnw clean test
./mvnw clean package
```

En Windows se puede utilizar `mvnw.cmd`.

## Perfiles de persistencia

El perfil activo por defecto es `postgres`. Este perfil selecciona a la vez el dialecto del
`DataSource` y el adaptador `UserRepositoryPostgres`. Para usar MySQL:

```bash
SPRING_PROFILES_ACTIVE=mysql ./mvnw spring-boot:run
```

| Script de esquema | Motor |
| --- | --- |
| `src/main/resources/schema-postgres.sql` | PostgreSQL (perfil por defecto y Render) |
| `src/main/resources/schema.sql` | MySQL (perfil `mysql`) |

En ambos scripts el administrador inicial es `admin@example.com` con la contraseña
`Admin1234!`. Cambiar esa contraseña antes de exponer la API en Render.

## Base de datos remota (PostgreSQL)

El despliegue usa PostgreSQL en la nube. En Supabase, obtener la cadena de conexión desde
*Project Settings > Database > Connection string > Session pooler*:

* `DB_HOST`: host del **Session pooler** (`aws-0-<region>.pooler.supabase.com`).
* `DB_PORT`: `5432`. No usar el Transaction pooler (puerto `6543`): no admite sentencias
  preparadas con nombre.
* `DB_NAME`: nombre de la base (en Supabase, `postgres`).
* `DB_USERNAME`: `postgres.<project-ref>`.
* `DB_PASSWORD`: contraseña del proyecto.
* `DB_SSL_MODE`: `require` (o `verify-full` para validar el certificado).

Ejecutar `schema-postgres.sql` una vez sobre esa base de datos para crear los tipos, la tabla
`users`, el trigger de `updated_at` y el administrador inicial.

## Docker

```bash
docker compose up --build
```

El archivo `compose.yaml` levanta PostgreSQL, ejecuta `schema-postgres.sql` al crear el volumen
y arranca la API en `http://localhost:8080`. El servicio `mysql` del mismo archivo está pensado
para probar el perfil `mysql`.

## Correo transaccional (Brevo)

Render no permite conexiones SMTP salientes en el plan gratuito, por lo que las notificaciones se
envían con la **API HTTPS de Brevo** (puerto 443) mediante el perfil `brevo`:

1. Crear una cuenta gratuita en <https://www.brevo.com>.
2. Verificar el remitente en *Senders & IP > Senders* (no hace falta dominio propio).
3. Crear una API key en *SMTP & API > API Keys*.
4. Configurar las variables de entorno:

```text
BREVO_API_KEY=<api-key-de-brevo>
BREVO_FROM_ADDRESS=<remitente-verificado>
BREVO_FROM_NAME=Gestion de Usuarios
```

Con `SPRING_PROFILES_ACTIVE=postgres,brevo` se activa este adaptador. Sin ese perfil la aplicación
usa SMTP clásico con `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`,
`SMTP_FROM_ADDRESS` y `SMTP_FROM_NAME` (válido para desarrollo local o alojamientos con SMTP
saliente permitido).

Un fallo de correo no hace fallar la operación: el usuario ya quedó guardado y solo se registra el
aviso en el log.

## Despliegue en Render

El archivo `render.yaml` define el servicio web `users-management-api`, el build con Docker, el
perfil `postgres`, el health check y el despliegue automático de cada commit que llegue a la
rama `main`.

1. Crear un Blueprint en Render y seleccionar este repositorio.
2. Completar en el panel de Render los secretos marcados con `sync: false`:
   `DB_HOST`, `DB_PORT`, `DB_USERNAME`, `DB_PASSWORD`, `BREVO_API_KEY` y
   `BREVO_FROM_ADDRESS`. `JWT_SECRET` se genera automáticamente.
3. Ejecutar `schema-postgres.sql` una vez sobre la base de datos remota.
4. Desplegar el Blueprint. La API queda disponible en el subdominio `onrender.com` asignado por
   Render, Swagger UI en `/swagger-ui.html` y el documento OpenAPI en `/v3/api-docs`.
5. Probar la API desde Swagger UI: crear un usuario, iniciar sesión en `/api/auth/login` para obtener
   el JWT y comprobar que llega el correo de notificación.

Las credenciales nunca deben guardarse en `application.properties` ni en
`render.yaml`. Para desarrollo local, deben proporcionarse como variables de entorno.
