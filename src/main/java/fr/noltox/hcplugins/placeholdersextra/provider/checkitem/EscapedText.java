package fr.noltox.hcplugins.placeholdersextra.provider.checkitem;

import java.util.ArrayList;
import java.util.List;

final class EscapedText {

    private EscapedText() {
    }

    static List<String> split(String input, char separator) throws CheckItemParseException {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder(input.length());
        boolean escaped = false;
        for (int index = 0; index < input.length(); index++) {
            char character = input.charAt(index);
            if (escaped) {
                if (character == separator || character == '\\') {
                    current.append(character);
                } else {
                    current.append('\\').append(character);
                }
                escaped = false;
            } else if (character == '\\') {
                escaped = true;
            } else if (character == separator) {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(character);
            }
        }
        if (escaped) {
            throw new CheckItemParseException("Séquence d'échappement incomplète.");
        }
        parts.add(current.toString());
        return parts;
    }

    static String[] splitFirst(String input, char separator) {
        int index = input.indexOf(separator);
        if (index < 0) {
            return new String[]{input, ""};
        }
        return new String[]{input.substring(0, index), input.substring(index + 1)};
    }
}
