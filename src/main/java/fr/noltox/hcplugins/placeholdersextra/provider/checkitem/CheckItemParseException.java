package fr.noltox.hcplugins.placeholdersextra.provider.checkitem;

final class CheckItemParseException extends Exception {

    private static final long serialVersionUID = 1L;

    CheckItemParseException(String message) {
        super(message);
    }

    CheckItemParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
