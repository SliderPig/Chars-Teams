package com.example.charteams.model;

// What a team name may look like and which colours there are. No Minecraft in here.
public final class TeamRules {
    private TeamRules() {}

    public static final int MIN_NAME = 2, MAX_NAME = 24;
    public static final int MAX_INVITES_PER_PLAYER = 5;
    public static final long ASK_AGAIN_MS = 60_000L;

    public static final String[] COLOUR_NAMES = {"pink", "red", "orange", "yellow", "green", "aqua", "blue", "purple"};
    public static final int[] COLOUR_RGB = {0xFF88C8, 0xFF5A5A, 0xFFAA33, 0xFFE55C, 0x62DE6A, 0x5CE1E6, 0x6A8CFF, 0xB77CFF};

    // The name to use, tidied up, or null if it isn't allowed. Letters in any language, numbers, single spaces and a little punctuation.
    public static String cleanName(String raw) {
        if (raw == null) return null;
        StringBuilder out = new StringBuilder();
        boolean space = false;
        for (int i = 0; i < raw.length(); ) {
            int c = raw.codePointAt(i);
            i += Character.charCount(c);
            if (c == ' ' || c == '\t') {
                space = out.length() > 0;
                continue;
            }
            if (!Character.isLetterOrDigit(c) && "_-'!&.+".indexOf(c) < 0) return null;
            if (space) out.append(' ');
            space = false;
            out.appendCodePoint(c);
        }
        int length = out.codePointCount(0, out.length());
        return length < MIN_NAME || length > MAX_NAME ? null : out.toString();
    }

    public static boolean validColour(int colour) {
        return colour >= 0 && colour < COLOUR_NAMES.length;
    }

    // -1 when there's no such colour. "Colour" is often typed with its American spelling, and both purples get the same one.
    public static int colourNamed(String name) {
        if (name == null) return -1;
        for (int i = 0; i < COLOUR_NAMES.length; i++) {
            if (COLOUR_NAMES[i].equalsIgnoreCase(name.trim())) return i;
        }
        return -1;
    }
}
