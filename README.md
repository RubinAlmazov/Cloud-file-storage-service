# Cloud File Storage

Многопользовательское облачное хранилище файлов в духе Яндекс.Диска: регистрация, сессии, CRUD над файлами и папками, поиск.

## Стек

- **Java 21**, Spring Boot 4.0.3, Maven
- **Spring Security** + сессионная авторизация, **Spring Session** в Redis
- **Spring Data JPA** + PostgreSQL — пользователи
- **MinIO** (S3-совместимое хранилище) — файлы пользователей
- **Apache Kafka** — асинхронное создание корневой папки пользователя при регистрации
- **MyTinyParser** — собственный парсер multipart/form-data (отдельный проект, подключён как Maven-зависимость)
- **Testcontainers** — интеграционные тесты на реальных Postgres/MinIO
- **springdoc-openapi** — Swagger UI

## Структура хранилища

- Один бакет MinIO: `user-files`
- Корневая папка пользователя: `user-${id}-files/`
- Все объекты пользователя живут под этим префиксом

## Запуск

### Через Docker Compose (рекомендуемый путь)

Поднимает приложение и все зависимости (Postgres, Redis, MinIO, Kafka) одной командой.

1. Скопируй шаблон секретов и отредактируй при необходимости:
   ```bash
   cp .env.example .env
   ```
2. Из каталога `CloudFileStorage`:
   ```bash
   docker compose up --build
   ```

Что будет доступно:

| Сервис | Адрес |
|---|---|
| API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| MinIO Console | http://localhost:9001 |
| Postgres | localhost:5438 |

`docker compose down` — остановить (данные в volumes сохраняются).
`docker compose down -v` — снести вместе с данными.

### Локально (приложение на хосте, зависимости в Docker)

```bash
docker compose up postgres redis minio kafka
mvn spring-boot:run
```

В этом режиме приложение использует значения из `application.properties` (хосты `localhost`).

## Конфигурация

Все настройки можно переопределить через переменные окружения (Spring relaxed binding). В compose это уже сделано — приложение в контейнере общается с зависимостями по именам сервисов:

| Переменная | Значение в compose |
|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://postgres:5432/${POSTGRES_DB}` |
| `SPRING_DATASOURCE_USERNAME` | из `.env` |
| `SPRING_DATASOURCE_PASSWORD` | из `.env` |
| `SPRING_DATA_REDIS_HOST` | `redis` |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `kafka:29092` |
| `MINIO_ENDPOINT` | `http://minio:9000` |
| `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` | из `.env` |

Секреты Postgres и MinIO лежат в `.env`.

## API

Префиксы: `/auth`, `/user`, `/resource`.

### Аутентификация

| Метод | Путь | Описание |
|---|---|---|
| POST | `/auth/sign-up` | Регистрация |
| POST | `/auth/sign-in` | Вход (создаётся сессия в Redis) |
| GET | `/user/me` | Текущий пользователь |

### Файлы и папки

| Метод | Путь | Описание |
|---|---|---|
| GET | `/resource?path=...` | Информация о ресурсе |
| DELETE | `/resource?path=...` | Удалить файл/папку |
| POST | `/resource?path=...` | Загрузить файлы (multipart/form-data) |
| GET | `/resource/download?path=...` | Скачать (папки — ZIP) |
| GET | `/resource/rename?path=...&path2=...` | Переименовать/переместить |
| GET | `/resource/search?query=...` | Поиск по имени |
| GET | `/resource/list` | Список папок пользователя |
| GET | `/resource/directory?path=...` | Содержимое папки |
| POST | `/resource/derictory?path=...` | Создать пустую папку |

Подробности и формат тел запросов — в Swagger UI.

## Тесты

```bash
mvn test
```

Тесты используют Testcontainers — для них нужен запущенный Docker. Покрыты:

- `AuthServiceTest`, `AuthControllerTest` — регистрация/авторизация на реальной Postgres
- `UserServiceTest`
- `ResourceServiceTest` — операции с файлами на реальном MinIO

## Особенности реализации

### Свой multipart-парсер

Стандартный `spring.servlet.multipart` отключён (`spring.servlet.multipart.enabled=false`). Загрузка файлов идёт через свою библиотеку `MyTinyParser`, которая стримит тело запроса в MinIO без буферизации в памяти. Это позволяет принимать большие файлы без OOM.

Парсер подключён как Maven-зависимость:
```xml
<dependency>
    <groupId>org.me</groupId>
    <artifactId>MyTinyParser</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

В Docker-сборке парсер собирается на первом этапе multi-stage build — исходники соседнего проекта подкладываются в контекст сборки и устанавливаются в `~/.m2` контейнера.

### Создание корневой папки пользователя через Kafka

При регистрации `AuthService` отправляет userId в Kafka. Listener `KafkaService` потребляет сообщение и асинхронно создаёт `user-${id}-files/` в MinIO.
