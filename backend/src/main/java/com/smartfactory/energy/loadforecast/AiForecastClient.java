package com.smartfactory.energy.loadforecast;

import com.smartfactory.energy.loadforecast.dto.AiLoadForecastResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * AI 서버(FastAPI, ai/app/main.py) 호출. 받아서 응답 모양으로 바꾸는 것까지만 합니다.
 * 주소는 backend/.env 의 AI_SERVER_URL (없으면 http://localhost:8000).
 * Docker로 띄울 때는 infra/server/docker-compose.yml 이 http://ai:8000 으로 넘겨줍니다.
 */
@Component
public class AiForecastClient {

    private final RestClient restClient;

    public AiForecastClient(@Value("${AI_SERVER_URL:http://localhost:8000}") String baseUrl) {
        // 판단 엔진이 1분마다 부르므로, AI 서버가 늦으면 오래 기다리지 않고 대신 쓸 예측으로 넘어가게 짧게 둠
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    // start가 들어 있는 15분 칸부터 slots칸의 부하 예측
    // 실패(연결 안 됨, 시간 초과, 4xx·5xx, 응답 모양이 다름)하면 RestClientException을 던짐
    public AiLoadForecastResponse fetch(LocalDateTime start, int slots) {
        return restClient.get()
                .uri("/load-forecasts?start={start}&slots={slots}", start.withNano(0).toString(), slots)
                .retrieve()
                .body(AiLoadForecastResponse.class);
    }
}
