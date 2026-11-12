# 2. 백엔드 가이드

"설비(Device)를 등록하고 목록을 조회하는 API"를 예시로, API 하나를 만드는 전체 과정을 설명합니다.
아래 예시 코드는 실제로 동작을 확인한 코드입니다. 새 기능을 만들 때 복사해서 이름만 바꿔 쓰세요.

## 요청이 처리되는 흐름

```
프론트 요청 → Controller → Service → Repository → DB
                (입구)      (로직)      (DB 접근)
```

| 계층 | 하는 일 | 비유 |
|---|---|---|
| **Controller** | 요청을 받고 응답을 돌려줌. URL을 정의 | 식당 홀 직원 (주문 받기) |
| **Service** | 실제 비즈니스 로직 처리 | 주방 요리사 |
| **Repository** | DB에서 데이터 저장/조회 | 창고 관리인 |
| **Entity** | DB 테이블과 1:1로 매칭되는 클래스 | 창고에 보관되는 물건 |
| **DTO** | 요청/응답에 쓰는 데이터 모양 | 주문서, 영수증 |

> **왜 Entity를 바로 응답으로 보내지 않나요?**
> Entity는 DB 구조 그대로라서 숨겨야 할 값이 노출되거나, DB를 바꿀 때 API도 같이 깨집니다.
> 그래서 요청/응답은 항상 DTO로 주고받습니다.

## 폴더 구조 규칙: 기능별로 나누기

`com.smartfactory.energy` 아래에 **기능 이름으로 폴더**를 만들고, 그 안에 관련 파일을 모두 넣습니다.

```
src/main/java/com/smartfactory/energy/
├── EnergyApplication.java       # 실행 파일 (건드리지 않음)
├── global/                      # 공통 설정 (건드리지 않음)
│   └── exception/               # 에러 처리
├── device/                      # 설비 기능
│   ├── Device.java              # Entity
│   ├── DeviceRepository.java
│   ├── DeviceService.java
│   ├── DeviceController.java
│   └── dto/
│       ├── DeviceCreateRequest.java
│       └── DeviceResponse.java
├── energy/                      # 에너지 사용량 기능 (예시)
└── alarm/                       # 알람 기능 (예시)
```

기능별로 나누면 각자 맡은 폴더만 작업하게 되어 **Git 충돌이 거의 안 납니다.**

## 예시: 설비 API 만들기

만드는 순서: **Entity → Repository → DTO → Service → Controller**

### ① Entity: DB 테이블 정의

```java
package com.smartfactory.energy.device;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity                                              // 이 클래스는 DB 테이블이다
@Getter                                              // 모든 필드의 getter 자동 생성 (Lombok)
@NoArgsConstructor(access = AccessLevel.PROTECTED)   // JPA가 쓰는 기본 생성자
public class Device {

    @Id                                                   // 기본키
    @GeneratedValue(strategy = GenerationType.IDENTITY)   // 1, 2, 3... 자동 증가
    private Long id;

    @Column(nullable = false)   // 필수 값
    private String name;

    private String location;

    public Device(String name, String location) {
        this.name = name;
        this.location = location;
    }
}
```

> 서버를 실행하면 `device` 테이블이 **자동으로 생성**됩니다. SQL로 직접 만들 필요가 없습니다.
> (`application.properties`의 `ddl-auto=update` 설정 덕분)

### ② Repository: DB 접근

```java
package com.smartfactory.energy.device;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceRepository extends JpaRepository<Device, Long> {
}
```

인터페이스만 만들면 `save()`, `findAll()`, `findById()`, `deleteById()` 등이 **자동으로 생깁니다.**

조건 검색이 필요하면 **메서드 이름 규칙**만 지켜서 선언하면 됩니다:

```java
List<Device> findByLocation(String location);          // WHERE location = ?
List<Device> findByNameContaining(String keyword);     // WHERE name LIKE %?%
```

### ③ DTO: 요청/응답 모양

`record`를 쓰면 짧게 만들 수 있습니다. (getter는 `request.name()` 형태)

```java
package com.smartfactory.energy.device.dto;

import jakarta.validation.constraints.NotBlank;

// 등록 요청
public record DeviceCreateRequest(
        @NotBlank String name,   // 빈 값이면 400 오류로 자동 거부
        String location
) {
}
```

```java
package com.smartfactory.energy.device.dto;

import com.smartfactory.energy.device.Device;

// 응답
public record DeviceResponse(Long id, String name, String location) {

    // Entity → DTO 변환
    public static DeviceResponse from(Device device) {
        return new DeviceResponse(device.getId(), device.getName(), device.getLocation());
    }
}
```

### ④ Service: 비즈니스 로직

```java
package com.smartfactory.energy.device;

import com.smartfactory.energy.device.dto.DeviceCreateRequest;
import com.smartfactory.energy.device.dto.DeviceResponse;
import com.smartfactory.energy.global.exception.NotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor          // final 필드를 자동으로 주입받음 (Lombok)
@Transactional(readOnly = true)   // 기본은 읽기 전용
public class DeviceService {

    private final DeviceRepository deviceRepository;

    public List<DeviceResponse> findAll() {
        return deviceRepository.findAll().stream()
                .map(DeviceResponse::from)
                .toList();
    }

    public DeviceResponse findById(Long id) {
        Device device = deviceRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("설비를 찾을 수 없습니다."));   // 없으면 404
        return DeviceResponse.from(device);
    }

    @Transactional                // 저장/수정/삭제는 이걸 붙여야 함
    public DeviceResponse create(DeviceCreateRequest request) {
        Device device = new Device(request.name(), request.location());
        return DeviceResponse.from(deviceRepository.save(device));
    }
}
```

