package com.hmdp.servicedata.infrastructure.parser;

import com.hmdp.servicedata.application.imports.ImportIssue;
import com.hmdp.servicedata.application.imports.ImportRows;
import com.hmdp.servicedata.application.imports.WorkbookParseResult;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Parser tests over a synthetic sanitized workbook built in-memory with POI (no binary
 * fixture is committed; competition source files never enter the repository). Covers
 * label-column dropping, leading zeros, formula display values, duplicates, invalid
 * dates, missing media, unknown columns and sensitive-value masking.
 */
class CompetitionWorkbookParserTest {
    private static WorkbookParseResult result;

    private static byte[] buildFixture() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet chat = wb.createSheet("聊天记录");
            writeRow(chat, 0, "会话ID", "消息序号", "message_id", "发送时间", "角色", "买家昵称",
                    "发送方", "店铺", "scene_major", "scene_minor", "is_target_buyer_message",
                    "message_text", "内容类型", "chat_content", "category", "image_path",
                    "关联订单号", "关联工单号", "神秘未知列");
            writeRow(chat, 1, "S00082", "1", "msg-001", "2026-05-08 18:00:00", "买家", "方b**",
                    "方b**", "测试店", "咨询", "不良反应", "1",
                    "用了精华全脸发红刺痒", "文本", "[]", "service", "",
                    "0006920505423981949613", "BLFY70604980", "junk");
            writeRow(chat, 2, "S00082", "2", "msg-002", "2026-05-08 18:02:00", "客服", "方b**",
                    "客服A", "测试店", "咨询", "不良反应", "0",
                    "建议先停用并拍照", "文本", "[]", "service", "", "", "", "");
            writeRow(chat, 3, "S00082", "3", "msg-003", "2026-05-08 18:03:00", "买家", "方b**",
                    "方b**", "测试店", "咨询", "不良反应", "1",
                    "图给你", "图片", "[]", "service", "mock_images/x/S00082_03.jpg", "", "", "");
            // duplicate source key -> warning + skip
            writeRow(chat, 4, "S00082", "4", "msg-003", "2026-05-08 18:04:00", "买家", "方b**",
                    "方b**", "测试店", "", "", "", "重复键", "文本", "[]", "", "", "", "", "");
            // invalid datetime -> blocking issue
            writeRow(chat, 5, "S00090", "1", "msg-101", "not-a-date", "买家", "王**",
                    "王**", "测试店", "", "", "", "你好", "文本", "[]", "", "", "", "", "");
            // empty row then a message with neither text nor media -> warning
            chat.createRow(6);
            writeRow(chat, 7, "S00090", "2", "msg-102", "2026-05-08 19:00:00", "买家", "王**",
                    "王**", "测试店", "", "", "", "", "文本", "[]", "", "", "", "", "");

            Sheet orders = wb.createSheet("订单");
            writeRow(orders, 0, "订单号", "会话ID", "买家昵称", "店铺", "商品货号", "商品名称",
                    "数量", "单价(元)", "实付金额(元)", "订单状态", "下单时间", "付款时间",
                    "发货时间", "快递公司", "物流单号", "收货省", "收货市", "赠品", "买家留言");
            writeRow(orders, 1, "0006920505423981949613", "S00082", "方b**", "测试店", "XL19806",
                    "测试修护精华", "2", "799", "1598", "交易成功", "2026-04-15 13:38:56",
                    "2026-04-15 13:43:56", "2026-04-16 09:43:56", "中通快递", "773415850905356",
                    "江西省", "上饶市", "小样", "发顺丰");
            // formula cell for amount: parser must use the cached display value
            Row formulaRow = orders.createRow(2);
            formulaRow.createCell(0).setCellValue("6920999999999999999");
            formulaRow.createCell(1).setCellValue("S00090");
            formulaRow.createCell(6).setCellValue("abc");
            formulaRow.createCell(10).setCellValue("2026-04-15 10:00:00");

            Sheet cases = wb.createSheet("不良反应工单");
            writeRow(cases, 0, "工单号", "会话ID", "关联订单号", "买家昵称", "店铺", "类型",
                    "年龄", "肤质", "使用商品", "产品批次号", "不适部位", "症状描述", "用后多久出现",
                    "是否停用", "是否就医", "任务状态", "处理人", "创建时间", "完成时间");
            writeRow(cases, 1, "BLFY70604980", "S00082", "0006920505423981949613", "方b**",
                    "测试店", "线上", "32", "干性肌肤", "测试修护精华", "26C14", "全脸",
                    "全脸发红、起小颗粒疹子（买家自述）", "8小时", "暂时停用", "否", "待处理",
                    "G003", "2026-05-08 19:27:11", "");

            Sheet payouts = wb.createSheet("线下打款工单");
            writeRow(payouts, 0, "工单号", "会话ID", "关联订单号", "买家昵称", "店铺", "打款类型",
                    "退款问题类型", "退款金额(元)", "支付宝实名", "支付宝账号", "相关物流单号",
                    "转账状态", "工单状态", "处理人", "创建时间", "完成时间");
            writeRow(payouts, 1, "HV5725862244", "S00038", "6920947277927059788", "郭a**",
                    "测试店", "支付宝转账", "退退货运费", "23.5", "曹某某", "13512345678",
                    "463424962009352", "转账成功", "已完结", "G001", "2026-05-06 16:59:44",
                    "2026-05-06 20:02:44");

