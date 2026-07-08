# Deploy na server — korak po korak

Vodič za podizanje aplikacije javno na internet, sa HTTPS-om, Google prijavom
i pravim slanjem emaila. Pisano za prvi put; radi se na Ubuntu serveru.

Kad završiš, saobraćaj ide ovako:

```
korisnik  →  https://tvoj-domen.me  →  Caddy (443)  →  app (8080)  →  Postgres
                                        HTTPS/SSL       Quarkus        baza
```

Sve troje (Caddy, app, baza) su Docker kontejneri koje diže jedna komanda.

---

## Šta ti treba prije početka

1. **Server (VPS)** sa javnom IP adresom i Ubuntu 24.04. Za par korisnika je
   dovoljan najmanji paket (npr. Hetzner CX22 ili DigitalOcean basic, ~4–5 €/mjesec).
2. **Domen** (npr. sa Namecheap, Porkbun, Cloudflare). Bez domena nema HTTPS-a.
3. **Google nalog** (za OAuth i za slanje emaila) — već ga imaš.
4. Oko 45 minuta.

> **Savjet:** prvo sve istestiraj lokalno (Korak 0), pa tek onda na server.
> Deploy je onda samo promjena domena.

---

## Korak 0 (preporučeno): Testiraj lokalno prije servera

Na svom računaru, u folderu projekta:

1. Kopiraj `.env.example` u `.env`.
2. U `.env` postavi **za lokalni test**:
   - `APP_BASE_URL=http://localhost:8080`
   - `COOKIE_SECURE=false`
   - `APP_BIND=0.0.0.0`
   - `GOOGLE_CLIENT_ID=...` (tvoj client id; u Google konzoli origin mora biti `http://localhost:8080`)
   - `MAILER_MOCK=false` + Gmail podaci (vidi Korak 8)
3. Pokreni: `docker compose up --build -d`
4. Otvori `http://localhost:8080`, pa probaj: registraciju email+lozinkom
   (mejl treba da stigne) i Google dugme.

Ako oba puta rade lokalno, radiće i na serveru. Zaustavi sa `docker compose down`.
**Za produkciju NE koristiš `docker-compose.caddy.yml` lokalno** (Caddy traži pravi domen).

---

## Korak 1: Napravi server

Kod provajdera (Hetzner/DigitalOcean/…) napravi VPS sa **Ubuntu 24.04**.
Dobićeš **javnu IP adresu** (npr. `203.0.113.45`) i pristup preko SSH-a.

---

## Korak 2: Uperi domen na server (DNS)

Kod registrara domena napravi **A zapis**:

| Tip | Host | Vrijednost |
|-----|------|------------|
| A   | `@`  | IP tvog servera |

Ako želiš i `www`, dodaj još jedan A zapis sa host `www` na isti IP.

Sačekaj da se proširi (obično par minuta). Provjeri sa svog računara:

```
ping tvoj-domen.me
```

Treba da vraća IP tvog servera. **Ne nastavljaj dok ovo ne radi** — Caddy neće
moći da izvadi sertifikat dok domen ne pokazuje na server.

---

## Korak 3: Poveži se na server

Sa svog računara:

```
ssh root@IP_TVOG_SERVERA
```

Sve komande od sad kucaš **na serveru**.

---

## Korak 4: Instaliraj Docker

```
curl -fsSL https://get.docker.com | sh
```

Provjeri:

```
docker --version
docker compose version
```

---

## Korak 5: Skini kod

```
apt-get update && apt-get install -y git
git clone https://github.com/abed1n/moje-finansije.git
cd moje-finansije
```

---

## Korak 6: Generiši vlastite JWT ključeve

Ključevi u repou su samo za demo i javni su na GitHubu — **moraš svoje**:

```
mkdir keys
openssl genrsa -out keys/privateKey.pem 2048
openssl rsa -in keys/privateKey.pem -pubout -out keys/publicKey.pem
```

Zatim u `docker-compose.yml` otkomentariši red za mount ključeva (skini `#`):

```
      - ./keys:/app/keys:ro
```

---

## Korak 7: Podesi Google prijavu

