package phiner.de5.net.gateway.request;

public class InstrumentInfoRequest {
    private String requestId;
    private String instrument;

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getInstrument() {
        return instrument;
    }

    public void setInstrument(String instrument) {
        this.instrument = instrument;
    }
}
