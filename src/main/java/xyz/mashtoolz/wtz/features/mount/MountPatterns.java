package xyz.mashtoolz.wtz.features.mount;

import java.util.regex.Pattern;

public class MountPatterns {

    private static final String STAT_NAMES = "Speed|Acceleration|Altitude|Jump Height|Energy|Handling|Toughness|Boost|Training";

    public static final Pattern STAT = Pattern.compile(
            "(" + STAT_NAMES + ")\\D*(\\d+)/(\\d+)(?: \\((\\d+)\\))?"
    );

    public static final Pattern POTENTIAL = Pattern.compile(
            "(\\d+(?:[.,]\\d+)?[kK]?)\\s*Potential"
    );

    public static final Pattern ENERGY = Pattern.compile("Energy (\\d+)/(\\d+)");

    public static final Pattern STATUS = Pattern.compile(
            "(Ready to Feed|Ready to Breed|Feeding in (.+)|Breeding in (.+))"
    );

    public static final Pattern PROFESSION_LEVEL = Pattern.compile("(\\w+) Level\\D*(\\d+)");

    public static final Pattern TIMER = Pattern.compile(
            "(?:(\\d+)h)?\\s*(?:(\\d+)m)?\\s*(?:(\\d+)s)?"
    );
}
