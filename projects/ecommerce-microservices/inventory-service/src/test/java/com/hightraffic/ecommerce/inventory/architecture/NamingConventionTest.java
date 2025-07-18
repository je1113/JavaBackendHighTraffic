package com.hightraffic.ecommerce.inventory.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

@DisplayName("네이밍 규칙 검증 테스트")
class NamingConventionTest {

    private JavaClasses classes;

    @BeforeEach
    void setUp() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.hightraffic.ecommerce.inventory");
    }

    @Test
    @DisplayName("컨트롤러는 Controller로 끝나야 한다")
    void controllersShouldEndWithController() {
        ArchRule rule = classes()
                .that().resideInAPackage("..adapter.in.web..")
                .and().areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
                .should().haveSimpleNameEndingWith("Controller")
                .because("REST 컨트롤러는 'Controller'로 끝나야 합니다");

        rule.check(classes);
    }

    @Test
    @DisplayName("서비스는 Service로 끝나야 한다")
    void servicesShouldEndWithService() {
        ArchRule rule = classes()
                .that().resideInAPackage("..application.service..")
                .and().areNotInterfaces()
                .and().areTopLevelClasses()  // 최상위 클래스만 선택
                .should().haveSimpleNameEndingWith("Service")
                .because("애플리케이션 서비스는 'Service'로 끝나야 합니다");

        rule.check(classes);
    }

    @Test
    @DisplayName("UseCase 인터페이스는 UseCase로 끝나야 한다")
    void useCasesShouldEndWithUseCase() {
        ArchRule rule = classes()
                .that().resideInAPackage("..application.port.in..")
                .and().areInterfaces()
                .should().haveSimpleNameEndingWith("UseCase")
                .because("인바운드 포트는 'UseCase'로 끝나야 합니다");

        rule.check(classes);
    }

    @Test
    @DisplayName("아웃바운드 포트는 Port로 끝나야 한다")
    void outboundPortsShouldEndWithPort() {
        ArchRule rule = classes()
                .that().resideInAPackage("..application.port.out..")
                .and().areInterfaces()
                .should().haveSimpleNameEndingWith("Port")
                .because("아웃바운드 포트는 'Port'로 끝나야 합니다");

        rule.check(classes);
    }

    @Test
    @DisplayName("어댑터는 Adapter로 끝나야 한다")
    void adaptersShouldEndWithAdapter() {
        // 실제 어댑터 클래스만 검사 (지원 클래스 제외)
        classes.stream()
                .filter(javaClass -> javaClass.getPackageName().contains("adapter.out"))
                .filter(javaClass -> !javaClass.isInterface())
                .filter(javaClass -> javaClass.isTopLevelClass())
                .filter(javaClass -> {
                    String simpleName = javaClass.getSimpleName();
                    // 제외할 클래스 패턴들
                    return !simpleName.endsWith("JpaEntity") &&
                           !simpleName.endsWith("JpaRepository") &&
                           !simpleName.endsWith("Configuration") &&
                           !simpleName.endsWith("Config") &&
                           !simpleName.endsWith("Properties") &&
                           !simpleName.endsWith("Mapper") &&
                           !simpleName.endsWith("EventListener") &&
                           !simpleName.endsWith("Metrics") &&
                           !simpleName.endsWith("HealthIndicator") &&
                           !simpleName.endsWith("Handler") &&
                           !simpleName.endsWith("Listener") &&
                           !simpleName.endsWith("Initializer");
                })
                .forEach(javaClass -> {
                    if (!javaClass.getSimpleName().endsWith("Adapter")) {
                        throw new AssertionError(
                                String.format("클래스 %s는 아웃바운드 어댑터이므로 'Adapter'로 끝나야 합니다",
                                        javaClass.getFullName())
                        );
                    }
                });
    }

    @Test
    @DisplayName("JPA 엔티티는 JpaEntity로 끝나야 한다")
    void jpaEntitiesShouldEndWithJpaEntity() {
        ArchRule rule = classes()
                .that().resideInAPackage("..adapter.out.persistence..")
                .and().areAnnotatedWith("jakarta.persistence.Entity")
                .should().haveSimpleNameEndingWith("JpaEntity")
                .because("JPA 엔티티는 'JpaEntity'로 끝나야 합니다");

        rule.check(classes);
    }

    @Test
    @DisplayName("JPA 리포지토리는 JpaRepository로 끝나야 한다")
    void jpaRepositoriesShouldEndWithJpaRepository() {
        ArchRule rule = classes()
                .that().resideInAPackage("..adapter.out.persistence..")
                .and().areAssignableTo("org.springframework.data.jpa.repository.JpaRepository")
                .should().haveSimpleNameEndingWith("JpaRepository")
                .because("JPA 리포지토리는 'JpaRepository'로 끝나야 합니다");

        rule.check(classes);
    }

    @Test
    @DisplayName("이벤트 리스너는 EventListener로 끝나야 한다")
    void eventListenersShouldEndWithEventListener() {
        // @KafkaListener 메서드를 가진 클래스만 검사
        classes.stream()
                .filter(javaClass -> javaClass.getMethods().stream()
                        .anyMatch(method -> method.isAnnotatedWith("org.springframework.kafka.annotation.KafkaListener")))
                .forEach(javaClass -> {
                    if (!javaClass.getSimpleName().endsWith("EventListener")) {
                        throw new AssertionError(
                                String.format("클래스 %s는 @KafkaListener 메서드를 가지고 있으므로 'EventListener'로 끝나야 합니다",
                                        javaClass.getFullName())
                        );
                    }
                });
    }

    @Test
    @DisplayName("예외 클래스는 Exception으로 끝나야 한다")
    void exceptionsShouldEndWithException() {
        ArchRule rule = classes()
                .that().areAssignableTo(Exception.class)
                .should().haveSimpleNameEndingWith("Exception")
                .because("예외 클래스는 'Exception'으로 끝나야 합니다");

        rule.check(classes);
    }

    @Test
    @DisplayName("설정 클래스는 Configuration 또는 Config로 끝나야 한다")
    void configurationClassesShouldHaveCorrectNaming() {
        ArchRule rule = classes()
                .that().areAnnotatedWith("org.springframework.context.annotation.Configuration")
                .should().haveSimpleNameEndingWith("Configuration")
                .orShould().haveSimpleNameEndingWith("Config")
                .because("설정 클래스는 'Configuration' 또는 'Config'로 끝나야 합니다");

        rule.check(classes);
    }

    @Test
    @DisplayName("Value Object는 특정 네이밍 규칙을 따라야 한다")
    void valueObjectsShouldFollowNamingConvention() {
        ArchRule rule = classes()
                .that().resideInAPackage("..domain.model.vo..")
                .should().haveSimpleNameNotEndingWith("VO")
                .because("Value Object는 'VO' 접미사를 사용하지 않고 의미있는 이름을 가져야 합니다");

        rule.check(classes);
    }

    @Test
    @DisplayName("상수는 대문자와 밑줄로 구성되어야 한다")
    void constantsShouldBeUpperCase() {
        ArchRule rule = fields()
                .that().areDeclaredInClassesThat().resideInAPackage("com.hightraffic.ecommerce.inventory..")
                .and().areStatic()
                .and().areFinal()
                .and().areNotPrivate()
                .and().doNotHaveModifier(com.tngtech.archunit.core.domain.JavaModifier.SYNTHETIC)  // 컴파일러 생성 필드 제외
                .should().haveNameMatching("^[A-Z][A-Z0-9_]*$")
                .because("상수는 대문자와 밑줄로만 구성되어야 합니다");

        rule.check(classes);
    }

    @Test
    @DisplayName("테스트 헬퍼 메서드는 특정 접두사를 가져야 한다")
    void testHelperMethodsShouldHavePrefix() {
        ArchRule rule = methods()
                .that().areDeclaredInClassesThat().haveSimpleNameEndingWith("Test")
                .and().arePrivate()
                .and().areStatic()
                .should().haveNameStartingWith("create")
                .orShould().haveNameStartingWith("build")
                .orShould().haveNameStartingWith("make")
                .orShould().haveNameStartingWith("given")
                .orShould().haveNameStartingWith("when")
                .orShould().haveNameStartingWith("then")
                .orShould().haveNameStartingWith("verify")
                .orShould().haveNameStartingWith("assert")
                .allowEmptyShould(true)
                .because("테스트 헬퍼 메서드는 의도를 명확히 하는 접두사를 가져야 합니다");

        rule.check(classes);
    }

    @Test
    @DisplayName("DTO 클래스는 Request, Response, Message로 끝나야 한다")
    void dtoClassesShouldHaveCorrectSuffix() {
        ArchRule rule = classes()
                .that().resideInAPackage("..dto..")
                .and().areNotInterfaces()
                .and().areTopLevelClasses()  // 최상위 클래스만 선택
                .should().haveSimpleNameEndingWith("Request")
                .orShould().haveSimpleNameEndingWith("Response")
                .orShould().haveSimpleNameEndingWith("Message")
                .orShould().haveSimpleNameEndingWith("DTO")
                .because("DTO 클래스는 용도에 맞는 접미사를 가져야 합니다");

        rule.check(classes);
    }
}