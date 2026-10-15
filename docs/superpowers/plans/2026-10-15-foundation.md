# Server Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Создать проверяемую серверную основу с PostgreSQL, регистрацией, входом, собственным профилем и выходом из аккаунта.

**Architecture:** Gradle-проект с модулями `protocol` и `server`. HTTP-контроллеры используют транзакционные сервисы, Spring Data JPA хранит аккаунты, профили и сессии в схеме `game`. Flyway управляет структурой, Hibernate только проверяет её.

**Tech Stack:** Java 21, Gradle 9.8.1 Wrapper и Groovy DSL, Spring Boot 4.1.1, Spring Security, Spring Data JPA, PostgreSQL, Flyway, JUnit 5.

**Spec:** `docs/superpowers/specs/2026-10-15-foundation-design.md`

## Global Constraints

- Первый этап содержит только `protocol` и `server`; `client`, `game-core`, комнаты и WebSocket появятся позднее.
- Базовый package — `com.gamedead`; графические и native-зависимости не попадают в сервер.
- Java toolchain и компиляция — 21. Плагин Spring Boot — только в `server`, BOM — только для его зависимостей.
- Пользовательская БД `gamedead`, роль `gamedead_app`, схема `game` уже созданы; существующие объекты не удалять.
- Пароль БД читает приложение из `GAMEDEAD_DB_PASSWORD`; адрес и пользователь — из `GAMEDEAD_DB_URL` и `GAMEDEAD_DB_USERNAME`. Пароли не запрашивать в чате и не печатать.
- `ddl-auto=validate`; Flyway default schema и Hibernate default schema — `game`; Flyway clean и автоматический baseline выключены.
- Времена — `Instant` / `timestamptz`, UTC; UUID создаёт Java.
- Обычная сборка не подключается к БД. `:server:integrationTest` требует отдельную `gamedead_test` и не считается пройденной без запуска.
- Логин `[a-z0-9_]{3,24}` после trim и lower-case; имя 3–24 Unicode code points без управляющих символов, после trim.
- Пароль не нормализуется: минимум 8 Unicode code points, максимум 72 байта UTF-8; BCrypt cost 12 с префиксом алгоритма.
- Сессии: 32 случайных байта, Base64URL без padding, хранится SHA-256 хеш, срок 8 часов, Bearer, без продления.
- Игра позднее: 2–4 игрока, около 30 минут, рывок на Shift; эти параметры не превращать в серверную игровую реализацию на первом этапе.

## Review Focus

1. Параллельные регистрации `Mage` и `mage` должны дать один аккаунт и один 409, без частично созданного профиля — Task 2 и Task 4.
2. Длинный пароль на кириллице/emoji нельзя молча усечь на границе BCrypt; пароль с пробелами должен сохранять значение — Task 2.
3. Тестовый профиль не должен мигрировать пользовательскую БД при ошибке переменных окружения — Task 1.
4. Истёкшие/отозванные сессии и выключенный аккаунт не должны получать профиль; перезапуск не отменяет действующую сессию — Task 3 и Task 4.
5. Слишком большой JSON, повреждённый JSON и исчерпанный лимит попыток должны возвращать согласованные ошибки без секретов и без неограниченного роста счётчиков — Task 4.

---

## Task 1: Воспроизводимая сборка и миграция PostgreSQL

**Files:**
- Create: `.gitignore`, `settings.gradle`, `build.gradle`, `gradle.properties`, `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`.
- Create: `protocol/build.gradle`, `server/build.gradle`.
- Create: `server/src/main/java/com/gamedead/server/GameDeadServerApplication.java`.
- Create: `server/src/main/resources/application.yml`, `server/src/main/resources/db/migration/V1__create_accounts_and_sessions.sql`.
- Create: `server/src/integrationTest/resources/application-integration.yml`.
- Create: `server/src/testFixtures/java/com/gamedead/server/support/TestDatabaseGuard.java`.
- Test: `server/src/test/java/com/gamedead/server/config/TestDatabaseGuardTest.java`.
- Test: `server/src/integrationTest/java/com/gamedead/server/db/DatabaseMigrationIT.java`.

**Interfaces:**
- Consumes: существующая схема `game`, конфигурация из переменных окружения.
- Produces: задачи `build`, `:server:bootRun`, `:server:integrationTest`; таблицы `accounts`, `player_profiles`, `auth_sessions` ровно по spec.
- `TestDatabaseGuard.requireTestDatabase(String jdbcUrl)` разрешает только прямой PostgreSQL JDBC URL с единственным адресом и точным именем БД `gamedead_test`, иначе бросает `IllegalArgumentException` до подключения.

