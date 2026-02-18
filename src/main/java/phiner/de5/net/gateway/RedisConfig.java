package phiner.de5.net.gateway;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.lang.NonNull;
import phiner.de5.net.gateway.listener.*;

@Configuration
public class RedisConfig {

    @Bean
    public RedisMessageListenerContainer redisContainer(
            @NonNull RedisConnectionFactory connectionFactory,
            @NonNull InstrumentInfoRequestListener instrumentInfoRequestListener,
            @NonNull OrderOpenListener orderOpenListener,
            @NonNull OrderCloseListener orderCloseListener,
            @NonNull OrderSubmitListener orderSubmitListener,
            @NonNull OrderModifyListener orderModifyListener,
            @NonNull OrderCancelListener orderCancelListener
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(instrumentInfoRequestListener, new ChannelTopic("system:request:instrument_info"));
        container.addMessageListener(orderOpenListener, new ChannelTopic("order:open"));
        container.addMessageListener(orderCloseListener, new ChannelTopic("order:close"));
        container.addMessageListener(orderSubmitListener, new ChannelTopic("order:submit"));
        container.addMessageListener(orderModifyListener, new ChannelTopic("order:modify"));
        container.addMessageListener(orderCancelListener, new ChannelTopic("order:cancel"));
        return container;
    }

    @Bean
    @Qualifier("redisTemplateString")
    public RedisTemplate<String, String> redisTemplateString(@NonNull RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        return template;
    }

    @Bean
    @Qualifier("redisTemplateBytes")
    public RedisTemplate<String, byte[]> redisTemplateBytes(@NonNull RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, byte[]> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Use StringRedisSerializer for keys, as they are strings.
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        // Use the public static factory method RedisSerializer.byteArray() to obtain
        // the package-private ByteArrayRedisSerializer instance.
        template.setValueSerializer(RedisSerializer.byteArray());
        template.setHashValueSerializer(RedisSerializer.byteArray());

        return template;
    }
}
