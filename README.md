# Extensão MeusAnimesBlog para Dantotsu

Extensão personalizada para assistir animes do [meusanimes.blog](https://meusanimes.blog) no Dantotsu.

## Instalação

1. Abra o Dantotsu → Configurações → Extensões
2. Toque em **Adicionar Repositório de Anime**
3. Cole a URL abaixo:

```
https://raw.githubusercontent.com/fundacaom175-netizen/extensions-meusanimes/main/repo/index.min.json
```

4. Instale a extensão **Meus Animes**

## Build local

```bash
./gradlew :extension:assembleRelease
```

## Funcionalidades

- Busca de animes
- Lista de episódios
- Player de vídeo via iframe (serv01.meusdoramas.club)
- Legendado e Dublado

## Estrutura do site

```
Site: WordPress + DooPlayer Theme
Animes: /a/{slug}/
Episódios: /e/{slug}/
Player: serv01.meusdoramas.club/#/video/{post_id}/{season}/{ep}/
Search API: /wp-json/dooplay/search/?q={query}
```
