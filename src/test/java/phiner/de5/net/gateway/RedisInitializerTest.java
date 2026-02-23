package phiner.de5.net.gateway;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import phiner.de5.net.gateway.config.ForexProperties;
import phiner.de5.net.gateway.service.RedisService;

import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RedisInitializerTest {

    @Mock
    private RedisService redisService;

    @Mock
    private ForexProperties forexProperties;

    @InjectMocks
    private RedisInitializer redisInitializer;

    @Test
    public void testRun_Success() {
        List<String> instruments = List.of("EUR/USD");
        List<String> periods = List.of("DAILY");

        when(forexProperties.getInstruments()).thenReturn(instruments);
        when(forexProperties.getPeriods()).thenReturn(periods);

        redisInitializer.run(null);

        verify(redisService).saveConfigInstruments(instruments);
        verify(redisService).saveConfigPeriods(periods);
    }

    @Test
    public void testRun_NullConfig() {
        when(forexProperties.getInstruments()).thenReturn(null);
        when(forexProperties.getPeriods()).thenReturn(null);

        redisInitializer.run(null);

        verify(redisService, never()).saveConfigInstruments(any());
        verify(redisService, never()).saveConfigPeriods(any());
    }
}