- [ ] Создать только скрипты сборки, официальный Wrapper с проверенной SHA-256, main-класс и test source sets. В `server` включить `java-test-fixtures`; unit и integration source sets используют его fixtures с общим TestDatabaseGuard. Никаких паролей и обращения к БД при обычном `build`. Проверить доступность закреплённых релизов до загрузки; при недоступности зафиксировать препятствие, не подменять версии молча.
- [ ] Написать `TestDatabaseGuardTest`: принимает `jdbc:postgresql://localhost:5432/gamedead_test`; отвергает `gamedead`, пропущенное имя, другое имя и неоднозначный multi-host URL.
- [ ] Запустить `./gradlew.bat :server:test --tests '*TestDatabaseGuardTest'`: RED из-за отсутствующего guard. Реализовать guard в общей тестовой поддержке и повторить до GREEN.
- [ ] Написать `DatabaseMigrationIT`: миграция создаёт три таблицы с FK/UNIQUE/CHECK; повторный migrate применяет 0 изменений; отрицательный опыт и невалидный логин отвергаются, валидная строка сохраняется после повторной миграции.
- [ ] Подготовить локальную инструкцию для отдельной тестовой БД. До интеграционного запуска пользователь вводит пароль локально. Не пытаться получать его из истории терминала или настроек пользователя.
- [ ] Запустить `./gradlew.bat :server:integrationTest --tests '*DatabaseMigrationIT'`: RED на отсутствии V1, если тестовая БД доступна. Недоступную БД зафиксировать отдельно как препятствие запуска, не как RED теста миграции.
- [ ] Создать V1, конфигурацию Flyway/JPA и повторить PostgreSQL-проверки до GREEN. Не использовать H2 или пользовательскую `gamedead`.
- [ ] Проверить `./gradlew.bat build` без DB-переменных: BUILD SUCCESSFUL. Проверить `:server:dependencies --configuration runtimeClasspath`: нет libGDX/LWJGL.
- [ ] Зафиксировать изменения коммитом, если Git доступен и репозиторий подготовлен; записать команды и результаты в журнал выполнения.

## Task 2: Данные аккаунта и регистрация

**Files:**
- Create under `protocol/src/main/java/com/gamedead/protocol/auth/`: `RegisterRequest.java`, `RegisterResponse.java`.
- Create under `protocol/src/main/java/com/gamedead/protocol/account/`: `PlayerProfileResponse.java`.
- Create under `protocol/src/main/java/com/gamedead/protocol/error/`: `ApiError.java`.
- Create under `server/src/main/java/com/gamedead/server/account/`: `Account.java`, `PlayerProfile.java`, `AccountRepository.java`, `PlayerProfileRepository.java`, `AccountService.java`, `AccountInputPolicy.java`.
- Create under `server/src/main/java/com/gamedead/server/config/`: `CoreConfiguration.java` (Clock, SecureRandom, PasswordEncoder).
- Create under `server/src/main/java/com/gamedead/server/error/`: `InvalidInputException.java`, `LoginAlreadyTakenException.java`.
- Test: `server/src/test/java/com/gamedead/server/account/AccountInputPolicyTest.java`, `AccountServiceTest.java`.
- Test: `server/src/integrationTest/java/com/gamedead/server/account/AccountRegistrationIT.java`.

**Interfaces:**
- Consumes: V1 из Task 1, Clock и PasswordEncoder.
- Produces: `AccountService.register(RegisterRequest request): RegisterResponse`, `AccountService.getProfile(UUID accountId): PlayerProfileResponse`.
- `AccountInputPolicy.normalizeLogin(String): String`, `normalizeDisplayName(String): String`, `validatePassword(String): void`.
- `ApiError(String code, String message, Map<String, String> fieldErrors)`; DTO не содержат JPA/Bean Validation/Spring и не сериализуют пароль через `toString`.

- [ ] Написать parameterized `AccountInputPolicyTest`: границы логина 2/3/24/25, uppercase/пробелы, имя на кириллице и emoji, управляющие символы, null; пароль 7/8 code points и 72/73 UTF-8 байта, сохранение пробелов. Запустить RED: отсутствует policy.
- [ ] Реализовать policy, records и исключения; повторить policy-тесты до GREEN.
- [ ] Написать `AccountServiceTest`: регистрация использует нормализованные значения, хеш отличается от пароля и проверяется encoder; профиль имеет 0 XP и `mage_default`; время берётся из Clock. Запустить RED.
- [ ] Реализовать сущности, репозитории и сервис: регистрация аккаунта и профиля в одной транзакции, преобразование конфликта конкретного UNIQUE логина в доменную ошибку. Не преобразовывать любую ошибку БД в «логин занят».
- [ ] Написать и выполнить `AccountRegistrationIT`: две конкурентные регистрации нормализованного логина → один успех, один конфликт, один профиль; принудительная ошибка создания профиля откатывает аккаунт. Увидеть RED, затем исправить транзакционные границы и получить GREEN.
- [ ] Проверить `./gradlew.bat :server:test` и `./gradlew.bat :server:integrationTest --tests '*AccountRegistrationIT'`. Зафиксировать изменения и доказательства.

