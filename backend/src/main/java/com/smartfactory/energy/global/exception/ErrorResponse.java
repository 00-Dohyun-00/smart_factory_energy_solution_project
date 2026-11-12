package com.smartfactory.energy.global.exception;

/**
 * 에러 응답 형식. 모든 에러는 이 모양으로 응답됩니다.
 * 예: { "status": 404, "message": "설비를 찾을 수 없습니다." }
 */
public record ErrorResponse(int status, String message) {
}
