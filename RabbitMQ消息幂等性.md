# RabbitMQ消息幂等性

# 1:介绍

消息消费时的幂等性（消息不被重复消费）

同一个消息，第一次接收，正常处理业务，如果该消息第二次再接收，那就不能再处理业务，否则就处理重复了

幂等性是：对于一个资源，不管你请求一次还是请求多次，对该资源本身造成的影响应该是相同的

不能因为重复的请求而对该资源重复造成影响



以接口幂等性举例：

接口幂等性是指：一个接口用同样的参数反复调用，不会造成业务错误，那么这个接口就是具有幂等性的

# 2:实现思路

我们可以根据这次请求，来拿到有关这次请求的一些信息，从而根据这些信息来构建一个唯一的标识ID

如果是同样的请求，那么生成的ID是一样的，这样就可以判断是不是相同的请求了

![image-20230429192816658]( https://zzx-note.oss-cn-beijing.aliyuncs.com/rabbitmq/image-20230429192816658.png)

注意点：在短时间内的两次请求所生成的这个唯一标识是一样的，才可判断是两次重复的请求，时间长了就不算了

这个需要根据自己的业务来决定，有的业务也许永远都不会存在两个相同的请求【看具体业务】



比如某个用户下单的时候因为网络或者其它的意外，MQ内部同时来了两个订单，并且订单号都一样

本质上这仅仅是一个订单，所以在落库的时候只需要处理一个订单即可，另外的消息不需要处理

这个订单 ID 不可能重复，则如果我们仅仅根据订单的 ID 来构建这个标识的话

无论时间多长，已经处理过的订单就不应该再处理，这个就跟时间没有关系了



另外一个业务：假设用户支付之后，需要扣减库存，因为网络或者其它的意外，MQ内部同时来了两个扣减库存的

消息，这个时候我们本质应该只处理一个扣减库存的操作，参数有商品 ID 和 扣减的数量

如果这个时候我们根据用户 ID 和 商品 ID 和 扣减的数量来生成这个唯一的标识

这个时候两个消息构建这个标识的话肯定是一样的，短时间内确实是无效的

但是第二天，又是这个用户，又是买的这个商品，它计算出来的标识还是一样的

但是这个时候属于有效的操作了，这个就跟时间有关系了



所以：具体看业务的情况，以及我们构建这个唯一标识所涉及的参数来综合考虑

# 3:实现技术

我们可以使用AOP的方式来实现，假设我们拿到消息之后需要执行的业务方法为 doAdd() 方法

则我们可以这样做：

![image-20230429193647780]( https://zzx-note.oss-cn-beijing.aliyuncs.com/rabbitmq/image-20230429193647780.png)

我们可以自定义一个注解，这个注解整合切面的操作，达到添加一个注解就含有切面的功能

# 5:添加幂等性注解

```java
@Target(value = ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface Idempotence {
}
```

# 6:获取IP工具类

```java
@Slf4j
public class IPUtil {

    /**
     * 获取IP地址
     * <p>
     * 使用Nginx等反向代理软件， 则不能通过request.getRemoteAddr()获取IP地址
     * 如果使用了多级反向代理的话，X-Forwarded-For的值并不止一个，而是一串IP地址
     * X-Forwarded-For中第一个非unknown的有效IP字符串，则为真实IP地址
     */
    public static String getIpAddr(HttpServletRequest request) {
        String ip = null;
        try {
            ip = request.getHeader("x-forwarded-for");
            if (StringUtils.isEmpty(ip) || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("Proxy-Client-IP");
            }
            if (StringUtils.isEmpty(ip) || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("WL-Proxy-Client-IP");
            }
            if (StringUtils.isEmpty(ip) || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("HTTP_CLIENT_IP");
            }
            if (StringUtils.isEmpty(ip) || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("HTTP_X_FORWARDED_FOR");
            }
            if (StringUtils.isEmpty(ip) || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getRemoteAddr();
            }
        } catch (Exception e) {
            log.error("IPUtil ERROR ", e);
        }

        //使用代理，则获取第一个IP地址
        ip = ip.split(",")[0].trim();
        return ip;
    }
}
```

# 7:为注解添加环绕通知

```java
package com.zzx.aspect;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zzx.utils.IPUtil;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import javax.annotation.Resource;
import javax.jws.Oneway;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.time.Duration;

/**
 * @author ZZX
 * @version 1.0.0
 * @date 2023:04:29 20:14:29
 */

@Slf4j
@Aspect
@Component
public class IdempotenceAspect {

    @Resource
    private ObjectMapper objectMapper;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 幂等性接口的环绕通知
     * 核心:根据请求的各种参数来构建一个唯一的标识
     * 1:拿到IP
     * 2:拿到方法的全权限名
     * 3:拿到某些参数值转化为字符串
     * 4:拿到登陆用户的身份ID
     * 5:组合成为一个唯一的标记
     * 6:判断Redis的setnx是否执行成功
     * 7:根据执行的结果来判断是否执行目标方法
     *
     * @param proceedingJoinPoint
     * @return true代表不是重复消息，false代表一秒内请求了多次
     */
    @Around(value = "@annotation(com.zzx.anno.Idempotence)")
    public Object idempotenceAround(ProceedingJoinPoint proceedingJoinPoint) throws Throwable {
        // 拿到请求对象
        ServletRequestAttributes servletRequestAttributes = (ServletRequestAttributes)
                RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = servletRequestAttributes.getRequest();

        // TODO 拿到IP
        String ip = IPUtil.getIpAddr(request);

        // TODO 拿到方法的全限定名称
        // 拿到请求方法的签名
        MethodSignature methodSignature = (MethodSignature) proceedingJoinPoint.getSignature();
        // 拿到方法
        Method method = methodSignature.getMethod();
        // 拿到方法的名称
        String methodName = method.getName();
        // 拿到这个方法所属的类
        Class<?> methodDeclaringClass = method.getDeclaringClass();
        // 拿到这个方法所属的类的名称
        String typeName = methodDeclaringClass.getTypeName();
        // 拼接全限定方法名
        String fullMethodName = typeName + "." + methodName;

        // TODO 拿到参数转化为字符串
        Object[] args = proceedingJoinPoint.getArgs();
        // 转化为JSON字符串
        String argsJson = objectMapper.writeValueAsString(args);

        // TODO 拿到身份的标识【这里就随便写，整合到项目记得改】
        Long userId = 1001L;

        // TODO 组合成唯一的标记
        String key = ip + "-" + fullMethodName + "-" + argsJson + "-" + userId;

        // TODO 存入Redis
        // 这里的值随便存，我这里存了个字符串ok
        // 这里的过期时间比较讲究，根据业务决定，比如我这里就是设置了一秒内不允许处理完全相同的两个请求

        Boolean result = stringRedisTemplate
                .opsForValue()
                .setIfAbsent("idempotence:" + key, "ok", Duration.ofSeconds(1));

        if (result) {
            // 表明是有效的消息，可以执行原始方法并返回
            return proceedingJoinPoint.proceed();
        }

        // 表明是重复的消息，在一秒内触发了多次
        log.error("消息重复了");

        return false;
    }
}
```

下面我们就可以使用我们这个幂等性注解来测试好不好用了

# 8:实践操作

## 8.1:统一结果类

```java
@Data
public class HttpResult {
    private Boolean success;
    private Integer code;
    private String message;
    private Map<String, Object> data = new HashMap<>();

    /**
     * 成功，缺乏数据
     *
     * @return
     */
    public static HttpResult ok() {
        HttpResult httpResult = new HttpResult();
        httpResult.setSuccess(HttpResultEnum.SUCCESS.getSuccess());
        httpResult.setCode(HttpResultEnum.SUCCESS.getCode());
        httpResult.setMessage(HttpResultEnum.SUCCESS.getMessage());
        return httpResult;
    }

    /**
     * 失败，缺乏数据
     *
     * @return
     */
    public static HttpResult error() {
        HttpResult httpResult = new HttpResult();
        httpResult.setSuccess(HttpResultEnum.FAIL.getSuccess());
        httpResult.setCode(HttpResultEnum.FAIL.getCode());
        httpResult.setMessage(HttpResultEnum.FAIL.getMessage());
        return httpResult;
    }

    /**
     * 设置泛型，缺乏数据
     *
     * @param httpResultEnum
     * @return
     */
    public static HttpResult setResult(HttpResultEnum httpResultEnum) {
        HttpResult httpResult = new HttpResult();
        httpResult.setSuccess(httpResultEnum.getSuccess());
        httpResult.setCode(httpResultEnum.getCode());
        httpResult.setMessage(httpResultEnum.getMessage());
        return httpResult;
    }

    /**
     * 设置成功标志位
     *
     * @return
     */
    public HttpResult success() {
        this.setSuccess(HttpResultEnum.SUCCESS.getSuccess());
        return this;
    }

    /**
     * 设置失败标志位
     *
     * @return
     */
    public HttpResult fail() {
        this.setSuccess(HttpResultEnum.FAIL.getSuccess());
        return this;
    }

    /**
     * 添加单个键值对数据
     *
     * @param key
     * @param value
     * @return
     */
    public HttpResult data(String key, Object value) {
        this.data.put(key, value);
        return this;
    }

    /**
     * 添加集合数据
     *
     * @param map
     * @return
     */
    public HttpResult data(Map<String, Object> map) {
        this.setData(map);
        return this;
    }
}
```

## 8.2:结果枚举类

```java
public enum HttpResultEnum {
    SUCCESS(true, 200, "成功"),
    FAIL(false, 2000, "失败");

    private Boolean success;
    private Integer code;
    private String message;

    HttpResultEnum(Boolean success, Integer code, String message) {
        this.success = success;
        this.code = code;
        this.message = message;
    }

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
```

可以根据自己的业务来添加枚举

## 8.3:Controller

```java
@RestController
@RequestMapping("/stock")
public class StockController {

    @Resource
    private StockService stockService;

    @PostMapping("/del")
    public HttpResult delStock(@RequestBody Stock stock) {
        boolean flag = stockService.delStock(stock.getGoodId(), stock.getCount());
        if (flag) {
            return HttpResult.ok();
        }
        return HttpResult.error();
    }
}
```

## 8.4:Service和实现

```java
public interface StockService {
    
    /**
     * 扣减库存
     * @param goodId
     * @param num
     * @return
     */
    boolean delStock(Long goodId,Integer num);
}
```

```java
@Slf4j
@Service
public class StockServiceImpl implements StockService {

    @Override
    @Idempotence
    public boolean delStock(Long goodId, Integer num) {
        log.info("删除库存号为{}的商品,删除数量{}",goodId,num);
        return true;
    }
}
```

实现类加上了我们的幂等性注解

## 8.5:配置文件

```yml
spring:
  rabbitmq:
    host: 192.168.101.66
    port: 5672
    username: ZZX
    password: JXLZZX79
    virtual-host: zzx

  redis:
    host: 192.168.101.66
    port: 6379
```

## 8.6:消息发送

```java
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

        // 短时间发送多个相同的消息【模拟消息的意外重复】
        for (int i = 0; i < 5; i++) {
            CorrelationData correlationData = new CorrelationData(UUID.randomUUID().toString());
            rabbitTemplate.convertAndSend("idempotent.exchange",
                    "idempotent", message, correlationData);
        }
    }
}
```



## 8.7:交换机和队列

```java
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
```

我就不配置消息的可靠性了，具体可以翻看我的这篇文章

地址：http://101.42.247.138:8090/archives/rabbitmqxiao-xi-ke-kao-xing-xiang-jie

上面的代码就是配置了一个交换机和队列，绑定了一个消费者，消费者就是发送HTTP请求去执行扣减库存的操作

## 8.8:主启动类

```java
@SpringBootApplication
public class App implements ApplicationRunner{

    @Resource
    private MsgProvider msgProvider;

    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        msgProvider.sendMsg();
    }
}
```

主启动类实现了 ApplicationRunner 接口，则项目启动就直接发送消息到MQ

## 8.9:启动测试

项目启动就发送了五条消息到 MQ，查看控制台日志：

![image-20230430164758854]( https://zzx-note.oss-cn-beijing.aliyuncs.com/rabbitmq/image-20230430164758854.png)

# 9:总结

还是这张图，原理要明白

![image-20230429192816658]( https://zzx-note.oss-cn-beijing.aliyuncs.com/rabbitmq/image-20230429192816658.png)

其实最重要的就是一个设计思想，代码并不复杂，只不过我们需要理解其中的思想，我们可以根据这次请求，来拿

到有关这次请求的一些信息，从而根据这些信息来构建一个唯一的标识 ID

如果是同样的请求，那么生成的ID是一样的，这样就可以判断短期内是不是相同的请求了

那么这个标识怎么存放呢，我们当然是放入 Redis，因为可能项目是集群

正好利用 Redis 的 setnx 操作来保证消息的唯一性

我们应该设置一个有效的TTL，根据业务而定，这个TTL的设计时长就很有考究了，主要看业务

然后知道了思路怎么做呢？最重要的要能够想到使用 AOP，在业务执行之前判断消息是不是短期重复的

如果单纯使用AOP的切面表达式，不太灵活

所以我们可以自定义一个注解，这个注解整合切面的操作，达到添加一个注解就含有切面的功能

这样使用起来也非常的方便，效果也非常的好



如果对自定义注解AOP实现共性需求不太了解的话，可以看我的这篇文章

地址：http://101.42.247.138:8090/archives/zi-ding-yi-zhu-jie-jie-he-aopshi-xian-gong-xing-xu-qiu