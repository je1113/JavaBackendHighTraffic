package com.hightraffic.ecommerce.inventory.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.Architectures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

@DisplayName("패키지 구조 검증 테스트")
class PackageStructureTest {

    private JavaClasses classes;

    @BeforeEach
    void setUp() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.hightraffic.ecommerce.inventory");
    }

    @Test
    @DisplayName("도메인 패키지는 올바른 하위 패키지를 가져야 한다")
    void domainPackageShouldHaveCorrectSubPackages() {
        ArchRule rule = classes()
                .that().resideInAPackage("..domain..")
                .should().resideInAnyPackage(
                        "..domain.model..",
                        "..domain.model.vo..",
                        "..domain.service..",
                        "..domain.exception..",
                        "..domain.event..",
                        "..domain.repository.."
                )
                .because("도메인 패키지는 model, service, exception, event, repository 하위 패키지로 구성되어야 합니다");

        rule.check(classes);
    }

    @Test
    @DisplayName("애플리케이션 패키지는 올바른 하위 패키지를 가져야 한다")
    void applicationPackageShouldHaveCorrectSubPackages() {
        ArchRule rule = classes()
                .that().resideInAPackage("..application..")
                .should().resideInAnyPackage(
                        "..application.port.in..",
                        "..application.port.out..",
                        "..application.service..",
                        "..application.handler.."
                )
                .because("애플리케이션 패키지는 port와 service 하위 패키지로 구성되어야 합니다");

        rule.check(classes);
    }

    @Test
    @DisplayName("어댑터 패키지는 인바운드와 아웃바운드로 구분되어야 한다")
    void adapterPackageShouldBeOrganizedByDirection() {
        ArchRule rule = classes()
                .that().resideInAPackage("..adapter..")
                .should().resideInAnyPackage(
                        "..adapter.in..",
                        "..adapter.out.."
                )
                .because("어댑터는 인바운드(in)와 아웃바운드(out)로 구분되어야 합니다");

        rule.check(classes);
    }

    @Test
    @DisplayName("웹 어댑터는 올바른 하위 패키지를 가져야 한다")
    void webAdapterShouldHaveCorrectSubPackages() {
        ArchRule rule = classes()
                .that().resideInAPackage("..adapter.in.web..")
                .and().areNotInterfaces()
                .should().resideInAnyPackage(
                        "..adapter.in.web",
                        "..adapter.in.web.dto.."
                )
                .because("웹 어댑터는 컨트롤러와 DTO로 구성되어야 합니다");

        rule.check(classes);
    }

    @Test
    @DisplayName("영속성 어댑터는 올바른 하위 패키지를 가져야 한다")
    void persistenceAdapterShouldHaveCorrectSubPackages() {
        ArchRule rule = classes()
                .that().resideInAPackage("..adapter.out.persistence..")
                .and().areNotInterfaces()
                .and().areTopLevelClasses()  // 최상위 클래스만 검사
                .should().haveSimpleNameEndingWith("Adapter")
                .orShould().haveSimpleNameEndingWith("JpaEntity")
                .orShould().haveSimpleNameEndingWith("JpaRepository")
                .orShould().haveSimpleNameEndingWith("Mapper")
                .orShould().haveSimpleNameEndingWith("EventListener")
                .because("영속성 어댑터는 Adapter, Entity, Repository로 구성되어야 합니다");

        rule.check(classes);
    }

    @Test
    @DisplayName("메시징 어댑터는 올바른 구조를 가져야 한다")
    void messagingAdapterShouldHaveCorrectStructure() {
        ArchRule rule = classes()
                .that().resideInAPackage("..adapter.in.messaging..")
                .and().areTopLevelClasses()  // 최상위 클래스만 검사
                .should().haveSimpleNameEndingWith("EventListener")
                .orShould().haveSimpleNameEndingWith("Configuration")
                .orShould().haveSimpleNameEndingWith("Message")
                .orShould().haveSimpleNameEndingWith("ErrorHandler")
                .orShould().haveSimpleNameEndingWith("Metrics")
                .orShould().resideInAPackage("..adapter.in.messaging.dto..")
                .because("메시징 어댑터는 EventListener와 관련 클래스로 구성되어야 합니다");

        rule.check(classes);
    }

    @Test
    @DisplayName("DTO는 특정 패키지에만 위치해야 한다")
    void dtosShouldBeInSpecificPackages() {
        ArchRule rule = classes()
                .that().haveSimpleNameEndingWith("DTO")
                .or().haveSimpleNameEndingWith("Request")
                .or().haveSimpleNameEndingWith("Response")
                .or().haveSimpleNameEndingWith("Message")
                .and().areTopLevelClasses()  // 최상위 클래스만 검사
                .should().resideInAnyPackage(
                        "..adapter.in.web.dto..",
                        "..adapter.in.messaging.dto..",
                        "..adapter.out.external.dto.."
                )
                .because("DTO는 어댑터의 dto 패키지에만 위치해야 합니다");

        rule.check(classes);
    }

    @Test
    @DisplayName("설정 클래스는 config 패키지에 위치해야 한다")
    void configurationClassesShouldBeInConfigPackage() {
        ArchRule rule = classes()
                .that().haveSimpleNameEndingWith("Configuration")
                .or().haveSimpleNameEndingWith("Config")
                .or().haveSimpleNameEndingWith("Properties")
                .should().resideInAnyPackage(
                        "..config..",
                        "..adapter.in.messaging..",
                        "..adapter.out.."
                )
                .because("설정 클래스는 config 패키지 또는 해당 어댑터 패키지에 위치해야 합니다");

        rule.check(classes);
    }

    @Test
    @DisplayName("예외 클래스는 올바른 패키지에 위치해야 한다")
    void exceptionClassesShouldBeInCorrectPackages() {
        ArchRule rule = classes()
                .that().haveSimpleNameEndingWith("Exception")
                .should().resideInAnyPackage(
                        "..domain.exception..",
                        "..domain..",  // 도메인 객체 내 중첩 클래스 허용
                        "..application..",  // 애플리케이션 계층도 예외를 가질 수 있음
                        "..adapter.."  // 어댑터 계층의 모든 부분이 예외를 가질 수 있음
                )
                .because("예외 클래스는 해당 계층의 책임에 맞는 패키지에 위치해야 합니다");

        rule.check(classes);
    }
}