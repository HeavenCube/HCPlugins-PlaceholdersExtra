package fr.noltox.hcplugins.placeholdersextra.provider.checkitem;

import java.util.List;

record CheckItemQuery(
        CheckItemOperation operation,
        ItemSelection selection,
        ItemCriteria criteria,
        List<InfoRequest> infoRequests,
        boolean reportAmount
) {

    CheckItemQuery {
        infoRequests = List.copyOf(infoRequests);
    }
}
