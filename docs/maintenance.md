# Manutenção das extensões

Este fork publica Kaktustoon, Nexus Mangás e ToonLivre para o Mihon. A lista de
módulos publicados está em `.github/scripts/extensions.txt`; as convenções de código
estão em [CONTRIBUTING.md](../CONTRIBUTING.md).

## Identidade e publicação

- Repositório de publicação: `xHayatsy/extensions`, branch `mihon`.
- Repositório de referência: `keiyoushi/extensions-source`, remote `upstream`.
- ToonLivre: módulo `src/pt/mangalivre`, pacote
  `eu.kanade.tachiyomi.extension.pt.mangalivre`, source ID `2834885536325274328`.
- Índice instalado no Mihon:
  `https://raw.githubusercontent.com/xHayatsy/extensions/repo/index.json`.

O nome interno `mangalivre` é anterior ao nome ToonLivre. Preservar o pacote e o
source ID mantém a atualização do APK e o vínculo das obras na biblioteca.

Para validar uma alteração no ToonLivre:

```bash
./gradlew :src:pt:mangalivre:spotlessApply :src:pt:mangalivre:assembleDebug :src:pt:mangalivre:lintRelease
```

O workflow `.github/workflows/build_push.yml` publica os módulos alterados quando
há push na branch `mihon`. Ele assina os APKs com os secrets já configurados no
GitHub, cria os assets de release e atualiza os índices na branch `repo`. Depois do
push, conferir o resultado do workflow e a versão no índice; o push sozinho não
confirma a publicação. Não copiar chaves de assinatura ou credenciais para a
documentação ou a memória.

O publicador atual mantém `index.json` e `index.pb`. O antigo `index.min.json`
contém uma lista vazia e não serve para verificar as versões publicadas.

## Emulador

O SDK local fica em `$ANDROID_HOME` (`/home/pedro/Android/Sdk` nesta máquina).
O AVD usado no teste do ToonLivre é `Medium_Phone_API_37.0`, com Mihon instalado
como `app.mihon`.

```bash
"$ANDROID_HOME/emulator/emulator" -avd Medium_Phone_API_37.0 -no-audio -no-boot-anim -gpu swiftshader
adb devices -l
adb shell am start -n app.mihon/eu.kanade.tachiyomi.ui.main.MainActivity
```

O APK de debug usa assinatura diferente do APK publicado. Se o Android recusar a
substituição por incompatibilidade de assinatura, substituir somente a extensão
no emulador de testes e aprová-la em Browse → Extensions → Trust. Preservar o
aplicativo Mihon e seus dados. Ao concluir, instalar o APK assinado da publicação
para que as próximas atualizações funcionem normalmente.

## Correção do leitor do ToonLivre em novembro de 2026

A versão 1.6.91 falhava ao abrir o capítulo 15 de **Copiando Habilidades Usando
Afinidade**, com `MissingFieldException` para `pageCount` e `firstHandle`.

O `POST /api/reader/chapter/access` passou a responder com um objeto `chapter`
contendo `pages`, uma lista ordenada de URLs de imagens. O capítulo de referência
tem manga ID `obra-b0104568`, chapter ID `cap-a58a3dfa-15` e 15 imagens. A resposta
também inclui metadados de autorização que não são necessários para baixar essas
imagens públicas do CDN.

A versão 1.6.92 lê `chapter.pages` diretamente e remove o encadeamento antigo de
`firstHandle`, `nextHandle` e `/api/reader/p/{token}`. O tratamento já existente de
verificação Turnstile permanece. Não guardar respostas com tokens de acesso em
fixtures, logs permanentes ou páginas de memória.

Para repetir o cenário no Mihon, abrir ToonLivre, pesquisar `copiando`, selecionar
a obra e abrir o capítulo 15. Verificar a leitura e o download completo, além de
um capítulo vizinho. O CDN respondeu HTTP 200 com `image/webp` para as 15 imagens
durante a investigação. Requisições HEAD foram recusadas; usar GET para verificar
essas imagens.

Validação local em 24/11/2026: build debug e lint release concluídos; lint com
zero erros e um aviso de `DataExtractionRules` no manifesto gerado. No emulador,
o capítulo 15 abriu, o download terminou em CBZ com 15 imagens e `ComicInfo.xml`,
e a integridade do ZIP foi verificada. O capítulo 14 abriu com 13 páginas.

## AI Memory e instruções dos agentes

O projeto já usa AI Memory. `.ai-memory.toml` fixa o workspace `default` e o
projeto `keiyoushi-extensions-source`, preservando o histórico anterior apesar do
nome local `mihon` e do nome do fork.

`AGENTS.md` é o arquivo canônico de instruções; `CLAUDE.md` contém apenas
`@AGENTS.md`. As skills gerenciadas ficam em `.agents/skills` e `.claude/skills`.
Os hooks capturam o histórico automaticamente; notas duráveis são escritas quando
o usuário pede. Documentação técnica e procedimentos ficam neste repositório;
AI Memory permite encontrá-los em sessões futuras. Memória recuperada é evidência
histórica e precisa ser conferida contra o código e as instruções atuais.

`ai-memory doctor --json` confirmou o marcador válido, o escopo correto e a
captura de sessões de Codex e Claude sem lacunas detectadas em 24/11/2026.
