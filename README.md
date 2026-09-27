# HCPlugins-PlaceholdersExtra

Plugin Paper propriétaire de l'expansion PlaceholderAPI `hcextra`.

**Licence :** code source consultable et contributions bienvenues, mais usage
réservé aux serveurs HeavenCube. Toute réutilisation ou distribution exige une
autorisation écrite préalable. Voir [LICENSE](LICENSE).

Cloner `HCPlugins-Core` à côté de ce dépôt, puis lancer `./gradlew build`.
La CI compile Core depuis ses sources et crée un JAR versionné à chaque build de `main`.

Le module `placeholders-api` est un contrat Java local pour les plugins contributeurs,
compilé depuis ce dépôt par Gradle composite. Aucun package Maven n'est publié.
