package phiner.de5.net.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrdersHistoryRequest {
    private String requestId;
    private String instrument; // Optional, if null fetch all subscribed instruments
    private long startTime;
    private long endTime; // Optional, if 0 use current time
}
