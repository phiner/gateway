package phiner.de5.net.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InstrumentInfoDTO {
    private String name;
    private String currency;
    private double pip;
    private double point;
    private String description;
}
