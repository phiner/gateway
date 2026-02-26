package phiner.de5.net.gateway.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonDeserialize(using = PositionsRequestDeserializer.class)
public class PositionsRequest {
    private String requestId;
}
