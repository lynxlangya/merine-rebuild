package com.merine.rebuild.agent.chat;

import com.merine.rebuild.agent.chat.dto.ChatEvent;
import com.merine.rebuild.agent.chat.dto.ChatErrorCode;
import com.merine.rebuild.agent.chat.dto.ChatRequest;
import com.merine.rebuild.agent.chat.dto.MessagePart;
import com.merine.rebuild.agent.chat.dto.MessagePart.ChartMeta;
import com.merine.rebuild.agent.chat.dto.MessagePart.ChartSeries;
import com.merine.rebuild.agent.chat.dto.MessagePart.ChartSpec;
import com.merine.rebuild.agent.chat.dto.MessagePart.ChartType;
import com.merine.rebuild.agent.chat.dto.MessagePart.NoticeLevel;
import com.merine.rebuild.agent.chat.dto.MessagePart.QuestionMode;
import com.merine.rebuild.agent.chat.dto.MessagePart.SourceKind;
import com.merine.rebuild.agent.chat.dto.MessagePart.SourceRef;
import com.merine.rebuild.agent.chat.dto.MessagePart.StepItem;
import com.merine.rebuild.agent.chat.dto.MessagePart.StepLabel;
import com.merine.rebuild.agent.chat.dto.MessagePart.StepStatus;
import com.merine.rebuild.agent.chat.dto.MessagePart.TableColumn;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 脚本化替身：本期唯一的执行者，按用户输入里的关键词选择固定场景。
 *
 * <p>所有内容都是合成数据，输出里带「本地演示 / 合成数据」字样；不调用任何外部模型或网络，
 * 也不读写业务表。场景覆盖直接回答、追问、资料不足、慢流、失败与超时，
 * 供前端与集成测试验证流式协议。真实模型接入时删掉这个替身即可，
 * 不预建 ModelClient 之类的抽象接口。
 */
@ChatDemoOnly
@Component
final class ScriptedChatStub {

    void run(ChatStreamWriter writer, ChatPacing pacing, ChatRequest request, boolean answering)
            throws InterruptedException {
        pacing.sleep(40);
        if (answering) {
            answer(writer, pacing);
            return;
        }
        String text = lastUserText(request);
        if (text.contains("失败")) {
            failure(writer, pacing);
            return;
        }
        if (text.contains("资料不足")) {
            insufficient(writer, pacing);
            return;
        }
        if (text.contains("澄清") || text.contains("走私")) {
            clarify(writer, pacing);
            return;
        }
        direct(writer, pacing, text.contains("慢") ? 700 : 40);
    }

    private void direct(ChatStreamWriter writer, ChatPacing pacing, long step) throws InterruptedException {
        steps(writer, pacing, "读取本地演示脚本", "按合成数据统计（未连接业务库）");
        pacing.sleep(step);
        writer.startPart("p-t1", "TEXT");
        writer.textDelta("p-t1", "这是本地演示回复（合成数据），用于验证流式渲染与部件组装。");
        pacing.sleep(step);
        writer.textDelta("p-t1", "当前对话没有连接任何外部模型，也没有读写业务数据。");
        writer.donePart("p-t1");
        pacing.sleep(step);
        table(writer, pacing);
        chart(writer, pacing, "立案");
        sources(writer, pacing);
    }

    private void answer(ChatStreamWriter writer, ChatPacing pacing) throws InterruptedException {
        Map<String, String> conditions = writer.run().conditions();
        String confirmed = conditions.getOrDefault("Q1", "未记录");
        steps(writer, pacing, "确认口径：" + confirmed, "按合成数据重算");
        pacing.sleep(80);
        writer.startPart("p-t1", "TEXT");
        writer.textDelta("p-t1", "按你确认的口径「" + confirmed + "」统计（合成数据）：");
        writer.textDelta("p-t1", "2026 年 9 月共 12 起，按港口分布如下。");
        writer.donePart("p-t1");
        table(writer, pacing);
        chart(writer, pacing, confirmed);
    }

