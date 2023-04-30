package com.zzx.service;

/**
 * @author ZZX
 * @version 1.0.0
 * @date 2023:04:29 20:08:03
 */

public interface StockService {
    /**
     * 扣减库存
     * @param goodId
     * @param num
     * @return
     */
    boolean delStock(Long goodId,Integer num);
}
