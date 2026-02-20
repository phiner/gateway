package phiner.de5.net.gateway.dto;

import com.dukascopy.api.IBar;

public class BarDTO {
    private final String instrument;
    private final String period;
    private final long time;
    private final double open;
    private final double close;
    private final double low;
    private final double high;

    public BarDTO(String instrument, String period, IBar bar) {
        this.instrument = instrument;
        this.period = formatPeriod(period);
        this.time = bar.getTime();
        this.open = bar.getOpen();
        this.close = bar.getClose();
        this.low = bar.getLow();
        this.high = bar.getHigh();
    }

    private String formatPeriod(String period) {
        if (period == null) return null;
        // e.g., "15 Mins" -> "15Mins" -> "15Min", "5MINS" -> "5MIN" -> "5Min" (if we want to be very precise)
        // Let's stick to simple: remove space, then remove trailing 's' or 'S' if it exists.
        String formatted = period.replace(" ", "");
        if (formatted.length() > 1 && (formatted.endsWith("s") || formatted.endsWith("S"))) {
            formatted = formatted.substring(0, formatted.length() - 1);
        }
        return formatted;
    }

    // Add getters for all fields to allow for serialization
    public String getInstrument() { return instrument; }
    public String getPeriod() { return period; }
    public long getTime() { return time; }
    public double getOpen() { return open; }
    public double getClose() { return close; }
    public double getLow() { return low; }
    public double getHigh() { return high; }
}
