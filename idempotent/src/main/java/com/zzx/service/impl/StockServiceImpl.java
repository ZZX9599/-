package com.zzx.service.impl;

import com.zzx.anno.Idempotence;
import com.zzx.service.StockService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * @author ZZX
 * @version 1.0.0
 * @date 2023:04:29 20:11:22
 */

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
