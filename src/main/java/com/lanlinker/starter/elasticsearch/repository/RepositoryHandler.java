package com.lanlinker.starter.elasticsearch.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lanlinker.starter.elasticsearch.annotaions.Id;
import com.lanlinker.starter.elasticsearch.annotaions.Index;
import com.lanlinker.starter.elasticsearch.entiry.PageInfo;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.CreateIndexResponse;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.suggest.SuggestBuilder;
import org.elasticsearch.search.suggest.SuggestBuilders;
import org.elasticsearch.search.suggest.completion.CompletionSuggestion;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * @author hc
 * @date 2021/7/9 10:57
 */
public class RepositoryHandler<T, ID> implements Repository<T, ID>, InvocationHandler {

    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * Elasticsearch的客户端
     */
    private final RestHighLevelClient client;

    /**
     * id字段名称
     */
    private String id;

    /**
     * id字段
     */
    private Field idField;

    /**
     * 索引库名称
     */
    private String indexName;

    /**
     * T对应的字节码
     */
    private final Class<T> clazz;
    /**
     * ID对应的字节码
     */
    private final Class<ID> idType;

    public RepositoryHandler(RestHighLevelClient client, Class<?> repositoryInterface){
        this.client = client;
        this.indexName = indexName;
        // 参数的接口应该是这样的：interface MyRepository extends Repository<IndexData, Long>
        // 反射获取接口声明的泛型
        ParameterizedType parameterizedType = (ParameterizedType) repositoryInterface.getGenericInterfaces()[0];
        // 获取泛型对应的真实类型,这里有2个，<IndexData, Long>
        Type[] actualType = parameterizedType.getActualTypeArguments();
        // 我们取数组的第一个，肯定是T的类型，即实体类类型
        this.clazz = (Class<T>) actualType[0];
        // 我们取数组的第一个，肯定是ID的类型，即ID的类型
        this.idType = (Class<ID>) actualType[1];

        // 利用反射获取注解
        if (clazz.isAnnotationPresent(Index.class)) {
            // 获取@Index注解
            Index indices = clazz.getAnnotation(Index.class);
            // 获取索引库及类型名称
            indexName = indices.value();
        } else {
            // 没有注解，我们用类名称首字母小写，作为索引库名称
            String simpleName = clazz.getSimpleName();
            indexName = simpleName.substring(0, 1).toLowerCase() + simpleName.substring(1);
        }


        // 获取带有@Id注解的字段：
        // 获取所有字段
        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) {
            field.setAccessible(true);
            // 判断是否包含@Id注解
            if (field.isAnnotationPresent(Id.class)) {
                id = field.getName();
                idField = field;
            }
        }
        // 没有发现包含@Id的字段，抛出异常
        if (StringUtils.isBlank(id)) {
            // 没有找到id字段，则抛出异常
            throw new RuntimeException("实体类中必须有一个字段标记@IndexID注解。");
        }
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // object 方法，走原生方法
        if (Object.class.equals(method.getDeclaringClass())) {
            return method.invoke(this,args);
        }
        // 其它走本地代理
        return method.invoke(this, args);
    }

    @Override
    public Boolean createIndex(String source) {
        try {
            // 发起请求，准备创建索引库
            CreateIndexResponse response = client.indices().create(
                    new CreateIndexRequest(indexName).source(source, XContentType.JSON),
                    RequestOptions.DEFAULT);
            // 返回执行结果
            return response.isAcknowledged();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Boolean deleteIndex() {
        try {
            // 发起请求，删除索引库
            AcknowledgedResponse response = client.indices()
                    .delete(new DeleteIndexRequest(indexName), RequestOptions.DEFAULT);
            // 返回执行结果
            return response.isAcknowledged();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean save(T t) {
        try {
            // 从对象中获取id
            String id = getID(t);
            // 把对象转为JSON
            String json = toJson(t);
            // 准备请求
            IndexRequest request = new IndexRequest(indexName)
                    .id(id)
                    .source(json, XContentType.JSON);
            // 发出请求
            IndexResponse response = client.index(request, RequestOptions.DEFAULT);
            // 判断是否有失败
            return response.getShardInfo().getFailed() == 0;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean saveAll(Iterable<T> iterable) {
        // 创建批处理请求
        BulkRequest request = new BulkRequest();
        // 遍历要处理的文档集合，然后创建成IndexRequest，逐个添加到BulkRequest中
        iterable.forEach(t -> request.add(new IndexRequest(indexName).id(getID(t)).source(toJson(t), XContentType.JSON)));
        try {
            // 发送批处理请求
            BulkResponse bulkResponse = client.bulk(request, RequestOptions.DEFAULT);
            // 判断结果
            if(bulkResponse.status() != RestStatus.OK){
                return false;
            }
            if(bulkResponse.hasFailures()){
                throw new RuntimeException(bulkResponse.buildFailureMessage());
            }
            return true;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean deleteById(ID id) {
        try {
            // 准备请求
            DeleteRequest request = new DeleteRequest(indexName, id.toString());
            // 发出请求
            DeleteResponse response = client.delete(request, RequestOptions.DEFAULT);
            // 判断是否有失败
            return response.getShardInfo().getFailed() == 0;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Mono<T> queryById(ID id) {
        // 通过Mono.create函数来构建一个Mono，sink用来发布查询到的数据或失败结果
        return Mono.create(sink -> {
            // 开启异步查询
            client.getAsync(
                    new GetRequest(indexName, id.toString()),
                    RequestOptions.DEFAULT,
                    // 异步回调
                    new ActionListener<GetResponse>() {
                        @Override
                        public void onResponse(GetResponse response) {
                            // 判断查询是否成功
                            if (!response.isExists()) {
                                // 不成功则返回错误
                                sink.error(new RuntimeException("文档不存在！"));
                            }
                            // 成功时的回调，
                            sink.success(fromJson(response.getSourceAsString()));
                        }

                        @Override
                        public void onFailure(Exception e) {
                            // 失败时的回调
                            sink.error(e);
                        }
                    });
        });
    }

    @Override
    public Mono<PageInfo<T>> queryBySourceBuilderForPageHighlight(SearchSourceBuilder sourceBuilder) {
        return Mono.create(sink -> {
            // 准备搜索请求，并接受用户提交的查询参数
            SearchRequest request = new SearchRequest(indexName).source(sourceBuilder);
            // 发送异步请求
            client.searchAsync(request, RequestOptions.DEFAULT, new ActionListener<SearchResponse>() {
                @Override
                public void onResponse(SearchResponse response) {
                    // 成功的回调函数
                    if (response.status() != RestStatus.OK) {
                        sink.error(new RuntimeException("查询失败"));
                    }
                    // 处理返回结果
                    // 获取命中的结果
                    SearchHits searchHits = response.getHits();
                    // 总条数
                    long total = searchHits.getTotalHits().value;
                    // 数据
                    SearchHit[] hits = searchHits.getHits();
                    // 处理数据
                    List<T> list = new ArrayList<>(hits.length);
                    for (SearchHit hit : hits) {
                        T t = null;
                        try {
                            // 把查询到的json反序列化为T类型
                            t = mapper.readValue(hit.getSourceAsString(), clazz);
                        } catch (IOException e) {
                            sink.error(e);
                        }
                        list.add(t);
                        // 获取高亮结果的集合
                        Map<String, HighlightField> highlightFields = hit.getHighlightFields();
                        // 判断是否有高亮
                        if (!CollectionUtils.isEmpty(highlightFields)) {
                            // 遍历高亮字段
                            for (HighlightField highlightField : highlightFields.values()) {
                                // 获取字段名称
                                String fieldName = highlightField.getName();
                                // 获取高亮值
                                String value = StringUtils.join(highlightField.getFragments());
                                try {
                                    // 把高亮值注入 t 中
                                    BeanUtils.setProperty(t, fieldName, value);
                                } catch (Exception e) {
                                    sink.error(e);
                                }
                            }
                        }
                    }
                    // 发布分页结果
                    sink.success(new PageInfo<>(total, list));
                }

                @Override
                public void onFailure(Exception e) {
                    // 失败回调
                    sink.error(e);
                }
            });
        });
    }

    @Override
    public Mono<List<String>> suggestBySingleField(String suggestField, String prefixKey) {
        return Mono.create(sink -> {
            // 准备查询条件
            SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
            sourceBuilder.suggest(new SuggestBuilder()
                    .addSuggestion("mySuggestion",
                            SuggestBuilders.completionSuggestion(suggestField).prefix(prefixKey)
                                    .size(30).skipDuplicates(true)));
            // 准备请求对象
            SearchRequest request = new SearchRequest(indexName).source(sourceBuilder);

            // 发送异步请求
            client.searchAsync(request, RequestOptions.DEFAULT, new ActionListener<SearchResponse>() {
                @Override
                public void onResponse(SearchResponse response) {
                    // 成功的回调函数
                    if (response.status() != RestStatus.OK) {
                        sink.error(new RuntimeException("查询失败"));
                    }
                    // 处理结果
                    List<String> list = handleSuggestResponse(response);
                    // 发布数据
                    sink.success(list);
                }
                @Override
                public void onFailure(Exception e) {
                    sink.error(e);
                }
            });
        });
    }

    private String getID(T t) {
        if(t == null){
            throw new RuntimeException(t.getClass().getName() + "实例不能为null！");
        }
        try {
            Object value = idField.get(t);
            return value == null ? null : value.toString();
        } catch (Exception e) {
            throw new RuntimeException("实体类中没有id字段或者id字段没有get方法");
        }
    }

    private String toJson(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private T fromJson(String json) {
        try {
            return mapper.readValue(json, clazz);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private List<String> handleSuggestResponse(SearchResponse response) {
        return StreamSupport.stream(response.getSuggest().spliterator(), true)
                .map(s -> (CompletionSuggestion) s)
                .map(CompletionSuggestion::getOptions)
                .flatMap(List::stream)
                .map(CompletionSuggestion.Entry.Option::getText)
                .map(Text::string)
                .distinct()
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toList());
    }
}
