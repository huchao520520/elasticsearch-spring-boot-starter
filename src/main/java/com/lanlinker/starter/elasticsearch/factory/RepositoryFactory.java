package com.lanlinker.starter.elasticsearch.factory;

import com.lanlinker.starter.elasticsearch.repository.RepositoryHandler;
import org.elasticsearch.client.RestHighLevelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.FactoryBean;

import java.lang.reflect.Proxy;

public class RepositoryFactory<T> implements FactoryBean<T> {
	// 日志记录
    private static final Logger log = LoggerFactory.getLogger(RepositoryFactory.class);

	// 被代理的接口，就是例子中的MyRepository
    private Class<T> interfaceType;

    // elasticsearch客户端
    private RestHighLevelClient client;

    public RepositoryFactory(Class<T> interfaceType, RestHighLevelClient client) {
        log.info("RepositoryFactory init ...");
        this.interfaceType = interfaceType;
        this.client = client;
    }

    @Override
    public T getObject() throws Exception {
        log.info("RepositoryBean proxy init ...");
        // 生成动态代理对象并返回
        return (T) Proxy.newProxyInstance(interfaceType.getClassLoader(), new Class[]{interfaceType},
                new RepositoryHandler(client, interfaceType));
    }

    @Override
    public Class<?> getObjectType() {
        return interfaceType;
    }
}
