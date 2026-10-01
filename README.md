# Magpie Podcast (Android)

App Android nativo para ouvir a [Magpie Podcast](https://membros.magpiepodcast.com.br/) com **retomada de minutagem** local (o site web perde a posição).

## Funcionalidades

- Login via WebView no site oficial (Google / e-mail + captcha) — captura o JWT em `localStorage.token` ou na URL `/auth/google/callback?token=...`
- Lista de episódios (`GET /episodes`) com miniatura, título, data e duração
- Player: play, pause, stop, −15s, +30s, seek
- Histórico local com posição salva a cada segundo + pause/stop
- Continuar de onde parou (lista e aba Histórico)

## Toolchain (igual ao `dex-media-switcher`)

| Componente | Versão |
|------------|--------|
| AGP | 8.1.4 |
| Gradle | 8.0 |
| Kotlin | 1.9.22 |
| compileSdk / targetSdk | 34 |
| Media3 | 1.4.1 |

## Como rodar

Precisa de **JDK 17** (Gradle 8.0 não roda em Java 25). Exemplo:

```bash
export JAVA_HOME=/opt/android-studio/jbr
./gradlew :app:assembleDebug
```

Ou abra a pasta `magpie-podcast` no Android Studio → Sync → Run ▶ `app`.

APK debug: `app/build/outputs/apk/debug/app-debug.apk`

## Testes e cobertura

```bash
export JAVA_HOME=/opt/android-studio/jbr
./gradlew :app:testDebugUnitTest :app:createDebugUnitTestCoverageReport
```

Relatório: `app/build/reports/coverage/test/debug/index.html`

## Segurança

- O token **não** fica hardcoded — só após login no WebView
- Se você colou um Bearer token em chat/logs, considere fazer logout/login de novo no site

## Nota sobre Google no WebView

Se o Google bloquear o login embutido (`disallowed_useragent`), use e-mail + senha + captcha no mesmo WebView, ou abra o fluxo Google normalmente (o app já usa UA de Chrome).
