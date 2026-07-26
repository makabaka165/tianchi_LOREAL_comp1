package com.hmdp.serviceassist.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Freezes the two-layer output contract (CONTRACT-001):
 * the business payload schema (owned by serviceassist) validates blocks[0].data,
 * while the envelope schema describes the platform AgentRunOutput and must never
 * duplicate business fields. Positive and negative examples for each schema.
 */
class CustomerServiceSchemaContractTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static JsonSchema businessSchema;
    private static JsonSchema envelopeSchema;
    private static JsonSchema inputSchema;
    private static JsonNode businessSchemaNode;
    private static JsonNode envelopeSchemaNode;

    @BeforeAll
    static void loadSchemas() throws IOException {
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
        businessSchemaNode = MAPPER.readTree(Files.readString(
                Path.of("docs/contracts/customer-service-assistance-output.schema.json"),
                StandardCharsets.UTF_8));
        envelopeSchemaNode = MAPPER.readTree(Files.readString(
                Path.of("docs/contracts/customer-service-agent-run-output.schema.json"),
                StandardCharsets.UTF_8));
        JsonNode inputSchemaNode = MAPPER.readTree(Files.readString(
                Path.of("docs/contracts/customer-service-assistance-input.schema.json"),
                StandardCharsets.UTF_8));
        businessSchema = factory.getSchema(businessSchemaNode);
        envelopeSchema = factory.getSchema(envelopeSchemaNode);
        inputSchema = factory.getSchema(inputSchemaNode);
    }

    private static ObjectNode validBusinessPayload() {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("contractVersion", "1.0");

        ObjectNode analysis = root.putObject("analysis");
        analysis.put("intentCode", "AFTER_SALES_ADVERSE_REACTION");
        analysis.put("subIntentCode", "SKIN_REDNESS");
        analysis.put("summary", "消费者反馈使用精华后面部泛红刺痛，情绪焦虑，要求处理。");
        ObjectNode emotion = analysis.putObject("emotion");
        emotion.put("label", "ANXIOUS");
        emotion.put("trend", "WORSENING");
        emotion.put("confidence", 0.86);
        ArrayNode riskSignals = analysis.putArray("riskSignals");
        ObjectNode signal = riskSignals.addObject();
        signal.put("type", "ADVERSE_REACTION");
        signal.put("severity", "HIGH");
        signal.put("confidence", 0.9);
        signal.put("summary", "消费者自述使用产品后泛红刺痛");
        signal.putArray("evidenceRefs").add("msg:S00082-12");

        ArrayNode facts = root.putArray("facts");
        ObjectNode fact = facts.addObject();
        fact.put("key", "order.no");
        fact.put("value", "202606150001");
        fact.putArray("evidenceRefs").add("order:snap-001");

        ObjectNode replyDraft = root.putObject("replyDraft");
        replyDraft.put("text", "非常抱歉给您带来不适。建议您先暂停使用该产品，保留产品和照片作为凭证，我们已为您升级到专业售后同事跟进。");
        replyDraft.put("tone", "APOLOGETIC");
        replyDraft.put("editable", true);
        replyDraft.putArray("evidenceRefs").add("msg:S00082-12");

        ArrayNode actions = root.putArray("actions");
        ObjectNode action = actions.addObject();
        action.put("code", "ESCALATE_SPECIALIST");
        action.put("title", "升级专业售后");
        action.putObject("parameters");
        action.put("requiresHumanConfirmation", true);
        action.putArray("evidenceRefs").add("msg:S00082-12");

        ArrayNode citations = root.putArray("citations");
        ObjectNode citation = citations.addObject();
        citation.put("refId", "msg:S00082-12");
        citation.put("sourceType", "MESSAGE");
        citation.put("sourceId", "S00082-12");
        citation.put("label", "消费者描述不良反应的消息");
        ObjectNode orderCitation = citations.addObject();
        orderCitation.put("refId", "order:snap-001");
        orderCitation.put("sourceType", "ORDER_SNAPSHOT");
        orderCitation.put("sourceId", "snap-001");
        orderCitation.put("label", "关联订单快照");

        root.putArray("warnings");
        root.put("needHumanEscalation", true);
        return root;
    }

    private static ObjectNode validEnvelope(JsonNode businessPayload) {
        ObjectNode envelope = MAPPER.createObjectNode();
        envelope.put("answer", "已生成结构化辅助建议");
        ArrayNode blocks = envelope.putArray("blocks");
        ObjectNode block = blocks.addObject();
        block.put("type", "JSON");
        block.put("text", "customer service assistance payload");
        block.set("data", businessPayload);
        envelope.putArray("warnings");
        return envelope;
    }

    @Test
    void businessSchemaAcceptsAdverseReactionExample() {
        Set<ValidationMessage> errors = businessSchema.validate(validBusinessPayload());
        assertThat(errors).isEmpty();
    }

    @Test
    void businessSchemaRejectsMissingEvidence() {
        ObjectNode payload = validBusinessPayload();
        ((ObjectNode) payload.get("facts").get(0)).remove("evidenceRefs");
        assertThat(businessSchema.validate(payload)).isNotEmpty();
    }

    @Test
    void businessSchemaRejectsEmptyRiskSignalEvidence() {
        ObjectNode payload = validBusinessPayload();
        ((ObjectNode) payload.get("analysis").get("riskSignals").get(0))
                .putArray("evidenceRefs");
        assertThat(businessSchema.validate(payload)).isNotEmpty();
    }

    @Test
    void businessSchemaRejectsUnknownTopLevelField() {
        ObjectNode payload = validBusinessPayload();
        payload.put("scene_major", "leak");
        assertThat(businessSchema.validate(payload)).isNotEmpty();
    }

    @Test
    void businessSchemaRejectsIllegalActionCode() {
        ObjectNode payload = validBusinessPayload();
        ((ObjectNode) payload.get("actions").get(0)).put("code", "AUTO_REFUND");
        assertThat(businessSchema.validate(payload)).isNotEmpty();
    }

    @Test
    void businessSchemaRejectsActionWithoutHumanConfirmation() {
        ObjectNode payload = validBusinessPayload();
        ((ObjectNode) payload.get("actions").get(0)).put("requiresHumanConfirmation", false);
        assertThat(businessSchema.validate(payload)).isNotEmpty();
    }

    @Test
    void envelopeSchemaAcceptsWrappedBusinessPayload() {
        Set<ValidationMessage> errors = envelopeSchema.validate(validEnvelope(validBusinessPayload()));
        assertThat(errors).isEmpty();
    }

    @Test
    void envelopeSchemaRejectsMissingBlocks() {
        ObjectNode envelope = MAPPER.createObjectNode();
        envelope.put("answer", "no blocks");
        assertThat(envelopeSchema.validate(envelope)).isNotEmpty();
    }

    @Test
    void envelopeSchemaRejectsWrongContractVersion() {
        ObjectNode payload = validBusinessPayload();
        payload.put("contractVersion", "2.0");
        // the envelope pins blocks[0].data.contractVersion == 1.0 regardless of payload internals
        ObjectNode envelope = validEnvelope(payload);
        assertThat(envelopeSchema.validate(envelope)).isNotEmpty();
    }

    @Test
    void envelopeSchemaRejectsFirstBlockWithoutData() {
        ObjectNode envelope = validEnvelope(validBusinessPayload());
        ((ObjectNode) envelope.get("blocks").get(0)).remove("data");
        assertThat(envelopeSchema.validate(envelope)).isNotEmpty();
    }

    @Test
    void envelopeSchemaDoesNotDuplicateBusinessFieldDefinitions() {
        // the two layers stay separate: the envelope must not redefine business fields
        String envelopeText = envelopeSchemaNode.toString();
        assertThat(envelopeText).doesNotContain("replyDraft", "needHumanEscalation", "riskSignals");
        assertThat(envelopeSchemaNode.get("$id").asText())
                .isNotEqualTo(businessSchemaNode.get("$id").asText());
    }

    @Test
    void inputSchemaAcceptsMinimalIdentifiers() {
        ObjectNode input = MAPPER.createObjectNode();
        input.put("conversationId", "S00082");
        input.put("contextSnapshotId", "snap-0001");
        input.put("assistanceRequestId", "req-0001");
        input.put("locale", "zh-CN");
        assertThat(inputSchema.validate(input)).isEmpty();
    }

    @Test
    void inputSchemaRejectsEvaluationLabelPassThrough() {
        ObjectNode input = MAPPER.createObjectNode();
        input.put("conversationId", "S00082");
        input.put("contextSnapshotId", "snap-0001");
        input.put("assistanceRequestId", "req-0001");
        input.put("scene_major", "咨询");
        assertThat(inputSchema.validate(input)).isNotEmpty();
    }

    @Test
    void inputSchemaRejectsMissingRequiredIds() {
        ObjectNode input = MAPPER.createObjectNode();
        input.put("conversationId", "S00082");
        assertThat(inputSchema.validate(input)).isNotEmpty();
    }
}
