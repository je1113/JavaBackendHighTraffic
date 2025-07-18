package com.hightraffic.ecommerce.common.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

@DisplayName("도메인 이벤트 아키텍처 테스트")
class DomainEventArchitectureTest {

    private JavaClasses classes;

    @BeforeEach
    void setUp() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.hightraffic.ecommerce.common");
    }

    @Test
    @DisplayName("도메인 이벤트는 DomainEvent를 상속해야 한다")
    void domainEventsShouldExtendDomainEvent() {
        ArchRule rule = classes()
                .that().resideInAPackage("..event..")
                .and().haveSimpleNameEndingWith("Event")
                .and().doNotHaveSimpleName("DomainEvent")
                .should().beAssignableTo("com.hightraffic.ecommerce.common.event.base.DomainEvent")
                .because("모든 도메인 이벤트는 DomainEvent를 상속해야 합니다");

        rule.check(classes);
    }

    @Test
    @DisplayName("도메인 이벤트는 불변이어야 한다")
    void domainEventsShouldBeImmutable() {
        ArchRule rule = classes()
                .that().resideInAPackage("..event..")
                .and().haveSimpleNameEndingWith("Event")
                .and().doNotHaveSimpleName("DomainEvent")
                .should().haveOnlyFinalFields()
                .because("도메인 이벤트는 불변 객체여야 합니다");

        rule.check(classes);
    }

    @Test
    @DisplayName("도메인 이벤트는 올바른 패키지에 위치해야 한다")
    void domainEventsShouldBeInCorrectPackage() {
        ArchRule rule = classes()
                .that().haveSimpleNameEndingWith("Event")
                .and().areAssignableTo("com.hightraffic.ecommerce.common.event.base.DomainEvent")
                .should().resideInAPackage("com.hightraffic.ecommerce.common.event..")
                .because("도메인 이벤트는 event 패키지 하위에 위치해야 합니다");

        rule.check(classes);
    }

    @Test
    @DisplayName("도메인 이벤트는 프레임워크 의존성을 가지지 않아야 한다")
    void domainEventsShouldNotDependOnFrameworks() {
        ArchRule rule = classes()
                .that().resideInAPackage("..event..")
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage(
                        "java..",
                        "com.hightraffic.ecommerce.common.event..",
                        "com.fasterxml.jackson..", // JSON 직렬화를 위해 허용
                        "jakarta.validation.." // 표준 검증 API 허용
                )
                .because("도메인 이벤트는 외부 프레임워크에 의존하지 않아야 합니다");

        rule.check(classes);
    }
}