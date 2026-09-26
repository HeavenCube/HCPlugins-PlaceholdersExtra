package fr.noltox.hcplugins.placeholdersextra.provider.luckperms;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.matcher.NodeMatcher;
import net.luckperms.api.node.types.PermissionNode;
import net.luckperms.api.query.QueryMode;
import net.luckperms.api.query.QueryOptions;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Compte de façon asynchrone les utilisateurs auxquels un nœud de permission
 * précis est attribué directement.
 *
 * <p>Les permissions héritées de groupes et les permissions dérivées d'un
 * wildcard, d'une expression régulière ou d'un shorthand ne sont jamais prises
 * en compte.</p>
 */
final class LuckPermsPermissionCounter {

    private final LuckPerms luckPerms;
    private final Executor executor;

    LuckPermsPermissionCounter(LuckPerms luckPerms, Executor executor) {
        this.luckPerms = Objects.requireNonNull(luckPerms, "luckPerms");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    private static boolean grantsDirectPermission(
            PreparedQuery prepared,
            Collection<? extends Node> nodes
    ) {
        return nodes.stream()
                .anyMatch(node ->
                        node instanceof PermissionNode permissionNode
                                && permissionNode.getPermission().equalsIgnoreCase(
                                prepared.query().permission()
                        )
                                && permissionNode.getValue()
                                && !permissionNode.hasExpired()
                                && (!prepared.query().isContextual()
                                || prepared.queryOptions().satisfies(
                                permissionNode.getContexts()
                        )));
    }

    /**
     * Recherche les nœuds persistants correspondants dans le stockage LuckPerms,
     * puis les complète avec les nœuds propres aux utilisateurs déjà chargés.
     *
     * @param query requête normalisée
     * @return nombre futur d'utilisateurs possédant directement le nœud demandé
     */
    CompletableFuture<Integer> count(PermissionQuery query) {
        Objects.requireNonNull(query, "query");

        return CompletableFuture.supplyAsync(() -> prepare(query), executor)
                .thenCompose(prepared -> findStoredNodes(prepared)
                        .thenApplyAsync(
                                storedNodes -> countDirectPermissions(
                                        prepared,
                                        storedNodes
                                ),
                                executor
                        ));
    }

    private PreparedQuery prepare(PermissionQuery query) {
        var queryOptionsBuilder = luckPerms.getContextManager().queryOptionsBuilder(
                query.isContextual() ? QueryMode.CONTEXTUAL : QueryMode.NON_CONTEXTUAL
        );

        if (query.isContextual()) {
            var contexts = luckPerms.getContextManager()
                    .getContextSetFactory()
                    .immutableBuilder();
            query.contexts().forEach(context ->
                    contexts.add(context.key(), context.value()));
            queryOptionsBuilder.context(contexts.build());
        }

        return new PreparedQuery(query, queryOptionsBuilder.build());
    }

    private CompletableFuture<Map<UUID, Collection<Node>>> findStoredNodes(
            PreparedQuery prepared
    ) {
        var matcher = NodeMatcher.key(prepared.query().permission());
        return luckPerms.getUserManager().searchAll(matcher);
    }

    private int countDirectPermissions(
            PreparedQuery prepared,
            Map<UUID, Collection<Node>> storedNodes
    ) {
        var matchingUsers = new HashSet<UUID>();
        storedNodes.forEach((uniqueId, nodes) -> {
            if (grantsDirectPermission(prepared, nodes)) {
                matchingUsers.add(uniqueId);
            }
        });

        /*
         * Les données chargées sont la source de vérité pour ces UUID : elles
         * incluent les nœuds transitoires et les modifications pas encore
         * persistées. Elles remplacent donc le résultat issu du stockage.
         */
        for (var user : luckPerms.getUserManager().getLoadedUsers()) {
            matchingUsers.remove(user.getUniqueId());
            if (grantsDirectPermission(
                    prepared,
                    user.getNodes(NodeType.PERMISSION)
            )) {
                matchingUsers.add(user.getUniqueId());
            }
        }

        return matchingUsers.size();
    }

    private record PreparedQuery(PermissionQuery query, QueryOptions queryOptions) {
    }
}
