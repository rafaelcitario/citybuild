# Pacotes 3D gratuitos para o Skyline Idle

O jogo atual **não usa arquivos externos**: os prédios são desenhados em código (isométrico 3D procedural, com luz, dia/noite e animações). Abaixo estão os pacotes gratuitos que encontrei para trocar por modelos 3D reais mais tarde.

## Pacotes encontrados

| Pacote | Licença | Observações |
|---|---|---|
| [Kenney City Kit (Commercial)](https://kenney.nl/assets/city-kit-commercial) | CC0 (confirmado na página) | Prédios comerciais e arranha-céus |
| [Kenney City Kit (Industrial)](https://kenney.nl/assets/city-kit-industrial) | Kenney publica em CC0 (confira na página) | Fábricas, chaminés |
| [Kenney City Kit (Suburban)](https://kenney-assets.itch.io/city-kit-suburban) | Kenney publica em CC0 (confira na página) | Casas |
| [Kenney City Kit (Roads)](https://kenney.nl/assets/city-kit-roads) | Kenney publica em CC0 (confira na página) | Ruas e cruzamentos |
| [Quaternius Downtown City MegaKit](https://quaternius.com/packs/downtowncitymegakit.html) | CC0 (confirmado na página) | Mais de 300 modelos, formatos OBJ, FBX e glTF. Boa parte é gratuita via itch.io |
| [Quaternius Lowpoly Modular Street](https://quaternius.itch.io/lowpoly-modular-street) | Confira na página | Peças modulares de rua |

Dica: CC0 permite uso pessoal e comercial sem atribuição.

## Como trocar o desenho procedural por modelos reais

1. Baixe os `.glb` (ou converta de FBX/OBJ) e coloque em `app/src/main/assets/models/`.
2. Use um renderizador glTF. A opção mais direta é o [SceneView](https://github.com/sceneview/sceneview) (Apache 2.0, baseado em Filament). A página do projeto informa versão 4.65.0, minSdk 24, e pede AGP 9.4+, Kotlin 2.4 e JVM 21, então o `build.gradle.kts` e o workflow (JDK 21) precisam ser atualizados junto.
3. Mapeie cada tipo de prédio (`TYPES` em `Game.kt`) para um modelo e troque `building()` em `MainActivity.kt` por um `ModelNode`.

Como o repositório do GitHub Actions tem internet, também dá para baixar os pacotes durante o build com um passo `curl` no workflow, sem versionar os binários.
