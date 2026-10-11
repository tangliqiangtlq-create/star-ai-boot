package org.jeecg.common.es;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.util.RestUtil;
import org.jeecg.common.util.oConvertUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Some methods of ElasticSearch (create index, add data, query, etc.)
 *
 * @author sunjianlei
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "jeecg.elasticsearch", name = "cluster-nodes")
public class JeecgElasticsearchTemplate {
    /** esServiceAddress */
    private String baseUrl;
    private final String FORMAT_JSON = "format=json";
    /** Elasticsearch version number */
    private String version = null;

    /**The maximum number of entries that ElasticSearch can return */
    public static final int ES_MAX_SIZE = 10000;

    /**es7*/
    public static final String IE_SEVEN = "7";

    /**url not found 404*/
    public static final String URL_NOT_FOUND = "404 Not Found";

    public JeecgElasticsearchTemplate(@Value("${jeecg.elasticsearch.cluster-nodes}") String baseUrl, @Value("${jeecg.elasticsearch.check-enabled}") boolean checkEnabled) {
        log.debug("JeecgElasticsearchTemplate BaseURL：" + baseUrl);
        if (StringUtils.isNotEmpty(baseUrl)) {
            this.baseUrl = baseUrl;
            // Verify whether the configured ESAddress is valid
            if (checkEnabled) {
                try {
                    this.getElasticsearchVersion();
                    log.info("ElasticSearch service connected successfully");
                    log.info("ElasticSearch version: " + this.version);
                } catch (Exception e) {
                    this.version = "";
                    log.warn("ElasticSearch service connection failed, reason: configuration not passed. BaseURL may be missing or incorrect, or Elasticsearch service may not be running. All methods will be rejected!");
                }
            }
        }
    }

    /**
     * get Elasticsearch version number information, returns on failurenull
     */
    private void getElasticsearchVersion() {
        if (this.version == null) {
            String url = this.getBaseUrl().toString();
            JSONObject result = RestUtil.get(url);
            if (result != null) {
                JSONObject v = result.getJSONObject("version");
                this.version = v.getString("number");
            }
        }
    }

    public StringBuilder getBaseUrl(String indexName, String typeName) {
        typeName = typeName.trim().toLowerCase();
        return this.getBaseUrl(indexName).append("/").append(typeName);
    }

    public StringBuilder getBaseUrl(String indexName) {
        indexName = indexName.trim().toLowerCase();
        return this.getBaseUrl().append("/").append(indexName);
    }

    public StringBuilder getBaseUrl() {
        return new StringBuilder("http://").append(this.baseUrl);
    }

    /**
     * cat QueryElasticSearchSystem data, returnjson
     */
    private <T> ResponseEntity<T> cat(String urlAfter, Class<T> responseType) {
        String url = this.getBaseUrl().append("/_cat").append(urlAfter).append("?").append(FORMAT_JSON).toString();
        return RestUtil.request(url, HttpMethod.GET, null, null, null, responseType);
    }

    /**
     * Query all indexes
     * <p>
     * Inquiry address:GET http://{baseUrl}/_cat/indices
     */
    public JSONArray getIndices() {
        return getIndices(null);
    }


    /**
     * Query a single index
     * <p>
     * Inquiry address:GET http://{baseUrl}/_cat/indices/{indexName}
     */
    public JSONArray getIndices(String indexName) {
        StringBuilder urlAfter = new StringBuilder("/indices");
        if (!StringUtils.isEmpty(indexName)) {
            urlAfter.append("/").append(indexName.trim().toLowerCase());
        }
        return cat(urlAfter.toString(), JSONArray.class).getBody();
    }

    /**
     * Does the index exist?
     */
    public boolean indexExists(String indexName) {
        try {
            JSONArray array = getIndices(indexName);
            return array != null;
        } catch (org.springframework.web.client.HttpClientErrorException ex) {
            if (HttpStatus.NOT_FOUND == ex.getStatusCode()) {
                return false;
            } else {
                throw ex;
            }
        }
    }

