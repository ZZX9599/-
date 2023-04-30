package com.zzx.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zzx.anno.Idempotence;
import com.zzx.entity.Stock;
import com.zzx.service.StockService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.client.RestTemplate;
import sun.net.www.http.HttpClient;

import javax.annotation.Resource;
import java.io.IOException;
import java.net.URL;

/**
 * @author ZZX
 * @version 1.0.0
 * @date 2023:04:28 10:29:56
 */

@Slf4j
@Configuration
public class MqConfig {

    @Resource
    private ObjectMapper objectMapper;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue("idempotent.queue"),
            exchange = @Exchange("idempotent.exchange"),
            key = "idempotent"
    ))
    public void printMsg01(Message message) throws IOException {
        byte[] body = message.getBody();
        String json = new String(body);
        Stock stock = objectMapper.readValue(json, Stock.class);

        // 调用接口执行方法
        RestTemplate restTemplate = new RestTemplate();

        String url = "http://localhost:8080/stock/del";

        // 参数一:URL
        // 参数二:请求体
        // 参数三:返回值类型
        String result = restTemplate.postForObject(url, stock, String.class);
        log.info("消息消费的结果:{}",result);
    }
}
