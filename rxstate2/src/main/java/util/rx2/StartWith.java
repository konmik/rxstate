package util.rx2;

public enum StartWith {
    /**
     * No start values will be emitted.
     */
    NO,

    /**
     * Current value will be emitted on the RxState scheduler.
     */
    SCHEDULE,

    /**
     * Current value will be emitted immediately on the subscription thread.
     * Do this only if you're subscribing already on the RxState scheduler.
     */
    IMMEDIATE
}
