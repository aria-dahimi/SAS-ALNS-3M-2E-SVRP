package config;

/** Scheduling engine used to evaluate complete candidate solutions. */
public enum SchedulingMode {
    APPROXIMATE,
    EXACT;

    public static SchedulingMode fromConfig(String value) {
        try {
            return SchedulingMode.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new IllegalArgumentException(
                    "Unsupported schedulingMode='" + value
                    + "'. Expected APPROXIMATE or EXACT.");
        }
    }
}
