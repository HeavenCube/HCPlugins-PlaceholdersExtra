# HCPlugins-PlaceholdersExtra

Plugin Paper propriétaire de l'expansion PlaceholderAPI `hcextra`.

**Licence :** code source consultable et contributions bienvenues, mais usage
réservé aux serveurs HeavenCube. Toute réutilisation ou distribution exige une
autorisation écrite préalable. Voir [LICENSE](LICENSE).

Cloner `HCPlugins-Core` à côté de ce dépôt, puis lancer `./gradlew build`.
La CI compile Core depuis ses sources et crée un JAR versionné à chaque build de `main`.

Le module `placeholders-api` est un contrat Java local pour les plugins contributeurs,
compilé depuis ce dépôt par Gradle composite. Aucun package Maven n'est publié.

## Liens importants

- [HCPlugins-Core](https://github.com/HeavenCube/HCPlugins-Core) : HCCore, services communs et guide de création des plugins.
- [HCPlugins-actions](https://github.com/HeavenCube/HCPlugins-actions) : workflows GitHub Actions partagés.
- [HCPack-CustomGlowing](https://github.com/HeavenCube/HCPack-CustomGlowing) : resource pack Nexo des shaders de glow custom.
- [HCPlugins-AdvancementsRedirect](https://github.com/HeavenCube/HCPlugins-AdvancementsRedirect)
- [HCPlugins-Glowing](https://github.com/HeavenCube/HCPlugins-Glowing)
- [HCPlugins-HuskHomesGUI](https://github.com/HeavenCube/HCPlugins-HuskHomesGUI)
- [HCPlugins-ItemFrame](https://github.com/HeavenCube/HCPlugins-ItemFrame)
- [HCPlugins-JoinMessage](https://github.com/HeavenCube/HCPlugins-JoinMessage)
- [HCPlugins-PlaceholdersExtra](https://github.com/HeavenCube/HCPlugins-PlaceholdersExtra)

## Maintenance et documentation technique

HCCore est obligatoire. Pour toute modification technique, commencer par [AGENTS.md](AGENTS.md),
puis [le guide du plugin](docs/TECHNICAL.md) et le Core voisin.
Le [guide commun](https://github.com/HeavenCube/HCPlugins-Core/blob/main/docs/ECOSYSTEM.md) décrit les conventions de toute la suite.
`CLAUDE.md` et `GEMINI.md` renvoient aux mêmes instructions, sans copie des règles.
Le catalogue commun `plugins/HCPlugins/translations.yml` se recharge par `/hcplugins core reload`.
