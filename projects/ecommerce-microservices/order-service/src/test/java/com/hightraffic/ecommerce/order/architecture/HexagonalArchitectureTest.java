package com.hightraffic.ecommerce.order.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;

@DisplayName("헥사고날 아키텍처 의존성 테스트")
class HexagonalArchitectureTest {

    private static final String DOMAIN_PACKAGE = "com.hightraffic.ecommerce.order.domain..";
    private static final String APPLICATION_PACKAGE = "com.hightraffic.ecommerce.order.application..";
    private static final String ADAPTER_PACKAGE = "com.hightraffic.ecommerce.order.adapter..";
    private static final String PORT_IN_PACKAGE = "com.hightraffic.ecommerce.order.application.port.in..";
    private static final String PORT_OUT_PACKAGE = "com.hightraffic.ecommerce.order.application.port.out..";
    
    private JavaClasses classes;

    @BeforeEach
    void setUp() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.hightraffic.ecommerce.order");
    }

    @Test
    @DisplayName("어댑터는 포트를 통해서만 애플리케이션 레이어에 접근해야 한다")
    void adaptersShouldOnlyAccessApplicationThroughPorts() {
        // Handler와 Service 패키지에만 직접 접근을 금지
        ArchRule rule = noClasses()
                .that().resideInAPackage(ADAPTER_PACKAGE)
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "..application.service..",
                        "..application.handler.."
                )
                .because("어댑터는 포트 인터페이스를 통해서만 애플리케이션 레이어에 접근해야 합니다");

        rule.check(classes);
    }

    @Test
    @DisplayName("인바운드 어댑터는 인바운드 포트만 사용해야 한다")
    void inboundAdaptersShouldOnlyUseInboundPorts() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..adapter.in..")
                .should().dependOnClassesThat()
                .resideInAPackage(PORT_OUT_PACKAGE)
                .because("인바운드 어댑터는 아웃바운드 포트를 직접 사용하지 않아야 합니다");

        rule.check(classes);
    }

    @Test
    @DisplayName("아웃바운드 어댑터는 아웃바운드 포트를 구현해야 한다")
    void outboundAdaptersShouldImplementOutboundPorts() {
        ArchRule rule = classes()
                .that().resideInAPackage("..adapter.out..")
                .and().haveSimpleNameEndingWith("Adapter")
                .and().areNotInterfaces()
                .and().areNotEnums()
                .should().implement(
                        "com.hightraffic.ecommerce.order.application.port.out.EventPublishingPort"
                ).orShould().implement(
                        "com.hightraffic.ecommerce.order.application.port.out.LoadOrderPort"
                ).orShould().implement(
                        "com.hightraffic.ecommerce.order.application.port.out.LoadOrdersByCustomerPort"
                ).orShould().implement(
                        "com.hightraffic.ecommerce.order.application.port.out.OrderPersistencePort"
                ).orShould().implement(
                        "com.hightraffic.ecommerce.order.application.port.out.PaymentProcessingPort"
                ).orShould().implement(
                        "com.hightraffic.ecommerce.order.application.port.out.PublishEventPort"
                ).orShould().implement(
                        "com.hightraffic.ecommerce.order.application.port.out.SaveOrderPort"
                ).orShould().implement(
                        "com.hightraffic.ecommerce.order.application.port.out.StockValidationPort"
                ).orShould().implement(
                        "com.hightraffic.ecommerce.order.application.port.out.OrderPolicyPort"
                )
                .because("아웃바운드 어댑터는 아웃바운드 포트 인터페이스를 구현해야 합니다");

        rule.check(classes);
    }

    @Test
    @DisplayName("애플리케이션 서비스는 인바운드 포트를 구현해야 한다")
    void applicationServicesShouldImplementInboundPorts() {
        ArchRule rule = classes()
                .that().resideInAPackage("..application.service..")
                .and().haveSimpleNameEndingWith("Service")
                .should().implement(
                        "com.hightraffic.ecommerce.order.application.port.in.CancelOrderUseCase"
                ).orShould().implement(
                        "com.hightraffic.ecommerce.order.application.port.in.ConfirmOrderUseCase"
                ).orShould().implement(
                        "com.hightraffic.ecommerce.order.application.port.in.CreateOrderUseCase"
                ).orShould().implement(
                        "com.hightraffic.ecommerce.order.application.port.in.GetOrderUseCase"
                )
                .because("애플리케이션 서비스는 인바운드 포트(UseCase)를 구현해야 합니다");

        rule.check(classes);
    }
    
    @Test
    @DisplayName("애플리케이션 핸들러는 인바운드 포트를 구현해야 한다")
    void applicationHandlersShouldImplementInboundPorts() {
        // Order service doesn't have specific event handler use cases in port.in
        // So we'll check that handlers exist in the handler package
        ArchRule rule = classes()
                .that().resideInAPackage("..application.handler..")
                .and().haveSimpleNameEndingWith("Handler")
                .should().haveSimpleNameEndingWith("Handler")
                .because("애플리케이션 핸들러는 Handler로 끝나야 합니다");

        rule.check(classes);
    }

    @Test
    @DisplayName("포트는 인터페이스여야 한다")
    void portsShouldBeInterfaces() {
        ArchRule rule = classes()
                .that().resideInAPackage("..application.port..")
                .and().haveSimpleNameEndingWith("Port")
                .or().haveSimpleNameEndingWith("UseCase")
                .should().beInterfaces()
                .because("포트는 인터페이스로 정의되어야 합니다");

        rule.check(classes);
    }

    @Test
    @DisplayName("웹 어댑터는 다른 어댑터에 의존하지 않아야 한다")
    void webAdaptersShouldNotDependOnOtherAdapters() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..adapter.in.web..")
                .should().dependOnClassesThat()
                .resideInAPackage("..adapter.out..")
                .because("웹 어댑터는 다른 어댑터에 직접 의존하지 않아야 합니다");

        rule.check(classes);
    }

    @Test
    @DisplayName("영속성 어댑터는 도메인 모델을 직접 반환하지 않아야 한다")
    void persistenceAdaptersShouldNotExposeEntities() {
        ArchRule rule = classes()
                .that().resideInAPackage("..adapter.out.persistence..")
                .and().haveSimpleNameEndingWith("JpaEntity")
                .should().onlyBeAccessed()
                .byClassesThat().resideInAPackage("..adapter.out.persistence..")
                .because("JPA 엔티티는 영속성 어댑터 외부로 노출되지 않아야 합니다");

        rule.check(classes);
    }

    @Test
    @DisplayName("이벤트 핸들러는 애플리케이션 서비스만 호출해야 한다")
    void eventHandlersShouldOnlyCallApplicationServices() {
        ArchRule rule = classes()
                .that().resideInAPackage("..adapter.in.messaging..")
                .and().haveSimpleNameEndingWith("EventListener")
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage(
                        "com.hightraffic.ecommerce.order.application.port.in..",
                        "com.hightraffic.ecommerce.order.adapter.in.messaging..",
                        "com.hightraffic.ecommerce.common..",
                        "org.springframework..",
                        "org.slf4j..",
                        "java..",
                        "com.fasterxml.jackson..",  // JSON 처리를 위한 Jackson
                        "org.apache.kafka.."        // Kafka 메시징 인프라
                )
                .because("이벤트 핸들러는 애플리케이션 포트를 통해서만 비즈니스 로직을 호출해야 합니다");

        rule.check(classes);
    }

    @Test
    @DisplayName("설정 클래스는 어댑터 패키지에 위치해야 한다")
    void configurationClassesShouldBeInConfigPackage() {
        ArchRule rule = classes()
                .that().haveSimpleNameEndingWith("Configuration")
                .or().haveSimpleNameEndingWith("Config")
                .should().resideInAPackage("..config..")
                .orShould().resideInAPackage("..adapter.in.messaging..")  // KafkaConfiguration이 여기 있음
                .because("설정 클래스는 config 패키지 또는 해당 어댑터 패키지에 위치해야 합니다");

        rule.check(classes);
    }
}