    /**
     * according toIDGet index data, no query returnednull
     * <p>
     * Inquiry address:GET http://{baseUrl}/{indexName}/{typeName}/{dataId}
     *
     * @param indexName index name
     * @param typeName type, an arbitrary string used for classification
     * @param dataId    Data id
     * @return
     */
    public JSONObject getDataById(String indexName, String typeName, String dataId) {
        String url = this.getBaseUrl(indexName, typeName).append("/").append(dataId).toString();
        log.info("url:" + url);
        JSONObject result = RestUtil.get(url);
        boolean found = result.getBoolean("found");
        if (found) {
            return result.getJSONObject("_source");
        } else {
            return null;
        }
    }

    /**
     * Create index
     * <p>
     * Inquiry address:PUT http://{baseUrl}/{indexName}
     */
    public boolean createIndex(String indexName) {
        String url = this.getBaseUrl(indexName).toString();

        /* Return results (for reference only)
        "createIndex": {
            "shards_acknowledged": true,
            "acknowledged": true,
            "index": "hello_world"
        }
        */
        try {
            return RestUtil.put(url).getBoolean("acknowledged");
        } catch (org.springframework.web.client.HttpClientErrorException ex) {
            if (HttpStatus.BAD_REQUEST == ex.getStatusCode()) {
                log.warn("Index creation failed: " + indexName + " already exists, no need to create again");
            } else {
                ex.printStackTrace();
            }
        }
        return false;
    }

    /**
     * Delete index
     * <p>
     * Inquiry address:DELETE http://{baseUrl}/{indexName}
     */
    public boolean removeIndex(String indexName) {
        String url = this.getBaseUrl(indexName).toString();
        try {
            return RestUtil.delete(url).getBoolean("acknowledged");
        } catch (org.springframework.web.client.HttpClientErrorException ex) {
            if (HttpStatus.NOT_FOUND == ex.getStatusCode()) {
                log.warn("Index deletion failed: " + indexName + " does not exist, no deletion needed");
            } else {
                ex.printStackTrace();
            }
        }
        return false;
    }

    /**
     * Get index field mapping (field type can be obtained)
     * <p>
     *
     * @param indexName index name
     * @param typeName  Category name
     * @return
     */
    public JSONObject getIndexMapping(String indexName, String typeName) {
        String url = this.getBaseUrl(indexName, typeName).append("/_mapping?").append(FORMAT_JSON).toString();
        // against es 7.x version compatible
        this.getElasticsearchVersion();
        if (oConvertUtils.isNotEmpty(this.version) && this.version.startsWith(IE_SEVEN)) {
            url += "&include_type_name=true";
        }
        log.info("getIndexMapping-url:" + url);
        /*
         * Reference returnJSONstructure:
         *
         *{
         *    // Index name
         *    "[indexName]": {
         *        "mappings": {
         *            // Category name
         *            "[typeName]": {
         *                "properties": {
         *                    //Field name
         *                    "input_number": {
         *                        // FieldType
         *                        "type": "long"
         *                    },
         *                    "input_string": {
         *                        "type": "text",
         *                        "fields": {
         *                            "keyword": {
         *                                "type": "keyword",
         *                                "ignore_above": 256
         *                            }
         *                        }
         *                    }
         *                 }
         *            }
         *        }
         *    }
         * }
         */
        try {
            return RestUtil.get(url);
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            String message = e.getMessage();
            if (message != null && message.contains(URL_NOT_FOUND)) {
                return null;
            }
            throw e;
        }
    }

    /**
     * Get the index Field mapping and return the Java entity class
     *
     * @param indexName
     * @param typeName
     * @return
     */
    public <T> Map<String, T> getIndexMappingFormat(String indexName, String typeName, Class<T> clazz) {
        JSONObject mapping = this.getIndexMapping(indexName, typeName);
        Map<String, T> map = new HashMap<>(5);
        if (mapping == null) {
            return map;
        }
        // Get Field property
        JSONObject properties = mapping.getJSONObject(indexName)
                .getJSONObject("mappings")
                .getJSONObject(typeName)
                .getJSONObject("properties");
        // Encapsulated into javaType
        for (String key : properties.keySet()) {
            T entity = properties.getJSONObject(key).toJavaObject(clazz);
            map.put(key, entity);
        }
        return map;
    }

