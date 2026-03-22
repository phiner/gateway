package phiner.de5.net.gateway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ErrorDTO {

    @JsonProperty("code")
    private String code;

    @JsonProperty("message")
    private String message;

    @JsonProperty("type")
    private String type;

    @JsonProperty("timestamp")
    private long timestamp;

    @JsonProperty("orderId")
    private String orderId;

    @JsonProperty("instrument")
    private String instrument;

    @JsonProperty("label")
    private String label;

    @JsonProperty("context")
    private String context;

    public ErrorDTO() {
        this.timestamp = System.currentTimeMillis();
    }

    public ErrorDTO(String code, String message, String type) {
        this.code = code;
        this.message = message;
        this.type = type;
        this.timestamp = System.currentTimeMillis();
    }

    public ErrorDTO(String code, String message, String type, String context) {
        this.code = code;
        this.message = message;
        this.type = type;
        this.context = context;
        this.timestamp = System.currentTimeMillis();
    }

    public static ErrorDTO orderError(String code, String message, String orderId, String label) {
        ErrorDTO error = new ErrorDTO(code, message, "ORDER_ERROR");
        error.setOrderId(orderId);
        error.setLabel(label);
        return error;
    }

    public static ErrorDTO connectionError(String code, String message) {
        return new ErrorDTO(code, message, "CONNECTION_ERROR");
    }

    public static ErrorDTO validationError(String code, String message, String context) {
        return new ErrorDTO(code, message, "VALIDATION_ERROR", context);
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getInstrument() { return instrument; }
    public void setInstrument(String instrument) { this.instrument = instrument; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getContext() { return context; }
    public void setContext(String context) { this.context = context; }
}