package com.hmdp.servicedata.infrastructure.parser;

import com.hmdp.servicedata.application.imports.ImportIssue;
import com.hmdp.servicedata.application.imports.ImportRows;
import com.hmdp.servicedata.application.imports.WorkbookParseResult;
import com.hmdp.servicedata.application.port.out.WorkbookParserPort;
import com.hmdp.servicedata.domain.model.ImportErrorSeverity;
import com.hmdp.servicedata.domain.model.Message;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.poifs.filesystem.FileMagic;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the official competition workbook into typed import rows.
 *
 * Non-negotiables enforced here:
 *   * evaluation label columns (scene_major / scene_minor / is_target_* / category)
 *     are dropped at the cell level and never enter any output row;
 *   * IDs and business numbers are read through DataFormatter display strings, never
 *     through numeric cell values, so leading zeros and long digits survive;
 *   * datetimes are parsed with explicit patterns or Excel serials against a fixed
 *     source timezone, independent of the machine default;
 *   * image paths only produce MISSING_MEDIA references — the files do not exist and
 *     nothing may pretend they were processed;
 *   * account-like values (Alipay account/realname, phones) are masked before they
 *     can reach detail maps, issues or logs.
 */
@Component
public class CompetitionWorkbookParser implements WorkbookParserPort {
    public static final String PARSER_VERSION = "competition-workbook-v1";
    public static final String SOURCE_SYSTEM = "competition-workbook";

    static final ZoneId SOURCE_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** Leading timestamp with an optional trailing annotation like （定金）. */
    private static final Pattern ANNOTATED_DATE_TIME =
            Pattern.compile("^(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2})(.*)$");

    private static final long MAX_FILE_BYTES = 30L * 1024 * 1024;
    private static final int MAX_SHEETS = 16;
    private static final int MAX_ROWS_PER_SHEET = 20000;
    private static final int MAX_COLUMNS = 64;
    private static final int MAX_CELL_CHARS = 20000;

    /** Frozen deny-list: evaluation-only columns that must never survive parsing. */
    private static final Set<String> LABEL_DENY_LIST = Set.of(
            "scene_major", "scene_minor", "is_target_buyer_message", "category");

    /** Header names whose values are masked wherever they surface. */
    private static final Set<String> SENSITIVE_HEADERS = Set.of(
            "支付宝实名", "支付宝账号", "收件人电话", "联系电话", "手机号");

    private static final Set<String> CASE_SHEETS = Set.of(
            "补发换货工单", "线下打款工单", "物流工单", "不良反应工单", "售后退货工单");

    private final DataFormatter formatter = new DataFormatter(Locale.CHINA);

    @Override
    public String parserVersion() {
        return PARSER_VERSION;
    }

