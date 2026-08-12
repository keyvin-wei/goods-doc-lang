package com.hq.goods.lang.service.impl;

import com.hq.goods.lang.service.HuaqiuService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class HuaqiuServiceImpl implements HuaqiuService {
    @Override
    public String getList(List<String> list) {
        return "OK成功";
    }
}
