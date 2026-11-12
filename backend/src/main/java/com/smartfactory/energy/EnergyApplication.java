package com.smartfactory.energy;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class EnergyApplication {

    public static void main(String[] args) {
        // 서버 시간대를 한국 시간으로 고정 (PC/서버 설정과 관계없이 동일하게 동작)
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
        SpringApplication.run(EnergyApplication.class, args);
    }
}
