package com.lanlinker.starter.elasticsearch.annotaions;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author hc
 * @date 2021/7/9 11:12
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Index {

    /**
     * 索引库名称，必填
     * @return 索引库名称
     */
    String value();
}