    @Override
    public WorkbookParseResult parse(InputStream rawStream, long declaredSize) throws IOException {
        if (declaredSize > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("workbook exceeds size limit");
        }
        WorkbookParseResult result = new WorkbookParseResult(PARSER_VERSION);
        try (InputStream in = FileMagic.prepareToCheckMagic(new BufferedInputStream(rawStream))) {
            FileMagic magic = FileMagic.valueOf(in);
            if (magic != FileMagic.OOXML) {
                throw new IllegalArgumentException("only OOXML xlsx workbooks are accepted");
            }
            try (Workbook workbook = new XSSFWorkbook(in)) {
                if (workbook.getNumberOfSheets() > MAX_SHEETS) {
                    throw new IllegalArgumentException("workbook has too many sheets");
                }
                for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                    Sheet sheet = workbook.getSheetAt(i);
                    String name = normalize(sheet.getSheetName());
                    if ("聊天记录".equals(name)) {
                        parseChatSheet(sheet, result);
                    } else if ("订单".equals(name)) {
                        parseOrderSheet(sheet, result);
                    } else if (CASE_SHEETS.contains(name)) {
                        parseCaseSheet(sheet, name, result);
                    }
                    // 数据说明 and unknown sheets are ignored on purpose
                }
            }
        }
        deriveConversationsAndLinks(result);
        return result;
    }

    // ------------------------------------------------------------------ headers

    /** Trim, fold full-width to half-width, strip zero-width/invisible characters. */
    static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(raw.length());
        for (char c : raw.toCharArray()) {
            if (c == '​' || c == '‎' || c == '﻿' || c == ' ') {
                continue;
            }
            if (c >= 0xFF01 && c <= 0xFF5E) {
                sb.append((char) (c - 0xFEE0));
            } else if (c == '　') {
                sb.append(' ');
            } else {
                sb.append(c);
            }
        }
        return sb.toString().trim();
    }

    static String maskSensitive(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        int keep = Math.min(3, value.length());
        return value.substring(0, keep) + "***";
    }

    private Map<String, Integer> readHeader(Sheet sheet, String sheetName,
                                            WorkbookParseResult result) {
        Row headerRow = sheet.getRow(sheet.getFirstRowNum());
        Map<String, Integer> header = new LinkedHashMap<>();
        if (headerRow == null) {
            return header;
        }
        int lastCell = Math.min(headerRow.getLastCellNum(), MAX_COLUMNS);
        for (int c = 0; c < lastCell; c++) {
            String name = normalize(cellText(headerRow.getCell(c)));
            if (name.isEmpty()) {
                continue;
            }
            String lower = name.toLowerCase(Locale.ROOT);
            if (LABEL_DENY_LIST.contains(lower)) {
                result.recordDroppedLabelColumn(lower);
                continue;
            }
            header.put(name, c);
        }
        return header;
    }

    // ------------------------------------------------------------------- cells

    /** Display-string read: numeric IDs keep their exact rendered form. */
    private String cellText(Cell cell) {
        if (cell == null) {
            return "";
        }
        if (cell.getCellType() == CellType.FORMULA) {
            // formulas are not evaluated; the cached display value is used as-is
            return normalize(formatter.formatCellValue(cell));
        }
        String text = normalize(formatter.formatCellValue(cell));
        if (text.length() > MAX_CELL_CHARS) {
            return text.substring(0, MAX_CELL_CHARS);
        }
        return text;
    }

    private String text(Row row, Map<String, Integer> header, String column) {
        Integer index = header.get(column);
        return index == null ? "" : cellText(row.getCell(index));
    }

    private Instant dateTime(Row row, Map<String, Integer> header, String column,
                             String sheetName, WorkbookParseResult result) {
        Integer index = header.get(column);
        if (index == null) {
            return null;
        }
        Cell cell = row.getCell(index);
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            LocalDateTime local = cell.getLocalDateTimeCellValue();
            return local == null ? null : local.atZone(SOURCE_ZONE).toInstant();
        }
        String textValue = cellText(cell);
        if (textValue.isEmpty()) {
            return null;
        }
        Matcher annotated = ANNOTATED_DATE_TIME.matcher(textValue);
        if (annotated.matches()) {
            String annotation = annotated.group(2).trim();
            if (!annotation.isEmpty()) {
                // e.g. presale deposits render as "2026-04-30 09:53:30（定金）"
                result.getIssues().add(new ImportIssue(sheetName, row.getRowNum() + 1, column,
                        "ANNOTATED_DATETIME", ImportErrorSeverity.WARNING,
                        maskSensitive(annotation), "时间值带注记，已按前缀时间解析"));
            }
            try {
                return LocalDateTime.parse(annotated.group(1), DATE_TIME)
                        .atZone(SOURCE_ZONE).toInstant();
            } catch (RuntimeException e) {
                // fall through to the blocking issue below
            }
        }
        result.getIssues().add(new ImportIssue(sheetName, row.getRowNum() + 1, column,
                "INVALID_DATETIME", ImportErrorSeverity.BLOCKING,
                maskSensitive(textValue), "无法解析时间值"));
        return null;
    }

    private BigDecimal decimal(Row row, Map<String, Integer> header, String column,
                               String sheetName, WorkbookParseResult result,
                               ImportErrorSeverity severity) {
        String textValue = text(row, header, column);
        if (textValue.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(textValue.replace(",", ""));
        } catch (NumberFormatException e) {
            result.getIssues().add(new ImportIssue(sheetName, row.getRowNum() + 1, column,
                    "INVALID_NUMBER", severity, maskSensitive(textValue), "无法解析数值"));
            return null;
        }
    }

    private boolean isRowEmpty(Row row) {
        if (row == null) {
            return true;
        }
        for (int c = row.getFirstCellNum(); c >= 0 && c < row.getLastCellNum(); c++) {
            if (!cellText(row.getCell(c)).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ sheets

    private void parseChatSheet(Sheet sheet, WorkbookParseResult result) {
        String sheetName = "聊天记录";
        Map<String, Integer> header = readHeader(sheet, sheetName, result);
        warnUnknownColumns(sheetName, header, Set.of("会话ID", "消息序号", "message_id", "发送时间",
                "角色", "买家昵称", "发送方", "店铺", "message_text", "内容类型", "chat_content",
                "image_path", "关联订单号", "关联工单号"), result);
        Set<String> seenMessageKeys = new HashSet<>();
        int rows = 0;
        for (Row row : sheet) {
            if (row.getRowNum() == sheet.getFirstRowNum() || isRowEmpty(row)) {
                continue;
            }
            if (++rows > MAX_ROWS_PER_SHEET) {
                throw new IllegalArgumentException("chat sheet exceeds row limit");
            }
            int rowNo = row.getRowNum() + 1;
            String conversationId = text(row, header, "会话ID");
            if (conversationId.isEmpty()) {
                result.getIssues().add(new ImportIssue(sheetName, rowNo, "会话ID",
                        "MISSING_REQUIRED", ImportErrorSeverity.BLOCKING, null, "缺少会话ID"));
                continue;
            }
            String seqText = text(row, header, "消息序号");
            int sequence;
            try {
                sequence = Integer.parseInt(seqText);
            } catch (NumberFormatException e) {
                result.getIssues().add(new ImportIssue(sheetName, rowNo, "消息序号",
                        "INVALID_NUMBER", ImportErrorSeverity.BLOCKING,
                        maskSensitive(seqText), "消息序号必须是整数"));
                continue;
            }
            String sourceMessageId = text(row, header, "message_id");
            String messageKey = sourceMessageId.isEmpty()
                    ? conversationId + ":" + sequence : sourceMessageId;
            if (!seenMessageKeys.add(conversationId + "|" + messageKey)) {
                result.getIssues().add(new ImportIssue(sheetName, rowNo, "message_id",
                        "DUPLICATE_SOURCE_KEY", ImportErrorSeverity.WARNING,
                        maskSensitive(messageKey), "重复的消息来源键，重复行将被跳过"));
                continue;
            }
            Instant sentAt = dateTime(row, header, "发送时间", sheetName, result);
            String role = text(row, header, "角色");
            String alias = text(row, header, "买家昵称");
            String content = text(row, header, "message_text");
            String contentType = text(row, header, "内容类型");
            String mediaPath = text(row, header, "image_path");
            String mediaStatus = null;
            if (!mediaPath.isEmpty()) {
                // referenced image files are absent from the材料; never claim otherwise
                mediaStatus = Message.MEDIA_STATUS_MISSING;
                result.incrementMissingMedia();
            }
            if (content.isEmpty() && mediaPath.isEmpty()) {
                result.getIssues().add(new ImportIssue(sheetName, rowNo, "message_text",
                        "EMPTY_MESSAGE", ImportErrorSeverity.WARNING, null,
                        "消息既无文本也无媒体引用"));
                continue;
            }
            result.getMessages().add(new ImportRows.MessageRow(conversationId, sequence,
                    messageKey, sentAt, "买家".equals(role) ? "CONSUMER" : "AGENT", alias,
                    content, "图片".equals(contentType) ? "IMAGE" : "TEXT",
                    mediaPath.isEmpty() ? null : mediaPath, mediaStatus, rowNo));
            if (!alias.isEmpty()) {
                // alias identity scope is the chat sheet: the same masked nickname across
                // conversations is one alias record (limited merge, provenance preserved)
                result.getAliases().add(new ImportRows.ConsumerAliasRow(alias, "chat"));
            }
            String orderNo = text(row, header, "关联订单号");
            if (!orderNo.isEmpty()) {
                result.getLinks().add(new ImportRows.SourceLinkRow(
                        "CONVERSATION_ORDER", conversationId, orderNo, false));
            }
            String caseNo = text(row, header, "关联工单号");
            if (!caseNo.isEmpty()) {
                result.getLinks().add(new ImportRows.SourceLinkRow(
                        "CONVERSATION_CASE", conversationId, caseNo, false));
            }
        }
    }

    private void parseOrderSheet(Sheet sheet, WorkbookParseResult result) {
        String sheetName = "订单";
        Map<String, Integer> header = readHeader(sheet, sheetName, result);
        Set<String> known = Set.of("订单号", "会话ID", "买家昵称", "店铺", "商品货号", "商品名称",
                "数量", "单价(元)", "实付金额(元)", "订单状态", "下单时间", "付款时间", "发货时间",
                "快递公司", "物流单号", "收货省", "收货市", "赠品", "买家留言");
        warnUnknownColumns(sheetName, header, known, result);
        for (Row row : sheet) {
            if (row.getRowNum() == sheet.getFirstRowNum() || isRowEmpty(row)) {
                continue;
            }
            int rowNo = row.getRowNum() + 1;
            String orderNo = text(row, header, "订单号");
            if (orderNo.isEmpty()) {
                result.getIssues().add(new ImportIssue(sheetName, rowNo, "订单号",
                        "MISSING_REQUIRED", ImportErrorSeverity.BLOCKING, null, "缺少订单号"));
                continue;
            }
            String conversationId = text(row, header, "会话ID");
            BigDecimal unitPrice = decimal(row, header, "单价(元)", sheetName, result,
                    ImportErrorSeverity.WARNING);
            BigDecimal paid = decimal(row, header, "实付金额(元)", sheetName, result,
                    ImportErrorSeverity.WARNING);
            Integer quantity = null;
            String quantityText = text(row, header, "数量");
            if (!quantityText.isEmpty()) {
                try {
                    quantity = Integer.valueOf(quantityText);
                } catch (NumberFormatException e) {
                    result.getIssues().add(new ImportIssue(sheetName, rowNo, "数量",
                            "INVALID_NUMBER", ImportErrorSeverity.WARNING,
                            maskSensitive(quantityText), "数量必须是整数"));
                }
            }
            Map<String, String> detail = new LinkedHashMap<>();
            putDetail(detail, "收货省", text(row, header, "收货省"));
            putDetail(detail, "收货市", text(row, header, "收货市"));
            putDetail(detail, "赠品", text(row, header, "赠品"));
            putDetail(detail, "买家留言", text(row, header, "买家留言"));
            String alias = text(row, header, "买家昵称");
            result.getOrders().add(new ImportRows.OrderRow(orderNo, conversationId, alias,
                    "orders", text(row, header, "商品货号"), text(row, header, "商品名称"),
                    quantity, unitPrice, paid, text(row, header, "订单状态"),
                    dateTime(row, header, "下单时间", sheetName, result),
                    dateTime(row, header, "付款时间", sheetName, result),
                    dateTime(row, header, "发货时间", sheetName, result),
                    text(row, header, "快递公司"), text(row, header, "物流单号"), detail, rowNo));
            if (!conversationId.isEmpty()) {
                result.getLinks().add(new ImportRows.SourceLinkRow(
                        "CONVERSATION_ORDER", conversationId, orderNo, false));
            }
        }
    }

    private void parseCaseSheet(Sheet sheet, String sheetName, WorkbookParseResult result) {
        Map<String, Integer> header = readHeader(sheet, sheetName, result);
        for (Row row : sheet) {
            if (row.getRowNum() == sheet.getFirstRowNum() || isRowEmpty(row)) {
                continue;
            }
            int rowNo = row.getRowNum() + 1;
            String caseNo = text(row, header, "工单号");
            if (caseNo.isEmpty()) {
                result.getIssues().add(new ImportIssue(sheetName, rowNo, "工单号",
                        "MISSING_REQUIRED", ImportErrorSeverity.BLOCKING, null, "缺少工单号"));
                continue;
            }
            String conversationId = text(row, header, "会话ID");
            String orderNo = text(row, header, "关联订单号");
            String status = firstNonEmpty(text(row, header, "工单状态"), text(row, header, "任务状态"),
                    text(row, header, "转账状态"));
            String reason = firstNonEmpty(text(row, header, "售后原因"), text(row, header, "退货原因"),
                    text(row, header, "问题类型"), text(row, header, "退款问题类型"),
                    text(row, header, "类型"));
            String description = firstNonEmpty(text(row, header, "症状描述"),
                    text(row, header, "处理方案"), text(row, header, "签收建议"));
            Map<String, String> detail = new LinkedHashMap<>();
            for (Map.Entry<String, Integer> column : header.entrySet()) {
                String name = column.getKey();
                if (Set.of("工单号", "会话ID", "关联订单号", "买家昵称", "店铺",
                        "创建时间", "完成时间").contains(name)) {
                    continue;
                }
                String value = cellText(row.getCell(column.getValue()));
                if (value.isEmpty()) {
                    continue;
                }
                detail.put(name, SENSITIVE_HEADERS.contains(name) ? maskSensitive(value) : value);
            }
            String alias = text(row, header, "买家昵称");
            result.getServiceCases().add(new ImportRows.ServiceCaseRow(caseNo, sheetName,
                    conversationId, orderNo.isEmpty() ? null : orderNo, alias,
                    "cases:" + sheetName, status, reason, description,
                    dateTime(row, header, "创建时间", sheetName, result),
                    dateTime(row, header, "完成时间", sheetName, result), detail, rowNo));
            if (!conversationId.isEmpty()) {
                result.getLinks().add(new ImportRows.SourceLinkRow(
                        "CONVERSATION_CASE", conversationId, caseNo, false));
            }
        }
    }

    // -------------------------------------------------------------- derivation

    /** Conversations are derived from the message stream; links are de-duplicated. */
    private void deriveConversationsAndLinks(WorkbookParseResult result) {
        Map<String, ImportRows.ConversationRow> byId = new LinkedHashMap<>();
        Map<String, int[]> counters = new HashMap<>();
        Map<String, Instant[]> spans = new HashMap<>();
        Map<String, String> aliasByConversation = new LinkedHashMap<>();
        for (ImportRows.MessageRow message : result.getMessages()) {
            counters.computeIfAbsent(message.sourceConversationId, k -> new int[1])[0]++;
            Instant[] span = spans.computeIfAbsent(message.sourceConversationId,
                    k -> new Instant[2]);
            if (message.sentAt != null) {
                if (span[0] == null || message.sentAt.isBefore(span[0])) {
                    span[0] = message.sentAt;
                }
                if (span[1] == null || message.sentAt.isAfter(span[1])) {
                    span[1] = message.sentAt;
                }
            }
            if (message.senderAlias != null && !message.senderAlias.isEmpty()) {
                aliasByConversation.putIfAbsent(message.sourceConversationId, message.senderAlias);
            }
        }
        for (Map.Entry<String, int[]> entry : counters.entrySet()) {
            String conversationId = entry.getKey();
            Instant[] span = spans.get(conversationId);
            byId.put(conversationId, new ImportRows.ConversationRow(conversationId,
                    aliasByConversation.get(conversationId), "chat:" + conversationId,
                    entry.getValue()[0], span[0], span[1]));
        }
        result.getConversations().addAll(byId.values());

        Set<String> seenLinks = new HashSet<>();
        result.getLinks().removeIf(link -> !seenLinks.add(
                link.linkType + "|" + link.fromConversationId + "|" + link.toRef));

        Set<String> seenAliases = new HashSet<>();
        result.getAliases().removeIf(alias -> !seenAliases.add(
                alias.sourceScope + "|" + alias.displayAlias));
    }

    private void warnUnknownColumns(String sheetName, Map<String, Integer> header,
                                    Set<String> known, WorkbookParseResult result) {
        for (String name : header.keySet()) {
            if (!known.contains(name)) {
                result.recordUnknownColumn(sheetName, name);
            }
        }
    }

    private static void putDetail(Map<String, String> detail, String key, String value) {
        if (value != null && !value.isEmpty()) {
            detail.put(key, value);
        }
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return null;
    }
}