    /**
     * Save data; see saveOrUpdate.
     */
    public boolean save(String indexName, String typeName, String dataId, JSONObject data) {
        return this.saveOrUpdate(indexName, typeName, dataId, data);
    }

    /**
     * Update data; see saveOrUpdate
     */
    public boolean update(String indexName, String typeName, String dataId, JSONObject data) {
        return this.saveOrUpdate(indexName, typeName, dataId, data);
    }

    /**
     * Save or edit index data
     * <p>
     * Inquiry address:PUT http://{baseUrl}/{indexName}/{typeName}/{dataId}
     *
     * @param indexName index name
     * @param typeName type, an arbitrary string used for classification
     * @param dataId    Data id
     * @param data data to be stored
     * @return
     */
    public boolean saveOrUpdate(String indexName, String typeName, String dataId, JSONObject data) {
        String url = this.getBaseUrl(indexName, typeName).append("/").append(dataId).append("?refresh=wait_for").toString();
        /* Return result (for reference only)
       "createIndexA2": {
            "result": "created",
            "_shards": {
                "total": 2,
                "successful": 1,
                "failed": 0
            },
            "_seq_no": 0,
            "_index": "test_index_1",
            "_type": "test_type_1",
            "_id": "a2",
            "_version": 1,
            "_primary_term": 1
        }
         */

        try {
            // Remove empty values ​​in data
            Set<String> keys = data.keySet();
            List<String> emptyKeys = new ArrayList<>(keys.size());
            for (String key : keys) {
                String value = data.getString(key);
                //1. Eliminate null values
                if (oConvertUtils.isEmpty(value) || "[]".equals(value)) {
                    emptyKeys.add(key);
                }
                //2. Eliminate the upload control value (will cause ES synchronization failure and report exception failed to parse field [ge_pic] of type [text])
                if (oConvertUtils.isNotEmpty(value) && value.indexOf("[{")!=-1) {
                    emptyKeys.add(key);
                    log.info("-------Remove upload control field------------key: "+ key);
                }
            }
            for (String key : emptyKeys) {
                data.remove(key);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            String result = RestUtil.put(url, data).getString("result");
            return "created".equals(result) || "updated".equals(result);
        } catch (Exception e) {
            log.error(e.getMessage() + "\n-- url: " + url + "\n-- data: " + data.toJSONString());
            //TODO printing interface returns exception json
            return false;
        }
    }

    /**
     * Save data in batches
     *
     * @param indexName index name
     * @param typeName type, an arbitrary string used for classification
     * @param dataList The data array to be stored, each row of data must contain an id
     * @return
     */
    public boolean saveBatch(String indexName, String typeName, JSONArray dataList) {
        String url = this.getBaseUrl().append("/_bulk").append("?refresh=wait_for").toString();
        StringBuilder bodySb = new StringBuilder();
        for (int i = 0; i < dataList.size(); i++) {
            JSONObject data = dataList.getJSONObject(i);
            String id = data.getString("id");
            // Operation of this line
            // {"create": {"_id":"${id}", "_index": "${indexName}", "_type": "${typeName}"}}
            JSONObject action = new JSONObject();
            JSONObject actionInfo = new JSONObject();
            actionInfo.put("_id", id);
            actionInfo.put("_index", indexName);
            actionInfo.put("_type", typeName);
            action.put("create", actionInfo);
            bodySb.append(action.toJSONString()).append("\n");
            // The row's data
            data.remove("id");
            bodySb.append(data.toJSONString()).append("\n");
        }
        System.out.println("+-+-+-: bodySb.toString(): " + bodySb.toString());
        HttpHeaders headers = RestUtil.getHeaderApplicationJson();
        RestUtil.request(url, HttpMethod.PUT, headers, null, bodySb, JSONObject.class);
        return true;
    }

    /**
     * Delete index data.
     * <p>
     * Request URL: DELETE http://{baseUrl}/{indexName}/{typeName}/{dataId}
     */
    public boolean delete(String indexName, String typeName, String dataId) {
        String url = this.getBaseUrl(indexName, typeName).append("/").append(dataId).toString();
        /* Return result (for reference only)
        {
            "_index": "es_demo",
            "_type": "docs",
            "_id": "001",
            "_version": 3,
            "result": "deleted",
            "_shards": {
                "total": 1,
                "successful": 1,
                "failed": 0
            },
            "_seq_no": 28,
            "_primary_term": 18
        }
        */
        try {
            return "deleted".equals(RestUtil.delete(url).getString("result"));
        } catch (org.springframework.web.client.HttpClientErrorException ex) {
            if (HttpStatus.NOT_FOUND == ex.getStatusCode()) {
                return false;
            } else {
                throw ex;
            }
        }
    }


    /* = = = Methods below for query and query conditions = = =*/

    /**
     * Query data
     * <p>
     * Request Address: POST http://{baseUrl}/{indexName}/{typeName}/_search
     */
    public JSONObject search(String indexName, String typeName, JSONObject queryObject) {
        String url = this.getBaseUrl(indexName, typeName).append("/_search").toString();

        log.info("url:" + url + " ,search: " + queryObject.toJSONString());
        JSONObject res = RestUtil.post(url, queryObject);
        log.info("url:" + url + " ,return res: \n" + res.toJSONString());
        return res;
    }

    /**
     * @param source (source filter) fields to return; null returns all
     * @param query
     * @param from    start index
     * @param size    page size
     * @return { "query": query }
     */
    public JSONObject buildQuery(List<String> source, JSONObject query, int from, int size) {
        JSONObject json = new JSONObject();
        if (source != null) {
            json.put("_source", source);
        }
        json.put("query", query);
        json.put("from", from);
        json.put("size", size);
        return json;
    }

    /**
     * @return { "bool" : { "must": must, "must_not": mustNot, "should": should } }
     */
    public JSONObject buildBoolQuery(JSONArray must, JSONArray mustNot, JSONArray should) {
        JSONObject bool = new JSONObject();
        if (must != null) {
            bool.put("must", must);
        }
        if (mustNot != null) {
            bool.put("must_not", mustNot);
        }
        if (should != null) {
            bool.put("should", should);
        }
        JSONObject json = new JSONObject();
        json.put("bool", bool);
        return json;
    }

    /**
     * @param field field to query
     * @param args  query args, e.g. *foo* OR *bar* NOT *baz* OR *qux*
     * @return
     */
    public JSONObject buildQueryString(String field, String... args) {
        if (field == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(field).append(":(");
        if (args != null) {
            for (String arg : args) {
                sb.append(arg).append(" ");
            }
        }
        sb.append(")");
        return this.buildQueryString(sb.toString());
    }

    /**
     * @return { "query_string": { "query": query }  }
     */
    public JSONObject buildQueryString(String query) {
        JSONObject queryString = new JSONObject();
        queryString.put("query", query);
        JSONObject json = new JSONObject();
        json.put("query_string", queryString);
        return json;
    }

    /**
     * @param field      query field
     * @param min        minimum value
     * @param max        maximum value
     * @param containMin whether range includes minimum
     * @param containMax whether range includes maximum
     * @return { "range" : { field : { [ "gt[e]?containMin" : min ]?min!=null , [ "lt[e]?containMax" : max ]}} }
     */
    public JSONObject buildRangeQuery(String field, Object min, Object max, boolean containMin, boolean containMax) {
        JSONObject inner = new JSONObject();
        if (min != null) {
            if (containMin) {
                inner.put("gte", min);
            } else {
                inner.put("gt", min);
            }
        }
        if (max != null) {
            if (containMax) {
                inner.put("lte", max);
            } else {
                inner.put("lt", max);
            }
        }
        JSONObject range = new JSONObject();
        range.put(field, inner);
        JSONObject json = new JSONObject();
        json.put("range", range);
        return json;
    }

}