    private void clarify(ChatStreamWriter writer, ChatPacing pacing) throws InterruptedException {
        steps(writer, pacing, "检测到口径分歧", "等待用户确认后继续");
        pacing.sleep(60);
        writer.startPart("p-t1", "TEXT");
        writer.textDelta("p-t1", "为了给出准确结果，请先确认统计口径。");
        writer.donePart("p-t1");
        writer.startPart("p-q1", "QUESTION");
        writer.snapshotPart("p-q1", new MessagePart.Question(
                "Q1", "「走私」按哪种口径统计？", QuestionMode.SINGLE,
                List.of(new MessagePart.QuestionOption("LEAD", "风险线索", null),
                        new MessagePart.QuestionOption("CASE", "立案案件", null),
                        new MessagePart.QuestionOption("CONFIRMED", "认定事实", null)),
                false, null, null, null, null, true));
        writer.donePart("p-q1");
        writer.messageDone(ChatEvent.MessageStatus.AWAITING_INPUT);
    }

    private void insufficient(ChatStreamWriter writer, ChatPacing pacing) throws InterruptedException {
        steps(writer, pacing, "检查本地演示数据覆盖", "未找到可核对的数据范围");
        pacing.sleep(60);
        writer.startPart("p-n1", "NOTICE");
        writer.snapshotPart("p-n1", new MessagePart.Notice(NoticeLevel.WARNING,
                "本地演示数据不足以回答该问题，未生成任何数字。", "DEMO_DATA_GAP"));
        writer.donePart("p-n1");
        writer.startPart("p-t1", "TEXT");
        writer.textDelta("p-t1", "请补充数据范围，或在真实数据能力接入后再试。");
        writer.donePart("p-t1");
    }

    private void failure(ChatStreamWriter writer, ChatPacing pacing) throws InterruptedException {
        steps(writer, pacing, "读取本地演示脚本", "模拟上游失败");
        pacing.sleep(40);
        writer.startPart("p-t1", "TEXT");
        writer.textDelta("p-t1", "开始生成（本地演示）……");
        writer.donePart("p-t1");
        writer.fail(ChatErrorCode.INTERNAL, "本地演示脚本模拟的上游失败", true);
    }

    private void steps(ChatStreamWriter writer, ChatPacing pacing, String... labels)
            throws InterruptedException {
        writer.startPart("p-s1", "STEPS");
        List<StepItem> items = java.util.Arrays.stream(labels)
                .map(label -> new StepItem(StepLabel.QUERY, label, StepStatus.DONE))
                .toList();
        writer.snapshotPart("p-s1", new MessagePart.Steps(items));
        writer.donePart("p-s1");
        pacing.sleep(20);
    }

    private void table(ChatStreamWriter writer, ChatPacing pacing) throws InterruptedException {
        writer.startPart("p-tb1", "TABLE");
        writer.snapshotPart("p-tb1", new MessagePart.Table(
                "2026-09 立案数（按港口）",
                List.of(new TableColumn("port", "港口", null, false),
                        new TableColumn("count", "立案数", "起", true)),
                List.of(List.of("甲港", "8"), List.of("乙港", "4"), List.of("合计", "12")),
                "合成数据，仅用于界面验证"));
        writer.donePart("p-tb1");
        pacing.sleep(30);
    }

    private void chart(ChatStreamWriter writer, ChatPacing pacing, String seriesName)
            throws InterruptedException {
        writer.startPart("p-c1", "CHART");
        writer.snapshotPart("p-c1", new MessagePart.Chart(new ChartSpec(
                ChartType.BAR, "9 月立案数（按港口，合成数据）", "起",
                List.of("甲港", "乙港"),
                List.of(new ChartSeries(seriesName, List.of(8.0, 4.0))),
                new ChartMeta("demo.cases", "2026-09", "合成数据", null))));
        writer.donePart("p-c1");
        pacing.sleep(30);
    }

    private void sources(ChatStreamWriter writer, ChatPacing pacing) throws InterruptedException {
        writer.startPart("p-so1", "SOURCES");
        writer.snapshotPart("p-so1", new MessagePart.Sources(List.of(
                new SourceRef(SourceKind.MENU, null, "collaboration.tasks", null, "任务处置", null),
                new SourceRef(SourceKind.MENU, null, "collaboration.flows", null, "信息流转", null))));
        writer.donePart("p-so1");
    }

    private static String lastUserText(ChatRequest request) {
        return request.messages().stream()
                .filter(turn -> turn.role() == ChatRequest.ChatRole.USER)
                .reduce((first, second) -> second)
                .map(ChatRequest.ChatTurn::text)
                .orElse("");
    }
}
