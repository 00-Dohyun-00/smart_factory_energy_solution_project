package com.smartfactory.energy.global.exception;

/**
 * 데이터를 찾을 수 없을 때 사용합니다. (404 응답)
 * 사용 예: throw new NotFoundException("설비를 찾을 수 없습니다.");
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
