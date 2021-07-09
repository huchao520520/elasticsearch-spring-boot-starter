package com.lanlinker.starter.elasticsearch.repository;

import com.lanlinker.starter.elasticsearch.entiry.PageInfo;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 定义了操作Elasticsearch的CRUD的功能 <br/>
 * 泛型说明 <br/>
 * T：实体类类型
 * ID：实体类中的id类型
 *
 * @author hc
 * @date 2021/7/9 10:57
 */
public interface Repository<T, ID> {
    /**
     * 创建索引库
     *
     * @param source setting和mapping的json字符串
     * @return 是否创建成功
     */
    Boolean createIndex(String source);

    /**
     * 删除当前实体类相关的索引库
     *
     * @return 是否删除成功
     */
    Boolean deleteIndex();

    /**
     * 新增数据
     *
     * @param t 要新增的数据
     * @return 是否新增成功
     */
    boolean save(T t);

    /**
     * 批量新增
     *
     * @param iterable 要新增的数据
     * @return 是否新增成功
     */
    boolean saveAll(Iterable<T> iterable);

    /**
     * 根据id删除数据
     *
     * @param id id
     * @return 是否删除成功
     */
    boolean deleteById(ID id);

    /**
     * 异步功能，根据id查询数据
     *
     * @param id id
     * @return 包含实体类的Mono实例
     */
    Mono<T> queryById(ID id);


    /**
     * 根据{@link SearchSourceBuilder}查询数据，返回分页结果{@link PageInfo}，其中的数据已经高亮处理
     *
     * @param sourceBuilder 查询条件构建器
     * @return 结果处理器处理后的的数据
     */
    Mono<PageInfo<T>> queryBySourceBuilderForPageHighlight(SearchSourceBuilder sourceBuilder);

    /**
     * 根据指定的prefixKey对单个指定suggestField 做自动补全，返回推荐结果的列表{@link List}
     * @param suggestField 补全字段
     * @param prefixKey 关键字
     * @return 返回推荐结果列表{@link List}
     */
    Mono<List<String>> suggestBySingleField(String suggestField, String prefixKey);
}
