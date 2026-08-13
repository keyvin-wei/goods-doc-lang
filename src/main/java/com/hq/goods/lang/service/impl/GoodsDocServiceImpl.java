package com.hq.goods.lang.service.impl;

import com.hq.goods.lang.service.GoodsDocService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GoodsDocServiceImpl implements GoodsDocService {
    @Override
    public String getList(List<String> list) {
        return "OK成功";
    }
}
