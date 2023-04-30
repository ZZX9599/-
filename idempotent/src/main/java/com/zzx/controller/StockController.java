package com.zzx.controller;

import com.zzx.entity.Stock;
import com.zzx.service.StockService;
import com.zzx.utils.HttpResult;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * @author ZZX
 * @version 1.0.0
 * @date 2023:04:29 20:08:33
 */

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
