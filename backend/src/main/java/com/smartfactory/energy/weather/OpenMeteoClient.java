package com.smartfactory.energy.weather;

import com.smartfactory.energy.weather.dto.OpenMeteoResponse;
import java.net.URI;
import java.time.Duration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Open-Meteo 예보 API 호출. 받아서 응답 모양(OpenMeteoResponse)으로 바꾸는 것까지만 합니다.
 * 무료 사용 조건: 비상업용, 하루 1만 번 이하 (우리는 15분마다 = 하루 96번)
 */
@Component
public class OpenMeteoClient {

    // 공장 위치(창원). current = 지금 15분 칸, minutely_15 = 지금 칸부터 15분 간격 97칸
    // 일사량이 "직전 15분 평균"이라 칸이 하나 밀려서, 앞으로 24시간(96칸)을 다 채우려고 97칸을 받습니다.
    // 이미 인코딩된 주소라 URI.create로 그대로 보냄 (Asia%2FSeoul이 다시 인코딩되지 않게)
    static final URI FORECAST_URI = URI.create("https://api.open-meteo.com/v1/forecast"
            + "?latitude=35.2&longitude=128.625"
            + "&current=temperature_2m,relative_humidity_2m,cloud_cover,shortwave_radiation,precipitation"
            + "&minutely_15=shortwave_radiation,temperature_2m,cloud_cover"
            + "&forecast_minutely_15=97"
            + "&timezone=Asia%2FSeoul");

    private final RestClient restClient;

    public OpenMeteoClient() {
        // 응답이 늦어도 수집 작업이 오래 멈춰 있지 않게 시간 제한을 둠
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    // 실패(연결 안 됨, 시간 초과, 4xx·5xx, 응답 모양이 다름)하면 RestClientException을 던짐
    public OpenMeteoResponse fetch() {
        return restClient.get()
                .uri(FORECAST_URI)
                .retrieve()
                .body(OpenMeteoResponse.class);
    }
}
