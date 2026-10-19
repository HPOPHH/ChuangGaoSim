# CGSim

[![GitHub stars](https://img.shields.io/github/stars/HPOPHH/ChuangGaoSim?style=social)](https://github.com/HPOPHH/ChuangGaoSim/stargazers)

An open-source Android workout workbench for **Chuanggao Sports (cgapp)**. It implements the app's network protocol (login, request signing, workout data upload) as a standalone app, giving you a fully **manual, controllable** way to generate and submit workout records,and it is avaliable for nk/zd/cd.

[中文说明](README.zh-CN.md)

> ⚠️ **Disclaimer**
> Authorized use only.This project is for **educational and research purposes only** (protocol analysis, mobile security study). Follow your institution's sports assessment rules and applicable laws. Any consequences of using this project are borne by the user!This project does not guarantee or abuse liability!

## Features

- **SSO login**: complete your school's CAS unified authentication in a WebView; the app captures `jwtToken` automatically and splits it into `jwt` + `secret`
- **Dual login** (requires ROOT): sync credentials directly from the official Cg app's private storage
- **Manual workbench**:
  - Get SportId → wait for the allowed workout window → generate `UploadJsonSports` → submit → upload workout photo
  - **Custom distance & route** — three built-in campus tracks (standard 400 m geometry) plus a custom route-ID field
  - **Track generator**: a true 400 m track (two straights + two semicircles); each lap is normalized to exactly 400 m with per-lap inner/outer lane drift, true-normal low-frequency sway and GPS-grade noise
  - Modal confirmations with server response details at every step
- **Workout records**: history list with photo thumbnails (fetched from the server), record details
- **Photo upload**: multipart contract identical to the official app (`uploadpic`)

## Technical Details

### Request signing chain

Every request is signed by calling the bundled native library through JNI (`ChingoEncrypt.cgapiEnrypt(secret, url)`):

1. **Sign source** — the URL path starting at `/api` (or `/app`) plus query parameters sorted with a `TreeMap` (POST form fields included the same way)
2. **`cgapiEnrypt(secret, signSource)`** returns a `|`-separated string; field `[3]` becomes the `sign` header, field `[1]` becomes the `timestamp` header
3. The `secret` originates from the CAS callback page (`jwtToken` split by `.` — parts 0–2 are the jwt, part 3 is the secret, part 4 the logout time)

### Request headers

| Header | Source |
|---|---|
| `sign` | native output `split[3]` |
| `timestamp` | native output `split[1]` |
| `cgAuthorization` | jwt (parts 0–2 of jwtToken) |
| `imei` | `UUID(androidId.hashCode(), hash << 32)` — stable per login cycle |
| `v1` | MD5 of a per-install random value |
| `client` | `{"root":?,"heap":?,"t":ms}` AES-ECB encrypted with the first 16 bytes of `secret`, then Base64 |
| `User-Agent` | randomized `cgapp/2.9.x (...)` |

### Upload payload contract (`UploadJsonSports`)

Field-for-field aligned with the official app — including the traps:

- `sportImages` is a **flag** at submit time (`"1"`), later replaced server-side by the uploaded file path (`uploads/sportimages/<term>/<xh>/<uuid>.jpg`)
- `randomPointStr` must be a **JSON array or absent** — sending a string crashes the server (500)
- `jsonsports` POST field is AES-ECB encrypted (key = first 16 bytes of `secret`)
- `alreadyPassPoint` / `needPassPointCount` / per-minute speed series / per-km pace — all reproduced

### Photo upload

`POST api/l/v6/uploadpic` as `multipart/form-data`: one part per image named after the file (`application/octet-stream`), plus `xh` / `sportId` / `images` form parts (image names comma-terminated). Order: **submit the record first** (`savesports`), then upload the photo — the server binds the file to the existing record and rewrites `sportImages` itself.

## Build
If you want to use the gps data from Jinnan Main Stadium&Balitai Stadium, please send an email to **ny4tzbpe@anonaddy.me** Come and pay to obtain!（very cheap,only 6RMB）
Requires JDK 17 and Android SDK 35.

```bash
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

For release signing, copy `keystore.properties.example` to `keystore.properties` and fill in your own keystore. Pushing a `v*` tag triggers the GitHub Actions build workflow.

## well-done apk app
If you do not know how to use source code and want a well-done apk, email me(ny4tzbpe@anonaddy.me).(You need to pay,but it's cheap,only 6RMB)
## School configuration

Server and CAS endpoints are currently hardcoded:

- `app/src/main/java/com/hpophh/chuanggaosim/http.java` — `BASE_URL`
- `app/src/main/java/com/hpophh/chuanggaosim/login.java` — `CALLBACK_PREFIX` / `LOGIN_URL`
- `app/src/main/res/xml/network_security_config.xml`

Adjust these for your own institution.

## Project layout

```
ChuangGaoSim/
├── app/src/main/java/com/hpophh/chuanggaosim/   # app source
│   ├── cg/                                # data models aligned with the official app
│   └── net/crigh/...                      # JNI bridge to the signing library
├── app/src/main/assets/web/               # WebView front-end (workbench / records / settings)
├── app/src/main/jniLibs/arm64-v8a/        # signing .so (arm64)
└── .github/workflows/                     # tag-triggered CI build
```

## Credits

Based on ideas and partial implementation from a developer that refused to mention his name — thanks to the original author.
Also thanks to various open-source tools and the glm-5.3-flash model.
## Star History

<a href="https://www.star-history.com/?repos=hpophh%2FChuangGaoSim&type=date&legend=top-left">
 <picture>
   <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/chart?repos=hpophh/ChuangGaoSim&type=date&theme=dark&legend=top-left" />
   <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/chart?repos=hpophh/ChuangGaoSim&type=date&legend=top-left" />
   <img alt="Star History Chart" src="https://api.star-history.com/chart?repos=hpophh/ChuangGaoSim&type=date&legend=top-left" />
 </picture>
</a>

## License

[MIT](LICENSE)

---
