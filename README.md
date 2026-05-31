# Extensions MeusAnimes

Repositorio de extensoes para o **Dantotsu** (itsmechinmoy) com foco em sites PT-BR.

## Como usar

1. Abra o Dantotsu → Configuracoes → Extensoes
2. Toque em **"Adicionar Repositorio de Anime"**
3. Cole a URL abaixo:

```
https://raw.githubusercontent.com/fundacaom175-netizen/extensions-meusanimes/main/repo/index.min.json
```

4. As extensoes aparecem na lista — e so instalar

## Extensoes disponiveis

| Extensao | Site | Audio |
|----------|------|-------|
| **Meus Animes** | meusanimes.blog | LEG + DUB |

## Sites parceiros

O site meusanimes.blog usa o mesmo sistema (DooPlayer) que estes outros sites. 
Se algum dia o site cair, tente adicionar extensoes similares dos repositorios abaixo:

| Repositorio | URL | Tipo |
|-------------|-----|------|
| **Yuzono** (recomendado) | `https://raw.githubusercontent.com/yuzono/anime-repo/repo/index.min.json` | Anime |
| **Keiyoushi** | `https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.min.json` | Manga |
| **Claudemirovsky** | `https://raw.githubusercontent.com/Claudemirovsky/cursedyomi-extensions/repo/index.min.json` | Anime PT-BR |
| **LNReader Official** | `https://raw.githubusercontent.com/LNReader/lnreader-plugins/plugins/v3.0.0/.dist/plugins.min.json` | Novel |

## Build

Se quiser compilar manualmente:

```bash
./gradlew :extension:assembleRelease
```

Requer Android SDK 34 e JDK 17.
