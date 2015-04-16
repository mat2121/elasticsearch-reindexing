package org.codelibs.elasticsearch.reindex;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import junit.framework.TestCase;
import org.codelibs.elasticsearch.runner.ElasticsearchClusterRunner;
import static org.codelibs.elasticsearch.runner.ElasticsearchClusterRunner.newConfigs;
import org.codelibs.elasticsearch.runner.net.Curl;
import org.codelibs.elasticsearch.runner.net.CurlResponse;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.settings.ImmutableSettings.Builder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.node.Node;

public class ReindexingPluginTest extends TestCase {

    private ElasticsearchClusterRunner runner;

    @Override
    protected void setUp() throws Exception {
        // create runner instance
        runner = new ElasticsearchClusterRunner();
        // create ES nodes
        runner.onBuild(new ElasticsearchClusterRunner.Builder() {
            @Override
            public void build(final int number, final Builder settingsBuilder) {
                settingsBuilder.put("http.cors.enabled", true);
                settingsBuilder.put("index.number_of_replicas", 0);
            }
        }).build(newConfigs().ramIndexStore().numOfNode(1));

        // wait for yellow status
        runner.ensureYellow();
    }

    @Override
    protected void tearDown() throws Exception {
        // close runner
        runner.close();
        // delete all files
        runner.clean();
    }

    public void test_reindexing() throws Exception {

        final String index = "dataset";
        final String type = "item";
        
        final String logstashIndexPrefix = "logstash-";
        final String logstashType = "dummyLogstashType";

        // create an index
        runner.createIndex(index, null);

        // create 10 indexes, logstash styled.
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -10);
        SimpleDateFormat sdf = new SimpleDateFormat("YYYY.MM.dd");

        for (int i = 0; i < 10; i++) {
            Date d = cal.getTime();
            String logstashIndex = logstashIndexPrefix + sdf.format(d);
            runner.createIndex(logstashIndex, null);

            // create 500 documents
            for (int j = 1; j <= 500; j++) {
                final IndexResponse indexResponse1 = runner.insert(logstashIndex, logstashType,
                        String.valueOf(j), "{\"msg\":\"test " + i + "\", \"id\":\""
                        + i + "\"}");
                assertTrue(indexResponse1.isCreated());
            }

            cal.add(Calendar.DAY_OF_MONTH, 1);

            if (!runner.indexExists(logstashIndex)) {
                fail();
            }
        }
        
        
        if (!runner.indexExists(index)) {
            fail();
        }

        // create 1000 documents
        for (int i = 1; i <= 1000; i++) {
            final IndexResponse indexResponse1 = runner.insert(index, type,
                    String.valueOf(i), "{\"msg\":\"test " + i + "\", \"id\":\""
                            + i + "\"}");
            assertTrue(indexResponse1.isCreated());
        }
        runner.refresh();

        // search 1000 documents
        {
            final SearchResponse searchResponse = runner.search(index, type,
                    null, null, 0, 10);
            assertEquals(1000, searchResponse.getHits().getTotalHits());
        }

        assertTrue(runner.indexExists(index));

        Node node = runner.node();

        runner.ensureGreen();
        test_index_type_to_newIndex_newType(node, index, type);

        runner.ensureGreen();
        test_index_type_to_newIndex(node, index, type);

        runner.ensureGreen();
        test_index_to_newIndex(node, index, type);

        runner.ensureGreen();
        test_index_type_to_remote_newIndex_newType(node, index, type);

        runner.ensureGreen();
        test_index_type_to_remote_newIndex(node, index, type);

