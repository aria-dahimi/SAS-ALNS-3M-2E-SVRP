package search;

/**
 * Maps each neighborhood operator to its fixed search index.
 * The index order is kept for compatibility with existing experiments and weights.
 */
public enum NeighborhoodOperator {
    REMOVE_MEETING(0, "REMOVE_MEETING"),
    CHANGE_MEETING(1, "CHANGE_MEETING"),
    COMBINE_MEETINGS(2, "COMBINE_MEETINGS"),
    CHANGE_MEETING_PARKING(3, "CHANGE_MEETING_PARKING"),
    REMOVE_MEETING_BEST(4, "REMOVE_MEETING_BEST"),
    CHANGE_MEETING_BEST(5, "CHANGE_MEETING_BEST"),
    COMBINE_MEETINGS_BEST(6, "COMBINE_MEETINGS_BEST"),
    TRANSFER_CUSTOMER_BEST(7, "TRANSFER_CUSTOMER_BEST"),
    REMOVE_MEETING_NEW(8, "REMOVE_MEETING_NEW"),
    CHANGE_MEETING_NEW(9, "CHANGE_MEETING_NEW"),
    COMBINE_MEETINGS_NEW(10, "COMBINE_MEETINGS_NEW"),
    TRANSFER_CUSTOMER_NEW(11, "TRANSFER_CUSTOMER_NEW"),
    TRANSFER_CUSTOMER(12, "TRANSFER_CUSTOMER"),
    EXCHANGE_CUSTOMERS(13, "EXCHANGE_CUSTOMERS"),
    EXCHANGE_MEETING_CUSTOMERS(14, "EXCHANGE_MEETING_CUSTOMERS"),
    TRANSFER_CUSTOMER_KEEP_ROUTE(15, "TRANSFER_CUSTOMER_KEEP_ROUTE"),
    EXCHANGE_CUSTOMERS_KEEP_ROUTE(16, "EXCHANGE_CUSTOMERS_KEEP_ROUTE"),
    EXCHANGE_MEETING_CUSTOMERS_KEEP_ROUTE(17, "EXCHANGE_MEETING_CUSTOMERS_KEEP_ROUTE");

    private final int index;
    private final String reportName;

    NeighborhoodOperator(int index, String reportName) {
        this.index = index;
        this.reportName = reportName;
    }

    public int index() {
        return index;
    }

    public String reportName() {
        return reportName;
    }

    public static NeighborhoodOperator fromIndex(int index) {
        for (NeighborhoodOperator operator : values()) {
            if (operator.index == index) {
                return operator;
            }
        }
        throw new IllegalArgumentException("Unknown neighborhood operator index: " + index);
    }
}
