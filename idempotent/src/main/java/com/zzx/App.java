package com.zzx;

import com.zzx.provider.MsgProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import javax.annotation.Resource;

/**
 * @author ZZX
 * @version 1.0.0
 * @date 2023:04:28 10:38:51
 */

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
