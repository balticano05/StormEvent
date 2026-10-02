package com.workspace.storm.event.orchestration;

import com.workspace.storm.event.agent.Extractor;
import com.workspace.storm.event.agent.SearchIntent;
import com.workspace.storm.event.context.RequestContext;
import com.workspace.storm.event.dto.offer.Offer;
import com.workspace.storm.event.dto.source.ToolResult;
import com.workspace.storm.event.gateway.EventQuery;
import com.workspace.storm.event.gateway.HotelQuery;
import com.workspace.storm.event.gateway.TransportQuery;
import com.workspace.storm.event.llm.LlmGateway;
import com.workspace.storm.event.llm.LlmRequest;
import com.workspace.storm.event.llm.LlmResponse;
import com.workspace.storm.event.tool.SearchBusesTool;
import com.workspace.storm.event.tool.SearchEventsTool;
import com.workspace.storm.event.tool.SearchHotelsTool;
import com.workspace.storm.event.tool.SearchTrainsTool;

import java.time.LocalDate;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

/** Полный MVP-контур: prompt → intent → tools → combine → rank → text (fallback LLM-stub). */
@Component
public class SearchOrchestrator {

    private final Extractor extractor;
    private final SearchBusesTool busesTool;
    private final SearchTrainsTool trainsTool;
    private final SearchEventsTool eventsTool;
    private final SearchHotelsTool hotelsTool;
    private final LlmGateway llmGateway;
    private final String systemPrompt;

    public SearchOrchestrator(Extractor extractor, SearchBusesTool busesTool, SearchTrainsTool trainsTool,
                              SearchEventsTool eventsTool, SearchHotelsTool hotelsTool, LlmGateway llmGateway) {
        this.extractor = extractor;
        this.busesTool = busesTool;
        this.trainsTool = trainsTool;
        this.eventsTool = eventsTool;
        this.hotelsTool = hotelsTool;
        this.llmGateway = llmGateway;
        this.systemPrompt = "You are StormEvent assistant. Answer concisely in Russian.";
    }

    public String respond(String sessionId, String text, RequestContext ctx) {
        SearchIntent intent = extractor.extract(text);
        List<ToolResult> results = new ArrayList<>();
        TransportQuery transport = new TransportQuery(
                intent.from() != null ? intent.from() : "Минск",
                intent.to() != null ? intent.to() : "Гродно",
                intent.date() != null ? intent.date() : LocalDate.now().plusDays(1),
                intent.passengers(), null, false, intent.roundTrip());
        for (String domain : intent.domains()) {
            switch (domain) {
                case "BUS" -> results.add(busesTool.search(transport, ctx));
                case "TRAIN" -> results.add(trainsTool.search(transport, ctx));
                case "EVENT" -> results.add(eventsTool.search(new EventQuery("Минск", LocalDate.now().plusDays(1), null), ctx));
                case "HOTEL" -> results.add(hotelsTool.search(new HotelQuery("Минск", LocalDate.now().plusDays(1), LocalDate.now().plusDays(2), 1), ctx));
                default -> { }
            }
        }
        List<Offer> merged = Combiner.merge(results);
        List<Offer> ranked = Ranker.topN(merged, Ranker.Mode.CHEAPEST, 5);
        List<String> warnings = new ArrayList<>();
        for (ToolResult r : results) {
            warnings.addAll(r.warnings());
        }
        String summary = render(ranked, warnings);
        try {
            LlmResponse llm = llmGateway.complete(new LlmRequest(systemPrompt, List.of(text), List.of(), 1000L));
            if (!llm.failed() && llm.text() != null && !llm.text().isBlank()) {
                return llm.text() + "\n" + summary;
            }
        } catch (Exception ignored) {
            // fallback: summary only
        }
        return summary;
    }

    private static String render(List<Offer> offers, List<String> warnings) {
        StringBuilder sb = new StringBuilder();
        if (offers.isEmpty()) {
            sb.append("Варианты не найдены.");
        } else {
            for (Offer offer : offers) {
                sb.append(offer.getSource()).append(": ")
                        .append(offer.getFrom()).append(" → ").append(offer.getTo())
                        .append(", цена ")
                        .append(offer.getPrice() != null && offer.getPrice().getAmountByn() != null ? offer.getPrice().getAmountByn() : "?")
                        .append(" BYN\n");
            }
        }
        for (String warning : warnings) {
            sb.append("Внимание: ").append(warning).append("\n");
        }
        return sb.toString().trim();
    }

    public ToolResult run(PipelineContext ctx, RunPlan plan) {
        if (plan == null || plan.isEmpty()) {
            return ToolResult.empty("search");
        }
        return ToolResult.empty("search");
    }
}
