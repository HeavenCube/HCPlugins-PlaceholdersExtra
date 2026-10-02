# Guide technique — HCPlaceholdersExtra

## Point d’entrée

Ce dépôt appartient à la suite privée d’usage HeavenCube, publiée comme source consultable.
Il dépend obligatoirement de HCCore. Lire d’abord [AGENTS.md](../AGENTS.md), puis le Core voisin.
Le [guide commun](https://github.com/HeavenCube/HCPlugins-Core/blob/main/docs/ECOSYSTEM.md) décrit les règles Java/Paper, les contrats Core,
le packaging et la CI. Ce guide local décrit les particularités à préserver ; le code reste l’autorité.

## Dépendances et compilation

HCCore et PlaceholderAPI obligatoires ; LuckPerms, Nexo et Simple Voice Chat facultatifs.

Cloner Core à côté. Le sous-projet placeholders-api est embarqué non relocalisé dans le JAR Shadow propriétaire ; les plugins contributeurs le compilent en composite + compileOnly.

```powershell
.\gradlew.bat build
```

Utiliser JDK 25. Sous Linux : `./gradlew build`. Le JAR est dans `build/libs/` ; installer aussi
les plugins serveur requis. Un clone Core modifié affecte le classpath local ; noter son commit.
Après extension d’API commune, construire Core séparément et installer sa version compatible en premier.

## Commandes et permissions

`/hcplugins placeholders` : documentation brute, opérateurs. Expansion unique `hcextra`. Fournisseurs : luckperms_count, checkitem et voicechat ; HCGlowing contribue glow_color via placeholders-api.

La branche canonique est `/hcplugins placeholders` ; elle est enregistrée chez Core, pas comme
une deuxième racine. Les refus opérateur utilisent `HCPluginsCore.translations(plugin)`.
Ce plugin n'expose pas de commande reload de configuration.

## Fichiers et données

Aucun fichier de configuration administrateur propre actuellement. Les messages communs viennent du catalogue Core. Les caches/snapshots internes ne sont pas une base persistante de joueurs.

Aucun import automatique des anciens dossiers du monorepo. Messages communs dans
`plugins/HCPlugins/translations.yml` ; documentation des placeholders locale. Le catalogue
partagé se recharge avec `/hcplugins core reload`.

## Chemin d’exécution

HCPlaceholdersExtra publie PlaceholderProviderRegistry via ServicesManager et enregistre HCExtraExpansion (`hcextra`). ProviderRegistry associe les fournisseurs et retire ceux dont le propriétaire se désactive. Les intégrations optionnelles démarrent/s’arrêtent avec les plugins correspondants. Les requêtes standard et relationnelles ont des contextes différents.

## Carte du code pour une modification

| Fichier | Responsabilité et points à préserver |
| --- | --- |
| [HCPlaceholdersExtra.java](../src/main/java/fr/noltox/hcplugins/placeholdersextra/HCPlaceholdersExtra.java) | Services, expansion, intégrations et enable/disable des dépendances. |
| [HCExtraExpansion.java](../src/main/java/fr/noltox/hcplugins/placeholdersextra/HCExtraExpansion.java) | Identifiant hcextra et dispatch normal/relationnel. |
| [ProviderRegistry.java](../src/main/java/fr/noltox/hcplugins/placeholdersextra/ProviderRegistry.java) | Ownership, inscription thread serveur, fermeture idempotente. |
| [HCPlaceholders.java](../placeholders-api/src/main/java/fr/noltox/hcplugins/placeholdersextra/api/HCPlaceholders.java) | Lookup du service par les contributeurs. |
| [PlaceholderProvider.java](../placeholders-api/src/main/java/fr/noltox/hcplugins/placeholdersextra/api/PlaceholderProvider.java) | Contrat normal et relationnel pour un fournisseur. |
| [AsyncPermissionCountCache.java](../src/main/java/fr/noltox/hcplugins/placeholdersextra/provider/luckperms/AsyncPermissionCountCache.java) | Calcul/expiration/coalescence asynchrones du compteur. |
| [LuckPermsPermissionCounter.java](../src/main/java/fr/noltox/hcplugins/placeholdersextra/provider/luckperms/LuckPermsPermissionCounter.java) | Sémantique des permissions comptées et contextes. |
| [CheckItemProvider.java](../src/main/java/fr/noltox/hcplugins/placeholdersextra/provider/checkitem/CheckItemProvider.java) | Refuse les appels hors thread principal et joueurs hors ligne avant accès inventaire. |
| [CheckItemService.java](../src/main/java/fr/noltox/hcplugins/placeholdersextra/provider/checkitem/CheckItemService.java) | Opérations vérification/lecture/give/remove sur inventaire. |
| [CheckItemParser.java](../src/main/java/fr/noltox/hcplugins/placeholdersextra/provider/checkitem/CheckItemParser.java) | Grammaire et erreurs ; conserver les échappements. |
| [SimpleVoiceChatBridge.java](../src/main/java/fr/noltox/hcplugins/placeholdersextra/provider/voicechat/SimpleVoiceChatBridge.java) | Callbacks voice chat et état d’activité. |
| [NexoApiItemBridge.java](../src/main/java/fr/noltox/hcplugins/placeholdersextra/provider/checkitem/NexoApiItemBridge.java) | API Nexo publique facultative. |
| [PaperPdcItemBridge.java](../src/main/java/fr/noltox/hcplugins/placeholdersextra/provider/checkitem/PaperPdcItemBridge.java) | PersistentDataContainer PaperMC et données d'item. |

`src/main/resources/paper-plugin.yml` définit identité, dépendances et permissions serveur.
`settings.gradle.kts` définit les builds composites ; `build.gradle.kts` le packaging.
`.github/workflows/build.yml` appelle les actions partagées à `@main` ; `.github/dependabot.yml`
maintient les dépendances. Une mise à jour de dépendance doit conserver ces contrats.

## Invariants et zones à risque

- Plugin serveur `HCPlaceholdersExtra`, HCCore obligatoire ; module `placeholders`.
- HCCore et PlaceholderAPI obligatoires ; LuckPerms, Nexo et Simple Voice Chat facultatifs.
- Ne pas créer une deuxième expansion HeavenCube : contribuer au registre existant.
- Les fournisseurs peuvent être invoqués hors thread serveur. CheckItemProvider renvoie null dans ce cas : préserver ce contrôle avant accès/mutation inventaire, sans attente bloquante sur le scheduler.
- Conserver compte LuckPerms de permissions directes, positives, exactes, non expirées ; ne pas transformer en comptage de groupes/wildcards.
- CheckItem give/remove ont des effets réels : préserver quantité, slots, critères, échappements et traitement des erreurs.
- Le placeholder relationnel voix distingue viewer/target ; ne pas inverser les joueurs.
- Fermer handles/providers/intégrations et supprimer l’état d’un plugin désactivé ; conserver le contrat public non relocalisé.

Avant une nouvelle logique transversale : chercher les usages dans Core et les autres plugins ;
ajouter au Core le contrat partagé réellement nécessaire avant le raccordement local. Ne pas recopier
un loader YAML, un registre de commandes ou un catalogue de traductions. Garder les événements et
états spécifiques ici. Thread serveur pour le jeu ; considérer callbacks et APIs tierces selon leur
thread réel, puis revalider le contexte avant mutation.

## Validation et limites

Tests existants de cache LuckPerms, parser/matcher CheckItem et bridge voice chat. En jeu : contextes LuckPerms, variantes checkitem, give/remove/surplus, Nexo absent/présent, NBT, viewer/target distincts, voicechat et disable/re-enable d’un contributeur.

Les tests de parser n’établissent pas la sécurité des mutations d’inventaire sur un serveur réel. Documenter le comportement sans chaque intégration facultative.

Documentation seule : vérifier les liens locaux et le diff. Modification runtime : build, tests ciblés,
et scénario serveur correspondant. Rapporter seulement ce qui a été exécuté, avec résultat et limite.
Pour transfert entre IA, donner le commit Core testé et les fichiers/changements encore non committés.
