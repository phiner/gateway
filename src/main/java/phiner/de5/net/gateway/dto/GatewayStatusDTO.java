package phiner.de5.net.gateway.dto;

public class GatewayStatusDTO {
    private final String service = "gateway";
    private final String status;
    private final long timestamp;
    private final String message;

    public GatewayStatusDTO(String status, String message) {
        this.status = status;
        this.message = message;
        this.timestamp = System.currentTimeMillis();
    }

    public String getService() {
        return service;
    }

    public String getStatus() {
        return status;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getMessage() {
        return message;
    }
}
