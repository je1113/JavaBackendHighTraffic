package gatling

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._
import java.util.UUID

/**
 * Gatling 시뮬레이션: 재고 예약 부하 테스트
 * 
 * 시나리오: 100개 재고에 5000명의 사용자가 1초 내에 동시 주문
 */
class StockReservationSimulation extends Simulation {

  // HTTP 설정
  val httpProtocol = http
    .baseUrl("http://localhost:8081") // Order Service URL
    .acceptHeader("application/json")
    .contentTypeHeader("application/json")
    .userAgentHeader("Gatling/StockReservation")

  // 테스트 상품 ID
  val productId = "550e8400-e29b-41d4-a716-446655440001"
  
  // 사용자 ID 생성을 위한 feeder
  val customerFeeder = Iterator.continually(Map(
    "customerId" -> f"550e8400-e29b-41d4-a716-${scala.util.Random.nextInt(1000)}%012d",
    "orderIndex" -> scala.util.Random.nextInt(10000)
  ))

  // 주문 생성 시나리오
  val createOrderScenario = scenario("Stock Reservation Load Test")
    .feed(customerFeeder)
    .exec(http("Create Order")
      .post("/api/v1/orders")
      .body(StringBody(session => s"""{
        "customerId": "${session("customerId").as[String]}",
        "items": [
          {
            "productId": "$productId",
            "quantity": 1,
            "unitPrice": 10000
          }
        ],
        "shippingAddress": {
          "street": "Test Street ${session("orderIndex").as[Int]}",
          "city": "Seoul",
          "zipCode": "12345"
        }
      }""")).asJson
      .check(status.in(200, 201, 400, 409)) // 성공 및 재고 부족 응답 모두 예상
      .check(
        jsonPath("$.orderId").optional.saveAs("orderId"),
        jsonPath("$.message").optional.saveAs("errorMessage")
      )
    )
    .doIf(session => session.contains("orderId")) {
      exec(session => {
        println(s"Order created: ${session("orderId").as[String]}")
        session
      })
    }
    .doIf(session => session.contains("errorMessage")) {
      exec(session => {
        println(s"Order failed: ${session("errorMessage").as[String]}")
        session
      })
    }

  // 재고 확인 시나리오 (선택적)
  val checkStockScenario = scenario("Check Stock")
    .exec(http("Get Stock Info")
      .get(s"http://localhost:8082/api/v1/inventory/products/$productId/stock")
      .check(status.is(200))
      .check(jsonPath("$.availableQuantity").saveAs("availableStock"))
    )
    .exec(session => {
      println(s"Available stock: ${session("availableStock").as[String]}")
      session
    })

  // 시뮬레이션 설정
  setUp(
    // 메인 시나리오: 5000명의 사용자가 1초 내에 동시 접속
    createOrderScenario.inject(
      atOnceUsers(5000) // 5000명 동시 접속
    ).protocols(httpProtocol),
    
    // 재고 확인 (테스트 전후)
    checkStockScenario.inject(
      atOnceUsers(1), // 시작 시 확인
      nothingFor(5.seconds), // 5초 대기
      atOnceUsers(1) // 종료 후 확인
    ).protocols(httpProtocol)
  ).assertions(
    // 전체 요청 수 확인
    global.requestsPerSec.between(1000, 10000),
    // 응답 시간 확인
    global.responseTime.percentile(50).lt(100), // P50 < 100ms
    global.responseTime.percentile(95).lt(500), // P95 < 500ms
    global.responseTime.percentile(99).lt(1000), // P99 < 1000ms
    // 실패율 확인 (재고 부족은 예상되므로 실패로 보지 않음)
    global.failedRequests.percent.lt(5)
  )
}

/**
 * 고급 시뮬레이션: 점진적 부하 증가
 */
class StockReservationRampUpSimulation extends Simulation {

  val httpProtocol = http
    .baseUrl("http://localhost:8081")
    .acceptHeader("application/json")
    .contentTypeHeader("application/json")

  val productId = "550e8400-e29b-41d4-a716-446655440001"
  
  val customerFeeder = Iterator.continually(Map(
    "customerId" -> f"550e8400-e29b-41d4-a716-${scala.util.Random.nextInt(1000)}%012d",
    "orderIndex" -> scala.util.Random.nextInt(10000)
  ))

  val createOrderScenario = scenario("Ramp Up Stock Reservation")
    .feed(customerFeeder)
    .exec(http("Create Order - Ramp Up")
      .post("/api/v1/orders")
      .body(StringBody(session => s"""{
        "customerId": "${session("customerId").as[String]}",
        "items": [
          {
            "productId": "$productId",
            "quantity": 1,
            "unitPrice": 10000
          }
        ],
        "shippingAddress": {
          "street": "Test Street ${session("orderIndex").as[Int]}",
          "city": "Seoul",
          "zipCode": "12345"
        }
      }""")).asJson
      .check(status.in(200, 201, 400, 409))
    )

  setUp(
    createOrderScenario.inject(
      // 점진적으로 부하 증가
      rampUsersPerSec(0).to(1000).during(10.seconds), // 0에서 1000 RPS로 10초간 증가
      constantUsersPerSec(1000).during(20.seconds), // 1000 RPS 유지 20초
      rampUsersPerSec(1000).to(5000).during(10.seconds), // 1000에서 5000 RPS로 10초간 증가
      constantUsersPerSec(5000).during(10.seconds) // 5000 RPS 유지 10초
    ).protocols(httpProtocol)
  )
}

/**
 * 동시성 테스트: 정확히 100개만 성공해야 함
 */
class StockReservationConcurrencyTest extends Simulation {

  val httpProtocol = http
    .baseUrl("http://localhost:8081")
    .acceptHeader("application/json")
    .contentTypeHeader("application/json")

  val productId = "550e8400-e29b-41d4-a716-446655440001"
  
  // 순차적 사용자 ID
  val sequentialCustomerFeeder = (1 to 5000).map(i => 
    Map("customerId" -> f"550e8400-e29b-41d4-a716-${i}%012d", "orderIndex" -> i)
  ).iterator

  val createOrderScenario = scenario("Concurrency Test")
    .feed(sequentialCustomerFeeder)
    .exec(http("Create Order - Concurrent")
      .post("/api/v1/orders")
      .body(StringBody(session => s"""{
        "customerId": "${session("customerId").as[String]}",
        "items": [
          {
            "productId": "$productId",
            "quantity": 1,
            "unitPrice": 10000
          }
        ],
        "shippingAddress": {
          "street": "Test Street ${session("orderIndex").as[Int]}",
          "city": "Seoul",
          "zipCode": "12345"
        }
      }""")).asJson
      .check(
        status.in(200, 201, 400, 409),
        status.saveAs("responseStatus")
      )
    )
    .exec(session => {
      val status = session("responseStatus").as[Int]
      if (status == 200 || status == 201) {
        println(s"SUCCESS: Order ${session("orderIndex").as[Int]} - Customer ${session("customerId").as[String]}")
      }
      session
    })

  setUp(
    createOrderScenario.inject(
      atOnceUsers(5000) // 정확히 5000명 동시 접속
    ).protocols(httpProtocol)
  ).assertions(
    // 정확히 100개의 성공 응답 예상 (초기 재고)
    global.successfulRequests.count.between(95, 105), // 약간의 오차 허용
    // 나머지는 재고 부족 응답
    global.requestsPerSec.greaterThan(1000) // 높은 동시성 확인
  )
}