            wb.write(out);
            return out.toByteArray();
        }
    }

    private static void writeRow(Sheet sheet, int rowNo, String... values) {
        Row row = sheet.createRow(rowNo);
        for (int i = 0; i < values.length; i++) {
            row.createCell(i).setCellValue(values[i]);
        }
    }

    @BeforeAll
    static void parseFixture() throws IOException {
        byte[] bytes = buildFixture();
        result = new CompetitionWorkbookParser().parse(new ByteArrayInputStream(bytes), bytes.length);
    }

    @Test
    void labelColumnsAreDroppedAtTheParserBoundary() {
        assertThat(result.getDroppedLabelColumns())
                .contains("scene_major", "scene_minor", "is_target_buyer_message", "category");
        // no normalized output may carry label text anywhere
        String all = result.getMessages().stream()
                .map(m -> m.sourceConversationId + m.content + m.senderAlias)
                .reduce("", String::concat)
                + result.getServiceCases().stream()
                .map(c -> String.valueOf(c.detail))
                .reduce("", String::concat);
        assertThat(all).doesNotContain("scene_", "is_target");
    }

    @Test
    void leadingZeroOrderNumbersSurviveAsDisplayStrings() {
        assertThat(result.getOrders())
                .extracting(o -> o.orderNo)
                .contains("0006920505423981949613");
        ImportRows.MessageRow first = result.getMessages().get(0);
        assertThat(first.sourceConversationId).isEqualTo("S00082");
    }

    @Test
    void missingMediaIsCountedNotFabricated() {
        assertThat(result.getMissingMediaCount()).isEqualTo(1);
        ImportRows.MessageRow media = result.getMessages().stream()
                .filter(m -> m.mediaPath != null).findFirst().orElseThrow();
        assertThat(media.mediaStatus).isEqualTo("MISSING_MEDIA");
    }

    @Test
    void duplicateSourceKeysAndEmptyMessagesBecomeWarnings() {
        List<String> codes = result.getIssues().stream()
                .map(ImportIssue::getErrorCode)
                .collect(Collectors.toList());
        assertThat(codes).contains("DUPLICATE_SOURCE_KEY", "EMPTY_MESSAGE");
        assertThat(result.getMessages())
                .filteredOn(m -> "重复键".equals(m.content))
                .isEmpty();
    }

    @Test
    void invalidDatetimeIsABlockingIssueWithLocation() {
        ImportIssue issue = result.getIssues().stream()
                .filter(i -> "INVALID_DATETIME".equals(i.getErrorCode()))
                .findFirst().orElseThrow();
        assertThat(issue.isBlocking()).isTrue();
        assertThat(issue.getSheet()).isEqualTo("聊天记录");
        assertThat(issue.getRowNo()).isEqualTo(6);
    }

    @Test
    void datetimesUseTheFixedSourceTimezone() {
        ImportRows.MessageRow first = result.getMessages().get(0);
        assertThat(first.sentAt).isEqualTo(Instant.parse("2026-05-08T10:00:00Z"));
    }

    @Test
    void conversationsAreDerivedWithSequenceCountsAndSpans() {
        ImportRows.ConversationRow conversation = result.getConversations().stream()
                .filter(c -> "S00082".equals(c.sourceConversationId)).findFirst().orElseThrow();
        assertThat(conversation.messageCount).isEqualTo(3);
        assertThat(conversation.consumerAlias).isEqualTo("方b**");
        assertThat(conversation.firstMessageAt).isBefore(conversation.lastMessageAt);
    }

    @Test
    void linksAreDeduplicatedAcrossChatAndSheets() {
        long orderLinks = result.getLinks().stream()
                .filter(l -> "CONVERSATION_ORDER".equals(l.linkType)
                        && "S00082".equals(l.fromConversationId)
                        && "0006920505423981949613".equals(l.toRef))
                .count();
        assertThat(orderLinks).isEqualTo(1);
        assertThat(result.getLinks().stream()
                .filter(l -> "CONVERSATION_CASE".equals(l.linkType)
                        && "BLFY70604980".equals(l.toRef))
                .count()).isEqualTo(1);
    }

    @Test
    void sensitiveAccountValuesAreMaskedInCaseDetails() {
        ImportRows.ServiceCaseRow payout = result.getServiceCases().stream()
                .filter(c -> "HV5725862244".equals(c.caseNo)).findFirst().orElseThrow();
        assertThat(payout.detail.get("支付宝账号")).isEqualTo("135***");
        assertThat(payout.detail.get("支付宝实名")).isEqualTo("曹某某***".substring(0, 3) + "***");
        assertThat(String.valueOf(payout.detail)).doesNotContain("13512345678");
    }

    @Test
    void unknownColumnsAreRecordedAsWarningsNotErrors() {
        assertThat(result.getUnknownColumns()).contains("聊天记录!神秘未知列");
    }

    @Test
    void adverseReactionCaseKeepsTypedFieldsAndDescription() {
        ImportRows.ServiceCaseRow adverse = result.getServiceCases().stream()
                .filter(c -> "BLFY70604980".equals(c.caseNo)).findFirst().orElseThrow();
        assertThat(adverse.caseType).isEqualTo("不良反应工单");
        assertThat(adverse.orderNo).isEqualTo("0006920505423981949613");
        assertThat(adverse.description).contains("买家自述");
        assertThat(adverse.caseStatus).isEqualTo("待处理");
    }

    @Test
    void nonOoxmlPayloadIsRejected() {
        byte[] junk = "PK-not-really".getBytes();
        assertThatThrownBy(() -> new CompetitionWorkbookParser()
                .parse(new ByteArrayInputStream(junk), junk.length))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void oversizedDeclaredFileIsRejectedBeforeReading() {
        assertThatThrownBy(() -> new CompetitionWorkbookParser()
                .parse(new ByteArrayInputStream(new byte[0]), 31L * 1024 * 1024))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parserVersionIsStamped() {
        assertThat(result.getParserVersion()).isEqualTo(CompetitionWorkbookParser.PARSER_VERSION);
    }
}
