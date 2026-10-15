# Локальная разработка

## Основная база

База `gamedead` и схема `game` уже подготовлены. Пароль `gamedead_app` остаётся на твоём компьютере. Приложение не получает пароль администратора PostgreSQL.

| Переменная | Значение по умолчанию |
|---|---|
| GAMEDEAD_DB_URL | jdbc:postgresql://localhost:5432/gamedead |
| GAMEDEAD_DB_USERNAME | gamedead_app |
| GAMEDEAD_DB_PASSWORD | Обязательна, встроенного пароля нет |
| GAMEDEAD_BIND_ADDRESS | 127.0.0.1 |
| GAMEDEAD_PORT | 8080 |

Удобнее запускать `scripts/start-server.ps1`: пароль запрашивается без отображения и не записывается в файл. Не добавляй его в Git и не присылай в чат.

Flyway применяет версии миграций по очереди. Hibernate проверяет схему, не меняет её автоматически. Не редактируй уже применённую миграцию: добавляй следующую. Никакой команды очистки пользовательской базы для запуска не требуется.

## Отдельная тестовая БД

В SQL Shell под администратором выполни отдельной командой:

```sql
CREATE DATABASE gamedead_test OWNER gamedead_app ENCODING 'UTF8' TEMPLATE template0;
```

Затем:

```text
\connect gamedead_test postgres
```

```sql
CREATE SCHEMA game AUTHORIZATION gamedead_app;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
```

Используй ту же роль приложения. Интеграционные тесты добавляют данные в `gamedead_test`, поэтому в неё не следует класть ценные данные. Они отказываются работать с основной `gamedead`.

В отдельном окне PowerShell:

```powershell
$env:GAMEDEAD_TEST_DB_URL = 'jdbc:postgresql://localhost:5432/gamedead_test'
$env:GAMEDEAD_TEST_DB_USERNAME = 'gamedead_app'
$testPassword = Read-Host 'Пароль PostgreSQL' -AsSecureString
$env:GAMEDEAD_TEST_DB_PASSWORD = [System.Net.NetworkCredential]::new('', $testPassword).Password
try {
    .\gradlew.bat :server:integrationTest
} finally {
    Remove-Item Env:\GAMEDEAD_TEST_DB_PASSWORD -ErrorAction SilentlyContinue
}
```

Отсутствующая БД или неправильный пароль приводят к ошибке проверки, а не к пропуску тестов. Настоящие секреты не нужны для обычной сборки и unit-тестов.

## HTTP API

Все маршруты имеют префикс `/api/v1`.

| Метод и маршрут | Назначение |
|---|---|
| POST /auth/register | Регистрация: login, displayName, password |
| POST /auth/login | Вход: login, password |
| GET /me | Собственный профиль, нужен Bearer |
| POST /auth/logout | Отзыв текущего сеанса, нужен Bearer |

Регистрация не выполняет автоматический вход. Логин — 3–24 латинских буквы, цифры или подчёркивания; регистр не важен. Отображаемое имя — 3–24 символа, допускает русский. Пароль — от 8 символов и не более 72 байт UTF-8, пробелы сохраняются.

Пример в PowerShell после запуска сервера:

```powershell
$baseUrl = 'http://127.0.0.1:8080/api/v1'
$gamePassword = Read-Host 'Новый пароль игрового аккаунта' -AsSecureString
$plainGamePassword = [System.Net.NetworkCredential]::new('', $gamePassword).Password
$registration = @{
    login = 'mage_one'
    displayName = 'Первый маг'
    password = $plainGamePassword
} | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "$baseUrl/auth/register" -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($registration))
$credentials = @{ login = 'mage_one'; password = $plainGamePassword } | ConvertTo-Json
$session = Invoke-RestMethod -Method Post -Uri "$baseUrl/auth/login" -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($credentials))
$headers = @{ Authorization = "Bearer $($session.accessToken)" }
Invoke-RestMethod -Uri "$baseUrl/me" -Headers $headers
Invoke-RestMethod -Method Post -Uri "$baseUrl/auth/logout" -Headers $headers
Remove-Variable plainGamePassword, registration, credentials, session, headers
```

Сессионный токен — временный секрет. Не публикуй его; срок — 8 часов. После выхода он недействителен. Игровой пароль не связан с паролем PostgreSQL.

Ошибки: 400 неверные данные/JSON, 401 нет действующей сессии или неверный вход, 409 логин занят, 413 тело больше 8 KiB, 429 превышен лимит. Для одного IP разрешено 10 входов и 5 регистраций за минуту. `Retry-After` указывает время ожидания.

## Диагностика

- Нет Java: выбери JDK 21 в IDEA или задай JAVA_HOME в текущем PowerShell.
- Первая сборка не скачивает Gradle/зависимости: проверь интернет и настройки прокси Java/Gradle. Не отключай проверку сертификатов.
- Connection refused: проверь, запущен ли PostgreSQL и правильный ли порт.
- Authentication failed: проверь пароль роли `gamedead_app`; это не игровой пароль.
- Schema game does not exist: создай схему в нужной базе, как указано выше.
- Flyway checksum mismatch: не удаляй таблицы; восстанови исходную применённую миграцию и внеси изменение следующей версией.
- Port already in use: задай GAMEDEAD_PORT другим свободным портом.

