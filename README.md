# MySimplpCoin

---

# 안드로이드 비트코인 자동 매수매도 앱

기간 2023.03.29 ~

---

- 업비트 계정 로그인 후 연동
- 보유 중인 코인 목록 및 수익, 매수액 확인
- 공포 탐욕 지수, rsi, 이동 평균선, 볼린저 밴드, MACD 등을 계산해 매수와 매도에 적합한 코인 분류
- 보유 중인 현금과 코인을 체크하여 매수 및 매도 실행
- 앱을 종료하거나 백그라운드 상태에서도 실행 되도록 서비스 실행

---

- Android Gradle Plugin Version 7.3.1
- Gradle Version 7.4
- Hilt Version 2.42
- Kotlin Version 1.7.0 

---


# 1. API

## 업비트 API


BASE URL : https://api.upbit.com/v1/

JWT 인증 토큰 필수

Retrofit2 방식 사용




## 공포 탐욕지수 API


BASE URL : https://ubci-api.ubcindex.com/v1/crix/

별도 기한 및 키가 존재 하지 않는다.

Retrofit2 방식 사용




---

## 2. 라이브러리


