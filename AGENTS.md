# HCPlugins-PlaceholdersExtra

- Java 25, Paper 26.2, Gradle Kotlin DSL.
- HCCore owns `/hcplugins`; contribute `placeholders` through the Core API.
- The small `placeholders-api` module is a source dependency for contributors; do not publish it to Maven.
- Keep `hcextra` the sole HeavenCube PlaceholderAPI expansion.
- Preserve provider lifecycle, CheckItem operations, and optional LuckPerms, Nexo and voice chat integrations.
- Run `./gradlew build` before finalizing Java or Gradle changes.
- Do not commit, push, reset, rebase, stash, or change branches without explicit user authorization.
