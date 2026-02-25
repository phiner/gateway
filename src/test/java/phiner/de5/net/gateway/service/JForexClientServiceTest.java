package phiner.de5.net.gateway.service;

import com.dukascopy.api.system.IClient;
import com.dukascopy.api.system.ISystemListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import phiner.de5.net.gateway.config.JForexProperties;
import phiner.de5.net.gateway.strategy.TradingStrategy;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JForexClientServiceTest {

    @Mock
    private JForexProperties jForexProperties;
    @Mock
    private TradingStrategy tradingStrategy;
    @Mock
    private RedisService redisService;
    @Mock
    private IClient client;
    @Mock
    private ScheduledExecutorService scheduler;
    @Mock
    private ScheduledFuture<?> mockFuture;

    @InjectMocks
    private JForexClientService jForexClientService;

    private ISystemListener systemListener;

    @BeforeEach
    void setUp() {
        // 显式注入 Mock，避免 @InjectMocks 行为不确定
        ReflectionTestUtils.setField(jForexClientService, "jForexProperties", jForexProperties);
        ReflectionTestUtils.setField(jForexClientService, "tradingStrategy", tradingStrategy);
        ReflectionTestUtils.setField(jForexClientService, "redisService", redisService);
        ReflectionTestUtils.setField(jForexClientService, "client", client);
        ReflectionTestUtils.setField(jForexClientService, "scheduler", scheduler);
        
        // 捕获 SystemListener
        jForexClientService.init();
        ArgumentCaptor<ISystemListener> captor = ArgumentCaptor.forClass(ISystemListener.class);
        verify(client, atLeastOnce()).setSystemListener(captor.capture());
        systemListener = captor.getValue();
    }

    @Test
    void testOnDisconnectSchedulesReconnection() {
        // 触发断开连接
        systemListener.onDisconnect();

        // 验证 15 秒延迟重连任务已调度
        verify(scheduler).schedule(any(Runnable.class), eq(15L), eq(TimeUnit.SECONDS));
        
        // 验证 3 分钟周期全连接任务已调度
        verify(scheduler).scheduleAtFixedRate(any(Runnable.class), eq(3L), eq(3L), eq(TimeUnit.MINUTES));
    }

    @Test
    void testOnConnectCancelsTasks() {
        // 先模拟断开连接以创建任务引用
        // 注意：由于 JForexClientService 内部使用了原子操作或 null 检查
        // 我们通过反射注入一个 mock Future 来模拟正在运行的任务
        ReflectionTestUtils.setField(jForexClientService, "delayedReconnectTask", mockFuture);
        ReflectionTestUtils.setField(jForexClientService, "periodicConnectTask", mockFuture);

        // 触发连接成功
        systemListener.onConnect();

        // 验证任务被取消
        verify(mockFuture, times(2)).cancel(false);
    }

    @Test
    void testPeriodicTaskNotRedundant() {
        // 模拟周期任务已经在运行
        when(mockFuture.isDone()).thenReturn(false);
        ReflectionTestUtils.setField(jForexClientService, "periodicConnectTask", mockFuture);

        // 再次触发断开连接
        systemListener.onDisconnect();

        // 验证 scheduleAtFixedRate 没有被再次调用（仅在 setUp 中或者第一次调用时触发，这里我们手动注入了）
        // 在 setUp 中 init() 调用了 setupListener 但没触发 onDisconnect
        // 所以这里应该只在本次 onDisconnect 中检查 periodicConnectTask
        verify(scheduler, never()).scheduleAtFixedRate(any(), anyLong(), anyLong(), any());
    }
}