### ⑤ Controller: API 주소 정의

```java
package com.smartfactory.energy.device;

import com.smartfactory.energy.device.dto.DeviceCreateRequest;
import com.smartfactory.energy.device.dto.DeviceResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "설비", description = "설비 관리 API")   // Swagger에 표시될 그룹 이름
@RestController
@RequestMapping("/api/devices")                     // 모든 API는 /api 로 시작!
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;

    @Operation(summary = "설비 목록 조회")
    @GetMapping                                     // GET /api/devices
    public List<DeviceResponse> findAll() {
        return deviceService.findAll();
    }

    @Operation(summary = "설비 단건 조회")
    @GetMapping("/{id}")                            // GET /api/devices/1
    public DeviceResponse findById(@PathVariable Long id) {
        return deviceService.findById(id);
    }

    @Operation(summary = "설비 등록")
    @PostMapping                                    // POST /api/devices
    @ResponseStatus(HttpStatus.CREATED)             // 성공 시 201
    public DeviceResponse create(@Valid @RequestBody DeviceCreateRequest request) {
        return deviceService.create(request);
    }
}
```

### ⑥ Swagger로 테스트

1. 서버 재실행
2. http://localhost:8080/swagger-ui.html 접속
3. "설비" 그룹 → `POST /api/devices` → **Try it out** → 아래 입력 후 **Execute**
   ```json
   { "name": "공조기-1", "location": "A라인" }
   ```
4. `GET /api/devices` 로 방금 등록한 데이터가 조회되는지 확인
5. `GET /api/devices/999` 처럼 없는 id로 조회하면 404 에러가 오는지 확인

프론트 화면 없이도 API가 잘 되는지 확인할 수 있으니, **API를 만들면 꼭 Swagger로 먼저 테스트**하세요.

## 에러 처리

에러 처리는 `global/exception`에 이미 만들어져 있습니다. **예외를 던지기만 하면** 자동으로 아래 형식의 에러 응답이 나갑니다.

```json
{ "status": 404, "message": "설비를 찾을 수 없습니다." }
```

| 상황 | 코드 | 응답 |
|---|---|---|
| 데이터가 없을 때 | `throw new NotFoundException("설비를 찾을 수 없습니다.");` | 404 |
| 요청 값이 잘못됐을 때 | `throw new IllegalArgumentException("이미 등록된 설비입니다.");` | 400 |
| DTO 검증 실패 (`@NotBlank` 등) | 자동 처리 | 400 |
| 예상 못한 오류 | 자동 처리 (서버 로그에 원인 출력) | 500 |

- Controller에서 `try-catch`로 직접 잡지 마세요. 던지기만 하면 됩니다.
- 500 에러가 나면 IntelliJ 콘솔에서 빨간 `ERROR` 로그를 확인하세요. 원인이 거기 나옵니다.

## 콘솔에서 SQL 확인하기

JPA가 실행한 SQL이 콘솔에 자동으로 출력됩니다. 데이터가 이상하게 저장/조회되면 여기부터 확인하세요.

```
DEBUG ... org.hibernate.SQL :
    insert into device (location, name) values (?, ?)
```

## 시간 데이터

서버와 DB 모두 **한국 시간(Asia/Seoul)으로 고정**되어 있습니다.
날짜/시간 필드는 `LocalDateTime`을 쓰면 됩니다.

```java
private LocalDateTime measuredAt;   // 측정 시각
```

## API 주소 규칙 (REST)

주소는 **명사(복수형)**, 동작은 **HTTP 메서드**로 표현합니다.

| 동작 | 메서드 | 주소 | 어노테이션 |
|---|---|---|---|
| 목록 조회 | GET | `/api/devices` | `@GetMapping` |
| 단건 조회 | GET | `/api/devices/{id}` | `@GetMapping("/{id}")` |
| 등록 | POST | `/api/devices` | `@PostMapping` |
| 수정 | PUT | `/api/devices/{id}` | `@PutMapping("/{id}")` |
| 삭제 | DELETE | `/api/devices/{id}` | `@DeleteMapping("/{id}")` |

- 주소의 `{id}` 값 받기: `@PathVariable Long id`
- `?location=A라인` 같은 쿼리 값 받기: `@RequestParam String location`
- ❌ `/api/getDevices`, `/api/device/create` 처럼 동사를 넣지 않습니다.

## 자주 쓰는 어노테이션 요약

| 어노테이션 | 위치 | 의미 |
|---|---|---|
| `@RestController` | Controller | JSON을 응답하는 컨트롤러 |
| `@Service` | Service | 서비스 클래스 |
| `@Entity` | Entity | DB 테이블 |
| `@RequiredArgsConstructor` | 클래스 | `final` 필드 자동 주입 |
| `@Transactional` | Service 메서드 | DB 작업 묶음 처리 (저장/수정/삭제에 필수) |
| `@Valid` | Controller 파라미터 | DTO의 검증 어노테이션(`@NotBlank` 등) 실행 |
| `@NotBlank`, `@NotNull`, `@Positive` | DTO 필드 | 입력값 검증 |

## 주의사항

- **`application.properties`는 함부로 수정하지 마세요.** 바꿔야 하면 팀에 먼저 공유해주세요.
- 개인 설정이 필요하면 `application-local.properties`를 만들어 쓰세요. (Git에 올라가지 않음)
- Entity 필드 이름을 바꾸면 DB에 **새 컬럼이 추가**되고 옛날 컬럼은 남아 있습니다. 꼬이면 `docker compose down -v`로 DB를 초기화하세요.