1. U [Google Cloud konzoli](https://console.cloud.google.com/) → **APIs & Services → Credentials**.
2. Otvori svoj OAuth client (ili napravi novi tipa **Web application**).
3. Pod **Authorised JavaScript origins** dodaj: `https://tvoj-domen.me`
   (localhost slobodno ostaje). **Redirect URIs ostavi prazno.**
4. Pod **APIs & Services → OAuth consent screen**: popuni ime aplikacije i email
   za podršku. Za par ljudi ostavi režim "Testing" i dodaj ih kao **Test users**,
   ili klikni **Publish** da svako može (za email/profil scope ne treba Google provjera).
5. Zapamti **Client ID** (treba ti u Koraku 9). Client secret ti **ne treba**.

---

## Korak 8: Podesi slanje emaila (Gmail)

1. Na Google nalogu uključi **2-Step Verification** (ako već nije).
2. Google Account → **Security → App passwords** → generiši novu.
   Dobiješ 16 karaktera — to je `MAILER_PASSWORD` (nije obična lozinka naloga).

---

## Korak 9: Napravi `.env`

```
cp .env.example .env
nano .env
```

Postavi (Ctrl+O za snimanje, Ctrl+X za izlaz):

```
POSTGRES_PASSWORD=neka-jaka-lozinka
SEED_DEMO_DATA=false

APP_DOMAIN=tvoj-domen.me
APP_BASE_URL=https://tvoj-domen.me
COOKIE_SECURE=true
APP_BIND=127.0.0.1

GOOGLE_CLIENT_ID=tvoj-client-id.apps.googleusercontent.com

MAILER_MOCK=false
MAILER_HOST=smtp.gmail.com
MAILER_PORT=587
MAILER_USERNAME=tvoj-email@gmail.com
MAILER_PASSWORD=app-password-16-karaktera
MAILER_FROM=Moje finansije <tvoj-email@gmail.com>
```

---

## Korak 10: Otvori portove (firewall)

```
ufw allow 22
ufw allow 80
ufw allow 443
ufw --force enable
```

(22 = SSH da se ne zaključaš, 80 i 443 = HTTP/HTTPS.)

---

## Korak 11: Podigni aplikaciju (sa HTTPS-om)

```
docker compose -f docker-compose.yml -f docker-compose.caddy.yml up -d --build
```

Prvi put traje par minuta (build). Caddy odmah pokušava da izvadi sertifikat.

Provjeri da su sva tri kontejnera zdrava:

```
docker compose -f docker-compose.yml -f docker-compose.caddy.yml ps
```

---

## Korak 12: Testiraj

1. Otvori `https://tvoj-domen.me` u browseru — treba katanac (HTTPS) i landing.
2. Registruj se **email + lozinkom** → treba da ti stigne mejl za potvrdu
   (provjeri i spam). Klikni link, pa se prijavi.
3. Klikni **Nastavi putem Google naloga** → prijava bez ikakvog mejla.

Ako oboje radi — gotovo je. 🎉

---

## Održavanje

**Gledanje logova:**
```
docker compose -f docker-compose.yml -f docker-compose.caddy.yml logs -f app
```

**Nova verzija koda:**
```
git pull
docker compose -f docker-compose.yml -f docker-compose.caddy.yml up -d --build
```

**Zaustavljanje / paljenje:**
```
docker compose -f docker-compose.yml -f docker-compose.caddy.yml down
docker compose -f docker-compose.yml -f docker-compose.caddy.yml up -d
```

> Napomena: pošto pokrećeš sa dva `-f` fajla, isto ponavljaj u svim komandama.
> Možeš napraviti alias da ne kucaš svaki put:
> `alias pfm='docker compose -f docker-compose.yml -f docker-compose.caddy.yml'`
> pa koristiš `pfm ps`, `pfm logs -f app`, `pfm up -d` itd.

---

## Ako nešto ne radi

**Nema HTTPS / sertifikat se ne izdaje**
- Provjeri da `ping tvoj-domen.me` vraća IP servera (DNS mora biti gotov).
- Portovi 80 i 443 moraju biti otvoreni (Korak 10) i slobodni (ništa drugo ne sme da ih koristi).
- Pogledaj Caddy logove: `docker compose -f docker-compose.yml -f docker-compose.caddy.yml logs caddy`

**Google dugme se ne pojavljuje**
- `GOOGLE_CLIENT_ID` mora biti postavljen u `.env` i kontejner ponovo podignut.
- Provjeri: `curl https://tvoj-domen.me/api/auth/config` — treba da vrati tvoj client id.

**Google prijava baca grešku o origin-u**
- U Google konzoli origin mora biti tačno `https://tvoj-domen.me` (bez kose crte na kraju).

**Mejl ne stiže**
- Provjeri spam folder.
- `MAILER_MOCK` mora biti `false`, a `MAILER_PASSWORD` mora biti Gmail **app password** (ne obična lozinka).
- Pogledaj logove app kontejnera za grešku pri slanju.
