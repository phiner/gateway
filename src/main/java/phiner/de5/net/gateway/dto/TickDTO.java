package phiner.de5.net.gateway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TickDTO {

    @JsonProperty("instrument")
    private String instrument;

    @JsonProperty("time")
    private long time;

    @JsonProperty("ask")
    private double ask;

    @JsonProperty("bid")
    private double bid;

    public TickDTO(String instrument, long time, double ask, double bid) {
        this.instrument = instrument;
        this.time = time;
        this.ask = ask;
        this.bid = bid;
    }

    public String getInstrument() {
        return instrument;
    }

    public long getTime() {
        return time;
    }

    public double getAsk() {
        return ask;
    }

    public double getBid() {
        return bid;
    }
}