        runner.ensureGreen();
        test_index_to_remote_newIndex(node, index, type);
    }
    
    private void test_logsatsh(Node node){
        
    }
    

    private void test_index_type_to_newIndex_newType(Node node, String index,
            String type) throws Exception {
        String newIndex = "dataset2";
        String newType = "item2";

        try (CurlResponse curlResponse = Curl
                .post(node,
                        "/" + index + "/" + type + "/_reindex/" + newIndex
                                + "/" + newType)
                .param("wait_for_completion", "true").execute()) {
            Map<String, Object> map = curlResponse.getContentAsMap();
            assertTrue(((Boolean) map.get("acknowledged")).booleanValue());
            assertNull(map.get("name"));
        }

        runner.flush();

        assertTrue(runner.indexExists(index));
        assertTrue(runner.indexExists(newIndex));

        // search 1000 documents
        {
            final SearchResponse searchResponse = runner.search(newIndex,
                    newType, null, null, 0, 10);
            assertEquals(1000, searchResponse.getHits().getTotalHits());
        }

        runner.deleteIndex(newIndex);
    }

    private void test_index_type_to_newIndex(Node node, String index,
            String type) throws Exception {
        String newIndex = "dataset2";
        String newType = type;

        try (CurlResponse curlResponse = Curl
                .post(node, "/" + index + "/" + type + "/_reindex/" + newIndex)
                .param("wait_for_completion", "true").execute()) {
            Map<String, Object> map = curlResponse.getContentAsMap();
            assertTrue(((Boolean) map.get("acknowledged")).booleanValue());
            assertNull(map.get("name"));
        }

        runner.flush();

        assertTrue(runner.indexExists(index));
        assertTrue(runner.indexExists(newIndex));

        // search 1000 documents
        {
            final SearchResponse searchResponse = runner.search(newIndex,
                    newType, null, null, 0, 10);
            assertEquals(1000, searchResponse.getHits().getTotalHits());
        }

        runner.deleteIndex(newIndex);
    }

    private void test_index_to_newIndex(Node node, String index, String type)
            throws Exception {
        String newIndex = "dataset2";
        String newType = type;

        try (CurlResponse curlResponse = Curl
                .post(node, "/" + index + "/_reindex/" + newIndex)
                .param("wait_for_completion", "true").execute()) {
            Map<String, Object> map = curlResponse.getContentAsMap();
            assertTrue(((Boolean) map.get("acknowledged")).booleanValue());
            assertNull(map.get("name"));
        }

        runner.flush();

        assertTrue(runner.indexExists(index));
        assertTrue(runner.indexExists(newIndex));

        // search 1000 documents
        {
            final SearchResponse searchResponse = runner.search(newIndex,
                    newType, null, null, 0, 10);
            assertEquals(1000, searchResponse.getHits().getTotalHits());
        }

        runner.deleteIndex(newIndex);
    }

    private void test_index_type_to_remote_newIndex_newType(Node node,
            String index, String type) throws Exception {
        String newIndex = "dataset2";
        String newType = "item2";

        try (CurlResponse curlResponse = Curl
                .post(node,
                        "/" + index + "/" + type + "/_reindex/" + newIndex
                                + "/" + newType)
                .param("wait_for_completion", "true")
                .param("url",
                        "http://localhost:" + node.settings().get("http.port"))
                .execute()) {
            Map<String, Object> map = curlResponse.getContentAsMap();
            assertTrue(((Boolean) map.get("acknowledged")).booleanValue());
            assertNull(map.get("name"));
        }

        runner.flush();

        assertTrue(runner.indexExists(index));
        assertTrue(runner.indexExists(newIndex));

        // search 1000 documents
        {
            final SearchResponse searchResponse = runner.search(newIndex,
                    newType, null, null, 0, 10);
            assertEquals(1000, searchResponse.getHits().getTotalHits());
        }

        runner.deleteIndex(newIndex);
    }

    private void test_index_type_to_remote_newIndex(Node node, String index,
            String type) throws Exception {
        String newIndex = "dataset2";
        String newType = type;

        try (CurlResponse curlResponse = Curl
                .post(node, "/" + index + "/" + type + "/_reindex/" + newIndex)
                .param("wait_for_completion", "true")
                .param("url",
                        "http://localhost:" + node.settings().get("http.port"))
                .execute()) {
            Map<String, Object> map = curlResponse.getContentAsMap();
            assertTrue(((Boolean) map.get("acknowledged")).booleanValue());
            assertNull(map.get("name"));
        }

        runner.flush();

        assertTrue(runner.indexExists(index));
        assertTrue(runner.indexExists(newIndex));

        // search 1000 documents
        {
            final SearchResponse searchResponse = runner.search(newIndex,
                    newType, null, null, 0, 10);
            assertEquals(1000, searchResponse.getHits().getTotalHits());
        }

        runner.deleteIndex(newIndex);
    }

    private void test_index_to_remote_newIndex(Node node, String index,
            String type) throws Exception {
        String newIndex = "dataset2";
        String newType = type;

        try (CurlResponse curlResponse = Curl
                .post(node, "/" + index + "/_reindex/" + newIndex)
                .param("wait_for_completion", "true")
                .param("url",
                        "http://localhost:" + node.settings().get("http.port"))
                .execute()) {
            Map<String, Object> map = curlResponse.getContentAsMap();
            assertTrue(((Boolean) map.get("acknowledged")).booleanValue());
            assertNull(map.get("name"));
        }

        runner.flush();

        assertTrue(runner.indexExists(index));
        assertTrue(runner.indexExists(newIndex));

        // search 1000 documents
        {
            final SearchResponse searchResponse = runner.search(newIndex,
                    newType, null, null, 0, 10);
            assertEquals(1000, searchResponse.getHits().getTotalHits());
        }

        runner.deleteIndex(newIndex);
    }

    public void test_parentChild() throws Exception {

        final String index = "company";
        final String parentType = "branch";
        final String childType = "employee";

        // create an index
        runner.createIndex(index, null);
        runner.createMapping(index, childType, "{\"_parent\":{\"type\":\""
                + parentType + "\"}}");

        if (!runner.indexExists(index)) {
            fail();
        }

        // create parent 1000 documents
        for (int i = 1; i <= 100; i++) {
            final IndexResponse indexResponse1 = runner.insert(index,
                    parentType, String.valueOf(i), "{\"name\":\"Branch" + i
                            + "\"}");
            assertTrue(indexResponse1.isCreated());
            for (int j = 1; j <= 10; j++) {
                final IndexResponse indexResponse2 = runner
                        .client()
                        .prepareIndex(index, childType, i + "_" + j)
                        .setSource(
                                "{\"name\":\"Taro " + i + "_" + j
                                        + "\", \"age\":\"" + (i % 20 + 20)
                                        + "\"}").setParent(String.valueOf(i))
                        .setRefresh(true).execute().actionGet();
                assertTrue(indexResponse2.isCreated());
            }
        }
        runner.refresh();

        // search 100 parent documents
        {
            final SearchResponse searchResponse = runner.search(index,
                    parentType, null, null, 0, 10);
            assertEquals(100, searchResponse.getHits().getTotalHits());
        }
        // search 1000 child documents
        {
            final SearchResponse searchResponse = runner.search(index,
                    childType, null, null, 0, 10);
            assertEquals(1000, searchResponse.getHits().getTotalHits());
        }
        // search 5 parent documents
        {
            final SearchResponse searchResponse = runner
                    .search(index, parentType, QueryBuilders.hasChildQuery(
                            childType, QueryBuilders.matchQuery("age", "20")),
                            null, 0, 10);
            assertEquals(5, searchResponse.getHits().getTotalHits());
        }

        Node node = runner.node();

        runner.ensureGreen();
        test_index_to_newIndex_pc(node, index, parentType, childType);

        runner.ensureGreen();
        test_index_type_to_newIndex_pc(node, index, parentType, childType);

        runner.ensureGreen();
        test_index_to_remote_newIndex_pc(node, index, parentType, childType);

        runner.ensureGreen();
        test_index_type_to_remote_newIndex_pc(node, index, parentType,
                childType);
    }

    private void test_index_type_to_remote_newIndex_pc(Node node, String index,
            String parentType, String childType) throws Exception {
        String newIndex = "company2";
        String newParentType = parentType;
        String newChildType = childType;

        // create an index
        runner.createIndex(newIndex, null);
        runner.createMapping(newIndex, newChildType,
                "{\"_parent\":{\"type\":\"" + parentType + "\"}}");

        // reindex
        try (CurlResponse curlResponse = Curl
                .post(node,
                        "/" + index + "/" + parentType + "," + childType
                                + "/_reindex/" + newIndex + "/")
                .param("wait_for_completion", "true")
                .param("url",
                        "http://localhost:" + node.settings().get("http.port"))
                .execute()) {
            Map<String, Object> map = curlResponse.getContentAsMap();
            assertTrue(((Boolean) map.get("acknowledged")).booleanValue());
            assertNull(map.get("name"));
        }

        runner.flush();

        assertTrue(runner.indexExists(index));
        assertTrue(runner.indexExists(newIndex));

        // search 100 parent documents
        {
            final SearchResponse searchResponse = runner.search(newIndex,
                    newParentType, null, null, 0, 10);
            assertEquals(100, searchResponse.getHits().getTotalHits());
        }
        // search 1000 child documents
        {
            final SearchResponse searchResponse = runner.search(newIndex,
                    newChildType, null, null, 0, 10);
            assertEquals(1000, searchResponse.getHits().getTotalHits());
        }
        // search 5 parent documents
        {
            final SearchResponse searchResponse = runner
                    .search(newIndex, newParentType, QueryBuilders
                            .hasChildQuery(newChildType,
                                    QueryBuilders.matchQuery("age", "20")),
                            null, 0, 10);
            assertEquals(5, searchResponse.getHits().getTotalHits());
        }
        runner.deleteIndex(newIndex);
    }

    private void test_index_to_remote_newIndex_pc(Node node, String index,
            String parentType, String childType) throws Exception {
        String newIndex = "company2";
        String newParentType = parentType;
        String newChildType = childType;

        // create an index
        runner.createIndex(newIndex, null);
        runner.createMapping(newIndex, newChildType,
                "{\"_parent\":{\"type\":\"" + parentType + "\"}}");

        // reindex
        try (CurlResponse curlResponse = Curl
                .post(node, "/" + index + "/_reindex/" + newIndex + "/")
                .param("wait_for_completion", "true")
                .param("url",
                        "http://localhost:" + node.settings().get("http.port"))
                .execute()) {
            Map<String, Object> map = curlResponse.getContentAsMap();
            assertTrue(((Boolean) map.get("acknowledged")).booleanValue());
            assertNull(map.get("name"));
        }

        runner.flush();

        assertTrue(runner.indexExists(index));
        assertTrue(runner.indexExists(newIndex));

        // search 100 parent documents
        {
            final SearchResponse searchResponse = runner.search(newIndex,
                    newParentType, null, null, 0, 10);
            assertEquals(100, searchResponse.getHits().getTotalHits());
        }
        // search 1000 child documents
        {
            final SearchResponse searchResponse = runner.search(newIndex,
                    newChildType, null, null, 0, 10);
            assertEquals(1000, searchResponse.getHits().getTotalHits());
        }
        // search 5 parent documents
        {
            final SearchResponse searchResponse = runner
                    .search(newIndex, newParentType, QueryBuilders
                            .hasChildQuery(newChildType,
                                    QueryBuilders.matchQuery("age", "20")),
                            null, 0, 10);
            assertEquals(5, searchResponse.getHits().getTotalHits());
        }
        runner.deleteIndex(newIndex);
    }

    private void test_index_type_to_newIndex_pc(Node node, String index,
            String parentType, String childType) throws Exception {
        String newIndex = "company2";
        String newParentType = parentType;
        String newChildType = childType;

        // create an index
        runner.createIndex(newIndex, null);
        runner.createMapping(newIndex, newChildType,
                "{\"_parent\":{\"type\":\"" + parentType + "\"}}");

        // reindex
        try (CurlResponse curlResponse = Curl
                .post(node,
                        "/" + index + "/" + parentType + "," + childType
                                + "/_reindex/" + newIndex + "/")
                .param("wait_for_completion", "true").execute()) {
            Map<String, Object> map = curlResponse.getContentAsMap();
            assertTrue(((Boolean) map.get("acknowledged")).booleanValue());
            assertNull(map.get("name"));
        }

        runner.flush();

        assertTrue(runner.indexExists(index));
        assertTrue(runner.indexExists(newIndex));

        // search 100 parent documents
        {
            final SearchResponse searchResponse = runner.search(newIndex,
                    newParentType, null, null, 0, 10);
            assertEquals(100, searchResponse.getHits().getTotalHits());
        }
        // search 1000 child documents
        {
            final SearchResponse searchResponse = runner.search(newIndex,
                    newChildType, null, null, 0, 10);
            assertEquals(1000, searchResponse.getHits().getTotalHits());
        }
        // search 5 parent documents
        {
            final SearchResponse searchResponse = runner
                    .search(newIndex, newParentType, QueryBuilders
                            .hasChildQuery(newChildType,
                                    QueryBuilders.matchQuery("age", "20")),
                            null, 0, 10);
            assertEquals(5, searchResponse.getHits().getTotalHits());
        }
        runner.deleteIndex(newIndex);
    }

    private void test_index_to_newIndex_pc(Node node, String index,
            String parentType, String childType) throws Exception {
        String newIndex = "company2";
        String newParentType = parentType;
        String newChildType = childType;

        // create an index
        runner.createIndex(newIndex, null);
        runner.createMapping(newIndex, newChildType,
                "{\"_parent\":{\"type\":\"" + parentType + "\"}}");

        // reindex
        try (CurlResponse curlResponse = Curl
                .post(node, "/" + index + "/_reindex/" + newIndex + "/")
                .param("wait_for_completion", "true").execute()) {
            Map<String, Object> map = curlResponse.getContentAsMap();
            assertTrue(((Boolean) map.get("acknowledged")).booleanValue());
            assertNull(map.get("name"));
        }

        runner.flush();

        assertTrue(runner.indexExists(index));
        assertTrue(runner.indexExists(newIndex));

        // search 100 parent documents
        {
            final SearchResponse searchResponse = runner.search(newIndex,
                    newParentType, null, null, 0, 10);
            assertEquals(100, searchResponse.getHits().getTotalHits());
        }
        // search 1000 child documents
        {
            final SearchResponse searchResponse = runner.search(newIndex,
                    newChildType, null, null, 0, 10);
            assertEquals(1000, searchResponse.getHits().getTotalHits());
        }
        // search 5 parent documents
        {
            final SearchResponse searchResponse = runner
                    .search(newIndex, newParentType, QueryBuilders
                            .hasChildQuery(newChildType,
                                    QueryBuilders.matchQuery("age", "20")),
                            null, 0, 10);
            assertEquals(5, searchResponse.getHits().getTotalHits());
        }
        runner.deleteIndex(newIndex);
    }

}
