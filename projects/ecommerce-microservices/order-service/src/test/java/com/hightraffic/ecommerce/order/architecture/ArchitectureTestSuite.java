package com.hightraffic.ecommerce.order.architecture;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

/**
 * 아키텍처 테스트 스위트
 * 
 * 모든 아키텍처 관련 테스트를 한 번에 실행할 수 있습니다.
 * 
 * 실행 방법:
 * ./gradlew :order-service:test --tests "*.ArchitectureTestSuite"
 */
@Suite
@SelectClasses({
    DomainLayerArchitectureTest.class,
    HexagonalArchitectureTest.class,
    PackageStructureTest.class,
    NamingConventionTest.class
})
public class ArchitectureTestSuite {
}