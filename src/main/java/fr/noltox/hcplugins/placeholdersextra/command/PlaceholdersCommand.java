package fr.noltox.hcplugins.placeholdersextra.command;

import fr.noltox.hcplugins.core.api.command.CoreCommand;
import fr.noltox.hcplugins.core.api.message.CoreTranslations;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.List;

/**
 * Documents every placeholder exposed by the central {@code hcextra} expansion.
 */
public final class PlaceholdersCommand implements CoreCommand {

    private final CoreTranslations translations;

    public PlaceholdersCommand(CoreTranslations translations) {
        this.translations = translations;
    }

    private static final List<Component> DOCUMENTATION = List.of(
            Component.text("Placeholders hcextra disponibles (syntaxe brute) :", NamedTextColor.GOLD),
            entry(
                    "%hcextra_luckperms_count_<permission>%",
                    "Compte de façon asynchrone les joueurs ayant directement la permission exacte, "
                            + "positive et non expirée. Les groupes et wildcards sont ignorés ; cache de 30 s."
            ),
            Component.text(
                    "  Exemple : %hcextra_luckperms_count_bukkit.command.help% ; variante contextuelle : "
                            + "%hcextra_luckperms_count_<permission>:<clé>=<valeur>[,<clé>=<valeur>]%. "
                            + "Le premier calcul affiche « Calcul de la statistique… ». Nécessite LuckPerms.",
                    NamedTextColor.GRAY
            ),
            entry(
                    "%hcextra_checkitem_<modificateurs>%",
                    "Vérifie un inventaire ; préfixes amount_, getinfo:<slot>_, give_ et remove_. "
                            + "Critères principaux : mat, amt, nom, lore, enchantements, potion, main/slot, PDC et Nexo."
            ),
            Component.text(
                    "  Exemples : %hcextra_checkitem_mat:STONE,amt:2% ; "
                            + "%hcextra_checkitem_amount_nexo:custom_sword%. "
                            + "Les préfixes give/remove modifient réellement l'inventaire.",
                    NamedTextColor.GRAY
            ),
            entry(
                    "%hcextra_glow_color%",
                    "Renvoie le code couleur legacy actif du glow du joueur (par exemple &a), "
                            + "ou une chaîne vide sans glow actif. Prévu comme couleur finale du tagprefix TAB ; "
                            + "nécessite HCGlowing."
            ),
            entry(
                    "%rel_hcextra_voicechat_icon%",
                    "Placeholder relationnel viewer → target. Renvoie le sprite de parole, chuchotement, "
                            + "voice chat désactivé ou déconnexion selon l'état Simple Voice Chat ; sinon une chaîne vide. "
                            + "Nécessite Simple Voice Chat et son mod côté viewer."
            )
    );

    private static Component entry(String placeholder, String description) {
        return Component.text("- " + placeholder, NamedTextColor.AQUA)
                .append(Component.text(" — " + description, NamedTextColor.GRAY));
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        var sender = source.getSender();
        if (!sender.isOp()) {
            sender.sendMessage(translations.operatorOnly());
            return;
        }
        if (args.length != 0) {
            sender.sendMessage(Component.text("Utilisation : /hcplugins placeholders", NamedTextColor.RED));
            return;
        }
        DOCUMENTATION.forEach(sender::sendMessage);
    }
}