## Task 3: Сессии и HTTP API

**Files:**
- Create under `protocol/src/main/java/com/gamedead/protocol/auth/`: `LoginRequest.java`, `LoginResponse.java`.
- Create under `server/src/main/java/com/gamedead/server/auth/`: `AuthSession.java`, `AuthSessionRepository.java`, `TokenCodec.java`, `SessionService.java`, `AuthController.java`, `BearerAuthenticationFilter.java`, `AccountPrincipal.java`.
- Create: `server/src/main/java/com/gamedead/server/account/ProfileController.java`.
- Create: `server/src/main/java/com/gamedead/server/config/SecurityConfiguration.java`.
- Create: `server/src/main/java/com/gamedead/server/error/ApiExceptionHandler.java`, `AuthenticationFailedException.java`.
- Test under `server/src/test/java/com/gamedead/server/auth/`: `TokenCodecTest.java`, `SessionServiceTest.java`, `AuthApiTest.java`.
- Test: `server/src/integrationTest/java/com/gamedead/server/auth/AuthLifecycleIT.java`.

**Interfaces:**
- Consumes: `AccountService` и DTO Task 2, таблица сессий Task 1.
- Produces: четыре endpoint из HTTP-контракта spec.
- `TokenCodec.generate(): String`, `TokenCodec.hash(String token): String`.
- `SessionService.login(LoginRequest request): LoginResponse`, `authenticate(String token): AccountPrincipal`, `logout(UUID sessionId): void`.
- `AccountPrincipal(UUID accountId, UUID sessionId)`; профиль всегда извлекает accountId из проверенного principal.

- [ ] Написать TokenCodec и SessionService тесты до реализации: токен имеет 32 декодированных байта, хеш 64 hex-символа; expiresAt = Clock + 8 часов; в repository передаётся только хеш; неверный пароль/неизвестный логин/disabled дают одинаковую ошибку и выполняют encoder-проверку. Запустить RED.
- [ ] Реализовать генерацию SecureRandom, хеширование SHA-256, проверку BCrypt и транзакционное сохранение сессии с обновлением lastLoginAt; получить GREEN.
- [ ] Написать `AuthApiTest`: регистрация 201, вход 200, профиль без токена 401, выход 204, invalid input 400, duplicate 409; в JSON нет password/hash/sessionId. Проверить одинаковый ApiError для controller и security filter. Запустить RED.
- [ ] Реализовать контроллеры, Bearer filter, SecurityFilterChain и exception handler. Cookie/Basic/formLogin/request cache выключены; CSRF выключен для Bearer API, CORS не открыт; ответы с токеном используют Cache-Control: no-store.
- [ ] Написать `AuthLifecycleIT`: register → login → me → logout → me=401; неизвестный/expired/revoked токен и disabled=401; сохранённый токен работает после перезапуска Spring context; два аккаунта видят свои профили. Выполнить RED, исправить выявленные ошибки и получить GREEN.
- [ ] Проверить `./gradlew.bat :server:test` и `./gradlew.bat :server:integrationTest --tests '*AuthLifecycleIT'`. Зафиксировать изменения и результаты.

## Task 4: Ограничения запросов, инструкция запуска и общая проверка

**Files:**
- Create under `server/src/main/java/com/gamedead/server/auth/`: `AuthRateLimitFilter.java`, `AuthRequestSizeFilter.java`, `AuthAttemptLimiter.java`.
- Modify: `server/src/main/java/com/gamedead/server/config/SecurityConfiguration.java`.
- Modify: `server/src/main/java/com/gamedead/server/error/ApiExceptionHandler.java`.
- Create: `README.md`, `docs/local-development.md`, `scripts/start-server.ps1`.
- Test under `server/src/test/java/com/gamedead/server/auth/`: `AuthRateLimitTest.java`, `AuthRequestSizeTest.java`.
- Test: `server/src/integrationTest/java/com/gamedead/server/auth/AuthHttpLimitsIT.java`.

