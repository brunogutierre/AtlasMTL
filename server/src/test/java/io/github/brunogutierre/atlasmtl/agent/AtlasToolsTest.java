package io.github.brunogutierre.atlasmtl.agent;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.brunogutierre.atlasmtl.TestDatabase;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Calls the tools the way the model does: JSON arguments in, JSON text out. */
@SpringBootTest
@Import(TestDatabase.class)
@Sql(
    scripts = {"/geo-fixture.sql", "/geocoder-fixture.sql"},
    executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
@Sql(
    statements =
        "delete from address; delete from feature; delete from borough; delete from dataset",
    executionPhase = Sql.ExecutionPhase.AFTER_TEST_CLASS)
class AtlasToolsTest {

  @Autowired AtlasTools tools;
  @Autowired ResultStore store;
  private final JsonMapper json = JsonMapper.builder().build();

  private Map<String, ToolCallback> callbacks() {
    return Arrays.stream(ToolCallbacks.from(tools))
        .collect(Collectors.toMap(c -> c.getToolDefinition().name(), c -> c));
  }

  private JsonNode call(String tool, String arguments) {
    return json.readTree(callbacks().get(tool).call(arguments));
  }

  @Test
  void offersSixToolsWithEnumCategoriesInTheirSchema() {
    assertThat(callbacks())
        .containsOnlyKeys(
            "describe_categories",
            "find_features",
            "within_distance",
            "count_by_borough",
            "geocode",
            "get_borough");
    JsonNode schema =
        json.readTree(callbacks().get("find_features").getToolDefinition().inputSchema());
    assertThat(schema.at("/properties/category/enum").toString())
        .isEqualTo(
            "[\"PARK\",\"SCHOOL\",\"HOSPITAL\",\"CLSC\",\"LONG_TERM_CARE\",\"AMBULATORY_CARE\"]");
    assertThat(schema.at("/required").toString()).isEqualTo("[\"category\"]");
  }

  @Test
  void findsPlacesAndKeepsTheFullResultForTheMap() {
    JsonNode result = call("find_features", "{\"category\": \"SCHOOL\", \"borough\": \"plateau\"}");

    assertThat(result.get("total").asInt()).isEqualTo(2);
    assertThat(result.at("/examples/0/name").asString()).isEqualTo("École Alpha");
    assertThat(store.get(result.get("resultId").asString()))
        .hasValueSatisfying(r -> assertThat(r.places()).hasSize(2));
  }

  @Test
  void acceptsCategoriesInAnyCase() {
    assertThat(
            call("count_by_borough", "{\"category\": \"long-term care\"}")
                .get("category")
                .asString())
        .isEqualTo("LONG_TERM_CARE");
  }

  @Test
  void pairsPlacesWithTheNearestOfAnotherCategory() {
    JsonNode result =
        call(
            "within_distance",
            "{\"sourceCategory\": \"school\", \"targetCategory\": \"Hospital\", \"meters\": 600}");

    assertThat(result.get("total").asInt()).isOne();
    assertThat(result.at("/examples/0/nearest/name").asString()).isEqualTo("Hôpital Test");
    assertThat(store.get(result.get("resultId").asString()))
        .hasValueSatisfying(r -> assertThat(r.places()).hasSize(2)); // the school and the hospital
  }

  @Test
  void answersOutOfRangeArgumentsWithAHint() {
    JsonNode result =
        call(
            "within_distance",
            "{\"sourceCategory\": \"park\", \"targetCategory\": \"school\", \"meters\": 20000}");

    assertThat(result.get("error").asString()).contains("meters is 20000");
    assertThat(result.get("hint").asString()).contains("1 to 5000");
  }

  @Test
  void answersUnknownBoroughsAndAddressesWithAHint() {
    assertThat(call("get_borough", "{\"name\": \"Atlantis\"}").get("hint").asString())
        .contains("official name");
    assertThat(call("geocode", "{\"address\": \"Olympic Stadium\"}").get("error").asString())
        .contains("No Montréal address");
  }

  @Test
  void geocodesAndDescribesCategories() {
    assertThat(call("geocode", "{\"address\": \"1000 Sherbrooke St W\"}").get("address").asString())
        .isEqualTo("1000 rue Sherbrooke Ouest");
    JsonNode categories = call("describe_categories", "{}");
    assertThat(categories).hasSize(6);
    assertThat(categories.get(1).get("category").asString()).isEqualTo("SCHOOL");
    assertThat(categories.get(1).get("places").asInt()).isEqualTo(3);
  }
}
