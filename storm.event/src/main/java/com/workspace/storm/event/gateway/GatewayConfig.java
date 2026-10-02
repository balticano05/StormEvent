package com.workspace.storm.event.gateway;

import com.workspace.storm.event.client.BelHotelClient;
import com.workspace.storm.event.client.BzdClient;
import com.workspace.storm.event.db.repository.SourceStateRepository;
import com.workspace.storm.event.orchestration.SourceExecutor;
import com.workspace.storm.event.service.AtlasService;
import com.workspace.storm.event.service.TicketBusService;
import com.workspace.storm.event.service.TicketProService;

import java.util.ArrayList;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayConfig {

    @Bean
    public SourceStateGuard sourceStateGuard(SourceStateRepository repository) {
        return new SourceStateGuard(repository);
    }

    @Bean
    public AtlasGateway atlasGateway(AtlasService service, SourceStateGuard guard, SourceMetrics metrics) {
        return new AtlasGateway(service, guard, metrics);
    }

    @Bean
    public BzdGateway bzdGateway(BzdClient client, SourceStateGuard guard, SourceMetrics metrics) {
        return new BzdGateway(client, guard, metrics);
    }

    @Bean
    public TicketBusGateway ticketBusGateway(TicketBusService service, SourceStateGuard guard, SourceMetrics metrics) {
        return new TicketBusGateway(service, guard, metrics);
    }

    @Bean
    public TicketProGateway ticketProGateway(TicketProService service, SourceStateGuard guard, SourceMetrics metrics) {
        return new TicketProGateway(service, guard, metrics);
    }

    @Bean
    public BelHotelGateway belHotelGateway(BelHotelClient client, SourceStateGuard guard, SourceMetrics metrics) {
        return new BelHotelGateway(client, guard, metrics);
    }

    @Bean
    public GatewayRegistry gatewayRegistry(List<SourceGateway> gateways) {
        return new GatewayRegistry(gateways);
    }

    @Bean
    public SuggestService suggestService(List<TransportGateway> transportGateways) {
        List<SuggestProvider> providers = new ArrayList<>();
        for (TransportGateway gateway : transportGateways) {
            providers.add(new SuggestProvider() {
                @Override
                public String sourceId() {
                    return gateway.sourceId();
                }

                @Override
                public com.workspace.storm.event.dto.source.PrincipalResult<List<StationSuggestion>> suggest(String query) {
                    return gateway.suggest(query);
                }
            });
        }
        return new SuggestService(providers);
    }

    @Bean
    public SourceExecutor sourceExecutor() {
        return new SourceExecutor();
    }
}
