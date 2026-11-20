package com.smartfactory.energy.simulator;

import com.smartfactory.energy.ess.EssStatusMinute;
import com.smartfactory.energy.power.SitePowerMinute;
import java.util.Optional;
import org.springframework.stereotype.Component;

// MinuteAggregationJob이 이번 분을 저장한 직후 그 값을 메모리에 들고 있습니다.
// 이 값을 만든 서버(시뮬레이터를 켠 PC) 안에서는 SitePowerService가 방금 쓴 값을 그대로 쓸 수 있어서,
// 매초 폴링마다 site_power_minute/ess_status_minute의 "최신 1행"을 DB에서 다시 조회하지 않아도 됩니다.
// 시뮬레이터가 없는 서버에서는 계속 비어 있고(Optional.empty), 그런 서버는 그대로 DB에서 읽습니다.
@Component
public class LatestSiteStatus {

    private volatile SitePowerMinute sitePowerMinute;
    private volatile EssStatusMinute essStatusMinute;

    public void setSitePowerMinute(SitePowerMinute value) {
        this.sitePowerMinute = value;
    }

    public void setEssStatusMinute(EssStatusMinute value) {
        this.essStatusMinute = value;
    }

    public Optional<SitePowerMinute> sitePowerMinute() {
        return Optional.ofNullable(sitePowerMinute);
    }

    public Optional<EssStatusMinute> essStatusMinute() {
        return Optional.ofNullable(essStatusMinute);
    }
}
