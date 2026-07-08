# Personal Finance Manager

[![CI](https://github.com/abed1n/moje-finansije/actions/workflows/ci.yml/badge.svg)](https://github.com/abed1n/moje-finansije/actions/workflows/ci.yml)

Aplikacija za upravljanje licnim finansijama radjena u Quarkusu (Java 25) sa PostgreSQL bazom.
Frontend je obican HTML/CSS/JS (bez build koraka) i servira ga sam Quarkus, tako da se sve
pokrece kao jedna aplikacija.

## Sta aplikacija ima

- registracija i prijava (JWT token, lozinke se cuvaju kao BCrypt hash), uloge USER i ADMIN
- racuni (tekuci, stednja, gotovina, kartica) sa detaljima banke, stanje se samo azurira
- transferi izmedju racuna (ne ulaze u prihode/rashode, sa istorijom i ponistavanjem)
- racuni u stranim valutama se u ukupnom stanju preracunavaju u EUR po ECB kursu
- transakcije: prihodi i rashodi, kategorije, tagovi, filteri i pretraga, sortiranje i paginacija
- uvoz bankovnog izvoda (CSV) sa automatskom kategorizacijom koja uci iz korisnikovih ispravki,
  prepoznavanjem duplikata i pregledom prije potvrde
- ponavljajuce transakcije: mjesecna pravila (kirija, plata...) koja scheduler automatski upisuje
- prilozi uz transakcije (upload racuna/faktura vec pri unosu, preuzimanje, brisanje)
- CSV izvoz transakcija (postuje filtere, spreman za Excel)
- kategorije po korisniku, default set se kreira pri registraciji
- budzeti (mjesecni/godisnji) po kategorijama sa procentom potrosenog
- ciljevi stednje sa napretkom, rokom i uplatama
- upozorenja u aplikaciji kad budzet predje 80% limita (zvonce u sidebaru)
- dashboard: ukupno stanje, prihodi/rashodi, tok novca, rashodi po kategorijama,
  period selektor (mjesec / 3 mjeseca / godina) i drill-down po kategoriji
- tamna tema (prekidac u sidebaru, pamti se po korisniku)
- mobilni raspored sa donjom navigacijom
- PWA: instalira se kao aplikacija na telefon, radi i bez mreze
- alati preko vanjskih API-ja: konverter valuta, lokacija i vrijeme po IP adresi (sa istorijom)
- admin pregled svih korisnika
- scheduler: ponavljajuca pravila + prijava racuna u minusu
- health check na /q/health i Swagger UI na /q/swagger-ui

## Pokretanje kroz Docker

```shell
docker compose up --build
```

Aplikacija je na http://localhost:8080. Pri prvom pokretanju se ubace demo podaci:

- demo korisnik: demo@pfm.me / demo123
- admin: admin@pfm.me / admin123

Za produkciju kopirati `.env.example` u `.env`, promijeniti `POSTGRES_PASSWORD` i staviti
`SEED_DEMO_DATA=false`. Obavezno generisati i vlastite JWT kljuceve (oni u repou su samo za demo):

```shell
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out keys/privateKey.pem
openssl rsa -pubout -in keys/privateKey.pem -out keys/publicKey.pem
```

pa u `docker-compose.yml` otkomentarisati mount `./keys:/app/keys:ro` i varijable
`JWT_PRIVATE_KEY_LOCATION` / `JWT_PUBLIC_KEY_LOCATION` - nije potreban rebuild aplikacije.

## Pokretanje u dev modu

Treba lokalni PostgreSQL (baza `pfm_db`, user `pfm_user`, lozinka `admin123`, port 5432):

```shell
./mvnw quarkus:dev
```

Dev mod koristi drop-and-create semu i ubacuje demo podatke pri svakom startu.

## Migracije baze

U produkciji semu kreira i verzionise Flyway (`src/main/resources/db/migration`),
a Hibernate je pri startu samo validira. Za izmjenu seme dodati novu migraciju
(`V2__opis.sql`, `V3__...`) - Flyway je automatski primjenjuje pri startu.
Dev i test profil i dalje koriste drop-and-create pa se tu migracije ne izvrsavaju.

## Testovi

```shell
./mvnw test
```

Testovi koriste H2 u memoriji pa ne treba ni Docker ni PostgreSQL.

## API

Svi endpointi osim register/login traze `Authorization: Bearer <token>` header.

- `POST /api/auth/register`, `POST /api/auth/login` - vracaju token i podatke o korisniku
- `GET /api/auth/me`, `PUT /api/auth/profile`, `POST /api/auth/change-password`
- `GET|POST /api/accounts`, `GET|PUT|DELETE /api/accounts/{id}`
- `GET|POST /api/transactions`, `PUT|DELETE /api/transactions/{id}` - filteri: accountId,
  categoryId, type, from, to, search, limit
- `POST /api/transactions/{id}/attachments` (multipart), `GET|DELETE /api/transactions/attachments/{id}`
- `GET|POST /api/categories`, `PUT|DELETE /api/categories/{id}`
- `GET|POST /api/budgets`, `PUT|DELETE /api/budgets/{id}`
- `GET|POST /api/recurring`, `PUT /api/recurring/{id}/toggle`, `DELETE /api/recurring/{id}`
- `GET|POST /api/transfers`, `DELETE /api/transfers/{id}`
- `GET|POST /api/goals`, `POST /api/goals/{id}/deposit`, `DELETE /api/goals/{id}`
- `POST /api/import/preview` (multipart CSV), `POST /api/import/confirm`
- `GET /api/dashboard?months=1|3|12`
- `GET /api/tools/currency?from=EUR&to=USD&value=100`, `GET /api/tools/location`,
  `GET /api/tools/timezone` + `/history` varijante
- `GET /api/admin/users` (samo ADMIN)

Kompletna OpenAPI specifikacija je na `/q/openapi`.

## Struktura koda

- `me.fit.model` - JPA entiteti
- `me.fit.dto` - request/response klase (records) sa validacijom
- `me.fit.service` - poslovna logika, transakcije i provjere vlasnistva
- `me.fit.resource` - REST endpointi
- `me.fit.security` - generisanje JWT tokena i ucitavanje trenutnog korisnika
- `me.fit.rest.client` - REST klijenti za vanjske API-je
- `me.fit.schedulers` - periodicni poslovi
- `me.fit.exception` - exception mapperi
- `me.fit.startup` - demo data seeder
- `src/main/resources/META-INF/resources` - frontend (index.html, css, js)

## Konfiguracija za produkciju

| Varijabla | Opis | Default |
|---|---|---|
| DB_URL | JDBC url baze | jdbc:postgresql://localhost:5432/pfm_db |
| DB_USER | korisnik baze | pfm_user |
| DB_PASSWORD | lozinka baze | **obavezna** (nema default) |
| SEED_DEMO_DATA | demo podaci pri prvom startu | false |
| UPLOADS_DIR | folder za priloge | uploads |
| JWT_PRIVATE_KEY_LOCATION | putanja do privatnog kljuca za potpis tokena | jwt/privateKey.pem (demo) |
| JWT_PUBLIC_KEY_LOCATION | putanja do javnog kljuca za verifikaciju | jwt/publicKey.pem (demo) |

Aplikacija se izlaze javno iskljucivo iza reverse proxyja (nginx/Caddy/Traefik)
koji terminira HTTPS.
