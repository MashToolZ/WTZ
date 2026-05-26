package xyz.mashtoolz.wtz.enums;

import net.minecraft.client.gui.screen.ingame.HandledScreen;

public enum GUI {

    TRADE_MARKET("󏿨", MatchType.STARTS_WITH),
    ENCLOSURE("󏿭", MatchType.EQUALS);

    public enum MatchType {
        STARTS_WITH,
        EQUALS,
        CONTAINS,
        ENDS_WITH
    }

    private final String string;
    private final MatchType matchType;

    GUI(String string, MatchType matchType) {
        this.string = string;
        this.matchType = matchType;
    }

    public boolean is(String title, MatchType matchType) {
        return switch (matchType) {
            case STARTS_WITH -> title.startsWith(this.string);
            case EQUALS -> title.equals(this.string);
            case CONTAINS -> title.contains(this.string);
            case ENDS_WITH -> title.endsWith(this.string);
        };
    }

    public boolean is(String title) {
        return is(title, this.matchType);
    }

    public boolean is(HandledScreen<?> screen) {
        return is(screen.getTitle().getString(), this.matchType);
    }

    public boolean is(HandledScreen<?> screen, MatchType matchType) {
        return is(screen.getTitle().getString(), matchType);
    }
}
