package com.zzx.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zzx.entity.Stock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * @author ZZX
 * @version 1.0.0
 * @date 2023:04:28 10:32:08
 */

@Component
@Slf4j
public class MsgProvider {

    @Resource
    private RabbitTemplate rabbitTemplate;

    @Resource
    private ObjectMapper objectMapper;

    public void sendMsg() throws JsonProcessingException {
        // 创建扣减库存的消息
        Stock stock = new Stock();
        stock.setGoodId(1001L);
        stock.setCount(1);
        String json = objectMapper.writeValueAsString(stock);

        // 构建消息

        Message message = MessageBuilder
                .withBody(json.getBytes(StandardCharsets.UTF_8))
                .build();

        // 短时间发送多个一样的消息【模拟消息的意外重复】
        for (int i = 0; i < 5; i++) {
            CorrelationData correlationData = new CorrelationData(UUID.randomUUID().toString());
            rabbitTemplate.convertAndSend("idempotent.exchange",
                    "idempotent", message, correlationData);
        }
    }
}
