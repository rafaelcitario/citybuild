# Skyline Idle 🌆

City builder idle em Kotlin + Jetpack Compose, pensado para o Galaxy A55 (Android 14/15, `minSdk 26`).

## O que ele faz

- **Evolui sozinho:** o *Prefeito automático* escolhe sempre a melhor construção ou melhoria (maior retorno por moeda), espera juntar o dinheiro e substitui prédios fracos por melhores. Quando todos os prédios chegam ao nível máximo (alguns dias de jogo), ele usa *Renascer* sozinho e recomeça com bônus permanente (✨ Legado). Você também pode renascer manualmente antes.
- **Roda 24h:**
  - *Modo 24h* mantém a tela ligada.
  - Se você fechar o app, ao voltar ele simula até **24 horas** de progresso (renda e decisões do prefeito) e mostra um resumo.
  - O estado é salvo a cada 10 segundos e ao sair do app.
- **Visual:** cidade isométrica 3D com 7 tipos de prédio, ciclo de dia e noite pelo relógio do celular (sol, lua, estrelas, janelas acesas), fumaça de fábricas, pulsos neon e zoom/arraste com os dedos.
- **Progressão:** 6 níveis de cidade (Vilarejo até Distrito Neon, uns 11h para o último nível). Estimativa por simulação da economia. Mais lotes e novos prédios são liberados a cada nível. Parques dão bônus de renda global.

## Gerar o APK pelo GitHub

```bash
cd skyline
git init && git add . && git commit -m "Skyline Idle"
git branch -M main
git remote add origin https://github.com/SEU_USUARIO/skyline.git
git push -u origin main
```

Abra a aba **Actions**, espere o *Build APK* terminar (3 a 6 min) e baixe **Skyline-apk** em *Artifacts*. Para publicar uma Release com o APK, crie uma tag: `git tag v1.0 && git push origin v1.0`.

## Instalar no A55

Passe o `Skyline.apk` ao celular, toque nele e permita *instalar de fontes desconhecidas* se pedido. O APK é de debug, suficiente para uso pessoal.

## Assets 3D

Veja [ASSETS.md](ASSETS.md) para os pacotes gratuitos (CC0) e como usar modelos `.glb` no lugar do desenho procedural.

## Ajustes rápidos

- Velocidade da economia: `incomeOf`, `buildCost` e `upgradeCost` em `Game.kt`.
- Limite offline: `MAX_OFFLINE_SEC` em `Game.kt`.
- Tamanho da cidade: `GRID` em `Game.kt`.