**Interfaces:**
- Consumes: Clock, ApiError, endpoints Task 3 и Gradle-задачи Task 1.
- Produces: лимит входа 10/мин и регистрации 5/мин на IP, 429 + Retry-After; тело >8192 байт → 413, повреждённый JSON → 400.
- `AuthAttemptLimiter.tryAcquire(String address, String operation): LimitDecision`; `LimitDecision(boolean allowed, long retryAfterSeconds)`. Окно 60 секунд, истёкшие записи удаляются, число активных ключей ограничено 10000; при исчерпании ёмкости новые ключи временно получают 429, действующие продолжают свои окна.

- [ ] Написать `AuthRateLimitTest`: 10/11 входов, 5/6 регистраций, независимость адресов и операций, сброс через 60 секунд по Clock, очистка и ограниченная ёмкость; выполнить RED.
- [ ] Реализовать потокобезопасный limiter и filter. Использовать remoteAddr; forwarded headers не считать доверенными без настройки прокси. Получить GREEN.
- [ ] Написать `AuthRequestSizeTest`: Content-Length >8192, чтение chunked тела >8192, тело ровно 8192, неполный/повреждённый JSON. Выполнить RED.
- [ ] Реализовать ограничение фактически прочитанных байтов до JSON binding, единый ApiError, 429/413/400 без отражения секретов. Не полагаться только на Content-Length. Получить GREEN.
- [ ] Выполнить `AuthHttpLimitsIT` через настоящий HTTP-port, включая chunked отправку, oversized тело, лимиты и отсутствие пароля в ошибках. До исправлений увидеть соответствующие RED, затем GREEN.
- [ ] Написать русскую инструкцию: открыть корень проекта в IDEA, выбрать JDK 21 и Gradle Wrapper; запустить `build`; создать `gamedead_test` и `game` отдельно под администратором; ввести локальные пароли. `start-server.ps1` запрашивает пароль скрыто, задаёт переменную только процессу запуска и очищает её в finally. Не меняет пользовательский PATH и постоянные переменные.
- [ ] Документировать первый запуск миграций, параметры GAMEDEAD_DB_*, отдельные GAMEDEAD_TEST_DB_* для интеграционного профиля, проверку API, ожидаемые ошибки и HTTP локально/HTTPS перед доступом через интернет. Не публиковать сервер автоматически.
- [ ] Финальные проверки: `./gradlew.bat clean build`, затем `./gradlew.bat :server:integrationTest` на тестовой БД, затем проверка runtimeClasspath и исключения секретов из файлов. BUILD SUCCESSFUL для каждого выполненного набора; недоступные проверки перечислить явно.
- [ ] Проверить изменения относительно spec отдельным обзором; исправить существенные находки через воспроизводящий тест RED→GREEN и полный прогон. Зафиксировать итоговый коммит и обновить журнал.

## Выполнение и окружение

Рекомендуемый способ: реализация основным агентом в этом чате, задачи последовательно. Это небольшой связанный серверный этап; отдельные implementer-агенты для каждой задачи не нужны. Способ выполнения подтверждается пользователем вместе с планом; код до этого не создаётся.

В папке сейчас только документ архитектуры, Git-репозитория нет. При начале реализации проверить инструкции репозитория и подготовить Git/рабочую ветку `codex/foundation`, если среда разрешает запись Git-метаданных. Не заменять эту папку внешним checkout и не переносить пользовательские файлы. Если Git недоступен, оставить файлы в разрешённой папке, вести журнал и явно указать отсутствие коммитов.

Прогресс и отклонения записывать в `docs/superpowers/plans/2026-10-15-foundation-progress.md`; при доступных штатных скриптах навыка также использовать его ledger. Не повторять завершённые задачи после смены контекста.

Если нет DB-пароля/тестовой БД, продолжать реализацию и доступные unit-проверки, не объявлять PostgreSQL-интеграцию проверенной. Конкретные команды настройки и запуска предоставить пользователю; пароль не извлекать из сторонних файлов. Обычная пользовательская БД не используется для экспериментов.

## Проверка плана

Покрыты сборка и раздельные модули, три таблицы и миграции, четыре API-операции, Unicode-границы и BCrypt, сессии и их срок, конкурентная регистрация, реальные HTTP-ограничения, защита пользовательской БД, локальная настройка секретов и инструкция запуска. Игровые требования сохранены в spec и сознательно оставлены следующему этапу. Зависимости задач: Task 2 использует V1 Task 1; Task 3 использует AccountService/DTO Task 2; Task 4 использует API/ошибки Task 3. Конфликтов интерфейсов при проверке не обнаружено.
