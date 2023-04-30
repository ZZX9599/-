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
     * @return
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
