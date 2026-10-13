package com.codearena.api.websocket;

import com.codearena.api.config.CorsProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final CorsProperties corsProperties;
    private final StompAuthInterceptor authInterceptor;
    private final StompDestinationInterceptor destinationInterceptor;
    private final StompErrorHandler errorHandler;

    public WebSocketConfig(CorsProperties corsProperties, StompAuthInterceptor authInterceptor,
                           StompDestinationInterceptor destinationInterceptor, StompErrorHandler errorHandler) {
        this.corsProperties = corsProperties;
        this.authInterceptor = authInterceptor;
        this.destinationInterceptor = destinationInterceptor;
        this.errorHandler = errorHandler;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint(StompDestinations.ENDPOINT)
                .setAllowedOrigins(corsProperties.allowedOrigins().toArray(String[]::new));
        registry.setErrorHandler(errorHandler);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker(StompDestinations.TOPIC_PREFIX);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authInterceptor, destinationInterceptor);
    }
}
